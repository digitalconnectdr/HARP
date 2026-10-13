#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
RUNNER="$ROOT/tools/oai_snpn/run_rfsim_auth_case.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

mkdir -p "$TMP/ran" "$TMP/fixtures"
: > "$TMP/fake-amf"

cat > "$TMP/fixtures/amf.harp-snpn-positive.yaml" <<'YAML'
database:
  host: mysql
  user: test
  password: test
  database_name: oai_db
YAML
cp "$TMP/fixtures/amf.harp-snpn-positive.yaml" "$TMP/fixtures/amf.harp-snpn-negative.yaml"
: > "$TMP/fixtures/gnb.harp-snpn.conf"
: > "$TMP/fixtures/nrue.harp-snpn.conf"
: > "$TMP/fixtures/harp_subscriber.sql"

for mode in positive negative; do
  out="$(HARP_DRY_RUN=1 HARP_RUN_ID=selftest-$mode bash "$RUNNER" \
    "$mode" "$TMP/ran" "$TMP/fake-amf" "$TMP/fixtures")"
  grep -q 'PASS_HARP_RFSIM_AUTH_CASE_DRY_RUN' <<<"$out"
  grep -q "run log checker for mode=$mode" <<<"$out"
done

echo "PASS_HARP_RFSIM_AUTH_CASE_RUNNER_SELFTEST"
