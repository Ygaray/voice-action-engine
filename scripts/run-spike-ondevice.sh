#!/usr/bin/env bash
# Guarded host runner for the Phase 13 on-device model spike (D-03). The ONLY sanctioned host path to the TESTER for the spike.
#   TESTER ONLY. It targets the Gate-1 rig yahirs-s22-ultra-2 (USB serial R5CT10XNKQN first, the wireless
#   100.118.21.106:1496 only as a fallback, and only after ro.serialno proves it is the same handset). It NEVER
#   substitutes another device, never touches the personal phone (100.126.94.47), and has no option, argument or
#   environment variable that changes the target (ADB and CURL may name a binary only). Every adb call carries
#   -s <target>, except the single `adb connect` used to arm the wireless fallback. The guard is copied from
#   scripts/run-sample-gate1.sh and is not weakened; that runner and :sample are not touched by the spike.
#   Window grant: every device subcommand (all but fetch-model) refuses, before the lock and before any adb call, unless
#   the grant file holds the exact line `grant: open`. Only the orchestrator relay may open it (plan 13-08).
#   Time-box (D-07): window-start stamps the window; after timebox_s the runner refuses push-model, push-private and run
#   (INFRA reason=timebox_expired), while preflight, pull-evidence, meminfo, cooldown and cleanup stay allowed.
#   One device tester at a time: every device subcommand takes a non-blocking flock on the SAME lock file as
#   scripts/run-keystore-instrumented.sh and scripts/run-sample-gate1.sh.
#   Nothing here reads an API key or a Hugging Face token. The gated Gemma 3 weights are a human-only download.
#
# Usage: scripts/run-spike-ondevice.sh <subcommand> [arg]
#   fetch-model <id>        HOST ONLY (no grant, no lock, no adb): download a pinned model into VAE_SPIKE_MODEL_DIR, sha256-checked
#   preflight               guard, then print the device facts as one VAE_SPIKE_PREFLIGHT line (read-only shell calls)
#   window-start            stamp the time-boxed window and write the first evidence ENV line
#   build-install           clean-tree check, ./gradlew :spike-ondevice:assembleDebug --offline, install, whitelist
#   push-model <id>         push a pinned model file and compare its sha256 on the device
#   push-private            push the private SB fixture and gold labels into app-private storage only
#   run <stage>             force-stop, start the stage, and wait for its VAE_SPIKE_STAGE line (bounded by the time-box)
#   cooldown                wait until the device thermal status is light or better (15 min cap)
#   meminfo <stage>         read-only cross-check of the app's total PSS from dumpsys meminfo
#   pull-evidence <stage>   pull the app's evidence file through scripts/spike-evidence-filter.sh into evidence/
#   cleanup                 force-stop, remove private data, undo the whitelist, uninstall, and prove nothing is left
#   model ids: e2b_cpu e2b_gpu g3_1b (g3_1b is a file Yahir places himself; it is never downloaded here)
# Exit codes:
#   0 OK   1 FAIL   2 ERROR (usage, build, install, not granted)
#   3 INFRA (TESTER offline or busy, time-box expired, storage, not cooling)   4 INFRA (refused: identity mismatch or a non-TESTER device)
# The LAST line is always: SPIKE_ONDEVICE: <OK|FAIL|INFRA|ERROR> sub=<subcommand> <key=value ...>
set -uo pipefail

TESTER_USB="R5CT10XNKQN"
TESTER_WIFI="100.118.21.106:1496"
PERSONAL_IP="100.126.94.47"
EXPECTED_MODEL="SM-S908U"
MIN_SDK=35
PKG="io.github.ygaray.voiceactionengine.spike"
LOCK_FILE="${XDG_RUNTIME_DIR:-/tmp}/vae-keystore-tester.lock"
ADB="${ADB:-adb}"
CURL="${CURL:-curl}"

# The phase whose grant file and evidence this run uses. A path inside the repository only (VAE_SPIKE_PHASE_DIR retargets it).
PHASE_DIR="${VAE_SPIKE_PHASE_DIR:-.planning/phases/13-on-device-model-spike}"
GRANT_FILE="$PHASE_DIR/13-WINDOW-GRANT.md"
THRESHOLDS_FILE="$PHASE_DIR/13-THRESHOLDS.md"
EVIDENCE_DIR="$PHASE_DIR/evidence"
STAMP_FILE="${XDG_CACHE_HOME:-$HOME/.cache}/vae-spike/window.ts"
MODEL_DIR="${VAE_SPIKE_MODEL_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/vae-spike/models}"
PRIVATE_DIR_DEFAULT="${VAE_SPIKE_PRIVATE_DIR:-$HOME/.local/share/vae-spike}"
LOG_DIR="spike-ondevice/build/device-run"
APK_DIR="spike-ondevice/build/outputs/apk/debug"
# Everything the APK is built from: the build refuses unless these paths are clean, so the APK is exactly HEAD.
BUILD_PATHS="spike-ondevice core providers keystore gradle settings.gradle.kts build.gradle.kts gradle.properties scripts"
DEV_EXT_MODELS="/sdcard/Android/data/$PKG/files/models"
DEV_EXT_ROOT="/sdcard/Android/data/$PKG"
GIB=1073741824
TIMEBOX_MAX_S=14400
POLL_S=15
COOL_POLL_S=30
COOL_CAP_S=900

# Model pins (D-01), RESEARCH "Standard Stack". Revision-pinned URL, byte size and sha256.
HF_URL_BASE="https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1"
E2B_CPU_FILE="gemma-4-E2B-it.litertlm"
E2B_CPU_BYTES=2588147712
E2B_CPU_SHA="181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
E2B_GPU_FILE="gemma-4-E2B-it-gpu.litertlm"
E2B_GPU_BYTES=2008432640
E2B_GPU_SHA="a53a59001894c58e6bdb5b9b227709f91a2e3e556baa7d85acf9c55402ba5cf5"
# Gated (Gemma Terms of Use): Yahir downloads and places this himself. Its digest is recorded at push time, not compared.
G3_FILE="Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm"

SUBCOMMANDS="fetch-model preflight window-start build-install push-model push-private run cooldown meminfo pull-evidence cleanup"
MODELS="e2b_cpu e2b_gpu g3_1b"
# Exactly the Records.Stage wire names of the spike module (13-07 adds the parity test).
STAGES="prepare preflight init prefill kv_reuse rf_matrix screen_small confirm_small screen_sb confirm_sb sustained_small sustained_sb exit_reasons"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

SUB="none"
ARG=""
TARGET=""
MODEL=""
SDK=""
STAGINGS=()
TMP_DIR=""
PART_FILE=""
WIN_START=""
WIN_BOX=""
M_FILE=""
M_SHA=""
M_BYTES=""
HOST_SHA=""
HOST_SIZE=""
PRIVATE_FIXTURE=""
PRIVATE_GOLD=""
PUSHED_SHA=""

# Every adb invocation goes through adb_t: a timeout, and fd 9 (the lock) closed so a daemonized adb server never
# inherits and holds the lock. The caller supplies -s <target> (or `connect <wifi>`).
adb_t() { local secs="$1"; shift; timeout "$secs" "$ADB" "$@" 9>&-; }
adbt() { adb_t 30 -s "$TARGET" "$@"; }
strip_cr() { tr -d '\r'; }

# Device-side staging files are ALWAYS removed: on every normal finish, and on a signal or abnormal exit.
rm_stagings() {
  local p
  if [ -n "$TARGET" ]; then
    for p in "${STAGINGS[@]}"; do adb_t 15 -s "$TARGET" shell rm -f "$p" >/dev/null 2>&1; done
  fi
  STAGINGS=()
  return 0
}
# pull-evidence works in a private temp dir (the raw device file never leaves it); a half-downloaded model is removed too.
rm_tmp() { [ -n "$TMP_DIR" ] && rm -rf "$TMP_DIR"; TMP_DIR=""; return 0; }
rm_part() { [ -n "$PART_FILE" ] && rm -f "$PART_FILE"; PART_FILE=""; return 0; }
cleanup_local() { rm_stagings; rm_tmp; rm_part; }
trap 'cleanup_local; exit 130' INT TERM HUP
trap 'cleanup_local' EXIT

# finish <code> <OK|FAIL|INFRA|ERROR> <details>: remove any staging or temp file, print the one final line and exit.
finish() {
  local code="$1" outcome="$2" details="$3"
  cleanup_local
  echo "SPIKE_ONDEVICE: $outcome sub=$SUB $details"
  exit "$code"
}

in_list() { local needle="$1" item; shift; for item in $1; do [ "$item" = "$needle" ] && return 0; done; return 1; }

usage() {
  echo "usage: scripts/run-spike-ondevice.sh <subcommand> [arg]"
  echo "  subcommands: $SUBCOMMANDS"
  echo "  run | meminfo | pull-evidence <stage>   (stage: $STAGES)"
  echo "  fetch-model | push-model <id>           (id: $MODELS)"
  echo "  (the target is fixed to the TESTER; there is no option to change it)"
  finish 2 ERROR "reason=usage"
}

# a. Arguments are fixed before any adb call: one subcommand from the list, and only the argument that subcommand needs.
[ $# -ge 1 ] || usage
in_list "$1" "$SUBCOMMANDS" || usage
SUB="$1"
case "$SUB" in
  run | meminfo | pull-evidence)
    [ $# -eq 2 ] || usage
    in_list "$2" "$STAGES" || usage
    ARG="$2"
    ;;
  push-model | fetch-model)
    [ $# -eq 2 ] || usage
    in_list "$2" "$MODELS" || usage
    ARG="$2"
    ;;
  *)
    [ $# -eq 1 ] || usage
    ;;
esac

# The phase directory is a path inside the repository: never absolute, never containing "..".
case "$PHASE_DIR" in
  /* | *..*) echo "VAE_SPIKE_PHASE_DIR must be a relative path inside the repository"; finish 2 ERROR "reason=bad_phase_dir" ;;
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
  # Read the whole listing first: `grep -q` exits early and adb would die of SIGPIPE (141) under pipefail.
  local listing
  listing="$(adbt shell pm list packages 2>/dev/null | strip_cr || true)"
  grep -qxF "package:$PKG" <<<"$listing"
}

# The first numeric line of a command's output, or "unknown" (every evidence value is a closed-alphabet token).
num_or_unknown() { local v; v="$(grep -E -m1 '^[0-9]+$' || true)"; echo "${v:-unknown}"; }

# ---- grant, time-box and model helpers (host side) ---------------------------------------------------------------------

# grant_get <key>: the value of the first `key: value` line of the grant file (empty when absent).
grant_get() { sed -nE "s/^$1: *(.*)$/\1/p" "$GRANT_FILE" 2>/dev/null | head -n 1 | tr -d '\r'; }

# The window stamp is "<epoch> <timebox_s>". stamp_read sets WIN_START and WIN_BOX; it fails when the stamp is missing or damaged.
stamp_read() {
  [ -f "$STAMP_FILE" ] || return 1
  read -r WIN_START WIN_BOX <"$STAMP_FILE" || return 1
  case "${WIN_START}${WIN_BOX}" in '' | *[!0-9]*) return 1 ;; esac
  [ -n "$WIN_START" ] && [ -n "$WIN_BOX" ]
}

# D-07: push-model, push-private and run refuse once the time-box has expired (or was never started).
timebox_ok() {
  local now
  if ! stamp_read; then
    echo "no valid window stamp: run window-start first"
    finish 3 INFRA "reason=timebox_expired detail=no_window_stamp"
  fi
  now="$(date +%s)"
  if [ $((now - WIN_START)) -ge "$WIN_BOX" ]; then
    echo "TIME-BOX EXPIRED - the window ran ${WIN_BOX}s; only preflight, pull-evidence, meminfo, cooldown and cleanup are allowed now"
    finish 3 INFRA "reason=timebox_expired"
  fi
}

model_vars() {
  case "$1" in
    e2b_cpu) M_FILE="$E2B_CPU_FILE"; M_SHA="$E2B_CPU_SHA"; M_BYTES="$E2B_CPU_BYTES" ;;
    e2b_gpu) M_FILE="$E2B_GPU_FILE"; M_SHA="$E2B_GPU_SHA"; M_BYTES="$E2B_GPU_BYTES" ;;
    g3_1b) M_FILE="$G3_FILE"; M_SHA=""; M_BYTES="" ;;
  esac
}

# No model file may live inside the repository: the model directory must resolve outside it.
model_dir_check() {
  local m r
  m="$(realpath -m -- "$MODEL_DIR")"
  r="$(realpath -- "$ROOT")"
  case "$m/" in
    "$r/"*)
      echo "the model directory $MODEL_DIR is inside the repository; set VAE_SPIKE_MODEL_DIR to a path outside it"
      finish 2 ERROR "reason=model_dir_in_repo"
      ;;
  esac
}

host_precheck() {
  # D-03 window grant: nothing touches the lock or adb unless the exact line is present.
  if ! grep -qx 'grant: open' "$GRANT_FILE" 2>/dev/null; then
    echo "the TESTER window is not granted: $GRANT_FILE does not hold the exact line 'grant: open' - not touching the device"
    finish 2 ERROR "reason=window_not_granted"
  fi
  local box listing sb_labels dir fixture gold fsha gsha pin gemma
  case "$SUB" in
    window-start)
      if [ -e "$STAMP_FILE" ]; then
        echo "a window was already started for this grant ($STAMP_FILE exists): one window per grant"
        finish 2 ERROR "reason=window_already_started"
      fi
      box="$(grant_get timebox_s)"
      case "$box" in '' | *[!0-9]*) finish 2 ERROR "reason=bad_timebox" ;; esac
      if [ "$box" -lt 1 ] || [ "$box" -gt "$TIMEBOX_MAX_S" ]; then finish 2 ERROR "reason=bad_timebox"; fi
      if [ ! -f "$THRESHOLDS_FILE" ]; then
        echo "$THRESHOLDS_FILE is missing: the thresholds must be committed before any device step"
        finish 2 ERROR "reason=thresholds_missing"
      fi
      if ! listing="$(git -C "$ROOT" status --porcelain -- "$THRESHOLDS_FILE" 2>/dev/null)" || [ -n "$listing" ] \
        || ! git -C "$ROOT" ls-files --error-unmatch -- "$THRESHOLDS_FILE" >/dev/null 2>&1; then
        echo "$THRESHOLDS_FILE is not committed unchanged: commit it before the window starts"
        finish 2 ERROR "reason=thresholds_uncommitted"
      fi
      ;;
    build-install)
      if ! listing="$(git -C "$ROOT" status --porcelain -- $BUILD_PATHS 2>/dev/null)"; then
        finish 2 ERROR "reason=git_status_failed"
      fi
      if [ -n "$listing" ]; then
        echo "the working tree is dirty under the build paths; the APK must be exactly HEAD:"
        printf '%s\n' "$listing" | head -n 10
        finish 2 ERROR "reason=dirty_tree"
      fi
      ;;
    run)
      timebox_ok
      ;;
    push-model)
      timebox_ok
      model_dir_check
      model_vars "$ARG"
      if [ "$ARG" = g3_1b ]; then
        gemma="$(grant_get gemma3_1b)"
        case "$gemma" in
          skip*)
            echo "the grant records gemma3_1b: $gemma - the Gemma 3 1B control row is not pushed"
            finish 2 ERROR "reason=gemma3_not_placed"
            ;;
        esac
        if [ ! -f "$MODEL_DIR/$M_FILE" ]; then
          echo "Gemma 3 1B is not placed: Yahir downloads $M_FILE himself into $MODEL_DIR"
          finish 2 ERROR "reason=gemma3_not_placed"
        fi
      elif [ ! -f "$MODEL_DIR/$M_FILE" ]; then
        echo "model file missing on the host: scripts/run-spike-ondevice.sh fetch-model $ARG"
        finish 2 ERROR "reason=model_missing_on_host"
      fi
      HOST_SIZE="$(stat -c %s "$MODEL_DIR/$M_FILE")"
      HOST_SHA="$(sha256sum "$MODEL_DIR/$M_FILE" | cut -d' ' -f1)"
      if [ -n "$M_SHA" ] && { [ "$HOST_SHA" != "$M_SHA" ] || [ "$HOST_SIZE" != "$M_BYTES" ]; }; then
        echo "model digest mismatch on the host (${HOST_SHA:0:8} vs pinned ${M_SHA:0:8}): not pushing"
        finish 1 FAIL "reason=model_sha_mismatch host=${HOST_SHA:0:8} expected=${M_SHA:0:8}"
      fi
      ;;
    push-private)
      timebox_ok
      sb_labels="$(grant_get sb_labels)"
      case "$sb_labels" in
        default) dir="$PRIVATE_DIR_DEFAULT" ;;
        /*) dir="$sb_labels" ;;
        skip*) finish 2 ERROR "reason=sb_labels_skipped" ;;
        *) finish 2 ERROR "reason=sb_labels_invalid" ;;
      esac
      fixture="$dir/sb-fixture.json"
      gold="$dir/sb-gold.json"
      if [ ! -f "$fixture" ] || [ ! -f "$gold" ]; then
        echo "the private SB inputs are missing on the host (sb-fixture.json, sb-gold.json); not pushing"
        finish 2 ERROR "reason=private_missing_on_host"
      fi
      fsha="$(sha256sum "$fixture" | cut -d' ' -f1)"
      gsha="$(jq -r '.fixture_sha256 // empty' "$gold" 2>/dev/null || true)"
      if [ -z "$gsha" ] || [ "$gsha" != "$fsha" ]; then
        echo "the gold labels are not pinned to this fixture (gold ${gsha:0:8}, fixture ${fsha:0:8}): not pushing"
        finish 1 FAIL "reason=fixture_mismatch gold=${gsha:0:8} fixture=${fsha:0:8}"
      fi
      pin="$(grant_get sb_fixture_sha8)"
      if [ -n "$pin" ] && [ "${fsha:0:${#pin}}" != "$pin" ]; then
        echo "the fixture does not match the digest prefix the grant records (${fsha:0:8} vs $pin): not pushing"
        finish 1 FAIL "reason=fixture_pin_mismatch fixture=${fsha:0:8} grant=$pin"
      fi
      PRIVATE_FIXTURE="$fixture"
      PRIVATE_GOLD="$gold"
      ;;
  esac
}

# ---- host-only: fetch-model (no grant, no lock, no adb) ----------------------------------------------------------------

do_fetch_model() {
  if [ "$ARG" = g3_1b ]; then
    echo "Gemma 3 1B is gated (Gemma Terms of Use): accept the terms on Hugging Face and place $G3_FILE in the model directory yourself."
    echo "This runner never downloads it and never reads a Hugging Face token."
    finish 2 ERROR "reason=gated_human_download"
  fi
  model_vars "$ARG"
  model_dir_check
  local final part sha size avail need
  final="$MODEL_DIR/$M_FILE"
  part="$final.part"
  mkdir -p "$MODEL_DIR" || finish 2 ERROR "reason=model_dir_unwritable"
  if [ -f "$final" ] && [ "$(stat -c %s "$final")" = "$M_BYTES" ]; then
    sha="$(sha256sum "$final" | cut -d' ' -f1)"
    if [ "$sha" = "$M_SHA" ]; then
      finish 0 OK "id=$ARG sha=${sha:0:8} bytes=$M_BYTES cached=yes"
    fi
  fi
  avail="$(df --output=avail -B1 "$MODEL_DIR" 2>/dev/null | tail -n 1 | tr -d ' ')"
  need=$((M_BYTES + 2 * GIB))
  case "$avail" in '' | *[!0-9]*) avail=0 ;; esac
  if [ "$avail" -lt "$need" ]; then
    echo "not enough free space on the host for $M_FILE (need $need bytes, have $avail)"
    finish 3 INFRA "reason=storage"
  fi
  rm -f "$part"
  PART_FILE="$part"
  if ! timeout 7200 "$CURL" -L --fail --retry 3 -sS -o "$part" "$HF_URL_BASE/$M_FILE"; then
    finish 2 ERROR "reason=download_failed"
  fi
  size="$(stat -c %s "$part" 2>/dev/null || echo 0)"
  sha="$(sha256sum "$part" | cut -d' ' -f1)"
  if [ "$size" != "$M_BYTES" ] || [ "$sha" != "$M_SHA" ]; then
    rm -f "$part"
    PART_FILE=""
    echo "downloaded file does not match the pin (bytes $size, sha ${sha:0:8}); removed"
    finish 1 FAIL "reason=model_sha_mismatch bytes=$size sha=${sha:0:8} expected=${M_SHA:0:8}"
  fi
  mv -f "$part" "$final" || finish 2 ERROR "reason=model_dir_unwritable"
  PART_FILE=""
  finish 0 OK "id=$ARG sha=${sha:0:8} bytes=$size cached=no"
}

# ---- device subcommands ------------------------------------------------------------------------------------------------

# One closed-grammar line of read-only device facts (the host-side half of the PREFLIGHT evidence).
do_preflight() {
  local installed=no meminfo mem_total mem_avail storage abi page ocl therm batt line
  package_installed && installed=yes
  meminfo="$(adbt shell cat /proc/meminfo 2>/dev/null | strip_cr)"
  mem_total="$(printf '%s\n' "$meminfo" | sed -nE 's/^MemTotal: *([0-9]+) kB.*/\1/p' | num_or_unknown)"
  mem_avail="$(printf '%s\n' "$meminfo" | sed -nE 's/^MemAvailable: *([0-9]+) kB.*/\1/p' | num_or_unknown)"
  storage="$(adbt shell df -k /sdcard 2>/dev/null | strip_cr | tail -n 1 | awk '{print $4}' | num_or_unknown)"
  abi="$(adbt shell getprop ro.product.cpu.abi 2>/dev/null | strip_cr | tr -cd 'A-Za-z0-9_.-')"
  page="$(adbt shell getconf PAGESIZE 2>/dev/null | strip_cr | num_or_unknown)"
  ocl="$(adbt shell "ls /vendor/lib64/libOpenCL.so >/dev/null 2>&1 && echo present || echo absent" 2>/dev/null | strip_cr | tail -n 1)"
  case "$ocl" in present | absent) ;; *) ocl=unknown ;; esac
  therm="$(adbt shell dumpsys thermalservice 2>/dev/null | strip_cr | sed -nE 's/^ *[Tt]hermal [Ss]tatus: *([0-9]+).*/\1/p' | num_or_unknown)"
  batt="$(adbt shell dumpsys battery 2>/dev/null | strip_cr | sed -nE 's/^ *level: *([0-9]+).*/\1/p' | num_or_unknown)"
  line="VAE_SPIKE_PREFLIGHT source=host mem_total_kb=$mem_total mem_avail_kb=$mem_avail storage_free_kb=$storage abi=${abi:-unknown} page_size=$page opencl=$ocl thermal_status=$therm battery=$batt"
  echo "$line"
  mkdir -p "$EVIDENCE_DIR" || finish 2 ERROR "reason=evidence_dir_unwritable"
  echo "$line" >>"$EVIDENCE_DIR/preflight.host.txt"
  finish 0 OK "target=$TARGET model=$MODEL sdk=$SDK installed=$installed"
}

# D-07: stamp the window and write the first evidence ENV line (thresholds digest, code head, time-box).
do_window_start() {
  local now sha head box
  box="$(grant_get timebox_s)"
  now="$(date +%s)"
  sha="$(sha256sum "$THRESHOLDS_FILE" | cut -c1-8)"
  head="$(git -C "$ROOT" rev-parse --short=10 HEAD 2>/dev/null || echo unknown)"
  mkdir -p "$(dirname "$STAMP_FILE")" "$EVIDENCE_DIR" || finish 2 ERROR "reason=stamp_write_failed"
  printf '%s %s\n' "$now" "$box" >"$STAMP_FILE" || finish 2 ERROR "reason=stamp_write_failed"
  if ! printf 'VAE_SPIKE_ENV thresholds_sha=%s head=%s window_start=%s timebox_s=%s target=%s device_model=%s sdk=%s\n' \
    "$sha" "$head" "$now" "$box" "$TARGET" "$MODEL" "$SDK" >>"$EVIDENCE_DIR/env.txt"; then
    rm -f "$STAMP_FILE"
    finish 2 ERROR "reason=evidence_write_failed"
  fi
  finish 0 OK "window_start=$now timebox_s=$box thresholds_sha=$sha target=$TARGET"
}

do_build_install() {
  local apk_count apk md5 head
  mkdir -p "$LOG_DIR"
  if ! GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx1536m" \
    ./gradlew --offline -q -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false :spike-ondevice:assembleDebug >"$LOG_DIR/gradle.log" 2>&1 9>&-; then
    echo "Gradle build failed; the last lines of $LOG_DIR/gradle.log:"
    tail -n 40 "$LOG_DIR/gradle.log"
    finish 2 ERROR "reason=build_failed target=$TARGET"
  fi
  apk_count="$(find "$APK_DIR" -maxdepth 1 -name '*.apk' 2>/dev/null | wc -l | tr -d ' ')"
  [ "$apk_count" = 1 ] || finish 2 ERROR "reason=apk_missing target=$TARGET"
  apk="$(find "$APK_DIR" -maxdepth 1 -name '*.apk')"
  md5="$(md5sum "$apk" | cut -d' ' -f1)"
  head="$(git -C "$ROOT" rev-parse --short=10 HEAD 2>/dev/null || echo unknown)"
  echo "apk_md5=$md5 head=$head dirty=0"

  if ! adb_t 180 -s "$TARGET" install -r "$apk" >/dev/null 2>&1; then
    if [ "$(adb_t 15 -s "$TARGET" get-state 2>/dev/null | strip_cr)" != "device" ]; then
      finish 3 INFRA "reason=tester_offline target=$TARGET"
    fi
    finish 2 ERROR "reason=install_failed target=$TARGET"
  fi
  # Freecess can freeze a backgrounded app mid-run; whitelisting is best effort and is undone by cleanup.
  adbt shell cmd deviceidle whitelist "+$PKG" >/dev/null 2>&1 || true
  package_installed || finish 2 ERROR "reason=install_failed target=$TARGET"
  finish 0 OK "target=$TARGET apk_md5=$md5 head=$head dirty=0"
}

# new_staging <tag>: a fresh device-side staging path, registered so every exit path removes it.
new_staging() {
  local p="/data/local/tmp/vae-spike-$1-$(od -An -N6 -tx1 /dev/urandom | tr -d ' \n')"
  STAGINGS+=("$p")
  echo "$p"
}

# D-01: the host file was digest-checked in host_precheck; here it is pushed and read back on the device.
do_push_model() {
  local host_file="$MODEL_DIR/$M_FILE" avail_kb need_kb present dev_path kind=external dev_sha staging
  avail_kb="$(adbt shell df -k /sdcard 2>/dev/null | strip_cr | tail -n 1 | awk '{print $4}')"
  case "$avail_kb" in '' | *[!0-9]*) finish 3 INFRA "reason=storage detail=unreadable target=$TARGET" ;; esac
  need_kb=$(((HOST_SIZE + GIB) / 1024))
  if [ "$avail_kb" -le "$need_kb" ]; then
    echo "not enough free space on the device for $M_FILE (need over ${need_kb} kB, have ${avail_kb} kB)"
    finish 3 INFRA "reason=storage target=$TARGET"
  fi
  present="$(adbt shell "[ -d $DEV_EXT_MODELS ] && echo yes || echo no" 2>/dev/null | strip_cr | tail -n 1)"
  if [ "$present" != yes ]; then
    echo "the app's external models directory does not exist yet: scripts/run-spike-ondevice.sh run prepare first"
    finish 2 ERROR "reason=run_prepare_first target=$TARGET"
  fi
  dev_path="$DEV_EXT_MODELS/$M_FILE"
  if adb_t 1800 -s "$TARGET" push "$host_file" "$dev_path" >/dev/null 2>&1; then
    dev_sha="$(adb_t 900 -s "$TARGET" shell sha256sum "$dev_path" 2>/dev/null | strip_cr | cut -d' ' -f1)"
  else
    # A8 fallback: the direct push was refused, so stage under /data/local/tmp and copy in as the app (internal files dir).
    kind=internal
    staging="$(new_staging model)"
    if ! adb_t 1800 -s "$TARGET" push "$host_file" "$staging" >/dev/null 2>&1; then
      finish 2 ERROR "reason=push_failed target=$TARGET"
    fi
    if ! adb_t 1800 -s "$TARGET" shell "run-as $PKG sh -c 'mkdir -p files/models && cat > files/models/$M_FILE' < $staging" >/dev/null 2>&1; then
      finish 2 ERROR "reason=run_as_failed target=$TARGET"
    fi
    rm_stagings
    dev_sha="$(adb_t 900 -s "$TARGET" shell "run-as $PKG sha256sum files/models/$M_FILE" 2>/dev/null | strip_cr | cut -d' ' -f1)"
  fi
  if [ "$dev_sha" != "$HOST_SHA" ]; then
    if [ "$kind" = external ]; then
      adbt shell rm -f "$dev_path" >/dev/null 2>&1 || true
    else
      adbt shell "run-as $PKG rm -f files/models/$M_FILE" >/dev/null 2>&1 || true
    fi
    finish 1 FAIL "reason=model_readback_mismatch id=$ARG device=${dev_sha:0:8} expected=${HOST_SHA:0:8}"
  fi
  mkdir -p "$EVIDENCE_DIR" || finish 2 ERROR "reason=evidence_dir_unwritable"
  echo "VAE_SPIKE_MODEL id=$ARG sha=${HOST_SHA:0:8} bytes=$HOST_SIZE match=1 path=$kind" >>"$EVIDENCE_DIR/models.txt"
  finish 0 OK "id=$ARG sha=${HOST_SHA:0:8} bytes=$HOST_SIZE path=$kind target=$TARGET"
}

# push_private_file <host file> <device name>: staging plus run-as into files/private, read back by digest. Never prints content.
push_private_file() {
  local src="$1" name="$2" staging want got
  want="$(sha256sum "$src" | cut -d' ' -f1)"
  staging="$(new_staging private)"
  if ! adbt push "$src" "$staging" >/dev/null 2>&1; then
    finish 2 ERROR "reason=push_failed target=$TARGET"
  fi
  if ! adbt shell "run-as $PKG sh -c 'mkdir -p files/private && cat > files/private/$name' < $staging" >/dev/null 2>&1; then
    finish 2 ERROR "reason=run_as_failed target=$TARGET"
  fi
  rm_stagings
  got="$(adbt shell "run-as $PKG sha256sum files/private/$name" 2>/dev/null | strip_cr | cut -d' ' -f1)"
  if [ "$got" != "$want" ]; then
    adbt shell "run-as $PKG rm -rf files/private" >/dev/null 2>&1 || true
    finish 1 FAIL "reason=private_readback_mismatch device=${got:0:8} expected=${want:0:8}"
  fi
  PUSHED_SHA="$want"
}

do_push_private() {
  local fsha gsha
  push_private_file "$PRIVATE_FIXTURE" sb-fixture.json
  fsha="$PUSHED_SHA"
  push_private_file "$PRIVATE_GOLD" sb-gold.json
  gsha="$PUSHED_SHA"
  mkdir -p "$EVIDENCE_DIR" || finish 2 ERROR "reason=evidence_dir_unwritable"
  echo "VAE_SPIKE_ENV private=sb fixture_sha=${fsha:0:8} gold_sha=${gsha:0:8} match=1" >>"$EVIDENCE_DIR/env.txt"
  finish 0 OK "private=sb fixture_sha=${fsha:0:8} gold_sha=${gsha:0:8} target=$TARGET"
}

# The number of VAE_SPIKE_STAGE result lines the app has written for the stage so far (0 when the file is absent).
stage_count() {
  local n
  n="$(adbt shell "run-as $PKG grep -c '^VAE_SPIKE_STAGE stage=$ARG result=' files/evidence/$ARG.txt" 2>/dev/null | strip_cr | grep -E '^[0-9]+$' | tail -n 1)"
  echo "${n:-0}"
}

# The stage line is on the device: report its result token (D-07: the host never judges it).
finish_stage_done() {
  local line result
  line="$(adbt shell "run-as $PKG grep '^VAE_SPIKE_STAGE stage=$ARG result=' files/evidence/$ARG.txt" 2>/dev/null | strip_cr | tail -n 1)"
  result="$(printf '%s\n' "$line" | sed -nE 's/^VAE_SPIKE_STAGE stage=[a-z0-9_]+ result=([A-Za-z0-9_.:/,-]*).*/\1/p')"
  finish 0 OK "stage=$ARG result=${result:-unknown} target=$TARGET"
}

do_run() {
  local base now deadline pid
  base="$(stage_count)"
  # Every stage starts process-cold.
  adbt shell am force-stop "$PKG" >/dev/null 2>&1 || true
  if ! adbt shell am start -n "$PKG/.SpikeActivity" --es stage "$ARG" >/dev/null 2>&1; then
    finish 2 ERROR "reason=start_failed stage=$ARG target=$TARGET"
  fi
  # The wait is bounded by what is left of the time-box (WIN_START and WIN_BOX were loaded by timebox_ok).
  deadline=$((WIN_START + WIN_BOX))
  mkdir -p "$EVIDENCE_DIR" || finish 2 ERROR "reason=evidence_dir_unwritable"
  while :; do
    sleep "$POLL_S"
    [ "$(stage_count)" -gt "$base" ] && finish_stage_done
    pid="$(adbt shell pidof "$PKG" 2>/dev/null | strip_cr | tr -d ' ')"
    if [ -z "$pid" ]; then
      # The process may have finished normally just after the first check: look once more before calling it a death.
      [ "$(stage_count)" -gt "$base" ] && finish_stage_done
      echo "VAE_SPIKE_STAGE stage=$ARG result=process_gone source=host" >>"$EVIDENCE_DIR/$ARG.host.txt"
      finish 1 FAIL "reason=process_gone stage=$ARG target=$TARGET"
    fi
    now="$(date +%s)"
    if [ "$now" -ge "$deadline" ]; then
      adbt shell am force-stop "$PKG" >/dev/null 2>&1 || true
      echo "VAE_SPIKE_STAGE stage=$ARG result=timeout source=host" >>"$EVIDENCE_DIR/$ARG.host.txt"
      finish 3 INFRA "reason=timebox_expired stage=$ARG target=$TARGET"
    fi
  done
}

thermal_status() {
  adbt shell dumpsys thermalservice 2>/dev/null | strip_cr | sed -nE 's/^ *[Tt]hermal [Ss]tatus: *([0-9]+).*/\1/p' | head -n 1
}

do_cooldown() {
  local start now st waited
  start="$(date +%s)"
  while :; do
    st="$(thermal_status)"
    now="$(date +%s)"
    waited=$((now - start))
    case "$st" in
      '' | *[!0-9]*) ;;
      *) if [ "$st" -le 1 ]; then finish 0 OK "status=$st waited_s=$waited target=$TARGET"; fi ;;
    esac
    if [ "$waited" -ge "$COOL_CAP_S" ]; then
      echo "the device is still warm after ${waited}s (thermal status ${st:-unknown})"
      finish 3 INFRA "reason=not_cooling status=${st:-unknown} waited_s=$waited target=$TARGET"
    fi
    sleep "$COOL_POLL_S"
  done
}

# 13-THRESHOLDS (f): the informational cross-check of in-process totalPss against dumpsys meminfo TOTAL PSS.
do_meminfo() {
  local pid out kb mb
  pid="$(adbt shell pidof "$PKG" 2>/dev/null | strip_cr | tr -d ' ')"
  if [ -z "$pid" ]; then
    finish 1 FAIL "reason=not_running stage=$ARG target=$TARGET"
  fi
  out="$(adb_t 60 -s "$TARGET" shell dumpsys meminfo "$PKG" 2>/dev/null | strip_cr)"
  kb="$(printf '%s\n' "$out" | sed -nE 's/^ *TOTAL PSS: *([0-9]+).*/\1/p' | head -n 1)"
  [ -n "$kb" ] || kb="$(printf '%s\n' "$out" | sed -nE 's/^ *TOTAL +([0-9]+) .*/\1/p' | head -n 1)"
  case "$kb" in '' | *[!0-9]*) finish 2 ERROR "reason=meminfo_unparsed stage=$ARG target=$TARGET" ;; esac
  mb=$((kb / 1024))
  mkdir -p "$EVIDENCE_DIR" || finish 2 ERROR "reason=evidence_dir_unwritable"
  echo "VAE_SPIKE_MEM source=dumpsys stage=$ARG total_pss_mb=$mb" >>"$EVIDENCE_DIR/meminfo.host.txt"
  finish 0 OK "stage=$ARG total_pss_mb=$mb target=$TARGET"
}

# D-02: the raw device file stays in a private temp dir; only closed-grammar lines with no leak shape are kept, and a
# rejected capture writes nothing.
do_pull_evidence() {
  TMP_DIR="$(mktemp -d)"
  local raw="$TMP_DIR/raw" kept="$TMP_DIR/kept" err="$TMP_DIR/err" n d out head
  if ! adbt shell "run-as $PKG cat files/evidence/$ARG.txt" >"$raw" 2>/dev/null || [ ! -s "$raw" ]; then
    finish 2 ERROR "reason=evidence_missing stage=$ARG target=$TARGET"
  fi
  if ! "$ROOT/scripts/spike-evidence-filter.sh" <"$raw" >"$kept" 2>"$err"; then
    echo "LEAK SCAN FAIL - the capture is rejected and nothing was written"
    finish 1 FAIL "reason=leak_scan_failed stage=$ARG"
  fi
  n="$(grep -c '' "$kept")"
  if [ "$n" -eq 0 ]; then
    echo "no evidence-grammar line in the capture - nothing was written"
    finish 2 ERROR "reason=no_evidence_lines stage=$ARG"
  fi
  d="$(sed -nE 's/.*dropped=([0-9]+).*/\1/p' "$err" | tail -n 1)"
  out="$EVIDENCE_DIR/$ARG.txt"
  head="$(git -C "$ROOT" rev-parse --short=10 HEAD 2>/dev/null || echo unknown)"
  mkdir -p "$EVIDENCE_DIR" || finish 2 ERROR "reason=evidence_dir_unwritable"
  {
    echo "# spike stage=$ARG captured_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ) target=$TARGET head=$head"
    cat "$kept"
  } >>"$out"
  finish 0 OK "stage=$ARG kept=$n dropped=${d:-0} file=$out"
}

# T-13-26: leave the TESTER as found, and PROVE it: the package, the external model directory and any staging file are gone.
do_cleanup() {
  local listing ext tmp
  rm -f "$STAMP_FILE"
  adbt shell am force-stop "$PKG" >/dev/null 2>&1 || true
  adbt shell "run-as $PKG rm -rf files/private files/evidence files/models" >/dev/null 2>&1 || true
  adbt shell cmd deviceidle whitelist "-$PKG" >/dev/null 2>&1 || true
  adb_t 60 -s "$TARGET" uninstall "$PKG" >/dev/null 2>&1 || true
  adbt shell rm -rf "$DEV_EXT_ROOT" >/dev/null 2>&1 || true
  adbt shell "rm -f /data/local/tmp/vae-spike-*" >/dev/null 2>&1 || true
  # Proof 1: the package is not listed (an empty listing proves nothing).
  listing="$(adbt shell pm list packages 2>/dev/null | strip_cr)"
  if [ -z "$listing" ] || printf '%s\n' "$listing" | grep -qxF "package:$PKG"; then
    echo "WARNING: could not prove that $PKG is gone from $TARGET"
    finish 1 FAIL "reason=uninstall_failed target=$TARGET"
  fi
  # Proof 2: the external model directory is gone. Proof 3: no staging file is left under /data/local/tmp.
  ext="$(adbt shell "ls $DEV_EXT_ROOT >/dev/null 2>&1 && echo present || echo absent" 2>/dev/null | strip_cr | tail -n 1)"
  tmp="$(adbt shell "ls -d /data/local/tmp/vae-spike-* 2>/dev/null; echo __done" 2>/dev/null | strip_cr)"
  if [ "$ext" != absent ] || [ "$tmp" != "__done" ]; then
    echo "WARNING: residue is left on $TARGET (external directory: ${ext:-unknown}; staging listing is not empty or unproven)"
    finish 1 FAIL "reason=residue_present target=$TARGET"
  fi
  echo "spike package, external model directory and staging files removed"
  finish 0 OK "target=$TARGET"
}

# ---- dispatch ----------------------------------------------------------------------------------------------------------

cd "$ROOT" || finish 2 ERROR "reason=bad_root"
[ "$SUB" = fetch-model ] && do_fetch_model

# Device subcommands refuse a foreign serial, pass the host checks (grant first), take the lock, then resolve and verify the TESTER.
refuse_foreign_serial
host_precheck
acquire_and_resolve
echo "target=$TARGET model=$MODEL sdk=$SDK"
print_foreground
case "$SUB" in
  preflight) do_preflight ;;
  window-start) do_window_start ;;
  build-install) do_build_install ;;
  push-model) do_push_model ;;
  push-private) do_push_private ;;
  run) do_run ;;
  cooldown) do_cooldown ;;
  meminfo) do_meminfo ;;
  pull-evidence) do_pull_evidence ;;
  cleanup) do_cleanup ;;
  *) finish 2 ERROR "reason=not_implemented" ;;
esac
