#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/pure-java-selftest"
rm -rf "$OUT"
mkdir -p "$OUT"

javac -Xlint:all -Werror -d "$OUT" \
  "$ROOT/app/src/main/java/org/harp/l2/Stage0Protocol.java" \
  "$ROOT/app/src/main/java/org/harp/l2/SocksDestinationPolicy.java" \
  "$ROOT/app/src/main/java/org/harp/l2/SocksAddressResolver.java" \
  "$ROOT/app/src/main/java/org/harp/l2/SocksPolicies.java" \
  "$ROOT/app/src/main/java/org/harp/l2/MiniSocks5.java" \
  "$ROOT/app/src/main/java/org/harp/l2/Stage2SessionCredentials.java" \
  "$ROOT/app/src/main/java/org/harp/l2/Stage2ControlProtocol.java" \
  "$ROOT/app/src/main/java/org/harp/l2/Stage2TunnelConfig.java" \
  "$ROOT/tools/Stage01PureJavaSelfTest.java" \
  "$ROOT/tools/Stage2PureJavaSelfTest.java"

java -cp "$OUT" org.harp.l2.Stage01PureJavaSelfTest
java -cp "$OUT" org.harp.l2.Stage2PureJavaSelfTest
