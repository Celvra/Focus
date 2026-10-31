use anyhow::{anyhow, Result};
use base64::Engine;
use serde_json::json;
use std::path::{Path, PathBuf};

use crate::proto::{
    FsEditRequest, FsGrepRequest, FsGlobRequest, FsListEntry, FsMediaRequest, FsMoveRequest,
    FsPathRequest, FsReadRequest, FsWriteRequest,
};
use crate::workspace::{self, Workspace};

const MAX_READ_LINES: u32 = 2000;
const DEFAULT_READ_LINES: u32 = 500;
const MAX_MEDIA_BYTES: u64 = 20 * 1024 * 1024;
const GREP_LIMIT: usize = 400;
const GLOB_LIMIT: usize = 1000;
const MAX_DEPTH: u32 = 64;
const MAX_SCAN_BYTES: usize = 1024 * 1024;
const SKIP_DIRS: &[&str] = &[
    "node_modules",
    "target",
    "build",
    ".git",
    ".gradle",
    ".kotlin",
    ".cargo",
    ".m2",
    ".idea",
    ".vscode",
];

fn resolve_any(raw: &str, workspace: &Workspace) -> Result<PathBuf> {
    let expanded = if raw.starts_with('/') || raw.starts_with('~') {
        workspace::expand(raw)
    } else {
        workspace.default_root()?.join(raw)
    };
    workspace.check(&expanded.display().to_string())
}

fn resolve(raw: &str, workspace: &Workspace) -> Result<PathBuf> {
    let checked = resolve_any(raw, workspace)?;
    if !checked.exists() {
        let parent_exists = checked.parent().map(|p| p.exists()).unwrap_or(false);
        if !parent_exists {
            return Err(anyhow!("path does not exist: {raw}"));
        }
    }
    Ok(workspace::real_path(&checked)?)
}

fn resolve_new(raw: &str, workspace: &Workspace) -> Result<PathBuf> {
    let checked = resolve_any(raw, workspace)?;
    Ok(workspace::real_path(&checked)?)
}

fn resolve_dir(raw: &str, workspace: &Workspace) -> Result<PathBuf> {
    let target = resolve(raw, workspace)?;
    if !target.is_dir() {
        return Err(anyhow!("not a directory: {}", target.display()));
    }
    Ok(target)
}

fn relative_to_root(path: &Path, root: &Path) -> String {
    path.strip_prefix(root)
        .map(|value| value.to_string_lossy().replace('\\', "/"))
        .unwrap_or_else(|_| path.to_string_lossy().to_string())
}

fn workspace_root(workspace: &Workspace) -> Result<PathBuf> {
    workspace.default_root()
}

pub fn read(
    req: &FsReadRequest,
    workspace: &Workspace,
) -> Result<serde_json::Value> {
    let target = resolve(&req.path, workspace)?;
    let meta = std::fs::metadata(&target)?;
    if meta.is_dir() {
        return Err(anyhow!("not a file: {}", target.display()));
    }
    let start = req.offset.unwrap_or(1).max(1);
    let limit = req
        .limit
        .unwrap_or(DEFAULT_READ_LINES)
        .clamp(1, MAX_READ_LINES);
    let file = std::fs::File::open(&target)?;
    let reader = std::io::BufReader::new(file);
    let mut shown: Vec<String> = Vec::new();
    let mut total: u64 = 0;
    for line in std::io::BufRead::lines(reader) {
        total += 1;
        if total < start as u64 || shown.len() >= limit as usize {
            continue;
        }
        let text = line.unwrap_or_default();
        shown.push(format!("{:<5} {}", total, text));
    }
    let next = (start as usize - 1) + shown.len();
    let truncated = next < total as usize;
    let end = (start + shown.len().saturating_sub(1) as u32).max(start);
    let notice = if truncated {
        format!(
            "Showing lines {start}-{end} of {total}. Call again with offset={} to continue.",
            next + 1
        )
    } else {
        String::new()
    };
    Ok(json!({
        "lines": shown.join("\n"),
        "content": shown.iter().map(|line| line.get(6..).unwrap_or(line)).collect::<Vec<_>>().join("\n"),
        "total_lines": total,
        "start_line": start,
        "end_line": end,
        "next_offset": if truncated { json!(next + 1) } else { json!(null) },
        "truncated": truncated,
        "notice": notice,
        "size": meta.len(),
    }))
}

pub fn media(
    req: &FsMediaRequest,
    workspace: &Workspace,
) -> Result<serde_json::Value> {
    let target = resolve(&req.path, workspace)?;
    let meta = std::fs::metadata(&target)?;
    if meta.is_dir() {
        return Err(anyhow!("not a file: {}", target.display()));
    }
    let limit = req.max_bytes.unwrap_or(MAX_MEDIA_BYTES).clamp(1, MAX_MEDIA_BYTES);
    if meta.len() > limit {
        return Err(anyhow!(
            "file is {} bytes, media limit is {} bytes",
            meta.len(),
            limit
        ));
    }
    let bytes = std::fs::read(&target)?;
    let mime = media_mime(&target, &bytes);
    let encoded = base64::engine::general_purpose::STANDARD.encode(&bytes);
    Ok(json!({
        "path": target.display().to_string(),
        "name": target.file_name().map(|name| name.to_string_lossy().into_owned()).unwrap_or_default(),
        "mime": mime,
        "size": bytes.len(),
        "base64": encoded,
    }))
}

fn media_mime(path: &Path, bytes: &[u8]) -> &'static str {
    if bytes.starts_with(&[0xFF, 0xD8, 0xFF]) {
        return "image/jpeg";
    }
    if bytes.starts_with(b"\x89PNG\r\n\x1a\n") {
        return "image/png";
    }
    if bytes.starts_with(b"GIF87a") || bytes.starts_with(b"GIF89a") {
        return "image/gif";
    }
    if bytes.len() > 12 && &bytes[0..4] == b"RIFF" && &bytes[8..12] == b"WEBP" {
        return "image/webp";
    }
    if bytes.len() > 12 && &bytes[4..8] == b"ftyp" {
        let brand = &bytes[8..12];
        if brand == b"heic" || brand == b"heix" || brand == b"hevc" || brand == b"mif1" {
            return "image/heic";
        }
        if brand == b"avif" {
            return "image/avif";
        }
        if brand == b"qt  " {
            return "video/quicktime";
        }
        return "video/mp4";
    }
    if bytes.starts_with(&[0x1A, 0x45, 0xDF, 0xA3]) {
        return "video/webm";
    }
    if bytes.len() > 12 && &bytes[0..4] == b"RIFF" && &bytes[8..12] == b"AVI " {
        return "video/x-msvideo";
    }
    if bytes.starts_with(b"BM") {
        return "image/bmp";
    }
    if bytes.starts_with(b"II*\0") || bytes.starts_with(b"MM\0*") {
        return "image/tiff";
    }
    if bytes.starts_with(b"fLaC") {
        return "audio/flac";
    }
    if bytes.starts_with(b"OggS") {
        return "audio/ogg";
    }
    if bytes.starts_with(b"ID3") || bytes.starts_with(&[0xFF, 0xFB]) {
        return "audio/mpeg";
    }
    if bytes.len() > 12 && &bytes[0..4] == b"RIFF" && &bytes[8..12] == b"WAVE" {
        return "audio/wav";
    }
    match path
        .extension()
        .and_then(|value| value.to_str())
        .unwrap_or_default()
        .to_ascii_lowercase()
        .as_str()
    {
        "jpg" | "jpeg" => "image/jpeg",
        "png" => "image/png",
        "gif" => "image/gif",
        "webp" => "image/webp",
        "bmp" => "image/bmp",
        "heic" | "heif" => "image/heic",
        "avif" => "image/avif",
        "tif" | "tiff" => "image/tiff",
        "mp4" | "m4v" => "video/mp4",
        "mov" => "video/quicktime",
        "webm" => "video/webm",
        "mkv" => "video/x-matroska",
        "avi" => "video/x-msvideo",
        "3gp" => "video/3gpp",
        "mp3" => "audio/mpeg",
        "wav" => "audio/wav",
        "ogg" => "audio/ogg",
        "flac" => "audio/flac",
        _ => "application/octet-stream",
    }
}

pub fn write(
    req: &FsWriteRequest,
    workspace: &Workspace,
) -> Result<serde_json::Value> {
    let target = resolve_new(&req.path, workspace)?;
    if let Some(parent) = target.parent() {
        std::fs::create_dir_all(parent)?;
    }
    std::fs::write(&target, &req.content)?;
    Ok(json!({ "written": true, "bytes": req.content.len(), "path": target.display().to_string() }))
}

pub fn edit(
    req: &FsEditRequest,
    workspace: &Workspace,
) -> Result<serde_json::Value> {
    let target = resolve(&req.path, workspace)?;
    let original = std::fs::read_to_string(&target)?;
    let occurrences = original.matches(&req.old).count();
    if occurrences == 0 {
        return Err(anyhow!("old text not found in {}", target.display()));
    }
    if occurrences > 1 && !req.replace_all {
        return Err(anyhow!(
            "old text matches {occurrences} times, add more context or set replace_all"
        ));
    }
    let updated = if req.replace_all {
        original.replace(&req.old, &req.new)
    } else {
        original.replacen(&req.old, &req.new, 1)
    };
    std::fs::write(&target, &updated)?;
    let applied = if req.replace_all { occurrences } else { 1 };
    Ok(json!({ "replacements": applied, "path": target.display().to_string() }))
}

pub fn list(
    path: &str,
    depth: u32,
    workspace: &Workspace,
) -> Result<Vec<FsListEntry>> {
    let root = resolve_dir(path, workspace)?;
    let mut entries = Vec::new();
    walk(&root, depth.clamp(1, 8), &mut entries, 1);
    Ok(entries)
}

fn walk(dir: &Path, depth: u32, out: &mut Vec<FsListEntry>, level: u32) {
    let read = match std::fs::read_dir(dir) {
        Ok(read) => read,
        Err(_) => return,
    };
    for entry in read.flatten() {
        let path = entry.path();
        let meta = entry.metadata().ok();
        let is_dir = meta.as_ref().map(|m| m.is_dir()).unwrap_or(false);
        out.push(FsListEntry {
            path: path.display().to_string(),
            name: entry.file_name().to_string_lossy().into_owned(),
            is_dir,
            size: meta.as_ref().map(|m| m.len()).unwrap_or(0),
        });
        if is_dir && level < depth {
            walk(&path, depth, out, level + 1);
        }
    }
}

pub fn glob(req: &FsGlobRequest, workspace: &Workspace) -> Result<crate::proto::FsGlobResponse> {
    let root = match &req.root {
        Some(root) => resolve_dir(root, workspace)?,
        None => workspace_root(workspace)?,
    };
    let root = workspace::real_path(&root)?;
    let pattern = req.pattern.trim().trim_start_matches('/');
    let re = match regex::Regex::new(&glob_to_regex(pattern)) {
        Ok(re) => re,
        Err(e) => return Err(anyhow!("bad glob pattern: {e}")),
    };
    let mut found = Vec::new();
    let mut truncated = false;
    let base = workspace_root(workspace)?;
    visit(&root, &base, &re, &mut found, &mut truncated, 0);
    found.sort();
    Ok(crate::proto::FsGlobResponse {
        truncated,
        root: relative_to_root(&root, &base),
        matches: found,
    })
}

fn visit(
    dir: &Path,
    base: &Path,
    re: &regex::Regex,
    out: &mut Vec<String>,
    truncated: &mut bool,
    depth: u32,
) {
    if out.len() >= GLOB_LIMIT {
        *truncated = true;
        return;
    }
    if depth > MAX_DEPTH {
        *truncated = true;
        return;
    }
    let read = match std::fs::read_dir(dir) {
        Ok(read) => read,
        Err(_) => return,
    };
    for entry in read.flatten() {
        if out.len() >= GLOB_LIMIT {
            *truncated = true;
            return;
        }
        let path = entry.path();
        let file_type = match entry.file_type() {
            Ok(value) => value,
            Err(_) => continue,
        };
        let name = entry.file_name().to_string_lossy().into_owned();
        if file_type.is_dir() && SKIP_DIRS.contains(&name.as_str()) {
            continue;
        }
        let relative = relative_to_root(&path, base);
        if re.is_match(&relative) {
            out.push(relative);
        }
        if file_type.is_dir() && !file_type.is_symlink() {
            visit(&path, base, re, out, truncated, depth + 1);
        }
    }
}

fn glob_to_regex(pattern: &str) -> String {
    let mut out = String::with_capacity(pattern.len() * 2);
    let chars: Vec<char> = pattern.chars().collect();
    let mut i = 0;
    while i < chars.len() {
        let c = chars[i];
        match c {
            '*' => {
                if chars.get(i + 1) == Some(&'*') {
                    if chars.get(i + 2) == Some(&'/') {
                        out.push_str("(?:.*/)?");
                        i += 3;
                    } else {
                        out.push_str(".*");
                        i += 2;
                    }
                } else {
                    out.push_str("[^/]*");
                    i += 1;
                }
            }
            '?' => {
                out.push_str("[^/]");
                i += 1;
            }
            '[' => {
                out.push('[');
                i += 1;
                if chars.get(i) == Some(&'^') {
                    out.push('^');
                    i += 1;
                }
                if chars.get(i) == Some(&']') {
                    out.push(']');
                    i += 1;
                }
                while i < chars.len() && chars[i] != ']' {
                    let ch = chars[i];
                    match ch {
                        '.' | '+' | '(' | ')' | '{' | '}' | '|' | '\\' | '$' | '^' | '*' => {
                            out.push('\\');
                            out.push(ch);
                        }
                        _ => out.push(ch),
                    }
                    i += 1;
                }
                if i < chars.len() {
                    out.push(']');
                    i += 1;
                }
            }
            c => {
                match c {
                    '.' | '+' | '(' | ')' | '{' | '}' | '|' | '\\' | '$' | '^' => {
                        out.push('\\');
                        out.push(c);
                    }
                    _ => out.push(c),
                }
                i += 1;
            }
        }
    }
    format!("^{out}$")
}

pub fn grep(req: &FsGrepRequest, workspace: &Workspace) -> Result<crate::proto::FsGrepResponse> {
    let root = match &req.root {
        Some(root) => resolve_dir(root, workspace)?,
        None => workspace_root(workspace)?,
    };
    let root = workspace::real_path(&root)?;
    let pattern = if req.literal {
        regex::escape(&req.pattern)
    } else {
        req.pattern.clone()
    };
    let pattern = if req.case_sensitive {
        pattern
    } else {
        format!("(?i){pattern}")
    };
    let re = match regex::Regex::new(&pattern) {
        Ok(re) => re,
        Err(e) => return Err(anyhow!("bad regex pattern: {e}")),
    };
    let include_re = match req.include.as_ref() {
        Some(include) => Some(regex::Regex::new(&include_regex(include)?)?),
        None => None,
    };
    let exclude_re = match req.exclude.as_ref() {
        Some(exclude) => Some(regex::Regex::new(&include_regex(exclude)?)?),
        None => None,
    };
    let mut matches = Vec::new();
    let mut truncated = false;
    let limit = req.limit.unwrap_or(GREP_LIMIT as u32).clamp(1, GREP_LIMIT as u32) as usize;
    scan(
        &root,
        &root,
        &re,
        include_re.as_ref(),
        exclude_re.as_ref(),
        req.context.unwrap_or(0).min(5) as usize,
        &mut matches,
        &mut truncated,
        limit,
        0,
    );
    matches.truncate(limit);
    Ok(crate::proto::FsGrepResponse {
        truncated,
        root: relative_to_root(&root, &workspace_root(workspace)?),
        matches,
    })
}

fn include_regex(include: &str) -> Result<String> {
    let name = include.trim().trim_start_matches("**/").trim_matches('/');
    if name.contains('*') || name.contains('?') {
        let re = glob_to_regex(name);
        let stripped = re.trim_start_matches('^').trim_end_matches('$');
        Ok(format!("(^|/){stripped}$"))
    } else {
        Ok(format!("(^|/){}$", regex::escape(name)))
    }
}

fn scan(
    root: &Path,
    dir: &Path,
    re: &regex::Regex,
    include: Option<&regex::Regex>,
    exclude: Option<&regex::Regex>,
    context: usize,
    out: &mut Vec<crate::proto::FsGrepMatch>,
    truncated: &mut bool,
    limit: usize,
    depth: u32,
) {
    if out.len() >= limit || depth > MAX_DEPTH {
        *truncated = true;
        return;
    }
    let read = match std::fs::read_dir(dir) {
        Ok(read) => read,
        Err(_) => return,
    };
    for entry in read.flatten() {
        if out.len() >= limit {
            *truncated = true;
            return;
        }
        let path = entry.path();
        let name = entry.file_name().to_string_lossy().into_owned();
        let file_type = match entry.file_type() {
            Ok(value) => value,
            Err(_) => continue,
        };
        if file_type.is_dir() {
            if SKIP_DIRS.contains(&name.as_str()) || file_type.is_symlink() {
                continue;
            }
            scan(
                root,
                &path,
                re,
                include,
                exclude,
                context,
                out,
                truncated,
                limit,
                depth + 1,
            );
        } else {
            let relative = relative_to_root(&path, root);
            if let Some(include) = include {
                if !include.is_match(&relative) {
                    continue;
                }
            }
            if let Some(exclude) = exclude {
                if exclude.is_match(&relative) {
                    continue;
                }
            }
            let content = match std::fs::read(&path) {
                Ok(bytes) if bytes.len() <= MAX_SCAN_BYTES => String::from_utf8_lossy(&bytes).into_owned(),
                _ => continue,
            };
            if content.contains('\0') {
                continue;
            }
            let lines: Vec<&str> = content.lines().collect();
            for (i, line) in lines.iter().enumerate() {
                if let Some(found) = re.find(line) {
                    out.push(crate::proto::FsGrepMatch {
                        path: relative.clone(),
                        line: (i + 1) as u32,
                        column: (found.start() + 1) as u32,
                        text: line.chars().take(500).collect(),
                        before: if context > 0 {
                            lines[i.saturating_sub(context)..i].iter().map(|value| value.to_string()).collect()
                        } else {
                            Vec::new()
                        },
                        after: if context > 0 {
                            lines[(i + 1).min(lines.len())..(i + 1 + context).min(lines.len())]
                                .iter()
                                .map(|value| value.to_string())
                                .collect()
                        } else {
                            Vec::new()
                        },
                    });
                    if out.len() >= limit {
                        *truncated = true;
                        return;
                    }
                }
            }
            let _ = context;
        }
    }
}

pub fn mkdir(
    req: &FsPathRequest,
    workspace: &Workspace,
) -> Result<()> {
    let target = resolve_new(&req.path, workspace)?;
    std::fs::create_dir_all(&target)?;
    Ok(())
}

pub fn move_path(
    req: &FsMoveRequest,
    workspace: &Workspace,
) -> Result<()> {
    let from = resolve(&req.from, workspace)?;
    let to = resolve(&req.to, workspace)?;
    if !from.exists() {
        return Err(anyhow!("source does not exist: {}", req.from));
    }
    if from.starts_with(&to) {
        return Err(anyhow!("cannot move a directory into itself"));
    }
    if let Some(parent) = to.parent() {
        std::fs::create_dir_all(parent)?;
    }
    match std::fs::rename(&from, &to) {
        Ok(()) => Ok(()),
        Err(_) => {
            copy_recursive(&from, &to)?;
            std::fs::remove_dir_all(&from).ok();
            Ok(())
        }
    }
}

fn copy_recursive(src: &Path, dst: &Path) -> Result<()> {
    let meta = std::fs::metadata(src)?;
    if meta.is_dir() {
        std::fs::create_dir_all(dst)?;
        for entry in std::fs::read_dir(src)? {
            let entry = entry?;
            copy_recursive(&entry.path(), &dst.join(entry.file_name()))?;
        }
    } else {
        if let Some(parent) = dst.parent() {
            std::fs::create_dir_all(parent)?;
        }
        std::fs::copy(src, dst)?;
    }
    Ok(())
}

pub fn remove(
    req: &FsPathRequest,
    workspace: &Workspace,
) -> Result<()> {
    let target = resolve(&req.path, workspace)?;
    if !target.exists() {
        return Err(anyhow!("path does not exist: {}", req.path));
    }
    if target.is_dir() {
        std::fs::remove_dir_all(&target)?;
    } else {
        std::fs::remove_file(&target)?;
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::proto::{FsGlobRequest, FsGrepRequest, FsReadRequest};
    use std::fs;

    fn fixture(name: &str) -> (std::path::PathBuf, Workspace) {
        let base = std::env::temp_dir().join(format!(
            "focus-test-{name}-{}-{}",
            std::process::id(),
            rand::random::<u64>()
        ));
        fs::create_dir_all(&base).unwrap();
        let workspace = Workspace::new(vec![base.display().to_string()]).unwrap();
        (base, workspace)
    }

    #[test]
    fn glob_respects_root_and_skips_build_outputs() {
        let (base, workspace) = fixture("glob");
        fs::create_dir_all(base.join("feature/onboarding")).unwrap();
        fs::create_dir_all(base.join("build-logic/src")).unwrap();
        fs::create_dir_all(base.join("app/src")).unwrap();
        fs::write(base.join("feature/onboarding/Screen.kt"), "class Screen").unwrap();
        fs::write(base.join("build-logic/src/Plugin.kt"), "class Plugin").unwrap();
        fs::write(base.join("app/src/Main.kt"), "class Main").unwrap();
        let result = glob(
            &FsGlobRequest {
                pattern: "**/*.kt".into(),
                root: Some("feature/onboarding".into()),
            },
            &workspace,
        )
        .unwrap();
        assert_eq!(result.matches, vec!["feature/onboarding/Screen.kt"]);
        assert!(!result.matches.iter().any(|path| path.contains("build-logic")));
        let _ = fs::remove_dir_all(base);
    }

    #[test]
    fn grep_finds_deep_files() {
        let (base, workspace) = fixture("grep");
        let deep = base.join("a/b/c/d/e/f/g/h/i");
        fs::create_dir_all(&deep).unwrap();
        fs::write(deep.join("Engine.kt"), "class ChatEngine\nclass ChatTurnRunner\n").unwrap();
        let result = grep(
            &FsGrepRequest {
                pattern: "ChatTurnRunner|ChatEngine".into(),
                root: Some("a".into()),
                include: Some("*.kt".into()),
                exclude: None,
                literal: false,
                case_sensitive: true,
                context: Some(0),
                limit: None,
            },
            &workspace,
        )
        .unwrap();
        assert_eq!(result.matches.len(), 2);
        assert!(result.matches.iter().all(|item| item.path.ends_with("Engine.kt")));
        assert!(result.matches.iter().any(|item| item.text.contains("ChatEngine")));
        assert!(result.matches.iter().any(|item| item.text.contains("ChatTurnRunner")));
        let _ = fs::remove_dir_all(base);
    }

    #[test]
    fn read_reports_truncation_and_offset() {
        let (base, workspace) = fixture("read");
        let path = base.join("large.txt");
        let body = (1..=600).map(|index| format!("line {index}")).collect::<Vec<_>>().join("\n");
        fs::write(&path, body).unwrap();
        let result = read(
            &FsReadRequest {
                path: path.display().to_string(),
                offset: Some(1),
                limit: Some(100),
            },
            &workspace,
        )
        .unwrap();
        assert_eq!(result["total_lines"], 600);
        assert_eq!(result["truncated"], true);
        assert_eq!(result["next_offset"], 101);
        assert_eq!(result["start_line"], 1);
        assert_eq!(result["end_line"], 100);
        assert!(result["notice"].as_str().unwrap().contains("offset=101"));
        let _ = fs::remove_dir_all(base);
    }

    #[tokio::test]
    async fn run_command_captures_stdout_and_stderr() {
        let request = crate::proto::ExecRequest {
            command: Some("echo stdout-value; echo stderr-value >&2; exit 3".into()),
            argv: None,
            cwd: None,
            env: None,
            stdin: None,
            timeout_sec: Some(10),
            session_id: None,
        };
        let plan = crate::exec::plan(
            &request,
            &crate::config::AppConfig::default(),
            &Workspace::unrestricted(),
            None,
        )
        .unwrap();
        let (sender, mut receiver) = tokio::sync::mpsc::unbounded_channel();
        crate::exec::run(plan, sender).await.unwrap();
        let mut stdout = String::new();
        let mut stderr = String::new();
        let mut code = None;
        while let Some(event) = receiver.recv().await {
            match event {
                crate::proto::sse::Event::Stdout { data } => stdout.push_str(&data),
                crate::proto::sse::Event::Stderr { data } => stderr.push_str(&data),
                crate::proto::sse::Event::Exit { code: value, .. } => code = Some(value),
                _ => {}
            }
        }
        assert!(stdout.contains("stdout-value"));
        assert!(stderr.contains("stderr-value"));
        assert_eq!(code, Some(3));
    }
}
