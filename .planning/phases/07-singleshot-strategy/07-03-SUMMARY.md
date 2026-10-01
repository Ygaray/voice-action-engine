---
phase: 07-singleshot-strategy
plan: 03
subsystem: core-seams
tags: [tool-spec-provider, outcome-resolver, user-turn-renderer, seam-signoff, redaction]
status: complete

requires:
  - phase: 07-singleshot-strategy
    provides: "ModelRequest.singleToolCall flag (07-01) and its wire encoding (07-02)"
provides:
  - "ToolSpecProvider (fun interface, fixed(snapshot)) and ToolingSnapshot(system, tools, singleShotTool)"
  - "OutcomeResolver (fun interface), Extraction(toolName, arguments), open Resolution with Steps / NoMatch / Escalate / Failed"
  - "UserTurnRenderer (fun interface, standard()) and engine-built UserTurnContext(input, dateTime, carry)"
  - "evidence/seam-signoff.txt with SIGNOFF: APPROVE"
affects: [07-04, 07-05, 07-06, 09]

plan_head_before: 1c4331c55545fabc8d8e8ffb8402be361556f022
commits: 4

actuals:
  tokens: 9000
  tasks: 3
  commits: 4

tech-stack:
  added: []
  patterns:
    - "App seam as fun interface plus a final-leaf open abstract class result (Resolution) with an internal constructor, like ToolChoice and ModelResult"
    - "Renderer hook whose output can only become the user message, so the cached prefix is untouched by construction"

key-files:
  created:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/ToolSpecProvider.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/OutcomeResolver.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/UserTurn.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/SingleShotSeamTypesTest.kt
    - .planning/phases/07-singleshot-strategy/evidence/seam-signoff.txt
  modified:
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/RedactionCanaryTest.kt
    - config/detekt/detekt.yml

key-decisions:
  - "Seam shapes ship exactly as built and are confirmed by the relayed sign-off (SIGNOFF: APPROVE); they were provisional until that verdict"
  - "detekt MemberNameEqualsClassName excluded for OutcomeResolver.kt with a one-line justification, so Resolution.Steps keeps its signed-off `steps` property (no suppression annotation)"

requirements-completed: [SHOT-01]

coverage:
  - id: D1
    description: "The new seams compose with the shipped write path: tooling snapshot -> forced single-call request -> Extraction -> resolver -> Steps -> session.submit; an admitting gate gives one COMMITTED action, a holding gate leaves applyCount 0"
    requirement: SHOT-01
    verification:
      - kind: integration
        ref: "core SingleShotSeamTypesTest#theSeamsComposeWithAnAdmittingGateAndTheSinkSeesOneCommit, #theSeamsComposeWithAHoldingGateAndNothingIsWritten"
        status: pass
    human_judgment: false
  - id: D2
    description: "Seam value rules: snapshot rejects an unoffered forced tool and copies tools; Extraction rejects a blank name; Steps rejects an empty list and copies it"
    requirement: SHOT-01
    verification:
      - kind: unit
        ref: "core SingleShotSeamTypesTest (aSnapshotRejectsAForcedToolThatIsNotOffered, aSnapshotAcceptsNoForcedTool, aSnapshotKeepsACopyOfItsTools, anExtractionRejectsABlankToolName, stepsRejectAnEmptyList, stepsKeepACopyOfTheirList, aFixedProviderReturnsTheSameSnapshotForEveryCommand)"
        status: pass
    human_judgment: false
  - id: D3
    description: "The standard renderer's exact text for Los Angeles and UTC, no language or context in it; a lambda renderer works; the context exposes its parts unchanged"
    requirement: SHOT-01
    verification:
      - kind: unit
        ref: "core SingleShotSeamTypesTest#theStandardRendererFramesTheTranscriptWithTheLocalDateTimeInLosAngeles, #...InUtc, #theStandardRendererLeavesOutTheLanguageAndTheContext, #aLambdaRendererIsCalledWithTheCommand, #aContextExposesItsPartsUnchangedAndPrintsNoContent"
        status: pass
    human_judgment: false
  - id: D4
    description: "No toString of a new type prints argument values, system text, reply text, transcript, context or carry values (TEL-04)"
    requirement: SHOT-01
    verification:
      - kind: unit
        ref: "core SingleShotSeamTypesTest#noToStringPrintsArgumentValuesSystemTextOrReplyText; RedactionCanaryTest sweepSingleShotSeams"
        status: pass
    human_judgment: false
  - id: D5
    description: "Public surface still passes the shape gate; seam shapes signed off by the orchestrator, CT and SB before 07-04"
    requirement: SHOT-01
    verification:
      - kind: integration
        ref: "scripts/review-api-surface.sh --expect-sealed-complete -> API SURFACE OK sealed=AssistantPart,CommandOutcome,GateDecision,Message,RunTermination,StrategyOutcome,ToolStep classes=174"
        status: pass
      - kind: manual
        ref: ".planning/phases/07-singleshot-strategy/evidence/seam-signoff.txt (SIGNOFF: APPROVE)"
        status: pass
    human_judgment: false

duration: 30min
completed: 2026-10-01
---

# Phase 7 Plan 03: SingleShot seams Summary

**The app-facing seams a single-shot tier is built on (`ToolSpecProvider`/`ToolingSnapshot`, `OutcomeResolver`/`Extraction`/`Resolution`, `UserTurnRenderer`/`UserTurnContext`) exist, compose with the shipped gate and sink, leak nothing through `toString`, and were signed off APPROVE before any strategy depends on them.**

## Accomplishments

- Tracer (`SingleShotSeamTypesTest`): a scripted tier does by hand what the strategy will do. It takes the snapshot from `ToolSpecProvider.fixed(...)`, sends one forced request with `singleToolCall = true`, turns the first tool call into an `Extraction`, gets `Resolution.Steps` holding one `ToolStep.Mutation` from an `OutcomeResolver`, and submits it with `session.submit`. With an admitting gate the sink records exactly one COMMITTED action; with a holding gate `applyCount` stays 0 and one proposal is held. The resolver never writes.
- `ToolingSnapshot` requires a non-null `singleShotTool` to name one of its tools and keeps a copy of the list. `Extraction` requires a non-blank name. `Resolution` is an open abstract class with an internal constructor and four final leaves; `Steps` requires a non-empty list and keeps a copy. No sealed type, enum, data class or default-argument constructor was added.
- `UserTurnRenderer.standard()` renders `Current local date-time: <ISO offset date-time truncated to seconds> (<zone id>)`, a blank line, then the transcript; verified exactly for America/Los_Angeles and UTC. It lives in `core.strategy` so the agentic loop reuses it. `UserTurnContext` has an internal constructor and exposes `input`, `dateTime` and `carry` unchanged.
- Redaction: every new type's `toString` prints names, counts, lengths and class names only. Marker-absence unit tests plus a new `sweepSingleShotSeams()` in the `RedactionCanaryTest` built-type sweep find no canary.

## Seam sign-off (Task 3)

Resolved before the executor ran Task 3: the orchestrator's reply (relayed by the milestone master) was read from the scratchpad and recorded verbatim in `.planning/phases/07-singleshot-strategy/evidence/seam-signoff.txt`. The shapes were provisional until that verdict.

- Verdict line: `SIGNOFF: APPROVE`
- SUBSTITUTIONS: none
- CARRY lines (verbatim from the reply): `CARRY to Phase 9: AgenticLoopStrategy must accept the same userTurn: UserTurnRenderer. SB's byte-exact need is on the agentic path. Confirm it in Phase 9's plan.` The reply notes the master records this in 09-CONTEXT.md, so Phase 7 needs no change. No other change was requested; CT approved items 1-6 and SB approved items 1, 4 and 6.
- Phase 9 carry: `AgenticLoopStrategy` must accept the same `userTurn: UserTurnRenderer` (hence `UserTurnRenderer` and `UserTurnContext` live in `core.strategy`, not under a single-shot package).
- 07-01 open item (confirm `ModelRequest.singleToolCall`, its name and its seventh constructor position): covered by the approval of seam item 6 ("The single-call request flag is ModelRequest.singleToolCall"), which CT and SB approved. No rename is needed.

## Task Commits

1. Task 1 (tracer): `dcd170c` feat(07-03): add ToolSpecProvider, Extraction, OutcomeResolver and Resolution seams
2. Task 2: `c5b7ef7` feat(07-03): add UserTurnRenderer hook with engine default and canary sweep
3. Task 3 (sign-off evidence): `ca7c1d5` docs(07-03): record seam sign-off APPROVE with Phase 9 carry
4. This summary: committed last (the fourth commit counted below)

plan_head_before: `1c4331c55545fabc8d8e8ffb8402be361556f022`; measured commit count 3 before this summary was committed (`git rev-list --count 1c4331c..HEAD`), 4 including it.

## Verification

- `./gradlew :core:test --tests '*SingleShotSeamTypesTest' --tests '*ApiShapeTest'`, then with `*RedactionCanaryTest` and `*NoHardCodedConstantsTest`: green (SingleShotSeamTypesTest 10 tests at Task 1, 15 after Task 2).
- `./gradlew :core:detekt :core:scanBannedConstructs :core:verifyExplicitApiStrict --offline -q` exit 0.
- `./gradlew :core:check --offline -q` exit 0.
- `./gradlew check --offline -q` exit 0 across all modules, including the OkHttp matrix legs.
- `scripts/review-api-surface.sh --expect-sealed-complete` prints `API SURFACE OK sealed=AssistantPart,CommandOutcome,GateDecision,Message,RunTermination,StrategyOutcome,ToolStep classes=174`.
- `git log --oneline 1c4331c..HEAD -- providers/` prints nothing. No `api.txt`, no tag, no change to the contract, the ledger, STATE.md or ROADMAP.md.

## Deviations from Plan

**1. [Rule 3 - Blocking] detekt `MemberNameEqualsClassName` on `Resolution.Steps.steps`**
- Found during: Task 1 verify (`:core:detekt` reported 1 weighted issue at `OutcomeResolver.kt:55`).
- The signed-off shape has a `Steps` leaf exposing its list as `steps`. Renaming the property would change the public shape the plan asks to freeze; a `@Suppress` is prohibited by the plan and the project's rule.
- Fix: tuned the rule in `config/detekt/detekt.yml` with an `excludes` on `**/strategy/OutcomeResolver.kt` and a one-line justification, per the project's "tune rules, never bank debt" policy. No baseline and no suppression annotation. Committed with Task 1.

Otherwise none; Rules 1, 2 and 4 were not triggered.

## Deferred Issues

None.

## Self-Check: PASSED

- Created files exist: `ToolSpecProvider.kt`, `OutcomeResolver.kt`, `UserTurn.kt`, `SingleShotSeamTypesTest.kt`, `evidence/seam-signoff.txt`; modified `RedactionCanaryTest.kt`, `config/detekt/detekt.yml`.
- Commits `dcd170c`, `c5b7ef7`, `ca7c1d5` exist on `worktree-agent-a20ffadbf657395c6`.
- `evidence/seam-signoff.txt` contains exactly one line starting `SIGNOFF: ` (`SIGNOFF: APPROVE`).
