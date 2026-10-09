#!/usr/bin/env bash
set -euo pipefail

AMF_ANCHOR="5eedea557a3745b13ed9ec4bf29e6a28bd912574"
IMAGE_NAME="${HARP_AMF_BUILD_IMAGE:-harp-oai-amf-builder:24.04}"

if [ "$#" -ne 1 ]; then
  echo "usage: $0 <oai-cn5g-amf-checkout>" >&2
  exit 2
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: docker is required for this runner." >&2
  exit 2
fi

AMF="$(cd "$1" && pwd)"
HARP="$(cd "$(dirname "$0")/../.." && pwd)"

if ! git -C "$AMF" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "ERROR: not an AMF git checkout/worktree: $AMF" >&2
  exit 3
fi

HEAD="$(git -C "$AMF" rev-parse HEAD)"
if [ "$HEAD" != "$AMF_ANCHOR" ]; then
  echo "ERROR: AMF checkout must be pinned to $AMF_ANCHOR" >&2
  echo "       current HEAD: $HEAD" >&2
  exit 4
fi

if [ -n "$(git -C "$AMF" status --porcelain)" ]; then
  echo "ERROR: AMF checkout must be clean before container build." >&2
  exit 5
fi

SUBMODULE_STATUS="$(git -C "$AMF" submodule status --recursive)"
if [ -z "$SUBMODULE_STATUS" ]; then
  echo "ERROR: AMF submodules are not available." >&2
  exit 6
fi
if grep -Eq '^[+-U]' <<<"$SUBMODULE_STATUS"; then
  echo "ERROR: AMF submodules are missing or not at the pinned gitlink revisions:" >&2
  printf '%s\n' "$SUBMODULE_STATUS" >&2
  exit 6
fi

for required in src/common-src build/common-build; do
  if [ ! -d "$AMF/$required" ] || [ -z "$(ls -A "$AMF/$required" 2>/dev/null || true)" ]; then
    echo "ERROR: required initialized AMF submodule/content missing: $required" >&2
    exit 6
  fi
done

TMP_ROOT="$(mktemp -d)"
CLONE="$TMP_ROOT/amf"
cleanup() {
  rm -rf "$TMP_ROOT"
}
trap cleanup EXIT

echo "[HARP] creating disposable local AMF clone..."
git clone --no-hardlinks --no-checkout "$AMF" "$CLONE"
git -C "$CLONE" checkout --detach "$AMF_ANCHOR"

echo "[HARP] applying AMF SNPN blueprints to disposable clone..."
bash "$HARP/tools/oai_amf_snpn/apply_amf_blueprints.sh" "$CLONE"

echo "[HARP] copying pinned initialized submodule contents without Git metadata..."
for required in src/common-src build/common-build; do
  mkdir -p "$CLONE/$required"
  tar -C "$AMF/$required" --exclude=.git -cf - . | tar -C "$CLONE/$required" -xf -
done

echo "[HARP] building official AMF Ubuntu 24.04 builder image..."
docker build \
  --target oai-amf-builder \
  --file "$CLONE/docker/Dockerfile.amf.ubuntu" \
  --tag "$IMAGE_NAME" \
  "$CLONE"

echo "[HARP] verifying AMF binary exists in builder image..."
docker run --rm \
  --entrypoint /bin/bash \
  "$IMAGE_NAME" \
  -lc 'test -x /openair-amf/build/amf/build/oai_amf && /usr/bin/file /openair-amf/build/amf/build/oai_amf'

echo "PASS_HARP_AMF_SNPN_DOCKER_BUILD"
