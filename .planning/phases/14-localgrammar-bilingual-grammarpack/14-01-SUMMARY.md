---
phase: 14-localgrammar-bilingual-grammarpack
plan: 01
subsystem: core/strategy/grammar
tags: [localgrammar, grammarpack, trace-codes, resolver, bilingual, tracer]

requires:
  - phase: 13
    provides: SingleShot strategy, OutcomeResolver/Resolution seam, StrategyCapabilities.NO_PROVIDER
provides:
  - StepSubmission.kt shared submit path (resolutionOutcome, submitSteps with nullable providerCallId)
  - GrammarPack DSL (intent/en/es) with whole-sequence matching, GrammarMatch
  - LocalGrammarStrategy tracer - declared EN/ES phrase resolves to the app tool with zero provider calls
  - Six additive grammar TraceCodes, resolver-rejection slice (grammar_resolver_rejected)
  - Extraction.matchedLanguage (internal 4-arg ctor; public ctors leave it null)
affects: [14-02, 14-03, 14-04, 14-05, 14-06, 14-07, 14-08, 15]

actuals:
  tokens: 11400
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Grammar tier writes only through session.submit via submitSteps (app gate decides); providerCallId null"
    - "Resolver verdict passes through resolutionOutcome unchanged (D-02); NoMatch records a trace code, TierWalk clears the carry"

key-files:
  created:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/StepSubmission.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/GrammarPack.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/GrammarMatch.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/LocalGrammarStrategy.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/LocalGrammarPipelineTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/LocalGrammarVerdictsTest.kt
  modified:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/OutcomeResolver.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/singleshot/SingleShotOutcomes.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/singleshot/SingleShotStrategy.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/TraceCode.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TraceTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ApiShapeTest.kt

key-decisions:
  - "D-01: resolutionOutcome and SingleShot's submit helper moved to StepSubmission.kt as a pure move in the phase's first commit; SingleShot passes call.id, agentic package untouched"
  - "D-02: no try/catch in the grammar tier; a resolver throw is handled by TierWalk's strategy_error guard like SingleShot"
  - "D-05: Extraction gains matchedLanguage via an internal 4-arg constructor; public 2/3-arg constructors keep JVM signatures and yield null"

patterns-established:
  - "Resolver NoMatch on a grammar match: record GRAMMAR_RESOLVER_REJECTED, outcome stays StrategyOutcome.NoMatch, next tier sees carry == null"

requirements-completed: [GRAM-01, GRAM-03, GRAM-05]

coverage:
  - id: D1
    description: "D-01 pure move of the shared submit path into StepSubmission.kt; SingleShot suites unchanged and green"
    verification:
      - kind: unit
        ref: "./gradlew :core:test (SingleShot, commit-path and held suites)"
        status: pass
    human_judgment: false
  - id: D2
    description: "A declared EN or ES phrase resolves through resolver, gate, commit and sink with zero provider calls and providerCallId null"
    requirement: "GRAM-01"
    verification:
      - kind: unit
        ref: "LocalGrammarPipelineTest"
        status: pass
    human_judgment: false
  - id: D3
    description: "Resolver NoMatch ends the tier NoMatch with grammar_resolver_rejected and a cleared carry"
    requirement: "GRAM-03"
    verification:
      - kind: unit
        ref: "LocalGrammarVerdictsTest"
        status: pass
    human_judgment: false
  - id: D4
    description: "LocalGrammarStrategy.capabilities is fixed NO_PROVIDER"
    requirement: "GRAM-05"
    verification:
      - kind: unit
        ref: "LocalGrammarPipelineTest"
        status: pass
    human_judgment: false
  - id: D5
    description: "Six grammar TraceCodes pinned by TraceTest; Extraction constructor shapes pinned by ApiShapeTest"
    verification:
      - kind: unit
        ref: "TraceTest#theGrammarCodesHaveTheirSnakeCaseWireValues, ApiShapeTest#extractionPublicConstructorsLeaveMatchedLanguageNull"
        status: pass
    human_judgment: false

duration: 15min
completed: 2026-10-06
status: complete
plan_head_before: 31f8e91c51f6d5bd34ab01bf34c9293f6ea5d0ee
commits: 3
---

# Phase 14 Plan 01: LocalGrammar tracer Summary

**LocalGrammarStrategy resolves a declared EN/ES GrammarPack phrase to the app's tool through the existing resolver, gate, commit and sink path with zero provider calls, plus six grammar trace codes and the resolver-rejection slice.**

## Performance

- **Duration:** ~15 min for the resumed finish (Task 3 verify, gate, commit, summary); Tasks 1-2 were done by a prior executor that was killed by earlyoom
- **Tasks:** 3
- **Files:** 6 created, 6 modified

## Accomplishments

- Task 1 (cc5f4c9): `resolutionOutcome` and SingleShot's submit helper moved into `strategy/StepSubmission.kt` as `resolutionOutcome` / `submitSteps(session, steps, providerCallId: String?)`; pure move, SingleShot passes `call.id`.
- Task 2 (c9e12a9): `GrammarPack` DSL, `GrammarMatch`, `LocalGrammarStrategy` tracer with `Extraction.matchedLanguage`; `LocalGrammarPipelineTest` covers EN and ES phrases, `FakeAiProvider.callCount == 0` under `NoNetworkGuard`, `providerCallId == null`, NO_PROVIDER capabilities.
- Task 3 (0b6c239): six additive `GRAMMAR_*` TraceCodes, `grammar_resolver_rejected` recorded on resolver NoMatch, `LocalGrammarVerdictsTest`, TraceTest `grammarCodes` rows, ApiShapeTest Extraction constructor pins. Includes line-length trims in Task 2 files required by detekt.

## Task Commits

1. Task 1: `cc5f4c9` refactor - move resolutionOutcome and submit helper into StepSubmission (D-01)
2. Task 2: `c9e12a9` feat - LocalGrammarStrategy tracer
3. Task 3: `0b6c239` feat - six grammar trace codes, resolver-rejection slice, Extraction shape pins

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] detekt MaxLineLength on files touched in Tasks 2 and 3**
- **Found during:** wave-end gate
- **Issue:** lines over 120 chars in GrammarMatch.kt, GrammarPack.kt, OutcomeResolver.kt, ApiShapeTest.kt, LocalGrammarPipelineTest.kt, LocalGrammarVerdictsTest.kt
- **Fix:** trimmed lines (no behavior change); folded into the Task 3 commit rather than a separate style commit
- **Commit:** 0b6c239

**2. Environment: Gradle daemon killed by earlyoom once** during the wave-end gate; retried once with >6 GiB available and passed. No code change.

## Notes for downstream plans

- Null-label cross-pack disagreement and an unsupported language label currently return `Rejected(null)` per this plan; plan 14-06 is expected to attach `grammar_ambiguous` / `grammar_language_unsupported`.
- Phase 15 note: the D-01 guarded-prepare set was empty. `guardWrites` / `prepare` in `AgenticDispatch.kt` stay agentic-only; Phase 15 extracts what it needs.

## Verification

- Task 3 verify (`:core:test` TraceTest, ApiShapeTest, LocalGrammarVerdictsTest, LocalGrammarPipelineTest): exit 0.
- Wave-end gate `:core:test :core:detekt :core:scanBannedConstructs :core:metalavaCheckCompatibility`: exit 0 (Metalava compat green against committed `core/api.txt`).
- No file deletions in any plan commit.

## Self-Check: PASSED

- Created files exist (StepSubmission.kt, GrammarPack.kt, GrammarMatch.kt, LocalGrammarStrategy.kt, LocalGrammarPipelineTest.kt, LocalGrammarVerdictsTest.kt).
- Commits cc5f4c9, c9e12a9, 0b6c239 present on gsd/phase-14-localgrammar-bilingual-grammarpack.
