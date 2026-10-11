#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

command -v java >/dev/null 2>&1 || fail "JDK 17 is required (java not found)"
command -v javac >/dev/null 2>&1 || fail "JDK 17 is required (javac not found)"
command -v gradle >/dev/null 2>&1 || fail "Gradle 9.4.1 is required (gradle not found)"

JAVA_VERSION="$(java -version 2>&1 | sed -n '1s/.*version "\([^"]*\)".*/\1/p')"
JAVA_MAJOR="$(printf '%s' "$JAVA_VERSION" | cut -d. -f1)"
if ! [[ "$JAVA_MAJOR" =~ ^[0-9]+$ ]] || [ "$JAVA_MAJOR" -lt 17 ]; then
  fail "JDK 17 or newer required by AGP 9.2.1; detected java version $JAVA_VERSION"
fi

GRADLE_VERSION="$(gradle --version | sed -n 's/^Gradle //p' | head -n1)"
if [ "$GRADLE_VERSION" != "9.4.1" ]; then
  fail "Gradle 9.4.1 required for pinned AGP 9.2.1; detected $GRADLE_VERSION"
fi

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [ -z "$SDK_ROOT" ]; then
  fail "ANDROID_SDK_ROOT or ANDROID_HOME must point to Android SDK"
fi

[ -d "$SDK_ROOT/platforms/android-36" ]   || fail "Android SDK platform android-36 is missing"
[ -d "$SDK_ROOT/build-tools/36.0.0" ]   || fail "Android SDK Build Tools 36.0.0 are missing"

AAR="$ROOT/app/libs/hev-socks5-tunnel.aar"
EXPECTED_AAR_SHA="15ec8ed121663b562c99caa5bb602d1009f24e5b09e733438b81988f12feaaab"

if [ -f "$AAR" ]; then
  command -v sha256sum >/dev/null 2>&1 || fail "sha256sum required to verify HEV AAR"
  ACTUAL_AAR_SHA="$(sha256sum "$AAR" | awk '{print $1}')"
  if [ "$ACTUAL_AAR_SHA" != "$EXPECTED_AAR_SHA" ]; then
    fail "HEV AAR SHA-256 mismatch: $ACTUAL_AAR_SHA"
  fi
  echo "HEV_AAR=present"
  echo "HEV_AAR_SHA256=$ACTUAL_AAR_SHA"
  echo "ANDROID_BUILD_MODE=stage2a-capable"
else
  echo "HEV_AAR=absent"
  echo "ANDROID_BUILD_MODE=relay-preflight-only"
fi

echo "JAVA_VERSION=$JAVA_VERSION"
echo "GRADLE_VERSION=$GRADLE_VERSION"
echo "ANDROID_SDK_ROOT=$SDK_ROOT"
echo "PASS_HARP_ANDROID_BUILD_ENV"
