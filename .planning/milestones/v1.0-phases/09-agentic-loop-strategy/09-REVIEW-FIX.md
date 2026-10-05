---
phase: 09-agentic-loop-strategy
fixed_at: 2026-10-01T00:00:00Z
review_path: .planning/phases/09-agentic-loop-strategy/09-REVIEW.md
iteration: 1
findings_in_scope: 9
fixed: 9
skipped: 0
status: resolved
---

# Phase 9: Code Review Fix Report

**Fixed at:** 2026-10-01
**Source review:** .planning/phases/09-agentic-loop-strategy/09-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 9 (WR-01..WR-03, IN-01..IN-06)
- Fixed: 9 (WR-01 and IN-04 by documentation only, by instruction)
- Skipped: 0

Verification ran in the main checkout (no worktree; the fixer was the only writer). After every finding
`./gradlew :core:test :core:detekt --offline -q` exited 0. After the last commit `./gradlew check --offline -q`
exited 0 (all OkHttp matrix legs, detekt zero baseline, scanners), and
`scripts/review-api-surface.sh --expect-sealed-complete` printed `API SURFACE OK ... classes=181` (unchanged).
For IN-05, `:core:verifyInvariantScannerControls`, the three `scanBannedConstructs` tasks and
`scripts/verify-negative-controls.sh` (0 failures) were also run. No public API changed, so no `api.txt` change;
no tag, push or ledger edit. No locked decision (D-01..D-13) or signed-off seam item is contradicted by any fix.

## Fixed Issues

### WR-01: A tool turn landing exactly on the token ceiling still dispatches

**Files modified:** `core/.../strategy/agentic/AgenticLoopStrategy.kt` (KDoc only)
**Commit:** c89169e
**Applied fix:** Documentation only. The intentional, signed-off behavior (seam sign-off item 9 / Q10) is unchanged:
no predicate or test was touched. The `AgenticLoopStrategy` KDoc now states the equality boundary: before a model
call the run is refused at or above the ceiling; after a tool turn it fails before dispatch only strictly above the
ceiling; a tool turn ending exactly on the ceiling still runs its calls and the next model call is then refused.

### WR-02: A non-mutating tool could write entries into the executed list through Finished(PREVIEW) or Finished(ERROR)

**Files modified:** `core/.../strategy/agentic/AgenticDispatch.kt`, `core/src/test/.../AgenticLoopGateTest.kt`
**Commit:** 338a413
**Status:** fixed: requires human verification (logic change)
**Applied fix:** `guardWrites` is now a `when`: a mutating tool passes through, a read tool's Mutation is still
rewritten to the typed error, and a read tool's `Finished` of any kind other than READ is rewritten to a READ
`Finished` that keeps the content, token, ids and context. An ERROR kind keeps its error flag even when the app left it
unset (matching what the coordinator would have reported), so the model still sees `is_error` and the strike still
counts. Two new tests: a read tool returning PREVIEW leaves `executed` and the sink empty and the model gets its
content as a non-error; a read tool returning ERROR twice leaves them empty, stays an error to the model, strikes, and
ends the run as a failure after two requests. Existing strike and gate tests are unchanged and green.

### WR-03: Cancellation observed only at submit, so prepare still ran for later calls

**Files modified:** `core/.../strategy/agentic/AgenticDispatch.kt`, `core/src/test/.../AgenticLoopGateTest.kt`
**Commit:** 1fa75f4
**Applied fix:** `dispatchCall` starts with `currentCoroutineContext().ensureActive()`. New test cancels the caller from
the commit sink after the first call of a two-call turn (a write then a read) and asserts a `CancellationException`
and that the executor was asked to prepare only the first call. The test was confirmed to fail with the check
removed.

### IN-01: `lastTurnGuard` called `.first()` before whole-turn validation

**Files modified:** `core/.../strategy/agentic/AgenticLoopStrategy.kt`
**Commit:** 08fcc77
**Applied fix:** `firstOrNull()?.let { ... }` replaces `.first()`, with a comment naming the upstream invariant (the
turn decision only lets a tool turn through when it has calls). Check order not changed.

### IN-02: Unreachable fallback; history appended when struck out

**Files modified:** `core/.../strategy/agentic/AgenticLoopStrategy.kt`
**Commit:** b6c52fc
**Applied fix:** The `EXHAUSTED_CODE` return is commented as defensive and unreachable (kept, not turned into a
throw, so behavior is identical). `dispatchTurn` no longer extends the history when the run struck out, as well as
when it ended on a terminal call. Outcomes are unchanged; all loop tests green.

### IN-03: Stale and incomplete public KDoc

**Files modified:** `core/.../telemetry/TraceCode.kt`, `core/.../strategy/ToolExecutor.kt` (KDoc only)
**Commit:** 324e29d
**Applied fix:** `EXTRA_TOOL_CALLS_DROPPED` now says the model sent more calls than the strategy acts on (a single-shot
tier's first call, or the calls after a terminal call). `ToolExecutor` now states that a terminal tool is never
executed (the first call to one ends the run and the calls after it are dropped), and also records the WR-02 rule that a
preview or error result from a non-mutating tool is treated as a read.

### IN-04: An OTHER stop reason with tool calls dispatches writes

**Files modified:** `core/.../strategy/agentic/AgenticTurn.kt` (KDoc only)
**Commit:** 619bc2b
**Applied fix:** Documentation only; behavior follows D-11 unchanged. The `decideTurn` KDoc now names the four stop
reasons (refusal, max tokens, pause turn, context window exceeded) that are final even with tool calls, and says any
other answer with tool calls, including an unmapped stop reason, is a tool turn.

### IN-05: The tool-count scanner rule is easy to bypass

**Files modified:** `gradle/invariants.gradle.kts`, `config/negative-controls/clean.kt.txt`, new controls
`config/negative-controls/tool-count-operators.kt.txt`, `tool-count-number-first.kt.txt`, `tool-count-two-args.kt.txt`
**Commit:** 6b3a0d4
**Applied fix:** The rule now matches `<tools-ish>.size|count()` against 17 or 18 with `==`, `!=`, `<`, `<=`, `>`, `>=`
or `,`, in either order (so `assertEquals(18, tools.size)` is caught). The comment notes the rule is not a type checker
and that `src/test` is deliberately outside the raw rules. Each new form has its own negative control, and `clean.kt.txt`
gained near-misses (other numbers, `118`, `180`, a non-tools name, other operators); the existing control and all
near-misses stay green. Two new control files are new files the fix required.

### IN-06: Test fragility and small coupling

**Files modified:** `core/src/test/.../AgenticLoopStopLeavesTest.kt`, `AgenticLoopProviderNeutralityTest.kt`, `TraceTest.kt`
**Commit:** 44acbbe
**Applied fix:** The positional `LEAF_MAX_TOKENS` / `LEAF_REFUSAL` indexes are replaced with class-based checks (exactly
one unknown-stop, and max-tokens and refusal each present). The agentic source root is resolved by walking up from the
working directory (module dir or repo root). The hand-kept `THIRTY_TWO` total is replaced by a reflective read of
`TraceCode.Companion` compared with the listed wire values, so a new code without a row fails the test.

## Skipped Issues

None.

---

_Fixed: 2026-10-01_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
