# Phase 18: Voice Adapter - Pattern Map

**Mapped:** 2026-10-06
**Files analyzed:** 17 new/modified
**Analogs found:** 16 / 17

All analog paths below were checked as git-tracked source (no gitignored mirrors).

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `voice-adapter/build.gradle.kts` | config (AAR module) | build/publish | `keystore/build.gradle.kts` | exact |
| `voice-adapter/api.txt` | config (API seed) | n/a | `undo/api.txt` | exact |
| `voice-adapter/src/main/kotlin/.../voiceadapter/FinalSegmentMapping.kt` | utility (mapper) | transform | RESEARCH "Mapper" + `core/.../CommandInput.kt:13-18` | role-match |
| `voice-adapter/src/main/kotlin/.../voiceadapter/LanguageLabels.kt` | utility | transform | SB `SttAutoLanguage.normalizeLabel` (external, see RESEARCH) | role-match |
| `voice-adapter/src/test/kotlin/.../FinalSegmentMappingTest.kt`, `LanguageLabelsTest.kt`, `RedactionTest.kt` | test | transform | `keystore/src/test/kotlin/.../AesGcmTest.kt` (JUnit4 JVM layout) | role-match |
| `scripts/modules.list` | config | manifest | itself (`keystore` row) | exact |
| `settings.gradle.kts` (include + exclusiveContent) | config | build | itself | exact |
| `gradle/invariants.gradle.kts` (allowedEdges, ML scope, new `verifySttConfined`) | config/gate | build verification | `verifyNoDi` same file L315-334 | exact |
| `voice-adapter/build.gradle.kts` task `verifyAdapterSttCompileOnly` | gate | POM/module.json scan | `verifyDatastoreIsApi` keystore L59-95 | exact |
| `jitpack.yml` | config | build | itself | exact |
| `gradle/libs.versions.toml` (`stt-engine` entry) | config | build | `datastore-prefs` entry | exact |
| `scripts/verify-api-seed.sh` (aar task branch) | script | batch | itself L34 | exact |
| `scripts/verify-negative-controls.sh` (adapter plants) | script | batch | its own Part 2 plants (`backup`/`restore` + `expect_task_red`) | exact |
| `scripts/jitpack-dry-run.sh` | script | batch | IN-06 fix: line 44 already has `LC_ALL=C sort`; verify only | n/a |
| `core/.../commit/CommitSink.kt` (RT-01 KDoc only) | model | n/a | itself L31-41 | exact |
| `core/src/test/.../ActionEventTest.kt` (sentinel test) | test | event-driven | itself L40-70 | exact |
| `ECOSYSTEM.md`, `INTEGRATION.md`, ROADMAP wording (D-05) | docs | n/a | existing rows / INTEGRATION §11 `kts` fence | role-match |

## Pattern Assignments

### `voice-adapter/build.gradle.kts` (config, AAR publish)

**Analog:** `keystore/build.gradle.kts`. Copy L1-33 verbatim, changing only the namespace. Drop `testInstrumentationRunner`, the `DelicateKeyAccess` opt-in (L35-42), `api(libs.datastore.prefs)`, androidTest deps, the `assembleDebugAndroidTest` hook (L55), `verifyDatastoreIsApi`, and `verifyNoDataStoreCreation`.

```kotlin
plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
    alias(libs.plugins.detekt)
    alias(libs.plugins.metalava)
}
android {
    namespace = "io.github.ygaray.voiceactionengine.voiceadapter"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    defaultConfig { minSdk = 35 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
    publishing { singleVariant("release") { withSourcesJar() } }   // keystore L19-22
}
kotlin {                                                            // keystore L26-33
    explicitApi()
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        freeCompilerArgs.add("-Xjdk-release=11")
    }
}
dependencies {
    api(project(":core"))
    compileOnly(libs.stt.engine)          // never published
    testImplementation(libs.stt.engine)   // compileOnly is not on the unit-test classpath
    testImplementation(libs.junit)
}
```

**Publication tail** (keystore L115-129; artifactId literal is required by the manifest gate):
```kotlin
val engineGroup: String by rootProject.extra
val engineVersion: String by rootProject.extra
publishing { publications { register<MavenPublication>("release") {
    groupId = engineGroup; artifactId = "voice-action-engine-voice-adapter"; version = engineVersion
    afterEvaluate { from(components["release"]) }
} } }
apply(from = rootProject.file("gradle/invariants.gradle.kts"))
```
The manifest gate also greps the literal `api(project(":core"))`.

### `verifyAdapterSttCompileOnly` (gate, in `voice-adapter/build.gradle.kts`)

**Analog:** `keystore/build.gradle.kts:59-95` (`verifyDatastoreIsApi`). Keep its skeleton unchanged:

```kotlin
dependsOn("generatePomFileForReleasePublication", "generateMetadataFileForReleasePublication")
val dir = layout.buildDirectory.dir("publications/release")
doLast {
    val pom = dir.get().file("pom-default.xml").asFile
    if (!pom.isFile) throw GradleException("no generated POM found (vacuous)")
    val deps = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pom).getElementsByTagName("dependency")
    ...
    val module = dir.get().file("module.json").asFile
    if (module.isFile) { val variants = (groovy.json.JsonSlurper().parse(module) as Map<*, *>)["variants"] as List<*> ... }
}
tasks.named("check") { dependsOn(verifyDatastoreIsApi) }
```

Change the assertions:
- Non-vacuity: POM must contain `voice-action-engine-core`.
- Inversion: no `<dependency>` and no `module.json` `dependencies[*].module` may match `voice-engine-android*` or groupId `com.github.Ygaray.voice-engine-android`. Match on `voice-engine-android`, never on `Ygaray`, because the hub's own group `com.github.Ygaray.voice-action-engine` must remain.
- Classpath checks: `releaseRuntimeClasspath` has no component in that group, and `releaseCompileClasspath` does (non-vacuity). Use the `resolutionResult.allComponents` pattern from the next section.

### `verifySttConfined` (gate, in `gradle/invariants.gradle.kts`)

**Analog:** `verifyNoDi`, `gradle/invariants.gradle.kts:314-334`. Add it next to `verifyNoDi` as a copy with a different deny set, skipped when `project.name == "voice-adapter"`, wired into `check`.

```kotlin
val deniedDiGroups = setOf("com.google.dagger", ...)
val verifyNoDi = tasks.register("verifyNoDiArtifacts") {
    group = "verification"
    val modulePath = project.path
    val classpathNames = if (plugins.hasPlugin("com.android.library")) {
        listOf("releaseCompileClasspath", "releaseRuntimeClasspath")
    } else { listOf("compileClasspath", "runtimeClasspath") }
    val classpaths = classpathNames.map { configurations.named(it) }
    doLast {
        val hits = classpaths.flatMap { cp ->
            cp.get().incoming.resolutionResult.allComponents.mapNotNull { it.moduleVersion }
                .filter { it.group in deniedDiGroups }.map { "${cp.name}: ${it.group}:${it.name}:${it.version}" }
        }.distinct()
        if (hits.isNotEmpty()) throw GradleException("$modulePath resolves DI artifacts:\n" + ...)
    }
}
tasks.named("check") { dependsOn(verifyNoDi) }
```
Deny group: `com.github.Ygaray.voice-engine-android`. Also assert the classpath resolved something (non-vacuity).

### `gradle/invariants.gradle.kts` row edits

Edit L272-277 (`allowedEdges`) and L338 (ML-denial scope):
```kotlin
":undo" to emptySet<String>(),
":voice-adapter" to setOf(":core"),          // manifest gate greps this literal
...
if (project.name in setOf("core", "providers", "keystore", "undo", "voice-adapter")) {   // Pitfall 7
```
Leave `sampleRequiredEdges` and `sampleAllowedEdges` (L278-279) untouched, because `:sample` is not wired to the adapter (RESEARCH A2).

### `settings.gradle.kts`, `scripts/modules.list`, `jitpack.yml`

- `scripts/modules.list`: append `voice-adapter aar voice-action-engine-voice-adapter voiceadapter yes`. The `keystore aar ... yes` row is the model.
- `settings.gradle.kts`: change the include line to `include(":core", ":providers", ":keystore", ":undo", ":voice-adapter", ":sample")`. Keep the `":undo", ` prefix, which the selftest sed relies on. Inside `dependencyResolutionManagement.repositories` (currently only `google(); mavenCentral()`), add:
```kotlin
exclusiveContent {
    forRepository { maven { url = uri("https://jitpack.io") } }
    filter { includeGroup("com.github.Ygaray.voice-engine-android") }
}
```
- `jitpack.yml` single install line: append ` :voice-adapter:publishReleasePublicationToMavenLocal` after the `:undo:...` task. Never name `:sample`.
- `gradle/libs.versions.toml`: model on `datastore = "1.2.1"` (L11) and `datastore-prefs = {...}` (L25). Add `stt-engine = { group = "com.github.Ygaray.voice-engine-android", name = "voice-engine-android", version = "v0.7.0" }` with a "documented MINIMUM, never raise silently" comment. Per-module coordinate only.

### `voice-adapter/api.txt`

**Analog:** `undo/api.txt`, which contains exactly `// Signature format: 4.0` (header-only seed). It must be `git add`ed before the manifest gate runs (Pitfall 4).

### `FinalSegmentMapping.kt` and `LanguageLabels.kt` (utility, transform)

**Analog:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/CommandInput.kt:13-18`: `CommandInput(transcript, language = null, context = null, parentRunId = null)`. Use the RESEARCH "Mapper" snippet (RESEARCH L340-374). Rules:
- Package `io.github.ygaray.voiceactionengine.voiceadapter`. Hygiene check (a) requires it.
- Explicit `public` everywhere (explicitApi Strict).
- Overloads, not default parameters (additive-only API).
- Text verbatim, no trim.
- Normalizer is SB's closed set: `when (raw?.trim()?.lowercase()) { "en" -> "en"; "es" -> "es"; else -> null }`.
- Never stringify a `FinalSegment` (its `toString()` prints the transcript).
- Keep the stt-free functions in a separate `@file:JvmName("SttLanguageLabels")` file from the `FinalSegment` extension (`@file:JvmName("FinalSegmentCommandInput")`). This is a precaution.
- Do not use `Locale`.

### Adapter tests (test, transform)

**Analog:** `keystore/src/test/kotlin/.../AesGcmTest.kt` for the plain JUnit4 JVM layout (`testImplementation(libs.junit)`). No Robolectric and no coroutines-test. Construct segments directly with `FinalSegment("hola", 0, "es")`. Cases to cover:
- Verbatim text, including whitespace and blank text.
- `"en"`, `"es"`, `" ES "` map to en/es.
- `"en-US"`, `"auto"`, `""`, `"fr"` and null map to null, never a default.
- Context and parentRunId pass through by identity.
- A sentinel transcript never appears in `CommandInput.toString()`.

### `ActionEventTest.kt` (RT-01 sentinel test) and `CommitSink.kt` KDoc

**Analog:** the same file, `ActionEventTest.kt:28-70`. The existing pattern is `FakeMutation(toolName=..., behavior={ StepResult("deleted", false, token, mapOf(...)) }, targetIds=..., context=...)`, `RecordingCommitSink`, and `pipelineOf(ScriptedGate.admitAll(), sink, { _, session -> session.submit(ToolStep.Mutation(write)); StrategyOutcome.Completed("ok") })`. It runs inside `runTest { NoNetworkGuard.during { ... } }`. Build the event the same way, with a sentinel in the token, `targetIds` values, provider call id and `context.toString()`, then `assertFalse(sink.actions.single().toString().contains(SENTINEL))`.

`CommitSink.kt:31-41` currently reads:
```kotlin
/** Prints only whether a held run id is set, never its value. */
override fun toString(): String =
    "ActionEvent(runId=$runId, parentRunId=$parentRunId, heldRunId=${if (heldRunId == null) "null" else "set"}, action=$action)"
```
Keep the output shape, because `HeldRunIdTest:182-184` pins `heldRunId=set`/`null`. Only replace the KDoc with the policy sentence. Do not invent a `<redacted:N chars>` format; no field is an arg value (RESEARCH L258).

### `scripts/verify-api-seed.sh`

**Analog:** itself, L34: `TASK=":$MODULE:metalavaCheckCompatibility"`. Branch on `vae_module_field "$MODULE" packaging`: `aar` gives `metalavaCheckCompatibilityRelease`. The module is already read through `vae_module_field "$MODULE" kotlinPackage` at L21. The script runs `--offline`, so `:stt` v0.7.0 must be in the Gradle cache. Do the first resolution online (Pitfall 3).

### `scripts/verify-negative-controls.sh`

**Analog:** its existing Part 2 plants (backup, restore, `expect_task_red`). Add two plants:
- Flip `compileOnly(libs.stt.engine)` to `implementation`. Expect `:voice-adapter:verifyAdapterSttCompileOnly` red.
- Add an `:stt` or `:voice-adapter` edge to `:providers`. Expect `verifySttConfined` and `verifyModuleGraph` red.

Run only in a quiet window. The script is Gradle-heavy.

### `scripts/jitpack-dry-run.sh`

IN-06 fix is already present: L44 pipes through `LC_ALL=C sort`. Confirm only; no edit is needed.

## Shared Patterns

### Low-memory Gradle invocation
**Source:** `scripts/verify-api-seed.sh:32-33`. Apply to every Gradle-running plan, and run one such plan per wave.
```bash
export GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx1536m"
```

### Manifest-driven plumbing
**Source:** `scripts/modules.list` via `scripts/lib/modules.sh`. Adding the row makes `verify-module-manifest.sh`, `verify-repo-hygiene.sh`, `jitpack-dry-run.sh`, `api-dump-isolated.sh`, `review-api-surface.sh` and `published_versions.py` pick the module up with no edit. Still hard-coded: `scripts/verify-docs-coverage.sh:110,127` and `scripts/run-sample-gate1.sh:275`. Neither is on the P18 path.

### Secrets and transcripts
Never log. Never stringify a `FinalSegment`. detekt forbids print, println and `printStackTrace`, and `Log` is a forbidden import.

## No Analog Found

| File | Role | Reason |
|---|---|---|
| `:stt`-typed mapper public surface | utility | No existing module exposes an external-hub type. Use the RESEARCH snippet. |
| `INTEGRATION.md` adapter section | docs | Use a `kts` fence (as §11 does) so `DocSnippetsTest` needs no `:stt` in `:sample`. P19 owns the full docs. |

## Planning Notes

- Wave shape (RESEARCH L454): W1 scaffold + plumbing + gates (Gradle); W2 mapper + tests (Gradle) in parallel with a docs plan (bash only); W3 RT-01 `:core` plan (Gradle).
- Do not edit `scripts/release-cut.sh` (P20 RT-02). Record in the P18 SUMMARY that the header-only `voice-adapter/api.txt` adds one more new-module reason for gates 10/12 alongside `:undo`.
- Public names (`toCommandInput`, `commandInputOf`, `normalizeSttLanguageLabel`) freeze at the v1.1.0 tag. Confirm them in plan review.

## Metadata

**Analog search scope:** `keystore/`, `undo/`, `gradle/`, `scripts/`, `core/src/test`, `settings.gradle.kts`, `jitpack.yml`
**Pattern extraction date:** 2026-10-06
