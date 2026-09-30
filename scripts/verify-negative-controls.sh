#!/usr/bin/env bash
# Phase gate (NOT part of `check`): prove that every gate goes RED, and for the RIGHT reason, when its constraint is violated.
#   Part 1  source plants: one construct per banned rule in each published module (base text tested on the research prototype)
#   Part 2  build-file plants: each structural gate, with backup + restore of the touched build file
#   Part 3  matrix guard: a wrong expected OkHttp version must fail each leg
# Every plant is removed on exit (trap); the script then asserts the touched build files are byte-identical to their backups.
# Never commit a plant. Run:  scripts/verify-negative-controls.sh   (a few minutes warm)
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
PLANTS=(); RESTORE=(); LOG="$(mktemp)"; BAK="$(mktemp -d)"
key() { echo "${1//\//_}"; }
cleanup() {
  for f in "${PLANTS[@]:-}"; do [ -n "$f" ] && rm -f "$f"; done
  for f in "${RESTORE[@]:-}"; do if [ -n "$f" ] && [ -f "$BAK/$(key "$f")" ]; then cp "$BAK/$(key "$f")" "$f"; fi; done
  rm -f config/detekt-baseline.xml "$LOG"
}
trap cleanup EXIT
GRADLEW=./gradlew; fails=0

backup()  { cp "$1" "$BAK/$(key "$1")"; RESTORE+=("$1"); }
restore() { cp "$BAK/$(key "$1")" "$1"; }

expect_task_red() { # <label> <marker> <gradle args...>
  local label=$1 marker=$2 quiet="-q"; shift 2
  if [ "${VERBOSE:-0}" = "1" ]; then quiet=""; fi   # test stdout is only printed without -q
  if $GRADLEW $quiet "$@" >"$LOG" 2>&1; then
    echo "FAIL  [$label] stayed GREEN"; fails=$((fails+1))
  elif ! grep -qF -- "$marker" "$LOG"; then
    echo "FAIL  [$label] went red for the WRONG reason (missing '$marker')"; fails=$((fails+1))
  else
    echo "ok    [$label] went red ($marker)"
  fi
}

expect_red() { # <label> <module> <kotlin-body> <gradle-task>...   (source plant in <module>/src/main)
  local label=$1 mod=$2 body=$3; shift 3
  local dir="$mod/src/main/kotlin/io/github/ygaray/voiceactionengine/$mod"
  local f="$dir/ZzPlant.kt"; PLANTS+=("$f")
  printf 'package io.github.ygaray.voiceactionengine.%s\n\n%s\n' "$mod" "$body" > "$f"
  for task in "$@"; do
    local marker; case "$task" in
      *compile*Kotlin) marker="Visibility must be specified in explicit API mode" ;;
      *:detekt)        marker="weighted issues" ;;
      *scanBanned*)    marker="Banned constructs" ;;
      *)               marker="FAILED" ;;
    esac
    expect_task_red "$label" "$marker" "$task"
  done
  rm -f "$f"
}

echo "== Part 1: source plants"
for m in core providers keystore; do
  compile=":$m:compileKotlin"; [ "$m" = keystore ] && compile=":$m:compileReleaseKotlin"
  expect_red "public without modifier ($m)"  $m 'class NoVisibility(val x: Int)'                          "$compile"
  expect_red "DI import ($m)"                $m 'import javax.inject.Inject'                              ":$m:detekt" ":$m:scanBannedConstructs"
  expect_red "okhttp internal import ($m)"   $m 'import okhttp3.internal.Util'                            ":$m:detekt" ":$m:scanBannedConstructs"
  expect_red "android.util.Log import ($m)"  $m 'import android.util.Log'                                 ":$m:detekt" ":$m:scanBannedConstructs"
  expect_red "mockwebserver3 import ($m)"    $m 'import mockwebserver3.MockResponse'                      ":$m:detekt" ":$m:scanBannedConstructs"
  expect_red "runCatching ($m)"              $m 'internal fun p() = runCatching { 1 }'                    ":$m:scanBannedConstructs"
  expect_red "println ($m)"                  $m 'internal fun p() { println("x") }'                       ":$m:scanBannedConstructs"
  expect_red "runCatching in string template ($m)" $m 'internal fun p() = "${runCatching { 1 }}"'          ":$m:scanBannedConstructs"
  expect_red "FQ kotlin.io.println ($m)"     $m 'internal fun p() { kotlin.io.println("x") }'             ":$m:scanBannedConstructs"
  expect_red "printStackTrace ($m)"         $m 'internal fun p() { Exception("m").printStackTrace() }'   ":$m:scanBannedConstructs"
  expect_red "FQ DI annotation ($m)"         $m '@javax.inject.Inject internal class P'                   ":$m:scanBannedConstructs"
  expect_red "planning id comment ($m)"      $m '// T-01-02 leaked'                                       ":$m:detekt" ":$m:scanBannedConstructs"
done
touch config/detekt-baseline.xml
expect_task_red "baseline file" "detekt baseline is forbidden" :core:verifyNoDetektBaseline
rm -f config/detekt-baseline.xml

for m in core providers keystore; do
  expect_task_red "api.txt missing once released ($m)" "api.txt is missing" ":$m:verifyApiDumpPresent" -PvaeAssumeReleased
done

echo "== Part 2: build-file plants (backup + restore)"
backup core/build.gradle.kts
printf '\ndependencies { api("com.squareup.okhttp3:okhttp:4.12.0") }\n' >> core/build.gradle.kts
expect_task_red "core gains an HTTP dependency" "non-allow-listed" :core:verifyCoreDependencyAllowlist
restore core/build.gradle.kts

backup providers/build.gradle.kts
printf '\ndependencies { implementation(project(":keystore")) }\n' >> providers/build.gradle.kts
expect_task_red "forbidden project edge providers -> keystore" "forbidden project dependencies" :providers:verifyModuleGraph
restore providers/build.gradle.kts

printf '\ndependencies { implementation("javax.inject:javax.inject:1") }\n' >> providers/build.gradle.kts
expect_task_red "DI artifact on the providers classpath" "resolves DI artifacts" :providers:verifyNoDiArtifacts
restore providers/build.gradle.kts

sed -i 's/explicitApi()/ /' providers/build.gradle.kts
expect_task_red "explicitApi switched off" "expected Strict" :providers:verifyExplicitApiStrict
restore providers/build.gradle.kts

backup gradle/libs.versions.toml
sed -i 's/^okhttp = "4.12.0"/okhttp = "5.2.1"/' gradle/libs.versions.toml
expect_task_red "OkHttp compile floor raised" "must be 4.12.0" :providers:verifyOkHttpCompileFloor
restore gradle/libs.versions.toml

sed -i 's/VERSION_11/VERSION_17/g; s/JVM_11/JVM_17/g' core/build.gradle.kts
expect_task_red "core compiled at JVM 17" "Non-JVM-11 class files" :core:verifyBytecodeLevel
restore core/build.gradle.kts

sed -i '/testFixtures\(Api\|Runtime\)Elements/d' core/build.gradle.kts
expect_task_red "testFixtures leak into the published component" "testFixtures leaked" :core:verifyNoTestFixturesPublished
restore core/build.gradle.kts

echo "== Part 3: matrix guard rejects a wrong expected OkHttp version (the marker also proves which version each leg really ran)"
VERBOSE=1 expect_task_red "guard, 4.12.0 leg" "OKHTTP_RUNTIME=4.12.0 expected=9.9.9" :providers:cleanTest :providers:test -PvaeExpectedOkhttp=9.9.9
VERBOSE=1 expect_task_red "guard, 5.2.1 leg"  "OKHTTP_RUNTIME=5.2.1 expected=9.9.9"  :providers:cleanTestOkhttp521 :providers:testOkhttp521 -PvaeExpectedOkhttp=9.9.9
VERBOSE=1 expect_task_red "guard, 5.5.0 leg"  "OKHTTP_RUNTIME=5.5.0 expected=9.9.9"  :providers:cleanTestOkhttp550 :providers:testOkhttp550 -PvaeExpectedOkhttp=9.9.9

for f in "${RESTORE[@]}"; do
  if ! cmp -s "$f" "$BAK/$(key "$f")"; then echo "FAIL  [restore] $f differs from its backup"; fails=$((fails+1)); fi
done
echo "negative-control failures: $fails"; exit $((fails>0))
