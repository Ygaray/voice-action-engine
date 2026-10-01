---
phase: 06-keystore
plan: 05
subsystem: keystore
tags: [credential-source, pipeline, canary, api-shape]
requires: [06-04]
provides:
  - KeystoreCredentialSource, the public adapter from ApiKeyStore to the existing CredentialSource seam
  - KeystorePipelineTest, KeystoreCredentialSourceTest, the key reaching the provider and every state mapped to the right lookup
  - KeystoreCanaryTest, the no-secret-in-any-sink sweep
  - KeystoreApiShapeTest, the house public-shape lint over compiled :keystore classes
affects: [06-06, 06-07]
tech-stack:
  added: []
  patterns: [adapter relies on readSecret's total mapping instead of a catch, mirrored shape lint with self-proving synthetic classes]
key-files:
  created:
    - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/KeystoreCredentialSource.kt
    - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/KeystorePipelineTest.kt
    - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/KeystoreCredentialSourceTest.kt
    - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/KeystoreCanaryTest.kt
    - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/KeystoreApiShapeTest.kt
  modified: []
key-decisions:
  - "No main-source change was forced by the canary or shape tests: every sink was already clean and every shape rule already quiet"
  - "The three pipeline tests (happy path, lost key, never stored) all landed with the tracer commit; the task-2 commit adds the adapter-level matrix"
requirements-completed: [KEY-04]
status: complete
plan_head_before: f86b0c4b1ea398c63690050e950b6c846e18a644
commits: 3
actuals:
  tokens: 7000
  tasks: 3
  commits: 3
duration: 25m
completed: 2026-10-01
---

# Phase 6 Plan 5: KeystoreCredentialSource Summary

`KeystoreCredentialSource(store)` plugs the encrypted store into the real `commandPipeline` as its `credentials`: a saved (trimmed) key reaches `FakeAiProvider` as a `Credential` for the asked provider, a lost device key ends as `FailureReason.CredentialUnreadable(ANTHROPIC, "key_missing")` (trace `credential_unreadable`, never `credential_source_error`), and a never-stored key ends as `FailureReason.NotConfigured(ANTHROPIC)`.

## Tasks

| Task | Name | Commit |
| ---- | ---- | ------ |
| 1 | Tracer: saved key reaches the provider through the real pipeline | afa8e54 |
| 2 | Every KeyState maps to the right lookup; adapter never throws | 0c09b71 |
| 3 | Canary sweep and public-shape lint | 7448378 |

## Mapping

| Store answer | Lookup |
| ------------ | ------ |
| Ready with plaintext | Present(Credential(asked provider, plaintext)) |
| Ready without plaintext (kept total) | Unreadable(stored_value_malformed) |
| KeyMissing | Unreadable(key_missing) |
| Unreadable(c) | Unreadable(c) |
| NotConfigured, unmapped provider, anything newer | Missing |

A blank decrypted value is already mapped by `SecretReader` to `stored_value_malformed`, so `Credential`'s constructor can never throw inside the adapter. No catch exists in the adapter; cancellation passes through `readSecret` untouched (tested over `NeverEmittingDataStore`).

## Measurements

- Shape sweep inspected 37 compiled `:keystore` main classes (floor `MIN_INSPECTED = 10`); no enum, data-shaped class, leaked static field or public default-argument stub; each rule fires on its synthetic class.
- Canary: the key and its last 12 characters are absent from the toString of the four KeyState leaves, KeySlot, ApiKeyStore, KeystoreCredentialSource, the Present lookup and Credential; absent from the message and cause chain of 10 failure paths; absent from the DataStore file bytes (non-empty file).
- No main-source change was forced by the canary or shape tests. `readSecret` and `SecretReader` needed no fix.

## Deviations from Plan

None in behavior. One test-authoring slip fixed before commit: a test opened two stores over the same temp file (DataStore refuses that), reworked to one store per test and a separate test for the unmapped-provider case.

## Verification

- `./gradlew :keystore:testDebugUnitTest` for the four new test classes: green (pipeline 3, source 11, canary 3, shape 7).
- `./gradlew :keystore:detekt :keystore:scanBannedConstructs :keystore:verifyExplicitApiStrict`: green.
- `./gradlew :keystore:check :core:check --offline`: green.
- `git log f86b0c4..HEAD -- core/ providers/` prints nothing. No file deleted. No api.txt, tag or push.

## Self-Check: PASSED

All five created files exist; commits afa8e54, 0c09b71, 7448378 exist on the branch.
