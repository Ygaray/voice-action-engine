---
phase: 06-keystore
plan: 02
subsystem: keystore
tags: [aes-gcm, datastore, slot-table-validation, atomic-writes]
requires: [06-01]
provides:
  - ApiKeyStore.delete(provider) as one DataStore update, device key left in place
  - construction-time validation of KeySlot rows and of the whole slot table
  - RecordingDataStore, sbSlots and ctSlots test support
  - proofs of trim, blank refusal, IV freshness, single-update writes, serialized writes and failure-harmless writes
affects: [06-03, 06-04, 06-05, 06-06, 06-07]
tech-stack:
  added: []
  patterns: [private top-level validation function with require only, counting DataStore decorator for atomicity proofs]
key-files:
  created:
    - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/KeySlotValidationTest.kt
    - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/AesGcmTest.kt
    - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/ApiKeyStoreAtomicityTest.kt
  modified:
    - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/ApiKeyStore.kt
    - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/KeySlot.kt
    - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/KeystoreTestSupport.kt
    - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/ApiKeyStoreTest.kt
key-decisions:
  - "Table validation lives in a private top-level function indexValidated (plus requireDistinct) in ApiKeyStore.kt, called from the slotsByProvider initializer, so it uses require only and stays inside detekt's ThrowsCount and ReturnCount defaults"
  - "Task 3 needed no main-source change: the save order and mutex from the tracer already satisfied every behavior"
requirements-completed: [KEY-01, KEY-02]
status: complete
plan_head_before: f00d5f189faa035dad2917df9113a23c8743dd05
commits: 3
actuals:
  tokens: 30000
  tasks: 3
  commits: 3
duration: 30m
completed: 2026-10-01
---

# Phase 6 Plan 2: Full per-provider store Summary

A CalTracker-shaped three-provider store now saves, replaces and deletes each provider's key independently, a slot table that could alias two providers onto one key cannot be built, and every write is trimmed, IV-fresh, one atomic update, serialized and side-effect free on failure.

## Tasks

| Task | Name | Commit |
| ---- | ---- | ------ |
| 1 | Tracer: three-provider save, replace and delete with unrelated prefs untouched | 603a86e |
| 2 | Slot table validated at construction | 724ff67 |
| 3 | Writes trimmed, atomic, serialized, IV-fresh and harmless on failure | 0df8a37 |

## What was built

- `delete(provider)`: unmapped provider throws `IllegalArgumentException` naming the provider id; otherwise under `writeMutex` one `dataStore.edit` removes ciphertext and IV. It never touches the device keystore; deleting an absent pair is a no-op.
- `KeySlot` `init` refuses a blank alias, ciphertext key or iv key (each message names the field) and a ciphertext key equal to the iv key (message names that pref key).
- Table validation ended up in a private top-level function `indexValidated` in `ApiKeyStore.kt` (not in an `init` block): non-empty table, unique providers, unique aliases, every ct and iv name unique across all rows. The defensive `toList()` copy is taken before validation. Messages name the provider, alias or pref key only.
- Tests: `KeySlotValidationTest` (14 methods, one per rejected shape plus valid SB and CT tables, caller-list mutation, equality, toString), `AesGcmTest` (5), `ApiKeyStoreAtomicityTest` (8), plus the three-provider test in `ApiKeyStoreTest`. `RecordingDataStore` counts `updateData` calls; `sbSlots()` and `ctSlots()` carry the legacy aliases and pref names.

## Task 3 main-source change

None. The existing save order (slot lookup, trim, non-empty check, lock, seal on the IO dispatcher, one edit) satisfied every behavior unchanged.

## Deviations from Plan

- [Rule 1 - Test bug] The failed-lookup test first asserted the thrown exception was the same instance as the injected one. Coroutine stack-trace recovery returns a copy when an exception crosses the dispatcher boundary, so the test now compares type and message. No main-source impact.
- [Rule 3 - Tooling] The tests catch `IllegalArgumentException` and `UnrecoverableKeyException` through two small specific-type helpers instead of `assertThrows` with `runBlocking` or a generic catch, because a suppression annotation is prohibited and `ProviderId` is a value class (a vararg of it does not compile, so test helpers take a list).
- The plan-commit ledger file under the git dir could not be written (shell policy refused the combined command); `plan_head_before` is the known base commit f00d5f1 from the dispatch prompt, and `commits` was counted from it.

## Verification

- `./gradlew :keystore:check --offline -q` green (detekt, banned-construct scanner, explicit API, bytecode level, module graph, no-DI, datastore-api, no-DataStore-creation, lint, androidTest assemble, unit tests).
- Tests re-run clean (`cleanTestDebugUnitTest` then `testDebugUnitTest`) with 0 failures.
- No planning ids in `keystore/src` or `keystore/build.gradle.kts`; `git log f00d5f1..HEAD -- core/ providers/` is empty; no api.txt, tag, STATE.md or ROADMAP.md change.
- No files deleted.

## Self-Check: PASSED

KeySlotValidationTest.kt, AesGcmTest.kt and ApiKeyStoreAtomicityTest.kt exist; commits 603a86e, 724ff67 and 0df8a37 are present.
