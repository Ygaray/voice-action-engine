#!/usr/bin/env bash
# Phase gate (NOT part of `check`): prove that every ML-denial gate (SC4, D-09) goes RED, and for the RIGHT reason, when an
# on-device ML runtime or private spike artifact is planted, and GREEN on the clean tree.
#   Part A  an ML dependency declared on :core, :providers and :keystore trips verifyNoMlArtifacts
# Every plant is removed on exit (trap); the script then asserts the touched build files are byte-identical to their backups.
# Never commit a plant. Run:  scripts/verify-ml-denial-controls.sh   (a few minutes warm)
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
PLANTS=(); RESTORE=(); LOG="$(mktemp)"; BAK="$(mktemp -d)"
key() { echo "${1//\//_}"; }
cleanup() {
  for f in "${PLANTS[@]:-}"; do [ -n "$f" ] && rm -f "$f"; done
  for f in "${RESTORE[@]:-}"; do if [ -n "$f" ] && [ -f "$BAK/$(key "$f")" ]; then cp "$BAK/$(key "$f")" "$f"; fi; done
  rm -f "$LOG"
  rm -rf "$BAK"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

# Low-memory recipe (host runs earlyoom): single-use daemon, two workers, in-process Kotlin.
export GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx1536m"
GRADLEW=./gradlew; fails=0; plants=0
GRADLE_FLAGS=(--offline -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false)

backup()  { cp "$1" "$BAK/$(key "$1")"; RESTORE+=("$1"); }
restore() { cp "$BAK/$(key "$1")" "$1"; }

expect_task_red() { # <label> <marker> <gradle args...>
  local label=$1 marker=$2 quiet="-q"; shift 2
  plants=$((plants+1))
  if [ "${VERBOSE:-0}" = "1" ]; then quiet=""; fi   # test stdout is only printed without -q
  if $GRADLEW $quiet "${GRADLE_FLAGS[@]}" "$@" >"$LOG" 2>&1; then
    echo "FAIL  [$label] stayed GREEN"; fails=$((fails+1))
  elif ! grep -qF -- "$marker" "$LOG"; then
    echo "FAIL  [$label] went red for the WRONG reason (missing '$marker')"; fails=$((fails+1))
  else
    echo "ok    [$label] went red ($marker)"
  fi
}

expect_task_green() { # <label> <gradle args...>
  local label=$1; shift
  if $GRADLEW -q "${GRADLE_FLAGS[@]}" "$@" >"$LOG" 2>&1; then
    echo "ok    [$label] stayed green"
  else
    echo "FAIL  [$label] went RED on the clean tree"; fails=$((fails+1))
  fi
}

echo "== Part A: an ML dependency on a published module"
for m in core providers keystore; do
  backup "$m/build.gradle.kts"
  printf '\ndependencies { implementation("com.google.ai.edge.litertlm:litertlm-android:0.17.1") }\n' >> "$m/build.gradle.kts"
  expect_task_red "$m gains an ML dependency" "resolves ML artifacts" ":$m:verifyNoMlArtifacts"
  restore "$m/build.gradle.kts"
  expect_task_green ":$m clean tree" ":$m:verifyNoMlArtifacts"
done

for f in "${RESTORE[@]:-}"; do
  [ -z "$f" ] && continue
  if ! cmp -s "$f" "$BAK/$(key "$f")"; then echo "FAIL  [restore] $f differs from its backup"; fails=$((fails+1)); fi
done
if [ "$fails" -gt 0 ]; then echo "ML DENIAL CONTROLS FAIL failures=$fails"; exit 1; fi
echo "ML DENIAL CONTROLS OK plants=$plants"
