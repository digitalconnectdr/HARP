#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PAIR_RUNNER="$ROOT/tools/oai_snpn/run_rfsim_auth_pair.sh"

bash -n "$PAIR_RUNNER"
grep -q 'positive "$RAN_BUILD" "$AMF_BIN" "$FIXTURES"' "$PAIR_RUNNER"
grep -q 'negative "$RAN_BUILD" "$AMF_BIN" "$FIXTURES"' "$PAIR_RUNNER"
grep -q 'PASS_HARP_RFSIM_AUTH_PAIR' "$PAIR_RUNNER"

echo "PASS_HARP_RFSIM_AUTH_PAIR_RUNNER_SELFTEST"
