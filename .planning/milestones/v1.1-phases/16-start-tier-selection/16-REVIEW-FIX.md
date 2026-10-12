---
phase: 16-start-tier-selection
fixed_at: 2026-10-06T00:00:00Z
review_path: .planning/phases/16-start-tier-selection/16-REVIEW.md
iteration: 1
findings_in_scope: 7
fixed: 6
skipped: 1
status: resolved
---

# Phase 16: Code Review Fix Report

**Fixed at:** 2026-10-06
**Source review:** .planning/phases/16-start-tier-selection/16-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 7 (fix_scope: all)
- Fixed: 6
- Skipped: 1 (a documented acceptable-skip, see below)

**Verification:** Tier 1 per fix; the orchestrator then ran `:core:test :core:detekt :core:scanBannedConstructs :core:metalavaCheckCompatibility` once: green after one KDoc line-wrap follow-up commit. Edits were made in an isolated
worktree and fast-forwarded onto `gsd/phase-16-start-tier-selection`, so the commits sit directly on the branch.
`core/api.txt`, `providers/api.txt` and `keystore/api.txt` are untouched; no public surface was added.

## Fixed Issues

### WR-01: A picker turn recorded after the pick closed is attributed to the wrong tier

**Files modified:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/SelectionBook.kt`
**Commit:** c94bd9d (+ follow-up KDoc wrap for detekt MaxLineLength)
**Applied fix:** `SelectionBook.take` now claims every turn reported under the picker's id and returns true for it. The
turn is added to the selection only while it is open; a late turn is dropped from the attempts instead of landing on
the tier in flight. Its tokens still count in the run total, because `RunRecorder.turnRecorded` adds them regardless.
Status: fixed: requires human verification (logic change in recorder state handling; no existing test exercises a late
picker turn).

### WR-02: Docs say the zero-call head runs "whatever the selector", but `TierSelector.Fixed` skips it

**Files modified:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/TierSelector.kt`, `API.md`, `INTEGRATION.md`
**Commit:** ab2872d
**Applied fix:** Documentation only. The claim is now scoped to `Custom` and `Router`; `Fixed` is documented as
starting exactly at its tier and skipping the head, and `Linear` as running the ladder in order. The code (and the
characterization test that pins `Fixed`) is unchanged.

### WR-03: A zero-call tier that is not at the head is silently skipped when the pick lands past it

**Files modified:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/TierWalk.kt`, `INTEGRATION.md`, `core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/StartTierFallbackTest.kt`
**Commit:** 45b92b3
**Applied fix:** Took the reviewer's second option (document and pin), to avoid a behavior change on the frozen surface.
`TierWalk.walkPicked` KDoc and INTEGRATION now say a zero-call tier after a model tier is bypassed with the tiers the
pick skips and is not counted in `tiersBypassed`. Added
`aMidLadderZeroCallTierIsBypassedWhenThePickLandsPastIt` using the existing `Ladder` helper: the picker picks `agentic`,
`local` and `single` never execute, attempts are `grammar, agentic`, `tiersBypassed == 1`. The new test was not run.

### IN-02: A timed-out picker call is billed but never reaches `tokensUsed` or the trace

**Files modified:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/TierPolicy.kt`, `INTEGRATION.md`
**Commit:** 13bf7b5
**Applied fix:** Documentation only. The `pickerTimeoutMillis` KDoc and the INTEGRATION "Mistakes never fail or pay"
bullet now state that a timed-out call is cancelled with no turn record, may still be billed, and is absent from
`trace.usage` and `tokensUsed`, and advise raising the timeout for a cold connection. The default is unchanged.

### IN-03: The step-id length cap is an unrelated behavior change on the frozen plan-parse surface

**Files modified:** `API.md`
**Commit:** fbbb318
**Applied fix:** Documentation only. API.md now states step ids are at most 64 characters, that a longer id rejects the
plan as a bad id (a tightening since v1.0.0), and that a longer `$<id>.key` reference can only fail as unresolved. The
optional `REFERENCE` `{0,63}` bound was not applied, because it would change plan-parse behavior on the frozen surface.
No ledger or changelog was edited: the §11 ledger is control-plane owned and the repo has no changelog.

### IN-04: Minor duplication and readability issues

**Files modified:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/TierSelector.kt`, `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/RouterPicker.kt`
**Commit:** 39fa427
**Applied fix:** Hoisted the repeated `if (eligible.isEmpty()) null else 0` into a file-private `firstIfAny(eligible)` used
by `Linear`, `Custom` and `Router`. `routerRequest` now passes `cache =`, `singleToolCall =` and `reasoning =` by name
(the 8-parameter primary constructor is the only one with `reasoning`, so overload resolution is unambiguous).

## Skipped Issues

### IN-01: `router_fallback` is recorded when no picker was ever called, conflating two signals

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/StartTierPicking.kt:75-77`
**Reason:** skipped (documented acceptable-skip): the reviewer's own conditional applies ("keep it as is if the contract
is frozen"). A distinct code needs a new `TraceCode` constant (public surface change on a frozen API) and would break
the existing `StartTierPolicyTest` (`noModelTierLeft`) and API.md, which both specify that `router_fallback` also covers
"no model tier was left after the zero-call head". The overload is already documented in API.md.
**Original issue:** `router_fallback` is recorded for no-call cases (no model tier left, picker policy-forbidden) as
well as for a picker that was actually asked and failed, so a dashboard counting it as picker failure rate gets false
positives.

---

_Fixed: 2026-10-06_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
