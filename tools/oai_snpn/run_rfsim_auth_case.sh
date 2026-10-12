#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat >&2 <<'EOF'
usage: run_rfsim_auth_case.sh <positive|negative> <ran-build-dir> <amf-binary> <fixture-dir>

Environment:
  HARP_RFSIM_TIMEOUT=<seconds>        Runtime window per process (default: 45)
  HARP_LOG_DIR=<dir>                  Log directory (default: /tmp)
  HARP_TOPOLOGY_MODE=host|container   Required by lab preflight
  HARP_TOPOLOGY_CONFIRMED=1           Required for container mode
  HARP_DRY_RUN=1                      Print commands only; do not execute
EOF
  exit 2
}

[ "$#" -eq 4 ] || usage

MODE="$1"
RAN_BUILD="$(cd "$2" && pwd)"
AMF_BIN="$(readlink -f "$3")"
FIXTURES="$(cd "$4" && pwd)"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TIMEOUT_SECS="${HARP_RFSIM_TIMEOUT:-45}"
LOG_DIR="${HARP_LOG_DIR:-/tmp}"
DRY_RUN="${HARP_DRY_RUN:-0}"

case "$MODE" in
  positive)
    AMF_CFG="$FIXTURES/amf.harp-snpn-positive.yaml"
    EXPECTED_AMF_NID="10000000001"
    ;;
  negative)
    AMF_CFG="$FIXTURES/amf.harp-snpn-negative.yaml"
    EXPECTED_AMF_NID="10000000002"
    ;;
  *)
    usage
    ;;
esac

GNB_BIN="$RAN_BUILD/nr-softmodem"
UE_BIN="$RAN_BUILD/nr-uesoftmodem"
GNB_CFG="$FIXTURES/gnb.harp-snpn.conf"
UE_CFG="$FIXTURES/nrue.harp-snpn.conf"
SUBSCRIBER_SQL="$FIXTURES/harp_subscriber.sql"

AMF_LOG="$LOG_DIR/harp-amf-$MODE.log"
GNB_LOG="$LOG_DIR/harp-gnb-$MODE.log"
UE_LOG="$LOG_DIR/harp-ue-$MODE.log"

mkdir -p "$LOG_DIR"

extract_db_value() {
  local key="$1"
  python3 - "$AMF_CFG" "$key" <<'PY'
from pathlib import Path
import sys
cfg = Path(sys.argv[1]).read_text().splitlines()
wanted = sys.argv[2]
inside = False
for line in cfg:
    if line == "database:":
        inside = True
        continue
    if inside:
        if line and not line.startswith(" "):
            break
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or ":" not in stripped:
            continue
        k, v = stripped.split(":", 1)
        if k.strip() == wanted:
            print(v.strip().strip('"'))
            raise SystemExit(0)
raise SystemExit(1)
PY
}

DB_HOST="$(extract_db_value host)"
DB_USER="$(extract_db_value user)"
DB_PASS="$(extract_db_value password)"
DB_NAME="$(extract_db_value database_name)"

run() {
  if [ "$DRY_RUN" = "1" ]; then
    printf 'DRY_RUN:'
    printf ' %q' "$@"
    printf '\n'
    return 0
  fi
  "$@"
}

echo "[HARP] RFsim auth case: $MODE"
echo "[HARP] logs: $AMF_LOG $GNB_LOG $UE_LOG"

run bash "$ROOT/tools/oai_snpn/check_auth_only_runtime_artifacts.sh" \
  "$RAN_BUILD" "$AMF_BIN" "$FIXTURES"

if [ "$DRY_RUN" != "1" ]; then
  bash "$ROOT/tools/oai_snpn/check_rfsim_auth_lab_ready.sh" \
    "$FIXTURES" "$AMF_BIN" "$GNB_BIN" "$UE_BIN"
else
  echo "DRY_RUN: lab preflight skipped because it performs live connectivity checks"
fi

if [ "$DRY_RUN" = "1" ]; then
  echo "DRY_RUN: reset subscriber in $DB_NAME at $DB_HOST using $SUBSCRIBER_SQL"
  echo "DRY_RUN: start AMF with $AMF_CFG"
  echo "DRY_RUN: start gNB with $GNB_CFG"
  echo "DRY_RUN: start nrUE with $UE_CFG"
  echo "DRY_RUN: run log checker for mode=$MODE expected_amf_nid=$EXPECTED_AMF_NID"
  echo "PASS_HARP_RFSIM_AUTH_CASE_DRY_RUN"
  exit 0
fi

MYSQL_PWD="$DB_PASS" mysql -h "$DB_HOST" -u "$DB_USER" "$DB_NAME" < "$SUBSCRIBER_SQL"

: > "$AMF_LOG"
: > "$GNB_LOG"
: > "$UE_LOG"

AMF_PID=""
GNB_PID=""
cleanup() {
  set +e
  [ -n "$GNB_PID" ] && kill "$GNB_PID" 2>/dev/null || true
  [ -n "$AMF_PID" ] && kill "$AMF_PID" 2>/dev/null || true
  wait "$GNB_PID" 2>/dev/null || true
  wait "$AMF_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

timeout "$TIMEOUT_SECS" "$AMF_BIN" -c "$AMF_CFG" -o >"$AMF_LOG" 2>&1 &
AMF_PID=$!
sleep 2

timeout "$TIMEOUT_SECS" sudo "$GNB_BIN" \
  -O "$GNB_CFG" \
  --gNBs.[0].min_rxtxtime 6 \
  --rfsim \
  --log_config.global_log_options level,nocolor,time \
  >"$GNB_LOG" 2>&1 &
GNB_PID=$!
sleep 2

set +e
timeout "$TIMEOUT_SECS" sudo "$UE_BIN" \
  -r 106 \
  --numerology 1 \
  --band 78 \
  -C 3619200000 \
  --rfsim \
  --rfsimulator.[0].serveraddr 127.0.0.1 \
  -O "$UE_CFG" \
  --log_config.global_log_options level,nocolor,time \
  >"$UE_LOG" 2>&1
UE_RC=$?
set -e

if [ "$UE_RC" -ne 0 ] && [ "$UE_RC" -ne 124 ]; then
  echo "ERROR: nrUE exited unexpectedly with rc=$UE_RC" >&2
  exit "$UE_RC"
fi

cleanup
trap - EXIT INT TERM

if [ "$MODE" = "positive" ]; then
  python3 "$ROOT/tools/oai_snpn/check_rfsim_snpn_logs.py" \
    --mode positive \
    --ue-log "$UE_LOG" \
    --amf-log "$AMF_LOG"
else
  python3 "$ROOT/tools/oai_snpn/check_rfsim_snpn_logs.py" \
    --mode negative \
    --ue-log "$UE_LOG" \
    --amf-log "$AMF_LOG" \
    --expected-amf-nid "$EXPECTED_AMF_NID"
fi

echo "PASS_HARP_RFSIM_AUTH_CASE mode=$MODE"
