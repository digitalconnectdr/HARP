#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/build/oai-snpn-nid44"
rm -rf "$OUT"
mkdir -p "$OUT"

cc -std=c11 -Wall -Wextra -Werror \
  "$ROOT/tools/oai_snpn/nid44.c" \
  "$ROOT/tools/oai_snpn/nid44_selftest.c" \
  -o "$OUT/nid44_selftest"

"$OUT/nid44_selftest"
