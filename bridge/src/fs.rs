use anyhow::{anyhow, Result};
use serde_json::json;
use std::path::{Path, PathBuf};

use crate::proto::{
    FsEditRequest, FsGrepMatch, FsGrepRequest, FsGlobRequest, FsListEntry, FsMoveRequest,
    FsPathRequest, FsReadRequest, FsWriteRequest,
};
use crate::workspace::{self, Workspace};

const MAX_READ_BYTES: u64 = 512 * 1024;
const MAX_READ_LINES: u32 = 500;
const GREP_LIMIT: usize = 400;
const GLOB_LIMIT: usize = 500;
const GLOB_DEPTH: u32 = 6;
const GREP_DEPTH: u32 = 8;
const MAX_SCAN_BYTES: usize = 1024 * 1024;
const SKIP_DIRS: &[&str] = &[
    "node_modules",
    "target",
    "build",
    ".git",
    ".gradle",
    ".cargo",
    ".m2",
    ".idea",
    ".vscode",
];

fn resolve(raw: &str, workspace: &Workspace) -> Result<PathBuf> {
    let checked = workspace.check(raw)?;
    if !checked.exists() {
        let parent_exists = checked.parent().map(|p| p.exists()).unwrap_or(false);
        if !parent_exists {
            return Err(anyhow!("path does not exist: {raw}"));
        }
    }
    Ok(workspace::real_path(&checked)?)
}

fn resolve_new(raw: &str, workspace: &Workspace) -> Result<PathBuf> {
    let checked = workspace.check(raw)?;
    Ok(workspace::real_path(&checked)?)
}

fn resolve_dir(raw: &str, workspace: &Workspace) -> Result<PathBuf> {
    let target = resolve(raw, workspace)?;
    if !target.is_dir() {
        return Err(anyhow!("not a directory: {}", target.display()));
    }
    Ok(target)
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
    if meta.len() > MAX_READ_BYTES {
        return Err(anyhow!(
            "file too large ({} bytes), read a slice with offset and limit",
            meta.len()
        ));
    }
    let raw = std::fs::read(&target)?;
    let text = String::from_utf8_lossy(&raw).into_owned();
    let lines: Vec<&str> = text.lines().collect();
    let total = lines.len() as u64;
    let start = req.offset.unwrap_or(1).max(1);
    let limit = req.limit.unwrap_or(MAX_READ_LINES).min(MAX_READ_LINES);
    let shown: Vec<String> = lines
        .iter()
        .skip(start as usize - 1)
        .take(limit as usize)
        .enumerate()
        .map(|(i, line)| format!("{:<5} {}", start as usize + i, line))
        .collect();
    let truncated = (start as usize - 1) + shown.len() < lines.len();
    Ok(json!({
        "lines": shown.join("\n"),
        "total_lines": total,
        "start_line": start,
        "truncated": truncated,
        "size": meta.len(),
    }))
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
    walk(&root, depth.clamp(1, GLOB_DEPTH), &mut entries, 1);
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

pub fn glob(req: &FsGlobRequest, workspace: &Workspace) -> Result<Vec<String>> {
    let root = match &req.root {
        Some(root) => workspace.check(root)?,
        None => default_root(workspace)?,
    };
    let matches = glob_matches(&root, &req.pattern, GLOB_DEPTH);
    Ok(matches)
}

fn default_root(workspace: &Workspace) -> Result<PathBuf> {
    workspace.default_root()
}

fn glob_matches(root: &Path, pattern: &str, max_depth: u32) -> Vec<String> {
    let normalized = workspace::normalize(root);
    let expanded = if pattern.starts_with('/') || pattern.starts_with('~') {
        workspace::expand(pattern)
    } else {
        normalized.join(pattern)
    };
    let re = match regex::Regex::new(&glob_to_regex(&expanded.to_string_lossy())) {
        Ok(re) => re,
        Err(e) => {
            tracing::warn!(error = %e, pattern = %expanded.display(), "bad glob pattern");
            return Vec::new();
        }
    };
    let mut found = Vec::new();
    visit(&normalized, &re, &mut found, 0, max_depth);
    found.truncate(GLOB_LIMIT);
    found
}

fn visit(dir: &Path, re: &regex::Regex, out: &mut Vec<String>, depth: u32, max_depth: u32) {
    if out.len() >= GLOB_LIMIT {
        return;
    }
    let read = match std::fs::read_dir(dir) {
        Ok(read) => read,
        Err(_) => return,
    };
    for entry in read.flatten() {
        if out.len() >= GLOB_LIMIT {
            return;
        }
        let path = entry.path();
        let is_dir = entry.file_type().map(|t| t.is_dir()).unwrap_or(false);
        if re.is_match(&path.to_string_lossy()) {
            out.push(path.display().to_string());
        }
        if is_dir && depth < max_depth && !SKIP_DIRS.contains(&entry.file_name().to_string_lossy().as_ref()) {
            visit(&path, re, out, depth + 1, max_depth);
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
                    out.push('!');
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

pub fn grep(
    req: &FsGrepRequest,
    workspace: &Workspace,
) -> Result<Vec<FsGrepMatch>> {
    let root = match &req.root {
        Some(root) => workspace.check(root)?,
        None => default_root(workspace)?,
    };
    let pattern = if req.literal {
        regex::escape(&req.pattern)
    } else {
        req.pattern.clone()
    };
    let re = match regex::Regex::new(&pattern) {
        Ok(re) => re,
        Err(e) => return Err(anyhow!("bad regex pattern: {e}")),
    };
    let include_re = match req.include.as_ref() {
        Some(include) => {
            let expr = include_regex(include)?;
            Some(regex::Regex::new(&expr).map_err(|e| anyhow!("bad include pattern: {e}"))?)
        }
        None => None,
    };
    let mut matches = Vec::new();
    scan(&root, &re, include_re.as_ref(), &mut matches, 1);
    matches.truncate(GREP_LIMIT);
    Ok(matches)
}

fn include_regex(include: &str) -> Result<String> {
    let name = include.trim_matches('/');
    if name.contains('*') || name.contains('?') {
        let re = glob_to_regex(name);
        let stripped = re.trim_start_matches('^').trim_end_matches('$');
        Ok(format!("(^|/){stripped}$"))
    } else {
        Ok(format!("(^|/){}$", regex::escape(name)))
    }
}

fn scan(
    dir: &Path,
    re: &regex::Regex,
    include: Option<&regex::Regex>,
    out: &mut Vec<FsGrepMatch>,
    depth: u32,
) {
    if out.len() >= GREP_LIMIT || depth > GREP_DEPTH {
        return;
    }
    let read = match std::fs::read_dir(dir) {
        Ok(read) => read,
        Err(_) => return,
    };
    for entry in read.flatten() {
        if out.len() >= GREP_LIMIT {
            return;
        }
        let path = entry.path();
        let name = entry.file_name().to_string_lossy().into_owned();
        let is_dir = entry.file_type().map(|t| t.is_dir()).unwrap_or(false);
        if is_dir {
            if SKIP_DIRS.contains(&name.as_str()) {
                continue;
            }
            if name.starts_with('.') && name != ".git" {
                continue;
            }
            scan(&path, re, include, out, depth + 1);
        } else {
            if let Some(include) = include {
                if !include.is_match(&path.to_string_lossy()) {
                    continue;
                }
            }
            let content = match std::fs::read(&path) {
                Ok(bytes) if bytes.len() <= MAX_SCAN_BYTES => {
                    String::from_utf8_lossy(&bytes).into_owned()
                }
                _ => continue,
            };
            if content.contains('\0') {
                continue;
            }
            for (i, line) in content.lines().enumerate() {
                if re.is_match(line) {
                    out.push(FsGrepMatch {
                        path: path.display().to_string(),
                        line: (i + 1) as u32,
                        text: line.chars().take(500).collect(),
                    });
                    if out.len() >= GREP_LIMIT {
                        return;
                    }
                }
            }
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
