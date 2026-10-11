#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

bash "$ROOT/tools/check_android_build_environment.sh"

echo "[HARP] running pure-Java protocol self-tests..."
bash "$ROOT/tools/run_pure_java_selftest.sh"

echo "[HARP] building debug APK locally..."
(
  cd "$ROOT"
  gradle --no-daemon --stacktrace :app:assembleDebug
)

APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK" ]; then
  echo "ERROR: expected APK not found: $APK" >&2
  exit 2
fi

if [ ! -s "$APK" ]; then
  echo "ERROR: APK exists but is empty: $APK" >&2
  exit 3
fi

if command -v sha256sum >/dev/null 2>&1; then
  APK_SHA="$(sha256sum "$APK" | awk '{print $1}')"
  echo "APK_SHA256=$APK_SHA"
fi

echo "APK_PATH=$APK"
echo "PASS_HARP_ANDROID_DEBUG_APK_BUILD"
