#!/usr/bin/env bash
# Guarded runner for the :keystore instrumented test (KeystoreDeviceTest). The ONLY sanctioned way to run it.
#   TESTER ONLY. It targets the Gate-1 rig yahirs-s22-ultra-2 (USB serial R5CT10XNKQN first, the wireless
#   100.118.21.106:1496 only as a fallback, and only after ro.serialno proves it is the same handset). It NEVER
#   substitutes another device, never touches the personal phone (100.126.94.47), and has no option, argument or
#   environment variable that changes the target (ADB may name a different adb BINARY only). Every adb call carries
#   -s <target>, except the single `adb connect` used to arm the wireless fallback.
#   Do not run it while another agent or a person is driving the TESTER (one device-tester at a time); a host lock
#   file serializes runs of this script itself.
#
# Usage: scripts/run-keystore-instrumented.sh        (no arguments)
# Exit codes:
#   0 PASS   1 FAIL (tests ran and failed)   2 ERROR (usage, build, APK, install or instrumentation problem)
#   3 INFRA (TESTER offline or busy)         4 INFRA (refused: identity mismatch or a non-TESTER device requested)
# The LAST line is always: KEYSTORE_INSTRUMENTED: <PASS|FAIL|INFRA|ERROR> <key=value ...>
set -uo pipefail

TESTER_USB="R5CT10XNKQN"
TESTER_WIFI="100.118.21.106:1496"
PERSONAL_IP="100.126.94.47"
EXPECTED_MODEL="SM-S908U"
MIN_SDK=35
TEST_PKG="io.github.ygaray.voiceactionengine.keystore.test"
RUNNER="androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS="io.github.ygaray.voiceactionengine.keystore.KeystoreDeviceTest"
EXPECTED_MIN_TESTS=7
LOCK_FILE="${XDG_RUNTIME_DIR:-/tmp}/vae-keystore-tester.lock"
ADB="${ADB:-adb}"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="keystore/build/device-run"
LOG_FILE="$LOG_DIR/instrument.log"
APK_DIR="keystore/build/outputs/apk/androidTest/debug"

TARGET=""
MODEL=""
SDK=""
INSTALLED=0

# Every adb invocation goes through adb_t: a timeout, and fd 9 (the lock) closed so a daemonized adb server never
# inherits and holds the lock. The caller supplies -s <target> (or `connect <wifi>`).
adb_t() { local secs="$1"; shift; timeout "$secs" "$ADB" "$@" 9>&-; }
adbt() { adb_t 30 -s "$TARGET" "$@"; }
strip_cr() { tr -d '\r'; }

# Remove the test package from the target (idempotent). Prints "test package removed" once it is confirmed gone.
cleanup() {
  [ "$INSTALLED" = 1 ] || return 0
  INSTALLED=0
  adb_t 60 -s "$TARGET" uninstall "$TEST_PKG" >/dev/null 2>&1 || true
  if adb_t 30 -s "$TARGET" shell pm list packages 2>/dev/null | strip_cr | grep -qxF "package:$TEST_PKG"; then
    echo "WARNING: test package $TEST_PKG is still installed on $TARGET"
  else
    echo "test package removed"
  fi
}
# Abnormal exits (signals): still remove the APK, silently, never after the final line.
trap 'INSTALLED=0; [ -n "$TARGET" ] && adb_t 30 -s "$TARGET" uninstall "$TEST_PKG" >/dev/null 2>&1; exit 130' INT TERM

# finish <code> <PASS|FAIL|INFRA|ERROR> <details>: clean up, then print the one final line and exit.
finish() {
  local code="$1" outcome="$2" details="$3"
  cleanup
  echo "KEYSTORE_INSTRUMENTED: $outcome $details"
  exit "$code"
}

# a. No arguments: nothing can redirect the target.
if [ $# -gt 0 ]; then
  echo "usage: scripts/run-keystore-instrumented.sh   (takes no arguments; the target is fixed to the TESTER)"
  finish 2 ERROR "reason=usage"
fi

# b. A foreign ANDROID_SERIAL is refused before any adb call; an allowed one is dropped (every call uses -s anyway).
if [ -n "${ANDROID_SERIAL:-}" ] && [ "$ANDROID_SERIAL" != "$TESTER_USB" ] && [ "$ANDROID_SERIAL" != "$TESTER_WIFI" ]; then
  echo "TESTER IDENTITY MISMATCH - refusing (ANDROID_SERIAL names a device that is not the TESTER)"
  finish 4 INFRA "reason=refused_serial"
fi
unset ANDROID_SERIAL

# c. One run of this script at a time on this host.
exec 9>"$LOCK_FILE"
if ! flock -n 9; then
  echo "TESTER BUSY - another keystore instrumented run holds $LOCK_FILE"
  finish 3 INFRA "reason=tester_busy"
fi

# d. Resolve: USB first, wireless only as a fallback, otherwise offline. Never anything else.
if [ "$(adb_t 15 -s "$TESTER_USB" get-state 2>/dev/null | strip_cr)" = "device" ]; then
  TARGET="$TESTER_USB"
else
  adb_t 15 connect "$TESTER_WIFI" >/dev/null 2>&1 || true
  if [ "$(adb_t 15 -s "$TESTER_WIFI" get-state 2>/dev/null | strip_cr)" = "device" ]; then
    TARGET="$TESTER_WIFI"
  else
    echo "TESTER OFFLINE - not substituting"
    finish 3 INFRA "reason=tester_offline"
  fi
fi

# e. Identity on the selected target: the personal phone is refused outright, and the handset must prove it is the TESTER.
case "$TARGET" in
  *"$PERSONAL_IP"*) echo "TESTER IDENTITY MISMATCH - refusing (target is the personal phone)"; TARGET=""; finish 4 INFRA "reason=identity_mismatch" ;;
esac
SERIALNO="$(adbt shell getprop ro.serialno 2>/dev/null | strip_cr)"
MODEL="$(adbt shell getprop ro.product.model 2>/dev/null | strip_cr)"
SDK="$(adbt shell getprop ro.build.version.sdk 2>/dev/null | strip_cr)"
case "$SDK" in ''|*[!0-9]*) SDK_NUM=0 ;; *) SDK_NUM="$SDK" ;; esac
if [ "$SERIALNO" != "$TESTER_USB" ] || [ "$MODEL" != "$EXPECTED_MODEL" ] || [ "$SDK_NUM" -lt "$MIN_SDK" ]; then
  echo "TESTER IDENTITY MISMATCH - refusing (serial/model/sdk do not match the TESTER)"
  TARGET=""
  finish 4 INFRA "reason=identity_mismatch"
fi

# f. Informational: who is on the screen right now.
echo "target=$TARGET model=$MODEL sdk=$SDK"
FOREGROUND="$(adbt shell dumpsys activity activities 2>/dev/null | strip_cr | grep -m1 -E 'topResumedActivity|mResumedActivity' || true)"
echo "foreground: ${FOREGROUND:-unknown}"

# g. Build the instrumentation APK (offline; the only Gradle invocation).
cd "$ROOT" || finish 2 ERROR "reason=build_failed"
if ! ./gradlew :keystore:assembleDebugAndroidTest --offline -q 9>&-; then
  finish 2 ERROR "reason=build_failed target=$TARGET"
fi
APK_COUNT="$(find "$APK_DIR" -maxdepth 1 -name '*.apk' 2>/dev/null | wc -l | tr -d ' ')"
if [ "$APK_COUNT" != 1 ]; then
  finish 2 ERROR "reason=apk_missing target=$TARGET"
fi
APK="$(find "$APK_DIR" -maxdepth 1 -name '*.apk')"

# h. Install (the cleanup removes it again), and confirm the instrumentation is registered.
INSTALLED=1
if ! adb_t 180 -s "$TARGET" install -r -t "$APK" >/dev/null 2>&1; then
  if [ "$(adb_t 15 -s "$TARGET" get-state 2>/dev/null | strip_cr)" != "device" ]; then
    finish 3 INFRA "reason=tester_offline target=$TARGET"
  fi
  finish 2 ERROR "reason=install_failed target=$TARGET"
fi
if ! adbt shell pm list instrumentation 2>/dev/null | strip_cr | grep -qF "$TEST_PKG/$RUNNER"; then
  finish 2 ERROR "reason=instrumentation_missing target=$TARGET"
fi

# i. Run only the keystore device class; keep the full log and echo it.
mkdir -p "$LOG_DIR"
adb_t 300 -s "$TARGET" shell am instrument -w -e class "$TEST_CLASS" "$TEST_PKG/$RUNNER" 2>&1 | strip_cr | tee "$LOG_FILE"

# j. Parse. PASS needs "OK (N tests)" with N at least EXPECTED_MIN_TESTS.
OK_LINE="$(grep -E '^OK \([0-9]+ tests?\)' "$LOG_FILE" | tail -1 || true)"
if [ -n "$OK_LINE" ]; then
  N="$(echo "$OK_LINE" | sed -E 's/^OK \(([0-9]+) tests?\).*/\1/')"
  if [ "$N" -ge "$EXPECTED_MIN_TESTS" ]; then
    finish 0 PASS "tests=$N target=$TARGET model=$MODEL sdk=$SDK"
  fi
  finish 1 FAIL "tests_run=$N failures=0 reason=too_few_tests target=$TARGET"
fi
RUN="$(sed -nE 's/^Tests run: ([0-9]+).*/\1/p' "$LOG_FILE" | tail -1)"
FAILS="$(sed -nE 's/^Tests run: [0-9]+, +Failures: ([0-9]+).*/\1/p' "$LOG_FILE" | tail -1)"
echo "failing tests (names and exception classes only):"
# "1) testName(fully.qualified.Class)" is followed by the exception line; print the name and the exception class only.
awk '
  /^[0-9]+\) / { sub(/^[0-9]+\) /, ""); name = $0; want = 1; next }
  want && NF { cls = $0; sub(/:.*$/, "", cls); print "  " name " -> " cls; want = 0 }
' "$LOG_FILE"
if grep -q 'Process crashed' "$LOG_FILE"; then echo "  process crashed"; fi
finish 1 FAIL "tests_run=${RUN:-unknown} failures=${FAILS:-unknown} target=$TARGET"
