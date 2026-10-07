---
phase: 19-sample-gate-1-docs
plan: 09
subsystem: docs
tags: [doc-02, integration-md, doc-snippets, grammar-tier, plan-tier, router, undo, voice-adapter]

requires:
  - phase: 19-sample-gate-1-docs
    provides: plan 08 (frozen-surface review and the "Docs versus dump" list), plan 03 (RT-04 adapter surface)
provides:
  - INTEGRATION.md section 5 grammar-tier and plan-tier subsections, router-selector block, ladder-order sentence
  - INTEGRATION.md section 11 undo-wiring block, step 2 undo coordinate and bullet
  - INTEGRATION.md section 12 adapter-wiring block (compiled in :voice-adapter's own tests)
  - DocSnippetsTest regions grammar-tier, plan-tier, router-selector, undo-wiring with 8 executing tests
  - DocSnippetAdapterTest region adapter-wiring with 4 executing tests
  - verify-docs-coverage.sh C06/C07 reading regions from a file list derived once
affects: [19-10, 19-11, 19-12, 19-13, phase-20]

status: complete
actuals:
  tokens: 11800
  tasks: 4
  commits: 4
plan_head_before: d97b749221e8f543f257024a5a8b14ce47138a6d
commits: 4

tech-stack:
  added: []
  patterns:
    - "Doc blocks are generated from their regions (region text, common indent removed) so C06 byte-equality holds by construction"
    - "A doc snippet for a module the sample must not depend on is compiled in that module's own test sources"

key-files:
  created:
    - voice-adapter/src/test/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/DocSnippetAdapterTest.kt
  modified:
    - INTEGRATION.md
    - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/docs/DocSnippetsTest.kt
    - scripts/verify-docs-coverage.sh

key-decisions:
  - "The undo-wiring snippet renders UndoResult with an exhaustive outer when (no else) and puts the required else on the inner when over the open UndoReason, because section 11 and the UndoResult KDoc say the sealed result takes no else"
  - "The adapter-wiring region lives in :voice-adapter's own test sources (PD-07 as amended); verify-docs-coverage.sh derives its snippet file list once and C06/C07 both read it"
  - "Extraction has an internal 4-argument constructor, so matchedLanguage is shown through a resolver that records the language of each match rather than a standalone helper"

patterns-established:
  - "Snippet resolvers and sinks record languages, ids and counts only, never words, keys or arguments"

requirements-completed: []  # DOC-02 stays pending by the orchestrator's instruction: it needs the wiring test on the final SHA

coverage:
  - id: D1
    description: "Grammar-tier subsection plus compiled, executed grammar-tier region: EN and ES phrasings, a slot, LocalGrammarStrategy at the head, zero provider calls, near-miss null, offlineOnly miss capped by policy"
    requirement: DOC-02
    verification:
      - kind: unit
        ref: "sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/docs/DocSnippetsTest.kt#theGrammarTierAnswersBothLanguagesWithoutAProviderCall"
        status: pass
      - kind: unit
        ref: "sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/docs/DocSnippetsTest.kt#aGrammarNearMissIsNullAndAnOfflineCommandIsCappedByPolicy"
        status: pass
      - kind: other
        ref: "scripts/verify-docs-coverage.sh --only C06,C07"
        status: pass
    human_judgment: false
  - id: D2
    description: "Plan-tier subsection plus plan-tier region: write tool naming its returned key, $<stepId>.<key> reference, needs_lookup hand-on, hold ending Completed(partial) with remainingStepIds"
    requirement: DOC-02
    verification:
      - kind: unit
        ref: "sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/docs/DocSnippetsTest.kt#theSecondPlanStepReceivesTheFirstStepsId"
        status: pass
      - kind: unit
        ref: "sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/docs/DocSnippetsTest.kt#aHoldEndsThePlanPartiallyAndListsTheStepsThatNeverRan"
        status: pass
      - kind: unit
        ref: "sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/docs/DocSnippetsTest.kt#aPlanThatNeedsALookupHandsOnWithNothingRun"
        status: pass
    human_judgment: false
  - id: D3
    description: "Router-selector region in the router subsection: TierSelector.Router with one-line tierDescriptions and a selection source mapping the router id (start_tier_router) to a small model; the pick is visible in the trace"
    requirement: DOC-02
    verification:
      - kind: unit
        ref: "sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/docs/DocSnippetsTest.kt#aScriptedRouterAnswerStartsTheWalkAtTheNamedTier"
        status: pass
    human_judgment: false
  - id: D4
    description: "Undo-wiring region in section 11 and the undo coordinate in step 2: journal with adapter, ticket per mutation, compositeSink with the journal first, Undo all (N) label, undoAll status"
    requirement: DOC-02
    verification:
      - kind: unit
        ref: "sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/docs/DocSnippetsTest.kt#theUndoWiringRegionCountsOneCommandAndUndoAllRestoresTheStore"
        status: pass
      - kind: unit
        ref: "sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/docs/DocSnippetsTest.kt#theUndoWiringRegionRefusesWhenTheItemChangedSinceTheCommand"
        status: pass
      - kind: other
        ref: "scripts/verify-docs-coverage.sh (C03 on the new coordinate line)"
        status: pass
    human_judgment: false
  - id: D5
    description: "Adapter-wiring region compiled in :voice-adapter's own tests, one Kotlin fence in section 12, no :sample edge, coverage gate reads the second snippet file"
    requirement: DOC-02
    verification:
      - kind: unit
        ref: "voice-adapter/src/test/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/DocSnippetAdapterTest.kt (4 tests)"
        status: pass
      - kind: other
        ref: "scripts/verify-docs-coverage.sh; scripts/verify-stt-confinement.sh; git diff --exit-code -- sample/build.gradle.kts gradle/invariants.gradle.kts"
        status: pass
    human_judgment: false
  - id: D6
    description: "Whether the new prose reads well to a cold integrating agent (the isolated wiring test is the judge, plans 19-11 and 19-13)"
    verification: []
    human_judgment: true
    rationale: "Only a fresh agent following the docs can show they are sufficient; the wiring test on the final SHA owns that verdict"

duration: 20min
completed: 2026-10-07
---

# Phase 19 Plan 09: INTEGRATION.md grammar, plan, router, undo and adapter content Summary

**INTEGRATION.md now documents the grammar tier, the plan tier, the engine router, undo-all wiring and the voice adapter with five new Kotlin blocks, each byte-equal to a region that is compiled and executed (four in :sample's DocSnippetsTest, one in :voice-adapter's own tests), and the coverage gate reads both files.**

## Performance

- **Duration:** about 20 min
- **Started:** 2026-10-07T16:15:00Z (approximate; not recorded at start)
- **Completed:** 2026-10-07T16:32:00Z
- **Tasks:** 4
- **Files modified:** 4 (1 created)

## Accomplishments

- Grammar tier (Task 1, tracer): section 5 subsection covering phrasings per intent, template syntax, slots, normalize, never guessing, `matchedLanguage` with the locale fallback, terminal intents, `offlineOnly` with `cappedByPolicy`, the six `grammar_*` codes and `GrammarPack.match` for corpus tests. The region runs EN and ES commands through a grammar-first ladder with zero provider calls (the scripted provider records no request, trace usage is 0).
- Plan tier (Task 2): subsection with the `$<stepId>.<key>` reference, committed-only counting (PREVIEW/READ is a failed step, P15 OI-6), `needs_lookup`, hold semantics with `remainingStepIds`, replan limits. Region proves the second step receives the first step's id, a hold ends `Completed(partial = true)` with `remainingStepIds == ["third"]`, and `needs_lookup` hands on with nothing run.
- Router (Task 2): `router-selector` block after the Router bullet, with the id-mapping gotcha (P16 OI-8) and `PipelineEvent.StartTierSelected` named. A scripted `pick_start_tier` answer starts the walk at `agentic`, `tiersBypassed == 1`, the router request used the small model.
- Ladder-order sentence on one line: "Tiers run in the order they are added ... Fixed(id) is the exception".
- Undo (Task 3): `undo-wiring` block in section 11 (adapter, ticket per mutation, `compositeSink(bridge, appSink)` journal first, `Undo all (2)`, `undoAll` restores the store; a later edit refuses with `changed_since`); `voice-action-engine-undo:<version>` in step 2 with a bullet.
- Adapter (Task 4): one `adapter-wiring` fence in section 12; four tests; `verify-docs-coverage.sh` C06/C07 read the sample test plus `DocSnippetAdapterTest.kt` from a list built once.

## Task Commits

1. **Task 1 (tracer): grammar tier** - `ee984e8` (docs)
2. **Task 2: plan tier and router-selector** - `b82506b` (docs)
3. **Task 3: undo-wiring and undo coordinate** - `7f79ea7` (docs)
4. **Task 4: adapter-wiring in :voice-adapter's tests** - `4646b16` (docs)

**Plan metadata:** the commit that adds this SUMMARY, STATE.md and ROADMAP.md.

## Files Created/Modified

- `INTEGRATION.md` - grammar and plan subsections, router, undo-wiring and adapter-wiring blocks, undo coordinate, ordering sentence
- `sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/docs/DocSnippetsTest.kt` - four regions, eight tests (the class now holds 22)
- `voice-adapter/src/test/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/DocSnippetAdapterTest.kt` - new, adapter-wiring region and four tests
- `scripts/verify-docs-coverage.sh` - `SNIPPET_FILES` list, used by `region()` and by C07

## Verification

- `:sample:testDebugUnitTest --tests '*DocSnippetsTest*' --tests '*UndoBridgeParityTest*'`: DocSnippetsTest 22 tests, UndoBridgeParityTest 1, 0 failures.
- `:voice-adapter:testDebugUnitTest --tests '*DocSnippetAdapterTest*'`: 4 tests, 0 failures.
- `scripts/verify-docs-coverage.sh`: `DOC COVERAGE OK checks=25 types=107`; `--only C06,C07` OK.
- `scripts/verify-stt-confinement.sh`: `STT CONFINEMENT OK checks=6`; `scripts/verify-repo-hygiene.sh`: `HYGIENE OK`; `scripts/verify-module-manifest.sh`: OK.
- Non-vacuity of the extended gate (copy of the doc set in a scratch directory): changing one identifier in the doc's adapter block gives `DOC COVERAGE FAIL: C06 ... block 'adapter-wiring' differs from its region`; removing the adapter test file gives `C06 ... no region 'adapter-wiring'`.
- Acceptance greps all pass: `doc-snippet:start` count is 17 (at least 16), `remainingStepIds`, `needs_lookup`, `submit_plan`, `start_tier_router`, `order they are added`, `matchedLanguage`, `cappedByPolicy`, `GrammarPack`; step 2 holds `voice-action-engine-undo:<version>`; section 12 has exactly one Kotlin fence; `git diff --exit-code -- sample/build.gradle.kts gradle/invariants.gradle.kts` is empty.

## Decisions Made

- The sealed `UndoResult` is matched without an `else` and the else sits on the inner `when` over the open `UndoReason` (see Deviations 1).
- `matchedLanguage` is shown through a resolver that records the language of every match (`Extraction`'s 4-argument constructor is internal, so a bare helper could not be tested).
- Snippets keep the never-log rule (T-19-28): resolvers and summaries print ids, codes, counts and tier names; `MyRow`, `MyItem` are plain classes so their default `toString` carries no title; the prose repeats that router `tierDescriptions` are sent to the provider and must never hold secrets (T-19-30).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Contradiction] Truth text asked `undoAll`'s result to be rendered with an else branch**
- **Found during:** Task 3
- **Issue:** `UndoResult` is a closed sealed class; section 11 says "switch over it exhaustively, with no else" and Kotlin warns on a redundant else. An else on the outer `when` would teach the opposite of the doc.
- **Fix:** exhaustive outer `when` over `UndoResult` with no else; the required else branch is on the inner `when` over `UndoReason` in the `Refused` case.
- **Files modified:** DocSnippetsTest.kt, INTEGRATION.md
- **Committed in:** `7f79ea7`

**2. [Rule 3 - Blocking] `./gradlew :voice-adapter:test --tests ...` is not a valid invocation**
- **Found during:** Task 4
- **Issue:** on an Android library the `test` lifecycle task has no `--tests` option (`Unknown command-line option '--tests'`).
- **Fix:** ran `:voice-adapter:testDebugUnitTest --tests '*DocSnippetAdapterTest*'`, same tests.
- **Committed in:** `4646b16`

**3. [Rule 3 - Blocking] Task 4's verify runs `scripts/verify-docs-coverage.sh --selftest`, which does not exist**
- **Found during:** Task 4
- **Issue:** the script has no `--selftest` mode today (it exits 1 with `usage: unknown argument --selftest`); plan 19-10 adds it ("selftest" in the 19-10 roadmap line). "Keep its --selftest passing" has nothing to keep.
- **Fix:** did not invent a selftest here. Proved the extension non-vacuous by hand instead (two planted controls, see Verification). 19-10 must make its selftest cover the second snippet file (a planted change in the adapter block and a missing adapter test file).
- **Committed in:** n/a (no code change)

### Plan inconsistency noted, not a code change

The plan-level `<verification>` says "section 12 has no Kotlin fence", while PD-07 as amended and Task 4 require exactly one. Task 4's criterion (exactly one) was followed.

---

**Total deviations:** 3 (1 contradiction resolved in favour of the doc contract, 2 invalid commands adapted)
**Impact on plan:** none on scope; no api.txt, build file or invariants file touched.

## Issues Encountered

- Host memory was tight (swap full, about 9 GB available at the end); each Gradle run used the low-memory recipe, single-use daemon, one at a time, and `./gradlew --stop` was never run.
- `Extraction`'s full constructor is internal, which shaped the `matchedLanguage` demonstration (see Decisions).

## Findings for later plans

- 19-10: `API.md` should gain the RT-01 ASCII `\s` note; INTEGRATION.md's plan subsection already points there for "the exact syntax and its limits (step id characters, the 64 character cap, whitespace)", so that sentence reads true only after 19-10 lands.
- 19-10: make the coverage gate's selftest plant a change in the adapter block and remove `DocSnippetAdapterTest.kt`; a region name present in two snippet files would concatenate in `region()` and fail C06 loudly (no separate duplicate-name check was added).
- Docs frozen at the wiring SHA: any later change to INTEGRATION.md voids the wiring pass.

## Next Phase Readiness

Ready for 19-10 (manifest-driven coverage gate, README pin, API.md RT-01, ECOSYSTEM). DOC-02 is not ticked: it needs the isolated wiring test on the final SHA.

## Self-Check: PASSED

Created files exist, the four task commits are in history, and every acceptance criterion and plan-level verification command was re-run green (see Verification).

---
*Phase: 19-sample-gate-1-docs*
*Completed: 2026-10-07*
