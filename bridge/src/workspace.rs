use anyhow::{anyhow, Result};
use std::path::{Component, Path, PathBuf};

#[derive(Debug, Clone, Default)]
pub struct Workspace {
    roots: Vec<PathBuf>,
}

impl Workspace {
    pub fn unrestricted() -> Self {
        Self::default()
    }

    pub fn new(roots: Vec<String>) -> Result<Self> {
        let mut resolved = Vec::new();
        for root in roots {
            if root.trim().is_empty() {
                continue;
            }
            let path = PathBuf::from(&root);
            if !path.is_absolute() {
                return Err(anyhow!("workspace root must be absolute: {root}"));
            }
            if !path.exists() {
                return Err(anyhow!("workspace root does not exist: {root}"));
            }
            resolved.push(normalize(&path));
        }
        Ok(Self { roots: resolved })
    }

    pub fn roots(&self) -> Vec<String> {
        self.roots.iter().map(|p| p.display().to_string()).collect()
    }

    pub fn default_root(&self) -> Result<PathBuf> {
        if let Some(first) = self.roots.first() {
            return Ok(first.clone());
        }
        home_dir().ok_or_else(|| anyhow!("no home directory"))
    }

    pub fn restricted(&self) -> bool {
        !self.roots.is_empty()
    }

    pub fn check(&self, raw: &str) -> Result<PathBuf> {
        let path = expand(raw);
        if !path.is_absolute() {
            return Err(anyhow!("path must be absolute: {raw}"));
        }
        let normalized = normalize(&path);
        if self.roots.is_empty() {
            return Ok(normalized);
        }
        let allowed = self
            .roots
            .iter()
            .any(|root| normalized == *root || normalized.starts_with(root));
        if !allowed {
            return Err(anyhow!(
                "path outside workspace: {} (allowed: {})",
                normalized.display(),
                self.roots().join(", ")
            ));
        }
        Ok(normalized)
    }
}

pub fn expand(raw: &str) -> PathBuf {
    if let Some(rest) = raw.strip_prefix("~/") {
        if let Some(home) = home_dir() {
            return home.join(rest);
        }
    }
    if raw == "~" {
        if let Some(home) = home_dir() {
            return home;
        }
    }
    PathBuf::from(raw)
}

pub fn home_dir() -> Option<PathBuf> {
    std::env::var("HOME")
        .ok()
        .or_else(|| std::env::var("USERPROFILE").ok())
        .map(PathBuf::from)
}

pub fn normalize(path: &Path) -> PathBuf {
    let mut out = PathBuf::new();
    for component in path.components() {
        match component {
            Component::CurDir => {}
            Component::ParentDir => {
                out.pop();
            }
            other => out.push(other.as_os_str()),
        }
    }
    out
}

pub fn real_path(path: &Path) -> Result<PathBuf> {
    if path.exists() {
        return Ok(path.canonicalize()?);
    }
    let mut probe = path.to_path_buf();
    let mut missing = Vec::new();
    while !probe.exists() {
        match probe.file_name() {
            Some(name) => missing.push(name.to_os_string()),
            None => break,
        }
        if !probe.pop() {
            break;
        }
    }
    let base = probe.canonicalize().unwrap_or(probe);
    let mut out = base;
    for part in missing.into_iter().rev() {
        out.push(part);
    }
    Ok(out)
}
