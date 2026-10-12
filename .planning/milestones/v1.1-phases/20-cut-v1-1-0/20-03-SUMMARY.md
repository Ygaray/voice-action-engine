---
phase: 20-cut-v1-1-0
plan: 03
subsystem: sample
tags: [rt-07, key-fingerprint, sha-256, gate-1-no-echo, sample]

requires:
  - phase: 20-02
    provides: final api.txt baselines (untouched here)
provides:
  - KeyUx.fingerprint (java.security.MessageDigest SHA-256, first six lowercase hex digits)
  - defaulted KeyVault.fingerprint seam, ApiKeyStoreVault implementation through KeystoreCredentialSource
  - KeyUx.label(state, fingerprint) showing "Ready - fp <6 hex>"; no key character on screen
  - UiTags.neverEchoed and its test; one runbook bullet in 19-GATE1-RUNBOOK.md
affects: [20-09]

tech-stack:
  added: []
  patterns:
    - "screen-only derivative via a defaulted suspend seam method (default null) so existing fake vaults keep compiling"

key-files:
  created: []
  modified:
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/keys/KeyVault.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/ui/HeaderText.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/ui/UiTags.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/SampleViewModel.kt
    - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/KeyVaultTest.kt
    - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/SampleViewModelTest.kt
    - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/UiTagsTest.kt
    - .planning/phases/19-sample-gate-1-docs/19-GATE1-RUNBOOK.md

key-decisions:
  - "Fingerprint capped at six hex digits (24 bits), screen only; KeyState.Ready's key-tail property is not read by the sample and the :keystore API is untouched"
  - "MessageDigest is referenced fully qualified (no import) so the acceptance gate grep -c MessageDigest prints exactly 1"

requirements-completed: []  # VER-07 (cut v1.1.0) spans all 12 plans; not complete after 20-03

actuals:
  tokens: 6000
  tasks: 2
  commits: 2
plan_head_before: e6c98e1cc1209c31aa650cccffaa43017c62e148

duration: n/a
completed: 2026-10-07
status: complete
---

# Phase 20 Plan 03: Sample key fingerprint (RT-07) Summary

**The :sample key row now shows "Ready - fp ba7816"-style SHA-256 fingerprints instead of the last four key characters, and the Gate-1 no-echo contract for key_state_* is a tested tag list plus a runbook line.**

## What was done

- Task 1 (tracer, a0b5f25): `KeyUx.fingerprint` (SHA-256 via `java.security.MessageDigest`, first 3 bytes as 6 lowercase hex). `KeyVault.fingerprint` is a defaulted `suspend` method (null), so `MemoryVault`, `FakeVault` and every other fake still compile and show plain "Ready". `ApiKeyStoreVault` overrides it through `KeystoreCredentialSource(store).credential(provider)` and keeps the key only for the call. `KeyUx.label(state, fingerprint)` never reads the key-tail property. `HeaderText.keyRow` and `SampleViewModel.refreshKeys` pass the fingerprint (asked only when the state is Ready). The KDocs on `KeyUx.label` and the `KeyView.toString` override were reworded.
- Task 2 (9d4a5b0): `UiTags.neverEchoed(providers)` returns the key_state_ tags; `UiTagsTest.neverEchoedListsExactlyTheKeyStateTags` pins it; the Phase 19 runbook gained one bullet (stating it was added on purpose by RT-07) right after "Never read or print a key", plus the sentence that the runbook reads only the tags it names.

## The three flipped assertions

1. `KeyVaultTest.stateLabelsAreLoudAndNeverShowTheKey`: was "label contains WXYZ"; now `label(Ready("WXYZ")) == "Ready"` and `label(Ready("WXYZ"), "ba7816")` contains `ba7816` and not `WXYZ`. Added `fingerprintIsTheFirstSixHexDigitsOfSha256` (known vector `abc` -> `ba7816`, six lowercase hex, distinct keys differ).
2. `SampleViewModelTest.savingAKeyNeverLogsIt` (the single RT-07 no-echo test): the row is exactly `Ready - fp <fingerprint>`, contains neither `1234` nor `dummy`; no evidence line, rig sink line or `UiState.toString()` contains the key, its tail or the fingerprint. `CountingVault` overrides `fingerprint`.
3. `SampleViewModelTest.savingTheTypedFieldUsesAndClearsIt`: was "row ends with abcd"; now the row equals `Ready - fp <fingerprint>` and does not contain `abcd`.

## Verification

- Task 1 command (KeyVaultTest, SampleViewModelTest, TestKeyImporterTest, EvidenceLineTest, UiTagsTest): exit 0.
- `:sample:check` (unit tests + lint): exit 0 after Task 2.
- `scripts/verify-sample-device-guard.sh`: `SAMPLE DEVICE GUARD OK scenarios=43`.
- `grep -c MessageDigest KeyVault.kt` = 1; no `last4`, `Ready - ends in` or `last four characters` in sample main; no change under keystore/ or any api.txt in this plan (`git diff HEAD -- keystore` was empty before and after my commits).
- Device: none (JVM-only per the orchestrator ruling; delta belongs to 20-09 / waiver W03). No stage marker written.

## Deviations from Plan

**1. [Rule 3 - stale verify guard] `git diff --quiet 090fd8ec76 HEAD -- keystore` exits 1.** The only difference is `keystore/api.txt` (+9 lines), committed by plan 20-02 (7066836, the final api baseline). It is not from this plan. The intent (this plan changes nothing under keystore/) was checked against the plan's start commit instead: `git diff --quiet e6c98e1 HEAD -- keystore` exits 0.

**2. [Rule 1 - acceptance literal] MessageDigest count.** The first draft used an import plus a call (count 2); changed to the fully qualified call so the acceptance grep prints 1.

No other deviations. No auth gates.

## Host readings (R2, outside a quiet window)

| Run | MemAvailable kB | SwapTotal kB | SwapFree kB | swap check |
|-----|-----------------|--------------|-------------|-----------|
| Task 1 tests | 11118696 | 2097148 | 132 | exit 1 (WARNING, proceeded) |
| Task 2 `:sample:check` | 11398868 | 2097148 | 2092 | exit 1 (WARNING, proceeded) |

Swap was nearly full (about 0% free) on both runs; MemAvailable was well above the 5 GiB floor, no earlyoom kill, no retry needed, no gradle wrapper process was running. The master may want to ask the operator for a swap reset before the quiet windows (plans 20-07 to 20-11).

## Self-Check: PASSED

- Files modified exist (8 listed above); commits a0b5f25 and 9d4a5b0 exist; `git rev-list --count e6c98e1..HEAD` = 2 before this docs commit; `git status --porcelain -- . ':!.planning' ':!graphify-out' ':!.gsd'` prints nothing.
