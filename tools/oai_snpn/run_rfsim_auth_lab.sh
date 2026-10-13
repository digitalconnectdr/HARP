#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat >&2 <<'USAGE'
usage: run_rfsim_auth_lab.sh <ran-checkout> <amf-checkout> [fixture-dir]

The RAN and AMF checkouts must be disposable, clean worktrees pinned to the
commits documented by HARP. This runner intentionally generates fixtures
BEFORE applying HARP build blueprints, because the build gates modify those
worktrees.

Optional environment:
  HARP_RAN_BUILD_DIR=<dir>
  HARP_AMF_BUILD_DIR=<dir>
  HARP_PAIR_ID=<id>
  HARP_LOG_DIR=<dir>
  HARP_RFSIM_TIMEOUT=<seconds>
  HARP_STARTUP_GRACE=<seconds>
  HARP_TOPOLOGY_MODE=host|container
  HARP_TOPOLOGY_CONFIRMED=1
  HARP_DRY_RUN=1
USAGE
  exit 2
}

[ "$#" -ge 2 ] && [ "$#" -le 3 ] || usage

RAN="$(cd "$1" && pwd)"
AMF="$(cd "$2" && pwd)"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PAIR_ID="${HARP_PAIR_ID:-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
FIXTURES="${3:-/tmp/harp-snpn-rfsim-$PAIR_ID}"
RAN_BUILD="${HARP_RAN_BUILD_DIR:-$RAN/build-harp-snpn}"
AMF_BUILD="${HARP_AMF_BUILD_DIR:-$AMF/build-harp-snpn}"
AMF_BIN="$AMF_BUILD/amf"

if [ "${HARP_DRY_RUN:-0}" = "1" ]; then
  echo "DRY_RUN: generate fixtures from clean pinned RAN/AMF checkouts -> $FIXTURES"
  echo "DRY_RUN: build/test RAN -> $RAN_BUILD"
  echo "DRY_RUN: build/test AMF -> $AMF_BUILD"
  echo "DRY_RUN: run paired RFsim auth experiment pair_id=$PAIR_ID"
  echo "PASS_HARP_RFSIM_AUTH_LAB_DRY_RUN"
  exit 0
fi

echo "[HARP] step 1/5: build environment preflight"
bash "$ROOT/tools/oai_snpn/check_build_environment.sh"

echo "[HARP] step 2/5: generate RFsim fixtures while both worktrees are clean"
python3 "$ROOT/tools/oai_snpn/prepare_rfsim_lab_configs.py"   --ran "$RAN"   --amf "$AMF"   --out "$FIXTURES"

echo "[HARP] step 3/5: apply/build/test pinned RAN"
bash "$ROOT/tools/oai_snpn/run_pre_rfsim_gates.sh" "$RAN" "$RAN_BUILD"

echo "[HARP] step 4/5: apply/build pinned AMF"
bash "$ROOT/tools/oai_amf_snpn/run_amf_build_gate.sh" "$AMF" "$AMF_BUILD"

echo "[HARP] step 5/5: run positive/negative paired RFsim authentication"
HARP_PAIR_ID="$PAIR_ID" bash "$ROOT/tools/oai_snpn/run_rfsim_auth_pair.sh"   "$RAN_BUILD"   "$AMF_BIN"   "$FIXTURES"

echo "PASS_HARP_RFSIM_AUTH_LAB pair_id=$PAIR_ID"
