#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat >&2 <<'USAGE'
usage: run_rfsim_auth_case.sh <positive|negative> <ran-build-dir> <amf-binary> <fixture-dir>

Environment:
  HARP_RFSIM_TIMEOUT=<seconds>        Maximum runtime window after nrUE start (default: 45)
  HARP_STARTUP_GRACE=<seconds>        Early-exit startup grace (default: 2)
  HARP_LOG_DIR=<dir>                  Parent evidence/log directory (default: /tmp)
  HARP_RUN_ID=<id>                    Optional deterministic run identifier
  HARP_TOPOLOGY_MODE=host|container   Required by lab preflight
  HARP_TOPOLOGY_CONFIRMED=1           Required for container mode
  HARP_DRY_RUN=1                      Print commands only; do not execute
USAGE
  exit 2
}

[ "$#" -eq 4 ] || usage

MODE="$1"
RAN_BUILD="$(cd "$2" && pwd)"
AMF_BIN="$(readlink -f "$3")"
FIXTURES="$(cd "$4" && pwd)"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TIMEOUT_SECS="${HARP_RFSIM_TIMEOUT:-45}"
STARTUP_GRACE="${HARP_STARTUP_GRACE:-2}"
LOG_ROOT="${HARP_LOG_DIR:-/tmp}"
DRY_RUN="${HARP_DRY_RUN:-0}"
RUN_ID="${HARP_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
RESULT="FAIL"
FAIL_REASON="not_completed"

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
RFSIM_LIB="$RAN_BUILD/librfsimulator.so"
GNB_CFG="$FIXTURES/gnb.harp-snpn.conf"
UE_CFG="$FIXTURES/nrue.harp-snpn.conf"
SUBSCRIBER_SQL="$FIXTURES/harp_subscriber.sql"

CASE_DIR="$LOG_ROOT/harp-rfsim-$MODE-$RUN_ID"
CONFIG_DIR="$CASE_DIR/configs"
AMF_LOG="$CASE_DIR/harp-amf-$MODE.log"
GNB_LOG="$CASE_DIR/harp-gnb-$MODE.log"
UE_LOG="$CASE_DIR/harp-ue-$MODE.log"
MANIFEST="$CASE_DIR/evidence-manifest.txt"
CHECKSUMS="$CASE_DIR/checksums.sha256"

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

AMF_PID=""
GNB_PID=""
UE_PID=""

cleanup() {
  set +e
  for pid in "$UE_PID" "$GNB_PID" "$AMF_PID"; do
    [ -n "$pid" ] || continue
    kill "$pid" 2>/dev/null || true
  done
  sleep 1
  for pid in "$UE_PID" "$GNB_PID" "$AMF_PID"; do
    [ -n "$pid" ] || continue
    if kill -0 "$pid" 2>/dev/null; then
      kill -KILL "$pid" 2>/dev/null || true
    fi
  done
  for pid in "$UE_PID" "$GNB_PID" "$AMF_PID"; do
    [ -n "$pid" ] || continue
    wait "$pid" 2>/dev/null || true
  done
  set -e
}

snapshot_configs() {
  mkdir -p "$CONFIG_DIR"
  cp "$GNB_CFG" "$UE_CFG" "$CONFIG_DIR/"
  python3 - "$AMF_CFG" "$CONFIG_DIR/$(basename "$AMF_CFG")" <<'PY'
from pathlib import Path
import sys

src = Path(sys.argv[1]).read_text().splitlines()
out = []
inside_database = False
for line in src:
    if line == "database:":
        inside_database = True
        out.append(line)
        continue
    if inside_database and line and not line.startswith(" "):
        inside_database = False
    if inside_database and line.strip().startswith("password:"):
        indent = line[: len(line) - len(line.lstrip())]
        out.append(f'{indent}password: "<redacted>"')
    else:
        out.append(line)
Path(sys.argv[2]).write_text("\n".join(out) + "\n")
PY
  echo "Subscriber SQL intentionally not copied; original SHA-256 is recorded instead."     > "$CONFIG_DIR/README.txt"
}

write_manifest() {
  [ "$DRY_RUN" = "1" ] && return 0
  {
    echo "HARP_RFSIM_EVIDENCE_VERSION=1"
    echo "MODE=$MODE"
    echo "RUN_ID=$RUN_ID"
    echo "STARTED_AT=$STARTED_AT"
    echo "FINISHED_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "RESULT=$RESULT"
    echo "FAIL_REASON=$FAIL_REASON"
    echo "TIMEOUT_SECS=$TIMEOUT_SECS"
    echo "STARTUP_GRACE=$STARTUP_GRACE"
    echo "RAN_BUILD=$RAN_BUILD"
    echo "AMF_BINARY=$AMF_BIN"
    echo "GNB_BINARY=$GNB_BIN"
    echo "NRUE_BINARY=$UE_BIN"
    echo "RFSIM_MODULE=$RFSIM_LIB"
    echo "FIXTURE_DIR=$FIXTURES"
    echo "MYSQL_HOST=$DB_HOST"
    echo "MYSQL_USER=$DB_USER"
    echo "MYSQL_DATABASE=$DB_NAME"
    echo "TOPOLOGY_MODE=${HARP_TOPOLOGY_MODE:-unset}"
    echo "TOPOLOGY_CONFIRMED=${HARP_TOPOLOGY_CONFIRMED:-0}"
    if command -v git >/dev/null 2>&1 && git -C "$ROOT" rev-parse HEAD >/dev/null 2>&1; then
      echo "HARP_COMMIT=$(git -C "$ROOT" rev-parse HEAD)"
    fi
    if command -v uname >/dev/null 2>&1; then
      echo "HOST_UNAME=$(uname -a)"
    fi
  } > "$MANIFEST"
}

write_checksums() {
  [ "$DRY_RUN" = "1" ] && return 0
  command -v sha256sum >/dev/null 2>&1 || return 0
  : > "$CHECKSUMS"
  for f in     "$AMF_BIN" "$GNB_BIN" "$UE_BIN" "$RFSIM_LIB"     "$AMF_LOG" "$GNB_LOG" "$UE_LOG"     "$AMF_CFG" "$GNB_CFG" "$UE_CFG" "$SUBSCRIBER_SQL"; do
    [ -f "$f" ] && sha256sum "$f" >> "$CHECKSUMS"
  done
}

on_exit() {
  local rc=$?
  trap - EXIT INT TERM
  cleanup
  write_manifest || true
  write_checksums || true
  if [ "$DRY_RUN" != "1" ]; then
    echo "HARP_RFSIM_EVIDENCE_DIR=$CASE_DIR"
  fi
  exit "$rc"
}
trap on_exit EXIT INT TERM

fail_process() {
  local label="$1"
  local pid="$2"
  local log="$3"
  local rc=0
  set +e
  wait "$pid"
  rc=$?
  set -e
  FAIL_REASON="${label}_exited_rc_${rc}"
  echo "ERROR: $label exited before required RFsim evidence (rc=$rc)" >&2
  echo "----- $label log tail -----" >&2
  tail -n 60 "$log" >&2 || true
  echo "---------------------------" >&2
  exit 20
}

require_alive_after_grace() {
  local label="$1"
  local pid="$2"
  local log="$3"
  sleep "$STARTUP_GRACE"
  if ! kill -0 "$pid" 2>/dev/null; then
    fail_process "$label" "$pid" "$log"
  fi
  echo "PASS_HARP_${label}_STARTED pid=$pid"
}

marker_ready() {
  grep -q 'PASS_V5G_SNPN_SELECT' "$UE_LOG" 2>/dev/null || return 1
  grep -q 'HARP_SNPN_AUTH_RESPONSE_UE' "$UE_LOG" 2>/dev/null || return 1
  grep -q 'HARP_SNPN_AMF_SNN' "$AMF_LOG" 2>/dev/null || return 1
  if [ "$MODE" = "positive" ]; then
    grep -q 'PASS_V5G_SNPN_AUTH_AMF' "$AMF_LOG" 2>/dev/null || return 1
  else
    grep -q 'HARP_SNPN_AUTH_REJECT_AMF' "$AMF_LOG" 2>/dev/null || return 1
  fi
}

echo "[HARP] RFsim auth case: $MODE"
echo "[HARP] evidence directory: $CASE_DIR"

run bash "$ROOT/tools/oai_snpn/check_auth_only_runtime_artifacts.sh"   "$RAN_BUILD" "$AMF_BIN" "$FIXTURES"

if [ "$DRY_RUN" != "1" ]; then
  mkdir -p "$CASE_DIR"
  snapshot_configs

  bash "$ROOT/tools/oai_snpn/check_rfsim_auth_lab_ready.sh"     "$FIXTURES" "$AMF_BIN" "$GNB_BIN" "$UE_BIN"
else
  echo "DRY_RUN: lab preflight skipped because it performs live connectivity checks"
fi

if [ "$DRY_RUN" = "1" ]; then
  echo "DRY_RUN: evidence directory would be $CASE_DIR"
  echo "DRY_RUN: reset subscriber in $DB_NAME at $DB_HOST using $SUBSCRIBER_SQL"
  echo "DRY_RUN: start AMF with $AMF_CFG"
  echo "DRY_RUN: start gNB with $GNB_CFG"
  echo "DRY_RUN: start nrUE with $UE_CFG"
  echo "DRY_RUN: wait up to ${TIMEOUT_SECS}s for mode-specific auth evidence"
  echo "DRY_RUN: run log checker for mode=$MODE expected_amf_nid=$EXPECTED_AMF_NID"
  echo "PASS_HARP_RFSIM_AUTH_CASE_DRY_RUN"
  RESULT="DRY_RUN"
  FAIL_REASON="none"
  exit 0
fi

MYSQL_PWD="$DB_PASS" mysql -h "$DB_HOST" -u "$DB_USER" "$DB_NAME" < "$SUBSCRIBER_SQL"

: > "$AMF_LOG"
: > "$GNB_LOG"
: > "$UE_LOG"

"$AMF_BIN" -c "$AMF_CFG" -o >"$AMF_LOG" 2>&1 &
AMF_PID=$!
require_alive_after_grace AMF "$AMF_PID" "$AMF_LOG"

sudo "$GNB_BIN"   -O "$GNB_CFG"   --gNBs.[0].min_rxtxtime 6   --rfsim   --log_config.global_log_options level,nocolor,time   >"$GNB_LOG" 2>&1 &
GNB_PID=$!
require_alive_after_grace GNB "$GNB_PID" "$GNB_LOG"

sudo "$UE_BIN"   -r 106   --numerology 1   --band 78   -C 3619200000   --rfsim   --rfsimulator.[0].serveraddr 127.0.0.1   -O "$UE_CFG"   --log_config.global_log_options level,nocolor,time   >"$UE_LOG" 2>&1 &
UE_PID=$!

DEADLINE=$(( $(date +%s) + TIMEOUT_SECS ))
EVIDENCE_READY=0
while [ "$(date +%s)" -lt "$DEADLINE" ]; do
  if marker_ready; then
    EVIDENCE_READY=1
    break
  fi

  if ! kill -0 "$AMF_PID" 2>/dev/null; then
    fail_process AMF "$AMF_PID" "$AMF_LOG"
  fi
  if ! kill -0 "$GNB_PID" 2>/dev/null; then
    fail_process GNB "$GNB_PID" "$GNB_LOG"
  fi
  if ! kill -0 "$UE_PID" 2>/dev/null; then
    fail_process NRUE "$UE_PID" "$UE_LOG"
  fi

  sleep 1
done

if [ "$EVIDENCE_READY" -ne 1 ]; then
  FAIL_REASON="evidence_timeout"
  echo "ERROR: timed out after ${TIMEOUT_SECS}s waiting for required $MODE authentication evidence" >&2
  echo "----- AMF log tail -----" >&2
  tail -n 40 "$AMF_LOG" >&2 || true
  echo "----- gNB log tail -----" >&2
  tail -n 40 "$GNB_LOG" >&2 || true
  echo "----- nrUE log tail -----" >&2
  tail -n 40 "$UE_LOG" >&2 || true
  exit 21
fi

cleanup
AMF_PID=""
GNB_PID=""
UE_PID=""

if [ "$MODE" = "positive" ]; then
  python3 "$ROOT/tools/oai_snpn/check_rfsim_snpn_logs.py"     --mode positive     --ue-log "$UE_LOG"     --amf-log "$AMF_LOG"
else
  python3 "$ROOT/tools/oai_snpn/check_rfsim_snpn_logs.py"     --mode negative     --ue-log "$UE_LOG"     --amf-log "$AMF_LOG"     --expected-amf-nid "$EXPECTED_AMF_NID"
fi

RESULT="PASS"
FAIL_REASON="none"
echo "PASS_HARP_RFSIM_AUTH_CASE mode=$MODE"
