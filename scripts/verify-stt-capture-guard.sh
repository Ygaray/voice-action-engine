#!/usr/bin/env bash
# Offline proof of every refusal path of scripts/run-stt-capture.sh, with a FAKE adb and a FAKE gradlew (no device is touched,
# no real adb binary is ever invoked). Each scenario runs a copy of the runner inside a throwaway repo skeleton whose gradlew
# only records that it was called, with ADB pointed at a fake adb that logs its arguments and answers per scenario. Per
# scenario it checks the exit code, the message, the last output line and the logged adb calls, and for ALL of them:
#   - every logged adb call is the read-only `devices` listing or starts with `-s R5CT10XNKQN `
#   - a refusal scenario never logs an install / uninstall / push / pull / shell call, and never reaches the Gradle build
# Static checks on the runner's code lines (comments stripped): no adb call without -s, no personal-phone address outside its
# refusal constant, no command that writes a radio, airplane-mode or connectivity setting, and the grant check precedes the lock.
# Prints "STT CAPTURE GUARD OK scenarios=<n>" or "STT CAPTURE GUARD FAIL: <scenario>: <why>" (exit 1).
# Usage: scripts/verify-stt-capture-guard.sh
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUNNER_SRC="$HERE/run-stt-capture.sh"
LOCK_FILE="${XDG_RUNTIME_DIR:-/tmp}/vae-keystore-tester.lock"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
SCENARIOS=0
PHASE_REL=".planning/phases/14-localgrammar-bilingual-grammarpack"
REAL_GRANT="$HERE/../$PHASE_REL/14-WINDOW-GRANT.md"
REAL_PROMPTS="$HERE/../core/src/test/resources/grammar/stt-prompts.tsv"
APP_PKG="io.github.ygaray.voiceactionengine.sample"
TEST_PKG="io.github.ygaray.voiceactionengine.sample.test"
# Key shapes are assembled from fragments so that no literal key-shaped text is committed (the git secret hook scans staged files).
KEY_SHAPE="(^|[^A-Za-z0-9])s""k-[A-Za-z0-9_-]{20,}|bearer |x-api-key|authorization"
PLANT_KEY="s""k-ant-api03-abcdefghijklmnopqrstuvwxyz0123"

die() { echo "STT CAPTURE GUARD FAIL: $1" >&2; exit 1; }
[ -x "$RUNNER_SRC" ] || die "setup: $RUNNER_SRC is missing or not executable"
[ -f "$REAL_PROMPTS" ] || die "setup: $REAL_PROMPTS is missing"

# The runner shares its lock with the keystore and sample runners; if a real run holds it, every scenario would read as busy.
exec 8>"$LOCK_FILE" || die "setup: cannot open $LOCK_FILE"
flock -n 8 || die "setup: the tester lock is held by a real run; re-run when it is finished"
flock -u 8

# The fake adb: logs its arguments, then answers per the scenario knobs. Anything unexpected exits 1.
write_fake_adb() {
  cat >"$1" <<'FAKE'
#!/usr/bin/env bash
echo "$*" >>"$CALLS_LOG"
if [ "${1:-}" = devices ]; then
  echo "List of devices attached"
  printf '100.126.94.47:5555\tdevice\n'
  [ "${TESTER:-present}" = present ] && printf 'R5CT10XNKQN\tdevice\n'
  exit 0
fi
[ "${1:-}" = -s ] || exit 1
[ "${2:-}" = R5CT10XNKQN ] || exit 1
shift 2
case "${1:-}" in
  shell)
    shift
    case "$*" in
      "getprop ro.serialno") echo "${FAKE_SERIALNO:-R5CT10XNKQN}"; exit 0 ;;
      "getprop ro.product.model") echo SM-S908U; exit 0 ;;
      "getprop ro.build.version.sdk") echo 36; exit 0 ;;
      "pm list packages")
        echo "package:com.android.shell"
        [ -e "$STATE_DIR/installed_app" ] && echo "package:io.github.ygaray.voiceactionengine.sample"
        [ -e "$STATE_DIR/installed_test" ] && echo "package:io.github.ygaray.voiceactionengine.sample.test"
        exit 0 ;;
      "pm grant "*"android.permission.RECORD_AUDIO") exit 0 ;;
      "am force-stop "*) exit 0 ;;
      "mkdir -p "*) mkdir -p "$STATE_DIR/dev"; exit 0 ;;
      "test -d "*) mkdir -p "$STATE_DIR/dev"; exit 0 ;;
      "rm -f "*) exit 0 ;;
      "rm -rf "*) rm -rf "$STATE_DIR/dev"; exit 0 ;;
      "am instrument -w -e class "*)
        case "${RUN_MODE:-ok}" in
          ok) echo "INSTRUMENTATION_STATUS_CODE: 1"; echo "OK (1 test)"; exit 0 ;;
          timeout) exit 124 ;;
          *) echo "FAILURES!!!"; echo "Tests run: 1,  Failures: 1"; exit 1 ;;
        esac ;;
    esac
    exit 1 ;;
  install)
    case "$*" in
      *androidTest*) touch "$STATE_DIR/installed_test" ;;
      *) touch "$STATE_DIR/installed_app" ;;
    esac
    echo Success; exit 0 ;;
  uninstall)
    [ "${STICKY:-0}" = 1 ] && { echo Failure; exit 1; }
    case "${2:-}" in
      *.test) rm -f "$STATE_DIR/installed_test" ;;
      *) rm -f "$STATE_DIR/installed_app" ;;
    esac
    echo Success; exit 0 ;;
  push)
    mkdir -p "$STATE_DIR/dev"
    cp "${2:-}" "$STATE_DIR/dev/$(basename "${3:-}")" || exit 1
    exit 0 ;;
  pull)
    cp "$STATE_DIR/jsonl" "${3:-}" || exit 1
    exit 0 ;;
esac
exit 1
FAKE
  chmod +x "$1"
}

write_grant() {
  local file="$1" GRANT="$2"
  case "$GRANT" in
    pending | open | consumed | deferred)
      printf '# grant\n\nheader text\n\ngrant: %s\nrequested: 2026-10-06\ntimebox_s: 3600\ndevice: R5CT10XNKQN\n\n## Relay log\n\n- relayed\n' "$GRANT" >"$file" ;;
    late)
      printf '# grant\n\ngrant: pending\nrequested: 2026-10-06\n\n## Relay log\n\ngrant: open\n' >"$file" ;;
    dup)
      printf '# grant\n\ngrant: open\ngrant: pending\n\n## Relay log\n' >"$file" ;;
    space)
      printf '# grant\n\ngrant: open \nrequested: 2026-10-06\n' >"$file" ;;
    real_open)
      sed -E 's/^grant: [a-z]+$/grant: open/' "$REAL_GRANT" >"$file" ;;
    real)
      sed -E 's/^grant: [a-z]+$/grant: pending/' "$REAL_GRANT" >"$file" ;;
    none) ;;
  esac
}

# run_scenario <name> <expected-exit> <expected-message-or-"-"> <expected-final-fragment> <args...>
#   The final line must start with "STT_CAPTURE: " and contain the fragment. Knobs come from the caller's environment:
#   GRANT (default open), TESTER (present|absent), CALLS_EMPTY=1, MUTATES=1, APKS=1, FAKE_GRADLE_APK=1, RUN_MODE, STICKY=1,
#   PRE_INSTALLED=1, ANDROID_SERIAL_VALUE, FAKE_SERIALNO, NO_PROMPTS=1.
run_scenario() {
  local name="$1" want_code="$2" want_msg="$3" want_final="$4"; shift 4
  [ "${UNCOUNTED:-0}" = 1 ] || SCENARIOS=$((SCENARIOS + 1))
  local grant="${GRANT:-open}"
  local dir="$WORK/$name"
  LAST_DIR="$dir"
  mkdir -p "$dir/repo/scripts" "$dir/repo/$PHASE_REL" "$dir/repo/core/src/test/resources/grammar" "$dir/state"
  cp "$RUNNER_SRC" "$dir/repo/scripts/run-stt-capture.sh"
  chmod +x "$dir/repo/scripts/run-stt-capture.sh"
  [ "${NO_PROMPTS:-0}" = 1 ] || cp "$REAL_PROMPTS" "$dir/repo/core/src/test/resources/grammar/stt-prompts.tsv"
  write_grant "$dir/repo/$PHASE_REL/14-WINDOW-GRANT.md" "$grant"
  # A gradlew that proves the build was reached (or not). With FAKE_GRADLE_APK=1 it also leaves the two fake APKs, like a build.
  printf '#!/usr/bin/env bash\necho reached >"%s/gradle-reached"\nif [ "${FAKE_GRADLE_APK:-0}" = 1 ]; then\n  mkdir -p sample/build/outputs/apk/debug sample/build/outputs/apk/androidTest/debug\n  echo fake >sample/build/outputs/apk/debug/sample-debug.apk\n  echo fake >sample/build/outputs/apk/androidTest/debug/sample-debug-androidTest.apk\n  exit 0\nfi\nexit 1\n' "$dir" >"$dir/repo/gradlew"
  chmod +x "$dir/repo/gradlew"
  if [ "${APKS:-0}" = 1 ]; then
    mkdir -p "$dir/repo/sample/build/outputs/apk/debug" "$dir/repo/sample/build/outputs/apk/androidTest/debug"
    echo fake >"$dir/repo/sample/build/outputs/apk/debug/sample-debug.apk"
    echo fake >"$dir/repo/sample/build/outputs/apk/androidTest/debug/sample-debug-androidTest.apk"
  fi
  write_fake_adb "$dir/adb"
  : >"$dir/calls.log"
  printf '%s\n' '{"id":"en-01","lang":"en","status":"ok","recognized":"set the counter to zero"}' >"$dir/state/jsonl"
  mkdir -p "$dir/state/dev"
  if [ "${PRE_INSTALLED:-0}" = 1 ]; then touch "$dir/state/installed_app" "$dir/state/installed_test"; fi

  local out code
  # BASH_ENV is cleared: on this host it re-exports ANDROID_SERIAL (the TESTER) into every non-interactive bash, which would
  # silently overwrite the scenario's own value.
  if [ -n "${ANDROID_SERIAL_VALUE:-}" ]; then
    out="$(cd "$dir/repo" && env -u BASH_ENV SCENARIO="$name" CALLS_LOG="$dir/calls.log" STATE_DIR="$dir/state" ADB="$dir/adb" \
      ANDROID_SERIAL="$ANDROID_SERIAL_VALUE" "$dir/repo/scripts/run-stt-capture.sh" "$@" 8>&- 2>&1)"; code=$?
  else
    out="$(cd "$dir/repo" && env -u BASH_ENV -u ANDROID_SERIAL SCENARIO="$name" CALLS_LOG="$dir/calls.log" STATE_DIR="$dir/state" ADB="$dir/adb" \
      "$dir/repo/scripts/run-stt-capture.sh" "$@" 8>&- 2>&1)"; code=$?
  fi
  LAST_OUT="$out"

  [ "$code" = "$want_code" ] || die "$name: exit $code, expected $want_code ($(echo "$out" | tail -2 | tr '\n' '|'))"
  if [ "$want_msg" != "-" ]; then
    echo "$out" | grep -qF "$want_msg" || die "$name: message '$want_msg' not printed"
  fi
  local last; last="$(echo "$out" | tail -1)"
  case "$last" in
    "STT_CAPTURE: "*"$want_final"*) ;;
    *) die "$name: final line is not a STT_CAPTURE line containing '$want_final' (got: $last)" ;;
  esac
  if [ "${CALLS_EMPTY:-0}" = 1 ] && [ -s "$dir/calls.log" ]; then
    die "$name: adb was called but must not have been ($(head -3 "$dir/calls.log" | tr '\n' '|'))"
  fi
  local line
  while IFS= read -r line; do
    [ -z "$line" ] && continue
    case "$line" in
      "devices") ;;
      "-s R5CT10XNKQN "*) ;;
      *) die "$name: an adb call is not addressed to the TESTER with -s: '$line'" ;;
    esac
  done <"$dir/calls.log"
  # Refusals and read-only subcommands never mutate the device or reach the build; scenarios that legitimately do set MUTATES=1.
  if [ "${MUTATES:-0}" != 1 ]; then
    if grep -qE '(^|[[:space:]])(install|uninstall|push|pull)([[:space:]]|$)|shell (rm|mkdir|am|pm grant)' "$dir/calls.log"; then
      die "$name: a mutating adb call was logged"
    fi
    [ ! -e "$dir/gradle-reached" ] || die "$name: the Gradle build was reached"
  fi
  # No scenario may put a key-shaped token or a credential header word on an adb call or the output.
  if grep -qiE "$KEY_SHAPE" "$dir/calls.log"; then die "$name: a key-shaped token reached an argv"; fi
  if printf '%s\n' "$out" | grep -qiE "$KEY_SHAPE"; then die "$name: a key-shaped token reached the output"; fi
}

# assert_calls <name> <fixed-string>: the scenario's adb log contains the fragment; assert_no_calls is the opposite.
assert_calls() { grep -qF -- "$2" "$LAST_DIR/calls.log" || die "$1: expected an adb call containing '$2'"; }
assert_no_calls() { ! grep -qF -- "$2" "$LAST_DIR/calls.log" || die "$1: unexpected adb call containing '$2'"; }

# ---- 1. grant refusals: every device subcommand, zero adb calls, before the lock --------------------------------------------
for g in pending consumed deferred; do
  for sub in preflight install push-prompts run cleanup; do
    GRANT=$g CALLS_EMPTY=1 run_scenario "grant_${g}_$sub" 4 "NO WINDOW GRANT" "INFRA sub=$sub reason=no_grant" "$sub"
  done
  GRANT=$g CALLS_EMPTY=1 run_scenario "grant_${g}_pull" 4 "NO WINDOW GRANT" "INFRA sub=pull reason=no_grant" pull "$WORK/out-$g"
done
GRANT=none CALLS_EMPTY=1 run_scenario grant_file_missing 4 "NO WINDOW GRANT" "INFRA sub=preflight reason=no_grant" preflight
GRANT=late CALLS_EMPTY=1 run_scenario grant_open_only_in_later_block 4 "NO WINDOW GRANT" "INFRA sub=preflight reason=no_grant" preflight
GRANT=dup CALLS_EMPTY=1 run_scenario grant_two_grant_lines 4 "NO WINDOW GRANT" "INFRA sub=preflight reason=no_grant" preflight
GRANT=space CALLS_EMPTY=1 run_scenario grant_not_exact_line 4 "NO WINDOW GRANT" "INFRA sub=preflight reason=no_grant" preflight
# The committed grant file is pending today: the real file must refuse. A copy opened by one sed holds the real layout.
GRANT=real CALLS_EMPTY=1 run_scenario grant_real_file_pending 4 "NO WINDOW GRANT" "INFRA sub=preflight reason=no_grant" preflight
GRANT=real_open run_scenario grant_real_layout_opens 0 "-" "OK sub=preflight target=R5CT10XNKQN" preflight
# A refusal comes before the lock: a held lock under a pending grant is still no_grant, not tester_busy.
flock -n 8 || die "grant_pending_lock_held: cannot take the lock"
GRANT=pending CALLS_EMPTY=1 run_scenario grant_pending_lock_held 4 "NO WINDOW GRANT" "INFRA sub=preflight reason=no_grant" preflight
flock -u 8

# ---- 2. usage and target refusals -------------------------------------------------------------------------------------------
CALLS_EMPTY=1 run_scenario extra_argument 2 "-" "ERROR sub=preflight reason=usage" preflight --serial 100.126.94.47:5555
CALLS_EMPTY=1 run_scenario unknown_subcommand 2 "-" "ERROR sub=none reason=usage" frobnicate
CALLS_EMPTY=1 run_scenario no_subcommand 2 "-" "ERROR sub=none reason=usage"
CALLS_EMPTY=1 run_scenario pull_without_dir 2 "-" "ERROR sub=pull reason=usage" pull
ANDROID_SERIAL_VALUE="100.126.94.47:5555" CALLS_EMPTY=1 run_scenario foreign_android_serial 4 "TESTER IDENTITY MISMATCH - refusing" \
  "INFRA sub=preflight reason=refused_serial" preflight
TESTER=absent run_scenario tester_offline 3 "TESTER OFFLINE - not substituting" "INFRA sub=preflight reason=tester_offline" preflight
assert_no_calls tester_offline "100.126.94.47"
FAKE_SERIALNO=R5CTIMPOSTOR run_scenario identity_mismatch 4 "TESTER IDENTITY MISMATCH - refusing" "INFRA sub=preflight reason=identity_mismatch" preflight

# lock_busy: this guard holds the lock file on spare fd 8 (closed for the runner) while the runner runs, grant open.
flock -n 8 || die "lock_busy: cannot take the lock"
CALLS_EMPTY=1 run_scenario lock_busy 3 "TESTER BUSY" "INFRA sub=preflight reason=tester_busy" preflight
flock -u 8

# ---- 3. grant open + TESTER present: the happy paths, every adb call -s R5CT10XNKQN ---------------------------------------------
run_scenario preflight_happy 0 "-" "OK sub=preflight target=R5CT10XNKQN model=SM-S908U sdk=36 app_installed=no test_installed=no" preflight
assert_calls preflight_happy "-s R5CT10XNKQN shell pm list packages"

CALLS_EMPTY=1 run_scenario pull_into_repo 2 "refusing to pull raw recognizer output" "ERROR sub=pull reason=dest_in_repo" pull "$WORK/pull_into_repo/repo/captured"
CALLS_EMPTY=1 run_scenario pull_into_repo_root 2 "refusing to pull raw recognizer output" "ERROR sub=pull reason=dest_in_repo" pull "$WORK/pull_into_repo_root/repo"
MUTATES=1 run_scenario pull_outside_repo 0 "-" "OK sub=pull target=R5CT10XNKQN" pull "$WORK/host-private"
assert_calls pull_outside_repo "-s R5CT10XNKQN pull /sdcard/Android/data/$APP_PKG/files/stt-forms.jsonl"
[ -f "$WORK/host-private/stt-forms.jsonl" ] || die "pull_outside_repo: the JSONL was not pulled"

run_scenario install_without_apks 2 "run scripts/run-stt-capture.sh build first" "ERROR sub=install reason=apk_missing" install
APKS=1 MUTATES=1 run_scenario install_happy 0 "-" "OK sub=install target=R5CT10XNKQN" install
assert_calls install_happy "-s R5CT10XNKQN install -r "
assert_calls install_happy "-s R5CT10XNKQN shell pm grant $APP_PKG android.permission.RECORD_AUDIO"
[ -e "$LAST_DIR/state/installed_app" ] && [ -e "$LAST_DIR/state/installed_test" ] || die "install_happy: both packages must be installed in the fake"

MUTATES=1 run_scenario push_prompts_happy 0 "-" "OK sub=push-prompts target=R5CT10XNKQN prompts=88" push-prompts
assert_calls push_prompts_happy "-s R5CT10XNKQN push core/src/test/resources/grammar/stt-prompts.tsv /sdcard/Android/data/$APP_PKG/files/stt-prompts.tsv"
[ -f "$LAST_DIR/state/dev/stt-prompts.tsv" ] || die "push_prompts_happy: the prompt list never reached the fake device"

MUTATES=1 run_scenario run_happy 0 "-" "OK sub=run target=R5CT10XNKQN tests=1" run
assert_calls run_happy "-s R5CT10XNKQN shell am instrument -w -e class io.github.ygaray.voiceactionengine.sample.SttFormsCaptureTool -e captureSttForms true $TEST_PKG/androidx.test.runner.AndroidJUnitRunner"
! printf '%s\n' "$LAST_OUT" | grep -q 'INSTRUMENTATION_STATUS' || die "run_happy: raw instrumentation output was printed"
RUN_MODE=fail MUTATES=1 run_scenario run_failure 1 "-" "FAIL sub=run reason=instrumentation_failed" run
! printf '%s\n' "$LAST_OUT" | grep -q 'FAILURES' || die "run_failure: raw instrumentation output was printed"
RUN_MODE=timeout MUTATES=1 run_scenario run_timeout 1 "-" "FAIL sub=run reason=run_timeout" run

PRE_INSTALLED=1 MUTATES=1 run_scenario cleanup_happy 0 "capture packages removed" "OK sub=cleanup target=R5CT10XNKQN" cleanup
assert_calls cleanup_happy "-s R5CT10XNKQN uninstall $APP_PKG"
assert_calls cleanup_happy "-s R5CT10XNKQN uninstall $TEST_PKG"
assert_calls cleanup_happy "-s R5CT10XNKQN shell rm -rf /sdcard/Android/data/$APP_PKG/files/stt-capture"
[ ! -e "$LAST_DIR/state/installed_app" ] && [ ! -e "$LAST_DIR/state/installed_test" ] || die "cleanup_happy: a package is still installed in the fake"
PRE_INSTALLED=1 STICKY=1 MUTATES=1 run_scenario cleanup_unproven 1 "could not prove" "FAIL sub=cleanup reason=uninstall_failed" cleanup

# ---- 4. build: host only, no grant needed, no adb ---------------------------------------------------------------------------
GRANT=pending FAKE_GRADLE_APK=1 MUTATES=1 CALLS_EMPTY=1 run_scenario build_no_grant_reaches_gradle 0 "-" "OK sub=build app_apk=sample/build/outputs/apk/debug/sample-debug.apk" build
[ -e "$LAST_DIR/gradle-reached" ] || die "build_no_grant_reaches_gradle: the Gradle build was not run"
GRANT=pending MUTATES=1 CALLS_EMPTY=1 run_scenario build_failure 2 "Gradle build failed" "ERROR sub=build reason=build_failed" build

# ---- 5. filter: host only, allow-list by prompt id, status ok, no URL / key shape / control character ---------------------------
mk_jsonl() {
  {
    echo '{"id":"en-01","lang":"en","status":"ok","recognized":"set the counter to zero"}'
    echo '{"id":"en-02","lang":"en","status":"ok","recognized":"set the counter to 3"}'
    echo '{"id":"en-03","lang":"en","status":"error"}'
    echo '{"id":"en-04","lang":"en","status":"tts_unavailable"}'
    echo '{"id":"zz-99","lang":"en","status":"ok","recognized":"not a prompt id"}'
    echo '{"id":"en-05","lang":"en","status":"ok","recognized":"go to https://example.test now"}'
    echo '{"id":"en-06","lang":"en","status":"ok","recognized":"'"$PLANT_KEY"'"}'
    echo '{"id":"en-07","lang":"en","status":"ok","recognized":"a\u0007b"}'
    echo 'this is not json'
    echo '{"id":"en-01","lang":"en","status":"ok","recognized":"duplicate id"}'
  } >"$1"
}
SCENARIOS=$((SCENARIOS + 1))
mk_jsonl "$WORK/in.jsonl"
out="$(cd "$WORK" && env -u BASH_ENV ADB=/nonexistent "$RUNNER_SRC" filter "$WORK/in.jsonl" "$WORK/forms.tsv" 8>&- 2>&1)"; code=$?
[ "$code" = 0 ] || die "filter_happy: exit $code ($out)"
last="$(echo "$out" | tail -1)"
[ "$last" = "STT_CAPTURE: OK sub=filter ok=2 error=1 tts_unavailable=1 dropped=6 out=$WORK/forms.tsv" ] || die "filter_happy: wrong last line ($last)"
[ "$(head -1 "$WORK/forms.tsv")" = "$(printf 'id\tlang\ttext\texpect\trecognized\tprovenance')" ] || die "filter_happy: wrong header"
[ "$(grep -c '' "$WORK/forms.tsv")" = 3 ] || die "filter_happy: expected the header and two rows"
[ "$(awk -F'\t' 'NR>1 && NF==6 && $6=="synthetic-tts"' "$WORK/forms.tsv" | wc -l | tr -d ' ')" = 2 ] || die "filter_happy: rows must carry provenance synthetic-tts"
grep -qP '^en-01\ten\tset the counter to zero\t0\tset the counter to zero\tsynthetic-tts$' "$WORK/forms.tsv" || die "filter_happy: en-01 row is wrong"
! grep -qE 'example.test|not a prompt id|duplicate id|not json' "$WORK/forms.tsv" || die "filter_happy: a dropped row reached the output"
! grep -qiE "$KEY_SHAPE" "$WORK/forms.tsv" || die "filter_happy: a key shape reached the output"
! grep -qP '[\x00-\x08\x0b-\x1f\x7f]' "$WORK/forms.tsv" || die "filter_happy: a control character reached the output"
! printf '%s\n' "$out" | grep -qiE "$KEY_SHAPE|example.test" || die "filter_happy: dropped content was printed"

# filter_no_ok_rows: nothing usable -> exit 1 and no output file.
SCENARIOS=$((SCENARIOS + 1))
printf '%s\n' '{"id":"en-03","lang":"en","status":"error"}' >"$WORK/none.jsonl"
out="$(cd "$WORK" && env -u BASH_ENV "$RUNNER_SRC" filter "$WORK/none.jsonl" "$WORK/none.tsv" 8>&- 2>&1)"; code=$?
[ "$code" = 1 ] || die "filter_no_ok_rows: exit $code, expected 1"
echo "$out" | tail -1 | grep -q '^STT_CAPTURE: FAIL sub=filter reason=no_ok_rows' || die "filter_no_ok_rows: wrong last line"
[ ! -e "$WORK/none.tsv" ] || die "filter_no_ok_rows: an output file was written"

# ---- 6. static checks on the runner's code lines ----------------------------------------------------------------------------
CODE="$WORK/runner.code"
sed -E 's/^[[:space:]]*#.*$//' "$RUNNER_SRC" >"$CODE"

# static_adb_needs_s: every adb invocation in code is the single "$ADB" in adb_t, and every adb_t call carries -s or is `devices`.
SCENARIOS=$((SCENARIOS + 1))
[ "$(grep -c '"\$ADB"' "$CODE")" = 1 ] || die "static_adb_needs_s: the adb binary must be invoked in exactly one place (adb_t)"
if grep -E 'adb_t [0-9"$]' "$CODE" | grep -vE 'adb_t ([0-9]+|"\$RUN_TIMEOUT") (-s "\$TARGET"|devices)|adb_t\(\)' | grep -q .; then
  die "static_adb_needs_s: an adb_t call has neither -s nor the devices listing"
fi
grep -qE '^adbt\(\) \{ adb_t 30 -s "\$TARGET" ' "$CODE" || die "static_adb_needs_s: the adbt helper must carry -s"
if grep -E '(^|[^_a-zA-Z])adb[[:space:]]' "$CODE" | grep -q .; then die "static_adb_needs_s: a bare adb command appears in code"; fi

# static_target_pinned: TARGET is only ever the TESTER constant, and the personal address appears only on its constant line.
SCENARIOS=$((SCENARIOS + 1))
[ "$(grep -c '^TESTER_USB="R5CT10XNKQN"$' "$CODE")" = 1 ] || die "static_target_pinned: TESTER_USB constant missing"
[ "$(grep -c 'TARGET="[^"]' "$CODE")" = 1 ] || die "static_target_pinned: TARGET must be assigned from one place only"
grep -q '^  TARGET="\$TESTER_USB"$' "$CODE" || die "static_target_pinned: TARGET must be assigned the TESTER constant"
[ "$(grep -c '100\.126\.94\.47' "$CODE")" = 1 ] || die "static_target_pinned: the personal phone address must appear once, as its refusal constant"
grep -q '^PERSONAL_IP="100\.126\.94\.47"$' "$CODE" || die "static_target_pinned: the personal address is not on its constant line"
if grep -qE '100\.118\.21\.106|adb_t [0-9]+ connect|[^_]connect ' "$CODE"; then die "static_target_pinned: a wireless target or connect call appears"; fi

# static_no_radio_writes: no command that changes radio, airplane-mode or connectivity state (the pattern list lives only here).
SCENARIOS=$((SCENARIOS + 1))
RADIO_RE='airplane|flight_?mode|svc +(wifi|data|bluetooth|nfc|usb|power)|settings +(put|delete)|cmd +(wifi|connectivity|netpolicy)|ifconfig|ip +(link|addr|route)|tailscale|setprop|nmcli|rfkill|bluetooth_on|wifi_on|mobile_data|iptables|netd'
if grep -qiE "$RADIO_RE" "$CODE"; then die "static_no_radio_writes: a radio, airplane-mode or connectivity command appears in the runner ($(grep -iE "$RADIO_RE" "$CODE" | head -1))"; fi

# static_grant_before_lock: the grant check is invoked before the lock and before the first adb-using step in the dispatch.
SCENARIOS=$((SCENARIOS + 1))
grant_line="$(grep -n '^require_open_grant$' "$CODE" | cut -d: -f1)"
lock_line="$(grep -n '^acquire_and_resolve$' "$CODE" | cut -d: -f1)"
[ -n "$grant_line" ] && [ -n "$lock_line" ] && [ "$grant_line" -lt "$lock_line" ] || die "static_grant_before_lock: the grant check must precede the lock"
[ "$(grep -c 'flock' "$CODE")" = 1 ] || die "static_grant_before_lock: flock must appear in exactly one place"
grep -q 'grant: open' "$RUNNER_SRC" || die "static_grant_before_lock: the exact grant line is not named in the runner"

# static_modes: both scripts are executable.
SCENARIOS=$((SCENARIOS + 1))
[ -x "$RUNNER_SRC" ] && [ -x "$HERE/verify-stt-capture-guard.sh" ] || die "static_modes: both scripts must be executable"

echo "STT CAPTURE GUARD OK scenarios=$SCENARIOS"
