#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

bash "$ROOT/tools/oai_snpn/run_nid44_selftest.sh"
bash "$ROOT/tools/oai_snpn/run_snn_selftest.sh"
bash "$ROOT/tools/oai_amf_snpn/run_amf_snn_selftest.sh"
bash "$ROOT/tools/oai_amf_snpn/run_ue_amf_snn_crosscheck.sh"
python3 "$ROOT/tools/oai_snpn/kdf_reference.py"
python3 "$ROOT/tools/oai_amf_snpn/sqn_reference.py"

echo "PASS_HARP_SNPN_STANDALONE_SUITE"
