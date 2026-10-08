#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/build/oai-snpn-snn"
rm -rf "$OUT"
mkdir -p "$OUT"

cc -std=c11 -Wall -Wextra -Werror \
  "$ROOT/tools/oai_snpn/snn.c" \
  "$ROOT/tools/oai_snpn/snn_selftest.c" \
  -I"$ROOT/tools/oai_snpn" \
  -o "$OUT/snn_selftest"

"$OUT/snn_selftest"
