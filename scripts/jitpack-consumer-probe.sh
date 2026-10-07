#!/usr/bin/env bash
# Resolves every published engine module from an EMPTY Gradle dependency cache (BLD-03 consumer proof).
#   :jvmconsumer (kotlin.jvm)  -> providers (jar -> jar :core)             [jar->jar]
#   :app (AGP 9.2.1 app)       -> providers + keystore (AAR -> jar :core)  [AAR->jar]
#   :undoalone (kotlin.jvm)    -> undo only [stands alone: no :core, no coroutines]
#   :adapteralone (AGP library) -> voice-adapter only, with the :stt artifact added by the consumer itself as compileOnly;
#                                  resolves :core, never :stt (RT-03a; no consumer that omits the adapter ever gets :stt)
# Usage: VERSION=<commit-sha-or-tag> [REPO_URL=https://jitpack.io] [GROUP=com.github.Ygaray.voice-action-engine] scripts/jitpack-consumer-probe.sh
#
# Derived from 01-RESEARCH.md "Code Examples" 6 (tested on a scratch prototype). Phase 1 adaptations:
#  - :core has NO public types yet (D-09, no placeholder types), so transitive :core exposure is asserted from the
#    resolved dependency graph, and :providers' api exposure of OkHttp is proven by compiling an OkHttp type.
#  - the dependency-graph greps are hard assertions (no error-suppressing fallback).
set -euo pipefail
REPO_URL="${REPO_URL:-https://jitpack.io}"
VERSION="${VERSION:?set VERSION to the commit SHA or tag}"
GROUP="${GROUP:-com.github.Ygaray.voice-action-engine}"
WORK="$(mktemp -d)"; export GRADLE_USER_HOME="$WORK/gradle-home"; mkdir -p "$GRADLE_USER_HOME"
# The empty dependency cache is hundreds of MB: remove the workdir on exit unless KEEP_WORK=1 (debugging).
# The wrapper symlink is unlinked first so the recursive remove can never reach the real ~/.gradle/wrapper.
cleanup_work() { rm -f "$GRADLE_USER_HOME/wrapper"; [ "${KEEP_WORK:-0}" = 1 ] || rm -rf "$WORK"; }
trap cleanup_work EXIT
# Reuse only the Gradle DISTRIBUTION (never dependencies) to avoid a ~130 MB download; the dependency cache stays empty.
if [ -d "$HOME/.gradle/wrapper" ]; then ln -s "$HOME/.gradle/wrapper" "$GRADLE_USER_HOME/wrapper"; fi
ROOT="$(git rev-parse --show-toplevel)"
# The :stt pin is read from the catalog (the owner-pinned immutable tag), never hard-coded here.
STT_GROUP="com.github.Ygaray.voice-engine-android"
STT_VERSION="$(sed -n 's/^stt-engine[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' "$ROOT/gradle/libs.versions.toml" | head -n 1)"
[ -n "$STT_VERSION" ] || { echo "PROBE FAIL: stt-engine version not found in gradle/libs.versions.toml" >&2; exit 1; }
cd "$WORK"; mkdir -p app/src/main/kotlin/probe jvmconsumer/src/main/kotlin/probe undoalone/src/main/kotlin/probe adapteralone/src/main/kotlin/probe gradle
cp -r "$ROOT/gradle/wrapper" gradle/; cp "$ROOT/gradlew" .
echo "sdk.dir=${ANDROID_HOME:-$HOME/Android/Sdk}" > local.properties
printf 'android.useAndroidX=true\norg.gradle.jvmargs=-Xmx2g\n' > gradle.properties
cat > settings.gradle.kts <<KTS
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // The :stt AAR comes from the JitPack host through this one exact group; the engine group keeps coming from REPO_URL.
        exclusiveContent {
            forRepository { maven { url = uri("https://jitpack.io") } }
            filter { includeGroup("com.github.Ygaray.voice-engine-android") }
        }
        google(); mavenCentral(); maven { url = uri("$REPO_URL") }
    }
}
rootProject.name = "consumer-probe"
include(":app", ":jvmconsumer", ":undoalone", ":adapteralone")
KTS
cat > build.gradle.kts <<'KTS'
plugins {
    id("com.android.application") version "9.2.1" apply false
    id("com.android.library") version "9.2.1" apply false
    id("org.jetbrains.kotlin.jvm") version "2.3.20" apply false
}
KTS
cat > jvmconsumer/build.gradle.kts <<KTS
plugins { id("org.jetbrains.kotlin.jvm") }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) } }
java { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
dependencies { implementation("$GROUP:voice-action-engine-providers:$VERSION") }
KTS
cat > undoalone/build.gradle.kts <<KTS
plugins { id("org.jetbrains.kotlin.jvm") }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) } }
java { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
dependencies { implementation("$GROUP:voice-action-engine-undo:$VERSION") }
KTS
cat > app/build.gradle.kts <<KTS
plugins { id("com.android.application") }
android {
    namespace = "probe"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    defaultConfig { applicationId = "probe"; minSdk = 35; targetSdk = 36; versionCode = 1; versionName = "0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
}
dependencies {
    implementation("$GROUP:voice-action-engine-providers:$VERSION")
    implementation("$GROUP:voice-action-engine-keystore:$VERSION")
}
KTS
cat > adapteralone/build.gradle.kts <<KTS
plugins { id("com.android.library") }
android {
    namespace = "probe.adapter"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    defaultConfig { minSdk = 35 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
}
dependencies {
    implementation("$GROUP:voice-action-engine-voice-adapter:$VERSION")
    // The consumer adds the speech engine itself; compileOnly keeps the runtime classpath a clean view of what the adapter brings.
    compileOnly("$STT_GROUP:voice-engine-android:$STT_VERSION")
}
KTS
echo '<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application/></manifest>' > app/src/main/AndroidManifest.xml
# OkHttp is an `api` dependency of :providers, so this compiles only if the transitive graph resolved end to end.
for d in app jvmconsumer; do
  printf 'package probe\nval client: okhttp3.OkHttpClient = okhttp3.OkHttpClient()\n' > "$d/src/main/kotlin/probe/P.kt"
done
# :undoalone compiles an :undo type with nothing else on its classpath: UNDO-01 "a non-voice app can use it alone".
printf 'package probe\nval ticket = io.github.ygaray.voiceactionengine.undo.UndoJournal { }.newTicket()\n' > undoalone/src/main/kotlin/probe/P.kt
# :adapteralone compiles both adapter entry points (the two-argument and no-argument forms) against the :stt type it added itself.
printf 'package probe\n\nimport io.github.ygaray.sttengine.FinalSegment\nimport io.github.ygaray.voiceactionengine.voiceadapter.commandInputOf\nimport io.github.ygaray.voiceactionengine.voiceadapter.toCommandInput\n\nval fromText = commandInputOf("hello", "en")\n\nfun fromSegment(segment: FinalSegment) = segment.toCommandInput()\n' > adapteralone/src/main/kotlin/probe/P.kt
./gradlew --no-daemon :jvmconsumer:compileKotlin :app:compileDebugKotlin :undoalone:compileKotlin :adapteralone:compileDebugKotlin
jvm_deps="$(./gradlew --no-daemon -q :jvmconsumer:dependencies --configuration runtimeClasspath)"
app_deps="$(./gradlew --no-daemon -q :app:dependencies --configuration debugRuntimeClasspath)"
undo_deps="$(./gradlew --no-daemon -q :undoalone:dependencies --configuration runtimeClasspath)"
adapter_deps="$(./gradlew --no-daemon -q :adapteralone:dependencies --configuration debugRuntimeClasspath)"
for m in voice-action-engine-providers voice-action-engine-core; do
  grep -q "$m" <<<"$jvm_deps" || { echo "PROBE FAIL: $m missing from :jvmconsumer runtimeClasspath" >&2; exit 1; }
done
for m in voice-action-engine-providers voice-action-engine-keystore voice-action-engine-core; do
  grep -q "$m" <<<"$app_deps" || { echo "PROBE FAIL: $m missing from :app debugRuntimeClasspath" >&2; exit 1; }
done
grep -q "voice-action-engine-undo" <<<"$undo_deps" || { echo "PROBE FAIL: voice-action-engine-undo missing from :undoalone runtimeClasspath" >&2; exit 1; }
for m in voice-action-engine-core kotlinx-coroutines; do
  if grep -q "$m" <<<"$undo_deps"; then echo "PROBE FAIL: $m resolved on :undoalone runtimeClasspath (:undo must stand alone)" >&2; exit 1; fi
done
for m in voice-action-engine-voice-adapter voice-action-engine-core; do
  grep -q "$m" <<<"$adapter_deps" || { echo "PROBE FAIL: $m missing from :adapteralone debugRuntimeClasspath" >&2; exit 1; }
done
if grep -q "voice-engine-android" <<<"$adapter_deps"; then echo "PROBE FAIL: the :stt artifact resolved on :adapteralone debugRuntimeClasspath (the adapter must not bring :stt)" >&2; exit 1; fi
if grep -q "voice-engine-android" <<<"$app_deps"; then echo "PROBE FAIL: the :stt artifact resolved on :app debugRuntimeClasspath (a consumer that omits the adapter must never get :stt)" >&2; exit 1; fi
if grep -q "voice-engine-android" <<<"$jvm_deps"; then echo "PROBE FAIL: the :stt artifact resolved on :jvmconsumer runtimeClasspath (a consumer that omits the adapter must never get :stt)" >&2; exit 1; fi
echo "--- :jvmconsumer runtimeClasspath (engine lines)"; grep "voice-action-engine" <<<"$jvm_deps"
echo "--- :app debugRuntimeClasspath (engine lines)"; grep "voice-action-engine" <<<"$app_deps"
echo "--- :undoalone runtimeClasspath (engine lines)"; grep "voice-action-engine" <<<"$undo_deps"
echo "--- :adapteralone debugRuntimeClasspath (engine lines)"; grep "voice-action-engine" <<<"$adapter_deps"
if [ "${KEEP_WORK:-0}" = 1 ]; then where="workdir=$WORK (kept)"; else where="workdir removed on exit"; fi
echo "PROBE OK ($GROUP:*:$VERSION from $REPO_URL) $where"
