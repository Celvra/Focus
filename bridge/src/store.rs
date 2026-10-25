use anyhow::{Context, Result};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::path::Path;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Device {
    pub id: String,
    pub name: String,
    pub token_sha256: String,
    pub created_at: u64,
    pub last_seen: u64,
    #[serde(default)]
    pub workspace_roots: Vec<String>,
}

#[derive(Debug, Default, Clone, Serialize, Deserialize)]
pub struct DeviceStore {
    #[serde(default)]
    devices: Vec<Device>,
}

pub fn hash_token(token: &str) -> String {
    let mut hasher = Sha256::new();
    hasher.update(token.as_bytes());
    hex::encode(hasher.finalize())
}

impl DeviceStore {
    pub fn load(path: &Path) -> Result<Self> {
        if !path.exists() {
            return Ok(Self::default());
        }
        let text = std::fs::read_to_string(path).with_context(|| path.display().to_string())?;
        Ok(serde_json::from_str(&text)?)
    }

    pub fn save(&self, path: &Path) -> Result<()> {
        let text = serde_json::to_string_pretty(self)?;
        let tmp = path.with_extension("json.tmp");
        std::fs::write(&tmp, text)?;
        std::fs::rename(&tmp, path)?;
        Ok(())
    }

    pub fn list(&self) -> &[Device] {
        &self.devices
    }

    pub fn add(&mut self, device: Device) {
        self.devices.retain(|d| d.id != device.id);
        self.devices.push(device);
    }

    pub fn remove(&mut self, id: &str) -> bool {
        let before = self.devices.len();
        self.devices.retain(|d| d.id != id);
        before != self.devices.len()
    }

    pub fn by_token_hash(&self, hash: &str) -> Option<&Device> {
        self.devices.iter().find(|d| d.token_sha256 == hash)
    }

    pub fn by_id(&self, id: &str) -> Option<&Device> {
        self.devices.iter().find(|d| d.id == id)
    }

    pub fn by_id_mut(&mut self, id: &str) -> Option<&mut Device> {
        self.devices.iter_mut().find(|d| d.id == id)
    }
}

pub fn new_token() -> String {
    use rand::RngCore;
    let mut bytes = [0u8; 32];
    rand::rng().fill_bytes(&mut bytes);
    hex::encode(bytes)
}

pub fn new_device_id() -> String {
    use rand::RngCore;
    let mut bytes = [0u8; 8];
    rand::rng().fill_bytes(&mut bytes);
    hex::encode(bytes)
}

pub fn now_secs() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0)
}
