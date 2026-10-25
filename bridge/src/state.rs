use std::path::PathBuf;
use std::sync::Arc;

use tokio::sync::RwLock;

use crate::config::{AppConfig, DataPaths};
use crate::pairing::PairingState;
use crate::session::SessionManager;
use crate::store::{Device, DeviceStore};

pub struct AppState {
    pub config: AppConfig,
    pub paths: DataPaths,
    pub devices: Arc<RwLock<DeviceStore>>,
    pub pairing: Arc<RwLock<PairingState>>,
    pub sessions: Arc<SessionManager>,
}

impl AppState {
    pub fn new(
        config: AppConfig,
        paths: DataPaths,
        devices: Arc<RwLock<DeviceStore>>,
        pairing: Arc<RwLock<PairingState>>,
        sessions: Arc<SessionManager>,
    ) -> Self {
        Self {
            config,
            paths,
            devices,
            pairing,
            sessions,
        }
    }

    pub async fn touch_device(&self, device_id: &str) {
        let now = crate::store::now_secs();
        {
            let mut store = self.devices.write().await;
            if let Some(device) = store.by_id_mut(device_id) {
                if now.saturating_sub(device.last_seen) < 60 {
                    return;
                }
                device.last_seen = now;
            } else {
                return;
            }
        }
        self.flush_devices().await;
    }

    pub async fn save_device(&self, device: &Device) {
        self.devices.write().await.add(device.clone());
        self.flush_devices().await;
    }

    pub async fn remove_device(&self, device_id: &str) -> bool {
        let removed = self.devices.write().await.remove(device_id);
        if removed {
            self.flush_devices().await;
        }
        removed
    }

    pub async fn flush_devices(&self) {
        let path = self.paths.devices.clone();
        let snapshot = self.devices.read().await.clone();
        let result = tokio::task::spawn_blocking(move || snapshot.save(&path)).await;
        match result {
            Ok(Ok(())) => {}
            Ok(Err(err)) => tracing::warn!(error = %err, "save devices failed"),
            Err(err) => tracing::warn!(error = %err, "save devices task failed"),
        }
    }

    pub async fn rotate_pairing(&self) -> String {
        let mut state = self.pairing.write().await;
        state.rotate();
        let code = state.code().to_string();
        drop(state);
        self.write_pairing_code(&code);
        code
    }

    pub async fn pairing_code(&self) -> Option<String> {
        let state = self.pairing.read().await;
        if state.expired() {
            None
        } else {
            Some(state.code().to_string())
        }
    }

    pub async fn refresh_pairing(&self) {
        let code = {
            let mut state = self.pairing.write().await;
            if !state.expired() {
                return;
            }
            state.rotate();
            state.code().to_string()
        };
        self.write_pairing_code(&code);
    }

    pub fn write_pairing_code(&self, code: &str) {
        if let Err(err) = std::fs::write(&self.paths.pairing_code, code) {
            tracing::warn!(error = %err, "save pairing code failed");
        }
    }
}

pub fn home_dir() -> PathBuf {
    std::env::var("HOME")
        .or_else(|_| std::env::var("USERPROFILE"))
        .map(PathBuf::from)
        .unwrap_or_else(|_| PathBuf::from("."))
}
