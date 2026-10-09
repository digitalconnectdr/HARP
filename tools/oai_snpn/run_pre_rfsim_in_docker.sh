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

case "$(uname -m)" in
  x86_64) TARGETARCH=amd64 ;;
  aarch64|arm64) TARGETARCH=arm64 ;;
  *)
    echo "ERROR: unsupported host architecture for OAI Ubuntu image: $(uname -m)" >&2
    exit 2
    ;;
esac

if ! git -C "$OAI" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "ERROR: not an OAI git checkout/worktree: $OAI" >&2
  exit 3
fi

if ! git -C "$OAI" cat-file -e "$OAI_ANCHOR^{commit}" 2>/dev/null; then
  echo "ERROR: pinned OAI commit is not present locally: $OAI_ANCHOR" >&2
  exit 4
fi

TMP_ROOT="$(mktemp -d)"
CLONE="$TMP_ROOT/oai"
cleanup() {
  rm -rf "$TMP_ROOT"
}
trap cleanup EXIT

echo "[HARP] creating disposable local clone at pinned commit..."
git clone --no-hardlinks --no-checkout "$OAI" "$CLONE"
git -C "$CLONE" checkout --detach "$OAI_ANCHOR"

echo "[HARP] building official OAI Ubuntu 24.04 dependency image..."
docker build \
  --build-arg TARGETARCH="$TARGETARCH" \
  --file "$CLONE/docker/Dockerfile.base.ubuntu" \
  --tag "$IMAGE_NAME" \
  "$CLONE"

echo "[HARP] running pre-RFsim SNPN gates inside disposable clone..."
docker run --rm \
  -e HARP_ASN1C_EXEC=/opt/asn1c/bin/asn1c \
  -v "$CLONE:/workspace/oai" \
  -v "$HARP:/workspace/harp:ro" \
  -w /workspace/harp \
  "$IMAGE_NAME" \
  bash tools/oai_snpn/run_pre_rfsim_gates.sh \
    /workspace/oai \
    /workspace/oai/build-harp-snpn

echo "PASS_HARP_OAI_SNPN_DOCKER_PRE_RFSIM"
