use anyhow::Result;
use std::collections::HashMap;
use tokio::io::AsyncReadExt;
use tokio::process::Command;
use tokio::sync::mpsc;
use tokio::time::{timeout, Duration};

use crate::config::AppConfig;
use crate::proto::{sse::Event, ExecRequest};
use crate::session::resolve_shell;
use crate::workspace::{self, Workspace};

pub struct ExecPlan {
    pub program: String,
    pub args: Vec<String>,
    pub cwd: std::path::PathBuf,
    pub env: HashMap<String, String>,
    pub stdin: Option<String>,
    pub timeout_sec: u64,
    pub max_output_bytes: usize,
    pub display: String,
}

pub fn plan(req: &ExecRequest, config: &AppConfig, workspace: &Workspace, session_cwd: Option<std::path::PathBuf>) -> Result<ExecPlan> {
    let display = crate::proto::sse::display_command(req);
    if display.trim().is_empty() {
        anyhow::bail!("command or argv is required");
    }

    let requested = match &req.cwd {
        Some(raw) => Some(workspace::expand(raw)),
        None => session_cwd,
    };

    let cwd = match requested {
        Some(path) => {
            if workspace.restricted() {
                workspace.check(&path.display().to_string())?;
            }
            path
        }
        None => {
            if workspace.restricted() {
                workspace.default_root()?
            } else {
                workspace::home_dir().unwrap_or_else(|| std::path::PathBuf::from("."))
            }
        }
    };

    let (program, args) = match &req.argv {
        Some(argv) if !argv.is_empty() => (argv[0].clone(), argv[1..].to_vec()),
        _ => {
            let shell = resolve_shell(config.default_shell.clone());
            let program = shell.first().cloned().unwrap_or_else(|| "/bin/sh".into());
            let mut args = shell[1..].to_vec();
            let command = req.command.clone().unwrap_or_default();
            if cfg!(windows) {
                args.extend(["-NoProfile".to_string(), "-Command".to_string(), command]);
            } else {
                args.extend(["-c".to_string(), command]);
            }
            (program, args)
        }
    };

    let mut env = HashMap::new();
    if let Some(extra) = &req.env {
        for (k, v) in extra {
            env.insert(k.clone(), v.clone());
        }
    }

    Ok(ExecPlan {
        program,
        args,
        cwd,
        env,
        stdin: req.stdin.clone(),
        timeout_sec: req.timeout_sec.unwrap_or(config.exec_default_timeout),
        max_output_bytes: config.max_output_bytes.max(64 * 1024) as usize,
        display,
    })
}

pub async fn run(plan: ExecPlan, sender: mpsc::UnboundedSender<Event>) -> Result<()> {
    let mut command = Command::new(&plan.program);
    command.args(&plan.args);
    command.current_dir(&plan.cwd);
    command.stdin(if plan.stdin.is_some() {
        std::process::Stdio::piped()
    } else {
        std::process::Stdio::null()
    });
    command.stdout(std::process::Stdio::piped());
    command.stderr(std::process::Stdio::piped());
    for (k, v) in &plan.env {
        command.env(k, v);
    }

    #[cfg(unix)]
    {
        command.process_group(0);
    }

    let mut child = command.spawn()?;
    let pid = child.id().unwrap_or(0);
    let _ = sender.send(Event::Start {
        pid,
        command: plan.display.clone(),
    });

    if let Some(text) = &plan.stdin {
        if let Some(mut stdin) = child.stdin.take() {
            use tokio::io::AsyncWriteExt;
            let payload = text.clone();
            tokio::spawn(async move {
                let _ = stdin.write_all(payload.as_bytes()).await;
                let _ = stdin.shutdown().await;
            });
        }
    }

    let stdout = child.stdout.take();
    let stderr = child.stderr.take();
    let out_tx = sender.clone();
    let err_tx = sender.clone();
    let emitted = std::sync::Arc::new(std::sync::atomic::AtomicUsize::new(0));
    let out_bytes = emitted.clone();
    let out_limit = plan.max_output_bytes;
    let out_truncated = std::sync::Arc::new(std::sync::atomic::AtomicBool::new(false));
    let out_truncated_flag = out_truncated.clone();

    let out_task = tokio::spawn(async move {
        if let Some(mut stream) = stdout {
            let mut buffer = vec![0u8; 8192];
            loop {
                match stream.read(&mut buffer).await {
                    Ok(0) | Err(_) => break,
                    Ok(n) => {
                        let used = out_bytes.load(std::sync::atomic::Ordering::Relaxed);
                        if used >= out_limit {
                            if !out_truncated_flag.swap(true, std::sync::atomic::Ordering::Relaxed) {
                                let _ = out_tx.send(Event::Stderr {
                                    data: "\n[output truncated by bridge]\n".into(),
                                });
                            }
                            continue;
                        }
                        let take = n.min(out_limit - used);
                        out_bytes.fetch_add(take, std::sync::atomic::Ordering::Relaxed);
                        let text = String::from_utf8_lossy(&buffer[..take]).into_owned();
                        if out_tx.send(Event::Stdout { data: text }).is_err() {
                            break;
                        }
                        if take < n && !out_truncated_flag.swap(true, std::sync::atomic::Ordering::Relaxed) {
                            let _ = out_tx.send(Event::Stderr {
                                data: "\n[output truncated by bridge]\n".into(),
                            });
                        }
                    }
                }
            }
        }
    });

    let err_bytes = emitted.clone();
    let err_limit = plan.max_output_bytes;
    let err_truncated = out_truncated.clone();
    let err_task = tokio::spawn(async move {
        if let Some(mut stream) = stderr {
            let mut buffer = vec![0u8; 8192];
            loop {
                match stream.read(&mut buffer).await {
                    Ok(0) | Err(_) => break,
                    Ok(n) => {
                        let used = err_bytes.load(std::sync::atomic::Ordering::Relaxed);
                        if used >= err_limit {
                            if !err_truncated.swap(true, std::sync::atomic::Ordering::Relaxed) {
                                let _ = err_tx.send(Event::Stderr {
                                    data: "\n[output truncated by bridge]\n".into(),
                                });
                            }
                            continue;
                        }
                        let take = n.min(err_limit - used);
                        err_bytes.fetch_add(take, std::sync::atomic::Ordering::Relaxed);
                        let text = String::from_utf8_lossy(&buffer[..take]).into_owned();
                        if err_tx.send(Event::Stderr { data: text }).is_err() {
                            break;
                        }
                        if take < n && !err_truncated.swap(true, std::sync::atomic::Ordering::Relaxed) {
                            let _ = err_tx.send(Event::Stderr {
                                data: "\n[output truncated by bridge]\n".into(),
                            });
                        }
                    }
                }
            }
        }
    });

    let waited = timeout(Duration::from_secs(plan.timeout_sec.max(1)), child.wait()).await;
    let mut timed_out = false;
    let code = match waited {
        Ok(Ok(status)) => status.code().unwrap_or(-1),
        Ok(Err(_)) => -1,
        Err(_) => {
            timed_out = true;
            terminate(&mut child).await;
            -1
        }
    };

    let _ = tokio::join!(out_task, err_task);
    let _ = sender.send(Event::Exit { code, timed_out });
    Ok(())
}

async fn terminate(child: &mut tokio::process::Child) {
    #[cfg(unix)]
    {
        if let Some(pid) = child.id() {
            unsafe {
                libc::kill(-(pid as i32), libc::SIGTERM);
            }
            tokio::time::sleep(Duration::from_millis(800)).await;
            unsafe {
                libc::kill(-(pid as i32), libc::SIGKILL);
            }
        }
    }
    let _ = child.kill().await;
}
