#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

cat >"$TMP/ue-positive.log" <<'EOF'
PASS_V5G_SNPN_SELECT mcc=999 mnc=99 nid=10000000001
HARP_SNPN_AUTH_RESPONSE_UE nid=10000000001
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

python3 "$ROOT/tools/oai_snpn/check_rfsim_snpn_logs.py" \
  --mode positive \
  --require-registration \
  --ue-log "$TMP/ue-positive.log" \
  --amf-log "$TMP/amf-positive.log"

cat >"$TMP/amf-auth-only.log" <<'EOF'
HARP_SNPN_AMF_SNN 5G:mnc099.mcc999.3gppnetwork.org:10000000001
PASS_V5G_SNPN_AUTH_AMF nid=10000000001
EOF

python3 "$ROOT/tools/oai_snpn/check_rfsim_snpn_logs.py" \
  --mode positive \
  --ue-log "$TMP/ue-positive.log" \
  --amf-log "$TMP/amf-auth-only.log"

if python3 "$ROOT/tools/oai_snpn/check_rfsim_snpn_logs.py" \
  --mode positive \
  --require-registration \
  --ue-log "$TMP/ue-positive.log" \
  --amf-log "$TMP/amf-auth-only.log"; then
  echo "ERROR: registration-required gate accepted auth-only log" >&2
  exit 1
fi

cat >"$TMP/ue-negative.log" <<'EOF'
PASS_V5G_SNPN_SELECT mcc=999 mnc=99 nid=10000000001
HARP_SNPN_AUTH_RESPONSE_UE nid=10000000001
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

cat >"$TMP/amf-stale-positive-before-negative.log" <<'EOF'
HARP_SNPN_AMF_SNN 5G:mnc099.mcc999.3gppnetwork.org:10000000001
PASS_V5G_SNPN_AUTH_AMF nid=10000000001
HARP_SNPN_AMF_SNN 5G:mnc099.mcc999.3gppnetwork.org:10000000002
HARP_SNPN_AUTH_REJECT_AMF nid=10000000002
EOF

python3 "$ROOT/tools/oai_snpn/check_rfsim_snpn_logs.py" \
  --mode negative \
  --ue-log "$TMP/ue-negative.log" \
  --amf-log "$TMP/amf-stale-positive-before-negative.log" \
  --expected-amf-nid 10000000002

cat >"$TMP/amf-stale-reject-before-positive.log" <<'EOF'
HARP_SNPN_AMF_SNN 5G:mnc099.mcc999.3gppnetwork.org:10000000002
HARP_SNPN_AUTH_REJECT_AMF nid=10000000002
HARP_SNPN_AMF_SNN 5G:mnc099.mcc999.3gppnetwork.org:10000000001
PASS_V5G_SNPN_AUTH_AMF nid=10000000001
EOF

python3 "$ROOT/tools/oai_snpn/check_rfsim_snpn_logs.py" \
  --mode positive \
  --ue-log "$TMP/ue-positive.log" \
  --amf-log "$TMP/amf-stale-reject-before-positive.log"

echo "PASS_RFSIM_LOG_SESSION_CORRELATION"

cat >"$TMP/amf-plmn-mismatch.log" <<'EOF'
HARP_SNPN_AMF_SNN 5G:mnc099.mcc998.3gppnetwork.org:10000000001
PASS_V5G_SNPN_AUTH_AMF nid=10000000001
EOF

if python3 "$ROOT/tools/oai_snpn/check_rfsim_snpn_logs.py" \
  --mode positive \
  --ue-log "$TMP/ue-positive.log" \
  --amf-log "$TMP/amf-plmn-mismatch.log"; then
  echo "ERROR: checker accepted PLMN mismatch" >&2
  exit 1
fi

echo "PASS_RFSIM_LOG_PLMN_MISMATCH_REJECTED"

cat >"$TMP/amf-positive-contradictory.log" <<'EOF'
HARP_SNPN_AMF_SNN 5G:mnc099.mcc999.3gppnetwork.org:10000000001
PASS_V5G_SNPN_AUTH_AMF nid=10000000001
HARP_SNPN_AUTH_REJECT_AMF nid=10000000001
EOF

if python3 "$ROOT/tools/oai_snpn/check_rfsim_snpn_logs.py" \
  --mode positive \
  --ue-log "$TMP/ue-positive.log" \
  --amf-log "$TMP/amf-positive-contradictory.log"; then
  echo "ERROR: checker accepted contradictory positive auth/reject markers" >&2
  exit 1
fi

cat >"$TMP/amf-registration-before-auth.log" <<'EOF'
HARP_SNPN_AMF_SNN 5G:mnc099.mcc999.3gppnetwork.org:10000000001
PASS_V5G_SNPN_REGISTERED_AMF nid=10000000001
PASS_V5G_SNPN_AUTH_AMF nid=10000000001
EOF

if python3 "$ROOT/tools/oai_snpn/check_rfsim_snpn_logs.py" \
  --mode positive \
  --require-registration \
  --ue-log "$TMP/ue-positive.log" \
  --amf-log "$TMP/amf-registration-before-auth.log"; then
  echo "ERROR: checker accepted registration before authentication success" >&2
  exit 1
fi

echo "PASS_RFSIM_LOG_CAUSAL_ORDER_REJECTED"

cat >"$TMP/ue-auth-failure.log" <<'EOF'
PASS_V5G_SNPN_SELECT mcc=999 mnc=99 nid=10000000001
HARP_SNPN_AUTH_FAILURE_UE cause=21
EOF

if python3 "$ROOT/tools/oai_snpn/check_rfsim_snpn_logs.py" \
  --mode positive \
  --ue-log "$TMP/ue-auth-failure.log" \
  --amf-log "$TMP/amf-positive.log"; then
  echo "ERROR: checker accepted UE Authentication Failure" >&2
  exit 1
fi

echo "PASS_RFSIM_LOG_UE_AUTH_FAILURE_REJECTED"
