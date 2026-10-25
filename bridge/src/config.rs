use anyhow::{Context, Result};
use std::path::PathBuf;

pub const DEFAULT_PORT: u16 = 7788;
pub const DISCOVERY_PORT: u16 = 7789;

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct AppConfig {
    pub host: String,
    pub port: u16,
    pub device_name: String,
    pub default_shell: Option<String>,
    pub session_idle_timeout: u64,
    pub exec_default_timeout: u64,
    pub max_output_bytes: u64,
}

impl Default for AppConfig {
    fn default() -> Self {
        Self {
            host: "0.0.0.0".into(),
            port: DEFAULT_PORT,
            device_name: default_device_name(),
            default_shell: None,
            session_idle_timeout: 1800,
            exec_default_timeout: 120,
            max_output_bytes: 1_048_576,
        }
    }
}

fn default_device_name() -> String {
    std::env::var("HOSTNAME")
        .or_else(|_| std::env::var("COMPUTERNAME"))
        .unwrap_or_else(|_| "focus-bridge".into())
}

#[derive(Debug, Clone, Default)]
pub struct DataPaths {
    pub devices: PathBuf,
    pub pairing_code: PathBuf,
}

fn config_home() -> Result<PathBuf> {
    if let Ok(path) = std::env::var("FOCUS_BRIDGE_HOME") {
        return Ok(PathBuf::from(path));
    }
    if let Ok(home) = std::env::var("HOME") {
        return Ok(PathBuf::from(home).join(".focus-bridge"));
    }
    if let Ok(profile) = std::env::var("USERPROFILE") {
        return Ok(PathBuf::from(profile).join(".focus-bridge"));
    }
    Err(anyhow::anyhow!("no home directory"))
}

pub fn config_path() -> Result<PathBuf> {
    Ok(config_home()?.join("config.toml"))
}

pub fn data_paths() -> Result<DataPaths> {
    let home = config_home()?;
    std::fs::create_dir_all(&home).context("create bridge data dir")?;
    Ok(DataPaths {
        devices: home.join("devices.json"),
        pairing_code: home.join("pairing-code"),
    })
}

pub fn load_config() -> Result<AppConfig> {
    let path = config_path()?;
    if !path.exists() {
        let cfg = AppConfig::default();
        std::fs::create_dir_all(path.parent().unwrap()).context("create config dir")?;
        let text = toml::to_string(&cfg)?;
        std::fs::write(&path, text).context("write default config")?;
        return Ok(cfg);
    }
    let text = std::fs::read_to_string(&path).with_context(|| path.display().to_string())?;
    Ok(toml::from_str(&text)?)
}

pub fn load_devices(paths: &DataPaths) -> Result<crate::store::DeviceStore> {
    crate::store::DeviceStore::load(&paths.devices)
}
