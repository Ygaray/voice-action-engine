#!/usr/bin/env bash
# Phase gate (NOT part of `check`): prove that every gate goes RED, and for the RIGHT reason, when its constraint is violated.
#   Part 1  source plants: one construct per banned rule in each published module listed in scripts/modules.list (base text
#           tested on the research prototype), plus the api.txt-missing control for each of them
#   Part 2  build-file plants: each structural gate, with backup + restore of the touched build file; includes the three
#           :undo zero-dependency plants (a project edge to :core, a library dependency, core testFixtures on its test classpath)
#   Part 3  matrix guard: a wrong expected OkHttp version must fail each leg
#   Part 4  opt-in seam: KeyAccess and its ApiKeyStore constructor need @OptIn from another module (scripts/verify-keyaccess-opt-in.sh)
#   Part 5  ML denial (D-09): scripts/verify-ml-denial-controls.sh
# Every plant is removed on exit (trap); the script then asserts the touched build files are byte-identical to their backups.
# Never commit a plant. Run:  scripts/verify-negative-controls.sh   (a few minutes warm)
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
# shellcheck source=lib/modules.sh
. scripts/lib/modules.sh || { echo "NEGATIVE CONTROLS FAIL: cannot load scripts/lib/modules.sh" >&2; exit 1; }
MODULES="$(vae_modules)" || { echo "NEGATIVE CONTROLS FAIL: module manifest unreadable" >&2; exit 1; }
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
  local pkg; pkg="$(vae_module_field "$mod" kotlinPackage)" || { echo "FAIL  [$label] no kotlinPackage for $mod"; fails=$((fails+1)); return; }
  local dir="$mod/src/main/kotlin/io/github/ygaray/voiceactionengine/$pkg"
  local f="$dir/ZzPlant.kt"; PLANTS+=("$f")
  printf 'package io.github.ygaray.voiceactionengine.%s\n\n%s\n' "$pkg" "$body" > "$f"
  for task in "$@"; do
    local marker; case "$task" in
      *compile*Kotlin) marker="Visibility must be specified in explicit API mode" ;;
      *:detekt)        marker="weighted issues" ;;
      *scanBanned*)    marker="Banned constructs" ;;
      *)               marker="FAILED" ;;
    esac
    expect_task_red "$label" "${MARKER:-$marker}" "$task"   # MARKER overrides the per-task default
  done
  rm -f "$f"
}

echo "== Part 1: source plants"
for m in $MODULES; do
  compile=":$m:compileKotlin"; [ "$(vae_module_field "$m" packaging)" = aar ] && compile=":$m:compileReleaseKotlin"
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
  expect_red "app-domain name ($m)"          $m 'internal const val P = "log_food"'                       ":$m:scanBannedConstructs"
  expect_red "hard-coded tool count ($m)"    $m 'internal fun p(tools: List<Any>) = tools.size == 18'     ":$m:scanBannedConstructs"
done
# CLN-02 also covers core's testFixtures (shipped in no artifact, but they must name no app domain either).
fx="core/src/testFixtures/kotlin/io/github/ygaray/voiceactionengine/core/testing/ZzPlant.kt"; PLANTS+=("$fx")
printf 'package io.github.ygaray.voiceactionengine.core.testing\n\ninternal const val P = "log_food"\n' > "$fx"
expect_task_red "app-domain name (core testFixtures)" "Banned constructs" :core:scanBannedConstructs
rm -f "$fx"
for m in $MODULES; do   # aar modules (:keystore) compile against android.jar, which legitimately carries newer JDK APIs
  [ "$(vae_module_field "$m" packaging)" = jar ] || continue
  MARKER="Unresolved reference" expect_red "JDK 16 API (Stream.toList) in a JVM-11 module ($m)" $m 'internal fun p(): List<Int> = java.util.stream.Stream.of(1).toList()' ":$m:compileKotlin"
done
MARKER="creates a DataStore" expect_red "DataStore creation in keystore main" keystore 'internal val p = androidx.datastore.preferences.core.PreferenceDataStoreFactory' ":keystore:verifyNoDataStoreCreation"
touch config/detekt-baseline.xml
expect_task_red "baseline file" "detekt baseline is forbidden" :core:verifyNoDetektBaseline
rm -f config/detekt-baseline.xml
printf '<?xml version="1.0"?>\n<SmellBaseline/>\n' > config/zz-suppressions.xml; PLANTS+=(config/zz-suppressions.xml)
expect_task_red "baseline file under an arbitrary name" "baseline file (by content)" :core:verifyNoDetektBaseline
rm -f config/zz-suppressions.xml
backup keystore/build.gradle.kts
printf '\n// planted wiring: baseline.set(file("zz.xml"))\n' >> keystore/build.gradle.kts
expect_task_red "baseline wired via property set" "baseline wiring" :core:verifyNoDetektBaseline
restore keystore/build.gradle.kts

# Every module tracks its api.txt since v1.0.0, so the control plants the absence itself and restores the file afterwards.
for m in $MODULES; do
  backup "$m/api.txt"
  rm -f "$m/api.txt"
  expect_task_red "api.txt missing once released ($m)" "api.txt is missing" ":$m:verifyApiDumpPresent" -PvaeAssumeReleased
  restore "$m/api.txt"
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

backup keystore/build.gradle.kts
sed -i 's/api(libs.datastore.prefs)/implementation(libs.datastore.prefs)/' keystore/build.gradle.kts
expect_task_red "datastore demoted from api" "is not an api dependency" :keystore:verifyDatastoreIsApi
restore keystore/build.gradle.kts

backup gradle/libs.versions.toml
sed -i 's/^okhttp = "4.12.0"/okhttp = "5.2.1"/' gradle/libs.versions.toml
expect_task_red "OkHttp compile floor raised" "must be 4.12.0" :providers:verifyOkHttpCompileFloor
restore gradle/libs.versions.toml

sed -i 's/VERSION_11/VERSION_17/g; s/JVM_11/JVM_17/g; s/jdk-release=11/jdk-release=17/g' core/build.gradle.kts
expect_task_red "core compiled at JVM 17" "Non-JVM-11 class files" :core:verifyBytecodeLevel
restore core/build.gradle.kts

sed -i '/testFixtures\(Api\|Runtime\)Elements/d' core/build.gradle.kts
expect_task_red "testFixtures leak into the published component" "testFixtures leaked" :core:verifyNoTestFixturesPublished
restore core/build.gradle.kts

# :undo promises nothing but the Kotlin standard library: three ways to break that, each caught by its own gate.
backup undo/build.gradle.kts
printf '\ndependencies { implementation(project(":core")) }\n' >> undo/build.gradle.kts
expect_task_red "forbidden project edge undo -> core" "forbidden project dependencies" :undo:verifyModuleGraph
restore undo/build.gradle.kts

printf '\ndependencies { implementation(libs.coroutines.core) }\n' >> undo/build.gradle.kts
expect_task_red "undo gains a library dependency" "depends on more than the Kotlin standard library" :undo:verifyUndoZeroDeps
restore undo/build.gradle.kts

printf '\ndependencies { testImplementation(testFixtures(project(":core"))) }\n' >> undo/build.gradle.kts
expect_task_red "undo test classpath gains core testFixtures" "forbidden project dependencies" :undo:verifyModuleGraph
restore undo/build.gradle.kts

echo "== Part 3: matrix guard rejects a wrong expected OkHttp version (the marker also proves which version each leg really ran)"
VERBOSE=1 expect_task_red "guard, 4.12.0 leg" "OKHTTP_RUNTIME=4.12.0 expected=9.9.9" :providers:cleanTest :providers:test -PvaeExpectedOkhttp=9.9.9
VERBOSE=1 expect_task_red "guard, 5.2.1 leg"  "OKHTTP_RUNTIME=5.2.1 expected=9.9.9"  :providers:cleanTestOkhttp521 :providers:testOkhttp521 -PvaeExpectedOkhttp=9.9.9
VERBOSE=1 expect_task_red "guard, 5.5.0 leg"  "OKHTTP_RUNTIME=5.5.0 expected=9.9.9"  :providers:cleanTestOkhttp550 :providers:testOkhttp550 -PvaeExpectedOkhttp=9.9.9

echo "== Part 4: opt-in seam (cross-module negative-compile proof with a positive control)"
if scripts/verify-keyaccess-opt-in.sh; then echo "ok    [keyaccess opt-in]"; else echo "FAIL  [keyaccess opt-in]"; fails=$((fails+1)); fi

echo "== Part 5: ML denial (D-09: dependency, :core token, hygiene and jitpack plants)"
if scripts/verify-ml-denial-controls.sh; then echo "ok    [ml denial controls]"; else echo "FAIL  [ml denial controls]"; fails=$((fails+1)); fi

for f in "${RESTORE[@]}"; do
  if ! cmp -s "$f" "$BAK/$(key "$f")"; then echo "FAIL  [restore] $f differs from its backup"; fails=$((fails+1)); fi
done
echo "negative-control failures: $fails"; exit $((fails>0))
