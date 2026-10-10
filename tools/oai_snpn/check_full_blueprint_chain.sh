#!/usr/bin/env bash
set -euo pipefail

OAI_ANCHOR="f8f769592a7030be88ede4bb5ca66fa1ca6a80e0"

if [ "$#" -ne 1 ]; then
  echo "usage: $0 <openairinterface5g-checkout>" >&2
  exit 2
fi

CHECKOUT="$(cd "$1" && pwd)"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

if ! git -C "$CHECKOUT" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "ERROR: not an OAI git checkout/worktree: $CHECKOUT" >&2
  exit 2
fi

HEAD="$(git -C "$CHECKOUT" rev-parse HEAD)"
if [ "$HEAD" != "$OAI_ANCHOR" ]; then
  echo "ERROR: OAI checkout must be pinned to $OAI_ANCHOR" >&2
  echo "       current HEAD: $HEAD" >&2
  exit 3
fi

if [ -n "$(git -C "$CHECKOUT" status --porcelain)" ]; then
  echo "ERROR: OAI checkout must be clean before full blueprint validation." >&2
  exit 4
fi

OUTPUT="$(bash "$ROOT/tools/oai_snpn/apply_oai_blueprints.sh" "$CHECKOUT" --check)"
printf '%s\n' "$OUTPUT"

HUNKS="$(printf '%s\n' "$OUTPUT" | sed -n 's/^PASS_CONTEXT_BLUEPRINT_CHECK hunks=\([0-9][0-9]*\)$/\1/p')"
if [ -z "$HUNKS" ]; then
  echo "ERROR: full context-blueprint PASS marker missing." >&2
  exit 5
fi

PATCH_COUNT="$(grep -c '^  "\$ROOT/patches/oai/' "$ROOT/tools/oai_snpn/apply_oai_blueprints.sh" || true)"
if [ "$PATCH_COUNT" -ne 12 ]; then
  echo "ERROR: expected 12 OAI blueprints in wrapper, found $PATCH_COUNT" >&2
  exit 6
fi

echo "OAI_BLUEPRINT_COUNT=$PATCH_COUNT"
echo "OAI_BLUEPRINT_HUNK_COUNT=$HUNKS"
echo "PASS_HARP_OAI_FULL_BLUEPRINT_CHAIN"
