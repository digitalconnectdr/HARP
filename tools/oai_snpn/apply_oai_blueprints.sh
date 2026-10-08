#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -lt 1 ]; then
  echo "usage: $0 <openairinterface5g-checkout> [--check]" >&2
  exit 2
fi

CHECKOUT="$1"
shift
MODE=()
if [ "${1:-}" = "--check" ]; then
  MODE=(--check)
fi

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
APPLIER="$ROOT/tools/apply_context_blueprints.py"

python3 "$APPLIER" "${MODE[@]}" "$CHECKOUT" \
  "$ROOT/patches/oai/0001-nr-ue-serving-network-baseline-refactor.patch" \
  "$ROOT/patches/oai/0001b-nr-ue-snn-formatter-baseline-test.patch" \
  "$ROOT/patches/oai/0001c-nr-ue-kdf-baseline-test.patch" \
  "$ROOT/patches/oai/0002-nr-ue-snpn-serving-network-name.patch" \
  "$ROOT/patches/oai/0003a-gnb-snpn-config-plumbing.patch" \
  "$ROOT/patches/oai/0003b-gnb-snpn-sib1-encoding.patch" \
  "$ROOT/patches/oai/0003c-snpn-npn-codec-test.patch" \
  "$ROOT/patches/oai/0004-nr-ue-snpn-selection.patch"
