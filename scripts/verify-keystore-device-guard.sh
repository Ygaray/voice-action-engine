#!/usr/bin/env bash
# Offline proof of every refusal path of scripts/run-keystore-instrumented.sh, with a FAKE adb (no device is touched).
#   Each scenario runs a copy of the runner inside a throwaway repo skeleton whose gradlew only records that it was called,
#   with ADB pointed at a fake adb that logs its arguments and answers per scenario. Per scenario it checks the exit code,
#   the message, the last output line and the logged adb calls:
#     offline, usb_impostor, wireless_impostor, foreign_android_serial, extra_argument, lock_busy
#   and for ALL of them:
#     - no install / instrument / uninstall call, and the Gradle build was never reached
#     - every logged adb call is `connect 100.118.21.106:1496` or starts with `-s R5CT10XNKQN ` / `-s 100.118.21.106:1496 `
#       (so a call without -s, or addressed to any other serial, fails the guard)
# Prints "DEVICE GUARD OK" or "DEVICE GUARD FAIL: <scenario>: <why>" (exit 1). Usage: scripts/verify-keystore-device-guard.sh
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUNNER_SRC="$HERE/run-keystore-instrumented.sh"
LOCK_FILE="${XDG_RUNTIME_DIR:-/tmp}/vae-keystore-tester.lock"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

die() { echo "DEVICE GUARD FAIL: $1" >&2; exit 1; }
[ -x "$RUNNER_SRC" ] || die "setup: $RUNNER_SRC is missing or not executable"

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
      usb_impostor:R5CT10XNKQN) echo device; exit 0 ;;
      wireless_impostor:100.118.21.106:1496) echo device; exit 0 ;;
    esac
    echo "error: device '$serial' not found" >&2; exit 1 ;;
  shell)
    if [ "${2:-}" = getprop ] && [ "${3:-}" = ro.serialno ]; then echo R5CTIMPOSTOR; exit 0; fi
    exit 1 ;;
esac
exit 1
FAKE
  chmod +x "$1"
}

# run_scenario <name> <expected-exit> <expected-message-or-"-"> <expected-reason> <expect-calls-empty 0|1> <args...>
#   env for the run comes from the caller (ANDROID_SERIAL via the SCEN_ANDROID_SERIAL variable).
run_scenario() {
  local name="$1" want_code="$2" want_msg="$3" want_reason="$4" want_empty="$5"; shift 5
  local dir="$WORK/$name"
  mkdir -p "$dir/repo/scripts"
  cp "$RUNNER_SRC" "$dir/repo/scripts/run-keystore-instrumented.sh"
  chmod +x "$dir/repo/scripts/run-keystore-instrumented.sh"
  # A gradlew that proves the build was never reached.
  printf '#!/usr/bin/env bash\necho reached >"%s/gradle-reached"\nexit 1\n' "$dir" >"$dir/repo/gradlew"
  chmod +x "$dir/repo/gradlew"
  write_fake_adb "$dir/adb"
  : >"$dir/calls.log"

  local out code
  # BASH_ENV is cleared: on this host it re-exports ANDROID_SERIAL (the TESTER) into every non-interactive bash, which would
  # silently overwrite the scenario's own value.
  if [ -n "${SCEN_ANDROID_SERIAL:-}" ]; then
    out="$(env -u BASH_ENV SCENARIO="$name" CALLS_LOG="$dir/calls.log" ADB="$dir/adb" ANDROID_SERIAL="$SCEN_ANDROID_SERIAL" \
      "$dir/repo/scripts/run-keystore-instrumented.sh" "$@" 8>&- 2>&1)"; code=$?
  else
    out="$(env -u BASH_ENV -u ANDROID_SERIAL SCENARIO="$name" CALLS_LOG="$dir/calls.log" ADB="$dir/adb" \
      "$dir/repo/scripts/run-keystore-instrumented.sh" "$@" 8>&- 2>&1)"; code=$?
  fi

  [ "$code" = "$want_code" ] || die "$name: exit $code, expected $want_code"
  if [ "$want_msg" != "-" ]; then
    echo "$out" | grep -qF "$want_msg" || die "$name: message '$want_msg' not printed"
  fi
  local last; last="$(echo "$out" | tail -1)"
  case "$last" in
    "KEYSTORE_INSTRUMENTED: "*"reason=$want_reason"*) ;;
    *) die "$name: final line is not a KEYSTORE_INSTRUMENTED line with reason=$want_reason (got: $last)" ;;
  esac
  case "$last" in "KEYSTORE_INSTRUMENTED: INFRA "*|"KEYSTORE_INSTRUMENTED: ERROR "*) ;; *) die "$name: final line outcome is not INFRA or ERROR (got: $last)" ;; esac
  [ ! -e "$dir/gradle-reached" ] || die "$name: the Gradle build was reached"
  if [ "$want_empty" = 1 ] && [ -s "$dir/calls.log" ]; then
    die "$name: adb was called but must not have been ($(head -3 "$dir/calls.log" | tr '\n' '|'))"
  fi
  if grep -qE '(^|[[:space:]])(install|instrument|uninstall)([[:space:]]|$)|am instrument' "$dir/calls.log"; then
    die "$name: an install/instrument/uninstall call was logged"
  fi
  local line
  while IFS= read -r line; do
    [ -z "$line" ] && continue
    case "$line" in
      "connect 100.118.21.106:1496"|"devices") ;;
      "-s R5CT10XNKQN "*|"-s 100.118.21.106:1496 "*) ;;
      *) die "$name: an adb call is not addressed to the TESTER with -s: '$line'" ;;
    esac
  done <"$dir/calls.log"
}

SCEN_ANDROID_SERIAL=""
run_scenario offline 3 "TESTER OFFLINE - not substituting" tester_offline 0
run_scenario usb_impostor 4 "TESTER IDENTITY MISMATCH - refusing" identity_mismatch 0
run_scenario wireless_impostor 4 "TESTER IDENTITY MISMATCH - refusing" identity_mismatch 0
SCEN_ANDROID_SERIAL="100.126.94.47:5555" run_scenario foreign_android_serial 4 "TESTER IDENTITY MISMATCH - refusing" refused_serial 1
run_scenario extra_argument 2 "-" usage 1 --serial 100.126.94.47:5555

# lock_busy: this guard holds the lock file on spare fd 8 (closed for the runner) while the runner runs.
exec 8>"$LOCK_FILE" || die "lock_busy: cannot open $LOCK_FILE"
flock -n 8 || die "lock_busy: the lock is already held by a real run; re-run when it is finished"
run_scenario lock_busy 3 "TESTER BUSY" tester_busy 1
exec 8>&-

echo "DEVICE GUARD OK"
