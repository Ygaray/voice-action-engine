#!/usr/bin/env bash
# Guarded host runner for the Phase 14 D-12 recognizer capture. The ONLY sanctioned host path to the TESTER for it.
#   TESTER ONLY: USB serial R5CT10XNKQN, model SM-S908U, SDK at least 35. It NEVER substitutes another device, never
#   touches the personal phone (100.126.94.47), and has no option, argument or environment variable that changes the
#   target (ADB may name a binary only). Every adb call carries -s R5CT10XNKQN, except the read-only `adb devices` listing
#   used to see whether the TESTER is attached. The runner never changes the radio state of the device in any way.
#   Window gate: every device subcommand refuses (exit 4, reason=no_grant) before the lock and before any adb call
#   unless the FIRST block of key lines of the grant file holds the exact line `grant: open`. Only the orchestrator relay
#   may open it (plan 14-09 records the relayed answer); `grant: pending`, `consumed` and `deferred` all refuse.
#   One device tester at a time: device subcommands take a non-blocking flock on the SAME lock file as
#   scripts/run-keystore-instrumented.sh and scripts/run-sample-gate1.sh.
#   The recognizer output (JSONL from the on-device tool) is host-private until `filter` allow-lists it by prompt id and
#   drops any URL, key shape or control character; only the filtered TSV is ever committed.
#
# Usage: scripts/run-stt-capture.sh <subcommand> [args]
#   build                     host only, no grant needed: assemble the :sample debug and androidTest APKs, print their paths
#   preflight                 grant + lock + TESTER identity, then print target, model, sdk and install state
#   install                   install both APKs (-r) and grant RECORD_AUDIO to the app
#   push-prompts              push core/src/test/resources/grammar/stt-prompts.tsv into the app's external files dir (app-owned, no subdirectory)
#   run                       am instrument the capture tool (bounded), print only the pass/fail verdict
#   pull <host-dir>           pull the tool's JSONL into <host-dir> (refused inside the repository)
#   filter <jsonl> <out.tsv>  host only: allow-list by prompt id and status ok, write the labeled TSV
#   cleanup                   force-stop, remove the capture dir, uninstall both packages, prove both are gone
# JSONL contract (one JSON object per line, written by the capture tool): {"id","lang","status","recognized"} where status
# is ok | error | tts_unavailable and recognized is the final recognizer text (absent unless status is ok).
# Exit codes: 0 OK   1 FAIL   2 ERROR (usage, build, install, instrumentation)   3 INFRA (TESTER offline or busy)
#             4 INFRA (refused: no open grant, identity mismatch, or a non-TESTER device requested)
# The LAST line is always: STT_CAPTURE: <OK|FAIL|INFRA|ERROR> sub=<subcommand> <key=value ...>
set -uo pipefail

TESTER_USB="R5CT10XNKQN"
PERSONAL_IP="100.126.94.47"
EXPECTED_MODEL="SM-S908U"
MIN_SDK=35
APP_PKG="io.github.ygaray.voiceactionengine.sample"
TEST_PKG="io.github.ygaray.voiceactionengine.sample.test"
TOOL_CLASS="io.github.ygaray.voiceactionengine.sample.SttFormsCaptureTool"
INSTRUMENT_RUNNER="androidx.test.runner.AndroidJUnitRunner"
# Byte-identical to the sibling runners' LOCK_FILE line (the guard script asserts it), so all three share one lock.
LOCK_FILE="${XDG_RUNTIME_DIR:-/tmp}/vae-keystore-tester.lock"
ADB="${ADB:-adb}"

# Fixed on purpose: no environment variable may redirect the grant gate to a file the caller controls (the committed,
# relayed grant file is the control). The guard script's static check refuses any ${VAE_...} read in this file.
PHASE_DIR=".planning/phases/14-localgrammar-bilingual-grammarpack"
GRANT_FILE="$PHASE_DIR/14-WINDOW-GRANT.md"
PROMPTS="core/src/test/resources/grammar/stt-prompts.tsv"
# The app's own external files dir itself, NOT a subdirectory: an adb mkdir creates the subdirectory owned by shell (mode 2770,
# group ext_data_rw), which the app process cannot read through (observed on the TESTER: "prompt list missing"). A file pushed
# straight into the app-owned files dir is readable by the app, which is also how the stt-engine file-fed runs worked.
REMOTE_DIR="/sdcard/Android/data/$APP_PKG/files"
REMOTE_OUT="$REMOTE_DIR/stt-forms.jsonl"
APK_APP_DIR="sample/build/outputs/apk/debug"
APK_TEST_DIR="sample/build/outputs/apk/androidTest/debug"
LOG_DIR="sample/build/stt-capture"
RUN_TIMEOUT=1800
# Host-safe Gradle recipe: the host is memory tight, so one small in-process job.
LOW_MEM_GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx1536m"

SUBCOMMANDS="build preflight install push-prompts run pull filter cleanup"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

SUB="none"
ARG1=""
ARG2=""
TARGET=""
MODEL=""
SDK=""
TMP_DIR=""

# Every adb invocation goes through adb_t: a timeout, and fd 9 (the lock) closed so a daemonized adb server never
# inherits and holds the lock. The caller supplies -s <serial> (or the read-only `devices` listing).
adb_t() { local secs="$1"; shift; timeout "$secs" "$ADB" "$@" 9>&-; }
adbt() { adb_t 30 -s "$TARGET" "$@"; }
strip_cr() { tr -d '\r'; }

rm_tmp() { [ -n "$TMP_DIR" ] && rm -rf "$TMP_DIR"; TMP_DIR=""; return 0; }
trap 'rm_tmp; exit 130' INT TERM HUP
trap 'rm_tmp' EXIT

# finish <code> <OK|FAIL|INFRA|ERROR> <details>: remove any temp file, print the one final line and exit.
finish() {
  local code="$1" outcome="$2" details="$3"
  rm_tmp
  echo "STT_CAPTURE: $outcome sub=$SUB $details"
  exit "$code"
}

in_list() { local needle="$1" item; shift; for item in $1; do [ "$item" = "$needle" ] && return 0; done; return 1; }

usage() {
  echo "usage: scripts/run-stt-capture.sh <subcommand> [args]"
  echo "  subcommands: $SUBCOMMANDS"
  echo "  pull <host-dir>   filter <jsonl> <out.tsv>"
  echo "  (the target is fixed to the TESTER; there is no option to change it)"
  finish 2 ERROR "reason=usage"
}

# a. Arguments are fixed before any other work: one subcommand from the list, and only the arguments that subcommand needs.
[ $# -ge 1 ] || usage
in_list "$1" "$SUBCOMMANDS" || usage
SUB="$1"
case "$SUB" in
  pull)
    [ $# -eq 2 ] || usage
    ARG1="$2"
    ;;
  filter)
    [ $# -eq 3 ] || usage
    ARG1="$2"
    ARG2="$3"
    ;;
  *)
    [ $# -eq 1 ] || usage
    ;;
esac

# b. The window grant: the first block of key lines must carry the exact line `grant: open` (and no other grant line).
#    Runs before the lock and before any adb call. Anything else (pending, consumed, deferred, a missing or unreadable
#    file, `grant: open` anywhere but the first block, trailing characters) refuses.
grant_block() {
  awk 'BEGIN { s = 0 }
       s == 0 { if ($0 ~ /^[a-z_]+: /) { s = 1; print } ; next }
       s == 1 { if ($0 ~ /^[a-z_]+: /) print; else exit }' "$GRANT_FILE" 2>/dev/null
}

require_open_grant() {
  local block grants
  block="$(grant_block)"
  grants="$(printf '%s\n' "$block" | grep -c '^grant: ' || true)"
  if [ "$grants" = 1 ] && printf '%s\n' "$block" | grep -qx 'grant: open'; then
    return 0
  fi
  echo "NO WINDOW GRANT - $GRANT_FILE does not open the TESTER window; no device step was attempted"
  finish 4 INFRA "reason=no_grant"
}

# c. A foreign ANDROID_SERIAL is refused before any adb call; an allowed one is dropped (every call uses -s anyway).
refuse_foreign_serial() {
  if [ -n "${ANDROID_SERIAL:-}" ] && [ "$ANDROID_SERIAL" != "$TESTER_USB" ]; then
    case "$ANDROID_SERIAL" in
      *"$PERSONAL_IP"*) echo "TESTER IDENTITY MISMATCH - refusing (ANDROID_SERIAL names the personal phone)" ;;
      *) echo "TESTER IDENTITY MISMATCH - refusing (ANDROID_SERIAL names a device that is not the TESTER)" ;;
    esac
    finish 4 INFRA "reason=refused_serial"
  fi
  unset ANDROID_SERIAL
}

# d-f. Take the shared lock, require the TESTER attached over USB, and prove the identity of the handset.
acquire_and_resolve() {
  exec 9>"$LOCK_FILE"
  if ! flock -n 9; then
    echo "TESTER BUSY - another device run holds $LOCK_FILE"
    finish 3 INFRA "reason=tester_busy"
  fi

  local listing
  listing="$(adb_t 15 devices 2>/dev/null | strip_cr || true)"
  if ! printf '%s\n' "$listing" | grep -qE "^${TESTER_USB}[[:space:]]+device$"; then
    echo "TESTER OFFLINE - not substituting"
    finish 3 INFRA "reason=tester_offline"
  fi
  TARGET="$TESTER_USB"

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

package_installed() {
  # Read the whole listing first: `grep -q` exits early and adb would die of SIGPIPE under pipefail.
  local pkg="$1" listing
  listing="$(adbt shell pm list packages 2>/dev/null | strip_cr || true)"
  grep -qxF "package:$pkg" <<<"$listing"
}

# ---- host-only subcommands ---------------------------------------------------------------------------------------------

do_build() {
  mkdir -p "$LOG_DIR"
  if ! GRADLE_OPTS="$LOW_MEM_GRADLE_OPTS" ./gradlew --offline -q -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false \
    :sample:assembleDebug :sample:assembleDebugAndroidTest >"$LOG_DIR/gradle.log" 2>&1; then
    echo "Gradle build failed; the last lines of $LOG_DIR/gradle.log:"
    tail -n 40 "$LOG_DIR/gradle.log"
    finish 2 ERROR "reason=build_failed"
  fi
  local app test
  app="$(find "$APK_APP_DIR" -maxdepth 1 -name '*.apk' 2>/dev/null | head -1)"
  test="$(find "$APK_TEST_DIR" -maxdepth 1 -name '*.apk' 2>/dev/null | head -1)"
  if [ -z "$app" ] || [ -z "$test" ]; then
    finish 2 ERROR "reason=apk_missing"
  fi
  echo "app_apk=$app"
  echo "test_apk=$test"
  finish 0 OK "app_apk=$app test_apk=$test"
}

# The allow-list filter. Reads the tool's JSONL, keeps only rows whose id is a known prompt and whose status is ok, labels
# them synthetic-tts, and drops (never prints) any line carrying a URL, a key shape, a control character or bad JSON.
FILTER_PY='
import json, re, sys
jsonl, prompts, out = sys.argv[1], sys.argv[2], sys.argv[3]
CTRL = re.compile(r"[\x00-\x1f\x7f]")
URL = re.compile(r"[a-z][a-z0-9+.-]*://|www\.", re.I)
KEY = re.compile("(^|[^A-Za-z0-9])(s" "k-(ant|or|proj)-|s" "k-[A-Za-z0-9_-]{20})|bearer|x-api-key|authorization", re.I)
meta = {}
order = []
with open(prompts, encoding="utf-8") as f:
    for i, line in enumerate(f):
        if i == 0:
            continue
        p = line.rstrip("\n").split("\t")
        if len(p) == 5:
            meta[p[0]] = p
            order.append(p[0])
counts = {"ok": 0, "error": 0, "tts_unavailable": 0}
dropped = 0
rows = {}
def bad(text):
    return bool(CTRL.search(text) or URL.search(text) or KEY.search(text))
with open(jsonl, encoding="utf-8", errors="replace") as f:
    for raw in f:
        line = raw.rstrip("\n").rstrip("\r")
        if not line.strip():
            continue
        if bad(line):
            dropped += 1
            continue
        try:
            obj = json.loads(line)
        except ValueError:
            dropped += 1
            continue
        if not isinstance(obj, dict):
            dropped += 1
            continue
        rid, status, rec = obj.get("id"), obj.get("status"), obj.get("recognized")
        if not isinstance(rid, str) or rid not in meta or rid in rows or status not in counts:
            dropped += 1
            continue
        if status == "ok":
            if not isinstance(rec, str) or not rec.strip() or len(rec) > 200 or bad(rec):
                dropped += 1
                continue
            rows[rid] = rec
        counts[status] += 1
if rows:
    with open(out, "w", encoding="utf-8", newline="") as o:
        o.write("id\tlang\ttext\texpect\trecognized\tprovenance\n")
        for rid in order:
            if rid in rows:
                m = meta[rid]
                o.write("\t".join([rid, m[1], m[2], m[3], rows[rid], "synthetic-tts"]) + "\n")
print("ok=%d error=%d tts_unavailable=%d dropped=%d" % (counts["ok"], counts["error"], counts["tts_unavailable"], dropped))
sys.exit(0 if rows else 3)
'

do_filter() {
  [ -f "$ARG1" ] || { echo "jsonl not found: $ARG1"; finish 2 ERROR "reason=jsonl_missing"; }
  [ -f "$PROMPTS" ] || { echo "prompt list missing: $PROMPTS"; finish 2 ERROR "reason=prompts_missing"; }
  [ -d "$(dirname "$ARG2")" ] || { echo "output directory missing: $(dirname "$ARG2")"; finish 2 ERROR "reason=out_dir_missing"; }
  case "$(realpath -m "$ARG2")" in
    "$(realpath -m "$PROMPTS")") finish 2 ERROR "reason=out_is_prompts" ;;
  esac
  command -v python3 >/dev/null 2>&1 || finish 2 ERROR "reason=python_missing"
  local counts rc
  counts="$(python3 -c "$FILTER_PY" "$ARG1" "$PROMPTS" "$ARG2")"
  rc=$?
  if [ "$rc" -eq 0 ]; then
    finish 0 OK "$counts out=$ARG2"
  fi
  finish 1 FAIL "reason=no_ok_rows ${counts:-ok=0 error=0 tts_unavailable=0 dropped=0}"
}

# ---- device subcommands ------------------------------------------------------------------------------------------------

do_preflight() {
  local app=no test=no
  package_installed "$APP_PKG" && app=yes
  package_installed "$TEST_PKG" && test=yes
  finish 0 OK "target=$TARGET model=$MODEL sdk=$SDK app_installed=$app test_installed=$test"
}

do_install() {
  local app test
  app="$(find "$APK_APP_DIR" -maxdepth 1 -name '*.apk' 2>/dev/null | head -1)"
  test="$(find "$APK_TEST_DIR" -maxdepth 1 -name '*.apk' 2>/dev/null | head -1)"
  if [ -z "$app" ] || [ -z "$test" ]; then
    echo "APKs missing: run scripts/run-stt-capture.sh build first"
    finish 2 ERROR "reason=apk_missing target=$TARGET"
  fi
  if ! adb_t 180 -s "$TARGET" install -r "$app" >/dev/null 2>&1; then
    finish 2 ERROR "reason=install_failed target=$TARGET"
  fi
  if ! adb_t 180 -s "$TARGET" install -r "$test" >/dev/null 2>&1; then
    finish 2 ERROR "reason=install_failed target=$TARGET"
  fi
  adbt shell pm grant "$APP_PKG" android.permission.RECORD_AUDIO >/dev/null 2>&1 \
    || finish 2 ERROR "reason=grant_failed target=$TARGET"
  package_installed "$APP_PKG" && package_installed "$TEST_PKG" || finish 2 ERROR "reason=install_failed target=$TARGET"
  finish 0 OK "target=$TARGET"
}

do_push_prompts() {
  [ -f "$PROMPTS" ] || finish 2 ERROR "reason=prompts_missing target=$TARGET"
  adbt shell test -d "$REMOTE_DIR" >/dev/null 2>&1 || finish 2 ERROR "reason=files_dir_missing target=$TARGET"
  adbt push "$PROMPTS" "$REMOTE_DIR/stt-prompts.tsv" >/dev/null 2>&1 || finish 2 ERROR "reason=push_failed target=$TARGET"
  finish 0 OK "target=$TARGET prompts=$(($(grep -c '' "$PROMPTS") - 1))"
}

# The raw instrumentation output stays in a private temp dir and is never printed: only the verdict is reported.
do_run() {
  TMP_DIR="$(mktemp -d)"
  local raw="$TMP_DIR/instrument" rc tests
  adb_t "$RUN_TIMEOUT" -s "$TARGET" shell am instrument -w -e class "$TOOL_CLASS" -e captureSttForms true \
    "$TEST_PKG/$INSTRUMENT_RUNNER" >"$raw" 2>&1
  rc=$?
  if [ "$rc" -eq 124 ]; then
    # timeout only kills the local adb client: the on-device instrumentation would keep driving TTS and the recognizer after
    # the lock is released. Stop both packages (best effort) before reporting, so the TESTER is free when the lock is.
    adbt shell am force-stop "$APP_PKG" >/dev/null 2>&1 || true
    adbt shell am force-stop "$TEST_PKG" >/dev/null 2>&1 || true
    finish 1 FAIL "reason=run_timeout target=$TARGET timeout_s=$RUN_TIMEOUT"
  fi
  tests="$(sed -nE 's/^OK \(([0-9]+) tests?\)/\1/p' "$raw" | tail -1)"
  if [ "$rc" -eq 0 ] && [ -n "$tests" ]; then
    finish 0 OK "target=$TARGET tests=$tests"
  fi
  finish 1 FAIL "reason=instrumentation_failed target=$TARGET rc=$rc"
}

# A destination inside the repository is refused: the raw recognizer output must stay host-private until filtered.
pull_dest_guard() {
  local dest repo
  dest="$(realpath -m "$ARG1")"
  repo="$(realpath -m "$ROOT")"
  if [ "$dest" = "$repo" ] || [[ "$dest" == "$repo/"* ]]; then
    echo "refusing to pull raw recognizer output into the repository ($dest)"
    finish 2 ERROR "reason=dest_in_repo"
  fi
}

do_pull() {
  mkdir -p "$ARG1" || finish 2 ERROR "reason=dest_unwritable target=$TARGET"
  if ! adb_t 120 -s "$TARGET" pull "$REMOTE_OUT" "$ARG1/stt-forms.jsonl" >/dev/null 2>&1; then
    finish 2 ERROR "reason=pull_failed target=$TARGET"
  fi
  finish 0 OK "target=$TARGET file=$ARG1/stt-forms.jsonl lines=$(grep -c '' "$ARG1/stt-forms.jsonl")"
}

do_cleanup() {
  adbt shell am force-stop "$APP_PKG" >/dev/null 2>&1 || true
  adbt shell rm -f "$REMOTE_DIR/stt-prompts.tsv" "$REMOTE_OUT" >/dev/null 2>&1 || true
  adbt shell rm -rf "$REMOTE_DIR/stt-capture" >/dev/null 2>&1 || true
  adb_t 60 -s "$TARGET" uninstall "$TEST_PKG" >/dev/null 2>&1 || true
  adb_t 60 -s "$TARGET" uninstall "$APP_PKG" >/dev/null 2>&1 || true
  local listing
  listing="$(adbt shell pm list packages 2>/dev/null | strip_cr)"
  if [ -n "$listing" ] && ! printf '%s\n' "$listing" | grep -qxF "package:$APP_PKG" \
    && ! printf '%s\n' "$listing" | grep -qxF "package:$TEST_PKG"; then
    echo "capture packages removed"
    finish 0 OK "target=$TARGET"
  fi
  echo "WARNING: could not prove that both capture packages are gone from $TARGET"
  finish 1 FAIL "reason=uninstall_failed target=$TARGET"
}

# ---- dispatch ----------------------------------------------------------------------------------------------------------

cd "$ROOT" || finish 2 ERROR "reason=bad_root"
case "$SUB" in
  build) do_build ;;
  filter) do_filter ;;
esac

# Device subcommands: the grant first, then a foreign serial, then the host checks, the lock, the TESTER and its identity.
require_open_grant
refuse_foreign_serial
[ "$SUB" = pull ] && pull_dest_guard
acquire_and_resolve
case "$SUB" in
  preflight) do_preflight ;;
  install) do_install ;;
  push-prompts) do_push_prompts ;;
  run) do_run ;;
  pull) do_pull ;;
  cleanup) do_cleanup ;;
  *) finish 2 ERROR "reason=not_implemented" ;;
esac
