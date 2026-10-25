use axum::extract::{Request, State};
use axum::http::header::AUTHORIZATION;
use axum::http::StatusCode;
use axum::middleware::Next;
use axum::response::Response;
use std::sync::Arc;

use crate::state::AppState;
use crate::store::hash_token;

pub async fn require_device(
    State(state): State<Arc<AppState>>,
    request: Request,
    next: Next,
) -> Result<Response, StatusCode> {
    let token = request
        .headers()
        .get(AUTHORIZATION)
        .and_then(|value| value.to_str().ok())
        .and_then(|value| value.strip_prefix("Bearer "))
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .ok_or(StatusCode::UNAUTHORIZED)?;

    let hash = hash_token(token);
    let device = {
        let devices = state.devices.read().await;
        devices.by_token_hash(&hash).cloned()
    };
    let device = device.ok_or(StatusCode::UNAUTHORIZED)?;

    state.touch_device(&device.id).await;

    let mut request = request;
    request.extensions_mut().insert(device);
    Ok(next.run(request).await)
}
