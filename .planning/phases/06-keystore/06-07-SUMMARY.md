---
phase: 06-keystore
plan: 07
subsystem: keystore
tags: [device-guard, instrumented-test, androidkeystore, phase-gate]
requires: [06-06]
provides:
  - scripts/run-keystore-instrumented.sh, the TESTER-only guarded runner
  - scripts/verify-keystore-device-guard.sh, offline fake-adb proof of every refusal path
  - evidence/keystore-surface-review.txt, the :keystore public surface and cause codes
  - evidence/keystore-instrumented-run.txt, the TESTER run record
affects: []
tech-stack:
  added: []
  patterns: [fake-adb guard proof, fd-9 lock closed for adb children, runner copy in a throwaway repo skeleton]
key-files:
  created:
    - scripts/run-keystore-instrumented.sh
    - scripts/verify-keystore-device-guard.sh
    - .planning/phases/06-keystore/evidence/keystore-surface-review.txt
    - .planning/phases/06-keystore/evidence/keystore-instrumented-run.txt
  modified: []
key-decisions:
  - "The guard clears BASH_ENV for the runner: on this host ~/.config/test-device.env re-exports ANDROID_SERIAL (the TESTER) into every non-interactive bash, which silently overwrote the foreign-serial scenario's value"
  - "The guard runs a copy of the runner in a throwaway repo skeleton whose gradlew only records a marker, so 'no scenario reaches the Gradle build' is asserted, not assumed"
requirements-completed: [KEY-02, KEY-03, KEY-04]
status: complete
plan_head_before: 8d13043f7f45262b5f8ca9df09705eba16764967
commits: 3
actuals:
  tokens: 30000
  tasks: 3
  commits: 3
duration: 45m
completed: 2026-10-01
---

SC4 device half CLOSED: TESTER PASS (7 tests)

# Phase 6 Plan 7: Guarded TESTER runner, offline phase gate, and the device leg Summary

A runner that can only ever reach the TESTER, with every refusal path proven offline; the whole offline phase gate green; the `:keystore` surface and cause codes recorded; and `KeystoreDeviceTest` passed on the real AndroidKeyStore of the TESTER.

## Tasks

| Task | Name | Commit |
| ---- | ---- | ------ |
| 1 | Tracer: guarded runner plus offline fake-adb guard proof | 1fb66c0 |
| 2 | Offline phase gate; :keystore public surface and cause codes recorded | 16c83fb |
| 3 | TESTER run of the instrumented class (the only device task) | 32dd034 |

## Gate command results (all run one at a time, offline)

- `./gradlew check --offline -q`: exit 0 (whole project).
- `scripts/verify-repo-hygiene.sh`: `HYGIENE OK` (run before and after the surface review; `git ls-files -co --exclude-standard -- '*api.txt'` prints nothing).
- `scripts/verify-negative-controls.sh`: `negative-control failures: 0`.
- `scripts/verify-api-dump.sh`: `API DUMP PROOF OK (real tree untouched; copy removed on exit)` (core 1522 lines, providers 121, keystore 53).
- `scripts/review-api-surface.sh --expect-sealed-complete`: `API SURFACE OK sealed=AssistantPart,CommandOutcome,GateDecision,Message,RunTermination,StrategyOutcome,ToolStep classes=161`.
- `scripts/verify-keystore-device-guard.sh`: `DEVICE GUARD OK` (run in Task 1, in Task 2 and again after the device run).
- `git log 8d13043..HEAD -- keystore/ core/ providers/ build.gradle.kts gradle/` prints nothing: no source, test or build file changed.

## Device attempts

One attempt, through `scripts/run-keystore-instrumented.sh` with no arguments. Exit 0.
`target=R5CT10XNKQN model=SM-S908U sdk=35`; foreground was the yahirandroidtaste harness explorer (idle). `OK (7 tests)` in 0.905 s, `test package removed`, final line `KEYSTORE_INSTRUMENTED: PASS tests=7 target=R5CT10XNKQN model=SM-S908U sdk=35`. Only the TESTER's USB serial was addressed; no other device was touched, no airplane mode, no reboot, no settings change. Record: `.planning/phases/06-keystore/evidence/keystore-instrumented-run.txt`.

## D-10: the :keystore public surface (for the orchestrator)

Recorded verbatim in `evidence/keystore-surface-review.txt` (isolated-copy Metalava dump, not an api.txt). It shows both `ApiKeyStore` constructors over `androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>`, the four `KeyState` leaves (NotConfigured, Ready, KeyMissing, Unreadable), `KeySlot` and `KeystoreCredentialSource`, and no internal type.

## Cause codes for the orchestrator's confirmation (recorded, not blocking)

`key_missing`, `keystore_unavailable`, `decrypt_failed`, `stored_value_malformed`, `storage_unreadable`, with one-line meanings under "Cause codes (pending orchestrator confirmation)" in the surface review.

## Deviations from Plan

None to the plan's behavior. One implementation note: the plan's foreign-ANDROID_SERIAL scenario could not be driven naively, because `BASH_ENV=/home/yahir/.config/test-device.env` re-exports `ANDROID_SERIAL=100.118.21.106:1496` into every non-interactive bash. The guard therefore runs the runner under `env -u BASH_ENV`. (Rule 3: blocking issue, fixed inline in the guard.) Real runs are unaffected: that value is one of the two allowed TESTER serials.

Observation: the runner prints per-test dots only (plain `am instrument -w`, as the plan specifies), so the evidence lists the seven `@Test` method names from the source alongside the `OK (7 tests)` line rather than per-test results from the device.

## Deleted files

None.

## Self-Check: PASSED

- FOUND: scripts/run-keystore-instrumented.sh and scripts/verify-keystore-device-guard.sh (mode 100755), both evidence files
- FOUND commits: 1fb66c0, 16c83fb, 32dd034
