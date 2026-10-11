#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 4 ]; then
  echo "usage: $0 <fixture-dir> <amf-bin> <nr-softmodem> <nr-uesoftmodem>" >&2
  exit 2
fi

FIXTURES="$(cd "$1" && pwd)"
AMF_BIN="$2"
GNB_BIN="$3"
UE_BIN="$4"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

require_file() {
  if [ ! -f "$1" ]; then
    echo "ERROR: missing file: $1" >&2
    exit 3
  fi
}

require_exec() {
  if [ ! -x "$1" ]; then
    echo "ERROR: missing/non-executable binary: $1" >&2
    exit 4
  fi
}

for name in \
  gnb.harp-snpn.conf \
  nrue.harp-snpn.conf \
  amf.harp-snpn-positive.yaml \
  amf.harp-snpn-negative.yaml \
  harp_subscriber.sql; do
  require_file "$FIXTURES/$name"
done

require_exec "$AMF_BIN"
require_exec "$GNB_BIN"
require_exec "$UE_BIN"

python3 "$ROOT/tools/oai_snpn/check_rfsim_fixture_pair.py" "$FIXTURES"

AMF_CFG="$FIXTURES/amf.harp-snpn-positive.yaml"

readarray -t DB_VALUES < <(python3 - "$AMF_CFG" <<'PY'
from pathlib import Path
import sys

lines = Path(sys.argv[1]).read_text().splitlines()
inside = False
values = {}
for line in lines:
    if line == "database:":
        inside = True
        continue
    if inside:
        if line and not line.startswith(" "):
            break
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or ":" not in stripped:
            continue
        k, v = stripped.split(":", 1)
        values[k.strip()] = v.strip().strip('"')

for key in ("host", "user", "password", "database_name"):
    print(values.get(key, ""))
PY
)

DB_HOST="${HARP_MYSQL_HOST:-${DB_VALUES[0]}}"
DB_USER="${HARP_MYSQL_USER:-${DB_VALUES[1]}}"
DB_PASS="${HARP_MYSQL_PASSWORD:-${DB_VALUES[2]}}"
DB_NAME="${HARP_MYSQL_DATABASE:-${DB_VALUES[3]}}"

for pair in \
  "host:$DB_HOST" \
  "user:$DB_USER" \
  "password:$DB_PASS" \
  "database:$DB_NAME"; do
  key="${pair%%:*}"
  value="${pair#*:}"
  if [ -z "$value" ]; then
    echo "ERROR: AMF database $key is empty" >&2
    exit 5
  fi
done

echo "AMF_BINARY=$AMF_BIN"
echo "GNB_BINARY=$GNB_BIN"
echo "NRUE_BINARY=$UE_BIN"
echo "MYSQL_HOST=$DB_HOST"
echo "MYSQL_USER=$DB_USER"
echo "MYSQL_DATABASE=$DB_NAME"

if command -v getent >/dev/null 2>&1; then
  if ! getent hosts "$DB_HOST" >/dev/null 2>&1; then
    echo "ERROR: MySQL host does not resolve: $DB_HOST" >&2
    echo "       Override with HARP_MYSQL_HOST=<reachable-host> if needed." >&2
    exit 6
  fi
  echo "PASS_HARP_MYSQL_HOST_RESOLUTION"
else
  echo "WARN: getent unavailable; MySQL host resolution not checked"
fi

if ! command -v mysql >/dev/null 2>&1; then
  echo "ERROR: mysql client is required for subscriber reset/connectivity checks" >&2
  exit 7
fi

if ! MYSQL_PWD="$DB_PASS" mysql \
    -h "$DB_HOST" \
    -u "$DB_USER" \
    "$DB_NAME" \
    -Nse 'SELECT 1' 2>/dev/null | grep -qx '1'; then
  echo "ERROR: MySQL connectivity/query preflight failed" >&2
  exit 8
fi
echo "PASS_HARP_MYSQL_CONNECTIVITY"

N2_IFACE="${HARP_AMF_N2_IFACE:-$(python3 - "$AMF_CFG" <<'PY'
from pathlib import Path
import sys

lines = Path(sys.argv[1]).read_text().splitlines()
in_amf_nf = False
in_n2 = False
for line in lines:
    if line == "  amf:":
        in_amf_nf = True
        continue
    if in_amf_nf and line.startswith("  ") and not line.startswith("    ") and line.strip().endswith(":"):
        break
    if in_amf_nf and line.strip() == "n2:":
        in_n2 = True
        continue
    if in_n2:
        if line.strip().startswith("interface_name:"):
            print(line.split(":", 1)[1].strip().strip('"'))
            break
        if line and not line.startswith("      "):
            break
PY
)}"

if [ -n "$N2_IFACE" ]; then
  echo "AMF_N2_INTERFACE=$N2_IFACE"
  if command -v ip >/dev/null 2>&1; then
    if ! ip link show "$N2_IFACE" >/dev/null 2>&1; then
      echo "ERROR: configured AMF N2 interface is not present: $N2_IFACE" >&2
      echo "       Override with HARP_AMF_N2_IFACE=<runtime-interface> if needed." >&2
      exit 9
    fi
    echo "PASS_HARP_AMF_N2_INTERFACE"
  else
    echo "WARN: ip command unavailable; N2 interface presence not checked"
  fi
fi

GNB_CFG="$FIXTURES/gnb.harp-snpn.conf"
readarray -t TOPO_VALUES < <(python3 - "$GNB_CFG" <<'PY'
from pathlib import Path
import re
import sys

text = Path(sys.argv[1]).read_text()

patterns = (
    r'amf_ip_address\s*=\s*\(\{\s*ipv4\s*=\s*"([^"]+)"',
    r'GNB_IPV4_ADDRESS_FOR_NG_AMF\s*=\s*"([^"]+)"',
    r'GNB_IPV4_ADDRESS_FOR_NGU\s*=\s*"([^"]+)"',
)
for pattern in patterns:
    m = re.search(pattern, text, re.MULTILINE)
    print(m.group(1) if m else "")
PY
)

AMF_N2_IP="${HARP_AMF_N2_IP:-${TOPO_VALUES[0]}}"
GNB_N2_IP="${HARP_GNB_N2_IP:-${TOPO_VALUES[1]}}"
GNB_NGU_IP="${HARP_GNB_NGU_IP:-${TOPO_VALUES[2]}}"

for pair in \
  "amf_n2:$AMF_N2_IP" \
  "gnb_n2:$GNB_N2_IP" \
  "gnb_ngu:$GNB_NGU_IP"; do
  key="${pair%%:*}"
  value="${pair#*:}"
  if [ -z "$value" ]; then
    echo "ERROR: missing RFsim topology value: $key" >&2
    exit 10
  fi
done

echo "AMF_N2_IP=$AMF_N2_IP"
echo "GNB_N2_IP=$GNB_N2_IP"
echo "GNB_NGU_IP=$GNB_NGU_IP"

TOPOLOGY_MODE="${HARP_TOPOLOGY_MODE:-}"
case "$TOPOLOGY_MODE" in
  host)
    command -v ip >/dev/null 2>&1 \
      || { echo "ERROR: ip command required for host topology validation" >&2; exit 11; }
    ip addr show | grep -Fq " $AMF_N2_IP/" \
      || { echo "ERROR: host is missing AMF N2 IP $AMF_N2_IP" >&2; exit 11; }
    ip addr show | grep -Fq " $GNB_N2_IP/" \
      || { echo "ERROR: host is missing gNB N2 IP $GNB_N2_IP" >&2; exit 11; }
    echo "PASS_HARP_RFSIM_TOPOLOGY_HOST"
    ;;
  container)
    if [ "${HARP_TOPOLOGY_CONFIRMED:-0}" != "1" ]; then
      echo "ERROR: container topology requires HARP_TOPOLOGY_CONFIRMED=1" >&2
      echo "       after verifying AMF=$AMF_N2_IP and gNB=$GNB_N2_IP routing." >&2
      exit 12
    fi
    echo "PASS_HARP_RFSIM_TOPOLOGY_CONTAINER_CONFIRMED"
    ;;
  *)
    echo "ERROR: set HARP_TOPOLOGY_MODE=host or container" >&2
    exit 13
    ;;
esac

echo "PASS_HARP_RFSIM_AUTH_LAB_PREFLIGHT"
