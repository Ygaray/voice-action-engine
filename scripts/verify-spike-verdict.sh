#!/usr/bin/env bash
# Recomputes the Phase 13 spike verdict from an evidence directory (SPIKE-02): the verdict is a pure function of the
# closed-grammar evidence lines and the locked thresholds, evaluated by VerdictRules in :spike-ondevice through the
# VerdictReproductionTest Gradle JVM test (no device, no network).
#   --print [dir]   print the SPIKE_VERDICT / SPIKE_CONTROL / SPIKE_VERDICT_META lines for the evidence directory
#                   (default: .planning/phases/13-on-device-model-spike/evidence), then
#                   "SPIKE_VERDICT_CODE sha=<10 hex>", the commit of the code that computed them.
#   --check [dir]   not implemented yet (exit 2, reason=not_implemented)
# It refuses (exit 2, "SPIKE_VERDICT_CHECK: FAIL reason=dirty_harness") when spike-ondevice/src has uncommitted changes,
# because the code SHA must truly name the code that computed the lines.
# Exit codes: 0 ok, 1 check failed, 2 usage or environment error.
set -uo pipefail
ROOT="$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel)"
PHASE_REL=".planning/phases/13-on-device-model-spike"
# Low-memory Gradle recipe (host memory is tight): no daemon, 2 workers, in-process Kotlin compiler.
LOW_MEM_GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx1536m"

TMPFILES=()
cleanup() { [ "${#TMPFILES[@]}" -eq 0 ] || rm -f "${TMPFILES[@]}"; }
trap cleanup EXIT

usage_error() { echo "verify-spike-verdict: $1" >&2; exit 2; }

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

mode="${1:-}"
case "$mode" in
  --print)
    dir="$(resolve_dir "${2:-}")" || exit 2
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
    echo "SPIKE_VERDICT_CHECK: FAIL reason=not_implemented"
    exit 2
    ;;
  *)
    usage_error "usage: $0 --print [evidence-dir] | --check [evidence-dir]"
    ;;
esac
