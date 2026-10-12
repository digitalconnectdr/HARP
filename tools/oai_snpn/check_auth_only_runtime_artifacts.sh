#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 3 ]; then
  echo "usage: $0 <ran-build-dir> <amf-binary> <fixture-dir>" >&2
  exit 2
fi

RAN_BUILD="$(cd "$1" && pwd)"
AMF_BIN="$(readlink -f "$2")"
FIXTURES="$(cd "$3" && pwd)"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

GNB_BIN="$RAN_BUILD/nr-softmodem"
UE_BIN="$RAN_BUILD/nr-uesoftmodem"
RFSIM_LIB="$RAN_BUILD/librfsimulator.so"

for exe in "$GNB_BIN" "$UE_BIN" "$AMF_BIN"; do
  if [ ! -x "$exe" ]; then
    echo "ERROR: required executable missing or not executable: $exe" >&2
    exit 3
  fi
done

if [ ! -f "$RFSIM_LIB" ]; then
  echo "ERROR: RFsim module missing: $RFSIM_LIB" >&2
  exit 3
fi

for required in   gnb.harp-snpn.conf   nrue.harp-snpn.conf   amf.harp-snpn-positive.yaml   amf.harp-snpn-negative.yaml   harp_subscriber.sql; do
  if [ ! -f "$FIXTURES/$required" ]; then
    echo "ERROR: required fixture missing: $FIXTURES/$required" >&2
    exit 4
  fi
done

python3 "$ROOT/tools/oai_snpn/check_rfsim_fixture_pair.py" "$FIXTURES"
python3 "$ROOT/tools/oai_amf_snpn/check_auth_only_config.py"   "$FIXTURES/amf.harp-snpn-positive.yaml"
python3 "$ROOT/tools/oai_amf_snpn/check_auth_only_config.py"   "$FIXTURES/amf.harp-snpn-negative.yaml"

echo "RFSIM_GNB_BINARY=$GNB_BIN"
echo "RFSIM_UE_BINARY=$UE_BIN"
echo "RFSIM_MODULE=$RFSIM_LIB"
echo "AMF_BINARY=$AMF_BIN"
echo "PASS_HARP_AUTH_ONLY_RUNTIME_ARTIFACTS"
