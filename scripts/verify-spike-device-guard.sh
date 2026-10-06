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
      ANDROID_SERIAL="$ANDROID_SERIAL_VALUE" timeout -k 5 120 "$dir/repo/scripts/run-spike-ondevice.sh" "$@" 8>&- 2>&1)"; code=$?
  else
    out="$(env -u BASH_ENV -u ANDROID_SERIAL SCENARIO="$name" CALLS_LOG="$dir/calls.log" CURL_LOG="$dir/curl.log" STATE_DIR="$dir/state" ADB="$dir/adb" CURL="$dir/curl" \
      XDG_CACHE_HOME="$dir/cache" VAE_SPIKE_PHASE_DIR="$phase" VAE_SPIKE_MODEL_DIR="$models" VAE_SPIKE_PRIVATE_DIR="$dir/private" \
      timeout -k 5 120 "$dir/repo/scripts/run-spike-ondevice.sh" "$@" 8>&- 2>&1)"; code=$?
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

# window-start: the first evidence ENV line (thresholds digest prefix, code head, time-box) and the window stamp.
run_scenario window_start_ok 0 "-" "OK sub=window-start window_start=" window-start
envf="$(evidence env.txt)"
[ -f "$envf" ] || die "window_start_ok: no env.txt"
grep -qxE 'VAE_SPIKE_ENV thresholds_sha=[0-9a-f]{8} head=[0-9a-f]{10} window_start=[0-9]+ timebox_s=14400 target=R5CT10XNKQN device_model=SM-S908U sdk=36' "$envf" \
  || die "window_start_ok: bad ENV line ($(cat "$envf"))"
[ "$(grep -c '' "$envf")" = 1 ] || die "window_start_ok: env.txt must hold exactly the first ENV line"
want8="$(sha256sum "$LAST_DIR/repo/$PHASE_REL/13-THRESHOLDS.md" | cut -c1-8)"
grep -q "thresholds_sha=$want8 " "$envf" || die "window_start_ok: thresholds_sha is not the prefix of the thresholds file digest"
"$FILTER_SRC" <"$envf" >"$WORK/env.out" 2>/dev/null && cmp -s "$WORK/env.out" "$envf" || die "window_start_ok: the ENV line is not accepted by the filter"
read -r ws wb <"$LAST_DIR/cache/vae-spike/window.ts" || die "window_start_ok: no window stamp"
[ "$wb" = 14400 ] || die "window_start_ok: the stamp does not hold the granted time-box"
# one window per grant: a second window-start is refused before any adb call.
STAMP=fresh CALLS_EMPTY=1 run_scenario window_start_twice 2 "one window per grant" "ERROR sub=window-start reason=window_already_started" window-start
# the recorded time-box is the granted value (a shorter window), and an out-of-range or non-numeric one is refused.
TIMEBOX=3600 run_scenario window_start_short_box 0 "-" "OK sub=window-start window_start=" window-start
grep -q ' timebox_s=3600 ' "$(evidence env.txt)" || die "window_start_short_box: timebox_s=3600 not recorded"
TIMEBOX=14401 CALLS_EMPTY=1 run_scenario window_start_box_too_long 2 "-" "ERROR sub=window-start reason=bad_timebox" window-start
TIMEBOX=4h CALLS_EMPTY=1 run_scenario window_start_box_not_numeric 2 "-" "ERROR sub=window-start reason=bad_timebox" window-start
THR_DIRTY=1 CALLS_EMPTY=1 run_scenario window_start_thresholds_uncommitted 2 "commit it before the window starts" "ERROR sub=window-start reason=thresholds_uncommitted" window-start
[ ! -e "$LAST_DIR/cache/vae-spike/window.ts" ] || die "window_start_thresholds_uncommitted: a stamp was written"

# D-07 time-box: an expired (or never started) window refuses push-model, push-private and run before any adb call...
STAMP=expired CALLS_EMPTY=1 run_scenario timebox_expired_run 3 "TIME-BOX EXPIRED" "INFRA sub=run reason=timebox_expired" run init
STAMP=none CALLS_EMPTY=1 run_scenario timebox_no_stamp_run 3 "run window-start first" "INFRA sub=run reason=timebox_expired detail=no_window_stamp" run init
STAMP=expired MODEL_FILES="cpu" CALLS_EMPTY=1 run_scenario timebox_expired_push_model 3 "TIME-BOX EXPIRED" "INFRA sub=push-model reason=timebox_expired" push-model e2b_cpu
STAMP=expired PRIVATE=good CALLS_EMPTY=1 run_scenario timebox_expired_push_private 3 "TIME-BOX EXPIRED" "INFRA sub=push-private reason=timebox_expired" push-private
# ...while preflight, pull-evidence, meminfo, cooldown and cleanup stay allowed (an unmeasured metric turns red, it is not an overrun).
STAMP=expired run_scenario timebox_expired_allows_preflight 0 "-" "OK sub=preflight target=R5CT10XNKQN" preflight
FAKE_EVIDENCE_FILE="$WORK/pull.in" STAMP=expired MUTATES=1 run_scenario timebox_expired_allows_pull 0 "-" "OK sub=pull-evidence stage=init kept=$kept_n" pull-evidence init
STAMP=expired PRE_RUNNING=1 run_scenario timebox_expired_allows_meminfo 0 "-" "OK sub=meminfo stage=init total_pss_mb=1536" meminfo init
STAMP=expired run_scenario timebox_expired_allows_cooldown 0 "-" "OK sub=cooldown status=0" cooldown
STAMP=expired PRE_INSTALLED=1 MUTATES=1 run_scenario timebox_expired_allows_cleanup 0 "spike package, external model directory and staging files removed" "OK sub=cleanup" cleanup

# build-install: a dirty tree is refused before Gradle is reached; a clean tree builds with the low-memory recipe and installs.
DIRTY=1 CALLS_EMPTY=1 run_scenario build_install_dirty 2 "the APK must be exactly HEAD" "ERROR sub=build-install reason=dirty_tree" build-install
FAKE_GRADLE_APK=1 MUTATES=1 run_scenario build_install_ok 0 "dirty=0" "OK sub=build-install target=R5CT10XNKQN apk_md5=" build-install
assert_calls build_install_ok "-s R5CT10XNKQN install -r "
assert_calls build_install_ok "-s R5CT10XNKQN shell cmd deviceidle whitelist +$PKG"
[ -e "$LAST_DIR/gradle-reached" ] || die "build_install_ok: the Gradle build was not run"
for frag in "-Dorg.gradle.daemon=false" "-Dorg.gradle.workers.max=2" "-Dorg.gradle.parallel=false" "-Dkotlin.compiler.execution.strategy=in-process" "-Xmx1536m" "--offline" ":spike-ondevice:assembleDebug"; do
  grep -qF -- "$frag" "$LAST_DIR/gradle-reached" || die "build_install_ok: the Gradle call lacks '$frag' ($(cat "$LAST_DIR/gradle-reached"))"
done
MUTATES=1 run_scenario build_install_gradle_fails 2 "Gradle build failed" "ERROR sub=build-install reason=build_failed" build-install
assert_no_calls build_install_gradle_fails " install "

# push-model: digest on the host before the push, readback on the device after, the A8 fallback, storage, prepare-first.
STAMP=fresh MODEL_FILES="cpu gpu" MUTATES=1 run_scenario push_model_ok 0 "-" "OK sub=push-model id=e2b_cpu sha=${CPU_SHA:0:8} bytes=${#CPU_CONTENT} path=external" push-model e2b_cpu
assert_calls push_model_ok "-s R5CT10XNKQN push $LAST_DIR/models/$CPU_FILE $EXT_MODELS/$CPU_FILE"
assert_calls push_model_ok "-s R5CT10XNKQN shell sha256sum $EXT_MODELS/$CPU_FILE"
assert_no_calls push_model_ok "run-as"
grep -qxF "VAE_SPIKE_MODEL id=e2b_cpu sha=${CPU_SHA:0:8} bytes=${#CPU_CONTENT} match=1 path=external" "$(evidence models.txt)" || die "push_model_ok: bad models.txt ($(cat "$(evidence models.txt)"))"
"$FILTER_SRC" <"$(evidence models.txt)" >/dev/null 2>&1 || die "push_model_ok: the MODEL line is rejected by the filter"
STAMP=fresh MODEL_FILES="cpu gpu" MUTATES=1 run_scenario push_model_gpu_ok 0 "-" "OK sub=push-model id=e2b_gpu sha=${GPU_SHA:0:8} bytes=${#GPU_CONTENT} path=external" push-model e2b_gpu
assert_calls push_model_gpu_ok "$EXT_MODELS/$GPU_FILE"
STAMP=fresh MODEL_FILES="cpu" MODEL_BAD=cpu CALLS_EMPTY=1 run_scenario push_model_host_sha_mismatch 1 "model digest mismatch on the host" "FAIL sub=push-model reason=model_sha_mismatch" push-model e2b_cpu
[ ! -e "$(evidence models.txt)" ] || die "push_model_host_sha_mismatch: a models.txt line was written"
STAMP=fresh MODEL_FILES="" CALLS_EMPTY=1 run_scenario push_model_missing_on_host 2 "fetch-model e2b_cpu" "ERROR sub=push-model reason=model_missing_on_host" push-model e2b_cpu
STAMP=fresh MODEL_FILES="cpu" FAKE_READBACK_CORRUPT=1 MUTATES=1 run_scenario push_model_readback_mismatch 1 "-" "FAIL sub=push-model reason=model_readback_mismatch" push-model e2b_cpu
assert_calls push_model_readback_mismatch "-s R5CT10XNKQN shell rm -f $EXT_MODELS/$CPU_FILE"
[ ! -e "$LAST_DIR/state/extmodel" ] || die "push_model_readback_mismatch: the mismatching device file was not removed"
[ ! -e "$(evidence models.txt)" ] || die "push_model_readback_mismatch: a models.txt line was written"
STAMP=fresh MODEL_FILES="cpu" FAKE_REFUSE_DIRECT=1 MUTATES=1 run_scenario push_model_a8_fallback 0 "-" "OK sub=push-model id=e2b_cpu sha=${CPU_SHA:0:8} bytes=${#CPU_CONTENT} path=internal" push-model e2b_cpu
assert_calls push_model_a8_fallback "-s R5CT10XNKQN push $LAST_DIR/models/$CPU_FILE /data/local/tmp/vae-spike-model-"
assert_calls push_model_a8_fallback "run-as $PKG sh -c 'mkdir -p files/models && cat > files/models/$CPU_FILE' < /data/local/tmp/vae-spike-model-"
assert_calls push_model_a8_fallback "-s R5CT10XNKQN shell rm -f /data/local/tmp/vae-spike-model-"
assert_dev_clean push_model_a8_fallback
[ -f "$LAST_DIR/state/appmodel" ] || die "push_model_a8_fallback: the model never reached the app's internal storage"
grep -q 'match=1 path=internal$' "$(evidence models.txt)" || die "push_model_a8_fallback: models.txt does not say path=internal"
STAMP=fresh MODEL_FILES="cpu" FAKE_AVAIL_KB=1000 MUTATES=1 run_scenario push_model_storage 3 "not enough free space on the device" "INFRA sub=push-model reason=storage" push-model e2b_cpu
assert_no_calls push_model_storage " push "
STAMP=fresh MODEL_FILES="cpu" FAKE_NO_MODELS_DIR=1 run_scenario push_model_prepare_first 2 "run prepare first" "ERROR sub=push-model reason=run_prepare_first" push-model e2b_cpu
assert_no_calls push_model_prepare_first " push "
# The model directory may never be inside the repository.
STAMP=fresh MODEL_FILES="cpu" MODEL_DIR_IN_REPO=1 CALLS_EMPTY=1 run_scenario push_model_dir_in_repo 2 "inside the repository" "ERROR sub=push-model reason=model_dir_in_repo" push-model e2b_cpu
# g3_1b: a file Yahir places himself. Refused when the grant says skip, or when it is not placed; digest recorded when pushed.
STAMP=fresh MODEL_FILES="g3" CALLS_EMPTY=1 run_scenario push_model_g3_skipped_by_grant 2 "Gemma 3 1B control row is not pushed" "ERROR sub=push-model reason=gemma3_not_placed" push-model g3_1b
STAMP=fresh G3=skip MODEL_FILES="g3" CALLS_EMPTY=1 run_scenario push_model_g3_skip 2 "-" "ERROR sub=push-model reason=gemma3_not_placed" push-model g3_1b
STAMP=fresh G3=yes MODEL_FILES="" CALLS_EMPTY=1 run_scenario push_model_g3_not_placed 2 "Yahir downloads" "ERROR sub=push-model reason=gemma3_not_placed" push-model g3_1b
g3sha="$(sha_of fake-g3-model)"
STAMP=fresh G3=yes MODEL_FILES="g3" MUTATES=1 run_scenario push_model_g3_placed 0 "-" "OK sub=push-model id=g3_1b sha=${g3sha:0:8} bytes=13 path=external" push-model g3_1b
grep -qxF "VAE_SPIKE_MODEL id=g3_1b sha=${g3sha:0:8} bytes=13 match=1 path=external" "$(evidence models.txt)" || die "push_model_g3_placed: the digest was not recorded"

# push-private: both private files, digest-pinned to each other and to the grant, written only into app-private storage.
STAMP=fresh PRIVATE=good GRANT_PIN=match MUTATES=1 run_scenario push_private_ok 0 "-" "OK sub=push-private private=sb fixture_sha=" push-private
echo "$LAST_OUT" | tail -1 | grep -qE 'OK sub=push-private private=sb fixture_sha=[0-9a-f]{8} gold_sha=[0-9a-f]{8} target=' || die "push_private_ok: digests are not 8-hex prefixes only"
assert_calls push_private_ok "-s R5CT10XNKQN push $LAST_DIR/private/sb-fixture.json /data/local/tmp/vae-spike-private-"
assert_calls push_private_ok "run-as $PKG sh -c 'mkdir -p files/private && cat > files/private/sb-fixture.json'"
assert_calls push_private_ok "run-as $PKG sh -c 'mkdir -p files/private && cat > files/private/sb-gold.json'"
assert_calls push_private_ok "-s R5CT10XNKQN shell rm -f /data/local/tmp/vae-spike-private-"
assert_dev_clean push_private_ok
[ -f "$LAST_DIR/state/priv-sb-fixture.json" ] && [ -f "$LAST_DIR/state/priv-sb-gold.json" ] || die "push_private_ok: the files never reached app-private storage"
grep -qxE 'VAE_SPIKE_ENV private=sb fixture_sha=[0-9a-f]{8} gold_sha=[0-9a-f]{8} match=1' "$(evidence env.txt)" || die "push_private_ok: bad ENV line"
"$FILTER_SRC" <"$(evidence env.txt)" >/dev/null 2>&1 || die "push_private_ok: the private ENV line is rejected by the filter"
STAMP=fresh PRIVATE=mismatch CALLS_EMPTY=1 run_scenario push_private_fixture_mismatch 1 "not pinned to this fixture" "FAIL sub=push-private reason=fixture_mismatch" push-private
STAMP=fresh PRIVATE=good GRANT_PIN=bad CALLS_EMPTY=1 run_scenario push_private_grant_pin_mismatch 1 "does not match the digest prefix" "FAIL sub=push-private reason=fixture_pin_mismatch" push-private
STAMP=fresh PRIVATE="" CALLS_EMPTY=1 run_scenario push_private_missing_on_host 2 "-" "ERROR sub=push-private reason=private_missing_on_host" push-private
STAMP=fresh PRIVATE=good SB_LABELS=skip CALLS_EMPTY=1 run_scenario push_private_labels_skipped 2 "-" "ERROR sub=push-private reason=sb_labels_skipped" push-private
STAMP=fresh PRIVATE=good SB_LABELS=whatever CALLS_EMPTY=1 run_scenario push_private_labels_invalid 2 "-" "ERROR sub=push-private reason=sb_labels_invalid" push-private
STAMP=fresh PRIVATE=good SB_LABELS="$WORK/push_private_labels_path/private" MUTATES=1 run_scenario push_private_labels_path 0 "-" "OK sub=push-private private=sb" push-private

# run <stage>: process-cold start, the stage line, a process that disappears, and the time-box expiring mid-stage.
STAMP=fresh FAKE_STAGE_DONE=1 MUTATES=1 run_scenario run_ok 0 "-" "OK sub=run stage=init result=done" run init
assert_calls run_ok "-s R5CT10XNKQN shell am force-stop $PKG"
assert_calls run_ok "-s R5CT10XNKQN shell am start -n $PKG/.SpikeActivity --es stage init"
fs="$(grep -n 'am force-stop' "$LAST_DIR/calls.log" | head -1 | cut -d: -f1)"
st="$(grep -n 'am start' "$LAST_DIR/calls.log" | head -1 | cut -d: -f1)"
[ -n "$fs" ] && [ -n "$st" ] && [ "$fs" -lt "$st" ] || die "run_ok: the process was not force-stopped before the start"
STAMP=fresh FAKE_PROC_DIES=1 MUTATES=1 run_scenario run_process_gone 1 "-" "FAIL sub=run reason=process_gone stage=init" run init
[ "$(cat "$(evidence init.host.txt)")" = "VAE_SPIKE_STAGE stage=init result=process_gone source=host" ] || die "run_process_gone: bad host STAGE line"
"$FILTER_SRC" <"$(evidence init.host.txt)" >/dev/null 2>&1 || die "run_process_gone: the host STAGE line is rejected by the filter"
STAMP=short MUTATES=1 run_scenario run_timebox_inflight 3 "-" "INFRA sub=run reason=timebox_expired stage=init" run init
[ "$(cat "$(evidence init.host.txt)")" = "VAE_SPIKE_STAGE stage=init result=timeout source=host" ] || die "run_timebox_inflight: bad host STAGE line"
[ "$(grep -c 'am force-stop' "$LAST_DIR/calls.log")" -ge 2 ] || die "run_timebox_inflight: the app was not force-stopped at expiry"

# meminfo: a closed-grammar MEM line while the process runs, and a FAIL when it does not.
PRE_RUNNING=1 run_scenario meminfo_ok 0 "-" "OK sub=meminfo stage=confirm_small total_pss_mb=1536" meminfo confirm_small
[ "$(cat "$(evidence meminfo.host.txt)")" = "VAE_SPIKE_MEM source=dumpsys stage=confirm_small total_pss_mb=1536" ] || die "meminfo_ok: bad MEM line"
"$FILTER_SRC" <"$(evidence meminfo.host.txt)" >/dev/null 2>&1 || die "meminfo_ok: the MEM line is rejected by the filter"
run_scenario meminfo_not_running 1 "-" "FAIL sub=meminfo reason=not_running" meminfo confirm_small
[ ! -e "$(evidence meminfo.host.txt)" ] || die "meminfo_not_running: a MEM line was written"

# cooldown: waits while hot, and gives up at the cap.
FAKE_THERMAL_HOT_CALLS=1 run_scenario cooldown_ok 0 "-" "OK sub=cooldown status=0 waited_s=" cooldown
FAKE_THERMAL=3 COOL_CAP=2 run_scenario cooldown_not_cooling 3 "still warm" "INFRA sub=cooldown reason=not_cooling status=3" cooldown

# cleanup: force-stop, private data, whitelist, uninstall, and the three proofs; a leftover of any kind is a FAIL.
STAMP=fresh PRE_INSTALLED=1 PRE_EXT=1 PRE_TMP=1 MUTATES=1 run_scenario cleanup_ok 0 "spike package, external model directory and staging files removed" "OK sub=cleanup" cleanup
assert_calls cleanup_ok "-s R5CT10XNKQN shell am force-stop $PKG"
assert_calls cleanup_ok "run-as $PKG rm -rf files/private files/evidence files/models"
assert_calls cleanup_ok "-s R5CT10XNKQN shell cmd deviceidle whitelist -$PKG"
assert_calls cleanup_ok "-s R5CT10XNKQN uninstall $PKG"
assert_calls cleanup_ok "-s R5CT10XNKQN shell rm -f /data/local/tmp/vae-spike-*"
assert_calls cleanup_ok "-s R5CT10XNKQN shell pm list packages"
assert_calls cleanup_ok "-s R5CT10XNKQN shell ls /sdcard/Android/data/$PKG"
assert_calls cleanup_ok "-s R5CT10XNKQN shell ls -d /data/local/tmp/vae-spike-*"
[ ! -e "$LAST_DIR/state/installed" ] || die "cleanup_ok: the package is still installed in the fake"
[ ! -e "$LAST_DIR/state/extmodel" ] || die "cleanup_ok: the external model is still there"
assert_dev_clean cleanup_ok
[ ! -e "$LAST_DIR/cache/vae-spike/window.ts" ] || die "cleanup_ok: the window stamp was not removed"
STAMP=fresh PRE_INSTALLED=1 FAKE_UNINSTALL_FAILS=1 MUTATES=1 run_scenario cleanup_uninstall_failed 1 "could not prove" "FAIL sub=cleanup reason=uninstall_failed" cleanup
STAMP=fresh PRE_EXT=1 FAKE_EXT_RESIDUE=1 MUTATES=1 run_scenario cleanup_external_residue 1 "residue is left" "FAIL sub=cleanup reason=residue_present" cleanup
STAMP=fresh PRE_TMP=1 FAKE_TMP_RESIDUE=1 MUTATES=1 run_scenario cleanup_staging_residue 1 "residue is left" "FAIL sub=cleanup reason=residue_present" cleanup
[ ! -e "$LAST_DIR/cache/vae-spike/window.ts" ] || die "cleanup_staging_residue: the window stamp survived a cleanup"

# ---- Task 3: fetch-model (host only: no grant, no lock, no adb) ------------------------------------------------------------
PIN_REV="b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1"
# The fetch needs no grant and no lock: it works with a pending grant while this guard holds the lock.
flock -n 8 || die "fetch_model_ok: cannot take the lock"
GRANT=pending CALLS_EMPTY=1 run_scenario fetch_model_ok 0 "-" "OK sub=fetch-model id=e2b_cpu sha=${CPU_SHA:0:8} bytes=${#CPU_CONTENT} cached=no" fetch-model e2b_cpu
flock -u 8
[ "$(sha256sum "$LAST_DIR/models/$CPU_FILE" | cut -d' ' -f1)" = "$CPU_SHA" ] || die "fetch_model_ok: the model file is missing or its digest differs"
[ ! -e "$LAST_DIR/models/$CPU_FILE.part" ] || die "fetch_model_ok: a .part file was left"
grep -qF -- "-L --fail --retry 3" "$LAST_DIR/curl.log" || die "fetch_model_ok: curl was not called with -L --fail --retry 3"
grep -qF "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/$PIN_REV/$CPU_FILE" "$LAST_DIR/curl.log" || die "fetch_model_ok: the URL is not the revision-pinned one"
CALLS_EMPTY=1 run_scenario fetch_model_gpu_ok 0 "-" "OK sub=fetch-model id=e2b_gpu sha=${GPU_SHA:0:8} bytes=${#GPU_CONTENT} cached=no" fetch-model e2b_gpu
grep -qF "/resolve/$PIN_REV/$GPU_FILE" "$LAST_DIR/curl.log" || die "fetch_model_gpu_ok: the URL is not the revision-pinned one"
MODEL_FILES="cpu" CALLS_EMPTY=1 run_scenario fetch_model_cached 0 "-" "OK sub=fetch-model id=e2b_cpu sha=${CPU_SHA:0:8} bytes=${#CPU_CONTENT} cached=yes" fetch-model e2b_cpu
[ ! -s "$LAST_DIR/curl.log" ] || die "fetch_model_cached: curl was called for a cached file"
FAKE_CURL_BAD=1 CALLS_EMPTY=1 run_scenario fetch_model_sha_mismatch 1 "removed" "FAIL sub=fetch-model reason=model_sha_mismatch" fetch-model e2b_cpu
[ -z "$(ls -A "$LAST_DIR/models")" ] || die "fetch_model_sha_mismatch: a file was left in the model directory ($(ls -A "$LAST_DIR/models"))"
FAKE_CURL_RC=22 CALLS_EMPTY=1 run_scenario fetch_model_download_failed 2 "-" "ERROR sub=fetch-model reason=download_failed" fetch-model e2b_cpu
[ -z "$(ls -A "$LAST_DIR/models")" ] || die "fetch_model_download_failed: a file was left in the model directory"
MODEL_DIR_IN_REPO=1 CALLS_EMPTY=1 run_scenario fetch_model_dir_in_repo 2 "inside the repository" "ERROR sub=fetch-model reason=model_dir_in_repo" fetch-model e2b_cpu
[ ! -s "$LAST_DIR/curl.log" ] || die "fetch_model_dir_in_repo: curl was called"
[ -z "$(ls -A "$LAST_DIR/repo/models")" ] || die "fetch_model_dir_in_repo: a file was written inside the repository"
# g3_1b is gated: never downloaded, never a token. It is refused with the human-only instruction.
CALLS_EMPTY=1 run_scenario fetch_model_g3_gated 2 "never reads a Hugging Face token" "ERROR sub=fetch-model reason=gated_human_download" fetch-model g3_1b
[ ! -s "$LAST_DIR/curl.log" ] || die "fetch_model_g3_gated: curl was called for the gated model"
[ -z "$(ls -A "$LAST_DIR/models")" ] || die "fetch_model_g3_gated: a file appeared in the model directory"

# The runner holds no HF token and no key handling at all.
SCENARIOS=$((SCENARIOS + 1))
! grep -qiE 'HF_TOKEN|HUGGING_?FACE_(HUB_)?TOKEN|Authorization' "$RUNNER_SRC" || die "no_token_handling: the runner mentions a token or an authorization header"
echo "SPIKE DEVICE GUARD OK scenarios=$SCENARIOS"
