#!/usr/bin/env bash
set -euo pipefail

OAI_ANCHOR="f8f769592a7030be88ede4bb5ca66fa1ca6a80e0"

if [ "$#" -lt 1 ] || [ "$#" -gt 2 ]; then
  echo "usage: $0 <openairinterface5g-checkout> [build-dir]" >&2
  exit 2
fi

CHECKOUT="$(cd "$1" && pwd)"
BUILD_DIR="${2:-$CHECKOUT/build-harp-snpn}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

if [ ! -d "$CHECKOUT/.git" ]; then
  echo "ERROR: not a git checkout: $CHECKOUT" >&2
  exit 2
fi

HEAD="$(git -C "$CHECKOUT" rev-parse HEAD)"
if [ "$HEAD" != "$OAI_ANCHOR" ]; then
  echo "ERROR: OAI checkout must be pinned to $OAI_ANCHOR" >&2
  echo "       current HEAD: $HEAD" >&2
  exit 3
fi

if [ -n "$(git -C "$CHECKOUT" status --porcelain)" ]; then
  echo "ERROR: OAI checkout is not clean before HARP blueprint application." >&2
  echo "       Use a disposable worktree/check-out at the pinned commit." >&2
  exit 4
fi

echo "[HARP] validating independent SNPN KDF reference vectors..."
python3 "$ROOT/tools/oai_snpn/kdf_reference.py"

echo "[HARP] validating OAI blueprints..."
"$ROOT/tools/oai_snpn/apply_oai_blueprints.sh" "$CHECKOUT" --check

echo "[HARP] applying OAI blueprints..."
"$ROOT/tools/oai_snpn/apply_oai_blueprints.sh" "$CHECKOUT"

echo "[HARP] configuring minimal test build..."
cmake -S "$CHECKOUT" -B "$BUILD_DIR" -GNinja \
  -DENABLE_TESTS=ON \
  -DSANITIZE_ADDRESS=OFF

echo "[HARP] building only SNPN gate targets..."
cmake --build "$BUILD_DIR" --target \
  nas_lib_test \
  test_asn1_msg \
  test_snpn_sib1_codec

echo "[HARP] verifying focused CTest registration..."
TEST_RE='^(nas_lib_test|test_asn1_msg|test_snpn_sib1_codec)

echo "[HARP] verifying expected PASS markers..."
LOG_FILE="$BUILD_DIR/Testing/Temporary/LastTest.log"
if [ ! -f "$LOG_FILE" ]; then
  echo "ERROR: CTest log not found: $LOG_FILE" >&2
  exit 6
fi

required_markers=(
  PASS_V5G_SNN_FORMAT_BASELINE
  PASS_V5G_SNN_REFACTOR_BASELINE
  PASS_V5G_SNPN_SNN
  PASS_V5G_SNPN_KDF_UE
  PASS_V5G_SNPN_NPN_CODEC
  PASS_V5G_SNPN_NPN_CODEC_NEGATIVE
  PASS_V5G_SNPN_CELL_ACCESS_CODEC
  PASS_V5G_SNPN_SELECT_UNIT
  PASS_V5G_SNPN_SIB1_CODEC
)

for marker in "${required_markers[@]}"; do
  if ! grep -Fq "$marker" "$LOG_FILE"; then
    echo "ERROR: expected marker missing: $marker" >&2
    exit 7
  fi
done

echo "PASS_HARP_OAI_SNPN_PRE_RFSIM_GATES"

TEST_LISTING="$(ctest --test-dir "$BUILD_DIR" -N -R "$TEST_RE" 2>&1)"
printf '%s\n' "$TEST_LISTING"
if ! grep -Eq 'Total Tests: *3

echo "[HARP] verifying expected PASS markers..."
LOG_FILE="$BUILD_DIR/Testing/Temporary/LastTest.log"
if [ ! -f "$LOG_FILE" ]; then
  echo "ERROR: CTest log not found: $LOG_FILE" >&2
  exit 5
fi

required_markers=(
  PASS_V5G_SNN_FORMAT_BASELINE
  PASS_V5G_SNN_REFACTOR_BASELINE
  PASS_V5G_SNPN_SNN
  PASS_V5G_SNPN_KDF_UE
  PASS_V5G_SNPN_NPN_CODEC
  PASS_V5G_SNPN_NPN_CODEC_NEGATIVE
  PASS_V5G_SNPN_CELL_ACCESS_CODEC
  PASS_V5G_SNPN_SELECT_UNIT
  PASS_V5G_SNPN_SIB1_CODEC
)

for marker in "${required_markers[@]}"; do
  if ! grep -Fq "$marker" "$LOG_FILE"; then
    echo "ERROR: expected marker missing: $marker" >&2
    exit 6
  fi
done

echo "PASS_HARP_OAI_SNPN_PRE_RFSIM_GATES"
 <<<"$TEST_LISTING"; then
  echo "ERROR: expected exactly 3 focused SNPN CTest entries." >&2
  exit 5
fi

echo "[HARP] running focused CTest selection..."
ctest --test-dir "$BUILD_DIR" \
  --output-on-failure \
  -R "$TEST_RE"

echo "[HARP] verifying expected PASS markers..."
LOG_FILE="$BUILD_DIR/Testing/Temporary/LastTest.log"
if [ ! -f "$LOG_FILE" ]; then
  echo "ERROR: CTest log not found: $LOG_FILE" >&2
  exit 5
fi

required_markers=(
  PASS_V5G_SNN_FORMAT_BASELINE
  PASS_V5G_SNN_REFACTOR_BASELINE
  PASS_V5G_SNPN_SNN
  PASS_V5G_SNPN_KDF_UE
  PASS_V5G_SNPN_NPN_CODEC
  PASS_V5G_SNPN_NPN_CODEC_NEGATIVE
  PASS_V5G_SNPN_CELL_ACCESS_CODEC
  PASS_V5G_SNPN_SELECT_UNIT
  PASS_V5G_SNPN_SIB1_CODEC
)

for marker in "${required_markers[@]}"; do
  if ! grep -Fq "$marker" "$LOG_FILE"; then
    echo "ERROR: expected marker missing: $marker" >&2
    exit 6
  fi
done

echo "PASS_HARP_OAI_SNPN_PRE_RFSIM_GATES"
