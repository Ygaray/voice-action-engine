---
phase: 01-scaffold-publishing-proof
verified: 2026-09-30T23:10:00Z
status: passed
score: 5/5 must-haves verified
covered_files: [".gitignore", ".planning/REQUIREMENTS.md", ".planning/phases/01-scaffold-publishing-proof/01-01-PLAN.md", ".planning/phases/01-scaffold-publishing-proof/01-01-SUMMARY.md", ".planning/phases/01-scaffold-publishing-proof/01-02-PLAN.md", ".planning/phases/01-scaffold-publishing-proof/01-02-SUMMARY.md", ".planning/phases/01-scaffold-publishing-proof/01-03-PLAN.md", ".planning/phases/01-scaffold-publishing-proof/01-03-SUMMARY.md", ".planning/phases/01-scaffold-publishing-proof/01-04-PLAN.md", ".planning/phases/01-scaffold-publishing-proof/01-04-SUMMARY.md", ".planning/phases/01-scaffold-publishing-proof/01-05-PLAN.md", ".planning/phases/01-scaffold-publishing-proof/01-05-SUMMARY.md", ".planning/phases/01-scaffold-publishing-proof/01-06-PLAN.md", ".planning/phases/01-scaffold-publishing-proof/01-06-SUMMARY.md", "ECOSYSTEM.md", "README.md", "build.gradle.kts", "config/detekt/detekt.yml", "config/negative-controls/clean.kt.txt", "config/negative-controls/detekt/ForbiddenImports.kt", "config/negative-controls/di-fq.kt.txt", "config/negative-controls/packages.kt.txt", "config/negative-controls/planning-ids.kt.txt", "config/negative-controls/print-fq.kt.txt", "config/negative-controls/print.kt.txt", "config/negative-controls/runCatching.kt.txt", "config/negative-controls/string-template.kt.txt", "core/build.gradle.kts", "core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/CoreModule.kt", "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ScriptedHarnessTest.kt", "core/src/testFixtures/kotlin/io/github/ygaray/voiceactionengine/core/testing/NoNetworkGuard.kt", "core/src/testFixtures/kotlin/io/github/ygaray/voiceactionengine/core/testing/RecordingSink.kt", "core/src/testFixtures/kotlin/io/github/ygaray/voiceactionengine/core/testing/ScriptedResponses.kt", "gradle.properties", "gradle/invariants.gradle.kts", "gradle/libs.versions.toml", "gradle/wrapper/gradle-wrapper.properties", "jitpack.yml", "keystore/build.gradle.kts", "keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/KeystoreModule.kt", "providers/build.gradle.kts", "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/ProvidersModule.kt", "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/OkHttpVersionGuardTest.kt", "sample/build.gradle.kts", "sample/src/main/AndroidManifest.xml", "sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/SampleModule.kt", "scripts/jitpack-consumer-probe.sh", "scripts/jitpack-dry-run.sh", "scripts/jitpack-live-probe.sh", "scripts/verify-api-dump.sh", "scripts/verify-negative-controls.sh", "scripts/verify-repo-hygiene.sh", "settings.gradle.kts"]
covered_digest: "v1:sha256:591dc66d1ee584b93b0915d5dd09c89a82db4854508b68d9a939765a090587d3"
behavior_unverified: 0
overrides_applied: 1
overrides:
  - must_have: "A fake provider runs a :core pipeline test with zero network"
    reason: "No provider or pipeline type exists until Phases 2 and 3, and D-09 forbids public placeholder types. Phase 1 ships the generic unpublished primitives (ScriptedResponses, RecordingSink, NoNetworkGuard) plus a :core test that drives a test-local stand-in pipeline. FakeAiProvider lands in Phase 3 as a thin layer over them. Recorded as 'orchestrator resolution 2, accepted' in 01-05-PLAN.md and 01-RESEARCH.md Open Question 1."
    accepted_by: "orchestrator (per 01-05-PLAN.md resolution 2; carried forward by the verifier, not newly granted)"
    accepted_at: "2026-09-30T00:00:00Z"
---

# Phase 1: Scaffold & Publishing Proof Verification Report

**Phase Goal:** A consumer can resolve every published engine module from JitPack at its per-module coordinate. From the first commit on, one `./gradlew check` enforces the library's structural invariants and provides the test harnesses every later phase builds on.
**Verified:** 2026-09-30
**Status:** passed (one accepted scope-reading and three advisories below)
**Re-verification:** No, initial verification. Verified against the final state (HEAD `08db11f`, after the code-review fixes in 01-REVIEW-FIX.md).

## Goal Achievement

### Observable Truths (ROADMAP success criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | From an empty Gradle cache a throwaway consumer resolves the three per-module coordinates by commit SHA, `:providers` pulls `:core`, `:sample` never built or published, ECOSYSTEM.md lists the same coordinates, no control-plane writes | VERIFIED | `evidence/jitpack-probe.txt` holds two live passes (`7f9db22944` rung 0, final `5fc723786b`): JitPack build API lists exactly `voice-action-engine-{core,keystore,providers}`; log shows the explicit install command with no `:sample`; POM/module 200 for each; providers->core and keystore->core resolve at the same SHA; consumer with empty cache prints `PROBE OK`. `jitpack.yml` install line names only the three `publishReleasePublicationToMavenLocal` tasks. ECOSYSTEM.md and README.md carry the per-module table and retire the aggregator coordinate. I re-ran `scripts/jitpack-dry-run.sh` at HEAD (simulated JitPack, `-Xjdk-release` and other post-review build changes included): `DRY RUN OK`. No registry/deps-index writes exist in the repo. See advisory A1 on the live proof SHA. |
| 2 | `./gradlew check` passes on the four-module build and graph, package root, toolchain pins, JVM 11 bytecode (major 55), `:core` classpath free of HTTP/Android/DI/hub artifacts | VERIFIED | I ran `./gradlew check`: BUILD SUCCESSFUL (137 tasks). `settings.gradle.kts` includes the four modules; `verifyModuleGraph`, `verifyCoreDependencyAllowlist`, `verifyBytecodeLevel` (constant 55, fails on zero classes, also opens the AAR's `classes.jar`), `verifyNoDiArtifacts` are wired into `check`. Pins: Kotlin 2.3.20, AGP 9.2.1, wrapper Gradle 9.4.1 with SHA, `jdk: openjdk17`. I confirmed independently that `core.jar` and `providers.jar` class files are `cafebabe 0000 0037` (major 55) and that `:core:dependencies` lists only stdlib, annotations, coroutines, serialization. All source under `io/github/ygaray/voiceactionengine/`. |
| 3 | Planting any banned construct in library source makes `check` fail; detekt `maxIssues: 0`, no baseline | VERIFIED | I ran `scripts/verify-negative-controls.sh`: exit 0, 69 `ok` lines, `negative-control failures: 0`. It plants each construct (DI import and FQ annotation, `android.util.Log`, `okhttp3.internal`, `mockwebserver3`, `runCatching` including inside a string template, `println` including FQ, `printStackTrace`, planning-id comment, public-without-modifier) in each of the three modules and asserts the right gate goes red, plus seven build-file plants and baseline plants. I also planted `runCatching` and a `WR-12` comment by hand in `:core`: `scanBannedConstructs` failed on both with correct line numbers, then I removed the file. `config/detekt/detekt.yml` has `build.maxIssues: 0`, `buildUponDefaultConfig = true`, no `baseline` property, no baseline XML tracked; `verifyNoDetektBaseline` enforces it. |
| 4 | `explicitApi()` rejects undeclared visibility in each published module; Metalava emits `api.txt` for all three; dumps committed only at the `v1.0.0` cut | VERIFIED | `explicitApi()` in all three module builds plus `verifyExplicitApiStrict` (reads the live Kotlin extension). The "public without modifier" plant went red with "Visibility must be specified in explicit API mode" for core, providers and keystore. `scripts/verify-api-dump.sh` (re-run by me): `API DUMP PROOF OK`, dumping all three modules in an isolated copy, additive change stays green, removal goes red, real tree untouched. No `api.txt` is tracked or present (`ls */api.txt` fails, `git ls-files` empty). `verifyApiDumpPresent` (review fix WR-07) prevents the compat check going silently fail-open once a `v*` tag exists; it is exercised by three negative controls. |
| 5 | `check` runs the harnesses: fake provider pipeline test with zero network; OkHttp matrix trivial test on 4.12.0 / 5.2.1 / 5.5.0 with a per-leg runtime guard; `graphify-out/` and A10 fixture path gitignored | VERIFIED (PASSED (override) for the literal "fake provider" wording) | `:providers:check` depends on `test`, `testOkhttp521`, `testOkhttp550`. `OkHttpVersionGuardTest` reads `okhttp3.OkHttp.VERSION` reflectively (not inlined) and compares it to the leg's expected version; the negative-control run printed `OKHTTP_RUNTIME=4.12.0`, `5.2.1`, `5.5.0` for the three legs and each went red on a wrong expectation. `:core` `ScriptedHarnessTest` (7 tests) passes in `check`. `git check-ignore` confirms `/graphify-out/` (.gitignore:51) and `sb-a10-fixture*.json` (.gitignore:48) for `sample/src/debug/assets/sb-a10-fixture.json`. Fixtures are not published: after regenerating the publication files, `module.json` and POM contain no `test-fixtures` (`verifyNoTestFixturesPublished` is wired into `check` and is non-vacuous). The literal "fake provider" is a documented scope-reading, not a built `FakeAiProvider`; see the override and advisory A2. |

**Score:** 5/5 truths verified (1 via accepted override), 0 behavior-unverified.

The behavior-dependent claims (gates that must go red on a planted construct, matrix legs that must run the version they claim) are proven by executed negative controls, not by symbol presence.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, wrapper | Four-module build, pinned toolchain | VERIFIED | Substantive and wired; all versions match the pins. |
| `core/`, `providers/`, `keystore/` build files + marker sources | Published modules, `maven-publish`, per-module artifactIds, JVM 11, explicitApi | VERIFIED | `voice-action-engine-{core,providers,keystore}`; `-Xjdk-release=11` added by review fix WR-09; keystore uses `singleVariant("release")`. Marker objects are `internal` by design (D-09: no placeholder public types). |
| `sample/` | Inert unpublished app, edges to providers+keystore (+core), OkHttp 5.2.1 pin | VERIFIED | No `maven-publish`, no config-time reads, absent from `jitpack.yml`; appears nowhere in JitPack's module list. |
| `gradle/invariants.gradle.kts` | Scanner, bytecode, explicit API, graph, allowlist, DI, baseline, floor gates | VERIFIED | All gates wired into `check`; scanner rewritten as a string-template-aware lexer by WR-02 and retested. |
| `config/detekt/detekt.yml`, `config/negative-controls/**` | Zero-baseline rules and controls | VERIFIED | Controls carry `// EXPECT:` headers checked by `verifyInvariantScannerControls`. |
| `core/src/testFixtures/**`, `core/src/test/**` | Unpublished harness primitives and test | VERIFIED | Substantive, used by `:core` and `:providers` tests, excluded from publication. |
| `providers/src/test/.../OkHttpVersionGuardTest.kt` | Reflective per-leg version guard | VERIFIED | Reflective read, plus a real MockWebServer round trip. |
| `scripts/*.sh` | Live probe, consumer probe, dry run, api-dump proof, negative controls, hygiene | VERIFIED | All re-run by me except the live/consumer probes (need network and a pushed SHA); their evidence is recorded. |
| `evidence/jitpack-probe.txt`, `evidence/phase-gate.txt` | Recorded live proof | VERIFIED | Consistent with the script logic and with the JitPack-facing files. |
| `ECOSYSTEM.md`, `README.md` | Per-module coordinates | VERIFIED | Aggregator coordinate retired; "Current published tag: none". |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| Each module build | `invariants.gradle.kts` | `apply(from = ...)` | WIRED | All three published modules apply it; `check` depends on every gate. |
| `:providers` / `:keystore` | `:core` | `api(project(":core"))` | WIRED | Served POMs reference `voice-action-engine-core` at the same version (live evidence). |
| `:providers` tests | `:core` testFixtures | `testImplementation(testFixtures(project(":core")))` | WIRED | `OkHttpVersionGuardTest` imports `RecordingSink`. |
| `check` | matrix legs | `tasks.named("check") { dependsOn(legTest) }` | WIRED | Legs use the same compiled test classes with swapped runtime classpaths and JVM attributes. |
| `jitpack.yml` | per-module publish tasks | explicit install list | WIRED | Names `:core`, `:providers`, `:keystore` only. |

### Data-Flow Trace (Level 4)

Not applicable: this phase renders no dynamic data.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Full gate suite | `./gradlew check` | BUILD SUCCESSFUL | PASS |
| Every gate goes red on its plant | `scripts/verify-negative-controls.sh` | exit 0, 69 ok, 0 failures | PASS |
| Hygiene (package root, ignores, no tags, no api.txt/fixture/baseline) | `scripts/verify-repo-hygiene.sh` | `HYGIENE OK` | PASS |
| Metalava dump/additive/removal | `scripts/verify-api-dump.sh` | `API DUMP PROOF OK` | PASS |
| Simulated JitPack publish + empty-cache consumer at HEAD | `scripts/jitpack-dry-run.sh` | `DRY RUN OK version=dryrun-08db11f004` | PASS |
| Hand-planted `runCatching` + `WR-12` comment | `./gradlew :core:scanBannedConstructs` | failed on both, then clean after removal | PASS |
| Published metadata free of test-fixtures | regenerate publication, grep | 0 matches | PASS |
| Class-file major | header bytes of `core.jar`, `providers.jar` | `0x37` = 55 | PASS |

### Probe Execution

| Probe | Command | Result | Status |
|-------|---------|--------|--------|
| `scripts/jitpack-dry-run.sh` | `bash scripts/jitpack-dry-run.sh` | exit 0 | PASS |
| `scripts/verify-api-dump.sh` | `bash scripts/verify-api-dump.sh` | exit 0 | PASS |
| `scripts/verify-repo-hygiene.sh` | `bash scripts/verify-repo-hygiene.sh` | exit 0 | PASS |
| `scripts/verify-negative-controls.sh` | `bash scripts/verify-negative-controls.sh` | exit 0 | PASS |
| `scripts/jitpack-live-probe.sh` | not re-run (needs network and a pushed SHA; it was re-run read-only by the fixer) | evidence recorded | see advisory A1 |

### Requirements Coverage

Plan frontmatter unions to exactly the phase's ten IDs; REQUIREMENTS.md maps the same ten to Phase 1 and nothing else. No orphans. BLD-06 is Phase 4 by design.

| Requirement | Source Plan(s) | Description | Status | Evidence |
|-------------|----------------|-------------|--------|----------|
| BLD-01 | 01-01, 01-04 | Four modules, one-way deps, `:core` free of HTTP/Android/DI/hub | SATISFIED | `verifyModuleGraph`, `verifyCoreDependencyAllowlist` green; controls for HTTP dependency and forbidden edge go red. |
| BLD-02 | 01-01, 01-04 | Toolchain pins, JVM 11 bytecode | SATISFIED | Pins in catalog/wrapper; major 55 confirmed; JVM-17 plant goes red at `verifyBytecodeLevel`. |
| BLD-03 | 01-01, 01-02, 01-06 | Per-module JitPack coordinates by SHA, empty cache, `:sample` absent | SATISFIED | Two live passes recorded; see A1 for the final-HEAD caveat. The requirement text also says "again at the tag", which is the Phase 11 cut. |
| BLD-04 | 01-03, 01-06 | detekt zero-baseline plus banned constructs | SATISFIED | Gates and 69 controls green. |
| BLD-05 | 01-01, 01-04, 01-06 | explicitApi plus Metalava wiring, dumps at the cut | SATISFIED | Strict on all three, dump proof OK, no `api.txt` committed. |
| BLD-07 | 01-01, 01-06 | Package root `io.github.ygaray.voiceactionengine` | SATISFIED | All sources under that root; hygiene script asserts it. |
| BLD-08 | 01-01, 01-06 | ECOSYSTEM.md coordinates, `graphify-out/` and fixture ignored | SATISFIED | Verified in docs and via `git check-ignore`. |
| BLD-09 | 01-05 | Fake-provider harness in `:core` test sources only | SATISFIED (accepted reading) | Generic harness primitives in `testFixtures` (unpublished, verified). `FakeAiProvider` itself deferred to Phase 3 under the recorded orchestrator resolution; see A2. |
| CLN-01 | 01-03, 01-06 | No DI annotations | SATISFIED | Import/FQ-annotation scanner, detekt ForbiddenImport, `verifyNoDiArtifacts`; planted DI import/annotation/artifact all go red. |
| CLN-05 | 01-03, 01-06 | No planning ids in comments | SATISFIED | detekt ForbiddenComment plus comment-only scanner rule; hand plant confirmed. |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `config/negative-controls/detekt/ForbiddenImports.kt` | 12 | `TODO:` marker | Info | Intentional negative-control input, not library source; detekt `source` excludes it. |
| `OkHttpVersionGuardTest.kt` | 17 | `println` | Info | Test-only diagnostic marker the negative controls depend on; `scanBannedConstructs` covers `src/main` only. |
| Marker objects in `*Module.kt` | - | `internal object` stubs | Info | Deliberate (D-09): modules must emit class files without public placeholder API. |

No unreferenced `TBD`, `FIXME` or `XXX` markers in any file changed by this phase.

### Advisories (not blocking)

**A1. The live JitPack proof is at `5fc723786b`, not at HEAD `08db11f`.** The 16 review fixes are committed locally and unpushed (origin/main is `5fc7237`). They touch publish-path files (`-Xjdk-release=11` in the three module builds, `VERSION` trimming, a new `verifyApiDumpPresent` task that runs `git` only inside `check`). The fixer re-ran the live probe only against the already-built old ref. I covered the gap with the local simulated-JitPack dry run at HEAD, which passes, but the new build has not been built by real JitPack. The first push after this phase will exercise it; BLD-03 is re-proven "at the tag" in Phase 11 anyway. Recommend pushing and re-running `scripts/jitpack-live-probe.sh <new-sha>` before relying on it.

**A2. "Fake provider" is a stand-in, and BLD-09 is marked Complete.** The literal `FakeAiProvider` does not exist; Phase 1 ships `ScriptedResponses`, `RecordingSink`, `NoNetworkGuard` and a test-local stand-in pipeline. That reading was accepted by the orchestrator and is carried here as an override. The traceability risk is that REQUIREMENTS.md marks BLD-09 done in Phase 1, so no Phase 3 requirement owns delivering `FakeAiProvider`. Phase 3's success criteria reference "fake-provider tests", which will implicitly force it, but the orchestrator may want to add it to a Phase 3 criterion explicitly.

**A3. Minor.** `NoNetworkGuard` is a tripwire on the default `ProxySelector` only (limits now documented by WR-06). `01-VALIDATION.md` is still `draft / nyquist_compliant: false` by design (finalizer-owned). `ECOSYSTEM.md` mentions the planning label `D-01` in a heading; CLN-05 governs library comments, so this is outside its scope.

### Tag and Commit Hygiene

`git tag -l` is empty and `git ls-remote --tags origin` returned nothing. No `api.txt`, baseline XML, fixture JSON, `local.properties` or keystore is tracked. No tag was created by this verification, and nothing was committed. The working tree shows only the pre-existing `.planning/` changes; my plant files were removed.

### Human Verification Required

None. The one live-network item (A1) is an advisory recommendation, not a blocker: the pre-fix live proof and the post-fix dry run both pass.

### Gaps Summary

No gaps. All five roadmap success criteria hold in the final codebase state and all ten requirement IDs are accounted for and satisfied.

---

_Verified: 2026-09-30_
_Verifier: Claude (gsd-verifier)_
