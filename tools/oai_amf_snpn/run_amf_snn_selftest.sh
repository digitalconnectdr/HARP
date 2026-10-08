#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/build/oai-amf-snn"
rm -rf "$OUT"
mkdir -p "$OUT"

c++ -std=c++17 -Wall -Wextra -Werror \
  "$ROOT/tools/oai_amf_snpn/amf_snn.cpp" \
  "$ROOT/tools/oai_amf_snpn/amf_snn_selftest.cpp" \
  -I"$ROOT/tools/oai_amf_snpn" \
  -o "$OUT/amf_snn_selftest"

"$OUT/amf_snn_selftest"
