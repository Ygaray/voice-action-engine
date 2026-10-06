#!/usr/bin/env bash
# Recomputes the Phase 13 spike verdict from an evidence directory (SPIKE-02): the verdict is a pure function of the
# closed-grammar evidence lines and the locked thresholds, evaluated by VerdictRules in :spike-ondevice through the
# VerdictReproductionTest Gradle JVM test (no device, no network).
#   --print [dir]   print the SPIKE_VERDICT / SPIKE_CONTROL / SPIKE_VERDICT_META lines for the evidence directory
#                   (default: .planning/phases/13-on-device-model-spike/evidence), then
#                   "SPIKE_VERDICT_CODE sha=<10 hex>", the commit of the code that computed them.
#   --check [dir]   recompute and compare with the SPIKE_ lines of 13-VERDICT.md (the SPIKE_VERDICT_CODE and
#                   SPIKE_VERDICT_CHECK lines are not part of the comparison). It refuses evidence whose ENV thresholds_sha
#                   differs from the first 8 hex of sha256 of the committed 13-THRESHOLDS.md, and when spike-ondevice/ is
#                   gone from the working tree it recomputes in a temporary git worktree at the code SHA recorded in
#                   13-VERDICT.md, so the verdict stays reproducible after the spike module is deleted.
#                   Last line: "SPIKE_VERDICT_CHECK: OK lines=<n>" (exit 0) or "SPIKE_VERDICT_CHECK: FAIL reason=<code>"
#                   (exit 1). reasons: no_verdict_file, no_code_sha, thresholds_mismatch, code_sha_unavailable,
#                   compute_failed, verdict_differs.
# Both modes refuse (exit 2, "SPIKE_VERDICT_CHECK: FAIL reason=dirty_harness") when spike-ondevice/src has uncommitted
# changes, because the code SHA must truly name the code that computed the lines.
# Exit codes: 0 ok, 1 check failed, 2 usage or environment error.
set -uo pipefail
ROOT="$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel)"
PHASE_REL=".planning/phases/13-on-device-model-spike"
VERDICT_FILE="$ROOT/$PHASE_REL/13-VERDICT.md"
THRESHOLDS_FILE="$ROOT/$PHASE_REL/13-THRESHOLDS.md"
# Low-memory Gradle recipe (host memory is tight): no daemon, 2 workers, in-process Kotlin compiler.
LOW_MEM_GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx1536m"

TMPFILES=()
WORKTREE=""
cleanup() {
  [ "${#TMPFILES[@]}" -eq 0 ] || rm -f "${TMPFILES[@]}"
  if [ -n "$WORKTREE" ]; then
    git -C "$ROOT" worktree remove --force "$WORKTREE" >/dev/null 2>&1 || true
    rm -rf "$WORKTREE"
    git -C "$ROOT" worktree prune >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

usage_error() { echo "verify-spike-verdict: $1" >&2; exit 2; }
check_fail() { echo "SPIKE_VERDICT_CHECK: FAIL reason=$1"; exit 1; }

# The lines that are part of the verdict: SPIKE_VERDICT, SPIKE_CONTROL and SPIKE_VERDICT_META (not _CODE, not _CHECK).
verdict_lines() { grep -E '^(SPIKE_VERDICT |SPIKE_CONTROL |SPIKE_VERDICT_META )' || true; }

# compute_lines <tree root> <absolute evidence dir> <output file>: runs the Gradle JVM test in <tree root>.
compute_lines() {
  local tree="$1" dir="$2" out="$3"
  ( cd "$tree" && GRADLE_OPTS="$LOW_MEM_GRADLE_OPTS" ./gradlew --offline -q -Dorg.gradle.workers.max=2 \
      -Dorg.gradle.parallel=false :spike-ondevice:testDebugUnitTest --tests '*VerdictReproductionTest*' \
      "-PvaeSpikeEvidenceDir=$dir" "-PvaeSpikeVerdictOut=$out" >&2 )
}

refuse_dirty_harness() {
  local dirty
  dirty="$(git -C "$ROOT" status --porcelain -- spike-ondevice/src)"
  if [ -n "$dirty" ]; then
    echo "uncommitted changes under spike-ondevice/src:" >&2
    echo "$dirty" >&2
    echo "SPIKE_VERDICT_CHECK: FAIL reason=dirty_harness"
    exit 2
  fi
}

resolve_dir() {
  local d="${1:-$ROOT/$PHASE_REL/evidence}"
  [ -d "$d" ] || usage_error "not a directory: $d"
  (cd "$d" && pwd)
}

# thresholds_sha of the first ENV line over the evidence files in the order the verdict test reads them (sorted by name).
evidence_thresholds_sha() {
  local dir="$1" f
  for f in $(LC_ALL=C ls "$dir"/*.txt 2>/dev/null | LC_ALL=C sort); do
    grep -h '^VAE_SPIKE_ENV ' "$f" || true
  done | head -1 | grep -oE 'thresholds_sha=[0-9a-f]+' | head -1 | cut -d= -f2
}

mode="${1:-}"
case "$mode" in
  --print)
    dir="$(resolve_dir "${2:-}")" || exit 2
    [ -d "$ROOT/spike-ondevice" ] || usage_error "spike-ondevice/ is gone; use --check, which recomputes at the recorded code SHA"
    refuse_dirty_harness
    out="$(mktemp)"; TMPFILES+=("$out")
    if ! compute_lines "$ROOT" "$dir" "$out"; then
      echo "verify-spike-verdict: the Gradle verdict test failed" >&2
      exit 2
    fi
    [ -s "$out" ] || usage_error "the verdict test wrote no lines"
    cat "$out"
    echo "SPIKE_VERDICT_CODE sha=$(git -C "$ROOT" rev-parse --short=10 HEAD)"
    ;;
  --check)
    dir="$(resolve_dir "${2:-}")" || exit 2
    # (a) the committed verdict lines
    [ -f "$VERDICT_FILE" ] || check_fail no_verdict_file
    expected="$(verdict_lines <"$VERDICT_FILE")"
    [ -n "$expected" ] || check_fail no_verdict_file
    # (b) the code SHA recorded with them
    code_sha="$(grep -E '^SPIKE_VERDICT_CODE sha=[0-9a-f]+' "$VERDICT_FILE" | head -1 | sed -E 's/^SPIKE_VERDICT_CODE sha=([0-9a-f]+).*/\1/')"
    [ -n "$code_sha" ] || check_fail no_code_sha
    # (e) the evidence must have been produced under the committed thresholds (checked first: cheap, and a mismatch makes
    #     a recomputed verdict meaningless)
    want_sha="$(sha256sum "$THRESHOLDS_FILE" 2>/dev/null | cut -c1-8)"
    have_sha="$(evidence_thresholds_sha "$dir")"
    { [ -n "$want_sha" ] && [ "$have_sha" = "$want_sha" ]; } || check_fail thresholds_mismatch
    # (c) recompute: in place when the spike module exists, else in a temporary worktree at the recorded code SHA
    out="$(mktemp)"; TMPFILES+=("$out")
    if [ -d "$ROOT/spike-ondevice" ]; then
      refuse_dirty_harness
      if ! git -C "$ROOT" diff --quiet "$code_sha" HEAD -- spike-ondevice/src 2>/dev/null; then
        echo "note: spike-ondevice/src changed since the recorded code SHA $code_sha" >&2
      fi
      tree="$ROOT"
    else
      git -C "$ROOT" cat-file -e "$code_sha^{commit}" 2>/dev/null || check_fail code_sha_unavailable
      WORKTREE="$(mktemp -d)"
      git -C "$ROOT" worktree add --detach "$WORKTREE" "$code_sha" >/dev/null 2>&1 || check_fail code_sha_unavailable
      [ -f "$ROOT/local.properties" ] && cp "$ROOT/local.properties" "$WORKTREE/"
      tree="$WORKTREE"
    fi
    compute_lines "$tree" "$dir" "$out" || check_fail compute_failed
    [ -s "$out" ] || check_fail compute_failed
    # (d) exact, ordered comparison
    computed="$(verdict_lines <"$out")"
    if [ "$computed" != "$expected" ]; then
      diff -u <(printf '%s\n' "$expected") <(printf '%s\n' "$computed") || true
      check_fail verdict_differs
    fi
    # (f)
    echo "SPIKE_VERDICT_CHECK: OK lines=$(printf '%s\n' "$expected" | grep -c '')"
    ;;
  *)
    usage_error "usage: $0 --print [evidence-dir] | --check [evidence-dir]"
    ;;
esac
