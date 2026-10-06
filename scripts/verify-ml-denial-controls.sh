#!/usr/bin/env bash
# Phase gate (NOT part of `check`): prove that every ML-denial gate (SC4, D-09) goes RED, and for the RIGHT reason, when an
# on-device ML runtime or private spike artifact is planted, and GREEN on the clean tree.
#   Part A  an ML dependency declared on any published module (scripts/modules.list) trips verifyNoMlArtifacts
#   Part B  an ML token in :core main code trips the NoHardCodedConstantsTest on-device scan
#   Part C  a model file, a private gold-label file and the private spike fixture file (temporary-index plants, the real index is never touched) and a
#           jitpack.yml line naming the spike module trip scripts/verify-repo-hygiene.sh
# Every plant is removed on exit (trap); the script then asserts the touched build files are byte-identical to their backups.
# Never commit a plant. Run:  scripts/verify-ml-denial-controls.sh   (a few minutes warm)
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
# shellcheck source=lib/modules.sh
. scripts/lib/modules.sh || { echo "ML DENIAL CONTROLS FAIL: cannot load scripts/lib/modules.sh" >&2; exit 1; }
MODULES="$(vae_modules)" || { echo "ML DENIAL CONTROLS FAIL: module manifest unreadable" >&2; exit 1; }
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
for m in $MODULES; do
  backup "$m/build.gradle.kts"
  printf '\ndependencies { implementation("com.google.ai.edge.litertlm:litertlm-android:0.17.1") }\n' >> "$m/build.gradle.kts"
  expect_task_red "$m gains an ML dependency" "resolves ML artifacts" ":$m:verifyNoMlArtifacts"
  restore "$m/build.gradle.kts"
  expect_task_green ":$m clean tree" ":$m:verifyNoMlArtifacts"
done

echo "== Part B: an ML token in :core main code trips the on-device scan"
plant="core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/ZzMlPlant.kt"; PLANTS+=("$plant")
printf 'package io.github.ygaray.voiceactionengine.core\n\ninternal const val ZZ_ML_PLANT: String = "litertlm"\n' > "$plant"
VERBOSE=1 expect_task_red ":core on-device scan sees an ML token" "noOnDeviceImplementationCode FAILED" :core:test --tests '*NoHardCodedConstantsTest*'
rm -f "$plant"

echo "== Part C: model weights, private gold labels and the spike module in jitpack.yml trip repo hygiene"
expect_hygiene_red() { # <label> <marker>   (runs scripts/verify-repo-hygiene.sh with the caller's environment)
  local label=$1 marker=$2
  plants=$((plants+1))
  if scripts/verify-repo-hygiene.sh >"$LOG" 2>&1; then
    echo "FAIL  [$label] stayed GREEN"; fails=$((fails+1))
  elif ! grep -qF -- "$marker" "$LOG"; then
    echo "FAIL  [$label] went red for the WRONG reason (missing '$marker')"; fails=$((fails+1))
  else
    echo "ok    [$label] went red ($marker)"
  fi
}
if ! scripts/verify-repo-hygiene.sh >"$LOG" 2>&1 || ! grep -qx 'HYGIENE OK' "$LOG"; then
  echo "FAIL  [hygiene clean tree] INCONCLUSIVE: verify-repo-hygiene.sh is not HYGIENE OK before any plant"; fails=$((fails+1))
else
  echo "ok    [hygiene clean tree] HYGIENE OK"
  # Model-file plant: force-added into a TEMPORARY copy of the index; the real index is never touched.
  real_index="$(git rev-parse --git-path index)"
  tmp_index="$(mktemp)"; PLANTS+=("$tmp_index")
  cp "$real_index" "$tmp_index"
  for plant in zz-plant.litertlm zz-plant-sb-gold.json zz-plant-sb-fixture.json; do
    PLANTS+=("$plant"); : > "$plant"
    GIT_INDEX_FILE="$tmp_index" git add -f -- "$plant"
    GIT_INDEX_FILE="$tmp_index" expect_hygiene_red "hygiene sees $plant" "c: forbidden file(s) present"
    rm -f "$plant"
    GIT_INDEX_FILE="$tmp_index" git rm -q --cached -- "$plant"
  done
  rm -f "$tmp_index"
  # jitpack plant: an install line naming the spike module.
  backup jitpack.yml
  printf '  - ./gradlew :spike-ondevice:assembleDebug\n' >> jitpack.yml
  expect_hygiene_red "jitpack names the spike module" "f: jitpack.yml names the spike module"
  restore jitpack.yml
fi

for f in "${RESTORE[@]:-}"; do
  [ -z "$f" ] && continue
  if ! cmp -s "$f" "$BAK/$(key "$f")"; then echo "FAIL  [restore] $f differs from its backup"; fails=$((fails+1)); fi
done
if [ "$fails" -gt 0 ]; then echo "ML DENIAL CONTROLS FAIL failures=$fails"; exit 1; fi
echo "ML DENIAL CONTROLS OK plants=$plants"
