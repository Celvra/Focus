use std::collections::{HashMap, VecDeque};
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::Arc;

use anyhow::{anyhow, Result};
use serde::{Deserialize, Serialize};
use tokio::io::{AsyncBufReadExt, BufReader};
use tokio::process::Command;
use tokio::sync::{Mutex, RwLock};
use tokio::time::Duration;

use crate::config::AppConfig;
use crate::exec;
use crate::workspace::Workspace;

const MAX_JOBS: usize = 8;
const MAX_LOG_LINES: usize = 2000;
const MAX_LOG_BYTES: usize = 512 * 1024;
const LOG_RETENTION_SECS: u64 = 600;

#[derive(Debug, Clone, Serialize)]
pub struct BgJobInfo {
    pub id: String,
    pub command: String,
    pub cwd: String,
    pub pid: u32,
    pub started_at: u64,
    pub ended_at: u64,
    pub status: String,
    pub exit_code: i32,
    pub timed_out: bool,
    pub lines: usize,
    pub bytes: usize,
}

#[derive(Debug, Clone, Serialize)]
pub struct BgLogLine {
    pub seq: u64,
    pub stream: String,
    pub data: String,
    pub at: u64,
}

#[derive(Debug, Deserialize)]
pub struct BgStartRequest {
    pub command: String,
    #[serde(default)]
    pub cwd: Option<String>,
    #[serde(default)]
    pub env: Option<HashMap<String, String>>,
    #[serde(default)]
    pub timeout_sec: Option<u64>,
}

#[derive(Debug, Deserialize)]
pub struct BgIdRequest {
    pub id: String,
}

pub struct BgJob {
    info: Mutex<BgJobInfo>,
    logs: Mutex<VecDeque<BgLogLine>>,
    bytes: AtomicU64,
    seq: AtomicU64,
    timed_out: AtomicBool,
    killed: AtomicBool,
}

impl BgJob {
    async fn append(&self, stream: &str, data: String) {
        if data.is_empty() {
            return;
        }
        let seq = self.seq.fetch_add(1, Ordering::Relaxed) + 1;
        let at = crate::store::now_secs();
        let mut logs = self.logs.lock().await;
        let size = data.len();
        logs.push_back(BgLogLine {
            seq,
            stream: stream.to_string(),
            data,
            at,
        });
        while logs.len() > MAX_LOG_LINES {
            logs.pop_front();
        }
        let mut total = self.bytes.fetch_add(size as u64, Ordering::Relaxed) + size as u64;
        while total > MAX_LOG_BYTES as u64 && logs.len() > 1 {
            if let Some(line) = logs.pop_front() {
                total = total.saturating_sub(line.data.len() as u64);
            }
        }
        self.bytes.store(total, Ordering::Relaxed);
    }

    async fn info(&self) -> BgJobInfo {
        self.info.lock().await.clone()
    }

    async fn logs_after(&self, after: u64) -> Vec<BgLogLine> {
        let logs = self.logs.lock().await;
        logs.iter().filter(|line| line.seq > after).cloned().collect()
    }

    async fn set_status(&self, status: &str, code: i32, timed_out: bool) {
        let line_count = self.logs.lock().await.len();
        let mut info = self.info.lock().await;
        info.status = status.to_string();
        info.exit_code = code;
        info.timed_out = timed_out;
        info.ended_at = crate::store::now_secs();
        info.lines = line_count;
        info.bytes = self.bytes.load(Ordering::Relaxed) as usize;
    }
}

#[derive(Default)]
pub struct BgManager {
    jobs: RwLock<HashMap<String, Arc<BgJob>>>,
}

impl BgManager {
    pub async fn start(
        &self,
        req: &BgStartRequest,
        config: &AppConfig,
        workspace: &Workspace,
    ) -> Result<BgJobInfo> {
        if req.command.trim().is_empty() {
            return Err(anyhow!("command is required"));
        }
        {
            let jobs = self.jobs.read().await;
            let mut running = 0;
            for job in jobs.values() {
                if job.info.lock().await.status == "running" {
                    running += 1;
                }
            }
            if running >= MAX_JOBS {
                return Err(anyhow!("too many background commands, stop one first"));
            }
        }
        let exec_req = crate::proto::ExecRequest {
            command: Some(req.command.clone()),
            argv: None,
            cwd: req.cwd.clone(),
            env: req.env.clone(),
            stdin: None,
            timeout_sec: req.timeout_sec,
            session_id: None,
        };
        let plan = exec::plan(&exec_req, config, workspace, None)?;
        let mut command = Command::new(&plan.program);
        command.args(&plan.args);
        command.current_dir(&plan.cwd);
        command.stdin(std::process::Stdio::null());
        command.stdout(std::process::Stdio::piped());
        command.stderr(std::process::Stdio::piped());
        for (key, value) in &plan.env {
            command.env(key, value);
        }
        #[cfg(unix)]
        {
            command.process_group(0);
        }
        let mut child = command.spawn()?;
        let pid = child.id().unwrap_or(0);
        let id = crate::store::new_device_id();
        let started_at = crate::store::now_secs();
        let job = Arc::new(BgJob {
            info: Mutex::new(BgJobInfo {
                id: id.clone(),
                command: plan.display.clone(),
                cwd: plan.cwd.display().to_string(),
                pid,
                started_at,
                ended_at: 0,
                status: "running".into(),
                exit_code: 0,
                timed_out: false,
                lines: 0,
                bytes: 0,
            }),
            logs: Mutex::new(VecDeque::new()),
            bytes: AtomicU64::new(0),
            seq: AtomicU64::new(0),
            timed_out: AtomicBool::new(false),
            killed: AtomicBool::new(false),
        });
        {
            let mut jobs = self.jobs.write().await;
            jobs.insert(id.clone(), job.clone());
        }
        self.cleanup().await;

        if let Some(stdout) = child.stdout.take() {
            let job = job.clone();
            tokio::spawn(async move {
                let mut lines = BufReader::new(stdout).lines();
                while let Ok(Some(line)) = lines.next_line().await {
                    job.append("stdout", line).await;
                }
            });
        }
        if let Some(stderr) = child.stderr.take() {
            let job = job.clone();
            tokio::spawn(async move {
                let mut lines = BufReader::new(stderr).lines();
                while let Ok(Some(line)) = lines.next_line().await {
                    job.append("stderr", line).await;
                }
            });
        }

        let timeout = plan.timeout_sec.max(1);
        {
            let job = job.clone();
            tokio::spawn(async move {
                tokio::time::sleep(Duration::from_secs(timeout)).await;
                let pid = {
                    let info = job.info.lock().await;
                    if info.status == "running" {
                        Some(info.pid)
                    } else {
                        None
                    }
                };
                if let Some(pid) = pid {
                    job.timed_out.store(true, Ordering::Relaxed);
                    kill_group(pid);
                }
            });
        }

        let waiter = job.clone();
        tokio::spawn(async move {
            let status = child.wait().await;
            let code = status.ok().and_then(|value| value.code()).unwrap_or(-1);
            let timed_out = waiter.timed_out.load(Ordering::Relaxed);
            let killed = waiter.killed.load(Ordering::Relaxed);
            let name = if killed {
                "killed"
            } else if timed_out {
                "timed_out"
            } else if code == 0 {
                "completed"
            } else {
                "failed"
            };
            waiter.set_status(name, code, timed_out).await;
        });

        Ok(job.info().await)
    }

    pub async fn list(&self) -> Vec<BgJobInfo> {
        let jobs = self.jobs.read().await;
        let mut out = Vec::with_capacity(jobs.len());
        for job in jobs.values() {
            out.push(job.info().await);
        }
        out.sort_by_key(|info| info.started_at);
        out
    }

    pub async fn get(&self, id: &str) -> Option<Arc<BgJob>> {
        self.jobs.read().await.get(id).cloned()
    }

    pub async fn logs(&self, id: &str, after: u64) -> Option<(Vec<BgLogLine>, bool, BgJobInfo)> {
        let job = self.get(id).await?;
        let info = job.info().await;
        let lines = job.logs_after(after).await;
        let done = info.status != "running";
        Some((lines, done, info))
    }

    pub async fn kill(&self, id: &str) -> bool {
        let Some(job) = self.get(id).await else {
            return false;
        };
        let pid = job.info.lock().await.pid;
        job.killed.store(true, Ordering::Relaxed);
        kill_group(pid);
        job.set_status("killed", -1, false).await;
        true
    }

    pub async fn clear(&self, id: Option<&str>) {
        let mut jobs = self.jobs.write().await;
        match id {
            Some(id) => {
                if let Some(job) = jobs.get(id) {
                    if job.info.lock().await.status != "running" {
                        jobs.remove(id);
                    }
                }
            }
            None => {
                let finished: Vec<String> = {
                    let mut ids = Vec::new();
                    for (id, job) in jobs.iter() {
                        if job.info.lock().await.status != "running" {
                            ids.push(id.clone());
                        }
                    }
                    ids
                };
                for id in finished {
                    jobs.remove(&id);
                }
            }
        }
    }

    async fn cleanup(&self) {
        let now = crate::store::now_secs();
        let mut jobs = self.jobs.write().await;
        let stale: Vec<String> = {
            let mut ids = Vec::new();
            for (id, job) in jobs.iter() {
                let info = job.info.lock().await;
                if info.status != "running" && now.saturating_sub(info.ended_at) > LOG_RETENTION_SECS {
                    ids.push(id.clone());
                }
            }
            ids
        };
        for id in stale {
            jobs.remove(&id);
        }
    }
}

pub fn kill_group(pid: u32) {
    #[cfg(unix)]
    unsafe {
        libc::kill(-(pid as i32), libc::SIGTERM);
    }
    std::thread::spawn(move || {
        std::thread::sleep(Duration::from_millis(800));
        #[cfg(unix)]
        unsafe {
            libc::kill(-(pid as i32), libc::SIGKILL);
        }
    });
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn log_buffer_is_bounded() {
        let runtime = tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
            .unwrap();
        runtime.block_on(async {
            let info = Mutex::new(BgJobInfo {
                id: "x".into(),
                command: "x".into(),
                cwd: "/".into(),
                pid: 0,
                started_at: 0,
                ended_at: 0,
                status: "running".into(),
                exit_code: 0,
                timed_out: false,
                lines: 0,
                bytes: 0,
            });
            let job = BgJob {
                info,
                logs: Mutex::new(VecDeque::new()),
                bytes: AtomicU64::new(0),
                seq: AtomicU64::new(0),
                timed_out: AtomicBool::new(false),
                killed: AtomicBool::new(false),
            };
            for index in 0..(MAX_LOG_LINES + 50) {
                job.append("stdout", format!("line {index}")).await;
            }
            let logs = job.logs.lock().await;
            assert_eq!(logs.len(), MAX_LOG_LINES);
            assert_eq!(logs.front().map(|line| line.seq), Some(51));
        });
    }
}
