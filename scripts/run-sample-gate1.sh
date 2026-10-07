#!/usr/bin/env bash
# Guarded host runner for the sample app's live legs (the PHASE_DIR below names whose decision file and evidence it uses). The ONLY sanctioned host path to the TESTER for the sample.
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
# The key helper: it moves a key by file reference and refuses the personal phone itself. PUSH_TEST_KEY names a binary only.
PUSH_TEST_KEY="${PUSH_TEST_KEY:-push-test-key}"

# The phase whose decision file and evidence this run uses. Three environment variables retarget it without editing the
# script (they name PLANNING FILES only, enforced for the decision file: it must resolve under <repo>/.planning/; none of
# them can change the device target):
#   VAE_GATE1_PHASE_DIR       the phase directory (default: the current phase, Phase 19)
#   VAE_GATE1_DECISION_FILE   the live-leg decision file; wins over the derived default
#                             <leading digits of the phase directory name>-LIVE-LEG-DECISION.md inside PHASE_DIR
#   VAE_GATE1_EVIDENCE_DIR    the evidence directory (default: PHASE_DIR/evidence)
# Phase 20 inherits the same three variables.
PHASE_DIR="${VAE_GATE1_PHASE_DIR:-.planning/phases/19-sample-gate-1-docs}"
PHASE_BASE="$(basename "$PHASE_DIR")"
DECISION_FILE="${VAE_GATE1_DECISION_FILE:-$PHASE_DIR/${PHASE_BASE%%-*}-LIVE-LEG-DECISION.md}"
EVIDENCE_DIR="${VAE_GATE1_EVIDENCE_DIR:-$PHASE_DIR/evidence}"
HOST_FIXTURE="sample/src/debug/assets/sb-a10-fixture.json"
# The expected fixture digest is read from this Kotlin constant (single source of truth), never duplicated here.
FIXTURE_KT="sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/fixture/FixtureLoader.kt"
DEVICE_FIXTURE="files/fixture/sb-a10-fixture.json"
LOG_DIR="sample/build/device-run"
APK_DIR="sample/build/outputs/apk/debug"
STAMP_FILE="${XDG_CACHE_HOME:-$HOME/.cache}/vae-gate1/anthropic-agentic.ts"
WARM_WINDOW_SECONDS=360

SUBCOMMANDS="preflight build-install push-fixture push-keys capture-start capture-save cold-stamp verify-keys-gone cleanup"
# Exactly the LegId.wire set of sample/.../evidence/EvidenceLine.kt (the verifier proves the parity).
LEGS="ver02 smoke_anthropic smoke_openai smoke_openrouter multi_openai multi_openrouter responses_probe demo_clarify demo_partial grammar_offline plan_live router_live undo_all"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# The published-module list for the build-install dirty check comes from the manifest, never a hand-copied list. If the
# reader or the manifest is unreadable the check reports dirty=unknown; it never fails the install.
export VAE_MODULES_FILE="${VAE_MODULES_FILE:-$ROOT/scripts/modules.list}"
if [ -r "$ROOT/scripts/lib/modules.sh" ]; then
  # shellcheck source=lib/modules.sh
  . "$ROOT/scripts/lib/modules.sh"
fi

SUB="none"
ARG=""
TARGET=""
MODEL=""
SDK=""
STAGING=""

# Every adb invocation goes through adb_t: a timeout, and fd 9 (the lock) closed so a daemonized adb server never
# inherits and holds the lock. The caller supplies -s <target> (or `connect <wifi>`).
adb_t() { local secs="$1"; shift; timeout "$secs" "$ADB" "$@" 9>&-; }
adbt() { adb_t 30 -s "$TARGET" "$@"; }
strip_cr() { tr -d '\r'; }

# The device-side staging file of push-fixture is ALWAYS removed: on every normal finish, and on a signal or abnormal exit.
rm_staging() {
  [ -n "$STAGING" ] || return 0
  local path="$STAGING"
  STAGING=""
  [ -n "$TARGET" ] && adb_t 15 -s "$TARGET" shell rm -f "$path" >/dev/null 2>&1
  return 0
}
# capture-save works in a private temp dir (raw logcat never leaves it); it is removed on every exit path.
TMP_DIR=""
rm_tmp() { [ -n "$TMP_DIR" ] && rm -rf "$TMP_DIR"; TMP_DIR=""; return 0; }
trap 'rm_staging; rm_tmp; exit 130' INT TERM HUP
trap 'rm_staging; rm_tmp' EXIT

# finish <code> <OK|FAIL|INFRA|ERROR> <details>: remove any staging or temp file, print the one final line and exit.
finish() {
  local code="$1" outcome="$2" details="$3"
  rm_staging
  rm_tmp
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
  # Read the whole listing first: `grep -q` exits early and adb would die of SIGPIPE (141) under pipefail.
  local listing
  listing="$(adbt shell pm list packages 2>/dev/null | strip_cr || true)"
  grep -qxF "package:$PKG" <<<"$listing"
}

not_implemented() { finish 2 ERROR "reason=not_implemented"; }

# ---- host-side checks, run before the lock and before any adb call ----------------------------------------------------

# The expected fixture digest: the 64-hex literal of the FIXTURE_SHA256 constant in FixtureLoader.kt.
expected_fixture_sha() {
  grep -oE 'FIXTURE_SHA256 *= *"[0-9a-f]{64}"' "$FIXTURE_KT" 2>/dev/null | grep -oE '[0-9a-f]{64}' | head -1
}

host_precheck() {
  case "$SUB" in
    push-keys)
      # D-13: no live spend, and no key leaves the host, until the recorded decision says so. Only the exact line passes: pending, deferred, consumed or a missing file all refuse.
      # The decision file must live under this repository's .planning/ (resolved, so ../ and symlinks cannot escape), and the
      # recorded decision is the FIRST 'decision: ' line of the file, not any matching line quoted further down.
      local decision_abs planning_abs
      decision_abs="$(realpath -m -- "$DECISION_FILE" 2>/dev/null || true)"
      planning_abs="$(realpath -m -- "$ROOT/.planning" 2>/dev/null || true)"
      case "$decision_abs" in
        "$planning_abs"/?*) ;;
        *)
          echo "decision file is outside $planning_abs: $DECISION_FILE - not pushing keys"
          finish 2 ERROR "reason=decision_file_outside_planning"
          ;;
      esac
      if [ "$(sed -n 's/^decision: //p' "$decision_abs" 2>/dev/null | head -1)" != approved ]; then
        echo "live legs are not approved: $DECISION_FILE does not record 'decision: approved' - not pushing keys"
        finish 2 ERROR "reason=live_legs_not_approved"
      fi
      ;;
    push-fixture)
      if [ ! -f "$HOST_FIXTURE" ]; then
        echo "fixture missing on the host: copy the LE-1 fixture by hand per GATE1-RUNBOOK step A2"
        finish 2 ERROR "reason=fixture_missing_on_host"
      fi
      EXPECTED_SHA="$(expected_fixture_sha)"
      if [ -z "$EXPECTED_SHA" ]; then
        echo "cannot read the FIXTURE_SHA256 constant from $FIXTURE_KT"
        finish 2 ERROR "reason=fixture_digest_unreadable"
      fi
      HOST_SHA="$(sha256sum "$HOST_FIXTURE" | cut -d' ' -f1)"
      if [ "$HOST_SHA" != "$EXPECTED_SHA" ]; then
        echo "fixture digest mismatch (host ${HOST_SHA:0:8}, expected ${EXPECTED_SHA:0:8}): do not use; ask the orchestrator to regenerate"
        finish 1 FAIL "reason=fixture_sha_mismatch host=${HOST_SHA:0:8} expected=${EXPECTED_SHA:0:8}"
      fi
      ;;
  esac
}

# ---- cold-run stamp (host only: no device, no lock) --------------------------------------------------------------------

# D-03: a cold Anthropic agentic start must be at least 360 s after the previous one. The app enforces the same window itself.
do_cold_stamp() {
  local now last age remaining
  now="$(date +%s)"
  if [ "$ARG" = write ]; then
    if ! { mkdir -p "$(dirname "$STAMP_FILE")" && echo "$now" >"$STAMP_FILE"; }; then
      finish 2 ERROR "reason=stamp_write_failed"
    fi
    finish 0 OK "stamp=written epoch=$now"
  fi
  if [ -e "$STAMP_FILE" ]; then
    last="$(tr -d '[:space:]' <"$STAMP_FILE" 2>/dev/null)"
    case "$last" in
      '' | *[!0-9]*)
        echo "cold stamp $STAMP_FILE is unreadable; cannot prove the run is cold"
        finish 3 INFRA "reason=stamp_invalid"
        ;;
    esac
    age=$((now - last))
    if [ "$age" -lt "$WARM_WINDOW_SECONDS" ]; then
      remaining=$((WARM_WINDOW_SECONDS - age))
      [ "$remaining" -le "$WARM_WINDOW_SECONDS" ] || remaining="$WARM_WINDOW_SECONDS"
      echo "WARM WINDOW - the last Anthropic agentic start was ${age}s ago; wait ${remaining}s for a cold run"
      finish 3 INFRA "reason=warm_window remaining=$remaining"
    fi
  fi
  finish 0 OK "cold=yes"
}

# ---- device subcommands ------------------------------------------------------------------------------------------------

do_preflight() {
  local installed=no
  package_installed && installed=yes
  finish 0 OK "target=$TARGET model=$MODEL sdk=$SDK installed=$installed"
}

do_build_install() {
  mkdir -p "$LOG_DIR"
  if ! ./gradlew :sample:assembleDebug --offline -q >"$LOG_DIR/gradle.log" 2>&1 9>&-; then
    echo "Gradle build failed; the last lines of $LOG_DIR/gradle.log:"
    tail -n 40 "$LOG_DIR/gradle.log"
    finish 2 ERROR "reason=build_failed target=$TARGET"
  fi
  local apk_count apk md5 head dirty asset listing mods
  local -a dirty_paths
  apk_count="$(find "$APK_DIR" -maxdepth 1 -name '*.apk' 2>/dev/null | wc -l | tr -d ' ')"
  [ "$apk_count" = 1 ] || finish 2 ERROR "reason=apk_missing target=$TARGET"
  apk="$(find "$APK_DIR" -maxdepth 1 -name '*.apk')"
  md5="$(md5sum "$apk" | cut -d' ' -f1)"
  head="$(git -C "$ROOT" rev-parse --short=10 HEAD 2>/dev/null || echo unknown)"
  dirty=unknown
  if declare -F vae_modules >/dev/null && mods="$(vae_modules 2>/dev/null)" && [ -n "$mods" ]; then
    read -r -a dirty_paths <<<"$mods"
    dirty_paths+=(sample scripts gradle build.gradle.kts settings.gradle.kts)
    if listing="$(git -C "$ROOT" status --porcelain -- "${dirty_paths[@]}" 2>/dev/null)"; then
      if [ -n "$listing" ]; then dirty=1; else dirty=0; fi
    fi
  fi
  if listing="$(unzip -l "$apk" 2>/dev/null)"; then
    if printf '%s\n' "$listing" | grep -q 'assets/sb-a10-fixture'; then asset=present; else asset=absent; fi
  else
    asset=unknown
  fi
  echo "apk_md5=$md5 head=$head dirty=$dirty asset_fixture=$asset"

  if ! adb_t 180 -s "$TARGET" install -r "$apk" >/dev/null 2>&1; then
    if [ "$(adb_t 15 -s "$TARGET" get-state 2>/dev/null | strip_cr)" != "device" ]; then
      finish 3 INFRA "reason=tester_offline target=$TARGET"
    fi
    finish 2 ERROR "reason=install_failed target=$TARGET"
  fi
  # Freecess can freeze a backgrounded app mid-run; whitelisting is best effort and is undone by cleanup.
  adbt shell cmd deviceidle whitelist "+$PKG" >/dev/null 2>&1 || true
  package_installed || finish 2 ERROR "reason=install_failed target=$TARGET"
  finish 0 OK "target=$TARGET apk_md5=$md5 head=$head dirty=$dirty asset_fixture=$asset"
}

do_push_fixture() {
  # The digest was proven on the host (host_precheck). The staging file is removed by finish and by the EXIT trap.
  STAGING="/data/local/tmp/vae-fx-$(od -An -N6 -tx1 /dev/urandom | tr -d ' \n')"
  if ! adbt push "$HOST_FIXTURE" "$STAGING" >/dev/null 2>&1; then
    finish 2 ERROR "reason=push_failed target=$TARGET"
  fi
  if ! adbt shell "run-as $PKG sh -c 'mkdir -p files/fixture && cat > $DEVICE_FIXTURE' < $STAGING" >/dev/null 2>&1; then
    finish 2 ERROR "reason=run_as_failed target=$TARGET"
  fi
  local remote
  remote="$(adbt shell "run-as $PKG sha256sum $DEVICE_FIXTURE" 2>/dev/null | strip_cr | cut -d' ' -f1)"
  if [ "$remote" != "$EXPECTED_SHA" ]; then
    finish 1 FAIL "reason=fixture_readback_mismatch device=${remote:0:8} expected=${EXPECTED_SHA:0:8}"
  fi
  # LE-7: the 8-hex prefix only; the line is pasted into evidence, so no suffix is ever printed.
  finish 0 OK "fixture_sha=${EXPECTED_SHA:0:8} target=$TARGET"
}

do_push_keys() {
  local provider
  for provider in anthropic openai openrouter; do
    if ! timeout 120 "$PUSH_TEST_KEY" "$provider" --device "$TARGET" --package "$PKG" 9>&-; then
      echo "stop: tell Yahir which key file to (re)create, per test-keys.md; do not work around"
      finish 2 ERROR "reason=push_key_failed provider=$provider"
    fi
  done
  finish 0 OK "providers=anthropic,openai,openrouter target=$TARGET"
}

do_capture_start() {
  adbt logcat -c >/dev/null 2>&1 || finish 2 ERROR "reason=logcat_clear_failed target=$TARGET"
  finish 0 OK "target=$TARGET"
}

# D-02: the raw logcat stays in a private temp dir; only lines of the closed evidence grammar, with no key shape, are kept,
# and a rejected capture writes nothing.
do_capture_save() {
  TMP_DIR="$(mktemp -d)"
  local raw="$TMP_DIR/raw" kept="$TMP_DIR/kept" err="$TMP_DIR/err" n d out head
  if ! adbt logcat -d -v raw -s VaeSample:I >"$raw" 2>/dev/null; then
    finish 2 ERROR "reason=logcat_failed target=$TARGET"
  fi
  if ! "$ROOT/scripts/sample-evidence-filter.sh" <"$raw" >"$kept" 2>"$err"; then
    echo "LEAK SCAN FAIL - the capture is rejected and nothing was written"
    finish 1 FAIL "reason=leak_scan_failed leg=$ARG"
  fi
  if ! grep -q "^VAE_VERDICT leg=$ARG " "$kept"; then
    echo "no VAE_VERDICT line for leg $ARG in the capture - nothing was written"
    finish 1 FAIL "reason=no_verdict_line leg=$ARG"
  fi
  n="$(grep -c '' "$kept")"
  d="$(sed -nE 's/.*dropped=([0-9]+).*/\1/p' "$err" | tail -1)"
  out="$EVIDENCE_DIR/gate1-$ARG.txt"
  head="$(git -C "$ROOT" rev-parse --short=10 HEAD 2>/dev/null || echo unknown)"
  mkdir -p "$EVIDENCE_DIR"
  {
    echo "# gate1 leg=$ARG captured_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ) target=$TARGET head=$head"
    cat "$kept"
  } >>"$out"
  finish 0 OK "kept=$n dropped=${d:-0} file=$out"
}

# The proof must be POSITIVE: the device shell prints a sentinel (__rc=0) after listing the directory, so an adb failure, a
# timeout, an offline device or a refused run-as (all of which print nothing, or an error) can never read as "keys gone".
do_verify_keys_gone() {
  local out rc body count
  out="$(adbt shell "run-as $PKG sh -c 'if [ -d files/test-keys ]; then ls files/test-keys; fi; echo __rc=\$?'" 2>&1 | strip_cr; exit "${PIPESTATUS[0]}")"
  rc=$?
  case "$out" in
    "run-as:"* | *"not debuggable"* | *"Unknown package"*)
      finish 2 ERROR "reason=run_as_failed target=$TARGET"
      ;;
  esac
  # Success needs adb to have exited 0 AND the last output line to be exactly the sentinel.
  if [ "$rc" -ne 0 ] || [ "$(printf '%s\n' "$out" | tail -n 1)" != "__rc=0" ]; then
    echo "could not prove the plaintext key directory is empty (adb failed, timed out or the listing failed)"
    finish 2 ERROR "reason=check_unproven target=$TARGET"
  fi
  body="$(printf '%s\n' "$out" | sed '$d')"
  if [ -z "$body" ]; then
    echo "test-keys dir empty"
    finish 0 OK "keys_gone=yes"
  fi
  count="$(printf '%s\n' "$body" | grep -c .)"
  echo "plaintext key files are still present on the device (count only; names and contents are never printed)"
  finish 1 FAIL "reason=plaintext_keys_present count=$count"
}

do_cleanup() {
  adbt shell am force-stop "$PKG" >/dev/null 2>&1 || true
  adbt shell "run-as $PKG rm -rf files/test-keys files/fixture" >/dev/null 2>&1 || true
  adbt shell cmd deviceidle whitelist "-$PKG" >/dev/null 2>&1 || true
  adb_t 60 -s "$TARGET" uninstall "$PKG" >/dev/null 2>&1 || true
  local listing
  listing="$(adbt shell pm list packages 2>/dev/null | strip_cr)"
  if [ -n "$listing" ] && ! printf '%s\n' "$listing" | grep -qxF "package:$PKG"; then
    echo "sample package removed"
    finish 0 OK "target=$TARGET"
  fi
  echo "WARNING: could not prove that $PKG is gone from $TARGET"
  finish 1 FAIL "reason=uninstall_failed target=$TARGET"
}

# ---- dispatch ----------------------------------------------------------------------------------------------------------

cd "$ROOT" || finish 2 ERROR "reason=bad_root"
[ "$SUB" = cold-stamp ] && do_cold_stamp

# Device subcommands refuse a foreign serial, pass the host checks, take the lock, then resolve and verify the TESTER.
refuse_foreign_serial
host_precheck
acquire_and_resolve
echo "target=$TARGET model=$MODEL sdk=$SDK"
print_foreground
case "$SUB" in
  preflight) do_preflight ;;
  build-install) do_build_install ;;
  push-fixture) do_push_fixture ;;
  push-keys) do_push_keys ;;
  capture-start) do_capture_start ;;
  capture-save) do_capture_save ;;
  verify-keys-gone) do_verify_keys_gone ;;
  cleanup) do_cleanup ;;
  *) not_implemented ;;
esac
