#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
LAB_RUNNER="$ROOT/tools/oai_snpn/run_rfsim_auth_lab.sh"

bash -n "$LAB_RUNNER"
grep -q 'prepare_rfsim_lab_configs.py' "$LAB_RUNNER"
grep -q 'run_pre_rfsim_gates.sh' "$LAB_RUNNER"
grep -q 'run_amf_build_gate.sh' "$LAB_RUNNER"
grep -q 'run_rfsim_auth_pair.sh' "$LAB_RUNNER"

fixture_line="$(grep -n 'prepare_rfsim_lab_configs.py' "$LAB_RUNNER" | head -n1 | cut -d: -f1)"
ran_line="$(grep -n 'run_pre_rfsim_gates.sh' "$LAB_RUNNER" | head -n1 | cut -d: -f1)"
amf_line="$(grep -n 'run_amf_build_gate.sh' "$LAB_RUNNER" | head -n1 | cut -d: -f1)"

if [ "$fixture_line" -ge "$ran_line" ] || [ "$fixture_line" -ge "$amf_line" ]; then
  echo "ERROR: fixtures must be generated before blueprint build gates modify the worktrees" >&2
  exit 1
fi

echo "PASS_HARP_RFSIM_AUTH_LAB_RUNNER_SELFTEST"
