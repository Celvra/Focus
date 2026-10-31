use std::sync::Arc;

use axum::extract::ws::{Message, WebSocket, WebSocketUpgrade};
use axum::extract::{ConnectInfo, Extension, Path, Query, State};
use axum::http::StatusCode;
use axum::response::sse::{Event as SseEvent, Sse};
use axum::response::{IntoResponse, Response};
use axum::routing::{delete, get, post};
use axum::{Json, Router};
use futures_util::{SinkExt, StreamExt};
use serde::Deserialize;
use serde_json::json;
use std::net::SocketAddr;
use tokio::sync::mpsc;
use tokio_stream::wrappers::UnboundedReceiverStream;

use crate::auth::require_device;
use crate::background::{BgIdRequest, BgManager, BgStartRequest};
use crate::exec;
use crate::fs;
use crate::proto::*;
use crate::state::AppState;
use crate::store::{hash_token, new_device_id, new_token, now_secs, Device};
use crate::workspace::Workspace;

pub fn router(state: Arc<AppState>) -> Router {
    let public = Router::new()
        .route("/health", get(health))
        .route("/pair", post(pair));

    let guarded = Router::new()
        .route("/devices", get(list_devices))
        .route("/devices/{id}", delete(revoke_device))
        .route("/workspace", get(get_workspace).post(set_workspace))
        .route("/exec", post(run_exec))
        .route("/sessions", get(list_sessions).post(open_session))
        .route("/sessions/{id}", delete(close_session))
        .route("/sessions/{id}/ws", get(session_ws))
        .route("/sessions/{id}/tail", get(session_tail))
        .route("/sessions/{id}/input", post(session_input))
        .route("/fs/read", post(fs_read))
        .route("/fs/media", post(fs_media))
        .route("/fs/write", post(fs_write))
        .route("/fs/edit", post(fs_edit))
        .route("/fs/list", post(fs_list))
        .route("/fs/glob", post(fs_glob))
        .route("/fs/grep", post(fs_grep))
        .route("/fs/mkdir", post(fs_mkdir))
        .route("/fs/move", post(fs_move))
        .route("/fs/remove", post(fs_remove))
        .route("/bg/start", post(bg_start))
        .route("/bg/logs", get(bg_logs))
        .route("/bg/kill", post(bg_kill))
        .route("/bg/list", get(bg_list))
        .route("/bg/clear", post(bg_clear))
        .route_layer(axum::middleware::from_fn_with_state(
            state.clone(),
            require_device,
        ));

    Router::new()
        .merge(public)
        .merge(guarded)
        .with_state(state)
}

type ApiError = (StatusCode, Json<serde_json::Value>);

fn fail(status: StatusCode, message: impl Into<String>) -> ApiError {
    (status, Json(json!({ "error": message.into() })))
}

fn internal(err: anyhow::Error) -> ApiError {
    fail(StatusCode::BAD_REQUEST, err.to_string())
}

async fn health(State(state): State<Arc<AppState>>) -> Json<HealthInfo> {
    Json(HealthInfo {
        name: state.config.device_name.clone(),
        os: std::env::consts::OS.to_string(),
        version: env!("CARGO_PKG_VERSION").to_string(),
        sessions: state.sessions.count().await,
        default_shell: state
            .config
            .default_shell
            .clone()
            .unwrap_or_else(|| crate::session::shell_label(None)),
        home: crate::state::home_dir().display().to_string(),
        arch: std::env::consts::ARCH.to_string(),
    })
}

async fn pair(
    State(state): State<Arc<AppState>>,
    ConnectInfo(peer): ConnectInfo<SocketAddr>,
    Json(request): Json<PairRequest>,
) -> Result<Json<PairResponse>, ApiError> {
    if request.code.trim().is_empty() {
        return Err(fail(StatusCode::BAD_REQUEST, "pairing code is required"));
    }

    let ip = peer.ip();
    {
        let mut pairing = state.pairing.write().await;
        if pairing.blocked(ip) {
            return Err(fail(
                StatusCode::TOO_MANY_REQUESTS,
                "too many failed attempts, wait a minute",
            ));
        }
        if !pairing.verify(request.code.trim()) {
            pairing.record_failure(ip);
            return Err(fail(StatusCode::UNAUTHORIZED, "pairing code is wrong"));
        }
        pairing.clear_failures(ip);
    }

    let token = new_token();
    let device = Device {
        id: new_device_id(),
        name: if request.device_name.trim().is_empty() {
            "focus".into()
        } else {
            request.device_name.trim().to_string()
        },
        token_sha256: hash_token(&token),
        created_at: now_secs(),
        last_seen: now_secs(),
        workspace_roots: Vec::new(),
    };
    state.save_device(&device).await;
    state.rotate_pairing().await;

    tracing::info!(device = %device.name, "device paired");
    Ok(Json(PairResponse {
        device_id: device.id,
        token,
    }))
}

async fn list_devices(State(state): State<Arc<AppState>>) -> Json<serde_json::Value> {
    let store = state.devices.read().await;
    let devices: Vec<_> = store
        .list()
        .iter()
        .map(|device| {
            json!({
                "id": device.id,
                "name": device.name,
                "created_at": device.created_at,
                "last_seen": device.last_seen,
                "workspace_roots": device.workspace_roots,
            })
        })
        .collect();
    Json(json!({ "devices": devices }))
}

async fn revoke_device(
    State(state): State<Arc<AppState>>,
    Path(id): Path<String>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let removed = state.remove_device(&id).await;
    Ok(Json(json!({ "removed": removed })))
}

async fn get_workspace(Extension(device): Extension<Device>) -> Json<WorkspaceResponse> {
    Json(WorkspaceResponse {
        roots: device.workspace_roots.clone(),
    })
}

async fn set_workspace(
    State(state): State<Arc<AppState>>,
    Extension(device): Extension<Device>,
    Json(payload): Json<WorkspaceRequest>,
) -> Result<Json<WorkspaceResponse>, ApiError> {
    let workspace = Workspace::new(payload.roots).map_err(internal)?;
    let roots = workspace.roots();
    {
        let mut store = state.devices.write().await;
        if let Some(entry) = store.by_id_mut(&device.id) {
            entry.workspace_roots = roots.clone();
        }
    }
    let updated = state.devices.read().await.by_id(&device.id).cloned();
    if let Some(updated) = updated {
        state.save_device(&updated).await;
    }
    Ok(Json(WorkspaceResponse { roots }))
}

fn device_workspace(device: &Device) -> Workspace {
    Workspace::new(device.workspace_roots.clone()).unwrap_or_else(|_| Workspace::unrestricted())
}

async fn run_exec(
    State(state): State<Arc<AppState>>,
    Extension(device): Extension<Device>,
    Json(payload): Json<ExecRequest>,
) -> Result<Sse<impl futures_util::Stream<Item = Result<SseEvent, std::convert::Infallible>>>, ApiError>
{
    let workspace = device_workspace(&device);
    let session_cwd = match &payload.session_id {
        Some(id) => match state.sessions.get(id).await {
            Some(session) => Some(session.cwd().await),
            None => None,
        },
        None => None,
    };
    let plan = exec::plan(&payload, &state.config, &workspace, session_cwd)
        .map_err(|err| fail(StatusCode::BAD_REQUEST, err.to_string()))?;

    let (sender, receiver) = mpsc::unbounded_channel();
    tokio::spawn(async move {
        if let Err(err) = exec::run(plan, sender.clone()).await {
            let _ = sender.send(crate::proto::sse::Event::Error {
                message: err.to_string(),
            });
        }
    });

    let stream = UnboundedReceiverStream::new(receiver).map(|event| {
        Ok(SseEvent::default().data(
            serde_json::to_string(&event).unwrap_or_else(|_| "{}".into()),
        ))
    });
    Ok(Sse::new(stream).keep_alive(axum::response::sse::KeepAlive::default()))
}

async fn list_sessions(State(state): State<Arc<AppState>>) -> Json<serde_json::Value> {
    let sessions = state.sessions.list().await;
    Json(json!({ "sessions": sessions }))
}

async fn open_session(
    State(state): State<Arc<AppState>>,
    Extension(device): Extension<Device>,
    Json(payload): Json<SessionOpenRequest>,
) -> Result<Json<SessionInfo>, ApiError> {
    let workspace = device_workspace(&device);
    if let Some(cwd) = &payload.cwd {
        workspace
            .check(cwd)
            .map_err(|err| fail(StatusCode::BAD_REQUEST, err.to_string()))?;
    }
    let id = new_session_id();
    let cols = if payload.cols == 0 { 100 } else { payload.cols };
    let rows = if payload.rows == 0 { 30 } else { payload.rows };
    let session = state
        .sessions
        .open(id, payload.shell, payload.cwd, payload.name, cols, rows)
        .await
        .map_err(|err| fail(StatusCode::BAD_REQUEST, err.to_string()))?;
    let info = session.info.clone();
    tracing::info!(session = %info.id, "session opened");
    Ok(Json(info))
}

async fn close_session(
    State(state): State<Arc<AppState>>,
    Path(id): Path<String>,
) -> Json<serde_json::Value> {
    let closed = state.sessions.close(&id).await;
    Json(json!({ "closed": closed }))
}

async fn session_tail(
    State(state): State<Arc<AppState>>,
    Path(id): Path<String>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let session = state
        .sessions
        .get(&id)
        .await
        .ok_or_else(|| fail(StatusCode::NOT_FOUND, "session not found"))?;
    let tail = session.tail().await;
    Ok(Json(json!({ "output": tail })))
}

async fn session_input(
    State(state): State<Arc<AppState>>,
    Path(id): Path<String>,
    Json(payload): Json<SessionWriteRequest>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let session = state
        .sessions
        .get(&id)
        .await
        .ok_or_else(|| fail(StatusCode::NOT_FOUND, "session not found"))?;
    if let Some(signal) = payload.signal {
        session
            .signal(&signal)
            .await
            .map_err(|err| fail(StatusCode::BAD_REQUEST, err.to_string()))?;
    }
    if let Some(data) = payload.data {
        session
            .write(data.as_bytes())
            .await
            .map_err(|err| fail(StatusCode::BAD_REQUEST, err.to_string()))?;
    }
    if let (Some(cols), Some(rows)) = (payload.cols, payload.rows) {
        let _ = session.resize(cols, rows).await;
    }
    Ok(Json(json!({ "ok": true })))
}

#[derive(Debug, Deserialize)]
struct WsQuery {
    #[serde(default)]
    cols: Option<u16>,
    #[serde(default)]
    rows: Option<u16>,
}

async fn session_ws(
    State(state): State<Arc<AppState>>,
    Path(id): Path<String>,
    Query(query): Query<WsQuery>,
    upgrade: WebSocketUpgrade,
) -> Result<Response, ApiError> {
    let session = state
        .sessions
        .get(&id)
        .await
        .ok_or_else(|| fail(StatusCode::NOT_FOUND, "session not found"))?;

    if let Some(cols) = query.cols {
        if let Some(rows) = query.rows {
            let _ = session.resize(cols, rows).await;
        }
    }

    Ok(upgrade
        .on_upgrade(move |socket| session_socket(socket, session))
        .into_response())
}

async fn session_socket(socket: WebSocket, session: Arc<crate::session::Session>) {
    let (mut sink, mut stream) = socket.split();
    let mut output = session.subscribe();

    let tail = session.tail().await;
    if !tail.is_empty() {
        let _ = sink.send(Message::Text(tail.into())).await;
    }

    let writer = tokio::spawn(async move {
        loop {
            let chunk = match output.recv().await {
                Ok(chunk) => chunk,
                Err(tokio::sync::broadcast::error::RecvError::Lagged(_)) => continue,
                Err(_) => break,
            };
            let text = String::from_utf8_lossy(&chunk).into_owned();
            if sink.send(Message::Text(text.into())).await.is_err() {
                break;
            }
        }
    });

    while let Some(message) = stream.next().await {
        match message {
            Ok(Message::Text(text)) => {
                let input: SessionInput = match serde_json::from_str(&text) {
                    Ok(input) => input,
                    Err(err) => {
                        tracing::warn!(error = %err, "bad session input");
                        continue;
                    }
                };
                match input.kind.as_str() {
                    "input" => {
                        if let Some(data) = input.data {
                            let _ = session.write(data.as_bytes()).await;
                        }
                    }
                    "resize" => {
                        if let (Some(cols), Some(rows)) = (input.cols, input.rows) {
                            let _ = session.resize(cols, rows).await;
                        }
                    }
                    "signal" => {
                        if let Some(signal) = input.signal {
                            let _ = session.signal(&signal).await;
                        }
                    }
                    other => tracing::warn!(kind = other, "unknown session input"),
                }
            }
            Ok(Message::Binary(bytes)) => {
                let _ = session.write(&bytes).await;
            }
            Ok(Message::Close(_)) | Err(_) => break,
            _ => {}
        }
    }

    writer.abort();
}

fn new_session_id() -> String {
    crate::store::new_device_id()
}

async fn fs_read(
    Extension(device): Extension<Device>,
    Json(payload): Json<FsReadRequest>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let workspace = device_workspace(&device);
    fs::read(&payload, &workspace)
        .map(Json)
        .map_err(|err| fail(StatusCode::BAD_REQUEST, err.to_string()))
}

async fn fs_media(
    Extension(device): Extension<Device>,
    Json(payload): Json<FsMediaRequest>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let workspace = device_workspace(&device);
    fs::media(&payload, &workspace)
        .map(Json)
        .map_err(|err| fail(StatusCode::BAD_REQUEST, err.to_string()))
}

async fn fs_write(
    Extension(device): Extension<Device>,
    Json(payload): Json<FsWriteRequest>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let workspace = device_workspace(&device);
    fs::write(&payload, &workspace)
        .map(Json)
        .map_err(|err| fail(StatusCode::BAD_REQUEST, err.to_string()))
}

async fn fs_edit(
    Extension(device): Extension<Device>,
    Json(payload): Json<FsEditRequest>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let workspace = device_workspace(&device);
    fs::edit(&payload, &workspace)
        .map(Json)
        .map_err(|err| fail(StatusCode::BAD_REQUEST, err.to_string()))
}

async fn fs_list(
    Extension(device): Extension<Device>,
    Json(payload): Json<FsListRequest>,
) -> Result<Json<FsListResponse>, ApiError> {
    let workspace = device_workspace(&device);
    let entries = fs::list(&payload.path, payload.depth.unwrap_or(1), &workspace)
        .map_err(|err| fail(StatusCode::BAD_REQUEST, err.to_string()))?;
    Ok(Json(FsListResponse { entries }))
}

async fn fs_glob(
    Extension(device): Extension<Device>,
    Json(payload): Json<FsGlobRequest>,
) -> Result<Json<FsGlobResponse>, ApiError> {
    let workspace = device_workspace(&device);
    let response = fs::glob(&payload, &workspace)
        .map_err(|err| fail(StatusCode::BAD_REQUEST, err.to_string()))?;
    Ok(Json(response))
}

async fn fs_grep(
    Extension(device): Extension<Device>,
    Json(payload): Json<FsGrepRequest>,
) -> Result<Json<FsGrepResponse>, ApiError> {
    let workspace = device_workspace(&device);
    let response = fs::grep(&payload, &workspace)
        .map_err(|err| fail(StatusCode::BAD_REQUEST, err.to_string()))?;
    Ok(Json(response))
}

async fn fs_mkdir(
    Extension(device): Extension<Device>,
    Json(payload): Json<FsPathRequest>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let workspace = device_workspace(&device);
    fs::mkdir(&payload, &workspace)
        .map(|_| Json(json!({ "created": true })))
        .map_err(|err| fail(StatusCode::BAD_REQUEST, err.to_string()))
}

async fn fs_move(
    Extension(device): Extension<Device>,
    Json(payload): Json<FsMoveRequest>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let workspace = device_workspace(&device);
    fs::move_path(&payload, &workspace)
        .map(|_| Json(json!({ "moved": true })))
        .map_err(|err| fail(StatusCode::BAD_REQUEST, err.to_string()))
}

async fn fs_remove(
    Extension(device): Extension<Device>,
    Json(payload): Json<FsPathRequest>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let workspace = device_workspace(&device);
    fs::remove(&payload, &workspace)
        .map(|_| Json(json!({ "removed": true })))
        .map_err(|err| fail(StatusCode::BAD_REQUEST, err.to_string()))
}

#[derive(Debug, Deserialize)]
struct BgLogsQuery {
    id: String,
    #[serde(default)]
    after: u64,
    #[serde(default = "default_true")]
    follow: bool,
}

fn default_true() -> bool {
    true
}

async fn bg_start(
    State(state): State<Arc<AppState>>,
    Extension(device): Extension<Device>,
    Json(payload): Json<BgStartRequest>,
) -> Result<Json<crate::background::BgJobInfo>, ApiError> {
    let workspace = device_workspace(&device);
    let info = state
        .background
        .start(&payload, &state.config, &workspace)
        .await
        .map_err(|err| fail(StatusCode::BAD_REQUEST, err.to_string()))?;
    Ok(Json(info))
}

async fn bg_logs(
    State(state): State<Arc<AppState>>,
    Query(query): Query<BgLogsQuery>,
) -> Result<Response, ApiError> {
    if !query.follow {
        let (lines, done, info) = state
            .background
            .logs(&query.id, query.after)
            .await
            .ok_or_else(|| fail(StatusCode::NOT_FOUND, "background command not found"))?;
        return Ok(Json(json!({
            "lines": lines,
            "done": done,
            "job": info,
        }))
        .into_response());
    }
    if state.background.get(&query.id).await.is_none() {
        return Err(fail(StatusCode::NOT_FOUND, "background command not found"));
    }
    let manager = state.background.clone();
    let initial = BgStreamState {
        manager,
        id: query.id.clone(),
        after: query.after,
    };
    let stream = futures_util::stream::unfold(initial, |mut state| async move {
        loop {
            match state.manager.logs(&state.id, state.after).await {
                Some((lines, done, info)) => {
                    if !lines.is_empty() {
                        state.after = lines.last().map(|line| line.seq).unwrap_or(state.after);
                        let payload = json!({
                            "lines": lines,
                            "done": done,
                            "job": info,
                        });
                        return Some((Ok::<_, std::convert::Infallible>(SseEvent::default().data(payload.to_string())), state));
                    }
                    if done {
                        let payload = json!({ "lines": [], "done": true, "job": info });
                        return Some((Ok(SseEvent::default().data(payload.to_string())), state));
                    }
                }
                None => return None,
            }
            tokio::time::sleep(std::time::Duration::from_millis(300)).await;
        }
    });
    Ok(Sse::new(stream)
        .keep_alive(axum::response::sse::KeepAlive::default())
        .into_response())
}

struct BgStreamState {
    manager: Arc<BgManager>,
    id: String,
    after: u64,
}

async fn bg_kill(
    State(state): State<Arc<AppState>>,
    Json(payload): Json<BgIdRequest>,
) -> Json<serde_json::Value> {
    Json(json!({ "killed": state.background.kill(&payload.id).await }))
}

async fn bg_list(State(state): State<Arc<AppState>>) -> Json<serde_json::Value> {
    Json(json!({ "jobs": state.background.list().await }))
}

async fn bg_clear(
    State(state): State<Arc<AppState>>,
    Json(payload): Json<Option<BgIdRequest>>,
) -> Json<serde_json::Value> {
    state.background.clear(payload.as_ref().map(|value| value.id.as_str())).await;
    Json(json!({ "cleared": true }))
}
