# focus-bridge

Runs on your computer. Focus pairs over the local network and then runs shell commands, keeps persistent terminals, and reads or writes files.

## Build

```sh
cd bridge
cargo build --release
```

The binary lands in `target/release/focus-bridge`. Debug builds are enough while working on it.

## Run

```sh
focus-bridge serve
```

It listens on `0.0.0.0:7788` and prints a six digit pairing code. The same code is written to `pairing-code` in the data directory, so `focus-bridge pair` shows the current one at any time.

Options:

```sh
focus-bridge serve --port 8080 --host 192.168.1.20
```

- `--port` overrides the HTTP port, default 7788.
- `--host` overrides the bind address, default `0.0.0.0`.

The discovery responder uses UDP port 7789 so the phone can find the computer without typing an IP.

## Commands

| Command | What it does |
| --- | --- |
| `serve` | Start the bridge. This is the default when no command is given. |
| `pair` | Print the current pairing code. |
| `devices` | List paired devices with id, name, and last seen time. |
| `revoke <id>` | Remove one paired device and invalidate its token. |

## Files

Everything lives in `~/.focus-bridge/` unless `FOCUS_BRIDGE_HOME` points somewhere else.

| File | Contents |
| --- | --- |
| `config.toml` | Host, port, device name, shell, timeouts. Created on first run. |
| `devices.json` | Paired devices and the SHA-256 of their tokens. |
| `pairing-code` | Code the running bridge currently accepts. |

Tokens are stored hashed. The plaintext token only exists on the phone after pairing.

## Pairing

1. Start `focus-bridge serve` on the computer.
2. Open Focus, go to Terminal, and either scan the local network or type the IP and port.
3. Enter the six digit code.

The code is valid for five minutes, works once, and rotates after a successful pairing. Three wrong attempts from the same address within a minute return HTTP 429. The phone and the computer must be on the same network.

## HTTP API

All routes except `/health` and `/pair` need `Authorization: Bearer <token>`.

| Route | Purpose |
| --- | --- |
| `GET /health` | Name, OS, version, session count, default shell, home. |
| `POST /pair` | Exchange a pairing code for a device id and token. |
| `GET /devices` | List paired devices. |
| `DELETE /devices/{id}` | Revoke a device. |
| `GET /workspace` | Read the allowed workspace roots. |
| `POST /workspace` | Set the allowed roots. An empty list means no limit. |
| `POST /exec` | Run one command, streaming `start`, `stdout`, `stderr`, `exit`, `error` events. |
| `GET /sessions` | List open terminal sessions. |
| `POST /sessions` | Open a persistent terminal session. |
| `DELETE /sessions/{id}` | Close a session. |
| `GET /sessions/{id}/ws` | Attach a WebSocket for interactive input and output. |
| `GET /sessions/{id}/tail` | Read recent output without a socket. |
| `POST /sessions/{id}/input` | Send text, resize, or a signal without a socket. |
| `POST /fs/read` | Read a text file, optionally by line range. |
| `POST /fs/media` | Read an image or video as base64, up to 20 MB. |
| `POST /fs/write` | Write a whole file. |
| `POST /fs/edit` | Replace an exact snippet. |
| `POST /fs/list` | List a directory. |
| `POST /fs/glob` | Find paths by glob pattern. |
| `POST /fs/grep` | Search file contents with a regular expression. |
| `POST /fs/mkdir` | Create a directory. |
| `POST /fs/move` | Move or rename a path. |
| `POST /fs/remove` | Delete a file or directory. |

## Workspace

Without workspace roots the bridge touches any path the user account can reach. Setting roots limits every file operation and the working directory of commands to those directories. Read-only agent tools skip this limit only if no roots are set.

## Sessions

A session is a real PTY. Shell, working directory, columns, and rows are chosen when it opens. Closing a session kills its process. Sessions with no input for 30 minutes are cleaned up automatically.

Signals accepted by `POST /sessions/{id}/input`: `int`, `term`, `quit`, `hup`, plus the `sig` prefixed forms. Signals are not supported on Windows.

## Security

The bridge speaks plain HTTP. Use it on a trusted network only. Do not expose the port to the internet.
