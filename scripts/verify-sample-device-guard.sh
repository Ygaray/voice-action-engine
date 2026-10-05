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
# The phase directory the runner is pointed at (VAE_GATE1_PHASE_DIR); the one place to retarget when the phase changes.
PHASE_REL=".planning/phases/12-wave-1-seams-w04-fix"
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
      "run-as "*"if [ -d files/test-keys ]; then ls files/test-keys; fi; echo __rc=\$?"*)
        # KEYS_ADB_TIMEOUT: adb printed nothing and was killed (rc 124); KEYS_LS_FAILS: the listing itself failed.
        [ "${KEYS_ADB_TIMEOUT:-0}" = 1 ] && exit 124
        if [ "${KEYS_LS_FAILS:-0}" = 1 ]; then echo "ls: files/test-keys: Permission denied"; echo "__rc=1"; exit 0; fi
        if [ "${KEYS_PRESENT:-0}" -gt 0 ]; then echo zz-secret-name-marker.dat; fi
        echo "__rc=0"; exit 0 ;;
      "run-as "*" rm -rf "*) rm -f "$STATE_DIR/appfixture"; exit 0 ;;
    esac
    exit 1 ;;
  logcat)
    [ "${2:-}" = -c ] && exit 0
    cat "${FAKE_LOGCAT_FILE:-/dev/null}"; exit 0 ;;
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
  [ "${UNCOUNTED:-0}" = 1 ] || SCENARIOS=$((SCENARIOS + 1))
  local dir="$WORK/$name"
  LAST_DIR="$dir"
  mkdir -p "$dir/repo/scripts" "$dir/state"
  cp "$RUNNER_SRC" "$dir/repo/scripts/run-sample-gate1.sh"
  chmod +x "$dir/repo/scripts/run-sample-gate1.sh"
  cp "$HERE/sample-evidence-filter.sh" "$dir/repo/scripts/sample-evidence-filter.sh"
  chmod +x "$dir/repo/scripts/sample-evidence-filter.sh"
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
      mkdir -p "$dir/repo/$PHASE_REL"
      printf 'decision: %s\nrelayed_by: test\n' "$DECISION" >"$dir/repo/$PHASE_REL/12-LIVE-LEG-DECISION.md"
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
      XDG_CACHE_HOME="$dir/cache" PUSH_TEST_KEY="$dir/push-test-key" VAE_GATE1_PHASE_DIR="$PHASE_REL" \
      ANDROID_SERIAL="$ANDROID_SERIAL_VALUE" "$dir/repo/scripts/run-sample-gate1.sh" "$@" 8>&- 2>&1)"; code=$?
  else
    out="$(env -u BASH_ENV -u ANDROID_SERIAL SCENARIO="$name" CALLS_LOG="$dir/calls.log" STATE_DIR="$dir/state" ADB="$dir/adb" \
      XDG_CACHE_HOME="$dir/cache" PUSH_TEST_KEY="$dir/push-test-key" VAE_GATE1_PHASE_DIR="$PHASE_REL" \
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
# LE-7: the runner line is pasted as evidence, so it carries the 8-hex digest prefix only (no ellipsis, no suffix).
printf '%s\n' "$LAST_OUT" | tail -1 | grep -qE 'OK sub=push-fixture fixture_sha=[0-9a-f]{8} target=' \
  || die "push_fixture_happy: the fixture digest is not printed as an 8-hex prefix only"
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

MUTATES=1 run_scenario verify_keys_gone_absent 0 "test-keys dir empty" "OK sub=verify-keys-gone keys_gone=yes" verify-keys-gone
# A timeout (adb prints nothing and exits 124) must NOT read as "keys gone": it is an unproven check, an ERROR.
KEYS_ADB_TIMEOUT=1 MUTATES=1 run_scenario keys_gone_adb_timeout 2 "could not prove" "ERROR sub=verify-keys-gone reason=check_unproven" verify-keys-gone
# A failed listing (permission denied, rc 1 sentinel) is unproven too.
KEYS_LS_FAILS=1 MUTATES=1 run_scenario keys_gone_ls_failed 2 "could not prove" "ERROR sub=verify-keys-gone reason=check_unproven" verify-keys-gone
! printf '%s\n' "$LAST_OUT" | grep -qF "Permission denied" || die "keys_gone_ls_failed: the raw adb text was printed"

PRE_INSTALLED=1 MUTATES=1 run_scenario cleanup_happy 0 "sample package removed" "OK sub=cleanup" cleanup
assert_calls cleanup_happy "-s R5CT10XNKQN uninstall $PKG"
[ ! -e "$LAST_DIR/state/installed" ] || die "cleanup_happy: the package is still installed in the fake"

# ---- Task 3: evidence capture, allow-list filter, key-shape scan, grammar parity ----------------------------------------------
FILTER_SRC="$HERE/sample-evidence-filter.sh"
GOLDEN="$HERE/../sample/src/test/resources/evidence-lines.golden.txt"
EVIDENCE_KT="$HERE/../sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/evidence/EvidenceLine.kt"
[ -x "$FILTER_SRC" ] || die "setup: $FILTER_SRC is missing or not executable"
[ -f "$GOLDEN" ] || die "setup: $GOLDEN is missing"
EVID_REL="$PHASE_REL/evidence"

# Planted key-shaped tokens, assembled at run time from fragments (never written as a literal).
PLANT_A="s""k-ant-api03-abcdefghijklmnopqrstuvwxyz0123"
PLANT_B="s""k-or-v1-abcdefghijklmnopqrstuvwxyz0123456789"
PLANT_C="s""k-abcdefghijklmnopqrstuvwxyz0123456789"

# filter_keeps_golden: grammar parity with EvidenceLine - every golden line is kept unchanged and nothing is dropped.
SCENARIOS=$((SCENARIOS + 1))
"$FILTER_SRC" <"$GOLDEN" >"$WORK/golden.out" 2>"$WORK/golden.err" || die "filter_keeps_golden: the filter exited non-zero"
cmp -s "$WORK/golden.out" "$GOLDEN" || die "filter_keeps_golden: the filter changed or dropped a golden line"
grep -qx "FILTER OK kept=$(grep -c '' "$GOLDEN") dropped=0" "$WORK/golden.err" || die "filter_keeps_golden: wrong FILTER OK line ($(cat "$WORK/golden.err"))"

# filter_drops_free_text: a sentence and a VAE line with a space inside a value are dropped; the one valid line survives.
SCENARIOS=$((SCENARIOS + 1))
printf '%s\n' "the user said please delete my notes" "VAE_TURN leg=ver02 model=two words" "VAE_AUTORUN leg=ver02" "VAE_TURN leg=ver02 tail=" >"$WORK/free.in"
"$FILTER_SRC" <"$WORK/free.in" >"$WORK/free.out" 2>"$WORK/free.err" || die "filter_drops_free_text: the filter exited non-zero"
grep -qx "VAE_AUTORUN leg=ver02" "$WORK/free.out" || die "filter_drops_free_text: the valid line was lost"
! grep -qE 'user said|two words' "$WORK/free.out" || die "filter_drops_free_text: free text was kept"
grep -qx "FILTER OK kept=2 dropped=2" "$WORK/free.err" || die "filter_drops_free_text: wrong counts ($(cat "$WORK/free.err"))"

# filter_rejects_key_shape: a grammar-valid line carrying a key-shaped value is a leak: exit 1, nothing on stdout.
SCENARIOS=$((SCENARIOS + 1))
for plant in "$PLANT_A" "$PLANT_B" "$PLANT_C"; do
  printf '%s\n' "VAE_AUTORUN leg=ver02" "VAE_TURN leg=ver02 iteration=1 model=$plant" >"$WORK/leak.in"
  "$FILTER_SRC" <"$WORK/leak.in" >"$WORK/leak.out" 2>"$WORK/leak.err"; rc=$?
  [ "$rc" = 1 ] || die "filter_rejects_key_shape: exit $rc, expected 1"
  [ ! -s "$WORK/leak.out" ] || die "filter_rejects_key_shape: something reached stdout"
  grep -qx "LEAK SCAN FAIL" "$WORK/leak.err" || die "filter_rejects_key_shape: no LEAK SCAN FAIL"
done

# filter_rejects_fixture_content (LE-7 negative control): a ver02 line that names a tool, or a digest longer than the 8-hex
# prefix, is a leak: exit 1, nothing on stdout. The same shapes on a non-fixture leg are legitimate and are kept.
SCENARIOS=$((SCENARIOS + 1))
for planted in \
  "VAE_TURN leg=ver02 iteration=1 model=m stop_reason=tool_use tools=[zz_marker_tool] tool_count=1" \
  "VAE_TURN leg=ver02 iteration=1 model=m stop_reason=tool_use tools=zz_marker_tool tool_count=1" \
  "VAE_OUTCOME leg=ver02 kind=completed partial=false reason=none executed=1 committed=1 held=0 reply_len=4 terminal_tool=zz_marker_tool" \
  "VAE_SMOKE leg=ver02 tool=zz_marker_tool arg_keys=[id] optional_absent=true prompt_variant=0" \
  "VAE_ENV okhttp=5.2.1 fixture_sha=0123abcdef012 fixture_tools=4 min_cacheable=4096 prefix_chars=1 est_prefix_tokens=1" \
  "VAE_FIXTURE kind=loaded source=files sha=0123abcdef012 tools=4 bytes=1"; do
  printf '%s\n' "VAE_AUTORUN leg=ver02" "$planted" >"$WORK/fx.in"
  "$FILTER_SRC" <"$WORK/fx.in" >"$WORK/fx.out" 2>"$WORK/fx.err"; rc=$?
  [ "$rc" = 1 ] || die "filter_rejects_fixture_content: exit $rc, expected 1 for: $planted"
  [ ! -s "$WORK/fx.out" ] || die "filter_rejects_fixture_content: something reached stdout for: $planted"
  grep -qx "LEAK SCAN FAIL" "$WORK/fx.err" || die "filter_rejects_fixture_content: no LEAK SCAN FAIL for: $planted"
done
printf '%s\n' \
  "VAE_TURN leg=ver02 iteration=1 model=m stop_reason=tool_use tools=redacted tool_count=1" \
  "VAE_OUTCOME leg=ver02 kind=completed partial=false reason=none executed=1 committed=1 held=0 reply_len=4 terminal_tool=redacted" \
  "VAE_TURN leg=multi_openai iteration=1 model=m stop_reason=tool_calls tools=[find_items] tool_count=1" >"$WORK/fx.in"
"$FILTER_SRC" <"$WORK/fx.in" >"$WORK/fx.out" 2>"$WORK/fx.err" || die "filter_rejects_fixture_content: a redacted or non-fixture line was rejected"
cmp -s "$WORK/fx.out" "$WORK/fx.in" || die "filter_rejects_fixture_content: a redacted or non-fixture line was changed"

# capture_save_fixture_names: the same leak coming out of the (fake) logcat: leak_scan_failed and no evidence file.
{ cat "$GOLDEN"; echo "VAE_TURN leg=ver02 iteration=2 model=m stop_reason=tool_use tools=[zz_marker_tool] tool_count=1"; } >"$WORK/fx.logcat"
FAKE_LOGCAT_FILE="$WORK/fx.logcat" run_scenario capture_save_fixture_names 1 "LEAK SCAN FAIL" "FAIL sub=capture-save reason=leak_scan_failed" \
  capture-save ver02
[ ! -e "$LAST_DIR/repo/$EVID_REL/gate1-ver02.txt" ] || die "capture_save_fixture_names: an evidence file was written"

# capture_save_ver02_redacted: the golden (redacted) fixture-leg lines are saved, and no tool name is in the file.
FAKE_LOGCAT_FILE="$GOLDEN" run_scenario capture_save_ver02_redacted 0 "-" "OK sub=capture-save kept=$(grep -c '' "$GOLDEN") dropped=0" \
  capture-save ver02
if grep -E 'leg=ver02' "$LAST_DIR/repo/$EVID_REL/gate1-ver02.txt" | grep -qE ' tools=\[| terminal_tool=[^nr ]'; then
  die "capture_save_ver02_redacted: a fixture-leg line names a tool"
fi

# capture_save_leak: the planted line comes out of the (fake) logcat: leak_scan_failed and no evidence file.
{ printf '%s\n' "VAE_TURN leg=smoke_openai iteration=1 model=$PLANT_A"; cat "$GOLDEN"; } >"$WORK/leak.logcat"
FAKE_LOGCAT_FILE="$WORK/leak.logcat" run_scenario capture_save_leak 1 "LEAK SCAN FAIL" "FAIL sub=capture-save reason=leak_scan_failed" \
  capture-save smoke_openai
[ ! -e "$LAST_DIR/repo/$EVID_REL/gate1-smoke_openai.txt" ] || die "capture_save_leak: an evidence file was written"

CALLS_EMPTY=1 run_scenario capture_save_bad_leg 2 "-" "ERROR sub=capture-save reason=usage" capture-save ../x
[ ! -e "$LAST_DIR/repo/$EVID_REL" ] || die "capture_save_bad_leg: an evidence path was created"

# capture_save_no_verdict: the golden lines hold a verdict for ver02 only, so a smoke_openai capture has none.
FAKE_LOGCAT_FILE="$GOLDEN" run_scenario capture_save_no_verdict 1 "nothing was written" "FAIL sub=capture-save reason=no_verdict_line" \
  capture-save smoke_openai
[ ! -e "$LAST_DIR/repo/$EVID_REL/gate1-smoke_openai.txt" ] || die "capture_save_no_verdict: an evidence file was written"

# capture_save_happy: golden lines + a verdict for the leg + noise: the file holds the header and the kept lines only.
{ cat "$GOLDEN"; echo "some free text from another tag"; echo "VAE_VERDICT leg=smoke_openai verdict=PASS trigger=ui"; } >"$WORK/happy.logcat"
FAKE_LOGCAT_FILE="$WORK/happy.logcat" run_scenario capture_save_happy 0 "-" "OK sub=capture-save kept=13 dropped=1 file=$EVID_REL/gate1-smoke_openai.txt" \
  capture-save smoke_openai
ev="$LAST_DIR/repo/$EVID_REL/gate1-smoke_openai.txt"
[ -f "$ev" ] || die "capture_save_happy: no evidence file"
head -1 "$ev" | grep -qE '^# gate1 leg=smoke_openai captured_utc=[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9:]{8}Z target=R5CT10XNKQN head=' \
  || die "capture_save_happy: bad header ($(head -1 "$ev"))"
[ "$(grep -c '^VAE_' "$ev")" = 13 ] || die "capture_save_happy: expected 13 evidence lines"
! grep -q 'free text' "$ev" || die "capture_save_happy: free text was written"
assert_calls capture_save_happy "-s R5CT10XNKQN logcat -d -v raw -s VaeSample:I"

# capture-start is not a counted scenario: it only clears the log buffer on the TESTER.
UNCOUNTED=1 run_scenario capture_start_clears 0 "-" "OK sub=capture-start" capture-start
assert_calls capture_start_clears "-s R5CT10XNKQN logcat -c"

# leg_list_parity: the runner's leg list equals the LegId wire strings in EvidenceLine.kt.
SCENARIOS=$((SCENARIOS + 1))
runner_legs="$(sed -nE 's/^LEGS="([^"]*)"$/\1/p' "$RUNNER_SRC" | tr ' ' '\n' | sort | tr '\n' ' ')"
app_legs="$(sed -n '/enum class LegId/,/^}/p' "$EVIDENCE_KT" | grep -oE '\("[a-z0-9_]+"\)' | tr -d '()"' | sort | tr '\n' ' ')"
[ -n "$app_legs" ] || die "leg_list_parity: could not read the LegId wires"
[ "$runner_legs" = "$app_legs" ] || die "leg_list_parity: runner '$runner_legs' differs from app '$app_legs'"

echo "SAMPLE DEVICE GUARD OK scenarios=$SCENARIOS"
