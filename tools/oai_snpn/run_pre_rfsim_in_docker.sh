#!/usr/bin/env bash
set -euo pipefail

OAI_ANCHOR="f8f769592a7030be88ede4bb5ca66fa1ca6a80e0"
IMAGE_NAME="${HARP_OAI_BUILD_IMAGE:-harp-oai-ran-base:24.04}"

if [ "$#" -ne 1 ]; then
  echo "usage: $0 <openairinterface5g-checkout>" >&2
  exit 2
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: docker is required for this runner." >&2
  exit 2
fi

OAI="$(cd "$1" && pwd)"
HARP="$(cd "$(dirname "$0")/../.." && pwd)"

if ! git -C "$OAI" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "ERROR: not an OAI git checkout: $OAI" >&2
  exit 3
fi

HEAD="$(git -C "$OAI" rev-parse HEAD)"
if [ "$HEAD" != "$OAI_ANCHOR" ]; then
  echo "ERROR: OAI checkout must be pinned to $OAI_ANCHOR" >&2
  echo "       current HEAD: $HEAD" >&2
  exit 4
fi

if [ -n "$(git -C "$OAI" status --porcelain)" ]; then
  echo "ERROR: OAI checkout must be clean before container build/test." >&2
  exit 5
fi

echo "[HARP] building official OAI Ubuntu 24.04 dependency image..."
docker build \
  --file "$OAI/docker/Dockerfile.base.ubuntu" \
  --tag "$IMAGE_NAME" \
  "$OAI"

echo "[HARP] running pre-RFsim SNPN gates inside container..."
docker run --rm \
  -v "$OAI:/workspace/oai" \
  -v "$HARP:/workspace/harp:ro" \
  -w /workspace/harp \
  "$IMAGE_NAME" \
  bash tools/oai_snpn/run_pre_rfsim_gates.sh \
    /workspace/oai \
    /workspace/oai/build-harp-snpn

echo "PASS_HARP_OAI_SNPN_DOCKER_PRE_RFSIM"
