#!/usr/bin/env bash
set -euo pipefail

AMF_ANCHOR="5eedea557a3745b13ed9ec4bf29e6a28bd912574"

if [ "$#" -ne 1 ]; then
  echo "usage: $0 <oai-cn5g-amf-checkout>" >&2
  exit 2
fi

CHECKOUT="$(cd "$1" && pwd)"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

if ! git -C "$CHECKOUT" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "ERROR: not an AMF git checkout/worktree: $CHECKOUT" >&2
  exit 2
fi

HEAD="$(git -C "$CHECKOUT" rev-parse HEAD)"
if [ "$HEAD" != "$AMF_ANCHOR" ]; then
  echo "ERROR: AMF checkout must be pinned to $AMF_ANCHOR" >&2
  echo "       current HEAD: $HEAD" >&2
  exit 3
fi

if [ -n "$(git -C "$CHECKOUT" status --porcelain)" ]; then
  echo "ERROR: AMF checkout must be clean before full blueprint validation." >&2
  exit 4
fi

OUTPUT="$(bash "$ROOT/tools/oai_amf_snpn/apply_amf_blueprints.sh" "$CHECKOUT" --check)"
printf '%s\n' "$OUTPUT"

HUNKS="$(printf '%s\n' "$OUTPUT" | sed -n 's/^PASS_CONTEXT_BLUEPRINT_CHECK hunks=\([0-9][0-9]*\)$/\1/p')"
if [ -z "$HUNKS" ]; then
  echo "ERROR: AMF context-blueprint PASS marker missing." >&2
  exit 5
fi

EXPECTED_HUNKS=20
if [ "$HUNKS" -ne "$EXPECTED_HUNKS" ]; then
  echo "ERROR: expected $EXPECTED_HUNKS AMF blueprint hunks, found $HUNKS" >&2
  exit 6
fi

PATCH_COUNT="$(grep -c '^  "\$ROOT/patches/oai-amf/' "$ROOT/tools/oai_amf_snpn/apply_amf_blueprints.sh" || true)"
if [ "$PATCH_COUNT" -ne 2 ]; then
  echo "ERROR: expected 2 AMF blueprints in wrapper, found $PATCH_COUNT" >&2
  exit 7
fi

echo "AMF_BLUEPRINT_COUNT=$PATCH_COUNT"
echo "AMF_BLUEPRINT_HUNK_COUNT=$HUNKS"
echo "AMF_BLUEPRINT_EXPECTED_HUNKS=$EXPECTED_HUNKS"
echo "PASS_HARP_AMF_FULL_BLUEPRINT_CHAIN"
