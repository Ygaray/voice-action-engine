---
phase: 01-scaffold-publishing-proof
plan: 05
subsystem: testing
tags: [testfixtures, junit4, coroutines-test, okhttp-matrix, mockwebserver, reflective-guard, gradle]

requires:
  - phase: 01-03
    provides: "detekt wiring incl. src/testFixtures, skip() lines on testFixtures variants, detekt control tasks"
  - phase: 01-04
    provides: "verifyOkHttpCompileFloor, verifyCoreDependencyAllowlist (structural zero-network guarantee)"
provides:
  - "ScriptedResponses<T>, RecordingSink<T>, NoNetworkGuard in :core src/testFixtures (unpublished)"
  - "ScriptedHarnessTest: test-local stand-in pipeline driven through the fixtures with no network"
  - ":core:verifyNoTestFixturesPublished (non-vacuous, attached to check)"
  - ":providers:testOkhttp521 and :providers:testOkhttp550 legs over one 4.12.0-compiled test output, attached to check"
  - "OkHttpVersionGuardTest: reflective per-leg version guard printing OKHTTP_RUNTIME=<actual> expected=<expected> jar=<location>"
  - "Gradle property vaeExpectedOkhttp (negative-control lever for plan 06)"
affects: [01-06, phase-02, phase-03, phase-04]

plan_head_before: 3fafa3981387a7b792585eec33839f488ce06597

actuals:
  tokens: 4340
  tasks: 2
  commits: 2

tech-stack:
  added: []
  patterns:
    - "Generic type-agnostic fixtures in testFixtures; provider-specific fakes are thin layers added in later phases"
    - "Matrix legs are Test tasks reusing the compiled test classesDirs with a swapped runtime configuration (explicit JVM attributes, okhttp + mockwebserver moved together)"
    - "Version guards read constants reflectively, since compile-time constants are inlined"

key-files:
  created:
    - core/src/testFixtures/kotlin/io/github/ygaray/voiceactionengine/core/testing/ScriptedResponses.kt
    - core/src/testFixtures/kotlin/io/github/ygaray/voiceactionengine/core/testing/RecordingSink.kt
    - core/src/testFixtures/kotlin/io/github/ygaray/voiceactionengine/core/testing/NoNetworkGuard.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ScriptedHarnessTest.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/OkHttpVersionGuardTest.kt
  modified:
    - core/build.gradle.kts
    - providers/build.gradle.kts

key-decisions:
  - "NoNetworkGuard.during is a public inline function (so suspend calls work inside the block) backed by a @PublishedApi internal installTripwire(); the selector is restored in finally"
  - "Catch variable in NoNetworkGuard is named 'expected' so detekt's default SwallowedException allowed-name regex applies; no @Suppress and no rule tuning needed"
  - "The 4.12.0 default test task and both legs read expected.okhttp from vaeExpectedOkhttp when present, else from the leg's own version"

requirements-completed: [BLD-09]

coverage:
  - id: D1
    description: "Generic unpublished fixtures (ScriptedResponses, RecordingSink, NoNetworkGuard) drive a stand-in pipeline in a :core test with zero network; exhausted script fails loudly"
    requirement: BLD-09
    verification:
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ScriptedHarnessTest.kt (7 tests, ./gradlew :core:test)"
        status: pass
    human_judgment: false
  - id: D2
    description: "testFixtures are absent from the published POM and module metadata, and the assertion is non-vacuous"
    requirement: BLD-09
    verification:
      - kind: integration
        ref: "./gradlew :core:verifyNoTestFixturesPublished; publishReleasePublicationToMavenLocal into a temp repo shows no test-fixtures file or mention"
        status: pass
    human_judgment: false
  - id: D3
    description: "The same 4.12.0-compiled :providers tests run on OkHttp 4.12.0, 5.2.1 and 5.5.0 inside check, with a reflective guard proving each runtime and a wrong expectation failing the leg"
    requirement: BLD-09
    verification:
      - kind: integration
        ref: "./gradlew :providers:test :providers:testOkhttp521 :providers:testOkhttp550 (three OKHTTP_RUNTIME lines); -PvaeExpectedOkhttp=9.9.9 on testOkhttp521 exits non-zero"
        status: pass
    human_judgment: false

duration: 3min
completed: 2026-09-30
status: complete
---

# Phase 1 Plan 05: Test Harnesses Summary

**Unpublished generic :core test fixtures (ScriptedResponses, RecordingSink, NoNetworkGuard) plus an OkHttp 4.12.0 / 5.2.1 / 5.5.0 matrix in :providers with a reflective per-leg version guard, all inside `./gradlew check`**

## Performance

- **Duration:** ~3 min
- **Started:** 2026-09-30T21:13:34Z
- **Completed:** 2026-09-30T21:16Z
- **Tasks:** 2
- **Files modified:** 7 (5 created, 2 modified)

## Accomplishments
- Three generic fixtures in `:core` `src/testFixtures`, with no provider, pipeline, outcome or command type referenced, so no public placeholder type exists anywhere (`git ls-files core/src/main` lists only `CoreModule.kt`).
- `ScriptedHarnessTest` (7 tests): ordered replies and countdown, loud "script exhausted", snapshot and clear semantics, concurrent recording (8 threads x 500 events, nothing lost), classpath assertion, tripwire install and restore even on throw, and a `runTest` stand-in pipeline asserted by exact event order.
- `verifyNoTestFixturesPublished` fails on "vacuous" when nothing was generated and on "testFixtures leaked" otherwise; a real publish into a temp Maven repo confirmed no `test-fixtures` file and no mention in POM or `.module`.
- Two matrix legs re-running the same 4.12.0-compiled test classes. The guard prints one proof line per leg; observed:
  - 4.12.0 leg: `.../com.squareup.okhttp3/okhttp/4.12.0/.../okhttp-4.12.0.jar`
  - 5.2.1 leg: `.../com.squareup.okhttp3/okhttp-jvm/5.2.1/.../okhttp-jvm-5.2.1.jar`
  - 5.5.0 leg: `.../com.squareup.okhttp3/okhttp-jvm/5.5.0/.../okhttp-jvm-5.5.0.jar`
- Negative control: `-PvaeExpectedOkhttp=9.9.9` on `testOkhttp521` exits non-zero and still prints `OKHTTP_RUNTIME=5.2.1 expected=9.9.9`.
- Full `./gradlew check` green afterwards, including `:providers:verifyOkHttpCompileFloor` and detekt over the testFixtures source set.

## Task Commits

1. **Task 1: :core fixtures, stand-in pipeline harness test, testFixtures-not-published assertion** - `5895b0f` (feat)
2. **Task 2: :providers OkHttp matrix legs, reflective guard, legacy mockwebserver round trip** - `d89b63b` (feat)

**Plan metadata:** recorded in the docs(01-05) commit that follows this file.

## Files Created/Modified
- `core/src/testFixtures/.../core/testing/ScriptedResponses.kt` - ordered scripted replies, synchronized index, `check()`-based exhaustion failure
- `core/src/testFixtures/.../core/testing/RecordingSink.kt` - CopyOnWriteArrayList-backed recorder with immutable snapshots
- `core/src/testFixtures/.../core/testing/NoNetworkGuard.kt` - classpath assertion plus ProxySelector tripwire
- `core/src/test/.../core/ScriptedHarnessTest.kt` - harness tests with a private test-local stand-in pipeline
- `core/build.gradle.kts` - test and testFixtures dependencies, `verifyNoTestFixturesPublished`
- `providers/build.gradle.kts` - test dependencies, matrix legs, `vaeExpectedOkhttp`
- `providers/src/test/.../providers/OkHttpVersionGuardTest.kt` - `runtimeVersionMatchesLeg`, `trivialCallRoundTrips`

## Decisions Made
- `during` is `inline` so suspend pipeline calls can sit inside the guarded block; only `installTripwire()` is `@PublishedApi internal`.
- Detekt default rules needed no tuning and no suppression. The one rule the fixtures would have tripped, `SwallowedException` on the `ClassNotFoundException` pass condition, is satisfied by naming the catch variable `expected` (default allowed-name regex). `MagicNumber` was avoided with named constants in the test; `check()` is used for exhaustion.
- Expectation wiring: default test and each leg use `vaeExpectedOkhttp` if given, else their own version; the override can only cause failures because the guard compares against the real runtime.

## Deviations from Plan

None - plan executed exactly as written. Two small notes, neither a deviation from behavior:
- TDD: test file was written before the fixtures, but the RED compile failure was not run and committed separately; the plan specifies one commit per task over the five named files, which is what was done.
- A build-file comment initially contained the literal text `skip()`, which made `grep -c 'skip()'` print 3 instead of the required 2; the comment was reworded before the commit.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Plan 06 can grep `OKHTTP_RUNTIME=` lines and use `-PvaeExpectedOkhttp` for its negative control against each leg.
- Phases 2 and 3 can layer `RecordingCommitSink` and `FakeAiProvider` on `RecordingSink` / `ScriptedResponses` without any new published module.

## Self-Check: PASSED

- All seven files exist on disk; commits `5895b0f` and `d89b63b` found in `git log`.
- Acceptance criteria re-run: five-plus tests (7) pass; `grep -c 'skip()' core/build.gradle.kts` = 2; no `FakeAiProvider`/`AiProvider` in `core/src`; providers build file has `testOkhttp521`, `testOkhttp550`, `useVersion`, `vaeExpectedOkhttp`, `api(libs.okhttp)` and no `strictly`/`okhttp-bom`; `:providers:check --dry-run` lists both legs; both task commits touch only their named files.
- Plan-level verification: `./gradlew check` BUILD SUCCESSFUL; three matching `OKHTTP_RUNTIME=` lines; wrong expected version fails the leg; published `:core` POM and metadata never mention test-fixtures.

---
*Phase: 01-scaffold-publishing-proof*
*Completed: 2026-09-30*
