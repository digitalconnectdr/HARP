#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -lt 1 ]; then
  echo "usage: $0 <openairinterface5g-checkout> [--check]" >&2
  exit 2
fi

CHECKOUT="$1"
shift

EXPECTED_HEAD="f8f769592a7030be88ede4bb5ca66fa1ca6a80e0"

if ! git -C "$CHECKOUT" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "ERROR: not a Git worktree/checkout: $CHECKOUT" >&2
  exit 3
fi

HEAD="$(git -C "$CHECKOUT" rev-parse HEAD)"
if [ "$HEAD" != "$EXPECTED_HEAD" ]; then
  echo "ERROR: OAI checkout must be pinned to $EXPECTED_HEAD" >&2
  echo "       current HEAD: $HEAD" >&2
  exit 4
fi

if [ -n "$(git -C "$CHECKOUT" status --porcelain)" ]; then
  echo "ERROR: OAI checkout must be clean before blueprint application." >&2
  exit 5
fi
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
  "$ROOT/patches/oai/0003d-snpn-cell-access-codec-test.patch" \
  "$ROOT/patches/oai/0004-nr-ue-snpn-selection.patch" \
  "$ROOT/patches/oai/0004b-nr-ue-snpn-selection-unit-test.patch" \
  "$ROOT/patches/oai/0004c-snpn-full-sib1-codec-test.patch" \
  "$ROOT/patches/oai/0004d-nr-ue-snpn-auth-evidence.patch"
