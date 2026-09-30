---
status: complete
result: all_pass
gate: 1
phase: 01-scaffold-publishing-proof
source: [01-ROADMAP success criteria 1-5]
device: none (headless CLI/build + live JitPack; not device-verifiable by nature, no adb used)
apk: n/a (artifacts: core.jar md5 2080ab4a048088a3a2b89b4080463aba, providers.jar md5 94c82930e1863f2b2e5fb8580c5b9acd, keystore-release.aar md5 5b955c8790cf7fa78c5c5e168a3f5f98 @ 96c9c62; live ref a40f8319ca)
run: 2026-09-30T22:00:00Z
---

<!--
Gate-1 self-UAT for a build/publishing-infrastructure phase. Driver: no project AGENT-*-TESTING.md exists and the
phase has no UI; the target surface is the Gradle CLI build plus live JitPack, so no device playbook applies
(orchestrator instruction: device work is N/A-not-device-verifiable for this phase).
-->

# Self-UAT Log: Phase 1 (Scaffold & Publishing Proof), one log for the phase

**Target:** headless (repo CLI + live jitpack.io). No adb, no device, no emulator.
**Build identity:** HEAD `96c9c62` (pushed to origin/main); non-`.planning` tree at HEAD is byte-identical to the live-probed ref `a40f8319ca` (`git diff --stat a40f8319ca HEAD -- . ':!.planning'` empty). Working tree clean apart from orchestrator bookkeeping under `.planning/` and `graphify-out/`.
**Pre-flight:** JDK 17.0.19, Gradle wrapper 9.4.1, `jq` present, network to jitpack.io reachable.
**Prior verdict audited:** `01-VERIFICATION.md` (status passed, 5/5, one override). Treated as claims; every criterion below was re-observed this run.

## Criteria

### 1. SC1: per-module coordinates resolve from an empty Gradle cache by SHA; :sample never built or published; ECOSYSTEM.md lists the same coordinates
result: passed
- **Rung:** 3 (live headless data/log checks against jitpack.io; no visual rung applicable)
- **Target:** headless, live JitPack
- **Expected:** a throwaway consumer resolves `com.github.Ygaray.voice-action-engine:<artifactId>` for core, providers (pulling core) and keystore at a commit SHA; JitPack log shows no `:sample`; ECOSYSTEM.md lists the same per-module coordinates; no control-plane registry writes from this repo.
- **Arranged (seeded):** none (the ref `a40f8319ca` was already built on JitPack; probe is idempotent).
- **Did (drove):** `EVIDENCE_FILE=.planning/phases/01-scaffold-publishing-proof/evidence/self-uat-live-probe.txt scripts/jitpack-live-probe.sh a40f8319ca` (SKIP_CONSUMER unset) -> exit 0, `LIVE PROBE PASS`.
- **Observed:**
  - JitPack build API `status=ok`, `isTag=false`, commit `a40f8319ca859c01...`, modules exactly `voice-action-engine-{core,keystore,providers}`.
  - Install command in the JitPack build log: `./gradlew :core:publishReleasePublicationToMavenLocal :providers:publishReleasePublicationToMavenLocal :keystore:publishReleasePublicationToMavenLocal`. No `:sample` in it, no `sample` in any "Found artifact" line or the api module list (probe asserts a FORBID_RE=sample negative guard).
  - POM and Gradle `.module` 200 for each module; packaging jar, jar, aar; providers->core and keystore->core dependency lines resolve at `a40f8319ca`.
  - Consumer probe ran with `GRADLE_USER_HOME=$(mktemp -d)` (confirmed in scripts/jitpack-consumer-probe.sh:15, only the wrapper dist is symlinked), so the cache is empty; dependency tree printed providers -> core and keystore -> core, `PROBE OK`.
  - `ECOSYSTEM.md:27-29` and `README.md:11-13` list the three per-module coordinates; aggregator coordinate retired. `jitpack.yml` has no `sample` reference. No registry/deps-index files written by this repo.
- **Evidence:** `.planning/phases/01-scaffold-publishing-proof/evidence/self-uat-live-probe.txt` (probe transcript, reproduced in part above); prior recorded passes in `.planning/phases/01-scaffold-publishing-proof/evidence/jitpack-probe.txt`.

### 2. SC2: `./gradlew check` passes on the four-module build; JVM 11 bytecode (major 55); :core runtime classpath has no HTTP/Android/DI/hub artifact
result: passed
- **Rung:** 0/3 (compile + build gates + binary inspection; no visual claim)
- **Target:** headless
- **Expected:** green `check`; graph `:sample -> {:providers, :keystore} -> :core`; Kotlin 2.3.20 / AGP 9.2.1 / Gradle 9.4.1 / JDK 17; every published class major 55; :core runtime classpath clean.
- **Arranged (seeded):** none.
- **Did (drove):** `./gradlew check --console=plain` -> `BUILD SUCCESSFUL`, 137 actionable tasks. Then independent inspection (not via the repo's own gates): class-file headers read with `unzip -p | xxd` over EVERY class in `core/build/libs/core.jar`, `providers/build/libs/providers.jar` and the `classes.jar` inside `keystore-release.aar`; `./gradlew -q :core:dependencies --configuration runtimeClasspath`; `grep` of `settings.gradle.kts`, `gradle/libs.versions.toml`, `gradle-wrapper.properties`, `sample/build.gradle.kts`.
- **Observed:**
  - Class-file major: 0 of the classes (1 per jar, marker objects by design) are other than `0x0037` (55) in all three artifacts. Note each published module currently holds a single internal marker class, so the sweep is small but complete; the repo's `verifyBytecodeLevel` gate additionally fails on zero classes and was green.
  - `:core` runtimeClasspath contains only kotlinx-coroutines-core 1.11.0, kotlinx-serialization-json 1.11.0, kotlin-stdlib 2.3.20, annotations. No okhttp, android, dagger/javax.inject or other hub module.
  - `settings.gradle.kts`: `include(":core", ":providers", ":keystore", ":sample")`; sample depends on all three; pins agp 9.2.1, kotlin 2.3.20, okhttp 4.12.0, Gradle distribution 9.4.1, JDK 17 (toolchain not configured, target JVM 11 by explicit settings).
  - Test counts: `:core:test` ScriptedHarnessTest 7/0 failures; `:providers` guard 2 tests x 3 legs, 0 failures.
- **Evidence:** terminal transcript of the commands above (tail: `BUILD SUCCESSFUL in 5s, 137 actionable tasks`); artifact md5s in the frontmatter.

### 3. SC3: planting any banned construct in library source makes `./gradlew check` fail; detekt maxIssues 0, no baseline
result: passed
- **Rung:** 3 (negative controls executed against the real build)
- **Target:** headless
- **Expected:** each of DI annotation, `android.util.Log`, `okhttp3.internal.*`, `mockwebserver3.*`, `runCatching`, `println`, `printStackTrace`, app planning id in a comment turns `check` red, for the right reason; no detekt baseline exists.
- **Arranged (seeded):** plants created by the script itself (and removed by its trap).
- **Did (drove):** (a) `scripts/verify-negative-controls.sh` in the foreground -> exit 0, 69 `ok` lines, `negative-control failures: 0`. It covers every banned construct in each of core, providers and keystore (including FQ `kotlin.io.println`, FQ DI annotation, `runCatching` inside a string template, public-without-modifier), plus a JDK-16-API-on-JVM-11 plant, baseline file/property plants, missing `api.txt`, core gaining HTTP, forbidden module edge, DI artifact, explicitApi off, OkHttp floor raised, core at JVM 17, testFixtures leaking into publication, and the three matrix guards. (b) Independent hand plant, not via the script: wrote `core/.../ZzUat.kt` containing the comment `// Phase 03 D-04 and T-01-02 rationale`, ran `./gradlew -q :core:check`.
- **Observed:** (a) every plant red for its intended marker, tree restored (`git status` clean for source paths afterwards). (b) `:core:detekt` FAILED with `app planning id (T-xx-xx) [ForbiddenComment]` and `app planning id (Phase NN D-xx) [ForbiddenComment]` at the planted line; the plant was then removed and `git status core` was empty. `config/detekt/detekt.yml` has `build.maxIssues: 0`, no `baseline` property, and `git ls-files` contains no baseline XML.
- **Evidence:** `.planning/phases/01-scaffold-publishing-proof/evidence/self-uat-negative-controls.txt` (full 69-line transcript).

### 4. SC4: explicitApi rejects undeclared visibility in each published module; Metalava dumps exist for all three; dumps committed only at v1.0.0
result: passed
- **Rung:** 3
- **Target:** headless
- **Expected:** a public declaration without explicit visibility fails compile in core, providers and keystore; Metalava `apiDump` emits `api.txt` for all three modules; no `api.txt` in the real tree.
- **Arranged (seeded):** plants inside an isolated copy of the tree (script-managed).
- **Did (drove):** `scripts/verify-api-dump.sh` -> `API DUMP PROOF OK`. Explicit-API red path exercised by the "public without modifier" plants (core, providers, keystore) in the SC3 run, all `ok`. Checked the real tree: `ls */api.txt` -> no such file; `git ls-files | grep -c api.txt` -> 0.
- **Observed:** empty-surface dump produced `core/api.txt`, `providers/api.txt`, `keystore/api.txt` (1 line each) in the copy; planted public class appears and `apiCheck` green; additive change stays green; removal goes red. Real tree untouched, no stray dump armed the compat gate.
- **Evidence:** script transcript (tail) and `.planning/phases/01-scaffold-publishing-proof/evidence/self-uat-negative-controls.txt`.

### 5. SC5: `check` runs the harnesses: fake-provider pipeline test with zero network; OkHttp matrix 4.12.0 / 5.2.1 / 5.5.0 with per-leg runtime guard; graphify-out/ and A10 fixture path gitignored
result: passed
- **Rung:** 3
- **Target:** headless
- **Expected:** harness tests run under `check`; each matrix leg proves the OkHttp runtime version it claims; the ignore rules hold.
- **Arranged (seeded):** none.
- **Did (drove):** `./gradlew :providers:test :providers:testOkhttp521 :providers:testOkhttp550 :core:test --rerun-tasks -i`; parsed JUnit XML; `git check-ignore -v graphify-out sample/src/debug/assets/sb-a10-fixture.json`; `git ls-files graphify-out | wc -l`; read `ScriptedHarnessTest.kt`.
- **Observed:**
  - Printed `OKHTTP_RUNTIME=4.12.0 expected=4.12.0` (okhttp-4.12.0.jar), `OKHTTP_RUNTIME=5.2.1 expected=5.2.1` (okhttp-jvm-5.2.1.jar), `OKHTTP_RUNTIME=5.5.0 expected=5.5.0` (okhttp-jvm-5.5.0.jar); each leg 2 tests, 0 failures. The SC3 negative-control run also showed each leg going red on a wrong expected version.
  - `:core` ScriptedHarnessTest 7 tests, 0 failures, including `standInPipelineWalksScriptedTiersWithNoNetwork` (runs under `NoNetworkGuard.during { }`) and `noHttpStackOnCoreClasspath`.
  - `.gitignore:51 /graphify-out/` and `.gitignore:48 sb-a10-fixture*.json` match; 0 tracked files under graphify-out.
  - `scripts/verify-repo-hygiene.sh` -> `HYGIENE OK`.
  - Scope-reading caveat (audited, accepted): the literal "fake provider" is realised as generic unpublished primitives (ScriptedResponses, RecordingSink, NoNetworkGuard) driving a test-local stand-in pipeline, because no provider or pipeline type exists until Phases 2 and 3 and D-09 forbids public placeholder types. This is the orchestrator-accepted override already recorded in `01-VERIFICATION.md` and `01-05-PLAN.md`; `FakeAiProvider` is owed by Phase 3. Not a defect in Phase 1, but Phase 3 must deliver it.
- **Evidence:** terminal transcript of the commands above; `.planning/phases/01-scaffold-publishing-proof/evidence/self-uat-negative-controls.txt` for the guard-goes-red legs.

## Audit of the prior verdict

`01-VERIFICATION.md` claims were re-observed and held on every point; no contradiction found. One refinement: the bytecode sweep is over one marker class per module, so "every published class is major 55" is currently true but thin; it becomes meaningful as later phases add classes, and the `verifyBytecodeLevel` gate (fails on zero classes, opens the AAR) is what keeps it honest.

## Residuals and notes
- No target restore needed (no device). All plants were removed; working tree source paths clean.
- No git tags, pushes or commits of unrelated dirty files were made.
- Nothing is physical or device-hardware; no `.device-verify-pending.json` was written.
