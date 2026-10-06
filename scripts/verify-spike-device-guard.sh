#!/usr/bin/env bash
# Offline proof of every refusal path and every device step of scripts/run-spike-ondevice.sh, with a FAKE adb, a FAKE gradlew
# and a FAKE curl (no device is touched, no network is used, no model of real size exists). Each scenario runs a copy of the
# runner inside a throwaway git repo skeleton, with ADB pointed at a fake adb that logs its arguments and answers per scenario,
# and with the model pins, poll intervals and cool-down cap overridden ONLY inside that copy. Per scenario it checks the exit
# code, the message, the last output line and the logged adb calls, and for ALL of them:
#   - every logged adb call is `connect 100.118.21.106:1496` or starts with `-s R5CT10XNKQN ` / `-s 100.118.21.106:1496 `
#     (so a call without -s, or addressed to any other serial, fails the guard)
#   - a refusal / read-only scenario never logs an install / uninstall / push / run-as call, and never reaches the Gradle build
#   - no key-shaped token reaches an argv or the output
# Prints "SPIKE DEVICE GUARD OK scenarios=<n>" or "SPIKE DEVICE GUARD FAIL: <scenario>: <why>" (exit 1).
# Usage: scripts/verify-spike-device-guard.sh
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUNNER_SRC="$HERE/run-spike-ondevice.sh"
FILTER_SRC="$HERE/spike-evidence-filter.sh"
GOLDEN="$HERE/../spike-ondevice/src/test/resources/evidence-golden.txt"
LOCK_FILE="${XDG_RUNTIME_DIR:-/tmp}/vae-keystore-tester.lock"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
SCENARIOS=0
PHASE_REL=".planning/phases/13-on-device-model-spike"
EVID_REL="$PHASE_REL/evidence"
PKG="io.github.ygaray.voiceactionengine.spike"
EXT_MODELS="/sdcard/Android/data/$PKG/files/models"
CPU_FILE="gemma-4-E2B-it.litertlm"
GPU_FILE="gemma-4-E2B-it-gpu.litertlm"
G3_FILE="Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm"
# Key shapes are assembled from fragments so that no literal key-shaped text is committed (the git secret hook scans staged files).
KEY_SHAPE="(^|[^A-Za-z0-9])s""k-[A-Za-z0-9_-]{20,}|bearer |x-api-key|authorization"
PLANT="s""k-ant-api03-abcdefghijklmnopqrstuvwxyz0123"
FIXTURE_MARKER="zz-private-fixture-marker"

die() { echo "SPIKE DEVICE GUARD FAIL: $1" >&2; exit 1; }
[ -x "$RUNNER_SRC" ] || die "setup: $RUNNER_SRC is missing or not executable"
[ -x "$FILTER_SRC" ] || die "setup: $FILTER_SRC is missing or not executable"
[ -f "$GOLDEN" ] || die "setup: $GOLDEN is missing"

# The runner shares its lock with the other device runners; if a real run holds it, every scenario would read as busy.
exec 8>"$LOCK_FILE" || die "setup: cannot open $LOCK_FILE"
flock -n 8 || die "setup: the tester lock is held by a real run; re-run when it is finished"
flock -u 8

sha_of() { printf '%s' "$1" | sha256sum | cut -d' ' -f1; }
CPU_CONTENT="fake-model-cpu"
GPU_CONTENT="fake-model-gpu"
CPU_SHA="$(sha_of "$CPU_CONTENT")"
GPU_SHA="$(sha_of "$GPU_CONTENT")"

# The fake adb: logs its arguments, then answers per $SCENARIO and the FAKE_* knobs. Anything unexpected exits 1.
write_fake_adb() {
  cat >"$1" <<'FAKE'
#!/usr/bin/env bash
echo "$*" >>"$CALLS_LOG"
[ "$SCENARIO" = offline ] && exit 1
if [ "${1:-}" = connect ]; then
  case "$SCENARIO" in wireless_impostor*) echo "connected to ${2:-}"; exit 0 ;; esac
  exit 1
fi
serial=""
if [ "${1:-}" = -s ]; then serial="${2:-}"; shift 2; fi
PKG_FAKE="io.github.ygaray.voiceactionengine.spike"
case "${1:-}" in
  get-state)
    case "$SCENARIO:$serial" in
      wireless_impostor*:100.118.21.106:1496) echo device; exit 0 ;;
      wireless_impostor*:*) ;;
      *:R5CT10XNKQN) echo device; exit 0 ;;
    esac
    echo "error: device '$serial' not found" >&2; exit 1 ;;
  shell)
    shift
    case "$*" in
      "getprop ro.serialno")
        case "$SCENARIO" in usb_impostor* | wireless_impostor*) echo R5CTIMPOSTOR ;; *) echo R5CT10XNKQN ;; esac
        exit 0 ;;
      "getprop ro.product.model") echo SM-S908U; exit 0 ;;
      "getprop ro.build.version.sdk") echo 36; exit 0 ;;
      "getprop ro.product.cpu.abi") echo arm64-v8a; exit 0 ;;
      "getconf PAGESIZE") echo 4096; exit 0 ;;
      "dumpsys activity activities") echo "  topResumedActivity=ActivityRecord{1 u0 com.example/.Main t1}"; exit 0 ;;
      "pm list packages")
        echo "package:com.android.shell"
        [ -e "$STATE_DIR/installed" ] && echo "package:$PKG_FAKE"
        exit 0 ;;
      "cat /proc/meminfo")
        echo "MemTotal:       11800000 kB"; echo "MemFree:         500000 kB"; echo "MemAvailable:   6100000 kB"; exit 0 ;;
      "df -k /sdcard")
        echo "Filesystem     1K-blocks     Used Available Use% Mounted on"
        echo "/dev/fuse      200000000 50000000 ${FAKE_AVAIL_KB:-90000000}  36% /storage/emulated"
        exit 0 ;;
      "ls /vendor/lib64/libOpenCL.so"*) echo present; exit 0 ;;
      "dumpsys thermalservice")
        n=$(($(cat "$STATE_DIR/thermal.n" 2>/dev/null || echo 0) + 1)); echo "$n" >"$STATE_DIR/thermal.n"
        if [ "$n" -le "${FAKE_THERMAL_HOT_CALLS:-0}" ]; then echo "Thermal Status: 4"; else echo "Thermal Status: ${FAKE_THERMAL:-0}"; fi
        exit 0 ;;
      "dumpsys battery") echo "Current Battery Service state:"; echo "  level: 87"; exit 0 ;;
      "dumpsys meminfo "*)
        echo "** MEMINFO in pid 4242 [$PKG_FAKE] **"
        echo "           TOTAL PSS:  1572864            TOTAL RSS:  2000000       TOTAL SWAP PSS:        0"
        exit 0 ;;
      "cmd deviceidle whitelist "*) exit 0 ;;
      "am force-stop "*) rm -f "$STATE_DIR/running"; exit 0 ;;
      "am start -n "*)
        [ "${FAKE_PROC_DIES:-0}" = 1 ] || touch "$STATE_DIR/running"
        exit 0 ;;
      "pidof "*) [ -e "$STATE_DIR/running" ] && { echo 4242; exit 0; }; exit 1 ;;
      "[ -d /sdcard/Android/data/"*) [ "${FAKE_NO_MODELS_DIR:-0}" = 1 ] && echo no || echo yes; exit 0 ;;
      "sha256sum /sdcard/Android/data/"*)
        [ -f "$STATE_DIR/extmodel" ] || exit 1
        if [ "${FAKE_READBACK_CORRUPT:-0}" = 1 ]; then printf 'corrupt' | sha256sum | cut -d' ' -f1
        else sha256sum "$STATE_DIR/extmodel" | cut -d' ' -f1; fi
        exit 0 ;;
      "rm -f /sdcard/Android/data/"*) rm -f "$STATE_DIR/extmodel"; exit 0 ;;
      "rm -rf /sdcard/Android/data/"*) [ "${FAKE_EXT_RESIDUE:-0}" = 1 ] || rm -f "$STATE_DIR/extmodel"; exit 0 ;;
      "ls /sdcard/Android/data/"*) [ -e "$STATE_DIR/extmodel" ] && echo present || echo absent; exit 0 ;;
      "rm -f /data/local/tmp/vae-spike-*")
        [ "${FAKE_TMP_RESIDUE:-0}" = 1 ] || rm -f "$STATE_DIR"/dev/vae-spike-*
        exit 0 ;;
      "rm -f /data/local/tmp/"*) rm -f "$STATE_DIR/dev/$(basename "${3:-}")"; exit 0 ;;
      "ls -d /data/local/tmp/vae-spike-*"*)
        for f in "$STATE_DIR"/dev/vae-spike-*; do [ -e "$f" ] && echo "/data/local/tmp/$(basename "$f")"; done
        echo __done; exit 0 ;;
      "run-as "*"cat > files/models/"*)
        staging="${*##*< }"; src="$STATE_DIR/dev/$(basename "$staging")"
        [ -f "$src" ] || exit 1
        cp "$src" "$STATE_DIR/appmodel"; exit 0 ;;
      "run-as "*"sha256sum files/models/"*)
        [ -f "$STATE_DIR/appmodel" ] || exit 1
        if [ "${FAKE_READBACK_CORRUPT:-0}" = 1 ]; then printf 'corrupt' | sha256sum | cut -d' ' -f1
        else sha256sum "$STATE_DIR/appmodel" | cut -d' ' -f1; fi
        exit 0 ;;
      "run-as "*"cat > files/private/"*)
        staging="${*##*< }"; src="$STATE_DIR/dev/$(basename "$staging")"
        [ -f "$src" ] || exit 1
        name="${*##*cat > files/private/}"; name="${name%%\'*}"
        cp "$src" "$STATE_DIR/priv-$name"; exit 0 ;;
      "run-as "*"sha256sum files/private/"*)
        name="${*##*sha256sum files/private/}"
        [ -f "$STATE_DIR/priv-$name" ] || exit 1
        sha256sum "$STATE_DIR/priv-$name" | cut -d' ' -f1; exit 0 ;;
      "run-as "*"grep -c "*)
        n=$(($(cat "$STATE_DIR/stage.n" 2>/dev/null || echo 0) + 1)); echo "$n" >"$STATE_DIR/stage.n"
        if [ "$n" -gt 1 ] && [ "${FAKE_STAGE_DONE:-0}" = 1 ]; then echo 1; else echo 0; fi
        exit 0 ;;
      "run-as "*"grep '^VAE_SPIKE_STAGE"*) echo "VAE_SPIKE_STAGE stage=fake result=done trials=3"; exit 0 ;;
      "run-as "*"cat files/evidence/"*)
        [ -n "${FAKE_EVIDENCE_FILE:-}" ] && [ -f "$FAKE_EVIDENCE_FILE" ] || exit 1
        cat "$FAKE_EVIDENCE_FILE"; exit 0 ;;
      "run-as "*" rm -rf "*) rm -f "$STATE_DIR/appmodel" "$STATE_DIR"/priv-*; exit 0 ;;
      "run-as "*" rm -f "*) rm -f "$STATE_DIR/appmodel"; exit 0 ;;
    esac
    exit 1 ;;
  install) touch "$STATE_DIR/installed"; echo Success; exit 0 ;;
  uninstall) [ "${FAKE_UNINSTALL_FAILS:-0}" = 1 ] || rm -f "$STATE_DIR/installed"; echo Success; exit 0 ;;
  push)
    mkdir -p "$STATE_DIR/dev"
    case "${3:-}" in
      /sdcard/Android/data/*)
        [ "${FAKE_REFUSE_DIRECT:-0}" = 1 ] && exit 1
        cp "${2:-}" "$STATE_DIR/extmodel" || exit 1 ;;
      *) cp "${2:-}" "$STATE_DIR/dev/$(basename "${3:-}")" || exit 1 ;;
    esac
    exit 0 ;;
esac
exit 1
FAKE
  chmod +x "$1"
}

# A fake curl: writes a small "model" chosen by the URL into the -o file, and logs its argv. No network is used.
write_fake_curl() {
  cat >"$1" <<'FAKE'
#!/usr/bin/env bash
echo "$*" >>"$CURL_LOG"
out=""; url=""
while [ $# -gt 0 ]; do
  case "$1" in -o) out="$2"; shift 2 ;; http*) url="$1"; shift ;; *) shift ;; esac
done
case "$url" in *-gpu.litertlm) printf 'fake-model-gpu' >"$out" ;; *) printf 'fake-model-cpu' >"$out" ;; esac
[ "${FAKE_CURL_BAD:-0}" = 1 ] && printf 'tampered' >"$out"
exit "${FAKE_CURL_RC:-0}"
FAKE
  chmod +x "$1"
}

# run_scenario <name> <expected-exit> <expected-message-or-"-"> <expected-final-fragment> <args...>
#   The final line must start with "SPIKE_ONDEVICE: " and contain the fragment. Knobs come from the caller's environment:
#   CALLS_EMPTY=1 (adb must not have been called), ANDROID_SERIAL_VALUE (a foreign ANDROID_SERIAL for the run), MUTATES=1,
#   GRANT (open|pending|lookalike|missing), TIMEBOX (the grant's timebox_s), GRANT_PIN (match|bad|none), G3 (the grant's
#   gemma3_1b value), STAMP (none|fresh|expired|short), DIRTY=1, THR_DIRTY=1, MODEL_FILES ("cpu gpu g3"), MODEL_BAD (cpu),
#   PRIVATE (good|mismatch|missing), MODEL_DIR_IN_REPO=1, PHASE_OVERRIDE, PRE_INSTALLED / PRE_EXT / PRE_TMP / PRE_RUNNING,
#   COOL_CAP, and the FAKE_* knobs of the fake adb / curl.
run_scenario() {
  local name="$1" want_code="$2" want_msg="$3" want_final="$4"; shift 4
  [ "${UNCOUNTED:-0}" = 1 ] || SCENARIOS=$((SCENARIOS + 1))
  local dir="$WORK/$name"
  LAST_DIR="$dir"
  mkdir -p "$dir/repo/scripts" "$dir/repo/$PHASE_REL" "$dir/state/dev" "$dir/cache"
  cp "$RUNNER_SRC" "$dir/repo/scripts/run-spike-ondevice.sh"
  chmod +x "$dir/repo/scripts/run-spike-ondevice.sh"
  cp "$FILTER_SRC" "$dir/repo/scripts/spike-evidence-filter.sh"
  chmod +x "$dir/repo/scripts/spike-evidence-filter.sh"
  # The pins, poll intervals and the cool-down cap are overridden ONLY in this throwaway copy (the real runner has no knob).
  sed -i -E \
    -e "s/^E2B_CPU_BYTES=.*/E2B_CPU_BYTES=${#CPU_CONTENT}/" -e "s/^E2B_CPU_SHA=.*/E2B_CPU_SHA=\"$CPU_SHA\"/" \
    -e "s/^E2B_GPU_BYTES=.*/E2B_GPU_BYTES=${#GPU_CONTENT}/" -e "s/^E2B_GPU_SHA=.*/E2B_GPU_SHA=\"$GPU_SHA\"/" \
    -e "s/^POLL_S=.*/POLL_S=1/" -e "s/^COOL_POLL_S=.*/COOL_POLL_S=1/" -e "s/^COOL_CAP_S=.*/COOL_CAP_S=${COOL_CAP:-3}/" \
    "$dir/repo/scripts/run-spike-ondevice.sh"
  grep -q "^E2B_CPU_SHA=\"$CPU_SHA\"$" "$dir/repo/scripts/run-spike-ondevice.sh" || die "$name: setup: the pin override did not apply"
  # A gradlew that proves the build was reached (and with which options). With FAKE_GRADLE_APK=1 it also leaves one fake APK.
  printf '#!/usr/bin/env bash\necho "$GRADLE_OPTS | $*" >"%s/gradle-reached"\nif [ "${FAKE_GRADLE_APK:-0}" = 1 ]; then\n  mkdir -p spike-ondevice/build/outputs/apk/debug\n  echo fake-apk >spike-ondevice/build/outputs/apk/debug/spike-debug.apk\n  exit 0\nfi\nexit 1\n' "$dir" >"$dir/repo/gradlew"
  chmod +x "$dir/repo/gradlew"
  printf '# Phase 13 locked thresholds (fake for the guard verifier)\nTHRESHOLD warm_p50_ms=3000\n' >"$dir/repo/$PHASE_REL/13-THRESHOLDS.md"
  # A throwaway git repo so the dirty-tree check and the head stamp have something real to read.
  (
    cd "$dir/repo" || exit 1
    git init -q . >/dev/null 2>&1
    git add scripts gradlew "$PHASE_REL/13-THRESHOLDS.md" >/dev/null 2>&1
    GIT_AUTHOR_NAME=t GIT_AUTHOR_EMAIL=t@t GIT_COMMITTER_NAME=t GIT_COMMITTER_EMAIL=t@t \
      git -c core.hooksPath=/dev/null -c commit.gpgsign=false commit -q -m init >/dev/null 2>&1
  ) || die "$name: setup: the throwaway repo could not be created"
  if [ "${DIRTY:-0}" = 1 ]; then mkdir -p "$dir/repo/spike-ondevice/src" && echo x >"$dir/repo/spike-ondevice/src/Dirty.kt"; fi
  if [ "${THR_DIRTY:-0}" = 1 ]; then echo "THRESHOLD warm_p50_ms=9999" >>"$dir/repo/$PHASE_REL/13-THRESHOLDS.md"; fi

  write_fake_adb "$dir/adb"
  write_fake_curl "$dir/curl"
  : >"$dir/calls.log"
  : >"$dir/curl.log"
  [ "${PRE_INSTALLED:-0}" = 1 ] && touch "$dir/state/installed"
  [ "${PRE_EXT:-0}" = 1 ] && echo leftover >"$dir/state/extmodel"
  [ "${PRE_TMP:-0}" = 1 ] && echo leftover >"$dir/state/dev/vae-spike-leftover"
  [ "${PRE_RUNNING:-0}" = 1 ] && touch "$dir/state/running"

  # Host inputs: the model directory (outside the repo, or inside for the refusal), the private SB files, the grant, the stamp.
  local models="$dir/models"
  [ "${MODEL_DIR_IN_REPO:-0}" = 1 ] && models="$dir/repo/models"
  mkdir -p "$models" "$dir/private"
  local f
  for f in ${MODEL_FILES:-}; do
    case "$f" in
      cpu) if [ "${MODEL_BAD:-}" = cpu ]; then printf 'tampered-model' >"$models/$CPU_FILE"; else printf '%s' "$CPU_CONTENT" >"$models/$CPU_FILE"; fi ;;
      gpu) printf '%s' "$GPU_CONTENT" >"$models/$GPU_FILE" ;;
      g3) printf 'fake-g3-model' >"$models/$G3_FILE" ;;
    esac
  done
  local fixture_sha=""
  case "${PRIVATE:-}" in
    good | mismatch)
      printf '{"system":"%s","tools":[]}' "$FIXTURE_MARKER" >"$dir/private/sb-fixture.json"
      fixture_sha="$(sha256sum "$dir/private/sb-fixture.json" | cut -d' ' -f1)"
      if [ "$PRIVATE" = good ]; then
        jq -n --arg s "$fixture_sha" '{envelope:"sb",fixture_sha256:$s,items:[],tool_meta:{},version:1}' >"$dir/private/sb-gold.json"
      else
        jq -n --arg s "$(sha_of other)" '{envelope:"sb",fixture_sha256:$s,items:[],tool_meta:{},version:1}' >"$dir/private/sb-gold.json"
      fi
      ;;
  esac
  local grant="$dir/repo/$PHASE_REL/13-WINDOW-GRANT.md" pin=""
  case "${GRANT_PIN:-none}" in match) pin="${fixture_sha:0:8}" ;; bad) pin="deadbeef" ;; esac
  case "${GRANT:-open}" in
    open) printf '# grant\n\ngrant: open\n' >"$grant" ;;
    pending) printf '# grant\n\ngrant: pending\n' >"$grant" ;;
    lookalike) printf '# grant\n\ngrant: opened\n grant: open\nGrant: open\n# grant: open\ngrant:open\n' >"$grant" ;;
    missing) : ;;
  esac
  if [ "${GRANT:-open}" != missing ]; then
    {
      echo "relayed_by: test"
      echo "date: 2026-01-01"
      echo "timebox_s: ${TIMEBOX:-14400}"
      echo "sb_labels: ${SB_LABELS:-default}"
      [ -n "$pin" ] && echo "sb_fixture_sha8: $pin"
      echo "gemma3_1b: ${G3:-skipped_gated}"
    } >>"$grant"
  fi
  case "${STAMP:-none}" in
    fresh) mkdir -p "$dir/cache/vae-spike"; echo "$(date +%s) 14400" >"$dir/cache/vae-spike/window.ts" ;;
    expired) mkdir -p "$dir/cache/vae-spike"; echo "$(($(date +%s) - 20000)) 14400" >"$dir/cache/vae-spike/window.ts" ;;
    short) mkdir -p "$dir/cache/vae-spike"; echo "$(date +%s) 5" >"$dir/cache/vae-spike/window.ts" ;;
  esac

  local out code phase="${PHASE_OVERRIDE:-$PHASE_REL}"
  # BASH_ENV is cleared: on this host it re-exports ANDROID_SERIAL (the TESTER) into every non-interactive bash, which would
  # silently overwrite the scenario's own value.
  if [ -n "${ANDROID_SERIAL_VALUE:-}" ]; then
    out="$(env -u BASH_ENV SCENARIO="$name" CALLS_LOG="$dir/calls.log" CURL_LOG="$dir/curl.log" STATE_DIR="$dir/state" ADB="$dir/adb" CURL="$dir/curl" \
      XDG_CACHE_HOME="$dir/cache" VAE_SPIKE_PHASE_DIR="$phase" VAE_SPIKE_MODEL_DIR="$models" VAE_SPIKE_PRIVATE_DIR="$dir/private" \
      ANDROID_SERIAL="$ANDROID_SERIAL_VALUE" "$dir/repo/scripts/run-spike-ondevice.sh" "$@" 8>&- 2>&1)"; code=$?
  else
    out="$(env -u BASH_ENV -u ANDROID_SERIAL SCENARIO="$name" CALLS_LOG="$dir/calls.log" CURL_LOG="$dir/curl.log" STATE_DIR="$dir/state" ADB="$dir/adb" CURL="$dir/curl" \
      XDG_CACHE_HOME="$dir/cache" VAE_SPIKE_PHASE_DIR="$phase" VAE_SPIKE_MODEL_DIR="$models" VAE_SPIKE_PRIVATE_DIR="$dir/private" \
      "$dir/repo/scripts/run-spike-ondevice.sh" "$@" 8>&- 2>&1)"; code=$?
  fi
  LAST_OUT="$out"

  [ "$code" = "$want_code" ] || die "$name: exit $code, expected $want_code (output: $(printf '%s' "$out" | tail -3 | tr '\n' '|'))"
  if [ "$want_msg" != "-" ]; then
    echo "$out" | grep -qF "$want_msg" || die "$name: message '$want_msg' not printed"
  fi
  local last; last="$(echo "$out" | tail -1)"
  case "$last" in
    "SPIKE_ONDEVICE: "*"$want_final"*) ;;
    *) die "$name: final line is not a SPIKE_ONDEVICE line containing '$want_final' (got: $last)" ;;
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
  # No scenario may put a key-shaped token or a credential header word on an adb call or the output, and the private fixture
  # content never reaches an argv or the output.
  if grep -qiE "$KEY_SHAPE" "$dir/calls.log"; then die "$name: a key-shaped token reached an argv"; fi
  if printf '%s\n' "$out" | grep -qiE "$KEY_SHAPE"; then die "$name: a key-shaped token reached the output"; fi
  if grep -qF "$FIXTURE_MARKER" "$dir/calls.log" || printf '%s\n' "$out" | grep -qF "$FIXTURE_MARKER"; then
    die "$name: private fixture content reached an argv or the output"
  fi
}

# assert_calls <name> <fixed-string>: the scenario's adb log contains the fragment; assert_no_calls is the opposite.
assert_calls() { grep -qF -- "$2" "$LAST_DIR/calls.log" || die "$1: expected an adb call containing '$2'"; }
assert_no_calls() { ! grep -qF -- "$2" "$LAST_DIR/calls.log" || die "$1: unexpected adb call containing '$2'"; }
assert_dev_clean() { [ -z "$(ls -A "$LAST_DIR/state/dev")" ] || die "$1: a staging file was left on the device"; }
evidence() { echo "$LAST_DIR/repo/$EVID_REL/$1"; }

# ---- Task 1: usage, grant, target refusals, preflight, evidence pull -----------------------------------------------------
CALLS_EMPTY=1 run_scenario usage_no_subcommand 2 "-" "ERROR sub=none reason=usage"
CALLS_EMPTY=1 run_scenario usage_unknown_subcommand 2 "-" "ERROR sub=none reason=usage" frobnicate
CALLS_EMPTY=1 run_scenario usage_extra_argument 2 "-" "ERROR sub=preflight reason=usage" preflight --serial 100.126.94.47:5555
CALLS_EMPTY=1 run_scenario usage_bad_stage 2 "-" "ERROR sub=run reason=usage" run ../x
CALLS_EMPTY=1 run_scenario usage_bad_model 2 "-" "ERROR sub=push-model reason=usage" push-model gemma-9000
CALLS_EMPTY=1 run_scenario usage_missing_stage 2 "-" "ERROR sub=pull-evidence reason=usage" pull-evidence

# The window grant: no exact `grant: open` line, no adb call (and no lock): pending, missing, and look-alike lines.
GRANT=pending CALLS_EMPTY=1 run_scenario grant_pending 2 "not touching the device" "ERROR sub=preflight reason=window_not_granted" preflight
GRANT=missing CALLS_EMPTY=1 run_scenario grant_missing 2 "not touching the device" "ERROR sub=preflight reason=window_not_granted" preflight
GRANT=lookalike CALLS_EMPTY=1 run_scenario grant_lookalike 2 "not touching the device" "ERROR sub=cleanup reason=window_not_granted" cleanup
GRANT=pending CALLS_EMPTY=1 run_scenario grant_pending_blocks_run 2 "not touching the device" "ERROR sub=run reason=window_not_granted" run init
PHASE_OVERRIDE="/tmp/elsewhere" CALLS_EMPTY=1 run_scenario phase_dir_absolute 2 "relative path inside the repository" "ERROR sub=preflight reason=bad_phase_dir" preflight
PHASE_OVERRIDE="../elsewhere" CALLS_EMPTY=1 run_scenario phase_dir_dotdot 2 "relative path inside the repository" "ERROR sub=preflight reason=bad_phase_dir" preflight

ANDROID_SERIAL_VALUE="100.126.94.47:5555" CALLS_EMPTY=1 run_scenario foreign_android_serial_personal 4 "TESTER IDENTITY MISMATCH - refusing" \
  "INFRA sub=preflight reason=refused_serial" preflight
ANDROID_SERIAL_VALUE="emulator-5554" CALLS_EMPTY=1 run_scenario foreign_android_serial_emulator 4 "TESTER IDENTITY MISMATCH - refusing" \
  "INFRA sub=run reason=refused_serial" run init
run_scenario offline 3 "TESTER OFFLINE - not substituting" "INFRA sub=preflight reason=tester_offline" preflight
run_scenario usb_impostor 4 "TESTER IDENTITY MISMATCH - refusing" "INFRA sub=preflight reason=identity_mismatch" preflight
run_scenario wireless_impostor 4 "TESTER IDENTITY MISMATCH - refusing" "INFRA sub=preflight reason=identity_mismatch" preflight
# A refusal never reaches install, push, run-as or the build, even for a mutating subcommand with every host input in place.
STAMP=fresh PRIVATE=good run_scenario usb_impostor_push_private 4 "TESTER IDENTITY MISMATCH - refusing" "INFRA sub=push-private reason=identity_mismatch" push-private
STAMP=fresh MODEL_FILES="cpu" run_scenario wireless_impostor_push_model 4 "TESTER IDENTITY MISMATCH - refusing" "INFRA sub=push-model reason=identity_mismatch" push-model e2b_cpu

# lock_busy: this guard holds the lock file on spare fd 8 (closed for the runner) while the runner runs.
flock -n 8 || die "lock_busy: cannot take the lock"
CALLS_EMPTY=1 run_scenario lock_busy 3 "TESTER BUSY" "INFRA sub=preflight reason=tester_busy" preflight
flock -u 8

run_scenario preflight_ok 0 "foreground:" "OK sub=preflight target=R5CT10XNKQN model=SM-S908U sdk=36 installed=no" preflight
pf="$(evidence preflight.host.txt)"
[ -f "$pf" ] || die "preflight_ok: no preflight.host.txt"
grep -qxE 'VAE_SPIKE_PREFLIGHT source=host mem_total_kb=11800000 mem_avail_kb=6100000 storage_free_kb=90000000 abi=arm64-v8a page_size=4096 opencl=present thermal_status=0 battery=87' "$pf" \
  || die "preflight_ok: the PREFLIGHT line is not the closed grammar ($(cat "$pf"))"
"$FILTER_SRC" <"$pf" >"$WORK/pf.out" 2>"$WORK/pf.err" || die "preflight_ok: the PREFLIGHT line is rejected by the filter"
cmp -s "$WORK/pf.out" "$pf" || die "preflight_ok: the filter changed the PREFLIGHT line"
assert_no_calls preflight_ok " rm "
assert_no_calls preflight_ok "whitelist"

# pull_evidence_ok: golden lines + noise: the file holds the header and the kept lines only.
{ grep -vE '^(#|$)' "$GOLDEN"; echo "some free text from the app"; } >"$WORK/pull.in"
kept_n="$(grep -vE '^(#|$)' "$GOLDEN" | grep -c '')"
FAKE_EVIDENCE_FILE="$WORK/pull.in" MUTATES=1 run_scenario pull_evidence_ok 0 "-" "OK sub=pull-evidence stage=screen_small kept=$kept_n dropped=1 file=$EVID_REL/screen_small.txt" \
  pull-evidence screen_small
ev="$(evidence screen_small.txt)"
[ -f "$ev" ] || die "pull_evidence_ok: no evidence file"
head -1 "$ev" | grep -qE '^# spike stage=screen_small captured_utc=[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9:]{8}Z target=R5CT10XNKQN head=[0-9a-f]{10}$' \
  || die "pull_evidence_ok: bad header ($(head -1 "$ev"))"
[ "$(grep -c '^VAE_SPIKE_' "$ev")" = "$kept_n" ] || die "pull_evidence_ok: expected $kept_n evidence lines"
! grep -q 'free text' "$ev" || die "pull_evidence_ok: free text was written"
assert_calls pull_evidence_ok "-s R5CT10XNKQN shell run-as $PKG cat files/evidence/screen_small.txt"

# pull_evidence_leak: a grammar-valid line carrying a key-shaped value is a leak: FAIL leak_scan_failed and NO evidence file.
{ grep -vE '^(#|$)' "$GOLDEN"; echo "VAE_SPIKE_TRIAL env=small stage=confirm_small item=en001 model=$PLANT"; } >"$WORK/leak.in"
FAKE_EVIDENCE_FILE="$WORK/leak.in" MUTATES=1 run_scenario pull_evidence_leak 1 "LEAK SCAN FAIL" "FAIL sub=pull-evidence reason=leak_scan_failed" pull-evidence confirm_small
[ ! -e "$(evidence confirm_small.txt)" ] || die "pull_evidence_leak: an evidence file was written"
# An sb line that names a tool is the same kind of leak (the SB tool names are private).
{ grep -vE '^(#|$)' "$GOLDEN"; echo "VAE_SPIKE_TRIAL env=sb stage=confirm_sb item=ng001 tool=zz_marker_tool"; } >"$WORK/leak2.in"
FAKE_EVIDENCE_FILE="$WORK/leak2.in" MUTATES=1 run_scenario pull_evidence_sb_tool_name 1 "LEAK SCAN FAIL" "FAIL sub=pull-evidence reason=leak_scan_failed" pull-evidence confirm_sb
[ ! -e "$(evidence confirm_sb.txt)" ] || die "pull_evidence_sb_tool_name: an evidence file was written"
# No evidence file on the device: nothing is written either.
MUTATES=1 run_scenario pull_evidence_missing 2 "-" "ERROR sub=pull-evidence reason=evidence_missing" pull-evidence init
[ ! -e "$(evidence init.txt)" ] || die "pull_evidence_missing: an evidence file was written"

# ---- Task 2: window-start, time-box, build-install, models, private inputs, run, meminfo, cooldown, cleanup -----------------
echo "SPIKE DEVICE GUARD OK scenarios=$SCENARIOS"
