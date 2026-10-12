# Phase 18: Voice Adapter - Research

**Researched:** 2026-10-06
**Domain:** Android library module (AAR) that maps an `:stt` `FinalSegment` to `:core`'s `CommandInput`; build-plumbing rows for a new published module; one `:core` toString redaction (RT-01)
**Confidence:** HIGH (every build-plumbing and resolution claim was either read in this repo or proven by a throwaway Gradle probe run this session)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01 [minsdk]:** 35. Below API 34 `:stt` "auto" stamps a literal "en" without detection (SttEngineImpl.kt:1018), which the adapter can't distinguish from a real detection; lowering later is additive, raising after the tag breaks consumers. Module is a `com.android.library` AAR on `:keystore`'s recipe with explicit artifactId `voice-action-engine-voice-adapter`; JVM tests only (FinalSegment has a public ctor). — **Reversibility:** one-way — raising minSdk after the tag breaks consumers (lowering is additive) _(source: ai-auto)_
- **D-02 [stt-scope]:** compileOnly + POM-absence gate + documented minimum (avoids pushing OkHttp 5.2.1/corrections/coroutines-android and an :stt floor onto consumers — the A1 problem again). Repo: `exclusiveContent` jitpack.io filtered to `com.github.Ygaray.voice-engine-android`, per-module coordinate, never the aggregator. _(provisional — refresh at execution; depends on Phase 17)_ _(source: ai-auto)_
- **D-03 [gate]:** Phase 18 owns the :stt-specific gate and repo; Phase 17 the generic include/jitpack.yml/allowedEdges/manifest. _(provisional — refresh at execution; depends on Phase 17)_ _(source: ai-auto)_
- **D-04 [lang-norm]:** Closed set (matches SB `SttAutoLanguage.normalizeLabel` and CT `normalizeDetectedLanguage` today, so adopting the adapter changes no behavior). _(source: ai-auto)_
- **D-05 [surface]:** Single segment only (SB longest-wins and CT last-non-null differ; a frozen joiner fits neither). Update ROADMAP/consumer map: apps keep their own session aggregation. _(source: ai-auto)_
- **D-06 [mapping]:** Pure and total (CommandInput performs no validation; apps already guard blank text). _(source: ai-auto)_
- **D-07 [stt-semantics]:** Pass through + document now; raise the detected-vs-fallback signal to stt-engine via the orchestrator as a future `:stt` item (not this phase's scope). Also find the first `:stt` tag whose FinalSegment has `getLanguage()` for the documented minimum.
  - **Consumer condition (binding):** stt answer #9: detected-vs-fallback signal deferred to stt v3.2 seed 7b1f660; nothing blocks v1.1. At minSdk 35, native `language == null` means undetected → pass labels through, treat null as unknown; document the server path's fallback-to-"en".

Runtime Decisions (appended to CONTEXT.md 2026-10-06):
- **[stt-scope] refreshed:** Unchanged: compileOnly on the stt engine, a POM-absence gate, and a documented minimum. Repo: exclusiveContent jitpack.io filtered to com.github.Ygaray.voice-engine-android, per-module coordinate, never the aggregator.
- **[gate] refreshed:** P17 shipped the generic plumbing, so P18 only ADDS rows to it (scripts/modules.list, gradle/invariants.gradle.kts allowedEdges, plus sampleRequiredEdges/sampleAllowedEdges if :sample wires it). jitpack.yml, verify-module-manifest.sh and verify-release-manifest.sh pick it up from the manifest. P18 owns ONLY the :stt-specific parts: the POM-absence gate and the exclusiveContent repo. Release-cut gates 10/12 and selftest step 4 are P20 work (20-CONTEXT RT-02) and also need the new module branch.
- **RT-01 [actionevent-tostring]:** If it fits in P18, implement redact-by-default ActionEvent.toString() (ids, type, tier, status and counts only; never arg values, utterance text, model output or keys, which render as <redacted:N chars>; any debug accessor is opt-in) plus one sentinel-never-in-toString test. Otherwise P20 does it.

### Claude's Discretion
Areas marked `ai-auto` took research's recommendation without operator review; the planner may refine mechanics within the stated decision but must not reverse it without a new discuss pass.

### Deferred Ideas (OUT OF SCOPE)
- stt detected-vs-fallback label signal (`languageSource`/`languageDetected`) — deferred to stt v3.2 seed 7b1f660.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ADPT-01 | the `:voice-adapter` module (`voice-action-engine-voice-adapter`) maps an `:stt` v0.7.0 final segment, including its detected language, to `CommandInput`. `:core` still depends on no other hub. | Sections "stt types", "AAR forced", "Plumbing rows", "stt gates", "Language normalization", "Validation Architecture" |
</phase_requirements>

## Summary

`:stt`'s published artifact is an AAR, so `:voice-adapter` must be a `com.android.library` module (a `kotlin.jvm` module cannot put an AAR on its compile classpath). The `:keystore` recipe (AGP 9.2.1 built-in Kotlin, `singleVariant("release")`, JVM 11, minSdk 35, explicit API) is the template. The mapping itself is trivial and total: `CommandInput(transcript = segment.text, language = normalize(segment.language), context, parentRunId)`, with a closed-set `en`/`es` normalizer that is byte-for-byte what SB's `SttAutoLanguage.normalizeLabel` does (CT's is a stricter subset that is identical on real `:stt` output). The real work is build plumbing and two new gates.

I proved the risky part with a throwaway Gradle project (scratchpad, not in the repo): a `com.android.library` module on the repo's AGP 9.2.1 / Gradle 9.4.1 with `exclusiveContent` for the JitPack `:stt` group, `compileOnly` + `testImplementation` of `com.github.Ygaray.voice-engine-android:voice-engine-android:v0.7.0`, and the Metalava plugin. Result: compile OK; a JVM unit test that constructs `FinalSegment("hola", 0, "es")` ran and passed; the generated `pom-default.xml` and `module.json` contain zero occurrences of `voice-engine-android`; `releaseRuntimeClasspath` has no `:stt`; `releaseCompileClasspath` has `:stt` plus `kotlinx-coroutines-android:1.11.0` and `voice-engine-android-corrections:v0.7.0` transitively; `metalavaCheckCompatibilityRelease` and `metalavaGenerateSignatureRelease` both execute against the compileOnly AAR and write `io.github.ygaray.sttengine.FinalSegment` into the signature. So the D-02 design works exactly as decided.

P17 shipped the generic plumbing as data (`scripts/modules.list`), so adding the module is four edits (manifest row, `settings.gradle.kts` include + `exclusiveContent`, `invariants.gradle.kts` allowedEdges, `jitpack.yml` install line) plus the module's own files. Several scripts still hard-code module names or the jar-only seed task and need small extensions (listed in "Gates and scripts to extend"). RT-01 fits in P18 as a ~1 task `:core` change: the current `ActionEvent.toString()` already prints no arg value, so the work is a KDoc policy statement plus one sentinel test, no API or `HeldRunIdTest` change needed.

**Primary recommendation:** Scaffold `:voice-adapter` as a copy of `:keystore`'s build file (drop DataStore and androidTest, add `compileOnly`/`testImplementation` of the `:stt` v0.7.0 AAR), add the manifest row + exclusiveContent + allowedEdges + jitpack entry, add `verifySttConfined` / `verifyAdapterSttCompileOnly` gates, then implement the pure mapper with a closed-set normalizer and put RT-01 in a separate `:core` plan.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Segment to `CommandInput` mapping | `:voice-adapter` (new AAR) | — | The only place allowed to name an `:stt` type (L7/A7). |
| `CommandInput` type | `:core` | — | Unchanged; adapter depends on `:core` via `api(project(":core"))`. |
| Speech capture / `FinalSegment` production | `:stt` (external hub) | — | Out of scope; adapter consumes only the type. |
| Per-session aggregation (longest-wins / last-non-null) | Consumer app | — | D-05: not frozen into the adapter. |
| "Only `:voice-adapter` depends on `:stt`" proof | Gradle build gates (`invariants.gradle.kts` + `:voice-adapter` build file) | Bash manifest gate | A project-edge gate cannot see `:stt` (an external binary dep); needs a resolved-classpath + published-POM scan. |
| Redacted `ActionEvent.toString()` | `:core` | — | RT-01. |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| AGP `com.android.library` | 9.2.1 | AAR module | Repo pin; `:keystore` proves the recipe `[VERIFIED: gradle/libs.versions.toml]` |
| Kotlin (built-in via AGP 9) | 2.3.20 | Language | Repo pin; the `:stt` v0.7.0 POM also pins `kotlin-stdlib 2.3.20` `[VERIFIED: jitpack POM fetched this session]` |
| `:stt` AAR `com.github.Ygaray.voice-engine-android:voice-engine-android:v0.7.0` | v0.7.0 | `FinalSegment` type, `compileOnly` + `testImplementation` | D-02 `[VERIFIED: jitpack.io POM + .module HTTP 200, packaging aar]` |
| `junit:junit` | 4.13.2 | JVM unit tests | Repo pin `[VERIFIED: libs.versions.toml]` |
| Metalava plugin | 0.5.1 | api.txt seed / compat check | Repo pin; proven on a compileOnly-AAR module in the probe `[VERIFIED: probe run]` |
| detekt | 1.23.8 | Lint (plain `detekt`, syntax only) | Repo pin |

### Supporting
None new. No coroutines-test needed (mapper is pure, non-suspend). Do NOT add `coroutines-android`, OkHttp, or `:stt`'s transitive deps to the module.

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `compileOnly` `:stt` | `api` `:stt` at v0.7.0 floor | Rejected by D-02: pushes `:stt`, OkHttp 5.2.1 (runtime scope in its POM), coroutines-android and corrections onto every consumer. |
| AAR module | `kotlin.jvm` module | Not possible: the `:stt` variant is `libraryelements=aar`; a JVM module cannot resolve it for compile. |

**Installation:** no new libs in `libs.versions.toml` are strictly required. Recommended: add a catalog entry `stt-engine = { group = "com.github.Ygaray.voice-engine-android", name = "voice-engine-android", version = "v0.7.0" }` with a comment "documented MINIMUM, never raise silently" so the version lives in one place `[ASSUMED: catalog entry is a style choice]`.

**Version verification:** `v0.7.0` is a real, immutable mirror tag: `git ls-remote --tags https://github.com/Ygaray/voice-engine-android.git` lists `refs/tags/v0.7.0` (commit `0108c6d9...`), and `https://jitpack.io/com/github/Ygaray/voice-engine-android/voice-engine-android/v0.7.0/voice-engine-android-v0.7.0.pom` returned HTTP 200 this session `[VERIFIED: network fetch 2026-10-06]`.

## Package Legitimacy Audit

No npm/PyPI/crates package is introduced. The one new external artifact is the owner's own JitPack mirror (`Ygaray/voice-engine-android`, same owner as this repo, 8 tags v0.2.0..v0.7.0), resolved only through an `exclusiveContent` filter restricted to its exact group, so it cannot be dependency-confused by another repo. `gsd-tools package-legitimacy check` covers npm/pypi/crates and does not apply to a JitPack coordinate. Disposition: approved; the planner needs no `checkpoint:human-verify` for it.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| `com.github.Ygaray.voice-engine-android:voice-engine-android:v0.7.0` | JitPack | tags since v0.2.0 (2026) | n/a (owner's mirror) | github.com/Ygaray/voice-engine-android | owner-controlled | Approved |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

## stt types (research item 1)

**`FinalSegment`** `[VERIFIED: ~/Projects/Reusable/stt-engine/android/stt/src/main/kotlin/io/github/ygaray/sttengine/FinalSegment.kt:32-37, read this session]`:

```kotlin
public class FinalSegment @JvmOverloads constructor(
    public val text: String,
    public val segmentId: Int,
    public val language: String? = null,
) {
```
- Plain class (deliberately not a `data class`), public ctor, so JVM tests construct it directly. `toString()` is `"FinalSegment(text=$text, segmentId=$segmentId, language=$language)"` which prints the raw transcript: the adapter must never stringify a segment (also a pre-existing decision-map rule).
- Binary API (`stt/api/stt.api`): `public fun <init> (Ljava/lang/String;I)V`, `public fun <init> (Ljava/lang/String;ILjava/lang/String;)V`, `public final fun getLanguage ()Ljava/lang/String;`, `getSegmentId ()I`, `getText ()Ljava/lang/String;` `[VERIFIED: stt.api lines 30-40]`.
- KDoc contract for `language`: `en`/`es` on the server backend (per-segment server detection) or native backend on API 34+ (from a `HIGHLY_CONFIDENT` event only); `null` when no label was produced, always the case for a non-`"auto"` session.
- **First tag with `FinalSegment.language`:** mirror tag **v0.6.0**. Evidence: raw `FinalSegment.kt` at each mirror tag, `grep -c "val language"`: v0.2.0=0, v0.3.0=0, v0.3.1=0, v0.4.0=0, v0.4.1=0, v0.5.0=0, **v0.6.0=1**, v0.7.0 present `[VERIFIED: raw.githubusercontent.com fetch per tag, this session]`. (The local stt-engine checkout only has monorepo milestone tags v1.0/v2.0/v3.0; the JitPack tags exist only on the mirror.)
- **What v0.7.0 adds** over v0.6.0: native on-device bilingual `"auto"` detection (NBIL-01..11, `INTEGRATION.md:158`, `API.md:200`), i.e. `language` becomes meaningful on the native path on API 34+. So: the *compile* minimum is v0.6.0, the *documented semantic minimum* is v0.7.0 (what ADPT-01/SC1 name). Recommend the docs say "requires `:stt` v0.7.0 or newer; the `language` property exists since v0.6.0 but only carries native detections from v0.7.0".
- **Sub-34 fallback evidence:** `const val AUTO_SUB34_FALLBACK = "en"` at `SttEngineImpl.kt:1018` (internal/SttEngineImpl.kt) `[VERIFIED: v0.7.0 raw file grep shows line 1018]`. Server path "always en/es with an 'en' fallback at probability 0.0" is the CONTEXT/consumer-answer claim `[CITED: R-v1.1-CONSUMER-ANSWERS.md row 9]`, not re-derived here.

## AAR forced? (research item 2)

Yes. `[VERIFIED: jitpack POM]`: `<packaging>aar</packaging>`; `.module` variants carry `"org.gradle.libraryelements" : "aar"`. The `:stt` POM compile-scope deps are `kotlinx-coroutines-android:1.11.0`, `voice-engine-android-corrections:v0.7.0`, `kotlin-stdlib:2.3.20`; `okhttp:5.2.1` is `runtime` scope (so it does not reach a `compileOnly` classpath). Build `:voice-adapter` as `com.android.library`, namespace `io.github.ygaray.voiceactionengine.voiceadapter`, `minSdk = 35`, `compileSdk { version = release(36) { minorApiLevel = 1 } }`, JVM 11, `-Xjdk-release=11`, `explicitApi()`, `singleVariant("release") { withSourcesJar() }`, publication `release` from `components["release"]` inside `afterEvaluate`.

**The `:keystore` recipe to copy** `[VERIFIED: keystore/build.gradle.kts read this session]`: plugins block (`alias(libs.plugins.android.library)`, `maven-publish`, detekt, metalava), `android {}` block, top-level `kotlin { explicitApi(); compilerOptions { jvmTarget...; freeCompilerArgs.add("-Xjdk-release=11") } }`, `dependencies { api(project(":core")) ... }`, the `engineGroup`/`engineVersion` publication block, and the final `apply(from = rootProject.file("gradle/invariants.gradle.kts"))`. Drop: DataStore, `androidTestImplementation`, the `assembleDebugAndroidTest` check hook, `verifyDatastoreIsApi`, `verifyNoDataStoreCreation`, the `DelicateKeyAccess` opt-in. Keep `testImplementation(libs.junit)`; `testImplementation(testFixtures(project(":core")))` is optional (note: `:core`'s `testFixtures` project edge is already tolerated by `verifyModuleGraph` since it filters `ProjectDependency` paths).

`build.gradle.kts` dependency block (proven in the probe):
```kotlin
dependencies {
    api(project(":core"))
    // The :stt AAR is compile-only: it must never reach the published POM or .module (verifyAdapterSttCompileOnly).
    compileOnly(libs.stt.engine)
    testImplementation(libs.stt.engine)
    testImplementation(libs.junit)
}
```
Public API will expose `io.github.ygaray.sttengine.FinalSegment` in its signature (Metalava wrote it verbatim in the probe), so a consumer must have `:stt` on its own compile classpath. That is the documented requirement, not a bug.

## Plumbing rows to add (research item 3)

All facts below read this session.

| File | Exact change |
|------|--------------|
| `scripts/modules.list` | append `voice-adapter aar voice-action-engine-voice-adapter voiceadapter yes` (columns: `name packaging artifactId kotlinPackage dependsOnCore`; `modules.sh` allows `[a-z0-9-]` names, rejects duplicates by name or artifactId) |
| `settings.gradle.kts` | `include(":core", ":providers", ":keystore", ":undo", ":voice-adapter", ":sample")` (the manifest gate reads `^[[:space:]]*include\(` and `":[A-Za-z0-9_-]+"` tokens; keep `":undo", ` followed by the new token so the selftest's sed plant still lands) + the `exclusiveContent` block inside `dependencyResolutionManagement.repositories` (see below). `FAIL_ON_PROJECT_REPOS` means the repo must live in settings, not the module. |
| `gradle/invariants.gradle.kts` | `allowedEdges`: add `":voice-adapter" to setOf(":core"),` (the manifest gate greps the literal `":voice-adapter" to setOf(":core")`). Extend the ML-denial scope `if (project.name in setOf("core", "providers", "keystore", "undo"))` to include `"voice-adapter"` (SC4: no ML runtime on any published module). `sampleRequiredEdges`/`sampleAllowedEdges`: **leave unchanged** (recommendation below). |
| `jitpack.yml` | append ` :voice-adapter:publishReleasePublicationToMavenLocal` to the single install line (the manifest gate requires exactly the manifest set and never `:sample`) |
| `voice-adapter/build.gradle.kts` | must contain `maven-publish`, the literal `artifactId = "voice-action-engine-voice-adapter"`, `api(project(":core"))` (literal regex `api\(project\(":core"\)\)`), and `libs.plugins.android.library` (packaging check) |
| `voice-adapter/api.txt` | tracked file whose content is exactly `// Signature format: 4.0` (header-only seed, same as `undo/api.txt`; the manifest gate requires it tracked, i.e. `git add` before the gate runs) |
| `voice-adapter/src/main/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/` | must exist (manifest check 8) and every `.kt` under it must be in a package starting `io.github.ygaray.voiceactionengine` (hygiene check a) |
| `ECOSYSTEM.md` | add the module row; hygiene check b already greps `voice-action-engine-voice-adapter` (currently satisfied by the "planned" sentence at ECOSYSTEM.md:32) and the "planned for v1.1 and not yet published" sentence must be rewritten once `:undo` is already a table row and the adapter row is added |

`exclusiveContent` block (identical shape proven in the probe, settings scope):
```kotlin
exclusiveContent {
    forRepository { maven { url = uri("https://jitpack.io") } }
    filter { includeGroup("com.github.Ygaray.voice-engine-android") }
}
```
The `:stt` transitive `corrections` artifact is in the same group, so one `includeGroup` covers the whole compile closure. This repo's own published group (`com.github.Ygaray.voice-action-engine`) is different and is never served from this repo (never the aggregator `com.github.Ygaray`).

**`modules.list` effects that need no edit** (read from the scripts): `verify-module-manifest.sh` (checks 1-8 are all manifest-driven), `verify-repo-hygiene.sh` (a, b, c loop over `vae_modules`/`vae_artifacts`), `jitpack-dry-run.sh` (publishes from jitpack.yml, expects the manifest artifact set, `.aar` extension from packaging column), `api-dump-isolated.sh`, `verify-api-dump.sh`, `review-api-surface.sh`, `verify-negative-controls.sh` Part 1 (already uses `compileReleaseKotlin` for `aar`, and skips the JDK-16-API plant for `aar`), `verify-ml-denial-controls.sh` Part A, `jitpack-live-probe.sh`, `scripts/lib/published_versions.py` (dependsOnCore=yes requires a POM dep on `voice-action-engine-core` at the tag, which the adapter has). The manifest selftest (`--selftest`) copies every module dir automatically.

### Gates and scripts to extend (existing code that needs the new-module branch)

1. **`scripts/verify-api-seed.sh`** hard-codes `TASK=":$MODULE:metalavaCheckCompatibility"` and greps `^> Task $TASK$`. For an `aar` module the task is `metalavaCheckCompatibilityRelease` (probe `tasks --all` listed `…Debug` and `…Release` only). Make `TASK` depend on `vae_module_field "$MODULE" packaging`. Run it for `voice-adapter` as the seed proof (it uses `--offline` in an isolated copy, so `:stt` must already be in the Gradle cache; see Pitfall 3). `[VERIFIED: script read; Release task name VERIFIED by probe]`
2. **`scripts/verify-docs-coverage.sh`** line 127 (`for a in core providers keystore`) and `public_types` (line 110) enumerate three modules by hand. `:undo` is not there either; the adapter is optional for C01. Do not add the adapter to C01 unless README/INTEGRATION name it; C20 (API.md names every public top-level type) scans only core/providers/keystore sources, so adapter types need no API.md entry for the gate, but add a short one anyway. Kotlin fences in INTEGRATION need a `<!-- doc-snippet: -->` marker compiled by `:sample`'s `DocSnippetsTest`; **use a `kts` fence (as §11 does) for the adapter snippet** so no `:stt` dependency has to enter `:sample`. Full docs belong to P19 (DOC-02); P18 only needs the ECOSYSTEM row, a short INTEGRATION section, and the D-05 ROADMAP/consumer-map wording. `[VERIFIED: script and INTEGRATION.md §11 read]`
3. **`scripts/verify-repo-hygiene.sh`**: manifest-driven now (P17 summary said it hard-coded three; the current file reads `vae_modules`). Nothing to extend. `scripts/run-sample-gate1.sh:275` and `scripts/verify-docs-coverage.sh:110,127` are the remaining hard-coded lists; neither is on the P18 critical path.
4. **`scripts/verify-negative-controls.sh`**: add voice-adapter plants (Part 2 style): (a) `compileOnly(libs.stt.engine)` flipped to `implementation` -> `:voice-adapter:verifyAdapterSttCompileOnly` red; (b) `implementation(libs.stt.engine)` added to `providers/build.gradle.kts` (or `api(project(":voice-adapter"))` in `:providers`) -> `:providers:verifyModuleGraph` red / `verifySttConfined` red. Mirror the existing `backup`/`restore` + `expect_task_red` helpers. Heavy: run only in a quiet window.
5. **`scripts/release-cut.sh` gates 10/12 and selftest step 4** are P20 (RT-02). Adding a header-only `voice-adapter/api.txt` seed adds one more new-module reason for those same gates; record this in the P18 SUMMARY so P20 handles `:undo` and `:voice-adapter` together. Do NOT edit `release-cut.sh` in P18.
6. **`jitpack-consumer-probe.sh`** (clean-cache consumer probe): optional new `:adapteralone` consumer proving an app can resolve and compile the adapter with `:stt` added by the consumer. It needs network to `jitpack.io` for `:stt`, unlike the file-repo probe, so recommend deferring to P20's dry run `[ASSUMED: scope call]`.

**Phase 17 TODOs for P18:** none are written as TODOs. What P17 left explicitly for P18: (i) 17-REVIEW IN-06 notes the `jitpack-dry-run.sh` `found=` list uses a locale `sort` while `expected` uses `LC_ALL=C sort`, and names `voice-action-engine-voice-adapter` as the id that could reorder under a non-C locale; the fix is one word (`LC_ALL=C sort`) in `scripts/jitpack-dry-run.sh`, **do it in P18** since this module is the one that triggers it `[VERIFIED: 17-REVIEW.md:297-304]`; (ii) the RT-02 deliberate reds are P20's. STATE.md lists no P18-specific blocker.

## stt gates (research item 4)

Current gates cannot prove SC-2 for `:stt`: `verifyModuleGraph` only inspects `ProjectDependency` edges and `:stt` is an external binary dependency; `verifyCoreDependencyAllowlist` already proves `:core` has no `:stt` (it fails on any non-allowlisted component). New gates (D-03 says P18 owns them):

**G1 `verifyAdapterSttCompileOnly` (in `voice-adapter/build.gradle.kts`, modelled on `:keystore`'s `verifyDatastoreIsApi`, wired into `check`):**
- `dependsOn("generatePomFileForReleasePublication", "generateMetadataFileForReleasePublication")`, read `build/publications/release/pom-default.xml` and `module.json`.
- Assert: the POM exists and has a `<dependency>` whose artifactId is `voice-action-engine-core` (non-vacuity); **no** `<dependency>` whose artifactId is `voice-engine-android` or starts `voice-engine-android-`, or whose groupId is `com.github.Ygaray.voice-engine-android`; same for every `module.json` variant `dependencies[*].module`/`group`. Match on `voice-engine-android`, never on the substring `Ygaray` (this repo's own group is `com.github.Ygaray.voice-action-engine`, which must remain).
- Assert resolved classpaths: `releaseRuntimeClasspath` contains no component with group `com.github.Ygaray.voice-engine-android`; `releaseCompileClasspath` **does** contain `voice-engine-android` (non-vacuity: the dependency is really compile-visible). Probe evidence: runtime classpath = kotlin-stdlib only; compile classpath = stt + coroutines-android + corrections `[VERIFIED: probe]`.
- Probe-verified file names: `build/publications/release/pom-default.xml` and `build/publications/release/module.json`, the same paths `:keystore` already reads `[VERIFIED: keystore/build.gradle.kts:63-64 `dir.get().file("pom-default.xml")`/`"module.json"`, and probe `ls`]`.

**G2 `verifySttConfined` (in `gradle/invariants.gradle.kts`, applied to every published module, skips `voice-adapter`):** resolve the module's compile and runtime classpaths (`releaseCompileClasspath`/`releaseRuntimeClasspath` for Android, `compileClasspath`/`runtimeClasspath` for JVM, the same selection `verifyNoDi` already makes) and fail if any component has group `com.github.Ygaray.voice-engine-android` (`:stt`, `:recorder`, `:voicenotes`, `:transcription`, `:corrections` are all in that group). Do not skip the non-vacuity check the other gates use (assert the classpath resolved something). This is `verifyNoDi`'s exact pattern with a different deny group; add it next to `verifyNoDi`.

**G3 app-side proof (optional, recommended):** "an app that doesn't add `:voice-adapter` never pulls `:stt`". `:sample` is a real app that will deliberately not depend on the adapter. Cheapest proof: in the `core`-hosted repo-wide block, resolve `:sample`'s `releaseRuntimeClasspath` (and `debugRuntimeClasspath`) and fail on the `:stt` group. If the planner finds cross-project resolution awkward, G2 (no published module carries `:stt`) plus the POM-absence gate G1 already imply it, and a one-line bash assertion in `verify-module-manifest.sh` that `sample/build.gradle.kts` does not contain `voice-engine-android` is a viable cheap substitute `[ASSUMED: design choice]`.

**`:sample` wiring recommendation:** do **not** add `project(":voice-adapter")` to `:sample` in P18. Doing so would force `:stt` onto the sample's runtime classpath (it is `compileOnly` in the adapter) plus a JitPack repo in the sample, and Phase 19's legs have no speech leg. Consequences: `sampleRequiredEdges`/`sampleAllowedEdges` stay untouched; no sample changes; Phase 19 may revisit `[ASSUMED: recommended scope; D-03 allows "if :sample wires it"]`.

**JVM test classpath:** `testImplementation(libs.stt.engine)` is mandatory; `compileOnly` is not on the unit-test classpath. Proven in the probe: AGP unit tests got `FinalSegment` from the AAR's `classes.jar` and ran with no Robolectric or `isReturnDefaultValues`, because `FinalSegment` touches no Android API.

**Consumer instructions (document in INTEGRATION):**
```kts
// settings.gradle.kts (consumer): the :stt coordinate lives only on JitPack
dependencyResolutionManagement { repositories {
    exclusiveContent {
        forRepository { maven { url = uri("https://jitpack.io") } }
        filter { includeGroup("com.github.Ygaray.voice-engine-android") }
    }
    google(); mavenCentral()
} }
// app build file
implementation("com.github.Ygaray.voice-action-engine:voice-action-engine-voice-adapter:<version>")
implementation("com.github.Ygaray.voice-engine-android:voice-engine-android:v0.7.0") // the app already has :stt; v0.7.0 or newer
```
(Consumers already using the repo-wide JitPack block for the hub keep their own; note the hub's own group `com.github.Ygaray.voice-action-engine` also comes from JitPack, so a consumer's exclusiveContent must include both groups, or use a plain `maven("https://jitpack.io")`. State this in the doc `[ASSUMED: derived from the two groups both being JitPack-served]`.)

## Language normalization (research item 5)

D-04 closed set. Locations `[VERIFIED: read this session]`:
- **SB** `~/Projects/AndroidApps/Personal/SecondBrain/app/src/main/java/com/example/secondbrain/feature/cardeditor/SttAutoLanguage.kt`:
```kotlin
fun normalizeLabel(raw: String?): String? = when (raw?.trim()?.lowercase()) {
    ENGLISH_LABEL -> ENGLISH_LABEL
    SPANISH_LABEL -> SPANISH_LABEL
    else -> null
}
```
with `ENGLISH_LABEL = "en"`, `SPANISH_LABEL = "es"` (trim + case-insensitive exact match, returns the bare label; unknown stays null, "never a default").
- **CT** `~/Projects/AndroidApps/Personal/CalTracker_Android/app/src/main/java/com/caltracker/app/ui/voice/VoiceLogViewModel.kt:65-66`: `private fun normalizeDetectedLanguage(language: String?): String? = language?.takeIf { it == "en" || it == "es" }` (exact match only, no trim, no lowercase). Same in `ai/LogFoodRequestBuilder.kt:21-22`.

The adapter should implement SB's rule (superset of CT's). On real `:stt` output (labels are exactly `en`/`es` or null) the two are identical, so neither app's behavior changes. Do not use `Locale.forLanguageTag` or primary-subtag stripping (decision rejects it): `en-US`, `auto`, blank, `fr` -> null. Never default a null to `"en"`/`"es"`.

`CommandInput` fields `[VERIFIED: core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/CommandInput.kt:13-18]`:
```kotlin
public class CommandInput(
    public val transcript: String,
    public val language: String? = null,
    public val context: Any? = null,
    public val parentRunId: String? = null,
)
```
KDoc there: "`language` `"en"`, `"es"` or null when the caller does not know." The adapter's output language set is exactly that.

**Suggested public surface** (names are the planner's call `[ASSUMED]`; shape follows D-05/D-06 and the "additive-only" rule, so use explicit overloads, not default params, on every public function):
- `FinalSegment.toCommandInput(): CommandInput`, `FinalSegment.toCommandInput(context: Any?): CommandInput`, `FinalSegment.toCommandInput(context: Any?, parentRunId: String?): CommandInput` (segmentId dropped; text verbatim, not trimmed; pure and total).
- stt-type-free entry points in a **separate file/`@file:JvmName` facade** so an app can call them with no `:stt` class on its classpath at all: `commandInputOf(transcript: String, languageLabel: String?)` (+ context/parentRunId overloads) and `normalizeSttLanguageLabel(raw: String?): String?`. Separate facades matter because the `FinalSegment` extension's JVM facade class references `FinalSegment` in method signatures; keeping the stt-free functions out of that class avoids any reflective/verification coupling `[ASSUMED: precaution, not tested]`.
- Never call `FinalSegment.toString()`; never log.

## RT-01 ActionEvent.toString (research item 6)

Current definition `[VERIFIED: core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/CommitSink.kt:31-41]`:
```kotlin
public class ActionEvent internal constructor(
    public val runId: String,
    public val parentRunId: String?,
    public val action: ExecutedAction,
    public val heldRunId: String?,
) {
    /** Prints only whether a held run id is set, never its value. */
    override fun toString(): String =
        "ActionEvent(runId=$runId, parentRunId=$parentRunId, heldRunId=${if (heldRunId == null) "null" else "set"}, " +
            "action=$action)"
}
```
and `ExecutedAction.toString()` (same file, lines 71-74): `"ExecutedAction(position=$position, kind=$kind, applied=$applied, toolName=$toolName, targetIds=${targetIds.size}, context=${context?.let { it::class.simpleName }}, mutating=$mutating)"`.

Findings:
- It already prints **no** arg values, outcome tokens, provider call ids, `targetIds` values, or context contents (counts and the context's class name only). There is no tier, utterance or model-output field on these types. So the "redact by default" ruling is satisfied for every field that could leak; only the IN-04 inconsistency remains (`parentRunId` printed, `heldRunId` shown as `set`/`null`).
- `HeldRunIdTest` pins the current shape: `core/src/test/.../HeldRunIdTest.kt:182-184` asserts `childText.contains("heldRunId=set")`, `!childText.contains("heldRunId=${original.runId}")`, and `heldText.contains("heldRunId=null")` `[VERIFIED: grep output]`.
- **Effort: S (about one task, `:core:test` only).** Recommended minimal path, which needs no `HeldRunIdTest` change and no `core/api.txt` change (toString body is not in the signature file): keep the current output, replace the KDoc with the policy sentence ("ids and counts only; never arguments, outcome tokens, utterance text, model output or keys; run ids are engine-generated by default but the `runIds` seam is app code, so a held id is shown only as set or null"), and add one sentinel test in `ActionEventTest.kt` that builds an event whose `appOutcomeToken`, `targetIds` values, `providerCallId` and `context.toString()` all contain a unique sentinel string and asserts `event.toString()` does not contain it (use the existing `FakeMutation`/`RecordingCommitSink` fixtures as the other tests in that file do). The alternative of printing `heldRunId` (ruling allows "ids may be shown") would force a `HeldRunIdTest` edit and contradict IN-04's own point that `runIds` is app code; recommend against `[ASSUMED: judgment, ruling permits either]`.
- Note a literal reading of the ruling (`<redacted:N chars>` for arg values) is vacuous here: no field is an arg value. Say so in the plan so the executor does not invent a redaction format.
- Place it in its own plan: it touches only `:core` (a Gradle-running plan), so it must sit in a different wave from the module scaffold plan.

## Architecture Patterns

### System flow
```
:stt SttEngine.finals (Flow<FinalSegment>)    (app collects; adapter never touches the engine)
        |
        v   app calls ONE function per final segment
:voice-adapter  FinalSegment.toCommandInput(...)
        |   text -> transcript (verbatim)
        |   language -> normalizeSttLanguageLabel -> "en" | "es" | null
        v
:core  CommandInput(transcript, language, context, parentRunId)  ->  CommandPipeline.execute(...)

Build graph:  :voice-adapter --api--> :core        :voice-adapter --compileOnly--> :stt AAR (never published)
              :core/:providers/:keystore/:undo -> no :stt (verifySttConfined)
```

### Recommended structure
```
voice-adapter/
├── build.gradle.kts           # keystore recipe + compileOnly/testImplementation :stt + verifyAdapterSttCompileOnly
├── api.txt                    # header-only seed
└── src/
    ├── main/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/
    │   ├── FinalSegmentMapping.kt   # FinalSegment.toCommandInput overloads (only file naming an :stt type)
    │   └── LanguageLabels.kt        # stt-type-free commandInputOf + normalizeSttLanguageLabel
    └── test/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/
```
No `src/main/AndroidManifest.xml` is needed for a library with only a namespace (probe built without one) `[VERIFIED: probe]`.

### Anti-patterns
- `implementation`/`api` on `:stt`: pushes OkHttp 5.2.1 / coroutines-android / corrections onto consumers (A1 again). Gate G1 forbids it.
- A multi-segment joiner (D-05): apps keep their own aggregation.
- Trimming or validating text (D-06): total, verbatim.
- Matching the substring `Ygaray` in the POM gate: the hub's own group contains it.
- Using the aggregator coordinate `com.github.Ygaray:voice-engine-android` for `:stt`: it drags all five stt modules. Use the per-module coordinate.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| "Is `:stt` in the published POM" | A new bespoke publish inspector | Copy `:keystore`'s `verifyDatastoreIsApi` (same POM + `module.json` paths, XML parse, vacuity guards) | Already handles the `module.json`-absent escape hatch (`vaeDisableModuleMetadata`). |
| Resolved-classpath deny scan | New scanner | The `verifyNoDi` pattern (`resolutionResult.allComponents`, group deny set, non-vacuity) | Existing, reviewed pattern. |
| New-module wiring | Per-script edits | `scripts/modules.list` row | Every script already reads it. |
| API freeze | Hand-written api.txt | Header-only seed + Metalava, proven by `verify-api-seed.sh` | P17 D-09 pattern. |
| Language parsing | `Locale` / BCP-47 logic | Closed-set `when` on trimmed lowercase | Decision rejects tag parsing; matches SB. |

## Runtime State Inventory
Not a rename/refactor/migration phase. Omitted.

## Common Pitfalls

### Pitfall 1: kotlin.jvm module cannot consume the `:stt` AAR
**What goes wrong:** attribute-matching failure resolving `libraryelements=aar` for a JVM compile classpath. **Avoid:** `com.android.library` (D-01). **Warning sign:** "No matching variant" at `compileKotlin`.

### Pitfall 2: `.module` group differs from the POM/URL group
The `.module` file says `"group" : "com.github.Ygaray"` while the artifact path and POM group are `com.github.Ygaray.voice-engine-android`. Gradle resolved it fine in the probe with the exclusiveContent filter on the **path** group `com.github.Ygaray.voice-engine-android`. Do not "fix" the filter to `com.github.Ygaray`. `[VERIFIED: probe resolved and ran a test]`

### Pitfall 3: `--offline` scripts fail until `:stt` is cached
`verify-api-seed.sh`, `verify-negative-controls.sh`, `verify-ml-denial-controls.sh` all pass `--offline`. After adding the dependency the first resolution must be online (the probe run already populated `~/.gradle` for `v0.7.0`, `kotlinx-coroutines-android 1.11.0` and `voice-engine-android-corrections v0.7.0`, but a different Gradle home or a cleared cache will not have it). Make the first plan task's verification run online, then the others offline.

### Pitfall 4: manifest gate needs a tracked api.txt
`verify-module-manifest.sh` check 6 uses `git ls-files --error-unmatch`, so an untracked `voice-adapter/api.txt` fails. Commit (or `git add`) the seed before running the gate; same for the module's build file because the selftest `git add -A`s a pristine copy.

### Pitfall 5: public API freezes an `:stt` type
The signature file will contain `io.github.ygaray.sttengine.FinalSegment`. After v1.1.0 that is immutable (additive-only). Intentional, but call it out in the surface review.

### Pitfall 6: stringifying a `FinalSegment`
Its `toString()` includes the raw text. No `"$segment"`, no assertion messages that interpolate it, no log. The repo's scanner forbids print/println/Log, but not string templates; a sentinel test (`CommandInput.toString()` and adapter output never contain the transcript) is the check.

### Pitfall 7: `verifyNoMlArtifacts` scope list
`invariants.gradle.kts` scopes it by `project.name in setOf("core","providers","keystore","undo")`. A new module not added there silently has no ML-denial gate while `check` stays green. Add `"voice-adapter"`.

### Pitfall 8: jitpack-dry-run locale sort (IN-06)
`found=` pipes through bare `sort`; with `voice-action-engine-voice-adapter` vs `voice-action-engine-undo` a non-C locale can reorder. Fix with `LC_ALL=C sort` in the same plan that adds the module.

### Pitfall 9: `:stt`'s own `FinalSegment.language` meaning on the server path
Server path always emits en/es; a fallback "en" at probability 0.0 is indistinguishable from a detection. D-07: pass through, document, no code workaround. Do not add a heuristic.

## Code Examples

### Mapper (illustrative)
```kotlin
// Source: composed from D-04/D-06 and CommandInput.kt:13-18
@file:JvmName("FinalSegmentCommandInput")
package io.github.ygaray.voiceactionengine.voiceadapter

import io.github.ygaray.sttengine.FinalSegment
import io.github.ygaray.voiceactionengine.core.CommandInput

public fun FinalSegment.toCommandInput(context: Any?, parentRunId: String?): CommandInput =
    CommandInput(
        transcript = text,
        language = normalizeSttLanguageLabel(language),
        context = context,
        parentRunId = parentRunId,
    )
public fun FinalSegment.toCommandInput(context: Any?): CommandInput = toCommandInput(context, null)
public fun FinalSegment.toCommandInput(): CommandInput = toCommandInput(null, null)
```
```kotlin
@file:JvmName("SttLanguageLabels")
package io.github.ygaray.voiceactionengine.voiceadapter

private const val EN = "en"
private const val ES = "es"

public fun normalizeSttLanguageLabel(raw: String?): String? = when (raw?.trim()?.lowercase()) {
    EN -> EN
    ES -> ES
    else -> null
}
```
(`MagicNumber`-style detekt rules do not apply to string constants; `lowercase()` is locale-invariant in Kotlin, unlike `toLowerCase()`.)

### Gate G1 sketch
Copy `verifyDatastoreIsApi` from `keystore/build.gradle.kts`, replacing the "scopes == [compile]" assertion with the three assertions in the "stt gates" section.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `:stt` `FinalSegment(text, segmentId)` only | `FinalSegment.language` (v0.6.0), native auto-detect labels (v0.7.0) | stt 0.6.0 / 0.7.0 | Adapter can carry a language; native `null` means undetected at minSdk 35. |
| Per-app copy of label normalizer | One adapter normalizer | this phase | Apps may delete `SttAutoLanguage.normalizeLabel` / `normalizeDetectedLanguage` once they adopt it. |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Catalog entry for the `:stt` coordinate is the preferred place for the version | Standard Stack | Style only |
| A2 | Keep `:sample` unwired from `:voice-adapter` in P18 | stt gates | If the planner wires it, `sampleRequiredEdges/AllowedEdges` plus a JitPack repo and `:stt` runtime dep in `:sample` are needed |
| A3 | Separate JVM facades for the stt-free vs stt-typed functions | Language normalization | Cosmetic; no observed failure either way |
| A4 | Keep ActionEvent print shape, only add KDoc + sentinel test | RT-01 | Orchestrator may prefer printing `heldRunId`; costs a `HeldRunIdTest` edit |
| A5 | Defer the `:adapteralone` clean-cache probe to P20 | Gates to extend | P18 would then lack a clean-cache consumer proof (SC-1/SC-2 are still met by the gates) |
| A6 | A consumer's exclusiveContent must include both JitPack groups (hub group and `:stt` group) | stt gates | Doc wording only |
| A7 | Public function names (`toCommandInput`, `commandInputOf`, `normalizeSttLanguageLabel`) | Language normalization | Names freeze at the v1.1.0 tag; confirm in plan review |

## Open Questions

1. **Documented minimum wording: v0.6.0 vs v0.7.0.**
   - Known: `language` exists since mirror v0.6.0; native detections since v0.7.0; ADPT-01/SC1 say v0.7.0.
   - Recommendation: document "v0.7.0 or newer" (matches REQUIREMENTS) and add a one-line note that the property compiles against v0.6.0.
2. **Does P18 ship the app-side `:sample` classpath proof (G3)?**
   - Recommendation: yes if cheap (cross-project resolution in the `core` block); otherwise the bash manifest assertion substitute.
3. **ActionEvent heldRunId print shape (A4).** Recommendation: keep `set`/`null`; orchestrator already ruled "may be shown", so either passes.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 17 | Gradle | yes | 17 (daemon java-17-openjdk) | none needed |
| Gradle wrapper 9.4.1 / AGP 9.2.1 | build | yes | probe built with both | none |
| Android SDK (`ANDROID_HOME=/home/yahir/Android/Sdk`) | AAR module | yes | compileSdk 36.1 built in the probe | none |
| Network to `jitpack.io` | first resolution of `:stt`; later `--offline` scripts rely on the cache | yes | HTTP 200 this session | pre-populated `~/.gradle` cache from the probe run |
| Another Gradle daemon (other session) | memory | running, ~940 MB RSS | — | use single-use daemon flags below |
| Host memory | tight | 32 GB total, ~12 GB available at probe time | — | one Gradle-running plan per wave |

**Missing dependencies with no fallback:** none.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4.13.2 (AGP unit tests, `testDebugUnitTest`/`testReleaseUnitTest`); no Robolectric, no coroutines-test needed |
| Config file | `voice-adapter/build.gradle.kts` (does not exist yet); gates in `gradle/invariants.gradle.kts`; `config/detekt/detekt.yml` |
| Quick run command | `GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx1536m" ./gradlew --offline :voice-adapter:testDebugUnitTest` |
| Full suite command | same flags: `./gradlew --offline :voice-adapter:check :core:check` then `scripts/verify-module-manifest.sh`, `scripts/verify-repo-hygiene.sh`, `scripts/verify-docs-coverage.sh` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| ADPT-01 | text verbatim (incl. leading/trailing whitespace, blank text) and segmentId dropped | unit | `:voice-adapter:testDebugUnitTest --tests '*FinalSegmentMappingTest*'` | Wave 0 |
| ADPT-01 | `language` `"en"`->en, `"es"`->es, `" ES "`->es, `"en-US"`/`"auto"`/`""`/`"fr"`/null -> null; never a default | unit | `--tests '*LanguageLabelsTest*'` | Wave 0 |
| ADPT-01 | context and parentRunId overloads pass through by identity | unit | `--tests '*FinalSegmentMappingTest*'` | Wave 0 |
| ADPT-01 | `CommandInput.toString()` of the result contains no transcript sentinel | unit | `--tests '*RedactionTest*'` | Wave 0 |
| ADPT-01 / SC1 | public API surface is exactly the intended functions (no joiner) | gate | `scripts/verify-api-seed.sh voice-adapter` then Metalava dump review | Wave 0 |
| SC2 | POM and `module.json` name no `voice-engine-android*`; runtime classpath has no `:stt`; compile classpath does | gate | `:voice-adapter:verifyAdapterSttCompileOnly` | Wave 0 |
| SC2 | no other published module resolves the `:stt` group | gate | `:core:verifySttConfined :providers:verifySttConfined :keystore:verifySttConfined :undo:verifySttConfined` | Wave 0 |
| SC2 | `:core` still has no `:stt`/other-hub dependency | gate | `:core:verifyCoreDependencyAllowlist :voice-adapter:verifyModuleGraph` | exists (extend allowedEdges) |
| SC2 | manifest consistency incl. the new module | bash | `scripts/verify-module-manifest.sh` and `--selftest` | exists |
| SC2 | negative control: `compileOnly` -> `implementation` and a `:providers` `:stt` edge go red | negative control | `scripts/verify-negative-controls.sh` (heavy, quiet window) | extend |
| RT-01 | sentinel arg value / token / call id / context text never in `ActionEvent.toString()` | unit | `:core:test --tests '*ActionEventTest*' --tests '*HeldRunIdTest*'` | file exists, add test |
| Quality | detekt zero issues, explicit API, JVM-11 bytecode of the AAR, no DI/ML artifacts | gate | `:voice-adapter:check` | Wave 0 |

### Sampling Rate
- **Per task commit:** the single most relevant quick command (one Gradle task) or the bash gate for plumbing tasks.
- **Per wave merge:** `:voice-adapter:check :core:check` (one Gradle invocation, flags above).
- **Phase gate:** full suite plus `scripts/verify-api-seed.sh voice-adapter`, `scripts/verify-negative-controls.sh` in a quiet window.

### Which verifications need Gradle (host memory is tight; one Gradle-running plan per wave)
- **Gradle:** every `:voice-adapter:*` task, `:core:test`/`:core:check`, `scripts/verify-api-seed.sh`, `verify-negative-controls.sh`, `verify-ml-denial-controls.sh`, `jitpack-dry-run.sh`, `api-dump-isolated.sh`/`verify-api-dump.sh`.
- **Bash only (safe to run alongside a Gradle plan):** `scripts/verify-module-manifest.sh` (+ `--selftest`), `scripts/verify-repo-hygiene.sh`, `scripts/verify-docs-coverage.sh`, `scripts/verify-release-manifest.sh` (git + python, no Gradle).
- **Suggested wave shape:** W1 plan A scaffold + plumbing + gates (Gradle); W2 plan B mapper + tests (Gradle) and plan D docs/ECOSYSTEM/ROADMAP wording (bash only, parallel-safe); W3 plan C RT-01 `:core` change (Gradle). Plans A and B both edit `voice-adapter/` so they are sequential in any case.

### Wave 0 Gaps
- [ ] `voice-adapter/build.gradle.kts`, `api.txt` seed, source and test dirs (whole module)
- [ ] `scripts/modules.list` row, settings include + exclusiveContent, allowedEdges, jitpack line
- [ ] `verifyAdapterSttCompileOnly` and `verifySttConfined` gates, and their negative controls
- [ ] `scripts/verify-api-seed.sh` aar task-name branch
- [ ] ML-denial scope list gets `voice-adapter`
- [ ] `ActionEventTest` sentinel test

## Security Domain

`security_enforcement` is enabled (absent = enabled; config.json sets `true`, ASVS level 1).

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | — |
| V3 Session Management | no | — |
| V4 Access Control | no | — |
| V5 Input Validation | yes (light) | Closed-set language normalizer; total mapper; no parsing of untrusted structure |
| V6 Cryptography | no | — |
| V8 Data Protection (logging) | yes | Transcript text must not reach logs/exceptions/`toString()`; project rule "transcripts never reach logs, telemetry, exceptions or toString()" |
| V14 Config / supply chain | yes | `exclusiveContent` restricted to one JitPack group; `compileOnly` so `:stt` and its OkHttp 5.2.1 are not forced on consumers; immutable `v0.7.0` tag (never a branch) |

### Known Threat Patterns
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Transcript leak through `FinalSegment.toString()` or log | Information disclosure | Adapter never stringifies a segment; no logging (scanner forbids print/Log/printStackTrace); sentinel test |
| Dependency confusion on the `:stt` group | Tampering | `exclusiveContent` + per-module coordinate + pinned immutable tag |
| Transitive dependency bloat / hub leakage into `:core` | Elevation (scope creep) | G1/G2 gates plus existing `verifyCoreDependencyAllowlist`/`verifyModuleGraph` |
| Mislabeled language steering the model | Tampering (integrity of input) | Closed set; unknown -> null, never a guessed default |
| `ActionEvent.toString()` leaking arg values | Information disclosure | RT-01: ids/counts only, sentinel test |

## Sources

### Primary (HIGH confidence)
- Repo files read this session: `18-CONTEXT.md`, `v1.1-DECISION-MAP.md` (Phase 18), `R-v1.1-CONSUMER-ANSWERS.md` row 9, `gradle/invariants.gradle.kts` (full), `scripts/modules.list`, `scripts/lib/modules.sh`, `scripts/verify-module-manifest.sh`, `scripts/verify-api-seed.sh`, `scripts/verify-negative-controls.sh`, `scripts/verify-docs-coverage.sh`, `scripts/verify-repo-hygiene.sh`, `scripts/jitpack-dry-run.sh`, `scripts/release-cut.sh` (header/gates), `scripts/lib/published_versions.py`, `settings.gradle.kts`, `jitpack.yml`, `gradle/libs.versions.toml`, `keystore/build.gradle.kts`, `undo/build.gradle.kts`, `core/.../CommandInput.kt`, `core/.../commit/CommitSink.kt`, `17-01-SUMMARY.md`, `17-REVIEW.md` IN-04/IN-06.
- stt-engine repo (`~/Projects/Reusable/stt-engine/android`): `stt/.../FinalSegment.kt`, `stt/api/stt.api`, `stt/build.gradle.kts`, `settings.gradle.kts`, `jitpack.yml`, `INTEGRATION.md`.
- JitPack/GitHub network fetches 2026-10-06: `https://jitpack.io/com/github/Ygaray/voice-engine-android/voice-engine-android/v0.7.0/voice-engine-android-v0.7.0.pom` and `.module`; `git ls-remote --tags` on the mirror; `raw.githubusercontent.com/Ygaray/voice-engine-android/<tag>/stt/.../FinalSegment.kt` for v0.2.0..v0.7.0.
- **Gradle probe** (scratchpad, uses the repo's wrapper 9.4.1, AGP 9.2.1, Metalava 0.5.1): `:va:testDebugUnitTest` passed (1 test, 0 failures); generated POM/`module.json` contained no `voice-engine-android`; `releaseCompileClasspath` / `releaseRuntimeClasspath` trees as described; `metalavaCheckCompatibilityRelease` and `metalavaGenerateSignatureRelease` succeeded and emitted `io.github.ygaray.sttengine.FinalSegment` in `current.txt`.
- SB and CT sources: `SttAutoLanguage.kt`, `VoiceLogViewModel.kt:65-66`, `LogFoodRequestBuilder.kt:21-22`.

### Secondary (MEDIUM)
- `20-CONTEXT.md` RT-02/RT-03 text (orchestrator rulings), as quoted in the task context.

### Tertiary (LOW)
- none relied on.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH, resolved and run in a probe on the exact repo toolchain.
- Architecture / plumbing rows: HIGH, all scripts read; the manifest gate's literal greps are quoted.
- Pitfalls: HIGH for 1-8 (observed or read), MEDIUM for 9 (consumer-answer claim, not re-derived from stt server code).
- RT-01: HIGH on current code, MEDIUM on which print shape the orchestrator prefers.

**Research date:** 2026-10-06
**Valid until:** 2026-11-05 (stable; recheck if `:stt` publishes a tag above v0.7.0 or the manifest scripts change)
