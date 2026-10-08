#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -lt 1 ]; then
  echo "usage: $0 <oai-cn5g-amf-checkout> [--check]" >&2
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
  "$ROOT/patches/oai-amf/0001-amf-optional-snpn-snn-formatter.patch" \
  "$ROOT/patches/oai-amf/0002-amf-lab-snpn-nid-config.patch"
