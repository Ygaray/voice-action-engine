---
phase: 12-wave-1-seams-w04-fix
plan: 04
subsystem: keystore
tags: [keystore, key-access, opt-in, requires-opt-in, additive-api, negative-compile]
requires: []
provides:
  - "@DelicateKeyAccess, a RequiresOptIn(ERROR) marker with BINARY retention"
  - "KeyAccess as a public plain interface (two members), marked @DelicateKeyAccess"
  - "ApiKeyStore(dataStore, slots, keyAccess), the one new public constructor, marked @DelicateKeyAccess"
  - "scripts/verify-keyaccess-opt-in.sh, a cross-module negative-compile proof with a positive control, run by verify-negative-controls.sh Part 4"
affects: [12-07]
tech-stack:
  added: []
  patterns: ["opt-in gate proven from a different module (:sample) by two red plants and one green control"]
key-files:
  created:
    - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/DelicateKeyAccess.kt
    - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/ApiKeyStoreKeyAccessTest.kt
    - scripts/verify-keyaccess-opt-in.sh
  modified:
    - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/KeyAccess.kt
    - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/ApiKeyStore.kt
    - keystore/build.gradle.kts
    - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/KeystoreApiShapeTest.kt
    - scripts/verify-negative-controls.sh
    - .planning/REQUIREMENTS.md
key-decisions:
  - "Module-wide opt-in used the kotlin { compilerOptions { optIn.add(...) } } form; AGP 9 built-in Kotlin accepted it (research A6 confirmed), so no -opt-in free arg fallback"
  - "The cross-module proof greps for the marker's own message on the plant file, not the text 'needs opt-in' (research A5 was wrong: a RequiresOptIn message replaces the default text)"
requirements-completed: [SEAM-07]
status: complete
plan_head_before: d2da22c88c33fc5b926a943551231f20e3b39f56
commits: 2
metrics:
  completed: 2026-10-05
actuals:
  tokens: 9000
  tasks: 2
  commits: 2
---

# Phase 12 Plan 04: Opt-in KeyAccess seam Summary

The keystore's key-custody seam is open to app unit tests behind a compile-time opt-in: `KeyAccess` is a public plain interface and `ApiKeyStore(dataStore, slots, keyAccess)` is the one new constructor, both under `@DelicateKeyAccess` (`RequiresOptIn` level ERROR, BINARY retention). Strictly additive: `keystore/api.txt` is untouched and the v1.0.1 constructors are unchanged.

## What changed

- **Marker and seam (Task 1, tracer)**: `DelicateKeyAccess.kt` is new. `KeyAccess` went from internal to public with both members public, KDoc extended with the tests-only note and the read-never-creates contract. `ApiKeyStore` gained the marked 3-argument constructor delegating to the internal 4-argument primary with `Dispatchers.IO`. `keystore/build.gradle.kts` opts the keystore's own compilations in, so `AndroidKeyStoreKeyAccess` and `SecretReader` needed no edit.
- **JVM round trip**: `ApiKeyStoreKeyAccessTest` drives the production cipher and DataStore through the public constructor with `SoftwareKeyAccess`: save then read is `Ready("wxyz")`, reading an absent provider leaves `getOrCreateCalls` and `createdKeys` at 0, and a fresh store over the same DataStore and fake reads the key back.
- **Cross-module proof (Task 2)**: `scripts/verify-keyaccess-opt-in.sh` plants code in `:sample`: (a) the constructor without `@OptIn` is red, (b) implementing `KeyAccess` without `@OptIn` is red, (c) the same code as (a) with `@OptIn` compiles. It ran 3/3 ok, `keyaccess opt-in failures: 0`, and leaves nothing behind (trap cleanup; `git status --porcelain sample/src/main` empty). Mode is 100755. `verify-negative-controls.sh` runs it as Part 4.
- **Shape coverage**: `KeystoreApiShapeTest` sweeps `KeyAccess` and `DelicateKeyAccess`, asserts `KeyAccess` is a public interface with exactly two members, and asserts the three public constructors are present.
- **REQUIREMENTS.md**: SEAM-07 now says plain interface with two members, both gated by the opt-in marker.

## Deviations from Plan

**1. [Rule 1 - Bug in plan assumption] Opt-in error text (research A5)**
- Found during: Task 2, first run of the proof script.
- Issue: the plan expected the compiler error to contain "needs opt-in". With a custom `message` on `@RequiresOptIn`, Kotlin prints only that message, so both red plants "went red for the wrong reason" under the original marker.
- Fix: the script now greps for the marker's own message and requires it on the `ZzOptInPlant.kt` line, so a red from any other cause still fails the proof.
- Files modified: `scripts/verify-keyaccess-opt-in.sh`. Verification: script 3 ok, 0 failures.

**2. [Rule 1 - Test assumption] Constructor shape test is not exclusive**
- Found during: Task 2.
- Issue: Kotlin `internal` constructors are public in bytecode, so reflection sees the 4-argument primary as a fourth public constructor.
- Fix: the test asserts the three required shapes are present (containsAll), not that they are the only ones. The source-level API is guarded by `apiCheck`.
- Files modified: `KeystoreApiShapeTest.kt`.

Also a trivial first-compile fix in the new test (`KeyState.NotConfigured()` is a class, not an object). **Total deviations:** 2 auto-fixed. **Impact:** none on scope.

## Verification

- `./gradlew --offline -q :keystore:testDebugUnitTest :keystore:detekt :keystore:scanBannedConstructs :keystore:apiCheck`: exit 0, all keystore tests pass.
- `:keystore:compileReleaseKotlin` and the targeted Task 1 tests pass.
- `git diff --exit-code v1.0.1 -- keystore/api.txt`: exit 0.
- All Task 1 and Task 2 acceptance criteria re-run and pass (`git ls-files -s` shows 100755).

Not run: the full `scripts/verify-negative-controls.sh` (several minutes of Gradle on a memory-tight host); only its new Part 4 target script was run. Behavioral/device verification is out of scope for this plan.

## Threat model

T-12-09 mitigated (ERROR level, BINARY retention, red/red/green cross-module proof wired into the phase gate). T-12-10: no new toString or logging; test key is a fixed non-secret string. T-12-11: opt-in flag is only in the keystore's own compilation; `:sample` has none, which the proof relies on.

## Self-Check: PASSED

Created files exist (DelicateKeyAccess.kt, ApiKeyStoreKeyAccessTest.kt, verify-keyaccess-opt-in.sh); commits df3d039 and 4d8327e exist.
