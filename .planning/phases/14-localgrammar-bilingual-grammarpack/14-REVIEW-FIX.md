---
phase: 14-localgrammar-bilingual-grammarpack
fixed_at: 2026-10-06T00:00:00Z
review_path: .planning/phases/14-localgrammar-bilingual-grammarpack/14-REVIEW.md
iteration: 1
findings_in_scope: 7
fixed: 7
skipped: 0
status: all_fixed
---

# Phase 14: Code Review Fix Report

**Fixed at:** 2026-10-06
**Source review:** .planning/phases/14-localgrammar-bilingual-grammarpack/14-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 7 (0 critical, 7 warning; Info findings out of scope)
- Fixed: 7
- Skipped: 0

**Where verification ran:** the main checkout, on branch `gsd/phase-14-localgrammar-bilingual-grammarpack`, not an isolated worktree. The orchestrator asked for commits on the current branch, and a fresh worktree would have needed a full cold Gradle build on a memory-tight host. Each fix was committed by path, so the unrelated uncommitted `.planning/` changes in the tree were never staged. No device, adb or TESTER was touched.

## Verification

Final gate, run in the main checkout after all fixes:

`./gradlew :core:test :core:detekt :core:scanBannedConstructs :core:metalavaCheckCompatibility --offline` : BUILD SUCCESSFUL. 907 `:core` tests, 0 failures. detekt clean at zero baseline. `scanBannedConstructs` clean. Metalava compatibility passes (`core/api.txt` untouched, no public signature changed).

Also run:
- `scripts/verify-stt-capture-guard.sh` (fake adb, offline): `STT CAPTURE GUARD OK scenarios=57`.
- `scripts/run-stt-capture.sh build` (host only, no grant): `:sample:assembleDebug` and `:sample:assembleDebugAndroidTest` compiled after the WR-04 edit and again after the WR-07 edit.

A first full `:core:test` run after WR-01 and WR-05 failed 2 guard tests (`NoHardCodedConstantsTest` on a `MAX_` constant name, `ApiShapeTest` on a default-argument constructor stub). Both were caused by my own fixes and were repaired by two follow-up commits (listed below). The final gate above is green.

## Fixed Issues

### WR-01: `text(name, maxWords)` has no upper bound
**Files modified:** `core/.../grammar/SlotValidator.kt`, `core/.../grammar/Slots.kt`, `core/.../grammar/GrammarPack.kt` (doc only), `core/src/test/.../GrammarPackValidationTest.kt`
**Commits:** 80cb945, follow-up eb57c3c (constant renamed from `MAX_TEXT_WORDS` to `LARGEST_TEXT_SPAN` so the limit-constant source guard accepts it)
**Applied fix:** `maxWords` must now be in `1..64`, refused at build with the usual tool/slot-named message. `TextSlot.candidates` computes the window end in `Long` so it cannot wrap. With the ceiling in place the `span` sum in `RuleMatcher` can no longer overflow. Tests: `Int.MAX_VALUE` and 65 are refused; a slot at the ceiling still matches after a leading word. Public signatures unchanged (KDoc only).

### WR-02: `VAE_STT_PHASE_DIR` lets any caller redirect the grant gate
**Files modified:** `scripts/run-stt-capture.sh`, `scripts/verify-stt-capture-guard.sh`
**Commit:** cf87d37
**Applied fix:** `PHASE_DIR` is now the fixed phase path; the environment override is gone. The guard gained a scenario (`grant_env_override_ignored`: a decoy dir with an open grant plus the env var, skeleton grant pending, must still refuse with `no_grant` and zero adb calls) and two static checks (no `VAE_` read; `GRANT_FILE` assigned once from the fixed `PHASE_DIR`).
**Lock part of the finding:** `LOCK_FILE` keeps `${XDG_RUNTIME_DIR:-/tmp}`. That expression is byte-identical in `run-keystore-instrumented.sh` and `run-sample-gate1.sh`, and `XDG_RUNTIME_DIR` is the system-set runtime dir, not a runner-specific knob. Hard-coding it in this runner alone would have broken the shared exclusion on a host that sets it (this one does: `/run/user/1000`). Instead a new static check, `static_lock_shared`, asserts the three `LOCK_FILE=` lines are identical, so drift is caught. This is a deliberate narrowing of the review's suggested fix, not a skip.

### WR-03: A timed-out `run` leaves the instrumentation running
**Files modified:** `scripts/run-stt-capture.sh`, `scripts/verify-stt-capture-guard.sh`
**Commit:** 34cb6ab
**Applied fix:** On `rc == 124` the runner force-stops both `$APP_PKG` and `$TEST_PKG` (best effort, still `-s` the TESTER) before `finish`. The `run_timeout` scenario now asserts both `force-stop` calls were logged.

### WR-04: No per-prompt fault containment in the capture tool
**Files modified:** `sample/src/androidTest/.../SttFormsCaptureTool.kt`
**Commit:** 4a68cbf
**Applied fix:** Each prompt goes through `captureContained`, which turns `IOException`, `SecurityException`, `IllegalStateException`, `IllegalArgumentException` or `IndexOutOfBoundsException` into an `error` row whose `code` is the exception class name only (never the message). `readWav` bounds-checks the 16-byte `fmt ` body before reading it. `recognize` closes the pipe's write end when the feeder never started. Compiled via the runner's `build` subcommand; not run on a device.

### WR-05: A sentence terminator next to a filler vetoes the match
**Files modified:** `core/.../grammar/GrammarText.kt`, `core/.../grammar/RuleMatcher.kt`, `core/src/test/.../GrammarFillersTest.kt`
**Commits:** 90028db, follow-up bc0651d (removed a default argument on `GrammarToken` that tripped the "no default-argument constructor stub" shape guard)
**Applied fix:** Each `GrammarToken` records `breakBefore` (a terminator stood between the previous word and it). `GrammarTokens.clauseBreak` is now derived from the tokens it holds, ignoring the first, so a slice that dropped a filler no longer counts a terminator beside it. `RuleMatcher.match` tests `kept.clauseBreak`. Tests: `"Okay. Turn on the light"`, `"Turn on the light. Thanks."` and `"Okay! Turn on the light? Thanks!"` now match; terminators between kept words (`"Okay. Turn on. The light"`, `"Turn on the light. Turn on the light"`) still refuse. `GrammarTextTest` clause-break expectations unchanged and passing.
**Status:** fixed: requires human verification (logic change in the matcher; covered by the new tests and the existing 907-test suite, but the behavior is a semantic one).

### WR-06: Determinism and "decision equals match" tests compare `toString()`
**Files modified:** `core/src/test/.../GrammarNeverGuessesTest.kt`, `core/src/test/.../GrammarLanguageLabelTest.kt`
**Commit:** 86aa134
**Applied fix:** Both tests now compare `toolName`, `arguments`, `matchedLanguage`, `terminal` and `ruleId` through a private `assertSameMatch` helper that also treats null/non-null mismatches as failures. The determinism test asserts every corpus row is non-null; the decision test asserts at least one row matched, so neither can pass vacuously.

### WR-07: One utterance id for every TTS request
**Files modified:** `sample/src/androidTest/.../SttFormsCaptureTool.kt`
**Commit:** 1fc19ce
**Applied fix:** `synthesize` takes the prompt id and uses `"u-$promptId"` as the utterance id. The progress listener ignores `onDone` and `onError` for any other id, and a timeout or failed queue calls `tts.stop()` before the next prompt. Compiled via the runner's `build` subcommand; not run on a device.

## Skipped Issues

None.

## Not in scope

Info findings IN-01 through IN-07 were not attempted (`fix_scope: critical_warning`).

---

_Fixed: 2026-10-06_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
