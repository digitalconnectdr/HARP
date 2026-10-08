#!/usr/bin/env bash
set -euo pipefail

VERSION="2.18.0"
EXPECTED_SHA256="15ec8ed121663b562c99caa5bb602d1009f24e5b09e733438b81988f12feaaab"
URL="https://github.com/heiher/hev-socks5-tunnel/releases/download/${VERSION}/hev-socks5-tunnel.aar"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST_DIR="$ROOT/app/libs"
DEST="$DEST_DIR/hev-socks5-tunnel.aar"
TMP="$DEST.tmp"

mkdir -p "$DEST_DIR"
rm -f "$TMP"

echo "Downloading HEV ${VERSION} Android AAR..."
curl -fL --retry 3 --retry-delay 2 "$URL" -o "$TMP"

ACTUAL="$(sha256sum "$TMP" | awk '{print $1}')"
if [[ "$ACTUAL" != "$EXPECTED_SHA256" ]]; then
  rm -f "$TMP"
  echo "ERROR: SHA-256 mismatch"
  echo "expected: $EXPECTED_SHA256"
  echo "actual:   $ACTUAL"
  exit 1
fi

mv "$TMP" "$DEST"
echo "HEV_AAR_READY=$DEST"
echo "SHA256=$ACTUAL"
