#!/usr/bin/env bash
set -euo pipefail

EXPECTED_ASN1C_COMMIT="940dd5fa9f3917913fd487b13dfddfacd0ded06e"

required_cmds=(
  git
  cmake
  ninja
  cc
  c++
  python3
  bison
  flex
  autoreconf
  make
)

missing=0

echo "HARP OAI/AMF build environment preflight"
echo

for cmd in "${required_cmds[@]}"; do
  if command -v "$cmd" >/dev/null 2>&1; then
    printf 'FOUND   %-12s %s\n' "$cmd" "$(command -v "$cmd")"
  else
    printf 'MISSING %-12s\n' "$cmd"
    missing=1
  fi
done

echo
if command -v asn1c >/dev/null 2>&1; then
  echo "FOUND   asn1c       $(command -v asn1c)"
  asn1c -h 2>&1 | head -n 2 || true
else
  echo "MISSING asn1c"
  missing=1
fi

echo
echo "OAI expected asn1c source commit:"
echo "$EXPECTED_ASN1C_COMMIT"
echo
echo "Reference OAI build helper installs:"
echo "  bison"
echo "  flex"
echo "  mouse07410/asn1c @ $EXPECTED_ASN1C_COMMIT"

if [ "$missing" -ne 0 ]; then
  echo
  echo "FAIL_HARP_OAI_BUILD_ENV"
  exit 1
fi

echo
echo "PASS_HARP_OAI_BUILD_ENV_TOOLS"
