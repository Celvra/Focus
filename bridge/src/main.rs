mod auth;
mod cli;
mod config;
mod discovery;
mod exec;
mod fs;
mod pairing;
mod proto;
mod routes;
mod session;
mod state;
mod store;
mod workspace;

use std::sync::atomic::AtomicBool;
use std::sync::Arc;

use anyhow::{Context, Result};
use clap::Parser;
use tokio::sync::RwLock;

use crate::cli::{Cli, CommandKind};
use crate::config::{load_config, data_paths, load_devices};
use crate::state::AppState;

#[tokio::main]
async fn main() -> Result<()> {
    tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| tracing_subscriber::EnvFilter::new("focus_bridge=info,warn")),
        )
        .init();

    let cli = Cli::parse();
    match cli.command.unwrap_or(CommandKind::Serve(cli::ServeArgs { port: None, host: None })) {
        CommandKind::Serve(args) => serve(args).await,
        CommandKind::Pair => print_pair_code().await,
        CommandKind::Code => print_pair_code().await,
        CommandKind::Devices => list_devices(),
        CommandKind::Revoke { id } => revoke(&id),
    }
}

async fn serve(args: cli::ServeArgs) -> Result<()> {
    let mut config = load_config()?;
    if let Some(port) = args.port {
        config.port = port;
    }
    if let Some(host) = args.host {
        config.host = host;
    }

    let paths = data_paths()?;
    let devices = Arc::new(RwLock::new(load_devices(&paths)?));
    let pairing = Arc::new(RwLock::new(crate::pairing::PairingState::new()));
    let sessions = Arc::new(crate::session::SessionManager::new(
        crate::state::home_dir(),
        config.session_idle_timeout,
    ));

    let state = Arc::new(AppState::new(config.clone(), paths, devices, pairing, sessions));

    let code = state.pairing_code().await.unwrap_or_default();
    state.write_pairing_code(&code);
    let device_name = config.device_name.clone();
    let port = config.port;
    let host = config.host.clone();

    let stop = Arc::new(AtomicBool::new(false));
    discovery::spawn(device_name.clone(), port, stop.clone());

    let cleaner = state.clone();
    tokio::spawn(async move {
        let mut ticker = tokio::time::interval(std::time::Duration::from_secs(60));
        loop {
            ticker.tick().await;
            cleaner.sessions.sweep_idle().await;
            cleaner.refresh_pairing().await;
        }
    });

    let listener = tokio::net::TcpListener::bind((host.as_str(), port))
        .await
        .with_context(|| format!("bind {host}:{port}"))?;
    let address = listener.local_addr()?;

    tracing::info!(%address, device = %device_name, "bridge listening");
    println!("focus-bridge listening on {address}");
    println!("pairing code: {code}");

    let shutdown = {
        let stop = stop.clone();
        async move {
            let _ = tokio::signal::ctrl_c().await;
            stop.store(true, std::sync::atomic::Ordering::Relaxed);
            tracing::info!("shutting down");
        }
    };

    axum::serve(
        listener,
        routes::router(state)
            .into_make_service_with_connect_info::<std::net::SocketAddr>(),
    )
    .with_graceful_shutdown(shutdown)
    .await?;

    Ok(())
}

async fn print_pair_code() -> Result<()> {
    let paths = data_paths()?;
    match std::fs::read_to_string(&paths.pairing_code) {
        Ok(code) => {
            let trimmed = code.trim();
            if trimmed.is_empty() {
                return Err(anyhow::anyhow!(
                    "no pairing code saved yet, start the bridge with `focus-bridge serve`"
                ));
            }
            println!("pairing code: {trimmed}");
            println!("start the bridge with `focus-bridge serve` to accept it");
        }
        Err(_) => {
            println!("no pairing code saved yet, start the bridge with `focus-bridge serve`");
        }
    }
    Ok(())
}

fn list_devices() -> Result<()> {
    let paths = data_paths()?;
    let store = crate::store::DeviceStore::load(&paths.devices)?;
    if store.list().is_empty() {
        println!("no paired devices");
        return Ok(());
    }
    for device in store.list() {
        println!(
            "{}  {}  last seen {}",
            device.id, device.name, device.last_seen
        );
    }
    Ok(())
}

fn revoke(id: &str) -> Result<()> {
    let paths = data_paths()?;
    let mut store = crate::store::DeviceStore::load(&paths.devices)?;
    if store.remove(id) {
        store.save(&paths.devices)?;
        println!("revoked {id}");
    } else {
        println!("no device with id {id}");
    }
    Ok(())
}
