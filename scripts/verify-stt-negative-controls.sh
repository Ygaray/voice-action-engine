#!/usr/bin/env bash
# Phase gate (NOT part of `check`): prove that the :stt gates go RED, and for the RIGHT reason, when the speech engine
# leaks, and GREEN on the clean tree.
#   Part A  clean-tree controls: the adapter publication gate and the confinement gate stay green
#   Part B  plants, each restored right after its check:
#           1  the adapter's compileOnly turned into a runtime-visible configuration trips verifyAdapterSttCompileOnly
#           2  the speech engine added to :keystore (an AAR module, resolves) trips verifySttConfined
#           3  the speech engine added to :core (a JVM module, the AAR variant cannot match, so only the requested
#              selector shows the group) trips verifySttConfined
#           4  a project edge from :providers to :voice-adapter trips verifyModuleGraph
# Every plant is restored on exit (trap); the script then asserts the touched build files are byte-identical to their backups.
# Never commit a plant. Run:  scripts/verify-stt-negative-controls.sh   (a few minutes warm)
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
RESTORE=(); LOG="$(mktemp)"; BAK="$(mktemp -d)"
key() { echo "${1//\//_}"; }
cleanup() {
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
GRADLE_FLAGS=(--offline --max-workers=2 -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false)

backup()  { cp "$1" "$BAK/$(key "$1")"; RESTORE+=("$1"); }
restore() { cp "$BAK/$(key "$1")" "$1"; }

expect_task_red() { # <label> <marker> <gradle args...>
  local label=$1 marker=$2; shift 2
  plants=$((plants+1))
  if $GRADLEW -q "${GRADLE_FLAGS[@]}" "$@" >"$LOG" 2>&1; then
    echo "FAIL  [$label] stayed GREEN"; fails=$((fails+1))
  elif ! grep -qF -- "$marker" "$LOG"; then
    echo "FAIL  [$label] went red for the WRONG reason (missing '$marker')"; fails=$((fails+1))
  else
    echo "ok    [$label] went red ($marker)"
  fi
}

expect_task_green() { # <label> <gradle args...>
  local label=$1; shift
  plants=$((plants+1))
  if $GRADLEW -q "${GRADLE_FLAGS[@]}" "$@" >"$LOG" 2>&1; then
    echo "ok    [$label] stayed green"
  else
    echo "FAIL  [$label] went RED on the clean tree"; fails=$((fails+1))
  fi
}

echo "== Part A: clean-tree controls"
expect_task_green ":voice-adapter publication gate clean tree" :voice-adapter:verifyAdapterSttCompileOnly
expect_task_green ":core confinement gate clean tree" :core:verifySttConfined
expect_task_green ":keystore confinement gate clean tree" :keystore:verifySttConfined

echo "== Part B: plants"
# 1. The adapter's compileOnly becomes a runtime-visible configuration (a one-line sed; a plant that changed nothing is a failure).
backup voice-adapter/build.gradle.kts
sed -i 's/^\(\s*\)compileOnly(libs\.stt\.engine)/\1implementation(libs.stt.engine)/' voice-adapter/build.gradle.kts
if cmp -s voice-adapter/build.gradle.kts "$BAK/$(key voice-adapter/build.gradle.kts)"; then
  echo "FAIL  [adapter plant] the sed changed nothing (compileOnly line not found)"; fails=$((fails+1))
else
  expect_task_red "adapter publishes the speech engine" "must keep :stt compileOnly" :voice-adapter:verifyAdapterSttCompileOnly
fi
restore voice-adapter/build.gradle.kts

# 2. The speech engine on :keystore (an AAR module: the component resolves).
backup keystore/build.gradle.kts
printf '\ndependencies { implementation(libs.stt.engine) }\n' >> keystore/build.gradle.kts
expect_task_red ":keystore gains the speech engine" "resolves the :stt group" :keystore:verifySttConfined
restore keystore/build.gradle.kts

# 3. The speech engine on :core (a JVM module: the AAR variant cannot match, only the requested selector reveals the group).
backup core/build.gradle.kts
printf '\ndependencies { implementation(libs.stt.engine) }\n' >> core/build.gradle.kts
expect_task_red ":core gains the speech engine" "resolves the :stt group" :core:verifySttConfined
restore core/build.gradle.kts

# 4. A project edge to the adapter from a module that must not have one.
backup providers/build.gradle.kts
printf '\ndependencies { implementation(project(":voice-adapter")) }\n' >> providers/build.gradle.kts
expect_task_red ":providers gains the adapter" "forbidden project dependencies" :providers:verifyModuleGraph
restore providers/build.gradle.kts

for f in "${RESTORE[@]:-}"; do
  [ -z "$f" ] && continue
  if ! cmp -s "$f" "$BAK/$(key "$f")"; then echo "FAIL  [restore] $f differs from its backup"; fails=$((fails+1)); fi
done
if [ "$fails" -gt 0 ]; then echo "STT NEGATIVE CONTROLS FAIL failures=$fails"; exit 1; fi
echo "STT NEGATIVE CONTROLS OK plants=$plants"
