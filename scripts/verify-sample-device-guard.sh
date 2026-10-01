#!/usr/bin/env bash
# Offline proof of every refusal path of scripts/run-sample-gate1.sh, with a FAKE adb and a FAKE push-test-key (no device
# is touched, no key exists). Each scenario runs a copy of the runner inside a throwaway repo skeleton whose gradlew only
# records that it was called, with ADB pointed at a fake adb that logs its arguments and answers per scenario. Per scenario
# it checks the exit code, the message, the last output line and the logged adb calls, and for ALL of them:
#   - every logged adb call is `connect 100.118.21.106:1496` or starts with `-s R5CT10XNKQN ` / `-s 100.118.21.106:1496 `
#     (so a call without -s, or addressed to any other serial, fails the guard)
#   - a refusal scenario never logs an install / uninstall / push / run-as call, and never reaches the Gradle build
# Prints "SAMPLE DEVICE GUARD OK scenarios=<n>" or "SAMPLE DEVICE GUARD FAIL: <scenario>: <why>" (exit 1).
# Usage: scripts/verify-sample-device-guard.sh
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUNNER_SRC="$HERE/run-sample-gate1.sh"
LOCK_FILE="${XDG_RUNTIME_DIR:-/tmp}/vae-keystore-tester.lock"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
SCENARIOS=0

die() { echo "SAMPLE DEVICE GUARD FAIL: $1" >&2; exit 1; }
[ -x "$RUNNER_SRC" ] || die "setup: $RUNNER_SRC is missing or not executable"

# The runner shares its lock with the keystore runner; if a real run holds it, every scenario would read as busy.
exec 8>"$LOCK_FILE" || die "setup: cannot open $LOCK_FILE"
flock -n 8 || die "setup: the tester lock is held by a real run; re-run when it is finished"
flock -u 8

# The fake adb: logs its arguments, then answers per $SCENARIO. Anything unexpected exits 1.
write_fake_adb() {
  cat >"$1" <<'FAKE'
#!/usr/bin/env bash
echo "$*" >>"$CALLS_LOG"
[ "$SCENARIO" = offline ] && exit 1
if [ "${1:-}" = connect ]; then
  [ "$SCENARIO" = wireless_impostor ] && { echo "connected to ${2:-}"; exit 0; }
  exit 1
fi
serial=""
if [ "${1:-}" = -s ]; then serial="${2:-}"; shift 2; fi
case "${1:-}" in
  get-state)
    case "$SCENARIO:$serial" in
      wireless_impostor:100.118.21.106:1496) echo device; exit 0 ;;
      wireless_impostor:*) ;;
      *:R5CT10XNKQN) echo device; exit 0 ;;
    esac
    echo "error: device '$serial' not found" >&2; exit 1 ;;
  shell)
    shift
    case "$*" in
      "getprop ro.serialno")
        case "$SCENARIO" in usb_impostor | wireless_impostor) echo R5CTIMPOSTOR ;; *) echo R5CT10XNKQN ;; esac
        exit 0 ;;
      "getprop ro.product.model") echo SM-S908U; exit 0 ;;
      "getprop ro.build.version.sdk") echo 36; exit 0 ;;
      "dumpsys activity activities") echo "  topResumedActivity=ActivityRecord{1 u0 com.example/.Main t1}"; exit 0 ;;
      "pm list packages")
        echo "package:com.android.shell"
        [ -e "$STATE_DIR/installed" ] && echo "package:io.github.ygaray.voiceactionengine.sample"
        exit 0 ;;
    esac
    exit 1 ;;
esac
exit 1
FAKE
  chmod +x "$1"
}

# run_scenario <name> <expected-exit> <expected-message-or-"-"> <expected-final-fragment> <args...>
#   The final line must start with "SAMPLE_GATE1: " and contain the fragment. Knobs come from the caller's environment:
#   CALLS_EMPTY=1 (adb must not have been called), ANDROID_SERIAL_VALUE (a foreign ANDROID_SERIAL for the run).
run_scenario() {
  local name="$1" want_code="$2" want_msg="$3" want_final="$4"; shift 4
  SCENARIOS=$((SCENARIOS + 1))
  local dir="$WORK/$name"
  LAST_DIR="$dir"
  mkdir -p "$dir/repo/scripts" "$dir/state"
  cp "$RUNNER_SRC" "$dir/repo/scripts/run-sample-gate1.sh"
  chmod +x "$dir/repo/scripts/run-sample-gate1.sh"
  # A gradlew that proves the build was never reached (scenarios that expect the build override nothing here).
  printf '#!/usr/bin/env bash\necho reached >"%s/gradle-reached"\nexit 1\n' "$dir" >"$dir/repo/gradlew"
  chmod +x "$dir/repo/gradlew"
  write_fake_adb "$dir/adb"
  : >"$dir/calls.log"

  local out code
  # BASH_ENV is cleared: on this host it re-exports ANDROID_SERIAL (the TESTER) into every non-interactive bash, which would
  # silently overwrite the scenario's own value.
  if [ -n "${ANDROID_SERIAL_VALUE:-}" ]; then
    out="$(env -u BASH_ENV SCENARIO="$name" CALLS_LOG="$dir/calls.log" STATE_DIR="$dir/state" ADB="$dir/adb" \
      ANDROID_SERIAL="$ANDROID_SERIAL_VALUE" "$dir/repo/scripts/run-sample-gate1.sh" "$@" 8>&- 2>&1)"; code=$?
  else
    out="$(env -u BASH_ENV -u ANDROID_SERIAL SCENARIO="$name" CALLS_LOG="$dir/calls.log" STATE_DIR="$dir/state" ADB="$dir/adb" \
      "$dir/repo/scripts/run-sample-gate1.sh" "$@" 8>&- 2>&1)"; code=$?
  fi
  LAST_OUT="$out"

  [ "$code" = "$want_code" ] || die "$name: exit $code, expected $want_code"
  if [ "$want_msg" != "-" ]; then
    echo "$out" | grep -qF "$want_msg" || die "$name: message '$want_msg' not printed"
  fi
  local last; last="$(echo "$out" | tail -1)"
  case "$last" in
    "SAMPLE_GATE1: "*"$want_final"*) ;;
    *) die "$name: final line is not a SAMPLE_GATE1 line containing '$want_final' (got: $last)" ;;
  esac
  if [ "${CALLS_EMPTY:-0}" = 1 ] && [ -s "$dir/calls.log" ]; then
    die "$name: adb was called but must not have been ($(head -3 "$dir/calls.log" | tr '\n' '|'))"
  fi
  local line
  while IFS= read -r line; do
    [ -z "$line" ] && continue
    case "$line" in
      "connect 100.118.21.106:1496") ;;
      "-s R5CT10XNKQN "* | "-s 100.118.21.106:1496 "*) ;;
      *) die "$name: an adb call is not addressed to the TESTER with -s: '$line'" ;;
    esac
  done <"$dir/calls.log"
  # The remaining checks hold for refusals and read-only subcommands; scenarios that legitimately mutate set MUTATES=1.
  if [ "${MUTATES:-0}" != 1 ]; then
    if grep -qE '(^|[[:space:]])(install|uninstall|push)([[:space:]]|$)|run-as' "$dir/calls.log"; then
      die "$name: an install/uninstall/push/run-as call was logged"
    fi
    [ ! -e "$dir/gradle-reached" ] || die "$name: the Gradle build was reached"
  fi
}

# ---- Task 1: target refusals -------------------------------------------------------------------------------------------
run_scenario happy_preflight 0 "foreground:" "OK sub=preflight target=R5CT10XNKQN model=SM-S908U sdk=36 installed=no" preflight
run_scenario offline 3 "TESTER OFFLINE - not substituting" "INFRA sub=preflight reason=tester_offline" preflight
run_scenario usb_impostor 4 "TESTER IDENTITY MISMATCH - refusing" "INFRA sub=preflight reason=identity_mismatch" preflight
run_scenario wireless_impostor 4 "TESTER IDENTITY MISMATCH - refusing" "INFRA sub=preflight reason=identity_mismatch" preflight
ANDROID_SERIAL_VALUE="100.126.94.47:5555" CALLS_EMPTY=1 run_scenario foreign_android_serial 4 "TESTER IDENTITY MISMATCH - refusing" \
  "INFRA sub=preflight reason=refused_serial" preflight
CALLS_EMPTY=1 run_scenario extra_argument 2 "-" "ERROR sub=preflight reason=usage" preflight --serial 100.126.94.47:5555
CALLS_EMPTY=1 run_scenario unknown_subcommand 2 "-" "ERROR sub=none reason=usage" frobnicate
CALLS_EMPTY=1 run_scenario no_subcommand 2 "-" "ERROR sub=none reason=usage"

# lock_busy: this guard holds the lock file on spare fd 8 (closed for the runner) while the runner runs.
flock -n 8 || die "lock_busy: cannot take the lock"
CALLS_EMPTY=1 run_scenario lock_busy 3 "TESTER BUSY" "INFRA sub=preflight reason=tester_busy" preflight
flock -u 8

echo "SAMPLE DEVICE GUARD OK scenarios=$SCENARIOS"
