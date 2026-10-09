#!/usr/bin/env bash
set -euo pipefail

AMF_ANCHOR="5eedea557a3745b13ed9ec4bf29e6a28bd912574"

if [ "$#" -lt 1 ] || [ "$#" -gt 2 ]; then
  echo "usage: $0 <oai-cn5g-amf-checkout> [build-dir]" >&2
  exit 2
fi

CHECKOUT="$(cd "$1" && pwd)"
BUILD_DIR="${2:-$CHECKOUT/build-harp-snpn}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

if ! git -C "$CHECKOUT" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "ERROR: not a git worktree/checkout: $CHECKOUT" >&2
  exit 2
fi

HEAD="$(git -C "$CHECKOUT" rev-parse HEAD)"
if [ "$HEAD" != "$AMF_ANCHOR" ]; then
  echo "ERROR: AMF checkout must be pinned to $AMF_ANCHOR" >&2
  echo "       current HEAD: $HEAD" >&2
  exit 3
fi

if [ -n "$(git -C "$CHECKOUT" status --porcelain)" ]; then
  echo "ERROR: AMF checkout is not clean before HARP blueprint application." >&2
  exit 4
fi

for required in src/common-src build/common-build; do
  if [ ! -d "$CHECKOUT/$required" ] || [ -z "$(ls -A "$CHECKOUT/$required" 2>/dev/null || true)" ]; then
    echo "ERROR: required AMF submodule/content missing: $required" >&2
    echo "       initialize the pinned AMF checkout submodules before running." >&2
    exit 5
  fi
done

echo "[HARP] validating AMF blueprints..."
bash "$ROOT/tools/oai_amf_snpn/apply_amf_blueprints.sh" "$CHECKOUT" --check

echo "[HARP] applying AMF blueprints..."
bash "$ROOT/tools/oai_amf_snpn/apply_amf_blueprints.sh" "$CHECKOUT"

echo "[HARP] configuring AMF build..."
export OPENAIRCN_DIR="$CHECKOUT"
cmake -S "$CHECKOUT/build/amf" -B "$BUILD_DIR" -GNinja \
  -DCMAKE_BUILD_TYPE=Release \
  -DBUILD_SHARED_LIBS=OFF

echo "[HARP] building AMF target..."
cmake --build "$BUILD_DIR" --target amf

AMF_BIN="$BUILD_DIR/amf"
if [ ! -x "$AMF_BIN" ]; then
  AMF_BIN="$(find "$BUILD_DIR" -type f -name amf -perm -111 | head -n1 || true)"
fi
if [ -z "$AMF_BIN" ] || [ ! -x "$AMF_BIN" ]; then
  echo "ERROR: AMF binary not found after build." >&2
  exit 6
fi

echo "PASS_HARP_AMF_SNPN_BUILD"
echo "AMF_BINARY=$AMF_BIN"
