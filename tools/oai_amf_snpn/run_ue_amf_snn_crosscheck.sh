#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/build/snpn-snn-crosscheck"
rm -rf "$OUT"
mkdir -p "$OUT"

cat >"$OUT/ue_emit.c" <<'EOF'
#include "snn.h"
#include <stdio.h>

int main(void)
{
  const harp_serving_network_id_t sn = {
      .plmn = {.mcc = 999, .mnc = 99, .mnc_digit_length = 2},
      .has_nid = true,
      .nid = 0x10000000001ULL,
  };
  char out[128] = {0};
  if (!harp_format_serving_network_name(&sn, out, sizeof(out)))
    return 2;
  puts(out);
  return 0;
}
EOF

cat >"$OUT/amf_emit.cpp" <<'EOF'
#include "amf_snn.hpp"
#include <iostream>

int main()
{
  std::cout << harp_amf_snn("99", "999", 0x10000000001ULL) << "\n";
  return 0;
}
EOF

cc -std=c11 -Wall -Wextra -Werror \
  "$ROOT/tools/oai_snpn/snn.c" \
  "$OUT/ue_emit.c" \
  -I"$ROOT/tools/oai_snpn" \
  -o "$OUT/ue_emit"

c++ -std=c++17 -Wall -Wextra -Werror \
  "$ROOT/tools/oai_amf_snpn/amf_snn.cpp" \
  "$OUT/amf_emit.cpp" \
  -I"$ROOT/tools/oai_amf_snpn" \
  -o "$OUT/amf_emit"

"$OUT/ue_emit" >"$OUT/ue.txt"
"$OUT/amf_emit" >"$OUT/amf.txt"

diff -u "$OUT/ue.txt" "$OUT/amf.txt"

EXPECTED="5G:mnc099.mcc999.3gppnetwork.org:10000000001"
ACTUAL="$(cat "$OUT/ue.txt")"
test "$ACTUAL" = "$EXPECTED"

echo "PASS_UE_AMF_SNN_MATCH"
