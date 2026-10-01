---
phase: 08-multi-turn-mappers
plan: 08
subsystem: providers-test-live-capture
status: complete
tags: [live-capture, conversation-recorder, request-ceilings, sanitizer, replay-recheck, phase-gate]

requires:
  - phase: 08-multi-turn-mappers
    provides: "08-04 ConversationSanitizer and golden infrastructure, 08-05..08-07 WireDialect bindings, replayConversation and the conformance suite"
provides:
  - "ConversationPlans: the seven planned conversations, the allowed model list, the ceilings (6 / 13 / 4 / 9 / 19), selected() and violations()"
  - "ConversationRecorder(keys, rawDir, goldenDir, baseUrlFor): run(plans), requests, perVendor(), unmet, findings"
  - "ConversationCaptureRunTest: 20 key-free loopback tests of the recorder"
  - "boundedMultiTurnCapture on AnthropicLiveCaptureTest and ChatCompletionsLiveCaptureTest, behind a second opt-in variable"
  - "vae.golden.conversations.dir on both live Gradle tasks and vae.raw.dir on liveAnthropicCapture"
  - "evidence/phase-gate.txt: PHASE GATE: PASS"
affects: [08-09]

plan_head_before: 23162e1b52c23b4c26c6335b3a0f6c8f3ecae719
commits: 3

actuals:
  tokens: 60000
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "a request is counted before it is sent and never retried; a conversation whose worst case could pass a ceiling is skipped, not started"
    - "one ConversationSanitizer per conversation, then replayConversation on the sanitized copy before the golden file is written"
    - "printed output is limited to LIVE_CAPTURE and MANIFEST_ROW lines; request lines carry conv=, every other LIVE_CAPTURE line does not, so requests can be counted by counting conv= lines"

key-files:
  created:
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationCapture.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationCaptureRunTest.kt
    - .planning/phases/08-multi-turn-mappers/evidence/phase-gate.txt
  modified:
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicLiveCaptureTest.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatCompletionsLiveCaptureTest.kt
    - providers/build.gradle.kts

key-decisions:
  - "The recorder reuses the existing opt-in tasks; no Gradle task is added and neither live class loses its Live name, so test and both matrix legs still exclude them"
  - "The single-turn tests skip when the multi-turn variable is 1, so one invocation runs exactly one plan"
  - "Dialect, per-vendor and phase ceilings are all enforced in the recorder from actual counts (requests already sent plus the next conversation's maximum), not from the plan's nominal sums"
  - "The echo probe (OP) is a one-request finding: accepted or rejected with the status, raw copy only, never a golden"

requirements-completed: [XCR-02, XCR-03]

coverage:
  - id: D1
    description: "The live plan is bounded in code: A1 Haiku (3), A2 Sonnet thinking (3), O1 (3), OP (1), R1 (3), R2 (3), R3 (3) is 19 requests at most (Anthropic 6, OpenAI 4, OpenRouter 9); every model is on the allowed list; OP needs O1 planned first"
    requirement: "XCR-03"
    verification:
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationCaptureRunTest.kt#theStatedPlansSumToTheStatedCeilings"
        status: pass
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationCaptureRunTest.kt#everyPlannedModelIsOnTheAllowedList"
        status: pass
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationCaptureRunTest.kt#aPlanThatBreaksARuleIsAViolation"
        status: pass
    human_judgment: false
  - id: D2
    description: "Requests are counted before they are sent, a conversation that could pass a dialect or vendor ceiling is skipped without sending, and an unfinished, refused or truncated conversation writes no golden"
    requirement: "XCR-03"
    verification:
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationCaptureRunTest.kt#aConversationThatWouldPassTheDialectCeilingIsSkippedWithoutSending"
        status: pass
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationCaptureRunTest.kt#aConversationThatWouldPassTheVendorCeilingIsSkippedWithoutSending"
        status: pass
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationCaptureRunTest.kt#aConversationThatKeepsAskingForCallsStopsAtItsRequestLimitAndWritesNoGolden"
        status: pass
    human_judgment: false
  - id: D3
    description: "A completed Anthropic and Chat conversation is sanitized with one id map, keeps thinking text and signatures byte-identical, replays through replayConversation, and yields a MANIFEST_ROW with provenance captured; raw bodies exist only under the raw directory"
    requirement: "XCR-03"
    verification:
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationCaptureRunTest.kt#anAnthropicConversationIsRecordedSanitizedAndReplayed"
        status: pass
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationCaptureRunTest.kt#anOpenAiConversationIsRecordedSanitizedAndReplayed"
        status: pass
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationCaptureRunTest.kt#anOpenRouterConversationUsesItsOwnVendorAndIds"
        status: pass
    human_judgment: false
  - id: D4
    description: "The recorder prints only LIVE_CAPTURE and MANIFEST_ROW lines carrying counts, statuses and flags; a key-shaped string inside thinking refuses the whole conversation; the echo probe sends one request and writes no golden"
    requirement: "XCR-03"
    verification:
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationCaptureRunTest.kt#theRecorderPrintsOnlyCountsStatusesAndFlags"
        status: pass
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationCaptureRunTest.kt#aKeyShapedStringInsideThinkingRefusesTheWholeConversation"
        status: pass
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationCaptureRunTest.kt#theEchoProbeSendsTheUnfilteredStoredMessageOnceAndWritesNoGolden"
        status: pass
    human_judgment: false
  - id: D5
    description: "check stays key-free: both live tasks report SKIPPED without their opt-in variables, no Live class runs under test or the matrix legs, and no task was added"
    requirement: "XCR-03"
    verification:
      - kind: build
        ref: "env -u VAE_LIVE_ANTHROPIC -u VAE_LIVE_CHAT -u VAE_LIVE_ANTHROPIC_MULTITURN -u VAE_LIVE_CHAT_MULTITURN ./gradlew :providers:liveAnthropicCapture :providers:liveChatCompletionsCapture --offline"
        status: pass
    human_judgment: false
  - id: D6
    description: "Phase gate on the merged tree: check, review-api-surface, verify-repo-hygiene and verify-negative-controls all exit 0; public API surface unchanged; requirement coverage recorded"
    requirement: "XCR-02"
    verification:
      - kind: build
        ref: ".planning/phases/08-multi-turn-mappers/evidence/phase-gate.txt#PHASE GATE: PASS"
        status: pass
    human_judgment: false
---

# Phase 8 Plan 08: Live conversation recorder and phase gate Summary

A bounded multi-turn recorder (19 requests at most, counted before each is sent, never retried) now extends the two existing opt-in capture tasks, is proven key-free on loopback, and the Phase 8 gate is green with no public API change. No live request was made and no key was read.

## What was built

- `ConversationCapture.kt` holds `ConversationPlan`, `ConversationPlans` and `ConversationRecorder`.
- Per turn the recorder builds the request from `ConversationScript`, encodes it with the production encoder and the real key, counts it, posts it once, and writes the raw request messages and raw response under the raw directory (never headers).
- A completed conversation is sanitized whole by one `ConversationSanitizer`, its copy is replayed through `replayConversation`, and only then is `golden/conversations/<dialect>/captured_<code>.json` written and the `MANIFEST_ROW` printed.
- The two live classes gained `boundedMultiTurnCapture`, gated by the existing opt-in variable plus `VAE_LIVE_ANTHROPIC_MULTITURN` or `VAE_LIVE_CHAT_MULTITURN`. The single-turn tests skip when the multi-turn variable is 1.

## Conversation plan (code, model, request cap)

| Code | Dialect | Model | Cap | Notes |
|---|---|---|---|---|
| A1 | anthropic | claude-haiku-4-5 | 3 | long cached system text |
| A2 | anthropic | claude-sonnet-5-5 | 3 | must show a thinking block; thinking token limit |
| O1 | openai | gpt-5.4-mini | 3 | |
| OP | openai | gpt-5.4-mini | 1 | echo probe of O1's stored first turn; needs O1 first |
| R1 | openrouter | openai/gpt-5.4-mini | 3 | |
| R2 | openrouter | openai/gpt-oss-120b | 3 | thinking token limit |
| R3 | openrouter | anthropic/claude-sonnet-5.5 | 3 | thinking token limit |

Ceilings (all in `ConversationPlans`, all asserted by `ConversationCaptureRunTest`): Anthropic 6, Chat 13 (OpenAI 4, OpenRouter 9), phase 19. The two Sonnet 5.5 legs (A2, R3) are the stated cost deviation from "cheapest model" and stay under about USD 0.20 together with the rest.

## Exact run commands for 08-09 (not run here)

- Anthropic: `with-test-keys --only anthropic -- env VAE_LIVE_ANTHROPIC=1 VAE_LIVE_ANTHROPIC_MULTITURN=1 VAE_LIVE_ANTHROPIC_CONVERSATIONS=A1,A2 ./gradlew :providers:liveAnthropicCapture --offline --no-daemon --console=plain`
- Chat: `with-test-keys --only openai,openrouter -- env VAE_LIVE_CHAT=1 VAE_LIVE_CHAT_MULTITURN=1 VAE_LIVE_CHAT_CONVERSATIONS=O1,OP,R1,R2,R3 ./gradlew :providers:liveChatCompletionsCapture --offline --no-daemon --console=plain`

Output to expect: one `LIVE_CAPTURE conv=<code> turn=<k> status=...` line per request (so request count = number of `conv=` lines), `LIVE_CAPTURE finding=...` lines for observations, a `MANIFEST_ROW` line per accepted conversation, and a closing `LIVE_CAPTURE requests=<n> ceiling=<c>` line. The golden files land in `providers/src/test/resources/golden/conversations/`; raw bodies in `providers/build/live-anthropic/raw/` and `providers/build/live-chat/raw/conversations/`. The manifest rows still have to be added by hand in 08-09, because the recorder deliberately does not edit `MANIFEST.tsv`.

## Phase gate result

`PHASE GATE: PASS` (see `evidence/phase-gate.txt`): `./gradlew check --offline` exit 0, `scripts/review-api-surface.sh --expect-sealed-complete` exit 0 (API surface unchanged), `scripts/verify-repo-hygiene.sh` exit 0, `scripts/verify-negative-controls.sh` exit 0. Test counts after a clean re-run: core 535, providers 555 on each of the three OkHttp legs, keystore 96, zero failures.

## Deviations from Plan

**1. [Rule 2 - Missing critical functionality] Replay re-check runs before the golden is written**
- Found during: Task 1.
- Issue: the plan lists "write the golden, print the row, then replay and report a violation as unmet". That order would leave a non-replaying file in the committed resources directory when the replay fails, which is exactly the risk the replay re-check exists to remove.
- Fix: replay first; on a violation nothing is written or printed and the conversation is unmet. The raw copy under `build/` is kept for diagnosis.
- Files: `ConversationCapture.kt`. Verification: all 20 loopback tests green. Commit: cf2a480.

**2. [Rule 2 - Missing critical functionality] A turn that stops at the token limit is unmet**
- Found during: Task 1.
- Issue: the plan did not say what to do with a `max_tokens` stop. A truncated tool-use turn would produce a golden with cut-off arguments.
- Fix: such a turn ends the conversation as unmet ("truncated at the token limit") with no golden. Covered by `aTruncatedTurnIsUnmetAndWritesNoGolden`. Commit: cf2a480.

**3. [Rule 2 - Missing critical functionality] A2 without a thinking block writes no golden**
- Found during: Task 1.
- Issue: the plan says A2 without a thinking block is unmet, but a complete thinking-less file named `captured_a2` could be committed by mistake.
- Fix: A2 skips the golden step when turn 1 had no thinking block; the raw copy stays. Covered by `aThinkingConversationWithoutAThinkingBlockIsUnmetAndWritesNoGolden`. Commit: cf2a480.

**Total deviations:** 3 auto-added (all Rule 2, all stricter than the plan, none changing a planned behaviour). **Impact:** none on scope; every planned behaviour bullet still has its test.

## Issues Encountered

`scripts/verify-negative-controls.sh` leaves failing JUnit XML behind (it forces the OkHttp guard red on purpose), so test counts were taken from a clean re-run after it, as in the Phase 7 gate. That re-run (`--rerun-tasks`) left out the sample and keystore lint tasks for time; the first full `check` ran them green.

## Authentication Gates

None. No key was needed, read or created.

## Next

Ready for 08-09 (the approved, opt-in live run and the commit of its goldens). The live run is the only step that needs keys.

## Self-Check: PASSED

- All six files named under key-files exist on the worktree branch.
- Commits cf2a480, 36a95f7 and 035bc1f exist; `commits: 3` is measured from `plan_head_before` with `git rev-list --count`.
- `git diff --stat` against `plan_head_before` for `providers/src/main`, `core/` and `providers/src/test/resources` prints nothing; no api.txt and no tag exist.
