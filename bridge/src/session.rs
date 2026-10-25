use anyhow::{anyhow, Result};
use portable_pty::{native_pty_system, Child, CommandBuilder, MasterPty, PtySize};
use std::collections::{HashMap, VecDeque};
use std::io::{Read, Write};
use std::path::PathBuf;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Arc;
use tokio::sync::{broadcast, Mutex};

use crate::proto::SessionInfo;
use crate::store::now_secs;
use crate::workspace;

const TAIL_LIMIT: usize = 256 * 1024;
const BROADCAST_LIMIT: usize = 512;

pub fn default_shell() -> Vec<String> {
    if cfg!(windows) {
        vec!["powershell".into()]
    } else {
        match std::env::var("SHELL") {
            Ok(shell) if !shell.trim().is_empty() => vec![shell],
            _ => vec!["/bin/sh".into()],
        }
    }
}

pub fn resolve_shell(requested: Option<String>) -> Vec<String> {
    match requested {
        Some(shell) if !shell.trim().is_empty() => vec![shell],
        _ => default_shell(),
    }
}

pub fn shell_label(requested: Option<String>) -> String {
    resolve_shell(requested).join(" ")
}

pub struct Session {
    pub info: SessionInfo,
    pub cwd: PathBuf,
    writer: Mutex<Box<dyn Write + Send>>,
    master: Mutex<Box<dyn MasterPty + Send>>,
    child: Mutex<Box<dyn Child + Send + Sync>>,
    output: broadcast::Sender<Vec<u8>>,
    tail: Mutex<VecDeque<u8>>,
    last_active: AtomicU64,
}

impl Session {
    pub async fn cwd(&self) -> PathBuf {
        self.cwd.clone()
    }

    pub fn subscribe(&self) -> broadcast::Receiver<Vec<u8>> {
        self.output.subscribe()
    }

    pub fn touch(&self) {
        self.last_active.store(now_secs(), Ordering::Relaxed);
    }

    pub fn idle_secs(&self) -> u64 {
        now_secs().saturating_sub(self.last_active.load(Ordering::Relaxed))
    }

    pub async fn write(&self, bytes: &[u8]) -> Result<()> {
        let mut writer = self.writer.lock().await;
        writer.write_all(bytes)?;
        writer.flush()?;
        self.touch();
        Ok(())
    }

    pub async fn resize(&self, cols: u16, rows: u16) -> Result<()> {
        let master = self.master.lock().await;
        master.resize(PtySize {
            rows: rows.max(1),
            cols: cols.max(2),
            pixel_width: 0,
            pixel_height: 0,
        })?;
        self.touch();
        Ok(())
    }

    pub async fn signal(&self, name: &str) -> Result<()> {
        let signal = match name {
            "int" | "sigint" => libc::SIGINT,
            "term" | "sigterm" => libc::SIGTERM,
            "quit" | "sigquit" => libc::SIGQUIT,
            "hup" | "sighup" => libc::SIGHUP,
            other => return Err(anyhow!("unknown signal: {other}")),
        };
        let pid = {
            let child = self.child.lock().await;
            child.process_id().map(|p| p as i32)
        };
        let pid = pid.ok_or_else(|| anyhow!("session has no process id"))?;
        #[cfg(unix)]
        unsafe {
            libc::kill(pid, signal);
        }
        #[cfg(not(unix))]
        {
            let _ = signal;
            return Err(anyhow!("signals are not supported on this platform"));
        }
        self.touch();
        Ok(())
    }

    pub async fn tail(&self) -> String {
        let tail = self.tail.lock().await;
        let bytes: Vec<u8> = tail.iter().copied().collect();
        String::from_utf8_lossy(&bytes).into_owned()
    }

    pub async fn kill(&self) {
        let mut child = self.child.lock().await;
        let _ = child.kill();
    }
}

pub struct SessionManager {
    sessions: Mutex<HashMap<String, Arc<Session>>>,
    default_cwd: PathBuf,
    idle_timeout: u64,
}

impl SessionManager {
    pub fn new(default_cwd: PathBuf, idle_timeout: u64) -> Self {
        Self {
            sessions: Mutex::new(HashMap::new()),
            default_cwd,
            idle_timeout,
        }
    }

    pub async fn open(
        &self,
        id: String,
        shell: Option<String>,
        cwd: Option<String>,
        name: Option<String>,
        cols: u16,
        rows: u16,
    ) -> Result<Arc<Session>> {
        let argv = resolve_shell(shell);
        let program = argv.first().cloned().unwrap_or_else(|| "/bin/sh".into());

        let pair = native_pty_system().openpty(PtySize {
            rows: rows.max(1),
            cols: cols.max(2),
            pixel_width: 0,
            pixel_height: 0,
        })?;

        let work_dir = cwd
            .map(|c| workspace::expand(&c))
            .filter(|p| p.is_dir())
            .unwrap_or_else(|| self.default_cwd.clone());

        let mut command = CommandBuilder::new(program);
        command.cwd(&work_dir);
        command.env("TERM", "xterm-256color");
        command.env("COLORTERM", "truecolor");

        let child = pair.slave.spawn_command(command)?;
        drop(pair.slave);

        let reader = pair.master.try_clone_reader()?;
        let writer = pair.master.take_writer()?;

        let (sender, _) = broadcast::channel(BROADCAST_LIMIT);
        let info = SessionInfo {
            id: id.clone(),
            name: name.unwrap_or_else(|| argv.join(" ")),
            shell: argv.join(" "),
            cols: cols.max(2),
            rows: rows.max(1),
            created_at: now_secs(),
            closed: false,
        };

        let session = Arc::new(Session {
            info,
            cwd: work_dir.clone(),
            writer: Mutex::new(writer),
            master: Mutex::new(pair.master),
            child: Mutex::new(child),
            output: sender.clone(),
            tail: Mutex::new(VecDeque::new()),
            last_active: AtomicU64::new(now_secs()),
        });

        spawn_reader(reader, sender, session.clone());
        self.sessions.lock().await.insert(id, session.clone());
        Ok(session)
    }

    pub async fn get(&self, id: &str) -> Option<Arc<Session>> {
        self.sessions.lock().await.get(id).cloned()
    }

    pub async fn list(&self) -> Vec<SessionInfo> {
        self.sessions
            .lock()
            .await
            .values()
            .map(|s| s.info.clone())
            .collect()
    }

    pub async fn close(&self, id: &str) -> bool {
        let session = self.sessions.lock().await.remove(id);
        match session {
            Some(session) => {
                session.kill().await;
                true
            }
            None => false,
        }
    }

    pub async fn count(&self) -> usize {
        self.sessions.lock().await.len()
    }

    pub async fn sweep_idle(&self) {
        let expired: Vec<String> = {
            let sessions = self.sessions.lock().await;
            sessions
                .iter()
                .filter(|(_, s)| s.idle_secs() > self.idle_timeout)
                .map(|(id, _)| id.clone())
                .collect()
        };
        for id in expired {
            tracing::info!(session = %id, "closing idle session");
            self.close(&id).await;
        }
    }
}

fn spawn_reader(
    reader: Box<dyn Read + Send>,
    sender: broadcast::Sender<Vec<u8>>,
    session: Arc<Session>,
) {
    let (tx, mut rx) = tokio::sync::mpsc::unbounded_channel::<Vec<u8>>();
    std::thread::spawn(move || {
        let mut reader = reader;
        let mut buffer = [0u8; 8192];
        loop {
            match reader.read(&mut buffer) {
                Ok(0) | Err(_) => break,
                Ok(n) => {
                    if tx.send(buffer[..n].to_vec()).is_err() {
                        break;
                    }
                }
            }
        }
    });

    tokio::spawn(async move {
        while let Some(chunk) = rx.recv().await {
            session.touch();
            {
                let mut tail = session.tail.lock().await;
                for byte in &chunk {
                    if tail.len() >= TAIL_LIMIT {
                        tail.pop_front();
                    }
                    tail.push_back(*byte);
                }
            }
            let _ = sender.send(chunk);
        }
    });
}
