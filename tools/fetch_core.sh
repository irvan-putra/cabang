#!/bin/sh
# Installs the Delta Chat core RPC server (Android arm64) as the app's native library.
# The wheel URL and sha256 come from tools/core.lock, not from the download response.
set -eu
D="$(dirname "$0")"; . "$D/core.lock"
OUT="$D/../android/app/src/main/jniLibs/arm64-v8a"
W=$(mktemp -d)
curl -fsSL "$SERVER_URL" -o "$W/core.whl"
echo "$SERVER_SHA256  $W/core.whl" | sha256sum -c -
unzip -q -o "$W/core.whl" -d "$W/x"
mkdir -p "$OUT"; cp "$W/x/deltachat_rpc_server/deltachat-rpc-server" "$OUT/librpcsrv.so"; chmod 755 "$OUT/librpcsrv.so"
echo "installed $(wc -c < "$OUT/librpcsrv.so") bytes"
