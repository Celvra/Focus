use std::net::UdpSocket;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::Duration;

use crate::config::DISCOVERY_PORT;
use crate::proto::{DiscoverRequest, DiscoverResponse};

pub fn spawn(name: String, port: u16, stop: Arc<AtomicBool>) {
    std::thread::spawn(move || {
        let socket = match UdpSocket::bind(("0.0.0.0", DISCOVERY_PORT)) {
            Ok(socket) => socket,
            Err(err) => {
                tracing::warn!(error = %err, port = DISCOVERY_PORT, "discovery bind failed");
                return;
            }
        };
        socket.set_read_timeout(Some(Duration::from_millis(500))).ok();
        tracing::info!(port = DISCOVERY_PORT, "discovery listening");

        let mut buffer = [0u8; 1024];
        while !stop.load(Ordering::Relaxed) {
            let (len, peer) = match socket.recv_from(&mut buffer) {
                Ok(result) => result,
                Err(err)
                    if matches!(
                        err.kind(),
                        std::io::ErrorKind::WouldBlock | std::io::ErrorKind::TimedOut
                    ) =>
                {
                    continue;
                }
                Err(err) => {
                    tracing::warn!(error = %err, "discovery recv failed");
                    std::thread::sleep(Duration::from_millis(200));
                    continue;
                }
            };

            let text = String::from_utf8_lossy(&buffer[..len]);
            let Ok(request) = serde_json::from_str::<DiscoverRequest>(text.trim()) else {
                continue;
            };
            if !request.is_probe() {
                continue;
            }

            let payload = DiscoverResponse {
                name: name.clone(),
                version: env!("CARGO_PKG_VERSION").to_string(),
                port,
                os: std::env::consts::OS.to_string(),
                arch: std::env::consts::ARCH.to_string(),
            };
            match serde_json::to_vec(&payload) {
                Ok(bytes) => {
                    let _ = socket.send_to(&bytes, peer);
                }
                Err(err) => tracing::warn!(error = %err, "encode discovery response failed"),
            }
        }
        tracing::info!("discovery stopped");
    });
}
