---
phase: 10-sample-harness-gate-1-docs
fixed_at: 2026-10-01T00:00:00Z
review_path: .planning/phases/10-sample-harness-gate-1-docs/10-REVIEW.md
iteration: 1
findings_in_scope: 8
fixed: 8
skipped: 0
status: resolved
---

# Phase 10: Code Review Fix Report

**Fixed at:** 2026-10-01
**Source review:** .planning/phases/10-sample-harness-gate-1-docs/10-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 8 (CR-01, WR-01..WR-07; IN-01..IN-08 out of scope by instruction)
- Fixed: 8 (WR-02 and WR-03 share one commit because they share one file)
- Skipped: 0

Security-audit mapping: UF-1 = CR-01, UF-3 = WR-07, UF-4 = WR-03.

Verification ran in the main checkout (no worktree, by instruction; the fixer was the only writer). After the last commit:
`./gradlew check --offline -q` exited 0 (all modules, OkHttp matrix legs, detekt zero baseline, scanners, `:sample` unit tests
and lint), `scripts/verify-sample-device-guard.sh` printed `SAMPLE DEVICE GUARD OK scenarios=33` (was 27),
`scripts/verify-docs-coverage.sh` printed `DOC COVERAGE OK checks=23 types=96`, and `scripts/verify-repo-hygiene.sh` printed
`HYGIENE OK`. `git diff 6b97f99` over `core`, `providers` and `keystore` main sources is empty (no public API change, no
`api.txt` change); `README.md`, `INTEGRATION.md`, `API.md` and `ECOSYSTEM.md` are untouched. No tag, no push, no device,
adb or network use. No key-shaped or fixture content was written anywhere (negative controls use `zz_` marker names and
planted key shapes assembled from fragments).

## Fixed Issues

### CR-01: VER-02 evidence lines carry the fixture's tool names (LE-7 violation)

**Files modified:** `sample/.../evidence/EvidenceLine.kt`, `scripts/sample-evidence-filter.sh`,
`scripts/verify-sample-device-guard.sh`, `sample/src/test/resources/evidence-lines.golden.txt`,
`EvidenceLineTest.kt`, `AgenticLegTest.kt`, `CacheVerdictTest.kt`, `LegTestSupport.kt`
**Commit:** a45c116
**Status:** fixed: requires human verification (the live capture itself cannot be exercised offline)
**Applied fix:** Two layers. (1) At the source: `LegId.fixtureBacked` (true for `ver02`, decided from the leg alone so no
caller can forget it) makes `VAE_TURN` carry `tools=redacted tool_count=<n>` and `VAE_OUTCOME` carry
`terminal_tool=redacted|none`; other legs keep their synthetic names and also gain `tool_count`. The `EvidenceLine` KDoc now
says the alphabet is a shape filter, not a vocabulary. (2) In the host filter: a `leg=ver02` line with a `tool=` or
`arg_keys=` field, a `tools=` other than `redacted`, a `terminal_tool=` other than `none|redacted`, or any digest value
(`fixture_sha`, `sha`, `actual`) longer than 8 hex rejects the whole capture (`LEAK SCAN FAIL`, nothing on stdout), like a key
shape. Tests: `AgenticLegTest.noEmittedLineEverNamesAFixtureTool` runs the fixture leg with a fixture whose tool is renamed
`zz_private_find_items` (the model calls it) and asserts no emitted line contains it; `EvidenceLineTest` covers redaction,
the unchanged names on other legs, and the 8-hex digest; the golden file gains a redacted `ver02` turn and a named
`multi_openai` turn; the device-guard verifier gains `filter_rejects_fixture_content` (six planted shapes, exit 1, empty
stdout, plus a positive control for redacted and non-fixture lines), `capture_save_fixture_names` (no evidence file is
written) and `capture_save_ver02_redacted`.

### WR-01: `verify-keys-gone` reports success when adb fails or times out

**Files modified:** `scripts/run-sample-gate1.sh`, `scripts/verify-sample-device-guard.sh`
**Commit:** c5a8396
**Applied fix:** The device shell prints a `__rc=0` sentinel after listing; success requires adb exit 0 and the sentinel as
the last line. Anything else is `ERROR reason=check_unproven` (exit 2); the existing `run_as_failed` mapping is kept. Verifier
gains `verify_keys_gone_absent` (happy path), `keys_gone_adb_timeout` (empty output, rc 124, which the old code read as
"keys gone") and `keys_gone_ls_failed`; the raw adb text is never printed.

### WR-02: The 33+1 ceiling is not a combined invariant

**Files modified:** `sample/.../evidence/RequestBudget.kt`, `RequestBudgetTest.kt`
**Commit:** 9476408 (shared with WR-03)
**Status:** fixed: requires human verification (logic change)
**Applied fix:** A core call must also satisfy `core + optional + requests <= 34`. New test `theTotalCeilingHoldsInEitherOrder`
(`core = 30, optional = 2` refuses a core call). The decision file's "0-1 / 1" probe cap was not changed here: the probe
remains gated to one logical call and to the combined total, which is what the ceiling needs.

### WR-03: The budget store fails open on an unreadable or corrupt file, and on a failed write

**Files modified:** `sample/.../evidence/RequestBudget.kt`, `sample/.../evidence/EvidenceLine.kt`,
`sample/.../net/ProviderFactory.kt`, `RequestBudgetTest.kt`
**Commit:** 9476408
**Status:** fixed: requires human verification (logic change)
**Applied fix:** `FileBudgetStore.read()` returns `BudgetState.EMPTY` only when no file exists. A file that cannot be read,
is a directory, has a line without `=`, a number it cannot parse, a negative count, or lacks `core` or `optional` reads as
`BudgetState.UNREADABLE` (1000 per pool, so every call and every leg start is refused and the header shows the absurd count).
Unknown keys are still ignored. A count that cannot be saved (`IOException`, `SecurityException`) latches the guard closed
for the process, and `AttemptTap.record` now keeps and emits the attempt first, counts last, and emits a loud
`VAE_BUDGET fault=store_write_failed` line when the count was lost. All store writes go through one latch (this also stops
`markAgenticStart` and `recordRun` from throwing out of the leg).

### WR-04: Requests cancelled in flight, or killed with the process, are never counted

**Files modified:** `sample/.../evidence/RequestBudget.kt`, `RequestBudgetTest.kt`
**Commit:** 6758d5f
**Status:** fixed: requires human verification (logic change)
**Applied fix:** `BudgetedProvider.complete` saves a persisted `inflight` mark before the call can send (if the mark cannot be
saved the call is not sent) and settles it in a `finally`. A call that did not return (cancelled or threw) counts the one
request that was on the wire; a mark left by a dead process is counted as one request when the next `RequestBudget` is built.
A call that returns is not counted twice (attempts are still counted one by one by the observers). The count is deliberately
conservative: a kill after the response but before settlement over-counts by one rather than under-counting.
Tests: cancelled-on-the-wire, not-double-counted, stale mark after a restart, unsavable reservation does not send.

### WR-05: Debug autorun intent is unauthenticated and D-01 is not enforced

**Files modified:** `sample/.../legs/LegRunner.kt`, `sample/src/debug/.../DebugTools.kt`, `SampleViewModelTest.kt`,
`GATE1-RUNBOOK.md`
**Commit:** 3e4d60a
**Status:** fixed: requires human verification (logic change)
**Applied fix:** Least invasive correct fix: `LegRunner.run` refuses an autorun for a leg that has not been run from the screen
(started from the UI in this process, or, for live legs, a non-zero persisted run count) with a loud
`REFUSED reason=autorun_before_ui trigger=autorun` verdict and sends nothing. Reruns still work, so the runbook's rerun use is
unchanged (its Q2 line now states the enforcement). The intent itself cannot be authenticated (a launcher activity must be
exported), so that residual is stated in the `DebugTools` KDoc: any app on a debug device can ask for a RERUN of a leg already
driven by hand, bounded by the 33+1 budget and the warm window; release builds ignore the extra.

### WR-06: The runner prints the fixture SHA suffix

**Files modified:** `scripts/run-sample-gate1.sh`, `sample/.../ui/HeaderText.kt`, `scripts/verify-sample-device-guard.sh`,
`SampleViewModelTest.kt`, `GATE1-RUNBOOK.md`
**Commit:** 1256fa3
**Applied fix:** The `push-fixture` line prints `fixture_sha=<8-hex prefix>` only and the on-screen banner shows
`Fixture OK sha=<8-hex prefix> tools=<n> source=<s>` only. The verifier asserts the runner line shape; a banner test asserts
the suffix is absent. The runbook's expected banner text and `push-fixture` evidence line say prefix only, and tell the tester
not to paste anything longer. The filter (CR-01) also rejects any longer digest. Open question for the master, not changed
here: the full 64-hex digest remains a source constant in `FixtureLoader.kt` (the runner reads it from there for the host
check); if LE-7 means the digest itself must not be committed, that needs a decision on how the check is wired.

### WR-07: A non-whitelisted exception during import leaves plaintext key files on disk

**Files modified:** `sample/src/debug/.../DebugTools.kt`, `sample/.../SampleViewModel.kt`,
`sample/src/testDebug/.../TestKeyImporterTest.kt`, `SampleViewModelTest.kt`
**Commit:** a89f7a3
**Applied fix:** The file is destroyed in a `finally` around the save, so success, any exception and cancellation all destroy
it. Any non-cancellation save failure becomes a `SAVE_FAILED` report with the exception type only (never its message, which
could quote the key); `CancellationException` is rethrown. `importAll` also sweeps every pushed key file in a `finally`, so a
cancelled or crashed import leaves nothing behind. `importTestKeys` no longer crashes the app: an importer failure shows
`Import failed (<type>)` in red. Tests: unexpected exception destroys the file and the next provider still imports, a
cancelled import destroys every pushed file and removes the directory, a throwing importer is shown not crashed.

## Skipped Issues

None in scope. IN-01..IN-08 were out of scope by instruction and are unchanged.

---

_Fixed: 2026-10-01_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
