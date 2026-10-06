---
phase: 14-localgrammar-bilingual-grammarpack
plan: 07
subsystem: grammar-semantics
tags: [grammar, normalize, terminal-intent, resolver-verdicts, held, policy, redaction]
status: complete

requires:
  - phase: 14-05
    provides: slots with raw spoken spans (RuleVerdict.One.bindings)
  - phase: 14-06
    provides: GrammarPack.decide/agreed, language label table, never-guesses corpora
provides:
  - "Public IntentBuilder.normalize(slot, hook: (String, String) -> String?) with null/blank = reject and contained faults"
  - "Public IntentBuilder.terminal(): zero-commit intent ending handled with Completed(null, TerminalCall)"
  - "Resolver optional on LocalGrammarStrategy when every intent is terminal"
  - "Proofs for D-02 verdict pass-through, SC-2 held reporting, SC-5 policy, redaction over every grammar path"
affects: [14-08, 14-09, 14-10]

requirements-completed: []
requirements-contributed: [GRAM-01, GRAM-03, GRAM-04, GRAM-05]

actuals:
  tokens: 21500
  tasks: 3
  commits: 4
plan_head_before: 0d68697bb1bf3d9ebafe4fd8326de3159efdf1cf
commits: 4

tech-stack:
  added: []
  patterns:
    - "App hook faults collapse in the one internal guardedPlain: a fault becomes a refusal code (grammar_normalize_error), never the exception or its message"
    - "Normalize runs per parsed candidate pack inside GrammarPack.decide, before agreed(), so cross-pack agreement compares normalized values; an ambiguous transcript is refused before any hook sees it"
    - "Terminal intents return Completed(null, TerminalCall) straight from the tier, before the resolver and the gate"

key-files:
  created:
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarNormalizeTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/LocalGrammarHeldTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/LocalGrammarPolicyTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarRedactionTest.kt
  modified:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/GrammarPack.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/LocalGrammarStrategy.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/SlotValidator.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/LocalGrammarVerdictsTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/RedactionCanaryTest.kt

key-decisions:
  - "A hook's answer replaces the slot value as JsonPrimitive(String); blank (isBlank) rejects like null; no trimming of a non-blank answer"
  - "When both candidate packs parsed, every hook runs (labeled language first, else EN then ES) and the first refusal in that order decides the code; there is no fallback to the surviving pack"
  - "Ambiguity is decided before any hook runs, so an app hook never sees an ambiguous transcript"
  - "Terminal is part of the match and of the agreement key (already compared in agreed); the flag comes from the intent, not the phrasing"

patterns-established:
  - "hasNonTerminalIntent on GrammarPack drives the 'resolver is required' check; the message text is unchanged"

coverage:
  - id: D1
    description: "ROADMAP SC-4 / GRAM-04: normalize sees the raw spoken text and the matched pack's language (never null); its answer replaces the slot value; null/blank = NoMatch with grammar_slot_rejected; a throw = grammar_normalize_error with no message leak; once per parsed candidate pack; out-of-map token falls through with carry null"
    requirement: "GRAM-04"
    verification:
      - kind: unit
        ref: "GrammarNormalizeTest (13 tests)"
        status: pass
    human_judgment: false
  - id: D2
    description: "D-06 / SB condition: a terminal intent ends Completed(reply null, TerminalCall) with intent and slots, resolver and gate never called, no action recorded, no next tier, works under offlineOnly; all-terminal pack needs no resolver"
    requirement: "GRAM-01"
    verification:
      - kind: unit
        ref: "LocalGrammarVerdictsTest (terminal rows), LocalGrammarPolicyTest.aTerminalIntentEndsHandledUnderOfflineOnlyWithItsTerminalCall"
        status: pass
    human_judgment: false
  - id: D3
    description: "D-02: resolver Escalate keeps its carry, Failed stops the run with its reason, a throw ends Failed(Unexpected) with strategy_error, an escalation after an earlier commit is suppressed (escalation_suppressed)"
    requirement: "GRAM-03"
    verification:
      - kind: unit
        ref: "LocalGrammarVerdictsTest (10 tests)"
        status: pass
    human_judgment: false
  - id: D4
    description: "ROADMAP SC-2: a held grammar action is ActionKind.HELD, applied false, providerCallId null, never a success; the reply is kept for a hold and withheld when the apply errored"
    requirement: "GRAM-01"
    verification:
      - kind: unit
        ref: "LocalGrammarHeldTest (3 tests)"
        status: pass
    human_judgment: false
  - id: D5
    description: "GRAM-05 / ROADMAP SC-5: capabilities == NO_PROVIDER; the tier commits under offlineOnly, allowedProviders={OPENAI} and an empty set with zero provider calls; an offline-only no-match ends Unhandled(cappedByPolicy = true) with FakeAiProvider.callCount == 0"
    requirement: "GRAM-05"
    verification:
      - kind: unit
        ref: "LocalGrammarPolicyTest (6 tests)"
        status: pass
    human_judgment: false
  - id: D6
    description: "No canary transcript, slot text, hook exception message or resolver reply appears in any trace, event, outcome/pack/match/tier/extraction toString, or build exception message; match() never throws on 1,000 seeded random strings"
    requirement: "GRAM-04"
    verification:
      - kind: unit
        ref: "RedactionCanaryTest.noCanaryLeaksFromAGrammarRun, GrammarRedactionTest (3 tests)"
        status: pass
    human_judgment: false
---

# Phase 14 Plan 07: Normalize, Terminal Intents and the Tier Under Policy Summary

**The grammar tier now has the per-slot `normalize` hook (null rejects, a throw is contained, agreement is compared after it), zero-commit terminal intents that end handled with a `TerminalCall`, and tests pinning resolver verdicts, held reporting, offline-only/provider policy and redaction across every grammar path.**

## What was built

- **normalize (Task 1).** `IntentBuilder.normalize(slot, hook)` with KDoc stating the contract. `GrammarPack.decide` now maps each uniquely parsed language through `normalized()`, which calls the hook via `guardedPlain` for each bound slot that has one, with the raw spoken text and the matched pack's language. A fault gives `Rejected(GRAMMAR_NORMALIZE_ERROR)`, null or blank gives `Rejected(GRAMMAR_SLOT_REJECTED)`, anything else replaces the slot value. `agreed()` therefore compares normalized values (the 14-06 carry-forward). Build checks (`validateNormalizers`): declared slot, text or choice only, one hook per slot, messages carry tool and slot names only.
- **terminal and tier semantics (Task 2).** `IntentBuilder.terminal()`; `GrammarMatch.terminal` now reflects the intent. `LocalGrammarStrategy` returns `Completed(null, TerminalCall(tool, args))` before any resolver call; `resolver` is nullable and required only when the pack has a non-terminal intent (`hasNonTerminalIntent`, same error text). All other verdicts stay pass-through via `resolutionOutcome` and `submitSteps(session, it, null)`.
- **policy and redaction (Task 3).** `LocalGrammarPolicyTest`, a grammar run in `RedactionCanaryTest` (canary transcript and slot text, throwing hook, rejecting resolver, held write), and `GrammarRedactionTest`.

## Commits

| Task | Commit | Notes |
|------|--------|-------|
| 1 | a442176 `feat(14-07): per-slot normalize hook with contained faults and agreement after normalize` | `GrammarPack`, `SlotValidator.validateNormalizers`, `GrammarNormalizeTest` |
| 2 | db204ed `feat(14-07): terminal intents end handled with a TerminalCall; verdicts and held reporting pinned` | `LocalGrammarStrategy`, `LocalGrammarVerdictsTest`, `LocalGrammarHeldTest` |
| 3 | 9673153 `test(14-07): grammar policy proofs and redaction sweeps over every grammar path` | policy and redaction tests |
| fix | 39615db `fix(14-07): drop default arguments from IntentSpec so ApiShapeTest stays green` | wave gate found `ApiShapeTest` rejecting default-argument stubs on `IntentSpec` |

## Deviations from the plan

1. **[Rule 3 - blocking] `validateNormalizers` lives in `SlotValidator.kt`, not `PackValidator.kt`.** `PackValidator.kt` was already at detekt's `TooManyFunctions` threshold (11); `SlotValidator.kt` has room. `PackValidator.kt` is unchanged.
2. **[Rule 1] `IntentBuilder.terminal()` landed in the Task 1 commit,** because it shares `IntentSpec` and `GrammarPack.kt` with `normalize`. The strategy branch and tests are in the Task 2 commit.
3. **[Rule 3] `IntentSpec` default arguments removed** after the wave gate's `ApiShapeTest` failed (see the fix commit); both fields are now required constructor parameters.
4. **Suppression row reinterpreted.** The plan row "a first tier commits then escalates, followed by a grammar tier that finds no match" cannot reach the grammar tier: after a commit the walk never starts the next tier (EscalationSafetyTest pins this). The test asserts exactly that with the grammar tier second: partial `Completed`, `escalation_suppressed`, grammar resolver never invoked.
5. **Resolver `Failed` row uses `FailureReason.Auth()`** (any reason would do); the resolver-throw row asserts `Unexpected("IllegalStateException")`.
6. **detekt shaped the code, not the contract:** `decide`/`normalized` use a collected refusal instead of early returns (`ReturnCount`), and `LocalGrammarStrategy.resolve` is split into `resolve` and `resolveWith`.

## Verification (plan-level only)

- Task 1 verify classes plus `LocalGrammarPipelineTest` and `:core:detekt`: exit 0 (`GrammarNormalizeTest` 13 tests).
- Task 2 verify (`LocalGrammarVerdictsTest`, `LocalGrammarHeldTest`, `LocalGrammarPipelineTest`, `HeldReportingTest`) with `:core:detekt`: exit 0 (10 and 3 tests).
- Task 3 verify (`LocalGrammarPolicyTest`, `RedactionCanaryTest`, `GrammarRedactionTest`, `TierPolicyTest`, `:core:detekt`, `:core:scanBannedConstructs`): exit 0.
- Wave gate `:core:test :core:detekt :core:scanBannedConstructs :core:metalavaCheckCompatibility` in one invocation: exit 0 (901 tests). The first run failed on `ApiShapeTest` (deviation 3), fixed and re-run green.
- Acceptance greps: `normalize(slot: String, hook: (String, String) -> String?)` 1; `guardedPlain` in GrammarPack.kt 2 (import and use); `GRAMMAR_SLOT_REJECTED|GRAMMAR_NORMALIZE_ERROR` at least 2; `catch (` in GrammarPack.kt 0; `public fun terminal()` 1; `TerminalCall(` in LocalGrammarStrategy.kt 1; `ActionKind.HELD` in LocalGrammarHeldTest 2; `escalation_suppressed` in LocalGrammarVerdictsTest 1; `cappedByPolicy` and `offlineOnly = true` in LocalGrammarPolicyTest present; `LocalGrammarStrategy` in RedactionCanaryTest present.
- No device or behavioral verification was done or needed (no adb, TESTER untouched, `14-WINDOW-GRANT.md` not modified).

## Notes for downstream plans

- Public API grew by `IntentBuilder.normalize` and `IntentBuilder.terminal`; `api.txt` is regenerated at the next tag cut (the compat check passes, additions only).
- GRAM-04 is still listed by 14-08, so REQUIREMENTS.md was not ticked for it; GRAM-01/03/05 were already ticked.
- The build-time overlap self-check and the input cap run before normalize and see declared/spoken words only; normalize cannot change which phrasing matches.
- A hook runs on every uniquely parsed candidate pack even when another candidate pack's hook has already refused; only the first refusal's code is kept.

## Self-Check: PASSED

Created files exist (GrammarNormalizeTest.kt, LocalGrammarHeldTest.kt, LocalGrammarPolicyTest.kt, GrammarRedactionTest.kt); commits a442176, db204ed, 9673153 and 39615db exist on `gsd/phase-14-localgrammar-bilingual-grammarpack`; `commits: 4` measured from the persisted ledger (`git rev-list --count 0d68697..HEAD` = 4 before this SUMMARY commit).
