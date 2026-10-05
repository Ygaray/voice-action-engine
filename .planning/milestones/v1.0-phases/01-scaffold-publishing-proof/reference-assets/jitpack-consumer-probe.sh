#!/usr/bin/env bash
# Resolves every published engine module from an EMPTY Gradle dependency cache (BLD-03 consumer proof).
#   :jvmconsumer (kotlin.jvm)  -> providers (jar -> jar :core)             [jar->jar]
#   :app (AGP 9.2.1 app)       -> providers + keystore (AAR -> jar :core)  [AAR->jar]
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
# Reuse only the Gradle DISTRIBUTION (never dependencies) to avoid a ~130 MB download; the dependency cache stays empty.
if [ -d "$HOME/.gradle/wrapper" ]; then ln -s "$HOME/.gradle/wrapper" "$GRADLE_USER_HOME/wrapper"; fi
ROOT="$(git rev-parse --show-toplevel)"
cd "$WORK"; mkdir -p app/src/main/kotlin/probe jvmconsumer/src/main/kotlin/probe gradle
cp -r "$ROOT/gradle/wrapper" gradle/; cp "$ROOT/gradlew" .
echo "sdk.dir=${ANDROID_HOME:-$HOME/Android/Sdk}" > local.properties
printf 'android.useAndroidX=true\norg.gradle.jvmargs=-Xmx2g\n' > gradle.properties
cat > settings.gradle.kts <<KTS
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral(); maven { url = uri("$REPO_URL") } }
}
rootProject.name = "consumer-probe"
include(":app", ":jvmconsumer")
KTS
cat > build.gradle.kts <<'KTS'
plugins {
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.jvm") version "2.3.20" apply false
}
KTS
cat > jvmconsumer/build.gradle.kts <<KTS
plugins { id("org.jetbrains.kotlin.jvm") }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) } }
java { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
dependencies { implementation("$GROUP:voice-action-engine-providers:$VERSION") }
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
echo '<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application/></manifest>' > app/src/main/AndroidManifest.xml
# OkHttp is an `api` dependency of :providers, so this compiles only if the transitive graph resolved end to end.
for d in app jvmconsumer; do
  printf 'package probe\nval client: okhttp3.OkHttpClient = okhttp3.OkHttpClient()\n' > "$d/src/main/kotlin/probe/P.kt"
done
./gradlew --no-daemon :jvmconsumer:compileKotlin :app:compileDebugKotlin
jvm_deps="$(./gradlew --no-daemon -q :jvmconsumer:dependencies --configuration runtimeClasspath)"
app_deps="$(./gradlew --no-daemon -q :app:dependencies --configuration debugRuntimeClasspath)"
for m in voice-action-engine-providers voice-action-engine-core; do
  grep -q "$m" <<<"$jvm_deps" || { echo "PROBE FAIL: $m missing from :jvmconsumer runtimeClasspath" >&2; exit 1; }
done
for m in voice-action-engine-providers voice-action-engine-keystore voice-action-engine-core; do
  grep -q "$m" <<<"$app_deps" || { echo "PROBE FAIL: $m missing from :app debugRuntimeClasspath" >&2; exit 1; }
done
echo "--- :jvmconsumer runtimeClasspath (engine lines)"; grep "voice-action-engine" <<<"$jvm_deps"
echo "--- :app debugRuntimeClasspath (engine lines)"; grep "voice-action-engine" <<<"$app_deps"
echo "PROBE OK ($GROUP:*:$VERSION from $REPO_URL) workdir=$WORK"
