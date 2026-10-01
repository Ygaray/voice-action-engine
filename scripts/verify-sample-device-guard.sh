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
PKG="io.github.ygaray.voiceactionengine.sample"
# Key shapes are assembled from fragments so that no literal key-shaped text is committed (the git secret hook scans staged files).
KEY_SHAPE="(^|[^A-Za-z0-9])s""k-[A-Za-z0-9_-]{20,}|bearer |x-api-key|authorization"

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
      "am force-stop "* | "cmd deviceidle whitelist "*) exit 0 ;;
      "rm -f /data/local/tmp/"*) rm -f "$STATE_DIR/dev/$(basename "${3:-}")"; exit 0 ;;
      "run-as "*"cat > files/fixture/"*)
        staging="${*##*< }"; src="$STATE_DIR/dev/$(basename "$staging")"
        [ -f "$src" ] || exit 1
        cp "$src" "$STATE_DIR/appfixture"; exit 0 ;;
      "run-as "*"sha256sum files/fixture/"*)
        [ -f "$STATE_DIR/appfixture" ] || exit 1
        echo "$(sha256sum "$STATE_DIR/appfixture" | cut -d' ' -f1)  files/fixture/sb-a10-fixture.json"; exit 0 ;;
      "run-as "*" ls files/test-keys")
        if [ "${KEYS_PRESENT:-0}" -gt 0 ]; then echo zz-secret-name-marker.dat; exit 0; fi
        echo "ls: files/test-keys: No such file or directory"; exit 1 ;;
      "run-as "*" rm -rf "*) rm -f "$STATE_DIR/appfixture"; exit 0 ;;
    esac
    exit 1 ;;
  install) touch "$STATE_DIR/installed"; echo Success; exit 0 ;;
  uninstall) rm -f "$STATE_DIR/installed"; echo Success; exit 0 ;;
  push)
    mkdir -p "$STATE_DIR/dev"
    cp "${2:-}" "$STATE_DIR/dev/$(basename "${3:-}")" || exit 1
    exit 0 ;;
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
  # A gradlew that proves the build was reached (or not). With FAKE_GRADLE_APK=1 it also leaves one fake APK, like a build.
  printf '#!/usr/bin/env bash\necho reached >"%s/gradle-reached"\nif [ "${FAKE_GRADLE_APK:-0}" = 1 ]; then\n  mkdir -p sample/build/outputs/apk/debug\n  echo fake-apk >sample/build/outputs/apk/debug/sample-debug.apk\n  exit 0\nfi\nexit 1\n' "$dir" >"$dir/repo/gradlew"
  chmod +x "$dir/repo/gradlew"
  write_fake_adb "$dir/adb"
  : >"$dir/calls.log"
  # A fake push-test-key: it only records its argv. No key exists anywhere in this verifier.
  printf '#!/usr/bin/env bash\necho "$*" >>"%s/push.log"\nexit "${FAKE_PUSH_RC:-0}"\n' "$dir" >"$dir/push-test-key"
  chmod +x "$dir/push-test-key"
  : >"$dir/push.log"
  [ "${PRE_INSTALLED:-0}" = 1 ] && touch "$dir/state/installed"
  mkdir -p "$dir/state/dev"

  # Skeleton inputs the runner reads on the host: the decision file, the fixture and its digest constant, the cold stamp.
  case "${DECISION:-}" in
    approved | deferred)
      mkdir -p "$dir/repo/.planning/phases/10-sample-harness-gate-1-docs"
      printf 'decision: %s\nrelayed_by: test\n' "$DECISION" >"$dir/repo/.planning/phases/10-sample-harness-gate-1-docs/10-LIVE-LEG-DECISION.md"
      ;;
  esac
  case "${FIXTURE:-}" in
    good | bad | missing)
      local loader_dir="$dir/repo/sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/fixture" sha
      mkdir -p "$loader_dir"
      if [ "$FIXTURE" = good ] || [ "$FIXTURE" = missing ]; then sha="$(printf 'synthetic fixture bytes' | sha256sum | cut -d' ' -f1)"
      else sha="$(printf 'some other bytes' | sha256sum | cut -d' ' -f1)"; fi
      printf 'package x\n\ninternal const val FIXTURE_SHA256 = "%s"\n' "$sha" >"$loader_dir/FixtureLoader.kt"
      if [ "$FIXTURE" != missing ]; then
        mkdir -p "$dir/repo/sample/src/debug/assets"
        printf 'synthetic fixture bytes' >"$dir/repo/sample/src/debug/assets/sb-a10-fixture.json"
      fi
      ;;
  esac
  if [ -n "${STAMP_AGE:-}" ]; then
    mkdir -p "$dir/cache/vae-gate1"
    echo $(($(date +%s) - STAMP_AGE)) >"$dir/cache/vae-gate1/anthropic-agentic.ts"
  fi

  local out code
  # BASH_ENV is cleared: on this host it re-exports ANDROID_SERIAL (the TESTER) into every non-interactive bash, which would
  # silently overwrite the scenario's own value.
  if [ -n "${ANDROID_SERIAL_VALUE:-}" ]; then
    out="$(env -u BASH_ENV SCENARIO="$name" CALLS_LOG="$dir/calls.log" STATE_DIR="$dir/state" ADB="$dir/adb" \
      XDG_CACHE_HOME="$dir/cache" PUSH_TEST_KEY="$dir/push-test-key" \
      ANDROID_SERIAL="$ANDROID_SERIAL_VALUE" "$dir/repo/scripts/run-sample-gate1.sh" "$@" 8>&- 2>&1)"; code=$?
  else
    out="$(env -u BASH_ENV -u ANDROID_SERIAL SCENARIO="$name" CALLS_LOG="$dir/calls.log" STATE_DIR="$dir/state" ADB="$dir/adb" \
      XDG_CACHE_HOME="$dir/cache" PUSH_TEST_KEY="$dir/push-test-key" \
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
  # No scenario may put a key-shaped token or a credential header word on an adb call, a key-helper argv or the output.
  if grep -qiE "$KEY_SHAPE" "$dir/calls.log" "$dir/push.log"; then die "$name: a key-shaped token reached an argv"; fi
  if printf '%s\n' "$out" | grep -qiE "$KEY_SHAPE"; then die "$name: a key-shaped token reached the output"; fi
}

# assert_calls <name> <fixed-string>: the scenario's adb log contains the fragment; assert_no_calls is the opposite.
assert_calls() { grep -qF -- "$2" "$LAST_DIR/calls.log" || die "$1: expected an adb call containing '$2'"; }
assert_no_calls() { ! grep -qF -- "$2" "$LAST_DIR/calls.log" || die "$1: unexpected adb call containing '$2'"; }

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

# ---- Task 2: install, fixture, keys, cold stamp, plaintext check, cleanup ---------------------------------------------------
# build_install_happy: the stub gradlew builds one fake APK; install, deviceidle and pm all carry -s (the general check).
FAKE_GRADLE_APK=1 MUTATES=1 run_scenario build_install_happy 0 "apk_md5=" "OK sub=build-install target=R5CT10XNKQN apk_md5=" build-install
assert_calls build_install_happy "-s R5CT10XNKQN install -r "
assert_calls build_install_happy "-s R5CT10XNKQN shell cmd deviceidle whitelist +$PKG"
assert_calls build_install_happy "-s R5CT10XNKQN shell pm list packages"
[ -e "$LAST_DIR/gradle-reached" ] || die "build_install_happy: the Gradle build was not run"

CALLS_EMPTY=1 run_scenario push_fixture_missing 2 "copy the LE-1 fixture by hand per GATE1-RUNBOOK step A2" \
  "ERROR sub=push-fixture reason=fixture_missing_on_host" push-fixture
FIXTURE=bad CALLS_EMPTY=1 run_scenario push_fixture_mismatch 1 "do not use; ask the orchestrator to regenerate" \
  "FAIL sub=push-fixture reason=fixture_sha_mismatch" push-fixture
assert_no_calls push_fixture_mismatch "push "

FIXTURE=good MUTATES=1 run_scenario push_fixture_happy 0 "foreground:" "OK sub=push-fixture fixture_sha=" push-fixture
assert_calls push_fixture_happy "-s R5CT10XNKQN push sample/src/debug/assets/sb-a10-fixture.json /data/local/tmp/vae-fx-"
assert_calls push_fixture_happy "-s R5CT10XNKQN shell rm -f /data/local/tmp/vae-fx-"
[ -z "$(ls -A "$LAST_DIR/state/dev")" ] || die "push_fixture_happy: the staging file was left on the device"
[ -f "$LAST_DIR/state/appfixture" ] || die "push_fixture_happy: the fixture never reached app storage"

DECISION=deferred CALLS_EMPTY=1 run_scenario push_keys_not_approved 2 "not pushing keys" \
  "ERROR sub=push-keys reason=live_legs_not_approved" push-keys
[ ! -s "$LAST_DIR/push.log" ] || die "push_keys_not_approved: push-test-key was called"

DECISION=approved run_scenario push_keys_happy 0 "foreground:" "OK sub=push-keys providers=anthropic,openai,openrouter" push-keys
[ "$(wc -l <"$LAST_DIR/push.log" | tr -d ' ')" = 3 ] || die "push_keys_happy: push-test-key was not called exactly three times"
for p in anthropic openai openrouter; do
  grep -qxF "$p --device R5CT10XNKQN --package $PKG" "$LAST_DIR/push.log" || die "push_keys_happy: no exact call for $p"
done

STAMP_AGE=10 CALLS_EMPTY=1 run_scenario cold_stamp_warm 3 "WARM WINDOW" "INFRA sub=cold-stamp reason=warm_window remaining=3" cold-stamp check
STAMP_AGE=400 CALLS_EMPTY=1 run_scenario cold_stamp_cold 0 "-" "OK sub=cold-stamp cold=yes" cold-stamp check

KEYS_PRESENT=1 MUTATES=1 run_scenario verify_keys_gone_present 1 "plaintext key files are still present" \
  "FAIL sub=verify-keys-gone reason=plaintext_keys_present count=1" verify-keys-gone
! printf '%s\n' "$LAST_OUT" | grep -qF "zz-secret-name-marker" || die "verify_keys_gone_present: a file name was printed"
assert_no_calls verify_keys_gone_present " install "
assert_no_calls verify_keys_gone_present " uninstall "
assert_no_calls verify_keys_gone_present " push "
assert_no_calls verify_keys_gone_present "rm "

PRE_INSTALLED=1 MUTATES=1 run_scenario cleanup_happy 0 "sample package removed" "OK sub=cleanup" cleanup
assert_calls cleanup_happy "-s R5CT10XNKQN uninstall $PKG"
[ ! -e "$LAST_DIR/state/installed" ] || die "cleanup_happy: the package is still installed in the fake"

echo "SAMPLE DEVICE GUARD OK scenarios=$SCENARIOS"
