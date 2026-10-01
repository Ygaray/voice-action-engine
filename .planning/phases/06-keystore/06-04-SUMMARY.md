---
phase: 06-keystore
plan: 04
subsystem: keystore
tags: [legacy-compat, golden-vector, base64, independent-replica]
requires: [06-03]
provides:
  - LegacyWriters, a test-only independent replica of the SecondBrain and CalTracker writer and reader
  - LegacyCompatJvmTest, both apps' pairs read back and the library's pairs open with the old code
  - GoldenVectorTest, a fixed key, nonce and plaintext pinning the byte layout and the Base64 alphabet
affects: [06-05, 06-06, 06-07]
tech-stack:
  added: []
  patterns: [replica shares no code with the production cipher or reader so compat tests cannot agree with themselves, golden constants never edited to match the library]
key-files:
  created:
    - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/LegacyWriters.kt
    - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/LegacyCompatJvmTest.kt
    - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/GoldenVectorTest.kt
  modified: []
key-decisions:
  - "The reverse-direction test is one test per layout (SB, CT) because TempPreferences allows one DataStore file per test folder"
  - "GOLDEN_CT_B64 carries no secret-scan marker: it is not key-shaped and the marker would push the line past detekt's 120-column limit"
requirements-completed: [KEY-02, KEY-03]
status: complete
plan_head_before: 158c8d6c6ecd03aec1aab84200b7b2bab797ebcb
commits: 2
actuals:
  tokens: 3100
  tasks: 2
  commits: 2
duration: 25m
completed: 2026-10-01
---

# Phase 6 Plan 4: Legacy compatibility and golden vector Summary

SecondBrain's and CalTracker's existing stored pairs, written by an independent replica under the apps' literal aliases and preference names, read back through `ApiKeyStore` unchanged with no key created and no migration; pairs the store writes open with the replica for both layouts; and a fixed golden vector pins the AES/GCM byte layout and standard Base64.

## Tasks

| Task | Name | Commit |
| ---- | ---- | ------ |
| 1 | Tracer: SB-format pair from an independent replica reads back unchanged, SB layout re-verified at source | 66e94cf |
| 2 | CalTracker x3, reverse direction, golden vector, Base64 alphabet | 97d85ac |

## D-11 layout check

SB source re-read this phase (read only). Every observed value equals RESEARCH's table; no finding for the orchestrator.

| Item | SB file:line | Observed in SB | Replica value (`LegacyWriters`) |
| ---- | ------------ | -------------- | ------------------------------- |
| Keystore alias | KeystoreCrypto.kt:98 | `secondbrain_anthropic_api_key_v1` | `SB_ALIAS` in the test, same literal, installed in the software key access |
| Transformation | KeystoreCrypto.kt:100 | `AES/GCM/NoPadding` | `AES/GCM/NoPadding` |
| Tag length | KeystoreCrypto.kt:101 | 128 bits | `GCMParameterSpec(128, iv)` |
| IV source | KeystoreCrypto.kt:49-51 | encrypt-mode init with the key only, `iv = cipher.iv` (12-byte nonce) | encrypt-mode init with the key only, `cipher.iv` |
| Key spec | KeystoreCrypto.kt:73-82 | AES-256, GCM block mode, no padding, encrypt+decrypt purposes, no `setUserAuthenticationRequired` | 32-byte AES `SecretKeySpec` (software stand-in, same algorithm and size) |
| Base64 | AnthropicApiKeyRepository.kt:124-125 | `java.util.Base64.getEncoder()`, ciphertext and IV encoded separately | `java.util.Base64.getEncoder()` for both strings; standard decoder on open |
| Trim and blank rejection | AnthropicApiKeyRepository.kt:116-117 | `rawKey.trim()`, `require(trimmed.isNotEmpty())`, one `setAnthropicApiKeySecret` edit | store trims and rejects blank (covered by 06-02); reverse test saves `"  key-...-rollback  "` and the replica opens the trimmed value |
| Pref key, ciphertext | ThemePreferenceManager.kt:323 | `anthropic_api_key_ct` | `anthropic_api_key_ct` |
| Pref key, IV | ThemePreferenceManager.kt:329 | `anthropic_api_key_iv` | `anthropic_api_key_iv` |

CalTracker values (aliases and pref names) are taken from RESEARCH and the shared `ctSlots()` table; CT's source was not re-read in this plan (the plan scopes the re-read to SB).

## Tests

- `LegacyCompatJvmTest` (5): SB pair reads `Ready("QzT9")` with plaintext intact and zero `getOrCreateKey` calls; three CT provider pairs each read back their own key only; an `mcp_token_ct`/`mcp_token_iv` pair is byte-for-byte unchanged after save, read and delete of all three providers; store-written pairs open with the replica in the SB layout and in the CT layout.
- `GoldenVectorTest` (4): the replica reproduces `GOLDEN_IV_B64` and the 60-character `GOLDEN_CT_B64` exactly; the store reads that pair as `Ready("wxyz")`; the standard encoder gives `+//+`, `+/8=`, `+w==` with no line break; 25 successive saves all store strings matching `^[A-Za-z0-9+/]*={0,2}$`, with no CR/LF, that re-encode to themselves after a decode.

The replica reproduced the golden constants on the first run; neither the constants nor the replica were adjusted.

## Deviations from Plan

None to the plan's intent. Two mechanical adjustments:

1. [Rule 3 - Blocking] The reverse-direction check was first written as one test looping both layouts, which failed because `TempPreferences` creates a fixed-name file once per test folder. Split into one test per layout.
2. [Rule 3 - Blocking] detekt `MaxLineLength` flagged long test lines; shortened by extracting constants and a local, and dropped the secret-scan marker from the non-key-shaped `GOLDEN_CT_B64` line.

## Verification

- `./gradlew :keystore:testDebugUnitTest --tests '*LegacyCompatJvmTest' --tests '*GoldenVectorTest' --offline` green (9 tests).
- `./gradlew :keystore:check --offline` green (detekt over test sources, unit tests, lint, androidTest assemble).
- `LegacyWriters` code lines reference none of AesGcm, Sealed, SecretReader, KeyAccess, KeystoreCauses, ApiKeyStore.
- `git log 158c8d6..HEAD -- keystore/src/main core providers` prints nothing: tests only, no main-source, API, dependency or Gradle change.

## Self-Check: PASSED

Files found: LegacyWriters.kt, LegacyCompatJvmTest.kt, GoldenVectorTest.kt. Commits found: 66e94cf, 97d85ac.
