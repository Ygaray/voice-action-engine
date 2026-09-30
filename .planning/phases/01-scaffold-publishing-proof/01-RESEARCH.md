# Phase 1: Scaffold & Publishing Proof - Research

**Researched:** 2026-09-30
**Domain:** Gradle multi-module Kotlin/Android library scaffold (kotlin.jvm + AGP 9 built-in Kotlin), JitPack per-module publishing, zero-baseline static gates, OkHttp 4.12/5.x test matrix
**Confidence:** HIGH for everything proven on the scratch prototype (build, gates, matrix, Metalava, publication metadata, consumer resolution from a local Maven repo). MEDIUM for JitPack behaviour with an explicit E5 groupId and jar modules (only stt-engine's AAR-only precedent exists; the live probe settles it).

> Method note. Every `[VERIFIED: scratch prototype]` claim below was run this session on a throwaway 4-module project (Gradle 9.4.1 wrapper, JDK 17.0.19, AGP 9.2.1, Kotlin 2.3.20, real network) in the scratchpad, outside the repo. All code shown under "Code Examples" is the tested text. Nothing in the repo working tree was modified, staged or committed, and nothing was pushed. JitPack cannot be exercised without a pushed commit, so the probe procedure is written out exactly (see "JitPack Probe Procedure").

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
(Copied verbatim from `01-CONTEXT.md` `## Implementation Decisions`.)

### artifact-ids
- **D-01 [artifact-ids]:** Prefixed artifactIds everywhere: `com.github.Ygaray.voice-action-engine:voice-action-engine-{core,providers,keystore}` (v1.1: `voice-action-engine-undo`, `voice-action-engine-voice-adapter`). Orchestrator ruling; E7 aligns A18. Fix ECOSYSTEM.md/README.md aggregator coordinate in this phase. _(source: human)_

### jitpack-metadata
- **D-02 [jitpack-metadata]:** Explicit E5 groupId, keep .module metadata, prove jar→jar and AAR→jar resolution by commit SHA from a clean cache (stt v0.6.0 proves only AAR→AAR on a tag); fallback: disable module metadata on JitPack _(source: ai-auto)_

### sample-fixture
- **D-03 [sample-fixture]:** Inert com.android.application shell (no publish, no config-time reads), excluded from jitpack.yml; fixture at sample/src/debug/assets/sb-a10-fixture.json (gitignored) with a runtime loud error, plus a repo-wide glob ignore for sb-a10-fixture*.json and /graphify-out/ _(source: ai-auto)_

### detekt-config
- **D-04 [detekt-config]:** buildUponDefaultConfig=true, maxIssues: 0, no baseline file (and a check that none exists); tune rules deliberately (e.g. allow the broad catch only in the single collapse helper) rather than banking debt _(source: ai-auto)_

### invariant-scan
- **D-05 [invariant-scan]:** detekt ForbiddenImport + ForbiddenComment for what works syntax-only, plus a source-scan Gradle task under check for runCatching/println/printStackTrace/FQ DI annotations/baseline-file existence, each proven with a planted negative control _(source: ai-auto)_

### structural-checks
- **D-06 [structural-checks]:** Allowlist + bytecode scan + compile-floor assertion as Gradle checks under check (the compile-floor assertion also closes Phase 4's catalog-creep gap) _(source: ai-auto)_

### metalava
- **D-07 [metalava]:** Apply Metalava with apiDump/apiCheck aliases (YAT), guard compatibility with onlyIf { api.txt exists } so enforcement switches on automatically when the cut commits the dump; add a javap supplement for value-class members at the cut _(source: ai-auto)_

### explicit-api
- **D-08 [explicit-api]:** -Xexplicit-api=strict compiler flag uniformly (no dependence on AGP 9's partial kotlin {} extension), verified by a planted public-without-modifier negative control _(source: ai-auto)_

### test-fixtures
- **D-09 [test-fixtures]:** java-test-fixtures on :core with the testFixtures variants skipped from components["java"], verified by inspecting the published POM/.module (no -test-fixtures artifact); Phase 1 is scaffolding only (no public placeholder types) _(source: ai-auto)_

### matrix
- **D-10 [matrix]:** In-build extra Test tasks (okhttp + legacy mockwebserver moved together, okhttp-jvm variant attributes), reflective version guard per leg; fallback to the -P switch if attribute plumbing fails; Call.await bridge stays in Phase 4 _(source: ai-auto)_

### ext-build
- **D-11 [ext-build]:** Needs external research/probe: kotlin.jvm 2.3.20 next to AGP 9.2.1 built-in Kotlin; JitPack jar→jar/AAR→jar on commit SHA (and whether the aggregator lists :sample); okhttp-jvm variant attributes on Gradle 9.4.1; Metalava 0.5.1 on kotlin.jvm with a missing api.txt; detekt ForbiddenComment regex on planted planning ids _(source: ai-auto)_

### r1-verdict
- **D-12 [r1-verdict]:** Publishing probes (jar→jar and AAR→jar via JitPack by commit SHA, Metalava on kotlin.jvm) run FIRST; if any fallback changes consumer coordinates or module shape, message the orchestrator before Phase 2. _(source: human — orchestrator R1 GO-WITH-CHANGES)_

### Claude's Discretion
Anything not listed above follows `.planning/research/SUMMARY.md` and the phase's own research; Source `ai-auto` decisions took research's recommendation (orchestrator accepted the auto-resolved remainder).

### Deferred Ideas (OUT OF SCOPE)
See REQUIREMENTS.md v2 / LATER items.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description (REQUIREMENTS.md) | Research Support |
|----|-------------|------------------|
| BLD-01 | Four modules; one-way deps `:sample → {:providers, :keystore} → :core`; `:core` has no HTTP/Android/DI/other-hub dep | Prototype builds all four; `verifyModuleGraph` (edge allowlist) + `verifyCoreDependencyAllowlist` both proven to bite (Code Examples 3, 4) |
| BLD-02 | Kotlin 2.3.20, AGP 9.2.1, Gradle 9.4.1, JDK 17 build; every published module emits JVM 11 bytecode | kotlin.jvm + AGP 9 coexist (flag 2); explicit `jvmTarget` is mandatory (Pitfall 6); `verifyBytecodeLevel` reads class-file major 55 from the jar and from the AAR's `classes.jar` (Code Example 4) |
| BLD-03 | Each module resolvable from JitPack at its per-module coordinate from an empty cache, `:providers` pulling `:core`; `jitpack.yml` explicit install list, never `:sample` | Publication recipe + local clean-cache consumer probe proven jar→jar and AAR→jar with real `.module`, JitPack-style rewritten `.module`, and POM-only (flag 3); live probe procedure + fallback ladder below |
| BLD-04 | `./gradlew check` runs detekt syntax-only, `maxIssues: 0`, no baseline, with invariant rules | detekt config + what is inert syntax-only (ForbiddenMethodCall) + scanner that replaces it; 40 planted gate-checks proven red (flag 5, Code Example 5) |
| BLD-05 | `explicitApi()` on every published module; Metalava on all three; `api.txt` dumps committed only at the `v1.0.0` cut | `explicitApi()` DSL works on all three incl. AGP 9 built-in Kotlin (deviation from D-08, see Decision Deviations); Metalava proven on kotlin.jvm and AGP 9 library (flag 1) |
| BLD-07 | Package root `io.github.ygaray.voiceactionengine.*` decided in step 1 | Directory/namespace layout in Recommended Project Structure; keystore namespace `io.github.ygaray.voiceactionengine.keystore` built clean |
| BLD-08 | `ECOSYSTEM.md` lists per-module coordinates; `graphify-out/` and the A10 fixture path gitignored | Exact `.gitignore` lines proven with `git check-ignore`; ECOSYSTEM/README text to write below |
| BLD-09 | Fake-provider harness in `:core` test sources only (unpublished) | `java-test-fixtures` recipe proven unpublished (POM/.module inspected, leak detected when `skip()` omitted); see Open Question 1 on what "fake provider" can be before Phase 2/3 types exist |
| CLN-01 | No DI-framework annotations in library code | ForbiddenImport (`dagger.*`, `javax.inject.*`, `jakarta.inject.*`, `androidx.hilt.*`) + FQ-annotation scanner rule, both proven |
| CLN-05 | No app planning ids in comments | `ForbiddenComment` `comments:` regex list works syntax-only for `//`, `/* */`, KDoc and trailing comments (flag 5); scanner double-covers it |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

Extracted from `./.claude/CLAUDE.md` (project) and `~/.claude/CLAUDE.md` (global). The planner must treat these as locked.

- `:core` depends on no other hub and has **no HTTP dependency** (L7, A7). Enforced by classpath, not convention.
- OkHttp **compile floor 4.12** (plain `api`, no `strictly`, no BOM, no `okhttp-bom`); CI green on 4.12.x and 5.x (A1). Catalog comment "never raise".
- Public API **strictly additive** once tagged; Metalava per module; `api.txt` first created at the `v1.0.0` cut.
- **Domain-free**: no note/card/food names in the library.
- detekt **zero baseline** on library modules; plain `detekt` task only (never `detektMain`/`detektTest`); no detekt 2.0 alpha; no `detekt-formatting`.
- **Secrets** (API keys, transcripts, tool args/results) never reach logs, telemetry, exceptions or `toString()`: no `android.util.Log`, no `println`, no `printStackTrace`, no logging interceptors.
- Toolchain: do **not** apply `org.jetbrains.kotlin.android` (AGP 9 built-in Kotlin); **no** `jvmToolchain()` / foojay; JVM 11 bytecode; `org.gradle.configuration-cache=false`; `org.gradle.caching=true`.
- No Hilt/KSP/`javax.inject` in library code; no `org.json` in `:core`/`:providers`; no `kotlinx-coroutines-android` in `:core`/`:providers`; no Turbine/Robolectric/MockK; no BCV and no KGP `abiValidation`; legacy `okhttp3.mockwebserver` + JUnit 4.13.2 only.
- No throwaway git tags to test JitPack (tags immutable); build by commit SHA.
- Process: contract changes only via §10 amendments through the orchestrator; tag cuts agent-owned (A12); never commit §11.
- Global: tailnet-only URLs; "Never hand a bare localhost URL"; Gate-1 device rules do not apply to Phase 1 (no device work).

## Summary

Phase 1 is almost entirely build-engineering, and the two riskiest unknowns are now mostly retired locally. On a throwaway prototype identical in shape to the planned repo (`:core` and `:providers` as `kotlin.jvm`, `:keystore` as `com.android.library` on AGP 9.2.1 built-in Kotlin, `:sample` as an inert `com.android.application`), a single `./gradlew check` goes green in about 45 s warm, and every gate the phase must ship was proven to bite with a planted violation. The `kotlin.jvm` 2.3.20 plugin coexists with AGP 9.2.1 built-in Kotlin in one build with no special handling, Metalava 0.5.1 works on both module types (but **hard-fails** when `api.txt` is missing, so the `onlyIf` guard in D-07 is mandatory), and the OkHttp matrix works as in-build `Test` tasks with `okhttp-jvm` resolved automatically.

Several premises in CONTEXT needed correction and are flagged under "Decision Deviations": `kotlin { explicitApi() }` **does** work on the AGP 9 module (so the raw `-Xexplicit-api=strict` flag in D-08 is unnecessary, and it is actively worse because the flag also hits `test`/`testFixtures` compilations); detekt's `ForbiddenMethodCall` is a **silent no-op** syntax-only (so the source scanner is not optional); `ForbiddenComment` `values:` is deprecated but its replacement `comments:` accepts regex and works syntax-only; and `OkHttp.VERSION` is a Kotlin `const` that is inlined into the 4.12-compiled test bytecode, so the "reflective" version guard must genuinely reflect or every 5.x leg reports 4.12.0.

What only a live probe can settle is JitPack's indexing of an explicit `com.github.Ygaray.voice-action-engine` groupId and of **jar** modules next to an AAR. Strong circumstantial evidence exists: stt-engine's live JitPack artifacts show JitPack rewriting inter-module group, module and version in both POM and `.module` and rewriting every `.module` component id to `GROUP:repoName:VERSION`; Gradle 9.4.1 + AGP 9.2.1 tolerates that mismatch (resolved from real jitpack.io from an empty cache), and the same rewrite applied to the prototype's jar and AAR `.module` files also resolves. Both cheaper fallbacks (declare `com.github.Ygaray` like stt; publish POM-only) were verified locally and **do not change consumer coordinates or module shape**. Only two contingencies would (making the JVM modules AARs, or splitting repos), and there is no evidence either will be needed.

**Primary recommendation:** Wave 1 pushes a minimal publish-only skeleton (three publishable modules + inert `:sample` + `jitpack.yml`) and runs the JitPack probe by commit SHA before any gate work (D-12); Wave 2+ lands the gates exactly as tested in Code Examples, using `explicitApi()` (not the flag), `comments:` regex ForbiddenComment, the source scanner, a truly reflective matrix guard, and `testFixtures` with `skip()` plus a leak check.

## Research Flag Resolution (the six open items)

| # | Open item | Result | Evidence |
|---|-----------|--------|----------|
| 1 | Metalava 0.5.1 on `kotlin.jvm` modules inside an AGP-9 build with a missing `api.txt` | **Works, but check task hard-fails without `api.txt`** (`Invalid value for "--check-compatibility:api:released": …/api.txt is not a file`). Guard with `onlyIf { api.txt exists }`. With a dump present: additive change passes, removal fails with `Binary breaking change: Removed method …`, on JVM modules and on the Android module (both `Debug` and `Release` check tasks run under `check`). | [VERIFIED: scratch prototype] |
| 2 | `kotlin.jvm` 2.3.20 next to AGP 9.2.1 built-in Kotlin in one build | **Works.** Declare both at the root with `apply false`; apply `kotlin.jvm` only in `:core`/`:providers`, `com.android.library` only in `:keystore`. `:keystore` compiles with `kotlin-build-tools-impl:{strictly 2.3.20}`. `--warning-mode all` shows no AGP/Kotlin warnings. | [VERIFIED: scratch prototype] |
| 3 | JitPack jar→jar / AAR→jar under E5 by SHA; does the aggregator list `:sample` | **Locally proven** for real `.module`, JitPack-style rewritten `.module`, and POM-only. **Live JitPack: unproven** (probe procedure below). `:sample` cannot appear because JitPack only indexes what lands in `~/.m2` and `:sample` has no `maven-publish`. | [VERIFIED: scratch prototype], [CITED: stt v0.6.0 live JitPack log/POM/.module], [ASSUMED] for live jar modules |
| 4 | OkHttp KMP (`okhttp-jvm`) variant attributes for the matrix `Test` legs on Gradle 9.4.1 | **Works.** `extendsFrom(testRuntimeClasspath)` + explicit JVM attributes + `eachDependency { useVersion(v) }` for `okhttp` and `mockwebserver` resolves `okhttp-jvm-5.2.1.jar` / `okhttp-jvm-5.5.0.jar`. An attribute-less config also resolved on 9.4.1, but keep the attributes explicit. | [VERIFIED: scratch prototype] |
| 5 | detekt `ForbiddenComment` regex on planted planning ids | **Works syntax-only** via `comments:` list of `{value: <regex>, reason}` (`values:` is deprecated plain text). Caught `//`, `/* */`, KDoc and trailing comments; `T-x`, bare `Phase 12`, `TODO` without colon are not flagged. Note default config also bans `TODO:`/`FIXME:`/`STOPSHIP:` comments. | [VERIFIED: scratch prototype], [CITED: detekt.dev/docs/1.23.8/rules/style/#forbiddencomment] |
| 6 | `java-test-fixtures` on `:core` excluded from publication | **Works.** `skip()` on `testFixturesApiElements`/`testFixturesRuntimeElements` removes it; without `skip()` the `.module` lists testFixtures and `-test-fixtures.jar` is published. `:providers` tests consume `testFixtures(project(":core"))` and the fixtures can see `:core`'s `internal` members. | [VERIFIED: scratch prototype] |

## Decision Deviations and Flags (read before planning)

| Decision | Finding | Recommendation |
|----------|---------|----------------|
| **D-08** (raw `-Xexplicit-api=strict` flag "no dependence on AGP 9's partial `kotlin {}` extension") | `kotlin { explicitApi() }` **is available and effective on the AGP 9.2.1 built-in-Kotlin module** (planted public-without-modifier failed with `Visibility must be specified in explicit API mode`). The raw flag in `freeCompilerArgs` applies to **every** compilation, so `src/testFixtures` (and test) sources fail the moment they lack `public`. `explicitApi()` applies to main only. [VERIFIED: scratch prototype] | Use `explicitApi()` uniformly on all three modules. Keep the flag only as a documented fallback restricted to main-compilation tasks. D-08 is `ai-auto` (Claude's-discretion tier), so the planner may adopt this; state the deviation in the plan. The "planted negative control" part of D-08 is kept (script + `verifyExplicitApiStrict` config assertion). |
| **D-05** (syntax-only ForbiddenMethodCall assumption) | Confirmed: `ForbiddenMethodCall` needs type resolution and **silently reports nothing** in plain `detekt` (planted `println`, `print`, `printStackTrace`, `runCatching` produced zero findings). [VERIFIED: scratch prototype], [CITED: detekt docs "Requires Type Resolution"] | Do not configure it as a gate. The source scanner is the gate. |
| **D-02** (explicit E5 groupId as primary) | Consistent with evidence, but stt-engine used the *other* pattern (declared `com.github.Ygaray`, JitPack rewrote). Both yield identical consumer coordinates. | Keep explicit group primary; make the group one variable so fallback F1 is a one-line change. Probe tells which works. |
| **D-10** (reflective guard) | `okhttp3.OkHttp.VERSION` is inlined at compile time: the first prototype run reported `OKHTTP_RUNTIME=4.12.0 expected=5.2.1` on the 5.x legs. [VERIFIED: scratch prototype] | The guard must use `Class.forName("okhttp3.OkHttp").getField("VERSION").get(null)` (and log the `okhttp-jvm` jar location). A `const` read would produce a permanently false green. |
| **ROADMAP SC5 vs D-09** | SC5 says "A fake provider runs a `:core` pipeline test with zero network", but D-09 says no public placeholder types and `AiProvider`/pipeline types do not exist until Phases 2-3. | See Open Question 1. |

## Fallback Impact on Consumer Coordinates or Module Shape (D-12)

Flagged per the orchestrator's instruction: *"if any fallback changes consumer coordinates or module shape, message the orchestrator before Phase 2."*

| Fallback | Trigger | Consumer coordinates change? | Module shape change? | Evidence |
|----------|---------|------------------------------|----------------------|----------|
| **F1**: declare `groupId = "com.github.Ygaray"` and let JitPack coerce to `com.github.Ygaray.voice-action-engine` | JitPack does not index the explicit E5 group | **No**. Served at `com.github.Ygaray.voice-action-engine:voice-action-engine-<m>` either way | No | stt v0.6.0: declared `com.github.Ygaray`, served at `com.github.Ygaray.voice-engine-android:voice-engine-android-<m>`; dependency groups in POM and `.module` were rewritten; the wrong-group form returns 401 [VERIFIED: live jitpack.io] |
| **F2**: disable Gradle module metadata when building on JitPack (`GenerateModuleMetadata.enabled = false`) | JitPack's `.module` rewriting mis-resolves jar or AAR | **No** | **No** (consumers lose Gradle variant attributes such as `org.gradle.jvm.version`; POM scopes carry the `api` exposure) | Local POM-only repo resolved jar→jar and AAR→jar and compiled a transitive `:core` type [VERIFIED: scratch prototype] |
| **F3**: take `version` from JitPack's `$VERSION` env instead of a literal | JitPack's version rewriting misbehaves for SHA builds | No | No | Standard; JitPack exposes `VERSION` [CITED: docs.jitpack.io/building] |
| **F4**: turn `:core`/`:providers` into Android-library AARs | JitPack cannot index/serve `.jar` modules alongside AARs | **Artifact type changes jar→aar (coordinates string unchanged)** | **YES** (drops pure-JVM `:core`; conflicts with BLD-01/A7/L7 and the JVM test strategy) | No evidence this is needed. **Message the orchestrator if reached.** |
| **F5**: split the repo / separate repos per module | JitPack cannot publish multi-module here at all | **YES** | **YES** | No evidence. Contract-level; **message the orchestrator.** |

Only F4 and F5 trigger the D-12 message. F1-F3 are in-phase, silent fallbacks that keep the E5/E7 coordinates byte-for-byte.

## Architectural Responsibility Map

This is a build/tooling phase, so "tiers" are build layers rather than runtime tiers.

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Pure contract/pipeline code + fake harness | `:core` (kotlin.jvm) | testFixtures source set | No HTTP/Android/DI; JVM-testable; fixtures unpublished |
| OkHttp transports + A1 matrix | `:providers` (kotlin.jvm) | root catalog floor | `OkHttpClient` is an `api` type; matrix legs are plain JVM `Test` tasks |
| AndroidKeyStore persistence | `:keystore` (AGP 9 library) | `:core` seam | Only module needing `android.*` |
| Reference wiring / fixture loader | `:sample` (application) | none | Never published; inert in Phase 1 |
| Static invariants (imports, comments, banned calls, baseline) | root `config/detekt/detekt.yml` + `gradle/invariants.gradle.kts` | `scripts/verify-negative-controls.sh` | detekt for what works syntax-only, scanner for the rest, script for end-to-end compile-level controls |
| Structural invariants (graph, allowlist, bytecode, floor, publication contents) | `gradle/invariants.gradle.kts` tasks under `check` | none | Cheap, deterministic, no device |
| Publishing | per-module `maven-publish` `release` publication + `jitpack.yml` | `scripts/jitpack-consumer-probe.sh` | JitPack runs only the install list |
| API evolution | Metalava per module, guarded | Phase 11 cut | Enforcement switches on when `api.txt` is committed |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Kotlin (KGP `kotlin.jvm`, `plugin.serialization`) | 2.3.20 | Language/compiler | Consumer parity. `kotlin = "2.3.20"` [VERIFIED: ~/Projects/Reusable/android/yahirandroidtaste/gradle/libs.versions.toml] |
| Android Gradle Plugin | 9.2.1 | `:keystore`, `:sample` | `agp = "9.2.1"` [VERIFIED: same YAT catalog]; built-in Kotlin, do not apply `kotlin.android` |
| Gradle wrapper | 9.4.1 | Build | `distributionUrl=https\://services.gradle.org/distributions/gradle-9.4.1-bin.zip` and `distributionSha256Sum=2ab2958f2a1e51120c326cad6f385153bb11ee93b3c216c5fccebfdfbb7ec6cb` [VERIFIED: YAT gradle-wrapper.properties]; copy the wrapper dir + `gradlew` from YAT |
| JDK | 17 (host 17.0.19; JitPack `openjdk17`, which the JitPack log maps to SDKMAN `17.0.12-oracle`) | Build | [VERIFIED: `java -version`], [VERIFIED: live JitPack build.log of YAT v1.13.0] |
| Bytecode target | JVM 11 | All published modules | Must be set **explicitly** in both `java {}` and `kotlin { compilerOptions }` (Pitfall 6) |
| detekt Gradle plugin | 1.23.8 | Lint gate | [VERIFIED: YAT catalog `detekt = "1.23.8"`]; plain `detekt` task only |
| Metalava plugin `me.tylerbwong.gradle.metalava` | 0.5.1 | API signature dump + compat | Latest in Gradle Plugin Portal `maven-metadata.xml` (`<latest>0.5.1</latest>`) [VERIFIED: plugins.gradle.org]; YAT is on 0.5.0 |
| OkHttp (compile floor) | 4.12.0 | `:providers` `api` | A1 |
| kotlinx-coroutines-core | 1.11.0 | `:core` `api` | Resolved in prototype [VERIFIED: scratch prototype] |
| kotlinx-serialization-json | 1.11.0 | `:core` `api` (`JsonObject`) | Resolved in prototype |
| DataStore Preferences | 1.2.1 | `:keystore` `implementation` | Resolved in prototype |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `com.squareup.okhttp3:mockwebserver` (legacy) | 4.12.0 / 5.2.1 / 5.5.0 | JVM transport tests; moves with okhttp per leg | `:providers` tests only. 5.2.1 and 5.5.0 exist in Maven Central `maven-metadata.xml` [VERIFIED: repo1.maven.org] |
| junit:junit | 4.13.2 | Tests | All modules |
| kotlinx-coroutines-test | 1.11.0 | `runTest` | `:core`, `:providers` |
| OkHttp 5.2.1 | pinned in `:sample` only | Gate-1 later | `:sample` `implementation("com.squareup.okhttp3:okhttp:5.2.1")` built clean with `project(":providers")` (conflict resolution picks 5.2.1) [VERIFIED: scratch prototype] |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| In-build matrix `Test` legs | `-PokhttpTestVersion` + shell loop | Only if attribute plumbing fails; it did not |
| Script plugin `gradle/invariants.gradle.kts` (+ small module-level typed tasks) | `buildSrc` / `build-logic` convention plugin | Script plugin needs no compile step and no classpath-conflict risk with KGP/AGP on JitPack; its limitation is it cannot see plugin classes (use reflection or keep the typed task in the module build file, both shown) |
| `explicitApi()` DSL | `-Xexplicit-api=strict` | See D-08 deviation |

**Installation:** nothing to install by hand; all coordinates resolve through the wrapper. Catalog text is in Code Example 1.

**Version verification:** Maven-ecosystem equivalent of `npm view`. Resolved live from Maven Central / Google / Gradle Plugin Portal in the prototype: Kotlin 2.3.20, AGP 9.2.1, detekt 1.23.8, Metalava 0.5.1, OkHttp 4.12.0/5.2.1/5.5.0, coroutines 1.11.0, serialization 1.11.0, DataStore 1.2.1, JUnit 4.13.2. Publish dates were not separately checked; versions are pinned to the sibling repos' known-good catalogs, not "latest".

## Package Legitimacy Audit

`gsd-tools package-legitimacy check` supports `npm|pypi|crates` only, so it cannot audit Maven coordinates. Substitute evidence: every coordinate below is already in production use in sibling repos (SB, CT, YAT, backup-engine, stt-engine) **and** resolved from its authoritative registry in this session's prototype.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| org.jetbrains.kotlin:kotlin-gradle-plugin 2.3.20 | Gradle Plugin Portal/Maven Central | years | very high | github.com/JetBrains/kotlin | not seam-checkable; sibling-proven | Approved |
| com.android.tools.build:gradle 9.2.1 | Google Maven | years | very high | AOSP | not seam-checkable; sibling-proven | Approved |
| io.gitlab.arturbosch.detekt 1.23.8 | Gradle Plugin Portal | years | high | github.com/detekt/detekt | not seam-checkable; YAT/SB-proven | Approved |
| me.tylerbwong.gradle.metalava 0.5.1 | Gradle Plugin Portal | years | moderate | github.com/tylerbwong/metalava-gradle | not seam-checkable; YAT proves 0.5.0; 0.5.1 proven in prototype | Approved. Transitively pulls `com.android.tools.metalava` (present in Gradle cache) |
| com.squareup.okhttp3:okhttp / mockwebserver 4.12.0, 5.2.1, 5.5.0 | Maven Central | years | very high | github.com/square/okhttp | sibling-proven | Approved |
| org.jetbrains.kotlinx:kotlinx-coroutines-core / -test 1.11.0, kotlinx-serialization-json 1.11.0 | Maven Central | years | very high | github.com/Kotlin | sibling-proven | Approved |
| androidx.datastore:datastore-preferences 1.2.1 | Google Maven | years | very high | AOSP | sibling-proven | Approved |
| junit:junit 4.13.2 | Maven Central | years | very high | github.com/junit-team/junit4 | sibling-proven | Approved |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none (no package was discovered via WebSearch; all came from the project's locked stack in CLAUDE.md/STACK.md and sibling catalogs)

## Architecture Patterns

### System Architecture Diagram

```
                       git push (commit SHA)                          consumer app (empty Gradle cache)
developer ───────────────────────────────────▶ GitHub Ygaray/voice-action-engine
                                                      │                          ▲  resolve by SHA
                                                      ▼                          │  com.github.Ygaray.voice-action-engine:
                                   jitpack.io (on first request)                 │      voice-action-engine-{core,providers,keystore}
                                   reads jitpack.yml: jdk openjdk17              │
                                   install: ./gradlew :core:…  :providers:…  :keystore:…
                                     publishReleasePublicationToMavenLocal       │
                                                      │                          │
                     ~/.m2 scan ("Found artifact: …") ─┴─▶ rewrite POM+.module groups/versions ─▶ serve .jar/.aar/.module/.pom
                     (:sample has no maven-publish → never found → never served)

./gradlew check (local / per commit)
  ├─ :core      detekt ─ scanBannedConstructs ─ verifyBytecodeLevel ─ verifyExplicitApiStrict ─ verifyModuleGraph
  │             verifyCoreDependencyAllowlist ─ verifyNoDetektBaseline ─ verifyInvariantScannerControls
  │             detektNegativeControls→verifyDetektControls ─ verifyNoTestFixturesPublished ─ test ─ metalavaCheckCompatibility[onlyIf api.txt]
  ├─ :providers detekt ─ scan ─ bytecode ─ explicitApi ─ graph ─ verifyOkHttpCompileFloor(4.12.0) ─ test(4.12.0) ─ testOkhttp521 ─ testOkhttp550 ─ metalava[onlyIf]
  ├─ :keystore  detekt ─ scan ─ bytecode(AAR classes.jar) ─ explicitApi ─ graph ─ metalava Debug+Release[onlyIf]
  └─ :sample    lint/compile only (inert, no publish)
phase gate (not in check): scripts/verify-negative-controls.sh  ·  scripts/jitpack-consumer-probe.sh  ·  scripts/jitpack-dry-run.sh
```

### Recommended Project Structure
```
settings.gradle.kts            # include(":core", ":providers", ":keystore", ":sample"); every dir must exist (Gradle 9)
build.gradle.kts               # plugins … apply false; subprojects{} detekt wiring, metalava guard + apiDump/apiCheck, F2 hook
gradle.properties              # caching on, configuration-cache off, AndroidX flags
gradle/libs.versions.toml      # okhttp = "4.12.0" with "NEVER RAISE (A1)" comment
gradle/invariants.gradle.kts   # scanner + structural checks (Code Example 4)
gradle/wrapper/ gradlew*       # 9.4.1 + sha256, gradlew mode 100755
jitpack.yml                    # jdk + explicit install list
config/detekt/detekt.yml       # Code Example 5
config/negative-controls/      # *.kt.txt (scanner) and detekt/*.kt (detekt)
scripts/                       # verify-negative-controls.sh, jitpack-consumer-probe.sh, jitpack-dry-run.sh
core/     src/main/kotlin/io/github/ygaray/voiceactionengine/core/       (+ src/test, src/testFixtures/.../core/testing)
providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/  (+ src/test)
keystore/ src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/   (AndroidManifest not needed for a library namespace)
sample/   build.gradle.kts, src/main/AndroidManifest.xml, src/main/kotlin/.../sample/   (sample/src/debug/assets/ is gitignored, may be absent)
```

### Pattern 1: Single `release` publication per module, explicit E5 coordinates
**What:** every published module registers one `MavenPublication` named `release`, so the install task name is uniform (`publishReleasePublicationToMavenLocal`).
**When:** always. JVM: `from(components["java"])`. AAR: `android { publishing { singleVariant("release") { withSourcesJar() } } }` plus `afterEvaluate { from(components["release"]) }` (the JitPack-Android form, copied from YAT/backup-engine).
**Note:** with `groupId = "com.github.Ygaray.voice-action-engine"` the local `~/.m2` path is `com/github/Ygaray/voice-action-engine/<artifactId>/<ver>/`, i.e. the exact path JitPack serves at (Hypothesis H1 below).

### Pattern 2: Guarded Metalava with stable aliases
`onlyIf("api.txt exists")` on every `metalavaCheckCompatibility*`, and root-level `apiDump`/`apiCheck` aliases (Code Example 2). `apiDump` writes `api.txt` into each module dir; **Phase 1 must not commit those files** (a committed dump turns enforcement on early and freezes an interim API). Phase 11 commits them.

### Pattern 3: Matrix leg = same compiled classes, different resolved runtime classpath
Code Example 3. Compile once against 4.12.0; each leg's `Test` task reuses `sourceSets.test.output` + `sourceSets.main.output` and a leg-specific resolvable configuration. Move `okhttp` **and** `mockwebserver` together.

### Pattern 4: Negative controls at three levels
1. In-build, cheap: scanner controls (`config/negative-controls/*.kt.txt`, with `// EXPECT:` headers and a `clean.kt.txt` false-positive control) and detekt controls (typed `Detekt` task over `config/negative-controls/detekt`, `ignoreFailures = true`, asserted by counting findings in the XML).
2. Config assertions: `verifyExplicitApiStrict` (reflection), `verifyNoTestFixturesPublished`, `verifyOkHttpCompileFloor`.
3. End-to-end phase gate: `scripts/verify-negative-controls.sh` plants one construct per gate per module, asserts the gate goes red **and for the expected reason** (message marker), then removes the plant (~60-80 s).

### Anti-Patterns to Avoid
- **Reading `okhttp3.OkHttp.VERSION` directly in the guard test:** inlined constant, permanently false-green.
- **Raw `-Xexplicit-api=strict` in `freeCompilerArgs`:** hits `test`/`testFixtures`.
- **`jvmToolchain` / omitting `jvmTarget`:** build fails with `Inconsistent JVM-target compatibility detected for tasks 'compileJava' (11) and 'compileKotlin' (17)` [VERIFIED: scratch prototype].
- **Relying on `ForbiddenMethodCall` in plain `detekt`.**
- **Committing `api.txt` before the cut.**
- **Letting `:sample` read anything at configuration time, or apply `maven-publish`.**
- **Declaring a module dir in `settings.gradle.kts` that does not exist:** Gradle 9 error `Configuring project ':sample' without an existing directory is not allowed` [VERIFIED: scratch prototype].

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Public API signature + additive-only compat | Custom reflection/javap diff | Metalava 0.5.1 (`--check-compatibility:api:released`) | Proven here: additive passes, removal fails. Only the value-class mangled-member gap needs the `javap` supplement at the cut (YAT's documented limitation) |
| Import bans | grep | detekt `ForbiddenImport` | Works syntax-only, reports file:line |
| Comment bans | grep | detekt `ForbiddenComment` `comments:` regex | Works syntax-only incl. KDoc |
| OkHttp multi-version testing | Copy modules per version / recompile | Leg configurations over one compiled test output | Keeps the real A1 risk (4.12 bytecode on 5.x) |
| Fake publication of test helpers | Separate published `:testing` module | `java-test-fixtures` + `skip()` | Orchestrator ruling: unpublished; a new module would need an amendment |
| Exposing a consumer project for the JitPack probe | Hand-typed scratch projects each phase | `scripts/jitpack-consumer-probe.sh` (Code Example 6) | Phase 10 D-07 reuses it; one script covers jar→jar and AAR→jar |

Not to hand-roll but which **must** be custom: the source scanner (runCatching/println/printStackTrace/FQ DI/planning-ids) because no syntax-only detekt rule covers it. It is ~60 lines and its own negative controls keep it honest.

## Common Pitfalls

### Pitfall 1: False-green OkHttp matrix (const inlining)
**What goes wrong:** the 5.x legs report `OKHTTP_RUNTIME=4.12.0`. **Why:** `OkHttp.VERSION` is `const`, inlined when the tests compile against 4.12. **Avoid:** reflective field read + print the `okhttp-jvm` jar path; negative control (`expected.okhttp=9.9.9` must fail). **Warning sign:** guard passes without ever proving a jar path.

### Pitfall 2: Metalava check with no `api.txt`
**What goes wrong:** `check` fails at `metalavaCheckCompatibility`. **Avoid:** the `onlyIf` guard (Code Example 2). Also guard the Android `Debug` and `Release` variants (`startsWith("metalavaCheckCompatibility")`).

### Pitfall 3: Silent no-op rules
`ForbiddenMethodCall` and `ForbiddenAnnotation` need type resolution and report nothing in plain `detekt`. Any "gate" resting on them is an unguarded claim. Use the scanner and prove it with planted controls.

### Pitfall 4: Gradle 9 test-discovery failure
A module with `src/test` Kotlin but no discoverable test fails `test` with "There are test sources present and no filters are applied, but the test task did not discover any tests to execute" [VERIFIED: scratch prototype]. Do not leave helper-only test source sets; or set `failOnNoDiscoveredTests = false` with a justification.

### Pitfall 5: default detekt rules are real
With `buildUponDefaultConfig = true` the prototype tripped `MatchingDeclarationName` (file name vs single declaration), `FunctionOnlyReturningConstant`, `ThrowingExceptionsWithoutMessageOrCause`, `InvalidPackageDeclaration`; and default `ForbiddenComment` bans `TODO:`/`FIXME:`/`STOPSHIP:`. Name files after their single declaration; fix, do not bank. Later phases will meet `TooGenericExceptionCaught`, `SwallowedException`, `LongParameterList`, `MagicNumber`, `ReturnCount`, `TooManyFunctions`: tune in `config/detekt/detekt.yml` with a one-line justification (STACK.md prescription: `LongParameterList constructorThreshold: 8, ignoreDefaultParameters: true`).

### Pitfall 6: `jvmTarget` is mandatory on `kotlin.jvm` modules
Without `compilerOptions.jvmTarget = JVM_11`, Kotlin targets the running JDK (17) and Gradle fails as above. Set it in `:core`, `:providers` and (for clarity) `:keystore`.

### Pitfall 7: detekt scans only `src/main` and `src/test` by default
`src/testFixtures` (and later `src/androidTest`) are not scanned unless added to `detekt { source.setFrom(...) }`. Decide deliberately; the fakes are unpublished so this is a consistency call, not a safety one.

### Pitfall 8: script plugins cannot see plugin classes
Inside `apply(from = "gradle/invariants.gradle.kts")`, `org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension` and `io.gitlab.arturbosch.detekt.Detekt` do not resolve (`Unresolved reference 'kotlin'`). Use reflection (shown) or put the typed task in the module's own build file (the detekt control task lives in `core/build.gradle.kts` for this reason). Also note that inside `tasks.register { … }`, bare `extensions` is the **task's** extension container; use `project.extensions`.

### Pitfall 9: JitPack specifics
(a) The bare `com.github.Ygaray:voice-action-engine` is JitPack's synthesized **aggregator POM** (stt: live 200, pulls every module); per-module artifacts live only under `com.github.Ygaray.voice-action-engine:<artifactId>`; the wrong-group form returns **401** [VERIFIED: live jitpack.io for stt]. (b) JitPack caches a failed build per ref; retry = new commit, never a throwaway tag (tags immutable). (c) `gradlew` must be committed executable (`git update-index --chmod=+x gradlew`). (d) JitPack configures **every** included project even though only the install list runs, so `:sample` must configure cleanly with no secrets/fixture. (e) JitPack sets the Android SDK itself ("Found Android manifest", auto-installs platform 36.1 for YAT v1.13.0 on Gradle 9.4.1) [VERIFIED: live YAT build.log]; never commit `local.properties` (already ignored).

### Pitfall 10: accidental early `api.txt`
`apiDump` writes `<module>/api.txt`. A stray commit silently arms the compat gate and freezes an interim API. The Phase 1 wiring verification should run `apiDump` in a temp copy or delete the files afterwards.

## Code Examples

All tested on the prototype. Package names, group and artifactIds come from locked sources: D-01 "`com.github.Ygaray.voice-action-engine:voice-action-engine-{core,providers,keystore}`" and BLD-07 "Package root is `io.github.ygaray.voiceactionengine.*`, decided in step 1 and never changed."

### 1. Root files

`settings.gradle.kts`
```kotlin
pluginManagement {
    repositories {
        google { content { includeGroupByRegex("com\\.android.*"); includeGroupByRegex("com\\.google.*"); includeGroupByRegex("androidx.*") } }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "voice-action-engine"
include(":core", ":providers", ":keystore", ":sample")
```

`gradle.properties`
```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.caching=true
org.gradle.configuration-cache=false
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

`gradle/libs.versions.toml` (compose plugin/BOM entries are Phase 10's, omitted here)
```toml
[versions]
agp = "9.2.1"
kotlin = "2.3.20"
detekt = "1.23.8"
metalava = "0.5.1"
coroutines = "1.11.0"
serialization = "1.11.0"
# A1 COMPILE FLOOR for :providers. NEVER RAISE: consumers pick their own OkHttp. verifyOkHttpCompileFloor enforces it.
okhttp = "4.12.0"
junit = "4.13.2"
datastore = "1.2.1"

[libraries]
coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "okhttp" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
datastore-prefs = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }

[plugins]
android-library = { id = "com.android.library", version.ref = "agp" }
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }
metalava = { id = "me.tylerbwong.gradle.metalava", version.ref = "metalava" }
```

`jitpack.yml` (single invocation = one Gradle startup; stt lists one command per module, YAT a single command: both shapes exist on live JitPack, see Assumption A7)
```yaml
jdk:
  - openjdk17
install:
  # Explicit per-module list. NEVER name :sample (no maven-publish there; a mismatch is only discovered after the ref is cached).
  - ./gradlew :core:publishReleasePublicationToMavenLocal :providers:publishReleasePublicationToMavenLocal :keystore:publishReleasePublicationToMavenLocal
```

`.gitignore` additions (proven with `git check-ignore -v`: fixture at depth, a `.v2` glob sibling, and `graphify-out/x/g.json` all matched). The existing file already ignores `build/`, `.gradle/`, `.kotlin/`, `local.properties`, `*.aar`, `*.jks`.
```gitignore
# LE-7: SB-derived A10 fixture must never be committed (this repo is public)
sb-a10-fixture*.json
# graphify output (BLD-08)
/graphify-out/
```

### 2. Root `build.gradle.kts`
```kotlin
plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.metalava) apply false
}

subprojects {
    plugins.withId("io.gitlab.arturbosch.detekt") {
        extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
            buildUponDefaultConfig = true
            allRules = false
            config.setFrom(rootProject.file("config/detekt/detekt.yml"))
            // deliberately NO `baseline` property (zero-baseline policy; verifyNoDetektBaseline enforces)
        }
    }
    plugins.withId("me.tylerbwong.gradle.metalava") {
        // D-07: Metalava hard-fails without api.txt, so the compat checks only run once the v1.0.0 cut commits the dump.
        tasks.matching { it.name.startsWith("metalavaCheckCompatibility") }.configureEach {
            onlyIf("api.txt exists") { project.file("api.txt").isFile }
        }
        tasks.register("apiDump") {
            group = "verification"
            dependsOn(tasks.matching { it.name.startsWith("metalavaGenerateSignature") && !it.name.contains("Debug") })
        }
        tasks.register("apiCheck") {
            group = "verification"
            dependsOn(tasks.matching { it.name.startsWith("metalavaCheckCompatibility") && !it.name.contains("Debug") })
        }
    }
    // Fallback F2 hook (off by default): POM-only publication if the JitPack probe shows .module trouble.
    tasks.withType<GenerateModuleMetadata>().configureEach {
        enabled = System.getenv("VAE_DISABLE_MODULE_METADATA") == null
    }
}
```
(If the planner adopts `$VERSION`-driven versioning (F3), add `val engineVersion = providers.environmentVariable("VERSION").orElse(providers.gradleProperty("engineVersion"))` and use it in each publication.)

### 3. Module build files

`core/build.gradle.kts`
```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-test-fixtures`
    `maven-publish`
    alias(libs.plugins.detekt)
    alias(libs.plugins.metalava)
}
java { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11; withSourcesJar() }
kotlin {
    explicitApi()
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) }
}
dependencies {
    api(libs.coroutines.core)
    api(libs.serialization.json)
    testFixturesApi(libs.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(testFixtures(project(":core")))
}
// D-09: keep testFixtures out of the published component (without this, a -test-fixtures.jar is published and module.json lists it)
val javaComponent = components["java"] as AdhocComponentWithVariants
javaComponent.withVariantsFromConfiguration(configurations["testFixturesApiElements"]) { skip() }
javaComponent.withVariantsFromConfiguration(configurations["testFixturesRuntimeElements"]) { skip() }
publishing { publications { register<MavenPublication>("release") {
    groupId = "com.github.Ygaray.voice-action-engine"   // F1 fallback: "com.github.Ygaray"
    artifactId = "voice-action-engine-core"
    version = "0.0.0-local"                              // see F3
    from(components["java"])
} } }
apply(from = rootProject.file("gradle/invariants.gradle.kts"))

// detekt negative control (typed task lives here because script plugins cannot see the detekt classes)
val detektControls = tasks.register<io.gitlab.arturbosch.detekt.Detekt>("detektNegativeControls") {
    setSource(rootProject.file("config/negative-controls/detekt")); include("**/*.kt")
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    ignoreFailures = true
    reports {
        xml.required.set(true); xml.outputLocation.set(layout.buildDirectory.file("reports/detekt-controls/detekt.xml"))
        html.required.set(false); txt.required.set(false); sarif.required.set(false); md.required.set(false)
    }
}
val verifyDetektControls = tasks.register("verifyDetektControls") {
    group = "verification"; dependsOn(detektControls)
    val xml = layout.buildDirectory.file("reports/detekt-controls/detekt.xml")
    doLast {
        val rules = Regex("""<error [^>]*source="detekt\.([A-Za-z]+)"""").findAll(xml.get().asFile.readText()).map { it.groupValues[1] }.toList()
        val imports = rules.count { it == "ForbiddenImport" }; val comments = rules.count { it == "ForbiddenComment" }
        if (imports != 5) throw GradleException("expected 5 ForbiddenImport findings, got $imports")
        if (comments != 4) throw GradleException("expected 4 ForbiddenComment findings, got $comments")
    }
}
val verifyNoTestFixturesPublished = tasks.register("verifyNoTestFixturesPublished") {
    group = "verification"
    dependsOn("generateMetadataFileForReleasePublication", "generatePomFileForReleasePublication")
    val dir = layout.buildDirectory.dir("publications/release")
    doLast {
        val files = dir.get().asFile.listFiles { f -> f.name == "module.json" || f.name.endsWith(".xml") }.orEmpty()
        if (files.isEmpty()) throw GradleException("no generated publication metadata found (vacuous)")
        val leaks = files.filter { Regex("(?i)test-?fixtures").containsMatchIn(it.readText()) }.map { it.name }
        if (leaks.isNotEmpty()) throw GradleException("testFixtures leaked into the published component: $leaks")
    }
}
tasks.named("check") { dependsOn(verifyDetektControls, verifyNoTestFixturesPublished) }
```

`providers/build.gradle.kts` (matrix)
```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm); alias(libs.plugins.kotlin.serialization)
    `maven-publish`; alias(libs.plugins.detekt); alias(libs.plugins.metalava)
}
java { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11; withSourcesJar() }
kotlin { explicitApi(); compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) } }
dependencies {
    api(project(":core"))
    api(libs.okhttp)                       // plain api: a Gradle minimum, so consumers' 5.x wins
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)   // legacy okhttp3.mockwebserver only
    testImplementation(testFixtures(project(":core")))
}
publishing { publications { register<MavenPublication>("release") {
    groupId = "com.github.Ygaray.voice-action-engine"; artifactId = "voice-action-engine-providers"; version = "0.0.0-local"
    from(components["java"])
} } }

val okhttpLegs = mapOf("Okhttp521" to "5.2.1", "Okhttp550" to "5.5.0")   // the default `test` task is the 4.12.0 leg
okhttpLegs.forEach { (legName, v) ->
    val legCp = configurations.create("test${legName}RuntimeClasspath") {
        isCanBeConsumed = false; isCanBeResolved = true
        extendsFrom(configurations.testRuntimeClasspath.get())
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
            attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named(TargetJvmEnvironment.STANDARD_JVM))
        }
        resolutionStrategy.eachDependency {
            if (requested.group == "com.squareup.okhttp3" && (requested.name == "okhttp" || requested.name == "mockwebserver")) useVersion(v)
        }
    }
    val sourceSets = the<SourceSetContainer>()
    val legTest = tasks.register<Test>("test$legName") {
        group = "verification"
        description = "Runs the 4.12.0-compiled test classes on OkHttp $v (okhttp + mockwebserver swapped together)"
        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = sourceSets["test"].output + sourceSets["main"].output + legCp
        systemProperty("expected.okhttp", v)
        testLogging { showStandardStreams = true }
    }
    tasks.named("check") { dependsOn(legTest) }
}
apply(from = rootProject.file("gradle/invariants.gradle.kts"))
```

Guard test (`providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/OkHttpVersionGuardTest.kt`), reflective on purpose:
```kotlin
class OkHttpVersionGuardTest {
    @Test fun runtimeVersionMatchesLeg() {
        val expected = System.getProperty("expected.okhttp") ?: "4.12.0"
        // Reflective on purpose: OkHttp.VERSION is a compile-time constant and would be inlined as "4.12.0".
        val actual = Class.forName("okhttp3.OkHttp").getField("VERSION").get(null) as String
        println("OKHTTP_RUNTIME=$actual expected=$expected jar=" + OkHttpClient::class.java.protectionDomain.codeSource.location)
        assertEquals(expected, actual)
    }
    @Test fun trivialCallRoundTrips() {   // body?.string(): the 4.12-compatible body API
        MockWebServer().use { s ->
            s.enqueue(MockResponse().setBody("hi")); s.start()
            val r = OkHttpClient().newCall(Request.Builder().url(s.url("/")).build()).execute()
            assertEquals("hi", r.body?.string())
        }
    }
}
```
Observed per leg: `okhttp-jvm-5.2.1.jar`, `okhttp-jvm-5.5.0.jar`; the 4.12 leg resolves `okhttp-4.12.0`. Negative control: setting `expected.okhttp` to `9.9.9` fails the leg.

`keystore/build.gradle.kts`
```kotlin
plugins { alias(libs.plugins.android.library); `maven-publish`; alias(libs.plugins.detekt); alias(libs.plugins.metalava) }
android {
    namespace = "io.github.ygaray.voiceactionengine.keystore"
    compileSdk { version = release(36) { minorApiLevel = 1 } }     // YAT's proven form; JitPack auto-installs platform 36.1
    defaultConfig { minSdk = 35 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
    publishing { singleVariant("release") { withSourcesJar() } }    // #1 JitPack-Android failure if missing
}
kotlin { explicitApi(); compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) } }   // works under AGP 9 built-in Kotlin
dependencies { api(project(":core")); implementation(libs.datastore.prefs) }
publishing { publications { register<MavenPublication>("release") {
    groupId = "com.github.Ygaray.voice-action-engine"; artifactId = "voice-action-engine-keystore"; version = "0.0.0-local"
    afterEvaluate { from(components["release"]) }
} } }
apply(from = rootProject.file("gradle/invariants.gradle.kts"))
```
(`minSdk = 35` and `compileSdk 36.1` copy SB/YAT as STACK.md states; the planner should confirm `minSdk` with the orchestrator if the library should be lower. No `org.jetbrains.kotlin.android` plugin.)

`sample/build.gradle.kts` (inert: no publish, no config-time reads)
```kotlin
plugins { alias(libs.plugins.android.application) }
android {
    namespace = "io.github.ygaray.voiceactionengine.sample"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    defaultConfig { applicationId = "io.github.ygaray.voiceactionengine.sample"; minSdk = 35; targetSdk = 36; versionCode = 1; versionName = "0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
}
dependencies {
    implementation(project(":core")); implementation(project(":providers")); implementation(project(":keystore"))
    implementation("com.squareup.okhttp3:okhttp:5.2.1")   // Gate-1 runs 4.12-compiled bytecode on the real 5.x android variant
}
```
Manifest: `<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application android:label="vae-sample" /></manifest>`. `:sample:assembleDebug` and `:sample:lint` passed. Phase 1 adds no fixture reader; the runtime loud error is Phase 10.

### 4. `gradle/invariants.gradle.kts` (scanner + structural checks)

The full tested text is reproduced here because it is load-bearing. Applied from each published module. `if (project.name == "core")` blocks are repo-wide checks hosted on `:core` so they run once.

```kotlin
import java.io.File
import java.util.zip.ZipFile

data class Rule(val id: String, val regex: Regex)

// Matched against source text with comments and string/char literals blanked out (planning ids: COMMENT text only).
val bannedRules = listOf(
    Rule("runCatching", Regex("""\brunCatching\b""")),
    Rule("println/print", Regex("""(?<![\w.])(println|print)\s*\(""")),
    Rule("System.out/err", Regex("""\bSystem\s*\.\s*(out|err)\b""")),
    Rule("printStackTrace", Regex("""\.\s*printStackTrace\s*\(""")),
    Rule("DI annotation (FQ)", Regex("""@\s*(javax\s*\.\s*inject|jakarta\s*\.\s*inject|dagger|androidx\s*\.\s*hilt)\s*\.""")),
    Rule("forbidden package (FQ or import)", Regex("""\b(okhttp3\s*\.\s*internal|mockwebserver3|okhttp3\s*\.\s*coroutines|android\s*\.\s*util\s*\.\s*Log|javax\s*\.\s*inject|jakarta\s*\.\s*inject|dagger|androidx\s*\.\s*hilt)\b""")),
    Rule("app planning id in comment", Regex("""\b(T-\d+-\d+|WR-\d+|Phase\s+\d+\s+D-\d+)\b""")),
)

// Returns (code with literals blanked, comments only), both preserving line structure.
fun splitCodeAndComments(src: String): Pair<String, String> {
    val code = StringBuilder(); val comments = StringBuilder()
    var i = 0; val n = src.length
    fun keepNl(c: Char, sb: StringBuilder) { sb.append(if (c == '\n') '\n' else ' ') }
    while (i < n) {
        val c = src[i]
        when {
            src.startsWith("//", i) -> { while (i < n && src[i] != '\n') { comments.append(src[i]); code.append(' '); i++ } }
            src.startsWith("/*", i) -> {
                var depth = 0
                while (i < n) {
                    if (src.startsWith("/*", i)) { depth++; comments.append("  "); code.append("  "); i += 2 }
                    else if (src.startsWith("*/", i)) { depth--; comments.append("  "); code.append("  "); i += 2; if (depth == 0) break }
                    else { comments.append(src[i]); keepNl(src[i], code); i++ }
                }
            }
            src.startsWith("\"\"\"", i) -> {
                code.append("   "); comments.append("   "); i += 3
                while (i < n && !src.startsWith("\"\"\"", i)) { keepNl(src[i], code); keepNl(src[i], comments); i++ }
                if (i < n) { code.append("   "); comments.append("   "); i += 3 }
            }
            c == '"' -> {
                code.append(' '); comments.append(' '); i++
                while (i < n && src[i] != '"' && src[i] != '\n') { if (src[i] == '\\') { code.append(' '); comments.append(' '); i++ }; code.append(' '); comments.append(' '); i++ }
                if (i < n && src[i] == '"') { code.append(' '); comments.append(' '); i++ }
            }
            c == '\'' && i + 2 < n && (src[i + 2] == '\'' || src[i + 1] == '\\') -> {
                val end = src.indexOf('\'', i + 2).let { if (it < 0) n - 1 else it }
                while (i <= end) { code.append(' '); comments.append(' '); i++ }
            }
            else -> { code.append(c); keepNl(c, comments); i++ }
        }
    }
    return code.toString() to comments.toString()
}

fun scanText(label: String, text: String): List<String> {
    val (code, comments) = splitCodeAndComments(text)
    val out = mutableListOf<String>()
    for (rule in bannedRules) {
        val target = if (rule.id.startsWith("app planning id")) comments else code
        rule.regex.findAll(target).forEach { m ->
            val line = target.substring(0, m.range.first).count { it == '\n' } + 1
            out += "$label:$line [${rule.id}] '${m.value.trim()}'"
        }
    }
    return out
}

val scanBanned = tasks.register("scanBannedConstructs") {
    group = "verification"
    val files = fileTree("src/main") { include("**/*.kt", "**/*.java") }
    inputs.files(files)
    doLast {
        val violations = files.files.sorted().flatMap { f -> scanText(f.relativeTo(projectDir).path, f.readText()) }
        if (violations.isNotEmpty()) throw GradleException("Banned constructs in ${project.path}:\n" + violations.joinToString("\n") { "  $it" })
    }
}

val expectedMajor = 55   // JVM 11
val verifyBytecode = tasks.register("verifyBytecodeLevel") {
    group = "verification"
    val isAndroid = plugins.hasPlugin("com.android.library")
    val artifact: Provider<File> = if (isAndroid)
        tasks.named("bundleReleaseAar").flatMap { (it as org.gradle.api.tasks.bundling.AbstractArchiveTask).archiveFile }.map { it.asFile }
    else tasks.named<Jar>("jar").flatMap { it.archiveFile }.map { it.asFile }
    dependsOn(if (isAndroid) "bundleReleaseAar" else "jar")
    doLast {
        val file = artifact.get(); val bad = mutableListOf<String>(); var seen = 0
        fun checkStream(name: String, bytes: ByteArray) {
            if (!name.endsWith(".class") || name.startsWith("META-INF/versions/")) return
            seen++
            val major = ((bytes[6].toInt() and 0xff) shl 8) or (bytes[7].toInt() and 0xff)
            if (major != expectedMajor) bad += "$name major=$major"
        }
        ZipFile(file).use { z ->
            for (e in z.entries()) {
                if (e.isDirectory) continue
                if (e.name == "classes.jar") {   // AAR
                    java.util.zip.ZipInputStream(z.getInputStream(e)).use { zis ->
                        var ze = zis.nextEntry
                        while (ze != null) { if (!ze.isDirectory) checkStream(ze.name, zis.readBytes()); ze = zis.nextEntry }
                    }
                } else checkStream(e.name, z.getInputStream(e).readBytes())
            }
        }
        if (seen == 0) throw GradleException("No class files found in $file (vacuous check)")
        if (bad.isNotEmpty()) throw GradleException("Non-JVM-11 class files in ${file.name}: $bad")
    }
}

val verifyExplicitApi = tasks.register("verifyExplicitApiStrict") {
    group = "verification"
    val mode = provider {   // reflection: applied scripts do not see the Kotlin plugin's classes
        val ext = project.extensions.getByName("kotlin")
        (ext.javaClass.getMethod("getExplicitApi").invoke(ext) as Enum<*>?)?.name
    }
    doLast { if (mode.orNull != "Strict") throw GradleException("${project.path}: explicitApi is '${mode.orNull}', expected Strict") }
}

val allowedEdges = mapOf(":core" to emptySet<String>(), ":providers" to setOf(":core"), ":keystore" to setOf(":core"))
val verifyModuleGraph = tasks.register("verifyModuleGraph") {
    group = "verification"
    val edges = provider {
        project.configurations.flatMap { c -> c.dependencies.filterIsInstance<ProjectDependency>().map { it.path } }
            .filter { it != project.path }.toSet()
    }
    doLast {
        val extra = edges.get() - allowedEdges.getValue(project.path)
        if (extra.isNotEmpty()) throw GradleException("${project.path} has forbidden project dependencies $extra")
    }
}
tasks.named("check") { dependsOn(scanBanned, verifyBytecode, verifyExplicitApi, verifyModuleGraph) }

if (project.name == "core") {
    val allowed = setOf(
        "org.jetbrains.kotlin:kotlin-stdlib", "org.jetbrains:annotations",
        "org.jetbrains.kotlinx:kotlinx-coroutines-core", "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm", "org.jetbrains.kotlinx:kotlinx-coroutines-bom",
        "org.jetbrains.kotlinx:kotlinx-serialization-json", "org.jetbrains.kotlinx:kotlinx-serialization-json-jvm",
        "org.jetbrains.kotlinx:kotlinx-serialization-core", "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm", "org.jetbrains.kotlinx:kotlinx-serialization-bom",
    )
    val verifyCoreDeps = tasks.register("verifyCoreDependencyAllowlist") {
        group = "verification"
        val cps = listOf("compileClasspath", "runtimeClasspath").map { configurations.named(it) }
        doLast {
            val offenders = cps.flatMap { cp ->
                cp.get().incoming.resolutionResult.allComponents
                    .filter { it.id !is org.gradle.api.artifacts.component.ProjectComponentIdentifier }   // :core itself
                    .map { it.moduleVersion!! }
                    .filter { "${it.group}:${it.name}" !in allowed }
                    .map { "${cp.name}: ${it.group}:${it.name}:${it.version}" }
            }.distinct()
            if (offenders.isNotEmpty()) throw GradleException(":core classpath has non-allow-listed artifacts:\n" + offenders.joinToString("\n") { "  $it" })
        }
    }
    val verifyNoBaseline = tasks.register("verifyNoDetektBaseline") {
        group = "verification"
        val root = rootProject.projectDir
        doLast {
            val skip = setOf("build", ".gradle", ".git", ".planning", "graphify-out", ".kotlin")
            val hits = mutableListOf<String>()
            root.walkTopDown().onEnter { it.name !in skip }.forEach { f ->
                if (f.isFile && f.name.matches(Regex("(?i).*baseline.*\\.xml"))) hits += "baseline file: ${f.relativeTo(root)}"
                if (f.isFile && f.name.endsWith(".kts") && Regex("""\bbaseline\s*=""").containsMatchIn(f.readText())) hits += "baseline wiring: ${f.relativeTo(root)}"
            }
            if (hits.isNotEmpty()) throw GradleException("detekt baseline is forbidden (zero-baseline policy):\n" + hits.joinToString("\n") { "  $it" })
        }
    }
    val verifyScanner = tasks.register("verifyInvariantScannerControls") {
        group = "verification"
        val controls = fileTree(rootProject.file("config/negative-controls")) { include("*.kt.txt") }
        inputs.files(controls)
        doLast {
            val problems = mutableListOf<String>()
            if (controls.files.isEmpty()) problems += "no negative controls found (vacuous)"
            for (f in controls.files.sorted()) {
                val text = f.readText()
                val expect = Regex("""^// EXPECT: (.*)$""", RegexOption.MULTILINE).find(text)!!.groupValues[1].trim()
                val found = scanText(f.name, text).map { Regex("""\[(.*?)\]""").find(it)!!.groupValues[1] }.toSet()
                val wanted = if (expect == "(none)") emptySet() else expect.split(';').map { it.trim() }.toSet()
                if (found != wanted) problems += "${f.name}: expected $wanted but scanner found $found"
            }
            if (problems.isNotEmpty()) throw GradleException("Scanner negative controls failed:\n" + problems.joinToString("\n") { "  $it" })
        }
    }
    tasks.named("check") { dependsOn(verifyCoreDeps, verifyNoBaseline, verifyScanner) }
}

if (project.name == "providers") {
    val floor = "4.12.0"
    val verifyFloor = tasks.register("verifyOkHttpCompileFloor") {
        group = "verification"
        val cps = listOf("compileClasspath", "testCompileClasspath").map { configurations.named(it) }
        doLast {
            val errs = mutableListOf<String>(); var seen = 0
            cps.forEach { cp ->
                cp.get().incoming.resolutionResult.allComponents.mapNotNull { it.moduleVersion }
                    .filter { it.group == "com.squareup.okhttp3" && it.name in setOf("okhttp", "okhttp-jvm", "mockwebserver") }
                    .forEach { seen++; if (it.version != floor) errs += "${cp.name}: ${it.group}:${it.name}:${it.version} (must be $floor)" }
            }
            if (seen == 0) errs += "no okhttp found on compile classpaths (vacuous)"
            if (errs.isNotEmpty()) throw GradleException(errs.joinToString("\n"))
        }
    }
    tasks.named("check") { dependsOn(verifyFloor) }
}
```
Observed to bite (each re-run after a deliberate break): planted `runCatching` + planning id (scan), `api(libs.okhttp)` added to `:core` (allowlist lists `okhttp`, `okio`, `kotlin-stdlib-jdk7/8` …), catalog `okhttp = "5.2.1"` (floor lists five offenders), a `config/detekt-baseline.xml` (baseline), `implementation(project(":providers"))` in `:core` (graph), `explicitApi()` commented out (config assertion), `skip()` commented out (leak). Recommended ~10-line extension (same pattern, not separately run): deny `com.google.dagger:*`, `javax.inject:*`, `jakarta.inject:*` on all three modules' resolved classpaths to back CLN-01 at the dependency level.

### 5. `config/detekt/detekt.yml`
```yaml
build:
  maxIssues: 0

style:
  ForbiddenImport:
    active: true
    imports:
      - 'okhttp3.internal.*'
      - 'mockwebserver3.*'
      - 'okhttp3.coroutines.*'
      - 'android.util.Log'
      - 'dagger.*'
      - 'javax.inject.*'
      - 'jakarta.inject.*'
      - 'androidx.hilt.*'
    forbiddenPatterns: ''
  ForbiddenComment:
    active: true
    comments:                       # `values:` is deprecated in 1.23.8; `comments:` entries are REGEX
      - value: 'TODO:'
        reason: 'no TODO markers'
      - value: 'FIXME:'
        reason: 'no FIXME markers'
      - value: 'STOPSHIP:'
        reason: 'no STOPSHIP markers'
      - value: '\bT-\d+-\d+\b'
        reason: 'app planning id (T-xx-xx)'
      - value: '\bWR-\d+\b'
        reason: 'app planning id (WR-xx)'
      - value: '\bPhase\s+\d+\s+D-\d+\b'
        reason: 'app planning id (Phase NN D-xx)'
    allowedPatterns: ''
  # NO ForbiddenMethodCall / ForbiddenAnnotation: they need type resolution and silently do nothing in plain `detekt`.
```
Negative controls (all tested): `config/negative-controls/detekt/ForbiddenImports.kt` holds 5 forbidden imports + 3 planning-id comments + one `TODO:` (expects 5 `ForbiddenImport`, 4 `ForbiddenComment`). Scanner controls `config/negative-controls/{runCatching,print,di-fq,packages,planning-ids,clean}.kt.txt`, each starting with `// EXPECT: <rule ids separated by ;>`; `clean.kt.txt` mentions every banned word only in comments/strings/raw strings/char literals/identifier prefixes (`printable`, `printer`) and expects `(none)`.

### 6. `scripts/jitpack-consumer-probe.sh` (BLD-03 proof; Phase 10 D-07 reuses it)
Tested end-to-end against three local repos (real `.module`, JitPack-rewritten `.module`, POM-only). `REPO_URL` defaults to jitpack.io; set `TMPDIR` if you want the scratch dir elsewhere.
```bash
#!/usr/bin/env bash
# Usage: VERSION=<short-sha-or-tag> [REPO_URL=https://jitpack.io] [GROUP=…] scripts/jitpack-consumer-probe.sh
# Resolves every published module from an EMPTY Gradle dependency cache:
#   :jvmconsumer (kotlin.jvm) -> providers (jar->jar :core)        [jar->jar]
#   :app (AGP 9.2.1 app)      -> providers + keystore (AAR->jar :core) [AAR->jar]
set -euo pipefail
REPO_URL="${REPO_URL:-https://jitpack.io}"; VERSION="${VERSION:?set VERSION}"
GROUP="${GROUP:-com.github.Ygaray.voice-action-engine}"
WORK="$(mktemp -d)"; export GRADLE_USER_HOME="$WORK/gradle-home"; mkdir -p "$GRADLE_USER_HOME"
[ -d "$HOME/.gradle/wrapper" ] && ln -s "$HOME/.gradle/wrapper" "$GRADLE_USER_HOME/wrapper"   # reuse the Gradle DISTRIBUTION only, never dependencies
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
# Touch a :core type reachable ONLY transitively, so compile proves api exposure. ProviderId is a Phase 2+ type:
# in Phase 1 use any public :core symbol that exists (e.g. a tiny public `CoreModule` marker) or drop this and assert via :dependencies only.
CORE_TYPE="${CORE_TYPE:-io.github.ygaray.voiceactionengine.core.ProviderId}"
for d in app jvmconsumer; do printf 'package probe\nval coreId: %s = %s("x")\n' "$CORE_TYPE" "$CORE_TYPE" > $d/src/main/kotlin/probe/P.kt; done
./gradlew --no-daemon :jvmconsumer:compileKotlin :app:compileDebugKotlin
./gradlew --no-daemon :app:dependencies --configuration debugRuntimeClasspath | grep -E "voice-action-engine" || true
echo "PROBE OK ($GROUP:*:$VERSION from $REPO_URL) workdir=$WORK"
```
**Phase 1 wrinkle:** the prototype's `:core` had a public `ProviderId`; the real Phase 1 `:core` has *no* public types (D-09). The plan needs one deliberately public, deliberately tiny symbol in `:core` for this transitive-compile proof, or the probe must assert via `dependencies` only. Recommendation: assert via the resolved dependency graph (`voice-action-engine-core` present under both `providers` and `keystore`) **and** via `:jvmconsumer:compileKotlin` with no :core symbol; gate the stronger compile proof to Phase 2 when real public types exist. This avoids a public placeholder type.

### 7. Verification gate script
`scripts/verify-negative-controls.sh` (tested, 1 min warm; 0 failures over 40 gate-checks) plants one file `ZzPlant.kt` per module: public class with no modifier → `compile…Kotlin` must fail with `Visibility must be specified in explicit API mode`; `import javax.inject.Inject`, `import okhttp3.internal.Util`, `import android.util.Log`, a `// T-01-02` comment → `detekt` must fail with `weighted issues` AND `scanBannedConstructs` with `Banned constructs`; `runCatching`, `println`, `printStackTrace`, `@javax.inject.Inject` → scanner; an empty `config/detekt-baseline.xml` → `verifyNoDetektBaseline`. It uses `trap` cleanup and a per-task expected-message marker so "red for the wrong reason" is also caught. The Keystore module uses `:keystore:compileReleaseKotlin`.

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| `kotlin-android` plugin | AGP 9 built-in Kotlin; `kotlin { explicitApi(); compilerOptions {…} }` works on the AGP extension | No second plugin; `explicitApi()` available (contradicts the YAT-era assumption about a "partial" extension; YAT's `abiValidation` gap is real, `explicitApi` is not affected) |
| `detekt` `ForbiddenComment.values` | `comments: [{value: regex, reason}]` | Regex planning-id gate, syntax-only |
| OkHttp 4.x single artifact | OkHttp 5.x KMP `okhttp-jvm` / `okhttp-android` via Gradle module metadata | Matrix legs resolve `okhttp-jvm` automatically on the JVM classpath |
| BCV for JVM libs | Metalava (handles AGP 9 libs and kotlin.jvm) | One tool, additive-only semantics |

**Deprecated/outdated:** `ForbiddenComment.values`/`customMessage`; `ProjectDependency.getDependencyProject()` (removed in Gradle 9, use `.path`); configuring an included project whose directory does not exist (Gradle 9 error).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | JitPack indexes a publication whose declared groupId is already `com.github.Ygaray.voice-action-engine` (H1). Evidence: its log says "Looking for pom.xml in build directory and ~/.m2 / Found artifact …" and served stt artifacts from an `~/.m2` path identical in shape; not exercised for an explicit 4-segment group | Fallback ladder | Falls to F1 (no coordinate change). Probe decides |
| A2 | JitPack's POM/`.module` rewriting handles `.jar` modules exactly as it did stt's AAR modules | JitPack | F2 (POM-only) next; F4 only if jars cannot be served at all |
| A3 | Requesting any artifact URL (or the build.log URL) for a pushed SHA triggers a JitPack build; the short commit id is accepted as version ("the short commit id as the version" per docs.jitpack.io) | Probe procedure | Use the `api/builds` endpoint and/or the JitPack web UI "Get it" button; try the 10-char then 40-char form |
| A4 | The `modules` array from `https://jitpack.io/api/builds/<group>/<repo>/<ref>` lists exactly the published modules and will omit `sample` | SC1 | If it lists `sample`, something applied `maven-publish` to it; fix, re-push a new commit |
| A5 | `$VERSION` in JitPack builds equals the requested ref (tag `v1.0.0`, or the short SHA) | F3 | Publication version mismatch; JitPack rewrites versions anyway (stt literal `0.6.0` → `v0.6.0` observed) |
| A6 | A single `./gradlew` invocation with three publish tasks behaves like three separate `install` lines on JitPack | jitpack.yml | YAT (single command) is live-proven; stt (separate lines) is live-proven; both fine. If the single form trips, fall back to stt's per-line form |
| A7 | `minSdk = 35` / `compileSdk 36.1` for `:keystore` (copied from sibling catalogs via STACK.md) is what the orchestrator wants for the library | keystore build | Wrong minSdk blocks consumers on lower API; confirm with the orchestrator before the tag (not a Phase 1 blocker) |
| A8 | The 4.12.0 leg of the matrix is the default `test` task; `check` runs it automatically | Matrix | None (proven) |
| A9 | detekt default rules will produce additional findings on real Phase 2+ code beyond the five seen; the tuning cost is small | Pitfall 5 | Schedule tuning in those phases, never a baseline |

## Open Questions

1. **What can the Phase 1 "fake-provider harness" be before any provider/pipeline type exists?**
   - Known: D-09 forbids public placeholder types; `AiProvider`/`ProviderRouter` arrive in Phase 3 and the pipeline in Phase 2; ROADMAP SC5 says "A fake provider runs a `:core` pipeline test with zero network."
   - Unclear: whether SC5 accepts generic, type-agnostic fakes.
   - Recommendation: ship in `src/testFixtures` the *generic* harness (a scripted-response queue `ScriptedResponses<T>`, a `RecordingSink<T>`, and a test-only `NoNetworkGuard` assertion) plus one `:core` test that drives a **test-local** stand-in pipeline through them with zero network, and let Phases 2/3 add `FakeAiProvider`/`RecordingCommitSink` on top. "Zero network" is structurally guaranteed by `verifyCoreDependencyAllowlist` (no HTTP on the classpath), which is stronger than a runtime check. Put this reading in the plan and let the plan checker confirm SC5 is met.
2. **`minSdk`/`compileSdk` for `:keystore`** (see A7). Recommendation: copy SB (35 / 36.1), confirm with the orchestrator in the R-message.
3. **Publication version strategy** (literal vs `$VERSION`). Recommendation: read `VERSION` env with a `gradle.properties` fallback (F3) so the unrewritten output is already correct; the Phase 11 step "declared version equals the tag" then compares the fallback property with the tag.
4. **Should `src/testFixtures` be scanned by detekt?** Recommendation yes (`detekt { source.setFrom("src/main/kotlin", "src/test/kotlin", "src/testFixtures/kotlin") }`), because the fixtures are consumed by `:providers` and later phases; cost is nil. Not exercised in the prototype.
5. **Should the project keep REQ-ids (BLD-04, GATE-07) out of library comments too?** CLN-05 names app planning ids only; the regexes above deliberately do not match `[A-Z]+-\d\d` requirement ids. Decide whether to widen.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 17 | Gradle build (host; JitPack uses openjdk17) | ✓ | OpenJDK 17.0.19 | none needed |
| Gradle 9.4.1 distribution | wrapper | ✓ | `~/.gradle/wrapper/dists/gradle-9.4.1-bin` present | wrapper downloads it |
| Android SDK | `:keystore`, `:sample` | ✓ | `ANDROID_HOME=/home/yahir/Android/Sdk`; platforms 35, 36, 36.1, 37.0; build-tools 34-37 | JitPack auto-installs on its side |
| Network (Maven Central, Google Maven, Plugin Portal, jitpack.io) | dependency resolution, probes | ✓ | HTTP 200 on repo.maven.apache.org, jitpack.io | none |
| git, gh (auth as Ygaray), curl, jq, python3 | probes/scripts | ✓ | git 2.43.0, gh 2.45.0, curl 8.5.0, jq 1.7, Python 3.12.3 | none |
| adb / devices | not used in Phase 1 | ✓ (n/a) | 1.0.41 | none; no device work here |
| JitPack build of a pushed SHA | SC1 | ✗ in research | needs a pushed commit | Probe procedure below (Phase 1, Wave 1) |

**Missing dependencies with no fallback:** none.
**Missing with fallback:** the live JitPack probe (planned in execution, not research).

## JitPack Probe Procedure (Wave 1, before anything else; D-12)

Preconditions: the minimal publishing skeleton is committed (three publishable modules + inert `:sample` + `jitpack.yml` + executable `gradlew`) and pushed to `origin/main` (push authority confirmed for this effort per CLAUDE.md "Process"). Use a commit SHA, never a tag.

1. `SHA=$(git rev-parse --short=10 HEAD)`. Wait until `git ls-remote origin main` shows it.
2. Trigger + watch (A3): `curl -s -o /dev/null "https://jitpack.io/com/github/Ygaray/voice-action-engine/voice-action-engine-core/$SHA/voice-action-engine-core-$SHA.pom"` (404 while building is expected). Poll `curl -s https://jitpack.io/api/builds/com.github.Ygaray/voice-action-engine/$SHA` until `"status" : "ok"` (or `error`). Log: `https://jitpack.io/com/github/Ygaray/voice-action-engine/$SHA/build.log` (URL pattern verified live for stt `…/voice-engine-android/v0.6.0/build.log`).
3. Pass criteria in the log/API: `Running install command:` shows the three tasks; `Found artifact:` lists three `…voice-action-engine-{core,providers,keystore}`; the `✅ Build artifacts:` block lists exactly `com.github.Ygaray.voice-action-engine:voice-action-engine-{core,providers,keystore}:<SHA>`; the API `modules` array has those three and **no `sample`** (SC1 "JitPack's log shows `:sample` was never built or published").
4. Inspect served metadata: fetch the three `.pom` and `.module` files under `https://jitpack.io/com/github/Ygaray/voice-action-engine/<artifactId>/$SHA/`. Check `:providers` and `:keystore` depend on `com.github.Ygaray.voice-action-engine:voice-action-engine-core` with version `$SHA`; check no `testFixtures` string in `:core`; check the aggregator POM `https://jitpack.io/com/github/Ygaray/voice-action-engine/$SHA/voice-action-engine-$SHA.pom` (expected: a synthesized POM listing the three modules as `jar`/`aar` dependencies and never `sample`).
5. Clean-cache resolve: `VERSION=$SHA scripts/jitpack-consumer-probe.sh` (Code Example 6). Both the `:jvmconsumer` (jar→jar) and `:app` (AAR→jar) legs must pass from a fresh `GRADLE_USER_HOME`. Save the script output + build.log excerpt as the plan's evidence artifact (Phase 11 and Phase 10 D-07 reuse the script).
6. Decide: pass → record H1/A2 as verified and continue. Fail → walk the fallback ladder F1 → F2 → F3 in order, each as a **new commit** (failed builds are cached per ref). Only reaching F4/F5 triggers the D-12 message to `yahir-gsd-control-plane-f2` before Phase 2.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4.13.2 (+ kotlinx-coroutines-test 1.11.0, legacy okhttp mockwebserver in `:providers`); Gradle verification tasks as structural tests |
| Config file | none beyond Gradle build files; `config/detekt/detekt.yml` |
| Quick run command | `./gradlew :core:check :providers:check -q` (~25-40 s warm) |
| Full suite command | `./gradlew check` (~45 s warm, cold adds dependency download and Android lint) |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| BLD-01 | one-way module graph; `:core` has no HTTP/Android/DI/hub | structural | `./gradlew verifyModuleGraph :core:verifyCoreDependencyAllowlist` | ❌ Wave 0 |
| BLD-02 | toolchain pins + JVM 11 class files | structural | `./gradlew verifyBytecodeLevel` ; `grep -q '9.4.1' gradle/wrapper/gradle-wrapper.properties` ; `grep -E '^(kotlin|agp) =' gradle/libs.versions.toml` | ❌ Wave 0 |
| BLD-03 | per-module resolve from JitPack by SHA, `:sample` absent | live probe (manual-ish, scripted) | `VERSION=$SHA scripts/jitpack-consumer-probe.sh` + `curl …/api/builds/…/$SHA` (`modules`) | ❌ Wave 0 (justified: needs a pushed commit) |
| BLD-03 (local pre-check) | same from a local publish | integration | `scripts/jitpack-dry-run.sh` (clean clone → jitpack.yml install → `REPO_URL=file://…` probe) | ❌ Wave 0 |
| BLD-04 | detekt clean + invariant rules; planted constructs fail | unit+integration | `./gradlew detekt :core:verifyDetektControls :core:verifyInvariantScannerControls scanBannedConstructs` ; `scripts/verify-negative-controls.sh` | ❌ Wave 0 |
| BLD-05 | explicit API strict; Metalava dump + guarded check | structural + one-off | `./gradlew verifyExplicitApiStrict` ; wiring proof: `./gradlew apiDump` in a temp copy then `apiCheck` (and delete the dumps) | ❌ Wave 0 |
| BLD-07 | package root | structural | `git ls-files '*/src/main/kotlin/io/github/ygaray/voiceactionengine/*'` non-empty; `! git ls-files '*/src/main/**/*.kt' | grep -v voiceactionengine` | ❌ Wave 0 |
| BLD-08 | ECOSYSTEM/README coordinates; gitignore | unit | `grep -q 'voice-action-engine-core' ECOSYSTEM.md && ! grep -qE 'com\.github\.Ygaray:voice-action-engine([^-]|$)' ECOSYSTEM.md README.md` ; `git check-ignore -q sample/src/debug/assets/sb-a10-fixture.json graphify-out/x` | ❌ Wave 0 |
| BLD-09 | harness runs a test with zero network | unit | `./gradlew :core:test :core:verifyNoTestFixturesPublished` | ❌ Wave 0 |
| CLN-01 | no DI imports/annotations/artifacts | structural | scanner + detekt controls (above) | ❌ Wave 0 |
| CLN-05 | no planning ids in comments | unit | detekt controls + scanner `planning-ids.kt.txt` | ❌ Wave 0 |
| (A1 plumbing, carried to Phase 4) | matrix legs + reflective guard + compile floor | integration | `./gradlew :providers:test :providers:testOkhttp521 :providers:testOkhttp550 :providers:verifyOkHttpCompileFloor` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew :<touched module>:check -q` (25-40 s).
- **Per wave merge:** `./gradlew check`.
- **Phase gate:** `./gradlew check` green + `scripts/verify-negative-controls.sh` green + live JitPack probe evidence saved + `git status` shows no `api.txt` or fixture staged.

### Wave 0 Gaps
Greenfield: every file is a gap. Specifically: `settings.gradle.kts`, root `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle/invariants.gradle.kts`, wrapper + `gradlew` (mode 100755), `jitpack.yml`, `config/detekt/detekt.yml`, `config/negative-controls/**`, the three module build files, `:core` test + testFixtures, `:providers` guard test, `scripts/*.sh`, `.gitignore` additions, ECOSYSTEM/README edits. No framework install needed (Gradle resolves everything).

## Security Domain

`security_enforcement` is enabled (absent = enabled; `security_asvs_level: 1`, `security_block_on: high` in `.planning/config.json`).

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | none in Phase 1 |
| V3 Session Management | no | none |
| V4 Access Control | no | none |
| V5 Input Validation | no (no input handling) | n/a until Phase 2+ |
| V6 Cryptography | no (scaffold only; `:keystore` logic is Phase 6) | never hand-roll; AndroidKeyStore AES/GCM later |
| V7 Error Handling and Logging | yes | no logging sinks: `android.util.Log`, `println`, `printStackTrace`, `System.out/err` banned and scanned |
| V8 Data Protection | yes | SB-derived fixture `sb-a10-fixture*.json` gitignored (repo is public, LE-7); leak scan for the fixture name and key-shaped strings is the Phase 11 gate |
| V10 Malicious Code / V14 Configuration | yes | pinned versions from sibling-proven catalogs, Gradle wrapper with `distributionSha256Sum`, no `strictly`/BOM forcing of consumer versions, no secrets in build files (`:sample` reads nothing at configuration time), `local.properties` ignored |

### Known Threat Patterns for this stack
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Committed secret/fixture (public repo) | Information disclosure | `.gitignore` globs + Phase 11 leak scan |
| Secrets/transcripts reaching logs | Information disclosure | Banned logging constructs, no logging interceptors (Phase 4) |
| Dependency confusion / slopsquatted coordinate | Tampering | Coordinates restricted to the locked stack; all resolved from authoritative registries in the prototype |
| Build-time supply chain via JitPack | Tampering | Immutable tags/SHAs; install list names modules explicitly; wrapper checksum |
| Gate that silently stops gating (false green) | Repudiation/Tampering | Every gate has a negative control that asserts red for the right reason (Pitfalls 1, 3) |
| Internal OkHttp API use breaking on 5.x at runtime | Denial of service (consumer crash) | `ForbiddenImport okhttp3.internal.*` + scanner + matrix |

## Sources

### Primary (HIGH confidence)
- Scratch prototype run this session (Gradle 9.4.1, JDK 17.0.19, AGP 9.2.1, Kotlin 2.3.20, detekt 1.23.8, Metalava 0.5.1, real network): all `[VERIFIED: scratch prototype]` items, including local Maven publish, clean-`GRADLE_USER_HOME` consumer resolution, and the planted-construct run (0 failures over the gate-checks).
- Live JitPack, stt-engine v0.6.0: aggregator POM `https://jitpack.io/com/github/Ygaray/voice-engine-android/v0.6.0/voice-engine-android-v0.6.0.pom`, per-module `.pom`/`.module` under `…/com/github/Ygaray/voice-engine-android/<artifactId>/v0.6.0/`, `build.log` and `api/builds` JSON; resolved from an empty Gradle cache on Gradle 9.4.1 + AGP 9.2.1 (graph and `checkDebugAarMetadata` downloaded all four AARs). 401 on `com/github/Ygaray/voice-engine-android-corrections/…`.
- Live JitPack, yahirandroidtaste v1.13.0 `build.log`: AGP 9.2.1 + Gradle 9.4.1 + compileSdk 36.1 + JDK 17 builds on JitPack.
- Sibling repos read this session: `yahirandroidtaste` (`build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `jitpack.yml`, `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`, `tools/README-api-guard.md`), `stt-engine/android` (`jitpack.yml`, `settings.gradle.kts`, module publish blocks, commit `b8c9fdb` diff), `backup-engine` (`jitpack.yml`, `backup/build.gradle.kts`).
- detekt 1.23.8 `default-detekt-config.yml` extracted from `detekt-core-1.23.8.jar` in the Gradle cache (ForbiddenComment `comments`, ForbiddenImport, ForbiddenMethodCall, ForbiddenAnnotation defaults).
- Gradle Plugin Portal / Maven Central `maven-metadata.xml` (Metalava plugin 0.5.1 latest; OkHttp 5.2.1/5.5.0; mockwebserver 5.2.1/5.5.0).
- Project files: `01-CONTEXT.md`, `REQUIREMENTS.md`, `ROADMAP.md`, `research/SUMMARY.md`, `research/PITFALLS.md` (Pitfalls 1-4), `v1.0-DECISION-MAP.md` § Phase 1, `cross-repo/HANDOFF.md`, `CROSS-REPO-SCOPE-CONTRACT.md` E5/E7, `.claude/CLAUDE.md`.

### Secondary (MEDIUM confidence)
- [docs.jitpack.io/building](https://docs.jitpack.io/building/) and [BUILDING.md](https://raw.githubusercontent.com/jitpack/jitpack.io/master/BUILDING.md): multi-module group `com.github.User.Repo`, aggregator, `GROUP/ARTIFACT/VERSION` env, `install` must populate `~/.m2`; docs do not address `.module` handling.
- [docs.jitpack.io](https://docs.jitpack.io/): commit-hash versions, first-request build trigger.
- [metalava-gradle README](https://github.com/tylerbwong/metalava-gradle): supported project types and task names.
- [detekt 1.23.8 style rules](https://detekt.dev/docs/1.23.8/rules/style/#forbiddencomment): `comments`/`values` deprecation; type-resolution requirements.

### Tertiary (LOW confidence)
- None relied upon. JitPack behaviour for an explicit 4-segment groupId and for jar modules is an explicit `[ASSUMED]` (A1, A2) pending the probe.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH, versions pinned to sibling-proven catalogs and resolved in the prototype.
- Architecture / gates: HIGH, each gate was run and broken on purpose.
- Publishing on live JitPack: MEDIUM, strong indirect evidence (stt live artifacts, locally emulated rewrites, YAT build log) but the exact E5 + jar case needs the probe.
- Pitfalls: HIGH, all but the JitPack ones were reproduced.

**Research date:** 2026-09-30
**Valid until:** 2026-10-30 (toolchain pins are deliberately frozen to consumer parity; re-check only the JitPack probe results and detekt/Metalava versions if the phase slips)
