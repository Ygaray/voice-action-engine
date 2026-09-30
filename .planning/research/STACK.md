# Stack Research

**Domain:** Android/Kotlin JitPack library. A multi-provider LLM engine that turns a voice command into an app action (`voice-action-engine`, v1.0 = contract §6.2 steps 1–7)
**Researched:** 2026-09-29
**Confidence:** HIGH for versions and ecosystem parity. MEDIUM for the in-build OkHttp matrix plumbing and JitPack inter-module publishing, which are new to this ecosystem and must be proven in the scaffold phase.

> **How versions were verified.** Every version below was read on 2026-09-29 from the registry itself (`maven-metadata.xml` on Maven Central, Google Maven and the Gradle Plugin Portal). Consumer and sibling pins were read directly from the local repos' `gradle/libs.versions.toml` / build files. Upstream behavior claims come from upstream primary sources: the OkHttp `CHANGELOG.md`, JitPack `BUILDING.md`, the metalava-gradle source, and developer.android.com. The GSD `classify-confidence` seam rates generic webfetch/websearch as LOW. Wherever a claim rests only on web material, it is tagged LOW/MEDIUM below. Anything that is also proven by a local, working sibling build (YAT, stt-engine, backup-engine, SB, CT) is tagged HIGH.

---

## TL;DR: the prescriptive picks

1. **Match the consumers exactly. Don't chase latest.** Use Kotlin **2.3.20**, AGP **9.2.1**, Gradle **9.4.1**, coroutines **1.11.0**, serialization **1.11.0** and DataStore **1.2.1**. That is exactly what SecondBrain, CalTracker and YAT build with today, so the library forces zero upgrades. Latest upstream is Kotlin 2.4.20 / AGP 9.4.1 / Gradle 9.8.0, and nothing in v1.0 needs any of them.
2. **Bytecode target is JVM 11, not 17.** SB and YAT compile with `JavaVersion.VERSION_11`, and under AGP 9 built-in Kotlin, `jvmTarget` follows that setting. If a JVM-17 library ships a public `inline` function (DSL builders, `reified` helpers), SB fails to compile with *"Cannot inline bytecode built with JVM target 17 into bytecode that is being built with JVM target 11"*. backup-engine and stt-engine target 17 and have only avoided this because SB never inlines from them. The engine exposes a `commandPipeline { }` DSL, so it must target 11.
3. **`:core` and `:providers` are pure Kotlin/JVM modules** (`org.jetbrains.kotlin.jvm`). `:keystore` is an Android library using AGP 9 built-in Kotlin. `:sample` is an Android app that is never published. A JVM `:providers` makes the A1 OkHttp matrix a plain-Gradle `Test` task problem with no Robolectric, and nothing in the transports needs `android.*`.
4. **OkHttp:** compile against **4.12.0**, declared as a plain `api` requirement (never `strictly`, never a BOM). `./gradlew check` runs the same compiled tests against **4.12.0**, **5.2.1** (the version SB and CT converge on per A11) and **5.5.0** (latest). Tests use only the legacy `okhttp3.mockwebserver` API, which exists in both lines.
5. **Raw OkHttp + kotlinx.serialization `JsonObject`. No official Anthropic/OpenAI SDKs.** The contract's choice is correct: the SDKs drag Jackson, kotlin-reflect, jsonschema-generator and swagger onto every consumer's APK.
6. **detekt 1.23.8, syntax-only** (the plain `detekt` task, never `detektMain`). Use `buildUponDefaultConfig = true`, `maxIssues: 0` and **no baseline file at all**.
7. **Metalava (`me.tylerbwong.gradle.metalava` 0.5.1) on all three published modules** for the strictly-additive API rule. It supports both JVM and Android library modules, and its check runs `--check-compatibility:api:released`, so additions pass and breaks fail. Don't use BCV or KGP `abiValidation`: KGP's validator explicitly doesn't support Android library modules.
8. **JitPack:** `jdk: openjdk17`, one `install` line publishing the three modules. **Coordinates are per-module:** `com.github.Ygaray.voice-action-engine:voice-action-engine-{core,providers,keystore}:v1.0.0`. The contract's `com.github.Ygaray:voice-action-engine` is JitPack's auto-generated *aggregator* (see flag F1).

---

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended | Confidence |
|---|---|---|---|---|
| Kotlin (KGP, `kotlin.jvm`, `plugin.serialization`, `plugin.compose` for `:sample`) | **2.3.20** | Language + compiler plugins | Exact match with SB, CT, YAT, backup-engine and stt-engine. The compiler reads metadata only one minor version ahead ("compiler 2.0 can read up to 2.1", per the detekt #8865 error text). Building at 2.3.20 keeps every consumer safe, and the stdlib pulled transitively (2.3.20) equals what they already have. Latest is 2.4.20 (2026-09-07): no v1.0 feature needs it. | HIGH |
| Android Gradle Plugin | **9.2.1** | `:keystore` (library), `:sample` (app) | Same as SB, CT and YAT. YAT proves AGP 9.2.1 + JitPack + maven-publish + Metalava works in this ecosystem. AGP 9 has built-in Kotlin: **do not apply `org.jetbrains.kotlin.android`**. Declaring KGP 2.3.20 at the root upgrades AGP's built-in Kotlin, whose runtime floor is KGP 2.2.10. | HIGH |
| Gradle wrapper | **9.4.1** | Build | Same as SB, CT and YAT, and proven on JitPack with `openjdk17` by YAT. AGP 9 needs Gradle ≥ 9.1 and JDK ≥ 17. Latest is 9.8.0, but there's no reason to diverge. | HIGH |
| JDK (build) | **17** | Runs Gradle locally and on JitPack | The host has only OpenJDK 17.0.19. JitPack uses `openjdk17` across the whole ecosystem. **Don't configure `jvmToolchain()` or foojay auto-provisioning.** Set targets explicitly (next row) so the build runs on whatever JDK ≥ 17 is present. | HIGH |
| Bytecode target | **JVM 11** (`jvmTarget = JVM_11`, `source/targetCompatibility = 11`) | All published modules | The lowest consumer target is 11 (SB, YAT). See TL;DR #2. CT is on 17, and 11-bytecode is consumable by 17. | HIGH |
| OkHttp | **4.12.0** (compile floor, `api`) | `:providers` HTTP transport | Contract A1. A plain declaration is a Gradle *minimum*, so SB/CT's 5.2.1 wins conflict resolution with no action on their side. 4.12.0 is also what the official Anthropic/OpenAI Java SDKs pin. | HIGH |
| kotlinx.serialization-json | **1.11.0** | `JsonObject` tree for tool schemas, tool args and wire bodies; internal `@Serializable` response DTOs | Both port sources (SB `AnthropicAgentLoop`, CT `AnthropicProvider`) already speak `kotlinx.serialization.json.JsonObject`. SB's `MutationGate.admit(toolName, input: JsonObject?)` is the E1 seam shape. Both consumers pin 1.11.0. 1.12.0 is still RC. | HIGH |
| kotlinx.coroutines-core | **1.11.0** | `suspend` seams, cancellation, `Call.await()` bridge | Latest stable (2026-05-07) and the exact pin in SB, CT, backup-engine and stt-engine. `:core` takes **`-core` only**, never `-android`, to stay pure. | HIGH |
| androidx DataStore Preferences | **1.2.1** | `:keystore` ciphertext persistence | Latest stable (1.3.0 is alpha) and the exact pin in SB, CT and backup-engine. | HIGH |

### Module Layout

| Module | Gradle plugin(s) | Published? | Artifact | Depends on |
|---|---|---|---|---|
| `:core` | `org.jetbrains.kotlin.jvm`, `maven-publish`, detekt, metalava | Yes (jar) | `voice-action-engine-core` | `api`: coroutines-core, serialization-json. **No OkHttp, no Android, no other hub** (L7/A7), enforced by the classpath, not just by convention. |
| `:providers` | `kotlin.jvm`, `plugin.serialization`, `maven-publish`, detekt, metalava | Yes (jar) | `voice-action-engine-providers` | `api(project(":core"))`, `api` okhttp 4.12.0 (`OkHttpClient` is injectable so apps can share one connection pool) |
| `:keystore` | `com.android.library` (built-in Kotlin), `maven-publish`, detekt, metalava | Yes (AAR) | `voice-action-engine-keystore` | `api(project(":core"))` (it implements a `:core` key-provider seam), `implementation` datastore-preferences |
| `:sample` | `com.android.application`, `plugin.compose` | **Never** (A10) | n/a | all three project modules + **OkHttp 5.2.1 pinned**, so Gate-1 runs 4.12-compiled bytecode on the real `okhttp-android` 5.x variant on the TESTER |

Packages and namespaces: `io.github.ygaray.voiceactionengine.{core,providers,keystore,sample}`. This matches the `io.github.ygaray.*` convention used by backup-engine and YAT. AGP 9 enforces `android.uniquePackageNames=true`.

**Why `:providers` is JVM, not Android (HIGH):**
- The transports need nothing from `android.*`. The library must never log (PROJECT.md "keys never reach any sink"), so the lack of `android.util.Log` is a feature.
- The A1 matrix becomes ordinary JVM `Test` tasks with custom runtime classpaths. On an Android library module you'd be fighting AGP's unit-test variant plumbing.
- Android consumers consume jar libraries natively. When a consumer resolves OkHttp 5.x, Gradle module metadata picks `okhttp-android` automatically. OkHttp 5.x publishes `jvm` and `androidJvm` variants (verified in the `okhttp-5.5.0.module` file).
- Cost: you can't ship `consumerProguardFiles` through an AAR. If R8 rules are ever needed, put them in `src/main/resources/META-INF/proguard/<name>.pro` (R8 honors these in jars). Both consumers currently run `isMinifyEnabled = false`.
- The `ON_DEVICE` runtime capability gate (A5) is a `:core` seam (`OnDeviceCapability`, defaulting to "absent"). A future AICore implementation belongs in a new Android module (`:ondevice`, v1.x, needs a Pixel 10), not in `:providers`.

### Supporting Libraries

| Library | Version | Purpose | When to Use | Confidence |
|---|---|---|---|---|
| `com.squareup.okhttp3:mockwebserver` (**legacy** package `okhttp3.mockwebserver`) | tracks the OkHttp matrix leg (4.12.0 / 5.2.1 / 5.5.0) | JVM transport tests in `:providers` | Always, and **only this one**. It exists in both 4.12 and 5.x. OkHttp calls it "Obsolete" in 5.x but promises to keep publishing it, and CT's 1177 tests run green on it at 5.2.1 (A11). `mockwebserver3` is 5.x-only, so it can't compile against the 4.12 floor. | HIGH |
| `junit:junit` | **4.13.2** | Test framework, all modules | Ecosystem standard. Legacy mockwebserver *depends on* JUnit 4 in both 4.12 and 5.x. Android instrumented tests use JUnit 4. JUnit 5/6 (6.1.3 is current) buys nothing here and adds a second engine. | HIGH |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | **1.11.0** | `runTest`, virtual time for timeouts, budget ceilings and cancellation tests | Always, in `:core` and `:providers` tests. | HIGH |
| `androidx.test.ext:junit` / `androidx.test:runner` | **1.3.0 / 1.7.0** | `:keystore` androidTest (a real AndroidKeyStore round-trip on the TESTER) | Only for the device-side keystore test. The JCE `AES/GCM/NoPadding` logic itself is JVM-testable through the `KeystoreCryptoSeam` with a software key. | HIGH |
| `androidx.datastore:datastore-preferences-core` | 1.2.1 (test only) | JVM tests of `:keystore` persistence using a temp file | Local unit tests of the store logic without a device. | MEDIUM |
| Compose BOM + material3 + activity-compose (`:sample` only) | BOM **2026.04.01** (CT's known-good), activity-compose **1.13.0**, lifecycle-viewmodel-compose **2.10.0** | BYO-key field + "run agentic fixture" button + trace readout | `:sample` only. It mirrors how the real apps wire the engine (ViewModel + scope), which matters because `:sample` is the README's reference wiring. Keep it tiny and unpublished. Don't depend on YAT here. | MEDIUM |

### Development Tools

| Tool | Version | Purpose | Notes | Confidence |
|---|---|---|---|---|
| detekt (`io.gitlab.arturbosch.detekt`) | **1.23.8** | Lint gate, zero issues on every library module | Latest stable, built on Kotlin 2.0.21. **Use only the plain `detekt` task** (syntax-only). Type-resolution tasks (`detektMain`/`detektTest`) can't read Kotlin 2.3 metadata ([detekt#8865](https://github.com/detekt/detekt/issues/8865)) and produce false positives. `check` depends on plain `detekt` by default, so don't wire `detektMain` in. Proven at Kotlin 2.3.20 / Gradle 9.4.1 in SB and YAT. detekt 2.0 is still `2.0.0-alpha.6` (2026-08-04): don't put an alpha linter in a tag gate. | HIGH (syntax-only), LOW (type-res) |
| Metalava (`me.tylerbwong.gradle.metalava`) | **0.5.1** (YAT is on 0.5.0) | Public-API signature files + compatibility check (§11 rule 2) | Supports `com.android.library`, `kotlin` (JVM) and `java-library`. `metalavaCheckCompatibility[Release]` passes `--check-compatibility:api:released`, so additions are allowed and removals/signature changes fail. Mirror YAT's `apiDump`/`apiCheck` alias tasks. Commit `api.txt` per module. It's first created at v1.0.0 cut time; after that it always equals the last tagged API. | HIGH (Android, YAT-proven), MEDIUM (JVM modules in an AGP-9 build: verify in scaffold) |
| Kotlin explicit API mode | built-in | Forces explicit `public`/`internal` + return types on library modules | `kotlin { explicitApi() }` on `:core`, `:providers` and `:keystore`. This is the cheapest guard against accidentally public API that Metalava would then freeze forever. If the built-in-Kotlin `kotlin {}` block in `:keystore` doesn't expose it, fall back to `freeCompilerArgs.add("-Xexplicit-api=strict")`. | MEDIUM |
| Gradle build cache | on (`org.gradle.caching=true`) | Speed | Mirror YAT's `gradle.properties`. Keep `org.gradle.configuration-cache=false` like every sibling repo, since detekt 1.23 and the Metalava plugin aren't proven CC-clean here. | HIGH |

---

## A1: OkHttp compile floor 4.12, green on 4.12.x AND 5.x (must-pass)

### What is actually different between 4.12 and 5.x (from the OkHttp CHANGELOG)

| Change in 5.x | Impact on code compiled against 4.12 | Rule |
|---|---|---|
| `Response.body` changed from `ResponseBody?` to non-null `ResponseBody` | Same JVM signature (`body()Lokhttp3/ResponseBody;`), so it's **binary-compatible**. Source compiled at 4.12 must write `response.body?.string()`; on 5.x that's just a redundant safe call. SB's `response.body.string()` doesn't compile at the floor. | Always `body?.…`, treat null as a typed `Malformed` failure |
| Artifact split into KMP `okhttp-jvm` / `okhttp-android` via Gradle module metadata | Transparent for Gradle consumers. **Custom resolvable configurations must carry JVM attributes** or variant selection is ambiguous (matters for the matrix below). | Copy the attributes (snippet below) |
| `okhttp3.internal.*` reshuffled (CT hit `okhttp3.internal.Util` being dropped) | Links on 4.12, `NoSuchMethodError`/`NoClassDefFoundError` on 5.x | detekt `ForbiddenImport: okhttp3.internal.*` |
| New 5.x-only APIs: Kotlin constructors (`Request(url = …)`), `okhttp-coroutines` `Call.executeAsync()`, `Call.tag(...)`, `Response.socket`, `Request.toCurl()` | Already impossible, because main compiles against 4.12. The risk is someone "helpfully" bumping the `:providers` compile version. | Catalog comment + matrix guard test |
| Legacy MockWebServer: subclassing `okhttp3.mockwebserver.QueueDispatcher` is **not** source/binary compatible across 4→5 | Test-only breakage on the 5.x leg | Use `enqueue(MockResponse)` or subclass `Dispatcher`, never `QueueDispatcher` |
| `mockwebserver3` API (immutable `MockResponse.Builder`, nullable `RecordedRequest.body: ByteString?`) | Doesn't exist at 4.12 | Tests use `okhttp3.mockwebserver` only (detekt `ForbiddenImport: mockwebserver3.*`) |

Square states they "don't break binary compatibility with non-alpha APIs" in 5.x. The matrix is how we *prove* that for the exact surface we use. A11 corroborates it in practice: SB runs backup-engine (built against 4.12) on 5.2.1 in production.

### How to build the matrix (recommended: in-build, one `./gradlew check` runs every leg)

There is **no GitHub Actions CI anywhere in this ecosystem**. The "CI" is the agent-run Gradle gate on the host. So the matrix must live inside the build, where nobody can forget it. That rules out an external `-P` loop. The pattern:

- The `test` task runs against the declared 4.12.0 (the floor leg).
- For each 5.x version, create a resolvable runtime classpath that re-uses the **already-compiled** test classes. Tests are compiled once, against 4.12, exactly like a consumer's precompiled dependency.
- Force `com.squareup.okhttp3:*` to that version. Okio and stdlib then float up via normal conflict resolution.

```kotlin
// providers/build.gradle.kts (sketch — verify attribute set in the scaffold phase)
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

val okhttp5Legs = listOf(libs.versions.okhttpConsumer.get(), libs.versions.okhttpLatest.get()) // "5.2.1", "5.5.0"

okhttp5Legs.forEach { v ->
    val suffix = "OkHttp" + v.replace(".", "_")
    val runtime = configurations.create("testRuntimeClasspath$suffix") {
        isCanBeConsumed = false
        isCanBeResolved = true
        extendsFrom(configurations.testImplementation.get(), configurations.testRuntimeOnly.get())
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
            attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named(TargetJvmEnvironment.STANDARD_JVM))
            attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm) // picks okhttp-jvm, not okhttp-android
        }
        resolutionStrategy.eachDependency {
            if (requested.group == "com.squareup.okhttp3") { useVersion(v); because("A1 OkHttp matrix leg") }
        }
    }
    val leg = tasks.register<Test>("test$suffix") {
        group = "verification"
        description = "Runs :providers tests (compiled vs OkHttp 4.12) on OkHttp $v"
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().output + sourceSets.main.get().output + runtime
        systemProperty("vae.okhttp.expected", v)
        useJUnit()
    }
    tasks.named("check") { dependsOn(leg) }
}
tasks.test { systemProperty("vae.okhttp.expected", libs.versions.okhttp.get()) } // "4.12.0"
```

**Mandatory guard test** (without it, a misconfigured leg silently runs 4.12 three times):

```kotlin
@Test fun runtimeOkHttpIsTheLegUnderTest() {
    // OkHttp.VERSION is a `const val` — reading it directly would be INLINED at compile time
    // as "4.12.0". Read the runtime class reflectively instead.
    val actual = Class.forName("okhttp3.OkHttp").getField("VERSION").get(null) as String
    assertEquals(System.getProperty("vae.okhttp.expected"), actual)
}
```

**Fallback (HIGH confidence, less elegant):** if the attribute plumbing fights Gradle 9.4.1, drop the custom configurations. Instead apply the same `eachDependency` to the stock `testRuntimeClasspath` when `-PokhttpTestVersion=x` is set, and add a `scripts/okhttp-matrix.sh` that runs `./gradlew :providers:test` three times. The guard test stays either way.

**Third leg on device:** `:sample` pins OkHttp 5.2.1, so the Gate-1 run on the TESTER exercises the 4.12-compiled `:providers` on the *Android* 5.x variant, which the JVM legs can't cover.

**Cancellation-safe `Call.await()`** (CT port) works identically on both lines: `suspendCancellableCoroutine` + `invokeOnCancellation { call.cancel() }` + `enqueue`. With coroutines 1.11, resume with the three-argument `onCancellation = { _, response, _ -> response.close() }` overload so a response delivered after cancellation is closed rather than leaking a connection. (MEDIUM: verify the exact overload in the provider phase.)

---

## JitPack Mechanism-B publishing (multi-module)

Reference: backup-engine (single AAR module, AGP 8.13) and YAT (single AAR, **AGP 9.2.1 + Gradle 9.4.1 on JitPack `openjdk17`**, the closest proven analogue). stt-engine is the ecosystem's multi-module JitPack precedent: 5 AAR modules, one `install` list. But **none of its published modules depend on each other**. voice-action-engine will be the first repo here with published inter-module dependencies (`:providers → :core`, `:keystore → :core`). That's the one genuinely unproven piece.

**`jitpack.yml`:**
```yaml
jdk:
  - openjdk17
install:
  # Library modules ONLY. :sample is debug-only and must never appear here (A10).
  - ./gradlew :core:publishReleasePublicationToMavenLocal :providers:publishReleasePublicationToMavenLocal :keystore:publishReleasePublicationToMavenLocal
```

**Publication shape.** Name every publication `release`, so the task names are uniform across JVM and AAR modules:
- JVM (`:core`, `:providers`): `java { withSourcesJar() }` + `register<MavenPublication>("release") { from(components["java"]) }`.
- AAR (`:keystore`): `android { publishing { singleVariant("release") { withSourcesJar() } } }` + `afterEvaluate { from(components["release"]) }`. This is the backup-engine form. Skipping `singleVariant` is "the #1 JitPack-Android publish failure", per the backup-engine comment.

**Coordinates: set them correctly at build time instead of relying on JitPack's POM rewriting.** JitPack exports `JITPACK=true`, `GROUP`, `ARTIFACT` and `VERSION` (the tag or commit being built; see JitPack `BUILDING.md`). It also rewrites inter-module POM entries, and that has been reported as unpredictable ([jitpack#4112](https://github.com/jitpack/jitpack.io/issues/4112)). Its handling of Gradle `.module` metadata has been reported wrong ([jitpack#4476](https://github.com/jitpack/jitpack.io/issues/4476)). backup-engine and YAT hard-code `version = "1.1.1"` / `"1.10.0"` and let JitPack override. That only works because they're single-module.
```properties
# gradle.properties
group=com.github.Ygaray.voice-action-engine
```
```kotlin
// each published module
version = providers.environmentVariable("VERSION").getOrElse("0.0.0-LOCAL")
publishing.publications.register<MavenPublication>("release") {
    groupId = "com.github.Ygaray.voice-action-engine"
    artifactId = "voice-action-engine-core"   // -providers / -keystore
    version = project.version.toString()
}
```
With this, the POM and `.module` that `:providers` publishes already reference `com.github.Ygaray.voice-action-engine:voice-action-engine-core:v1.0.0`, which is exactly what JitPack serves. **Fallback** if clean-cache resolution still fails: `tasks.withType<GenerateModuleMetadata>().configureEach { enabled = System.getenv("JITPACK") == null }`. That makes JitPack serve POM-only metadata. (MEDIUM/LOW: web-sourced JitPack behavior. Prove it in the scaffold phase.)

**Prove it without burning a tag.** Tags are immutable (§11 rule 6), but JitPack builds any commit. In the scaffold phase, push, then resolve `com.github.Ygaray.voice-action-engine:voice-action-engine-providers:<short-sha>` from a scratch consumer with an empty `GRADLE_USER_HOME` (§11 rule 4). Check three things: `:core` arrives transitively, OkHttp arrives at 4.12.0 alone, and it upgrades to 5.2.1 when the scratch consumer declares 5.2.1.

**F1: coordinate flag for the orchestrator.** The contract §6.2 and `ECOSYSTEM.md` name `com.github.Ygaray:voice-action-engine`. For a multi-module repo, JitPack serves that plain coordinate as a **generated aggregator that depends on every published module**. That's the same trap SB documents for `voice-engine-android`, whose aggregator dragged in unwanted `:voicenotes`. It happens to equal the v1.0 consumer set (core + providers + keystore). But in v1.1 it would also pull `:voice-adapter` → `:stt`, which breaks the optional bridge (L7). Recommendation: consumers pin **per-module** coordinates `com.github.Ygaray.voice-action-engine:voice-action-engine-{core,providers,keystore}:vX`. Keep the plain coordinate as the "repo identity" name in docs. The §11 ledger row lists the per-module coordinates. Route this through the control plane as an erratum, not a silent edit. **Don't give any module the bare artifactId `voice-action-engine`**; that reproduces stt-engine's confusing plain-vs-standalone split.

---

## Build Configuration Sketch

**`gradle/libs.versions.toml` (new keys only, all verified 2026-09-29):**
```toml
[versions]
kotlin = "2.3.20"
agp = "9.2.1"
coroutines = "1.11.0"
kotlinxSerialization = "1.11.0"
datastore = "1.2.1"
# A1: COMPILE floor for :providers. Never raise this — consumers pick their own OkHttp.
okhttp = "4.12.0"
okhttpConsumer = "5.2.1"   # SB today, CT after A11 — matrix leg + :sample pin
okhttpLatest = "5.5.0"     # canary matrix leg (2026-08-16)
junit = "4.13.2"
androidxTestJunit = "1.3.0"
androidxTestRunner = "1.7.0"
detekt = "1.23.8"
metalava = "0.5.1"
composeBom = "2026.04.01"  # :sample only

[plugins]
android-library     = { id = "com.android.library", version.ref = "agp" }
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-jvm          = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
kotlin-compose      = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
detekt              = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }
metalava            = { id = "me.tylerbwong.gradle.metalava", version.ref = "metalava" }
```

**Root `build.gradle.kts`:** every plugin `apply false`. Declaring `kotlin-jvm` 2.3.20 at the root pins KGP for the whole build, **including AGP 9's built-in Kotlin** in `:keystore`/`:sample`. There's no `org.jetbrains.kotlin.android` anywhere. There's no KSP, so `android.disallowKotlinSourceSets` (a KSP-only workaround in YAT/SB) isn't needed.

**`settings.gradle.kts`:** copy backup-engine's block verbatim (content-filtered `google()`, `mavenCentral()`, `gradlePluginPortal()`, `FAIL_ON_PROJECT_REPOS`). Include `:core`, `:providers`, `:keystore`, `:sample`. No JitPack repo is needed, because the engine depends on no hub.

**JVM module target (`:core`, `:providers`):**
```kotlin
java { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11; withSourcesJar() }
kotlin {
    explicitApi()
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) }
}
```
KGP errors by default if the Java and Kotlin targets disagree, so set both.

**`:keystore`:** `compileSdk { version = release(36) }` (plain 36, **not** `minorApiLevel = 1`). AGP 9 now writes `minCompileSdk = compileSdk` into AAR metadata by default, and both consumers compile at 36.1, so 36 forces nothing. Use `minSdk = 26` (AndroidKeyStore AES/GCM needs 23, and 26 matches backup-engine; consumers are at 35), `compileOptions` 11/11, `singleVariant("release")`.

**`:sample`:** `minSdk = 35` (the S22 TESTER), `targetSdk = 36`, OkHttp `okhttpConsumer`, fixture JSON in `src/main/assets/` (from SB via the orchestrator, E2). It gets no detekt/metalava requirement and is excluded from `jitpack.yml`.

## detekt Configuration: a genuinely clean zero baseline

- `buildUponDefaultConfig = true` (**differs from SB/YAT's `false`** + ~10 hand-picked rules). "Genuinely clean" should mean detekt's default rule set is actually on.
- `allRules = false`; `build.maxIssues: 0`; **no `baseline` property and no baseline XML in the repo.** If a finding appears, fix the code or tune the rule in `config/detekt/detekt.yml` with a one-line justification comment. That's the "tune rules, never bank debt" requirement from PROJECT.md.
- Apply to `:core`, `:providers`, `:keystore` (and optionally `:sample`, which isn't required).
- Rules that encode this project's invariants, so they're enforced mechanically:

| Rule | Setting | Enforces |
|---|---|---|
| `style>ForbiddenImport` | `imports: ['okhttp3.internal.*', 'mockwebserver3.*', 'okhttp3.coroutines.*', 'android.util.Log', 'dagger.*', 'javax.inject.*']` | A1 floor, no 5.x-only test API, no logging sink, no DI annotations in library code |
| `style>ForbiddenMethodCall` | defaults (`kotlin.io.print`, `kotlin.io.println`) + `java.lang.Throwable.printStackTrace` | keys, transcripts and tool args never reach a sink |
| `exceptions>TooGenericExceptionCaught` / `SwallowedException` | keep active | the never-throw collapse lives in **one** internal helper (`catch (e: CancellationException) { throw e } catch (e: Exception) { → typed Failed }`), which carries the repo's only justified `@Suppress`. Everywhere else must catch specific types. |
| `complexity>LongParameterList` | `constructorThreshold: 8`, `ignoreDefaultParameters: true` | request/policy value classes with defaults |
| `style>MagicNumber` | keep; tests are excluded by default | HTTP status codes and token defaults become named constants (supports "limits come from policy") |

- Skip `detekt-formatting` (the ktlint wrapper). It adds a second Kotlin-parser version to keep in sync for little value. `kotlin.code.style=official` + IDE formatting is enough. (MEDIUM, taste call)
- Guard (only if a `detekt` run ever reports "detekt was compiled with Kotlin 2.0.21 but is currently running with 2.3.20"): pin `org.jetbrains.kotlin:*` to `2.0.21` on the `detekt` configuration via `resolutionStrategy.eachDependency` ([detekt#6198](https://github.com/detekt/detekt/issues/6198)). It isn't needed in SB/YAT today. (LOW)

## Strictly-additive public API (§11 rule 2)

- The tool is Metalava per module; commit `api.txt`. `metalavaCheckCompatibility` (JVM) / `metalavaCheckCompatibilityRelease` (`:keystore`) run under `check` (`enforceCheck` defaults to true).
- Workflow: until `v1.0.0` there is no released API, so the rule is vacuous. Generate `api.txt` (`apiDump`) as the last step before cutting `v1.0.0` and commit it with the tag. From then on it *is* the released API, and every later change must pass the compat check. Regenerate only at the next tag cut, and review that the diff is `+`-only.
- Kotlin-specific evolution rules. These are what break binary compatibility even when the change "looks additive":
  - **No public `data class` for types that will grow** (`CommandInput`, `TierPolicy`, request/result types). Adding a property changes the constructor and the `copy`/`componentN` signatures, and Metalava flags it as a break. Use regular classes with defaults, or builders. Reserve `data class` for frozen shapes.
  - **Sealed hierarchies (`StrategyOutcome`) are closed for consumers' `when`.** Adding a subtype is binary-safe but source-breaking for exhaustive `when` in apps. Decide in v1.0 whether `StrategyOutcome`/failure-reason types get an `Other`/`Unknown` escape hatch.
  - Interfaces that apps implement (`ToolExecutor`, `PreApplyGate`, `CommitSink`, `OutcomeResolver`, `ToolSpecProvider`): only ever add members **with default bodies**. Kotlin ≥ 2.2 compiles these as JVM default methods by default (`-jvm-default=enable`), so that stays binary-compatible for already-compiled implementors. (MEDIUM)
- **Don't use BCV** (`org.jetbrains.kotlinx.binary-compatibility-validator` 0.18.2, used by stt-engine on AGP 8). Its `apiCheck` fails on *any* diff, additive or not, and its AGP 9 built-in-Kotlin support is unproven. **Don't use KGP `abiValidation {}`**: it's experimental and its docs say it doesn't apply to Android library modules.

---

## Official Anthropic / OpenAI SDKs vs raw OkHttp: confirm the contract (raw OkHttp)

**Verdict: raw OkHttp transports + kotlinx.serialization `JsonObject`. Don't use the SDKs.** (HIGH)

| Factor | `com.anthropic:anthropic-java` 2.66.0 / `com.openai:openai-java` 4.72.0 | Raw OkHttp (contract) |
|---|---|---|
| Transitive weight on every consumer APK | jackson-core/databind/annotations/jdk8/jsr310/module-kotlin, kotlin-reflect, victools jsonschema-generator (+jackson/swagger modules), swagger-annotations, errorprone, standardwebhooks (read from the POMs) | OkHttp + Okio (already in both apps) + kotlinx.serialization (already in both apps) |
| R8/reflection | Jackson + kotlin-reflect need keep rules and are reflection-heavy on Android | none beyond kotlinx.serialization's shipped rules |
| Provider neutrality | Two unrelated type systems. Mapping both into the neutral transcript model (6a) is *more* work than mapping raw JSON. OpenRouter needs the OpenAI SDK with a base-URL override anyway. | One neutral model + two thin JSON mappers. Byte-exact control of `cache_control` placement (A10 breakpoint parity). |
| Version churn | Very frequent releases (2.66, 4.72). Each bump would be a transitive change pushed onto consumers. | OkHttp floor frozen at 4.12 (A1) |
| Port fidelity | Rewrite | SB `AnthropicAgentLoop` and CT `AnthropicProvider` are already OkHttp + kotlinx JSON. The port is mechanical. |
| Typed failures | SDK exception hierarchies, re-mapped | Direct HTTP status/body → typed `UnavailableReason`-style reasons |

What you give up (and why it's fine): streaming helpers (v1.0 is non-streaming tool use), automatic retries (the engine's typed failures + tier escalation are the retry policy) and model-ID constants (model IDs come from policy/config per PROJECT.md).

---

## Installation (dependency declarations)

```kotlin
// :core
dependencies {
    api(libs.kotlinx.coroutines.core)          // 1.11.0
    api(libs.kotlinx.serialization.json)       // 1.11.0 — JsonObject appears in public seams
    testImplementation(libs.junit)             // 4.13.2
    testImplementation(libs.kotlinx.coroutines.test)
}
// :providers
dependencies {
    api(project(":core"))
    api(libs.okhttp)                           // 4.12.0 — plain requirement, NOT strictly{}, NOT a BOM
    testImplementation(libs.okhttp.mockwebserver) // 4.12.0 legacy okhttp3.mockwebserver
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
// :keystore
dependencies {
    api(project(":core"))
    implementation(libs.datastore.preferences) // 1.2.1
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
```

## Alternatives Considered

| Recommended | Alternative | When to Use the Alternative |
|---|---|---|
| Kotlin 2.3.20 / AGP 9.2.1 / Gradle 9.4.1 | Latest (Kotlin 2.4.20 / AGP 9.4.1 / Gradle 9.8.0) | When SB/CT/YAT move up. Bump in lockstep. If the compiler moves ahead of consumers, also set `apiVersion`/`languageVersion` and `coreLibrariesVersion` to the consumers' minor. |
| JVM 11 bytecode | JVM 17 (backup-engine/stt-engine style) | Only once every consumer compiles at ≥ 17 (today SB and YAT are at 11). |
| `:providers` as `kotlin.jvm` | Android library | Only if a transport ever needs `android.*` (e.g. `AndroidDns`/ECH from OkHttp 5.5, which is 5.x-only anyway). Put that in a separate Android module. |
| In-build matrix `Test` tasks | `-PokhttpTestVersion` + shell loop | If Gradle attribute plumbing for the KMP OkHttp variants misbehaves. Keep the guard test. |
| Metalava | BCV / KGP `abiValidation` | Pure-JVM repos with no Android modules. |
| detekt 1.23.8 syntax-only | detekt 2.0 (alpha.6) with type resolution | When 2.0 goes stable: re-evaluate in v1.1+ and bump across SB/YAT at the same time. |
| JUnit 4 | JUnit 5/6 + `mockwebserver3-junit5` | If the OkHttp floor ever rises to 5.x (a new amendment reversing A1). |
| Raw AndroidKeyStore AES/GCM + DataStore | Google Tink (`tink-android`) | If key material grows beyond a handful of API-key strings, or key rotation/keysets become a requirement. |
| Hand-written fakes (fake provider harness) | MockK / Mockito | Not recommended. The fake-provider harness is itself a v1.0 deliverable, and fakes test the seams honestly. |

## What NOT to Use

| Avoid | Why | Use Instead |
|---|---|---|
| `org.jetbrains.kotlin.android` plugin | AGP 9 has built-in Kotlin and is incompatible with it | `com.android.library` alone |
| Hilt / KSP / `javax.inject` in library code | Port-cleanup requirement. Libraries mustn't force DI (backup-engine and YAT ship Hilt, which is a pattern *not* to copy). | Plain constructors/builders. Apps wire their own DI. |
| `jvmTarget = 17` on published modules | Inline-bytecode compile error in SB (JVM 11) | `JVM_11` |
| `strictly("4.12.0")`, `okhttp-bom`, or any OkHttp 5 dependency in `:providers` main | Forces consumers' OkHttp version (A1/A11 violation) | Plain `api("…:okhttp:4.12.0")` |
| `okhttp3.internal.*`, `Call.executeAsync()` (okhttp-coroutines), `Request(url = …)` Kotlin ctors | Missing or unstable on one side of 4.12↔5.x | Public 4.12 API + hand-rolled `Call.await()` |
| `mockwebserver3` / subclassing `QueueDispatcher` | 5.x-only / not compatible across 4→5 | Legacy `okhttp3.mockwebserver`, `enqueue`, or a custom `Dispatcher` |
| `logging-interceptor` / any `HttpLoggingInterceptor` | Leaks keys and bodies (explicit "keep" discipline) | Typed telemetry events with no payloads |
| Anthropic/OpenAI Java SDKs, LangChain4j, Spring AI | See the comparison above | Raw OkHttp + JSON mappers |
| Ktor client | A second HTTP stack. The contract says OkHttp transports. (SB/CT have Ktor 3.5.2 for MCP, unrelated.) | OkHttp |
| `org.json` in `:core`/`:providers` | Not on a pure-JVM classpath. On Android JVM tests it's a stub ("method not mocked"). backup-engine uses it, but that's an AAR. | kotlinx.serialization `JsonObject` |
| `androidx.security:security-crypto` (`EncryptedSharedPreferences`) | Deprecated by Google in 1.1.0-alpha07 onward, with keyset-corruption history (MEDIUM, multiple secondary sources) | AndroidKeyStore `AES/GCM/NoPadding` + DataStore (the SB/CT `KeystoreCrypto` shape) |
| `kotlinx-coroutines-android` in `:core`/`:providers` | Breaks "pure Kotlin". OkHttp's own dispatcher does the I/O. | `kotlinx-coroutines-core`. Apps already have `-android`. |
| Turbine | No `Flow` API in v1.0 (telemetry = trace + callback) | Collect callback events into a list in tests |
| Robolectric | Nothing in v1.0 needs Android framework shadows. AndroidKeyStore isn't available under Robolectric anyway. | JVM tests via seams + one androidTest on the TESTER |
| detekt `detektMain`/type resolution, detekt 2.0 alpha, a baseline XML | False positives on Kotlin 2.3 metadata / alpha in a tag gate / "banked debt" | Plain `detekt`, zero issues, tuned config |
| BCV plugin / KGP `abiValidation` | See above | Metalava |
| Throwaway git tags to test JitPack | Tags are immutable (§11) | JitPack builds by commit SHA |
| Any bundled on-device model / ML Kit GenAI / AICore dependency in v1.0 | Out of scope (v1.1 spike, L10). The S22s have no AICore. | `ON_DEVICE` seam + capability gate in `:core` only |

## Stack Patterns by Variant

**If a consumer compiles at JVM 11 (SB, YAT):**
- The engine already targets 11, so nothing to do. Keep public `inline`/`reified` functions to a minimum anyway. They also lock their bodies into the consumer's bytecode, which makes behavior fixes need a consumer recompile.

**If a consumer stays on OkHttp 4.12.0 (CT until its A11 bump lands):**
- It gets `:providers` against 4.12.0, the default `test` leg. No exclude and no forced upgrade.

**If a consumer is on OkHttp 5.x (SB 5.2.1, CT after A11):**
- Gradle picks their 5.x plus the `okhttp-android` variant. It's covered by the 5.2.1 JVM leg + `:sample` Gate-1.

**If v1.1 adds `:voice-adapter` (→ `:stt`):**
- It must be its own published artifact that only opted-in apps pin. That's another reason consumers must not use the aggregator coordinate (F1).

## Version Compatibility

| Package | Compatible With | Notes |
|---|---|---|
| Kotlin 2.3.20 (engine) | Consumers on Kotlin 2.3.20 (SB, CT, YAT) and anything newer | A consumer on 2.2.x could still read 2.3 metadata (+1 rule); older ones can't. |
| AGP 9.2.1 | Gradle ≥ 9.1 (using 9.4.1), JDK ≥ 17, KGP ≥ 2.2.10 (using 2.3.20) | Built-in Kotlin on by default |
| OkHttp 4.12.0 (compile) | Runtime 4.12.x and 5.0–5.5 (proven per leg by the matrix) | 5.x = KMP (`okhttp-jvm`/`okhttp-android`), Okio 3.18.1, stdlib ≥ 2.1.21 |
| legacy mockwebserver 5.5.0 | JUnit 4.13.2, pulls `mockwebserver3` transitively | Still published and marked obsolete |
| detekt 1.23.8 | Gradle 9.4.1, Kotlin 2.3.20 projects in **syntax-only** mode | Embedded Kotlin 2.0.21 |
| Metalava plugin 0.5.x | AGP 9.2.1 Android libs (YAT-proven), Kotlin JVM modules (documented, verify here) | `--check-compatibility:api:released` |
| kotlinx.serialization 1.11.0 / coroutines 1.11.0 | Kotlin 2.3.20 | Same pins as SB and CT. CT force-pins serialization on androidTest classpaths because of an androidx BOM (`strictly 1.7.3`), which is unaffected by the engine. |
| DataStore 1.2.1 | minSdk ≥ 21 | Only one active DataStore per file per process. `:keystore` must own a dedicated file (or take an app-provided instance) and be a process singleton (see ARCHITECTURE/PITFALLS). |

## Roadmap Implications (for the scaffold phase)

1. The scaffold phase must **prove the three unproven pieces first**, before any contract code:
   - (a) a `kotlin.jvm` + AGP-9 built-in-Kotlin mixed build publishing JVM and AAR modules;
   - (b) JitPack resolving `:providers` with its transitive `:core` from a clean cache, by commit SHA;
   - (c) the OkHttp matrix tasks plus the guard test, green on all three legs with a trivial `Call.await()` test.
   These are cheap and front-load the only real stack risk.
2. Metalava/`api.txt` only becomes enforcing at the v1.0.0 cut. Explicit API mode + detekt are enforcing from day one.
3. The A1 must-pass lives in phase 3a, but its *harness* (matrix tasks, guard test, ForbiddenImport rules) belongs in phase 1 (scaffold). Then every transport test written in 3a/3b is automatically dual-tested.

## Sources

- Maven Central / Google Maven / Gradle Plugin Portal `maven-metadata.xml`, queried 2026-09-29 (primary, HIGH): OkHttp 5.5.0 (2026-08-16) and 5.2.1; KGP 2.4.20 latest / 2.3.20; coroutines 1.11.0; serialization 1.11.0 (1.12.0-RC); detekt 1.23.8 / dev.detekt 2.0.0-alpha.6; BCV 0.18.2; Turbine 1.2.1; AGP 9.4.1 latest / 9.2.1; DataStore 1.2.1; Robolectric 4.17; JUnit Jupiter 6.1.3; metalava plugin 0.5.1; anthropic-java 2.66.0; openai-java 4.72.0; androidx.test runner 1.7.0 / ext-junit 1.3.0; services.gradle.org current = 9.8.0.
- `okhttp-5.5.0.module`, `mockwebserver-5.5.0.module`/`.pom`, `mockwebserver-4.12.0.pom`, `anthropic-java-*-2.66.0.pom`, `openai-java-*-4.72.0.pom` (primary, HIGH): variant split, JUnit-4 dependency, SDK transitive trees.
- [OkHttp CHANGELOG.md](https://raw.githubusercontent.com/square/okhttp/master/CHANGELOG.md) (upstream primary): 5.0.0 artifact split, mockwebserver coordinates, `Response.body` non-null, `QueueDispatcher` incompatibility, "no binary breaks of non-alpha APIs".
- [JitPack BUILDING.md](https://raw.githubusercontent.com/jitpack/jitpack.io/master/BUILDING.md), [docs.jitpack.io/building](https://docs.jitpack.io/building/), [docs.jitpack.io/android](https://docs.jitpack.io/android/): multi-module coordinates, aggregator, env vars `JITPACK/GROUP/ARTIFACT/VERSION`.
- [jitpack#4112](https://github.com/jitpack/jitpack.io/issues/4112), [jitpack#4476](https://github.com/jitpack/jitpack.io/issues/4476): inter-module POM rewriting and `.module` metadata issues (LOW, community reports).
- [developer.android.com: migrate to built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin), [AGP 9.0 release notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes): built-in Kotlin, KGP 2.2.10 runtime floor, Gradle 9.1/JDK 17 minimums, `minCompileSdk` default, `uniquePackageNames`.
- [metalava-gradle README](https://github.com/tylerbwong/metalava-gradle) + `MetalavaExtension.kt` / `MetalavaCheckCompatibilityTask.kt` source: supported module types, `--check-compatibility:api:released`.
- [Kotlin Gradle binary compatibility validation](https://kotlinlang.org/docs/gradle-binary-compatibility-validation.html): experimental, not for Android library modules.
- [detekt#8865](https://github.com/detekt/detekt/issues/8865), [detekt#6198](https://github.com/detekt/detekt/issues/6198): 1.23.8 vs Kotlin 2.3 metadata; Kotlin-version override (MEDIUM/LOW).
- [Android security-crypto releases](https://developer.android.com/jetpack/androidx/releases/security) + secondary write-ups: `EncryptedSharedPreferences` deprecation (MEDIUM).
- Local, proven builds (HIGH): `~/Projects/Reusable/android/backup-engine` (jitpack.yml, `singleVariant` publishing form), `~/Projects/Reusable/android/yahirandroidtaste` (AGP 9.2.1 + Gradle 9.4.1 + JitPack + detekt 1.23.8 + Metalava 0.5.0, empty baseline), `~/Projects/Reusable/stt-engine/android` (multi-module JitPack install list, BCV on AGP 8), SB and CT `gradle/libs.versions.toml` + `app/build.gradle.kts` (Kotlin 2.3.20, AGP 9.2.1, Gradle 9.4.1, JVM 11 in SB / 17 in CT, minSdk 35, compileSdk 36.1, coroutines/serialization 1.11.0, OkHttp 5.2.1 / 4.12.0, JitPack per-module coordinate comments in SB's catalog).

---
*Stack research for: Android/Kotlin JitPack library: multi-provider LLM voice-command engine*
*Researched: 2026-09-29*
