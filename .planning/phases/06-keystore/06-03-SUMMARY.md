---
phase: 06-keystore
plan: 03
subsystem: keystore
tags: [read-path, unreadable-causes, observe, cancellation, strict-base64]
requires: [06-02]
provides:
  - SecretReader, the one lookup-only decode and decrypt path behind read, observe and readSecret
  - KeystoreCauses, the single home of the five cause codes
  - ApiKeyStore.observe(provider): Flow<KeyState>
  - ThrowingDataStore and NeverEmittingDataStore test fakes
affects: [06-04, 06-05, 06-06, 06-07]
tech-stack:
  added: []
  patterns: [Stage carrier so a multi-step read has one exit per step and no extra return statements, catch chains opening with a CancellationException rethrow, catch parameters named ignored]
key-files:
  created:
    - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/SecretReader.kt
    - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/KeystoreCauses.kt
    - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/ReadNeverCreatesKeyTest.kt
    - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/UnreadableMappingTest.kt
    - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/KeyStateTest.kt
    - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/ApiKeyStoreObserveTest.kt
  modified:
    - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/ApiKeyStore.kt
    - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/KeystoreTestSupport.kt
key-decisions:
  - "Reads are modelled as a chain of private Stage values (value or finished read), because detekt's ReturnCount limit of 2 ruled out an early-return per failure and no suppression is allowed"
  - "A torn pair reads NotConfigured, a DataStore RuntimeException on read maps to storage_unreadable, and observe rethrows any non-IOException (RESEARCH defaults)"
requirements-completed: [KEY-03]
status: complete
plan_head_before: d19ec95c1b6e73e250aeb7b4eb9cf6151facfb93
commits: 3
actuals:
  tokens: 45000
  tasks: 3
  commits: 3
duration: 35m
completed: 2026-10-01
---

# Phase 6 Plan 3: Typed reads and observe Summary

Every read now answers with exactly one of NotConfigured, Ready(last4), KeyMissing or Unreadable(cause): a restored backup whose device key is gone reads KeyMissing without any key being created or anything written, every other failure maps to a stable cause code, cancellation always propagates, and `observe` streams the same states to the Settings UI.

## Tasks

| Task | Name | Commit |
| ---- | ---- | ------ |
| 1 | Tracer: restored backup reads KeyMissing, zero key creation and zero writes | 483f0e2 |
| 2 | Every failure maps to one stable cause code, cancellation propagates | 2bef3f1 |
| 3 | KeyState semantics and the observe stream | ca5be9f |

## Cause codes for orchestrator confirmation

These become public contract at the v1.0.0 tag (apps map them to UI copy). Recorded, not blocking.

- `key_missing`: the device key that protected the stored value is gone (backup restore, wiped key store). Reported as `KeyState.KeyMissing`; also exposed as `KeystoreCauses.keyMissingLookup` (a `CredentialLookup.Unreadable`) for the 06-05 adapter.
- `keystore_unavailable`: the key lookup threw (GeneralSecurityException or RuntimeException); the stored value may be fine.
- `decrypt_failed`: the cipher rejected the pair (bit-flipped ciphertext, wrong key).
- `stored_value_malformed`: strict Base64 failed in either half, the IV is not 12 bytes, the ciphertext is under 16 bytes, or the decrypted value is blank.
- `storage_unreadable`: the DataStore could not be read (IOException including CorruptionException, or a RuntimeException).

## Final step order in SecretReader.open

1. Pair lookup by the slot's two preference names; either half absent gives NotConfigured.
2. `keyAccess.existingKey(alias)`: null gives KeyMissing; a thrown GeneralSecurityException or RuntimeException gives keystore_unavailable (CancellationException rethrown first). The class never calls the creating method.
3. Strict `java.util.Base64` decode of both halves; IllegalArgumentException, an IV that is not 12 bytes, or a ciphertext under 16 bytes gives stored_value_malformed.
4. `AesGcm.open`: GeneralSecurityException or RuntimeException gives decrypt_failed (CancellationException rethrown first).
5. UTF-8 decode; a blank value gives stored_value_malformed; otherwise Ready with the last-four rule (empty for 0 to 4 characters).

The DataStore read itself is in `ApiKeyStore.storedPreferences()`: CancellationException rethrown, then IOException and RuntimeException both give storage_unreadable. `readSecret` runs the reader on the IO dispatcher.

## KeyState changes

None. 06-01's `KeyState` already satisfied every KeyStateTest behavior.

## Deviations from Plan

- [Rule 1 - Test bug] The first KeyStateTest created a `TempPreferences` per call, which collides on the temp file name; it now builds one store lazily per test.
- [Rule 3 - Tooling] The plan's "one small private function per step" was not enough for detekt's ReturnCount (3 returns in `open`), so the steps return a private `Stage` carrier and `open` chains them with `then`/`finish`. No suppression was needed.
- The plan-commit ledger file was not written (shell policy refuses combined commands in this worktree); `plan_head_before` is the dispatch base d19ec95 and `commits` was counted with `git rev-list --count d19ec95..HEAD` before this SUMMARY commit.

## Verification

- `./gradlew :keystore:check --offline -q` green (detekt, banned-construct scanner, explicit API, bytecode level, module graph, no-DI, datastore-api, no-DataStore-creation, lint, androidTest assemble, unit tests).
- The five cause literals appear in exactly one main file, KeystoreCauses.kt; no `catch` of Exception or Throwable, no suppression annotation and no `getOrCreateKey` in SecretReader.kt; no planning ids in `keystore/src`.
- `git log d19ec95..HEAD -- core/ providers/` is empty; no api.txt, tag, STATE.md or ROADMAP.md change. No files deleted.

## Self-Check: PASSED

SecretReader.kt, KeystoreCauses.kt, ReadNeverCreatesKeyTest.kt, UnreadableMappingTest.kt, KeyStateTest.kt and ApiKeyStoreObserveTest.kt exist; commits 483f0e2, 2bef3f1 and ca5be9f are present.
