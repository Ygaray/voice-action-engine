#!/usr/bin/env bash
# Agent wiring test (D-07, VER-04): can a FRESH agent wire the engine from README.md + INTEGRATION.md + API.md alone?
#   prepare <sha>            build the scratch-consumer workspace for a pushed, JitPack-built commit
#                            -> WIRING PREPARED dir=<dir> version=<sha10>
#   verify <dir> <version>   judge the agent's work mechanically, from an EMPTY Gradle cache against the repository
#                            in <dir>/settings.gradle.kts (JitPack) -> WIRING TEST: PASS checks=<n>
#                            or one "WIRING TEST: FAIL <id>: <why>" line per failed check (exit 1)
#   selftest                 prove the judge is not vacuous: an isolated local publication (the JitPack dry run, clean
#                            clone of HEAD), the committed reference solution must PASS and a planted bad copy must
#                            FAIL on W4, W5 and W12 -> WIRING SELFTEST OK | WIRING SELFTEST FAIL: <why>
#   selftest --local         the same proof, cheaper: publishes the WORKING TREE with the jitpack.yml install list into an
#                            isolated maven-local (--offline, host Gradle cache), no dry run and no clean clone
#   prepare-local <m2dir> <version>
#                            build the workspace against a file:// repository (the unpushed tree's local publication)
#                            with the docs of HEAD -> WIRING PREPARED dir=<dir> version=<version>
# The workspace holds the three docs, a skeleton derived from scripts/jitpack-consumer-probe.sh and TASK.md. It never
# holds engine or sample source, the planning tree, the reference solution or any key. The dispatch of the fresh agent
# is the master's job (an executor has no Agent tool): see .planning/releases/v1.1.0/wiring-test/DISPATCH.md.
# Env: WIRING_DIR=<dir> overrides the prepare target; REPO_URL overrides https://jitpack.io (prepare);
#      VAE_WIRING_ASSET_DIR=<dir> overrides the prompt + reference-solution directory;
#      WIRING_HOST_CACHE=1 makes verify reuse the host Gradle cache with --offline (selftest only, never for the real run).
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
# Stable, non-phase location: it survives the milestone archive (the Phase 10 copies stay in the archive untouched).
ASSET_DIR="${VAE_WIRING_ASSET_DIR:-$ROOT/.planning/releases/v1.1.0/wiring-test}"
PROMPT="$ASSET_DIR/AGENT-PROMPT.md"
REFERENCE="$ASSET_DIR/reference"
# W3 accepts every module of the manifest. VAE_MODULES_FILE is set explicitly so the reader never needs git.
VAE_MODULES_FILE="${VAE_MODULES_FILE:-$ROOT/scripts/modules.list}"; export VAE_MODULES_FILE
# shellcheck source=lib/modules.sh
. "$ROOT/scripts/lib/modules.sh"
MODULE_ALT="$(vae_modules | tr ' ' '|')"
GROUP_DEFAULT="$(grep -E '^engineGroup=' "$ROOT/gradle.properties" | cut -d= -f2)"
GROUP="${GROUP:-$GROUP_DEFAULT}"
DOC_FILES="README.md INTEGRATION.md API.md ECOSYSTEM.md"

# ---------------------------------------------------------------------------------------------------------------------
# make_workspace <dir> <version> <repo_url> <docs_rev>
# ---------------------------------------------------------------------------------------------------------------------
make_workspace() {
  local dir="$1" version="$2" repo_url="$3" rev="$4" f
  [ ! -e "$dir" ] || { echo "WIRING PREPARE FAIL: $dir already exists" >&2; return 1; }
  mkdir -p "$dir/gradle" "$dir/docs" "$dir/jvmconsumer/src/main/kotlin/wire" "$dir/jvmconsumer/src/test/kotlin/wire" \
    "$dir/app/src/main/kotlin/wire"
  cp -r "$ROOT/gradle/wrapper" "$dir/gradle/"
  cp "$ROOT/gradlew" "$dir/gradlew"
  chmod +x "$dir/gradlew"
  printf 'sdk.dir=%s\n' "${ANDROID_HOME:-$HOME/Android/Sdk}" > "$dir/local.properties"
  printf 'android.useAndroidX=true\norg.gradle.jvmargs=-Xmx2g\n' > "$dir/gradle.properties"
  cat > "$dir/settings.gradle.kts" <<KTS
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral(); maven { url = uri("$repo_url") } }
}
rootProject.name = "wiring-test"
include(":app", ":jvmconsumer")
KTS
  cat > "$dir/build.gradle.kts" <<'KTS'
plugins {
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.jvm") version "2.3.20" apply false
}
KTS
  cat > "$dir/jvmconsumer/build.gradle.kts" <<'KTS'
plugins { id("org.jetbrains.kotlin.jvm") }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) } }
java { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}
KTS
  cat > "$dir/app/build.gradle.kts" <<'KTS'
plugins { id("com.android.application") }
android {
    namespace = "wire.app"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    defaultConfig { applicationId = "wire.app"; minSdk = 35; targetSdk = 36; versionCode = 1; versionName = "0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
}
KTS
  echo '<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application/></manifest>' \
    > "$dir/app/src/main/AndroidManifest.xml"
  for f in $DOC_FILES; do git -C "$ROOT" show "$rev:$f" > "$dir/docs/$f"; done
  sed "s/{{VERSION}}/$version/g" "$PROMPT" > "$dir/TASK.md"
}

# ---------------------------------------------------------------------------------------------------------------------
# verify <dir> <version>
# ---------------------------------------------------------------------------------------------------------------------
verify() {
  local dir="${1:?usage: verify <dir> <version>}" version="${2:?usage: verify <dir> <version>}"
  [ -d "$dir" ] || { echo "WIRING TEST: FAIL W0: no such directory $dir"; return 1; }
  dir="$(cd "$dir" && pwd)"
  local fails=() log gh="" gradle_args=(--no-daemon -q) rc=0
  log="$(mktemp)"
  if [ "${WIRING_HOST_CACHE:-0}" = 1 ]; then
    gradle_args+=(--offline)
  else
    # An EMPTY dependency cache, reusing only the Gradle DISTRIBUTION (as scripts/jitpack-consumer-probe.sh does).
    gh="$(mktemp -d)"
    if [ -d "$HOME/.gradle/wrapper" ]; then ln -s "$HOME/.gradle/wrapper" "$gh/wrapper"; fi
    export GRADLE_USER_HOME="$gh"
  fi
  # The empty Gradle home is hundreds of MB: removed before every return. The wrapper symlink is unlinked first so the
  # recursive remove can never reach the real ~/.gradle/wrapper.
  verify_cleanup() { rm -f "$log"; if [ -n "$gh" ]; then rm -f "$gh/wrapper"; rm -rf "$gh"; unset GRADLE_USER_HOME; fi; }

  # W1 build green
  (cd "$dir" && ./gradlew "${gradle_args[@]}" :jvmconsumer:test :app:compileDebugKotlin) >"$log" 2>&1 || rc=$?
  if [ "$rc" -ne 0 ]; then
    fails+=("W1: gradle build failed (exit $rc): $(grep -m3 -E '^e: |error:|FAILED|What went wrong|Could not' "$log" | tr '\n' ' ' | cut -c1-400 || true)")
  fi

  # W2 tests >= 6 (the v1.1 task asks for six), no failures
  local tests=0 failed=0
  read -r tests failed < <(cat "$dir"/jvmconsumer/build/test-results/test/*.xml 2>/dev/null | grep -o '<testsuite [^>]*' \
    | awk '{ for (i = 1; i <= NF; i++) {
               if ($i ~ /^tests=/) { gsub(/[^0-9]/, "", $i); t += $i }
               if ($i ~ /^(failures|errors)=/) { gsub(/[^0-9]/, "", $i); f += $i } } }
           END { print t + 0, f + 0 }')
  { [ "$tests" -ge 6 ] && [ "$failed" -eq 0 ]; } || fails+=("W2: test results show tests=$tests failures+errors=$failed (need tests>=6, failures+errors=0)")

  # W3 per-module coordinates at the given version only; jvmconsumer has core or providers, app has keystore
  local bad_dep="" line mod ver jvm_ok=0 app_ok=0
  local w3_re="\"com\.github\.Ygaray\.voice-action-engine:voice-action-engine-(${MODULE_ALT}):([^\"]*)\""
  while IFS= read -r line; do
    if [[ "$line" =~ $w3_re ]]; then
      mod="${BASH_REMATCH[1]}"; ver="${BASH_REMATCH[2]}"
      [ "$ver" = "$version" ] || bad_dep="$bad_dep [$mod has version $ver]"
    else
      bad_dep="$bad_dep [${line:0:100}]"
    fi
  done < <(grep -h 'voice-action-engine' "$dir/jvmconsumer/build.gradle.kts" "$dir/app/build.gradle.kts" 2>/dev/null | grep -v '^[[:space:]]*//' || true)
  grep -qE "voice-action-engine-(core|providers):$version\"" "$dir/jvmconsumer/build.gradle.kts" 2>/dev/null && jvm_ok=1
  grep -qE "voice-action-engine-keystore:$version\"" "$dir/app/build.gradle.kts" 2>/dev/null && app_ok=1
  [ -z "$bad_dep" ] || fails+=("W3: engine dependency lines that are not per-module coordinates at version $version:$bad_dep")
  [ "$jvm_ok" = 1 ] || fails+=("W3: jvmconsumer/build.gradle.kts does not declare voice-action-engine-core or -providers at $version")
  [ "$app_ok" = 1 ] || fails+=("W3: app/build.gradle.kts does not declare voice-action-engine-keystore at $version")

  # W4 no aggregator coordinate anywhere in the build files
  if grep -rqE --include='*.kts' --include='*.gradle' --include='*.toml' 'com\.github\.Ygaray:voice-action-engine:' \
      "$dir/build.gradle.kts" "$dir/settings.gradle.kts" "$dir/jvmconsumer" "$dir/app" 2>/dev/null; then
    fails+=("W4: aggregator coordinate com.github.Ygaray:voice-action-engine: used (per-module coordinates only)")
  fi

  # W5 / W6 in the jvmconsumer main source
  local main_src="$dir/jvmconsumer/src/main"
  if ! { [ -d "$main_src" ] && grep -rqE --include='*.kt' 'else[[:space:]]*->' "$main_src" && grep -rqwE --include='*.kt' 'when' "$main_src"; }; then
    fails+=("W5: no 'when' with an 'else ->' branch in jvmconsumer main source")
  fi
  if ! { [ -d "$main_src" ] && grep -rqw --include='*.kt' 'partial' "$main_src"; }; then
    fails+=("W6: 'partial' is not referenced in jvmconsumer main source")
  fi

  # W10 grammar tier, W11 plan tier, W12 router selector, W13 undo journal + undoAll (+ the undo dependency)
  if ! { [ -d "$main_src" ] && grep -rqw --include='*.kt' 'GrammarPack' "$main_src" && grep -rqw --include='*.kt' 'LocalGrammarStrategy' "$main_src"; }; then
    fails+=("W10: GrammarPack and LocalGrammarStrategy are not both referenced in jvmconsumer main source")
  fi
  if ! { [ -d "$main_src" ] && grep -rqw --include='*.kt' 'PlanThenExecuteStrategy' "$main_src"; }; then
    fails+=("W11: PlanThenExecuteStrategy is not referenced in jvmconsumer main source")
  fi
  if ! { [ -d "$main_src" ] && grep -rqF --include='*.kt' 'TierSelector.Router' "$main_src"; }; then
    fails+=("W12: TierSelector.Router is not referenced in jvmconsumer main source")
  fi
  if ! { [ -d "$main_src" ] && grep -rqw --include='*.kt' 'UndoJournal' "$main_src" && grep -rqw --include='*.kt' 'undoAll' "$main_src" \
      && grep -qF "voice-action-engine-undo:$version\"" "$dir/jvmconsumer/build.gradle.kts" 2>/dev/null; }; then
    fails+=("W13: UndoJournal and undoAll in jvmconsumer main source plus a voice-action-engine-undo:$version dependency are required")
  fi

  # W7 no engine internals, no :core test fixtures; W8 no FakeAiProvider
  if grep -rqE --include='*.kt' 'import[[:space:]]+io\.github\.ygaray\.voiceactionengine\.([A-Za-z0-9_.]*\.)?internal([.;[:space:]]|$)|import[[:space:]]+io\.github\.ygaray\.voiceactionengine\.core\.testing' \
      "$dir/jvmconsumer/src" "$dir/app/src" 2>/dev/null; then
    fails+=("W7: imports an engine internal package or the :core test fixtures")
  fi
  if grep -rqw --include='*.kt' 'FakeAiProvider' "$dir/jvmconsumer/src" "$dir/app/src" 2>/dev/null; then
    fails+=("W8: FakeAiProvider is referenced (the engine's fake is not published; write your own)")
  fi

  # W9 the agent's reports exist
  if [ ! -f "$dir/STUMBLES.md" ] || [ ! -f "$dir/CONSULTED.md" ]; then
    fails+=("W9: STUMBLES.md and CONSULTED.md must both exist")
  fi

  verify_cleanup
  if [ "${#fails[@]}" -eq 0 ]; then
    echo "WIRING TEST: PASS checks=13"
    return 0
  fi
  local f
  for f in "${fails[@]}"; do echo "WIRING TEST: FAIL $f"; done
  return 1
}

# ---------------------------------------------------------------------------------------------------------------------
# prepare <sha>
# ---------------------------------------------------------------------------------------------------------------------
prepare() {
  local sha="${1:?usage: prepare <sha>}" sha10 dir
  git -C "$ROOT" cat-file -e "$sha^{commit}" 2>/dev/null || { echo "WIRING PREPARE FAIL: commit $sha does not exist locally" >&2; return 1; }
  sha10="$(git -C "$ROOT" rev-parse --short=10 "$sha")"
  git -C "$ROOT" fetch origin main >/dev/null 2>&1 || { echo "WIRING PREPARE FAIL: git fetch origin main failed" >&2; return 1; }
  git -C "$ROOT" merge-base --is-ancestor "$sha" origin/main \
    || { echo "WIRING PREPARE FAIL: not pushed ($sha10 is not an ancestor of origin/main)" >&2; return 1; }
  "$ROOT/scripts/jitpack-live-probe.sh" "$sha10" >/dev/null 2>&1 \
    || { echo "WIRING PREPARE FAIL: jitpack (scripts/jitpack-live-probe.sh $sha10 did not pass)" >&2; return 1; }
  dir="${WIRING_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/vae-wiring-test/$sha10}"
  [ ! -e "$dir" ] || { echo "WIRING PREPARE FAIL: $dir already exists (remove it or set WIRING_DIR)" >&2; return 1; }
  make_workspace "$dir" "$sha10" "${REPO_URL:-https://jitpack.io}" "$sha"
  echo "WIRING PREPARED dir=$dir version=$sha10"
}

# ---------------------------------------------------------------------------------------------------------------------
# prepare-local <m2dir> <version>: the unpushed tree's local publication (main stays unpushed through Phase 19)
# ---------------------------------------------------------------------------------------------------------------------
prepare_local() {
  local m2="${1:?usage: prepare-local <m2dir> <version>}" version="${2:?usage: prepare-local <m2dir> <version>}" dir
  [ -d "$m2" ] || { echo "WIRING PREPARE FAIL: $m2 is not a directory (publish the tree into an isolated maven-local first)" >&2; return 1; }
  m2="$(cd "$m2" && pwd)"
  dir="${WIRING_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/vae-wiring-test/local-$version}"
  [ ! -e "$dir" ] || { echo "WIRING PREPARE FAIL: $dir already exists (remove it or set WIRING_DIR)" >&2; return 1; }
  make_workspace "$dir" "$version" "file://$m2" "HEAD"
  echo "WIRING PREPARED dir=$dir version=$version"
}

# ---------------------------------------------------------------------------------------------------------------------
# selftest
# ---------------------------------------------------------------------------------------------------------------------
selftest() {
  local local_mode=0 dry_log m2 version ws bad out rc=0 d
  if [ "${1:-}" = "--local" ]; then local_mode=1; elif [ -n "${1:-}" ]; then echo "usage: $0 selftest [--local]" >&2; return 2; fi
  SELFTEST_TMP="$(mktemp -d)"
  dry_log="$SELFTEST_TMP/dry-run.log"
  mkdir -p "$SELFTEST_TMP/dry"
  # Every work directory the dry run and the consumer probe create (KEEP_WORK=1 keeps their clone, isolated maven-local
  # and the probe's Gradle home) is created through mktemp, so pointing TMPDIR into SELFTEST_TMP puts them all under one
  # root that the EXIT trap removes (also when the dry run dies half way). The wrapper symlinks are unlinked first so the
  # remove can never reach the real ~/.gradle/wrapper.
  cleanup_selftest() {
    [ -n "${SELFTEST_TMP:-}" ] && [ -d "$SELFTEST_TMP" ] || return 0
    find "$SELFTEST_TMP" -type l -name wrapper -delete 2>/dev/null || true
    rm -rf "$SELFTEST_TMP"
  }
  trap cleanup_selftest EXIT
  sf() { echo "WIRING SELFTEST FAIL: $*"; exit 1; }

  if [ "$local_mode" = 1 ]; then
    # The working tree, the jitpack.yml install list (same awk as scripts/jitpack-dry-run.sh), an isolated maven-local,
    # --offline on the host Gradle cache. Nothing is cloned and no dry run runs.
    local c cmds=()
    [ -f "$ROOT/jitpack.yml" ] || sf "no jitpack.yml in $ROOT"
    mapfile -t cmds < <(awk '/^install:/{f=1;next} f&&/^[^[:space:]#-]/{f=0} f&&/^[[:space:]]*-[[:space:]]/{sub(/^[[:space:]]*-[[:space:]]*/,"");print}' "$ROOT/jitpack.yml")
    [ "${#cmds[@]}" -gt 0 ] || sf "no install commands found in jitpack.yml"
    m2="$SELFTEST_TMP/m2/repository"; mkdir -p "$m2"
    version="local-$(git -C "$ROOT" rev-parse --short=10 HEAD)"
    for c in "${cmds[@]}"; do
      case "$c" in *":sample"*) sf "jitpack.yml install list names :sample: $c";; esac
      echo ">>> $c --offline -Dmaven.repo.local=$m2"
      (cd "$ROOT" && VERSION="$version" bash -c "$c --offline -Dmaven.repo.local=$m2" >"$dry_log" 2>&1) \
        || { tail -20 "$dry_log" >&2; sf "local publication failed"; }
    done
    [ -n "$(find "$m2" -name "voice-action-engine-core-$version.jar" -print -quit)" ] || sf "the local publication holds no core artifact for $version"
  else
    TMPDIR="$SELFTEST_TMP/dry" KEEP_WORK=1 "$ROOT/scripts/jitpack-dry-run.sh" >"$dry_log" 2>&1 || { tail -20 "$dry_log" >&2; sf "jitpack dry run failed"; }
    m2="$(grep -oE 'm2=[^ ]+ \(kept\)' "$dry_log" | tail -1 | sed -E 's/^m2=//; s/ \(kept\)$//')"
    version="$(grep -oE 'DRY RUN OK version=[^ ]+' "$dry_log" | tail -1 | sed 's/.*version=//')"
    [ -d "$m2" ] && [ -n "$version" ] || sf "could not parse m2/version from the dry run output"
  fi

  ws="$SELFTEST_TMP/ws"
  make_workspace "$ws" "$version" "file://$m2" "HEAD" || sf "make_workspace failed"
  # What the agent adds: the per-module dependencies, its own source files, its two reports.
  cat >> "$ws/jvmconsumer/build.gradle.kts" <<KTS
dependencies {
    implementation("$GROUP:voice-action-engine-core:$version")
    implementation("$GROUP:voice-action-engine-providers:$version")
    implementation("$GROUP:voice-action-engine-undo:$version")
}
KTS
  cat >> "$ws/app/build.gradle.kts" <<KTS
dependencies {
    implementation("$GROUP:voice-action-engine-keystore:$version")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
}
KTS
  cp "$REFERENCE/Wire.kt" "$ws/jvmconsumer/src/main/kotlin/wire/Wire.kt"
  cp "$REFERENCE/WireTest.kt" "$ws/jvmconsumer/src/test/kotlin/wire/WireTest.kt"
  cp "$REFERENCE/AppWire.kt" "$ws/app/src/main/kotlin/wire/AppWire.kt"
  printf 'none\n' > "$ws/STUMBLES.md"
  printf 'docs/README.md\ndocs/INTEGRATION.md\ndocs/API.md\ndocs/ECOSYSTEM.md\n' > "$ws/CONSULTED.md"

  # The planted bad copy is taken before any build output exists in the workspace.
  bad="$SELFTEST_TMP/ws-bad"
  cp -a "$ws" "$bad"
  sed -i "s#com.github.Ygaray.voice-action-engine:voice-action-engine-core:#com.github.Ygaray:voice-action-engine:#" \
    "$bad/jvmconsumer/build.gradle.kts"
  sed -i '/else[[:space:]]*->/d; /TierSelector\.Router/d' "$bad/jvmconsumer/src/main/kotlin/wire/Wire.kt"

  export WIRING_HOST_CACHE=1
  out="$(verify "$ws" "$version" 2>&1)" || rc=$?
  echo "--- reference solution verdict"; echo "$out"
  { [ "$rc" -eq 0 ] && grep -q '^WIRING TEST: PASS checks=13$' <<<"$out"; } || sf "the reference solution did not PASS"

  rc=0
  out="$(verify "$bad" "$version" 2>&1)" || rc=$?
  echo "--- planted bad copy verdict"; echo "$out"
  [ "$rc" -ne 0 ] || sf "the planted bad copy passed (the judge is vacuous)"
  for d in W4 W5 W12; do
    grep -q "^WIRING TEST: FAIL $d:" <<<"$out" || sf "the planted bad copy did not fail $d"
  done
  echo "WIRING SELFTEST OK"
}

cmd="${1:-}"
case "$cmd" in
  prepare)  shift; prepare "$@" ;;
  prepare-local) shift; prepare_local "$@" ;;
  verify)   shift; verify "$@" ;;
  selftest) shift; selftest "$@" ;;
  *) echo "usage: $0 prepare <sha> | prepare-local <m2dir> <version> | verify <dir> <version> | selftest [--local]" >&2; exit 2 ;;
esac
