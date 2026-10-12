#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

write_valid() {
  cat >"$TMP/amf.yaml" <<'EOF'
database:
  host: mysql
  user: test
  type: mysql
  password: test
  database_name: oai_db
  connection_timeout: 300

amf:
  amf_name: "OAI-AMF"
  snpn_nid: "10000000001"
  support_features_options:
    enable_simple_scenario: yes
    enable_nssf: no
    enable_advanced_features: no
EOF
}

write_valid
python3 "$ROOT/tools/oai_amf_snpn/check_auth_only_config.py" "$TMP/amf.yaml"

write_valid
sed -i 's/enable_simple_scenario: yes/enable_simple_scenario: no/' "$TMP/amf.yaml"
if python3 "$ROOT/tools/oai_amf_snpn/check_auth_only_config.py" "$TMP/amf.yaml"; then
  echo "ERROR: auth-only checker accepted simple_scenario=no" >&2
  exit 1
fi

write_valid
sed -i 's/host: mysql/host: wrong-db/' "$TMP/amf.yaml"
if python3 "$ROOT/tools/oai_amf_snpn/check_auth_only_config.py" "$TMP/amf.yaml"; then
  echo "ERROR: auth-only checker accepted wrong MySQL host" >&2
  exit 1
fi

write_valid
sed -i 's/  snpn_nid:/    snpn_nid:/' "$TMP/amf.yaml"
if python3 "$ROOT/tools/oai_amf_snpn/check_auth_only_config.py" "$TMP/amf.yaml"; then
  echo "ERROR: auth-only checker accepted wrong snpn_nid indentation" >&2
  exit 1
fi

echo "PASS_AMF_AUTH_ONLY_CONFIG_SELFTEST"
