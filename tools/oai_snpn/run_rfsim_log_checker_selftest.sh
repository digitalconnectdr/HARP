#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

cat >"$TMP/ue-positive.log" <<'EOF'
PASS_V5G_SNPN_SELECT mcc=999 mnc=99 nid=10000000001
EOF

cat >"$TMP/amf-positive.log" <<'EOF'
HARP_SNPN_AMF_SNN 5G:mnc099.mcc999.3gppnetwork.org:10000000001
PASS_V5G_SNPN_AUTH_AMF nid=10000000001
PASS_V5G_SNPN_REGISTERED_AMF nid=10000000001
EOF

python3 "$ROOT/tools/oai_snpn/check_rfsim_snpn_logs.py" \
  --mode positive \
  --ue-log "$TMP/ue-positive.log" \
  --amf-log "$TMP/amf-positive.log"

cat >"$TMP/ue-negative.log" <<'EOF'
PASS_V5G_SNPN_SELECT mcc=999 mnc=99 nid=10000000001
EOF

cat >"$TMP/amf-negative.log" <<'EOF'
HARP_SNPN_AMF_SNN 5G:mnc099.mcc999.3gppnetwork.org:10000000002
HARP_SNPN_AUTH_REJECT_AMF nid=10000000002
EOF

python3 "$ROOT/tools/oai_snpn/check_rfsim_snpn_logs.py" \
  --mode negative \
  --ue-log "$TMP/ue-negative.log" \
  --amf-log "$TMP/amf-negative.log" \
  --expected-amf-nid 10000000002

cat >"$TMP/amf-false-success.log" <<'EOF'
HARP_SNPN_AMF_SNN 5G:mnc099.mcc999.3gppnetwork.org:10000000002
HARP_SNPN_AUTH_REJECT_AMF nid=10000000002
PASS_V5G_SNPN_AUTH_AMF nid=10000000002
EOF

if python3 "$ROOT/tools/oai_snpn/check_rfsim_snpn_logs.py" \
  --mode negative \
  --ue-log "$TMP/ue-negative.log" \
  --amf-log "$TMP/amf-false-success.log" \
  --expected-amf-nid 10000000002; then
  echo "ERROR: negative mismatch accepted a false authentication success" >&2
  exit 1
fi

echo "PASS_RFSIM_SNPN_LOG_CHECKER_SELFTEST"
