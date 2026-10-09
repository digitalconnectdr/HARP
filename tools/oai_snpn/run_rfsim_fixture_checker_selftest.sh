#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

write_valid() {
  cat >"$TMP/amf.harp-snpn-positive.yaml" <<'EOF'
amf:
  amf_name: "OAI-AMF"
  snpn_nid: "10000000001"
  guami:
    - mcc: 999
      mnc: 99
      amf_region_id: 01
  plmn_support_list:
    - mcc: 999
      mnc: 99
      tac: 1
  support_features:
    enable_simple_scenario: yes
EOF

  cat >"$TMP/amf.harp-snpn-negative.yaml" <<'EOF'
amf:
  amf_name: "OAI-AMF"
  snpn_nid: "10000000002"
  guami:
    - mcc: 999
      mnc: 99
      amf_region_id: 01
  plmn_support_list:
    - mcc: 999
      mnc: 99
      tac: 1
  support_features:
    enable_simple_scenario: yes
EOF

  cat >"$TMP/nrue.harp-snpn.conf" <<'EOF'
uicc0 = {
  imsi = "999990000000001";
  nmc_size = 2;
  snpn_nid = "10000000001";
  key = "fec86ba6eb707ed08905757b1bb44b8f";
  opc = "C42449363BBAD02B66D16BC975D77CC1";
  amf = "8000";
  sqn = "000000";
};
EOF

  cat >"$TMP/gnb.harp-snpn.conf" <<'EOF'
gNBs =
(
 {
   plmn_list = ({ mcc = 999; mnc = 99; mnc_length = 2; snssaiList = ({ sst = 1, sd = 0xffffff }) });
   snpn = {
     enabled = "yes";
     nid = "10000000001";
   };
 }
);
EOF

  cat >"$TMP/harp_subscriber.sql" <<'EOF'
DELETE FROM users WHERE imsi = '999990000000001';
INSERT INTO users (
  imsi, msisdn, imei, imei_sv, ms_ps_status, rau_tau_timer,
  ue_ambr_ul, ue_ambr_dl, access_restriction, mme_cap,
  mmeidentity_idmmeidentity, `key`, `RFSP-Index`, urrp_mme,
  sqn, rand, OPc
) VALUES (
  '999990000000001', '1', '55000000000000', NULL, 'PURGED', 50,
  40000000, 100000000, 47, 0, 1,
  UNHEX('fec86ba6eb707ed08905757b1bb44b8f'), 0, 0, 0,
  UNHEX('000102030405060708090A0B0C0D0E0F'),
  UNHEX('C42449363BBAD02B66D16BC975D77CC1')
);
EOF
}

write_valid
python3 "$ROOT/tools/oai_snpn/check_rfsim_fixture_pair.py" "$TMP"

# Negative: extra AMF difference beyond NID.
printf '\n# accidental extra change\n' >>"$TMP/amf.harp-snpn-negative.yaml"
if python3 "$ROOT/tools/oai_snpn/check_rfsim_fixture_pair.py" "$TMP"; then
  echo "ERROR: checker accepted extra AMF difference" >&2
  exit 1
fi

# Negative: UE/SQL Ki mismatch.
write_valid
sed -i 's/fec86ba6eb707ed08905757b1bb44b8f/00000000000000000000000000000000/' "$TMP/nrue.harp-snpn.conf"
if python3 "$ROOT/tools/oai_snpn/check_rfsim_fixture_pair.py" "$TMP"; then
  echo "ERROR: checker accepted Ki mismatch" >&2
  exit 1
fi

# Negative: SQL SQN baseline not zero.
write_valid
python3 - "$TMP/harp_subscriber.sql" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1])
s = p.read_text()
needle = "  UNHEX('fec86ba6eb707ed08905757b1bb44b8f'), 0, 0, 0,"
if needle not in s:
    raise SystemExit("self-test SQN anchor missing")
p.write_text(s.replace(
    needle,
    "  UNHEX('fec86ba6eb707ed08905757b1bb44b8f'), 0, 0, 1,",
    1,
))
PY
if python3 "$ROOT/tools/oai_snpn/check_rfsim_fixture_pair.py" "$TMP"; then
  echo "ERROR: checker accepted non-zero SQL SQN baseline" >&2
  exit 1
fi

echo "PASS_RFSIM_FIXTURE_INVARIANT_SELFTEST"
