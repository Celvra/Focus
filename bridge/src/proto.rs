use serde::{Deserialize, Serialize};

#[derive(Debug, Serialize)]
pub struct HealthInfo {
    pub name: String,
    pub os: String,
    pub version: String,
    pub sessions: usize,
    pub default_shell: String,
    pub home: String,
    pub arch: String,
}

#[derive(Debug, Deserialize)]
pub struct PairRequest {
    pub code: String,
    #[serde(default)]
    pub device_name: String,
}

#[derive(Debug, Serialize)]
pub struct PairResponse {
    pub device_id: String,
    pub token: String,
}

#[derive(Debug, Deserialize)]
pub struct ExecRequest {
    #[serde(default)]
    pub command: Option<String>,
    #[serde(default)]
    pub argv: Option<Vec<String>>,
    #[serde(default)]
    pub cwd: Option<String>,
    #[serde(default)]
    pub env: Option<std::collections::HashMap<String, String>>,
    #[serde(default)]
    pub stdin: Option<String>,
    #[serde(default)]
    pub timeout_sec: Option<u64>,
    #[serde(default)]
    pub session_id: Option<String>,
}

pub mod sse {
    use super::ExecRequest;

    #[derive(Debug, serde::Serialize)]
    #[serde(tag = "type")]
    pub enum Event {
        #[serde(rename = "start")]
        Start { pid: u32, command: String },
        #[serde(rename = "stdout")]
        Stdout { data: String },
        #[serde(rename = "stderr")]
        Stderr { data: String },
        #[serde(rename = "exit")]
        Exit { code: i32, timed_out: bool },
        #[serde(rename = "error")]
        Error { message: String },
    }

    pub fn display_command(req: &ExecRequest) -> String {
        req.argv
            .as_ref()
            .map(|argv| argv.join(" "))
            .or_else(|| req.command.clone())
            .unwrap_or_default()
    }
}

#[derive(Debug, Deserialize)]
pub struct SessionOpenRequest {
    #[serde(default)]
    pub shell: Option<String>,
    #[serde(default)]
    pub cwd: Option<String>,
    #[serde(default)]
    pub name: Option<String>,
    #[serde(default)]
    pub cols: u16,
    #[serde(default)]
    pub rows: u16,
}

impl Default for SessionOpenRequest {
    fn default() -> Self {
        Self {
            shell: None,
            cwd: None,
            name: None,
            cols: 100,
            rows: 30,
        }
    }
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct SessionInfo {
    pub id: String,
    pub name: String,
    pub shell: String,
    pub cols: u16,
    pub rows: u16,
    pub created_at: u64,
    pub closed: bool,
}

#[derive(Debug, Deserialize)]
pub struct SessionWriteRequest {
    #[serde(default)]
    pub data: Option<String>,
    #[serde(default)]
    pub cols: Option<u16>,
    #[serde(default)]
    pub rows: Option<u16>,
    #[serde(default)]
    pub signal: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct WorkspaceRequest {
    #[serde(default)]
    pub roots: Vec<String>,
}

#[derive(Debug, Serialize)]
pub struct WorkspaceResponse {
    pub roots: Vec<String>,
}

#[derive(Debug, Deserialize)]
pub struct FsReadRequest {
    pub path: String,
    #[serde(default)]
    pub offset: Option<u32>,
    #[serde(default)]
    pub limit: Option<u32>,
}

#[derive(Debug, Deserialize)]
pub struct FsWriteRequest {
    pub path: String,
    #[serde(default)]
    pub content: String,
}

#[derive(Debug, Deserialize)]
pub struct FsEditRequest {
    pub path: String,
    pub old: String,
    pub new: String,
    #[serde(default)]
    pub replace_all: bool,
}

#[derive(Debug, Deserialize)]
pub struct FsListRequest {
    #[serde(default = "default_list_path")]
    pub path: String,
    #[serde(default)]
    pub depth: Option<u32>,
}

fn default_list_path() -> String {
    "~".into()
}

#[derive(Debug, Serialize)]
pub struct FsListEntry {
    pub path: String,
    pub name: String,
    pub is_dir: bool,
    pub size: u64,
}

#[derive(Debug, Serialize)]
pub struct FsListResponse {
    pub entries: Vec<FsListEntry>,
}

#[derive(Debug, Deserialize)]
pub struct FsGlobRequest {
    pub pattern: String,
    #[serde(default)]
    pub root: Option<String>,
}

#[derive(Debug, Serialize)]
pub struct FsGlobResponse {
    pub matches: Vec<String>,
}

#[derive(Debug, Deserialize)]
pub struct FsGrepRequest {
    pub pattern: String,
    #[serde(default)]
    pub root: Option<String>,
    #[serde(default)]
    pub include: Option<String>,
    #[serde(default)]
    pub literal: bool,
}

#[derive(Debug, Serialize)]
pub struct FsGrepMatch {
    pub path: String,
    pub line: u32,
    pub text: String,
}

#[derive(Debug, Serialize)]
pub struct FsGrepResponse {
    pub matches: Vec<FsGrepMatch>,
}

#[derive(Debug, Deserialize)]
pub struct FsPathRequest {
    pub path: String,
}

#[derive(Debug, Deserialize)]
pub struct FsMoveRequest {
    pub from: String,
    pub to: String,
}

pub const DISCOVER_KIND: &str = "focus-bridge-discover";

#[derive(Debug, Deserialize)]
pub struct DiscoverRequest {
    #[serde(default)]
    pub kind: String,
}

impl DiscoverRequest {
    pub fn is_probe(&self) -> bool {
        self.kind == DISCOVER_KIND
    }
}

#[derive(Debug, Serialize)]
pub struct DiscoverResponse {
    pub name: String,
    pub version: String,
    pub port: u16,
    pub os: String,
    pub arch: String,
}

#[derive(Debug, Deserialize)]
pub struct SessionInput {
    #[serde(rename = "type")]
    pub kind: String,
    #[serde(default)]
    pub data: Option<String>,
    #[serde(default)]
    pub cols: Option<u16>,
    #[serde(default)]
    pub rows: Option<u16>,
    #[serde(default)]
    pub signal: Option<String>,
}
