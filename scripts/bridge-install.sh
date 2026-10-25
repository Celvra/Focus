#!/bin/sh
set -eu

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/bridge"

command -v cargo >/dev/null 2>&1 || {
    echo "cargo is required, install Rust first: https://rustup.rs" >&2
    exit 1
}

cargo build --release

DEST="$HOME/.local/bin/focus-bridge"
mkdir -p "$HOME/.local/bin"
cp "target/release/focus-bridge" "$DEST"

case ":$PATH:" in
    *":$HOME/.local/bin:"*) ;;
    *)
        echo "$HOME/.local/bin is not on PATH. Add it, for example:"
        echo "  export PATH=\"\$HOME/.local/bin:\$PATH\""
        ;;
esac

echo "installed: $DEST"
echo
echo "start it with:"
echo "  focus-bridge serve"
echo
echo "then pair from the phone with the code it prints, or read the code any time with:"
echo "  focus-bridge pair"
