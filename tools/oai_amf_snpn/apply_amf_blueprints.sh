#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -lt 1 ]; then
  echo "usage: $0 <oai-cn5g-amf-checkout> [--check]" >&2
  exit 2
fi

CHECKOUT="$1"
shift

EXPECTED_HEAD="5eedea557a3745b13ed9ec4bf29e6a28bd912574"

if ! git -C "$CHECKOUT" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "ERROR: not a Git worktree/checkout: $CHECKOUT" >&2
  exit 3
fi

HEAD="$(git -C "$CHECKOUT" rev-parse HEAD)"
if [ "$HEAD" != "$EXPECTED_HEAD" ]; then
  echo "ERROR: AMF checkout must be pinned to $EXPECTED_HEAD" >&2
  echo "       current HEAD: $HEAD" >&2
  exit 4
fi

if [ -n "$(git -C "$CHECKOUT" status --porcelain)" ]; then
  echo "ERROR: AMF checkout must be clean before blueprint application." >&2
  exit 5
fi
MODE=()
if [ "${1:-}" = "--check" ]; then
  MODE=(--check)
fi

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
APPLIER="$ROOT/tools/apply_context_blueprints.py"

python3 "$APPLIER" "${MODE[@]}" "$CHECKOUT" \
  "$ROOT/patches/oai-amf/0001-amf-optional-snpn-snn-formatter.patch" \
  "$ROOT/patches/oai-amf/0002-amf-lab-snpn-nid-config.patch"
