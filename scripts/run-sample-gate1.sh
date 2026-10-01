#!/usr/bin/env bash
# Guarded host runner for the Phase 10 Gate-1 sample app. The ONLY sanctioned host path to the TESTER for this phase.
#   TESTER ONLY. It targets the Gate-1 rig yahirs-s22-ultra-2 (USB serial R5CT10XNKQN first, the wireless
#   100.118.21.106:1496 only as a fallback, and only after ro.serialno proves it is the same handset). It NEVER
#   substitutes another device, never touches the personal phone (100.126.94.47), and has no option, argument or
#   environment variable that changes the target (ADB and PUSH_TEST_KEY may name a binary only). Every adb call
#   carries -s <target>, except the single `adb connect` used to arm the wireless fallback.
#   One device tester at a time: every device subcommand takes a non-blocking flock on the SAME lock file as
#   scripts/run-keystore-instrumented.sh, so the two runners (and anyone else using this lock) exclude each other.
#   Keys only ever travel through push-test-key (by file reference, never on an argv); this script never reads one.
#
# Usage: scripts/run-sample-gate1.sh <subcommand> [arg]
#   preflight               guard, then print target, model, sdk, the foreground activity and whether the sample is installed
#   build-install           ./gradlew :sample:assembleDebug --offline, print apk/head/dirty facts, install, whitelist, confirm
#   push-fixture            digest-check the hand-copied host fixture against FixtureLoader.kt, then write it to app storage
#   push-keys               (only when the live-leg decision file says "decision: approved") push-test-key per provider
#   capture-start           clear logcat before a leg
#   capture-save <leg>      filter logcat through the evidence allow-list and append it to the leg's evidence file
#   cold-stamp <check|write> host-side cold-run stamp for the Anthropic agentic start (no device access)
#   verify-keys-gone        prove the app's plaintext key directory is empty after the import
#   cleanup                 force-stop, remove app-private test data, uninstall, and prove the package is gone
# Exit codes:
#   0 OK   1 FAIL   2 ERROR (usage, build, install, not approved)
#   3 INFRA (TESTER offline or busy, warm window)   4 INFRA (refused: identity mismatch or a non-TESTER device requested)
# The LAST line is always: SAMPLE_GATE1: <OK|FAIL|INFRA|ERROR> sub=<subcommand> <key=value ...>
set -uo pipefail

TESTER_USB="R5CT10XNKQN"
TESTER_WIFI="100.118.21.106:1496"
PERSONAL_IP="100.126.94.47"
EXPECTED_MODEL="SM-S908U"
MIN_SDK=35
PKG="io.github.ygaray.voiceactionengine.sample"
LOCK_FILE="${XDG_RUNTIME_DIR:-/tmp}/vae-keystore-tester.lock"
ADB="${ADB:-adb}"

SUBCOMMANDS="preflight build-install push-fixture push-keys capture-start capture-save cold-stamp verify-keys-gone cleanup"
# Exactly the LegId.wire set of sample/.../evidence/EvidenceLine.kt (the verifier proves the parity).
LEGS="ver02 smoke_anthropic smoke_openai smoke_openrouter multi_openai multi_openrouter responses_probe demo_clarify demo_partial"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

SUB="none"
ARG=""
TARGET=""
MODEL=""
SDK=""

# Every adb invocation goes through adb_t: a timeout, and fd 9 (the lock) closed so a daemonized adb server never
# inherits and holds the lock. The caller supplies -s <target> (or `connect <wifi>`).
adb_t() { local secs="$1"; shift; timeout "$secs" "$ADB" "$@" 9>&-; }
adbt() { adb_t 30 -s "$TARGET" "$@"; }
strip_cr() { tr -d '\r'; }

# finish <code> <OK|FAIL|INFRA|ERROR> <details>: print the one final line and exit.
finish() {
  local code="$1" outcome="$2" details="$3"
  echo "SAMPLE_GATE1: $outcome sub=$SUB $details"
  exit "$code"
}

in_list() { local needle="$1" item; shift; for item in $1; do [ "$item" = "$needle" ] && return 0; done; return 1; }

usage() {
  echo "usage: scripts/run-sample-gate1.sh <subcommand> [arg]"
  echo "  subcommands: $SUBCOMMANDS"
  echo "  capture-save <leg>   (leg: $LEGS)"
  echo "  cold-stamp <check|write>"
  echo "  (the target is fixed to the TESTER; there is no option to change it)"
  finish 2 ERROR "reason=usage"
}

# a. Arguments are fixed before any adb call: one subcommand from the list, and only the argument that subcommand needs.
[ $# -ge 1 ] || usage
in_list "$1" "$SUBCOMMANDS" || usage
SUB="$1"
case "$SUB" in
  capture-save)
    [ $# -eq 2 ] || usage
    in_list "$2" "$LEGS" || usage
    ARG="$2"
    ;;
  cold-stamp)
    [ $# -eq 2 ] || usage
    case "$2" in check | write) ARG="$2" ;; *) usage ;; esac
    ;;
  *)
    [ $# -eq 1 ] || usage
    ;;
esac

# b. A foreign ANDROID_SERIAL is refused before any adb call; an allowed one is dropped (every call uses -s anyway).
refuse_foreign_serial() {
  if [ -n "${ANDROID_SERIAL:-}" ] && [ "$ANDROID_SERIAL" != "$TESTER_USB" ] && [ "$ANDROID_SERIAL" != "$TESTER_WIFI" ]; then
    echo "TESTER IDENTITY MISMATCH - refusing (ANDROID_SERIAL names a device that is not the TESTER)"
    finish 4 INFRA "reason=refused_serial"
  fi
  unset ANDROID_SERIAL
}

# c-e. Take the shared lock, resolve USB first (wireless only as a fallback), and prove the identity of the handset.
acquire_and_resolve() {
  # c. One device tester at a time on this host.
  exec 9>"$LOCK_FILE"
  if ! flock -n 9; then
    echo "TESTER BUSY - another device run holds $LOCK_FILE"
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
  local serialno sdk_num
  serialno="$(adbt shell getprop ro.serialno 2>/dev/null | strip_cr)"
  MODEL="$(adbt shell getprop ro.product.model 2>/dev/null | strip_cr)"
  SDK="$(adbt shell getprop ro.build.version.sdk 2>/dev/null | strip_cr)"
  case "$SDK" in '' | *[!0-9]*) sdk_num=0 ;; *) sdk_num="$SDK" ;; esac
  if [ "$serialno" != "$TESTER_USB" ] || [ "$MODEL" != "$EXPECTED_MODEL" ] || [ "$sdk_num" -lt "$MIN_SDK" ]; then
    echo "TESTER IDENTITY MISMATCH - refusing (serial/model/sdk do not match the TESTER)"
    TARGET=""
    finish 4 INFRA "reason=identity_mismatch"
  fi
}

# f. Informational: who is on the screen right now (another tester may be driving the device).
print_foreground() {
  local fg
  fg="$(adbt shell dumpsys activity activities 2>/dev/null | strip_cr | grep -m1 -E 'topResumedActivity|mResumedActivity' || true)"
  echo "foreground: ${fg:-unknown}"
}

package_installed() {
  adbt shell pm list packages 2>/dev/null | strip_cr | grep -qxF "package:$PKG"
}

not_implemented() { finish 2 ERROR "reason=not_implemented"; }

do_preflight() {
  echo "target=$TARGET model=$MODEL sdk=$SDK"
  print_foreground
  local installed=no
  package_installed && installed=yes
  finish 0 OK "target=$TARGET model=$MODEL sdk=$SDK installed=$installed"
}

# Dispatch. Device subcommands refuse a foreign serial, take the lock, resolve and verify the TESTER, then run.
refuse_foreign_serial
acquire_and_resolve
case "$SUB" in
  preflight) do_preflight ;;
  *) print_foreground; not_implemented ;;
esac
