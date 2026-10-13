#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat >&2 <<'USAGE'
usage: run_rfsim_auth_pair.sh <ran-build-dir> <amf-binary> <fixture-dir>

Environment is forwarded to run_rfsim_auth_case.sh, including:
  HARP_RFSIM_TIMEOUT
  HARP_STARTUP_GRACE
  HARP_LOG_DIR
  HARP_TOPOLOGY_MODE
  HARP_TOPOLOGY_CONFIRMED
  HARP_DRY_RUN

Optional:
  HARP_PAIR_ID=<id>   deterministic pair identifier
USAGE
  exit 2
}

[ "$#" -eq 3 ] || usage

RAN_BUILD="$(cd "$1" && pwd)"
AMF_BIN="$(readlink -f "$2")"
FIXTURES="$(cd "$3" && pwd)"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CASE_RUNNER="$ROOT/tools/oai_snpn/run_rfsim_auth_case.sh"
LOG_ROOT="${HARP_LOG_DIR:-/tmp}"
PAIR_ID="${HARP_PAIR_ID:-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
DRY_RUN="${HARP_DRY_RUN:-0}"
PAIR_DIR="$LOG_ROOT/harp-rfsim-pair-$PAIR_ID"
PAIR_MANIFEST="$PAIR_DIR/pair-manifest.txt"
POS_RUN_ID="$PAIR_ID-positive"
NEG_RUN_ID="$PAIR_ID-negative"
POS_DIR="$LOG_ROOT/harp-rfsim-positive-$POS_RUN_ID"
NEG_DIR="$LOG_ROOT/harp-rfsim-negative-$NEG_RUN_ID"

python3 "$ROOT/tools/oai_snpn/check_rfsim_fixture_pair.py" "$FIXTURES"

echo "[HARP] RFsim auth pair: $PAIR_ID"
echo "[HARP] positive evidence: $POS_DIR"
echo "[HARP] negative evidence: $NEG_DIR"

HARP_RUN_ID="$POS_RUN_ID" bash "$CASE_RUNNER"   positive "$RAN_BUILD" "$AMF_BIN" "$FIXTURES"

HARP_RUN_ID="$NEG_RUN_ID" bash "$CASE_RUNNER"   negative "$RAN_BUILD" "$AMF_BIN" "$FIXTURES"

if [ "$DRY_RUN" = "1" ]; then
  echo "PASS_HARP_RFSIM_AUTH_PAIR_DRY_RUN pair_id=$PAIR_ID"
  exit 0
fi

for required in   "$POS_DIR/evidence-manifest.txt"   "$POS_DIR/harp-amf-positive.log"   "$POS_DIR/harp-ue-positive.log"   "$NEG_DIR/evidence-manifest.txt"   "$NEG_DIR/harp-amf-negative.log"   "$NEG_DIR/harp-ue-negative.log"; do
  if [ ! -f "$required" ]; then
    echo "ERROR: paired RFsim evidence file missing: $required" >&2
    exit 30
  fi
done

grep -qx 'RESULT=PASS' "$POS_DIR/evidence-manifest.txt"   || { echo "ERROR: positive evidence manifest is not PASS" >&2; exit 31; }
grep -qx 'RESULT=PASS' "$NEG_DIR/evidence-manifest.txt"   || { echo "ERROR: negative evidence manifest is not PASS" >&2; exit 31; }

mkdir -p "$PAIR_DIR"
{
  echo "HARP_RFSIM_PAIR_EVIDENCE_VERSION=1"
  echo "PAIR_ID=$PAIR_ID"
  echo "FINISHED_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "POSITIVE_DIR=$POS_DIR"
  echo "NEGATIVE_DIR=$NEG_DIR"
  echo "RAN_BUILD=$RAN_BUILD"
  echo "AMF_BINARY=$AMF_BIN"
  echo "FIXTURE_DIR=$FIXTURES"
  if command -v git >/dev/null 2>&1 && git -C "$ROOT" rev-parse HEAD >/dev/null 2>&1; then
    echo "HARP_COMMIT=$(git -C "$ROOT" rev-parse HEAD)"
  fi
} > "$PAIR_MANIFEST"

if command -v sha256sum >/dev/null 2>&1; then
  sha256sum     "$FIXTURES/gnb.harp-snpn.conf"     "$FIXTURES/nrue.harp-snpn.conf"     "$FIXTURES/amf.harp-snpn-positive.yaml"     "$FIXTURES/amf.harp-snpn-negative.yaml"     "$FIXTURES/harp_subscriber.sql"     > "$PAIR_DIR/fixture-checksums.sha256"
fi

echo "HARP_RFSIM_PAIR_EVIDENCE_DIR=$PAIR_DIR"
echo "PASS_HARP_RFSIM_AUTH_PAIR pair_id=$PAIR_ID"
