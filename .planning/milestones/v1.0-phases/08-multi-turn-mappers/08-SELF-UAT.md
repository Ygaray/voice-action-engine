---
status: complete
result: all_pass
gate: 1
phase: 08-multi-turn-mappers
source: [ROADMAP Phase 8 success criteria 1-4]
device: none (headless JVM; pure :core + :providers library phase, no adb, no device, no emulator, no live provider call made by this run)
apk: n/a (no artifact built or installed; HEAD bbf91a4f1e0fb2af1dfc8c63aaaa8b1f41bb860b)
run: 2026-10-01T16:24:00Z @ bbf91a4f1e0fb2af1dfc8c63aaaa8b1f41bb860b
---

<!--
BACKFILLED RECORD. Written on 2026-10-01 by the milestone master (a diagnose-only Gate-1 tester agent) after the
orchestrator flagged that Phase 8 had no SELF-UAT.md and no .planning/uat-pending/ fragment, unlike Phases 1-6.
Why the gap: the Phase 8 execute stage ruled Gate-1 N/A (`phase.has-uat-criteria` = false: JVM-only phase, no UI or
device-verifiable surface) and therefore wrote neither file. This log applies the same headless-JVM handling used for
Phases 1-6 (see 03-SELF-UAT.md). No source, test, config or build file was changed to produce it.
-->

# Self-UAT Log: Phase 8 (Multi-turn Mappers), one log for the phase (backfilled)

**Target:** headless (Gradle JVM test harness: one multi-turn conformance suite bound to Anthropic, OpenAI and OpenRouter over the real providers and a local `MockWebServer`, plus golden conversation files on disk). No adb, no device, no emulator. This run made no live provider call and read no key.
**Build identity:** HEAD `bbf91a4f1e0fb2af1dfc8c63aaaa8b1f41bb860b`. `git status --short core providers config scripts` is empty.
**Pre-flight:** JDK 17, Gradle wrapper 9.4.1. Stale `core/build/test-results/test` and `providers/build/test-results` removed first, then `./gradlew :core:test :providers:test --offline --rerun-tasks --console=plain` -> `BUILD SUCCESSFUL in 1m 8s`, exit 0, `11 actionable tasks: 11 executed`. Every count below was produced by this run.
**Unit suite (JUnit XML, parsed with a script):**

| Result dir | Suites | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|---|
| `core/build/test-results/test` | 55 | 535 | 0 | 0 | 0 |
| `providers/build/test-results/test` (OkHttp 4.12.0 floor) | 48 | 572 | 0 | 0 | 0 |

**Wider gate:** `./gradlew check --offline` (no `--rerun-tasks`; 179 actionable tasks, 32 executed, 2 from cache, 145 up to date) -> `BUILD SUCCESSFUL`, exit 0. Matrix legs produced fresh by this `check`: `testOkhttp521` 48 / 572 / 0 / 0 / 0 and `testOkhttp550` 48 / 572 / 0 / 0 / 0, so the conformance suite is green on OkHttp 4.12.0, 5.2.1 and 5.5.0. `keystore` `testDebugUnitTest` 15 / 96 / 0 / 0 / 0 (up to date). `scripts/review-api-surface.sh --expect-sealed-complete` -> `API SURFACE OK ... classes=177`; `scripts/verify-repo-hygiene.sh` -> `HYGIENE OK`. No `*Live*` class runs in `test` or the matrix legs.
**Seed/fixture integrity:** fixtures are in-tree: `providers/src/test/resources/golden/conversations/` with `MANIFEST.tsv` (11 rows: 6 derived, 5 captured) and one JSON per row; `ConversationScript` supplies prompts, tools and results (domain-free). Nothing external, nothing to restore.
**Prior verdicts audited, not relied on:** `08-VERIFICATION.md`, `evidence/phase-gate.txt` (PHASE GATE: PASS at 36a95f7, providers 555 tests then) and `evidence/live-multiturn-capture.txt`. Coverage below is re-derived from the ROADMAP text by reading test names, the suite source and the manifest. Counts have moved since the gate file (providers 555 -> 572 after the 08-09 capture rows and review fixes), so this log supersedes the gate file's numbers for HEAD.

## Criteria

### 1. SC1: one conformance suite runs against both the Anthropic and the Chat Completions mapper and round-trips multi-turn tool conversations, including parallel calls and empty arguments
result: passed
- **Rung:** 1
- **Target:** headless JVM harness
- **Expected:** a single abstract suite, bound per dialect, round-trips every fixture conversation (encode -> real provider -> decode, turn by turn) with zero violations, covering parallel calls and zero/empty arguments, on Anthropic, OpenAI and OpenRouter.
- **Arranged (seeded):** golden conversations from `MANIFEST.tsv`: Anthropic `derived_parallel_tools`, `derived_thinking`, `captured_a1`, `captured_a2`; OpenAI `derived_parallel_tools`, `derived_empty_args_forms`, `captured_o1`; OpenRouter `derived_parallel_tools`, `derived_reasoning_details`, `captured_r1`, `captured_r3`. Tags include `parallel`, `zero_arg`, `empty_args_forms`, `error_result`.
- **Did (drove):** forced re-run; read `MultiTurnConformanceSuite.kt` and the three subclasses.
- **Observed:** one `internal abstract class MultiTurnConformanceSuite` with a `WireDialect`; `AnthropicMultiTurnConformanceTest` 14/14, `OpenAiMultiTurnConformanceTest` 13/13, `OpenRouterMultiTurnConformanceTest` 11/11 (each test loops over every manifest row of its dialect). `everyFixtureConversationRoundTrips` asserts no replay violations, one request per turn, a final turn with no tool calls, and, for rows tagged `parallel`, that some turn really makes more than one call (so the tag cannot be decorative). `theDialectHasAtLeastOneFixture` stops an empty dialect passing vacuously. Empty-arguments handling is also pinned below the suite: `AnthropicDecoderTest.anAbsentOrNullToolInputDecodesAsAnEmptyObject`, `ChatDecoderTest.emptyArgumentFormsDecodeAsAnEmptyObject`, `theReplayRawKeepsEmptyArgumentsExactlyAsReceived`, `emptyArgumentsFormsDecodeAsEmptyObjectsAndStayAsReceivedInTheStoredTurn`, and `ChatMessageEncoderTest.aReplayIsRepairedOnlyWhereItWouldBeInvalidInput`. The `derived_empty_args_forms` row exercises the empty-string and absent forms end to end. Pre-flight: `ConversationCheckTest` 17/17 (replay key, tool-call coverage, kind-only codes with no id or content in the reason).
- **Evidence:** `providers/build/test-results/test/TEST-...conformance.{Anthropic,OpenAi,OpenRouter}MultiTurnConformanceTest.xml`, `...ConversationCheckTest.xml`, `...AnthropicDecoderTest.xml`, `...ChatDecoderTest.xml`.

### 2. SC2: assistant turns replay verbatim from `NativeReplay` to the same provider and model (golden with thinking blocks survives byte-for-byte); a transcript never crosses providers; `carry` stays semantic only
result: passed
- **Rung:** 1 (plus audited evidence file for the live half)
- **Target:** headless JVM harness
- **Expected:** every assistant turn in the whole history is re-sent byte-for-byte for a matching stamp; a stamp for another provider, model or shape is refused with zero requests; thinking / signed-reasoning goldens replay unchanged; no transcript type travels through `carry`.
- **Arranged (seeded):** the thinking goldens `derived_thinking` (thinking + redacted_thinking, placeholder signatures, proves the engine's replay) and `captured_a2` (a real Sonnet 5.5 response with a 1440-char signature), `derived_reasoning_details` and `captured_r3` (OpenRouter signed `reasoning_details`); stamp variants built in the test.
- **Did (drove):** forced re-run; read `everyAssistantTurnIsReplayedByteForByte`, `aStampForAnotherProviderOrModelFailsBeforeAnyRequest`, the encoder tests and `carry` in `core/src/main`.
- **Observed:** `everyAssistantTurnIsReplayedByteForByte` compares each replayed assistant turn in every request body against the recorded response text over the whole history and passes on all rows including the thinking rows, on all three dialects. `aStampForAnotherProviderOrModelFailsBeforeAnyRequest` asserts `FailureReason.Other("replay_mismatch")` and `requests == 0` for other provider, other model and other shape, while the original stamp and an unstamped copy both succeed with exactly one request. Encoder-level: `AnthropicEncoderTest.aMatchingNativeReplayIsSentVerbatimANullOneIsRebuiltAndAnyOtherIsRefused`, `ChatMessageEncoderTest.aReplayFromAnotherVendorProviderOrModelIsRefusedAndANullOneIsRebuilt`, `aMatchingReplayKeepsOnlyTheAllowedFieldsInStoredOrder`, `theReplayShapeThePreflightAcceptsIsExactlyTheOneTheEncoderAccepts`. **`carry` semantic only:** `carry` is an opaque `Any?` on the strategy/session seam (`RunSession.carry`, `StrategyOutcome.Escalate.carry`, `UserTurn.carry`); no `Message`, `AssistantMessage` or `NativeReplay` appears on it, and the escalation tests (`TierWalkTest`, `SingleShotOutcomeMappingTest.anEscalationFromTheResolverHandsTheSameCarryToTheNextTier`, `noToolCallFailureEscalatesAndTheNextTierRunsOnceWithNoCarry`) show the next tier receiving only that value. **Caveat:** there is no single test named for "carry cannot hold a transcript"; this half rests on the type shape plus the stamp-mismatch refusal (a transcript that did cross would be refused with zero requests). Judged sufficient for a headless record, flagged for the reader. **Live half (audited from `evidence/live-multiturn-capture.txt`, not re-run by me):** the Anthropic A2 run on `claude-sonnet-5-5` returned a signed thinking block, turn 2 replayed it verbatim and the real API answered 200 / `end_turn`; the OpenRouter R3 run (`anthropic/claude-sonnet-5.5`) returned a signed `reasoning_details` entry (signature 1611 chars) that was echoed and accepted, status 200. Both captured goldens replay green in this run.
- **Evidence:** `TEST-...{MultiTurn conformance x3,AnthropicEncoderTest,ChatMessageEncoderTest,ConversationGoldenTest}.xml`; `providers/src/test/resources/golden/conversations/anthropic/{derived_thinking,captured_a2}.json`; `.planning/phases/08-multi-turn-mappers/evidence/live-multiturn-capture.txt`.

### 3. SC3: tool results encoded per dialect (Anthropic: one user message with all of a turn's results plus `is_error`; Chat: one `role:tool` message per call id); golden tests built from recorded, sanitized real response bodies
result: passed
- **Rung:** 1
- **Target:** headless JVM harness
- **Expected:** Anthropic results ride in one user message in call order with `is_error` only on errors; Chat sends exactly one `role:tool` per call id in call order, errors wrapped as `{"error": ...}`; goldens include recorded, sanitized real bodies and pass the hygiene scan.
- **Arranged (seeded):** the same manifest; `ConversationScript.resultFor(call)` supplies deterministic ok / error / empty results.
- **Did (drove):** forced re-run; read `toolResultsAreEncodedPerDialect` and the two encoder tests; listed the manifest.
- **Observed:** `toolResultsAreEncodedPerDialect` asserts, for every request and every earlier assistant turn, `dialect.resultMessageCount(calls.size)` (one batched message on Anthropic, one per call on Chat), result ids in call order, and the error flag and content per call. `AnthropicEncoderTest.aToolResultBatchIsOneUserMessageOfToolResultBlocksInOrder`, `resultsGoOutInTheOrderOfTheCallsTheyAnswerInOneUserMessage`, `isErrorAppearsOnlyOnAnErrorResult`, `anEmptyResultCarriesNoContentKeyAndAnEmptyErrorKeepsIsError`; `ChatMessageEncoderTest.eachToolResultIsItsOwnToolMessageInCallOrderAndAnErrorIsWrapped`, `resultsAnsweredOutOfOrderAreSentInTheOrderOfTheCalls`, `aHeldForConfirmationResultIsSentByteForByteUnchanged`, `anErrorTextWithQuoteBackslashAndNewlineParsesBackToTheOriginal`, `anEmptyResultIsSentAsAnEmptyStringAndAnEmptyErrorIsStillWrapped`. Goldens: `ConversationGoldenTest` 22/22 (`everyManifestRowIsPresentCanonicalHygienicAndWellFormed`, `everyGoldenFileOnDiskHasAManifestRow`, `everyDialectHasADerivedRow`, hygiene refuses keys, bearer values and real ids); `ConversationSanitizerTest` 18/18; `ConversationCaptureRunTest` 21/21 (the recorder is proven key-free and bounded on loopback). The manifest holds 5 captured rows (`captured_a1`, `captured_a2`, `captured_o1`, `captured_r1`, `captured_r3`), all dated 2026-10-01 in the manifest note, so recorded, sanitized real bodies exist on Anthropic, OpenAI and OpenRouter. The live capture itself (14 requests, about USD 0.033 estimated) is audited from the evidence file, not re-run.
- **Gap noted, not a failure:** conversation `R2` (OpenRouter `openai/gpt-oss-120b`, reasoning without a signature) ended UNMET in the live run (the model kept asking for one call per turn through the 3-request cap), so no gpt-oss golden exists. The evidence file says this is model behaviour under the cap, every R2 request was accepted (200), and SC3's real half is met on all three dialects via A1, A2, O1, R1 and R3. Any future decision to capture a gpt-oss golden needs a different model or script; the ROADMAP text does not require that route.
- **Evidence:** `TEST-...{MultiTurn conformance x3,AnthropicEncoderTest,ChatMessageEncoderTest,ConversationGoldenTest,ConversationSanitizerTest,ConversationCaptureRunTest}.xml`; `providers/src/test/resources/golden/conversations/MANIFEST.tsv`; `evidence/live-multiturn-capture.txt`.

### 4. SC4: each dialect applies its own cache directive (Anthropic: single system breakpoint; Chat: automatic caching), and the cached prefix bytes stay identical on every iteration of a multi-turn conversation
result: passed
- **Rung:** 1 (plus audited evidence file for the live cache hit)
- **Target:** headless JVM harness
- **Expected:** for every consecutive request pair in a conversation, the earlier body's prefix is untouched (only appends); the dialect's directive is exactly its own (one `cache_control` breakpoint on Anthropic's last system block; none on Chat); the check has teeth (a rewrite is detected).
- **Arranged (seeded):** every manifest row's replayed request bodies; mutated copies (edited earlier user message, rewritten earlier assistant turn, changed tool description) as negative controls; a stray `cache_control` injected into each body as a directive negative control.
- **Did (drove):** forced re-run; read the suite and the Anthropic encoder cache tests.
- **Observed:** `everyIterationOnlyAppendsToTheCachedPrefix` asserts `appendOnlyViolation(previous, next) == null` for each consecutive pair on every row. `aRewriteOfAnEarlierTurnOrOfTheToolsFailsTheAppendOnlyCheck` first proves the unchanged-history control appends cleanly, then asserts a violation is reported for each of the three mutations, so the check is not vacuous. `theCacheDirectiveIsTheDialectsOwnOnEveryIteration` asserts `cacheDirectiveViolation(body) == null` for every request and a non-null violation when a stray directive is injected. `AnthropicEncoderTest.theOnlyBreakpointSitsOnTheLastSystemBlock`, `aMovingConversationTailAddsNoSecondBreakpoint`, `encodingTheSameRequestTwiceOrAnEqualOneGivesIdenticalBytes`, `languageDateAndTranscriptNeverTouchTheBytesBeforeTheMessages`; the reshape (forced tool choice plus instruction line) is pinned as single-turn only per COVERAGE.md and the Phase 9 carry-forward (loop must use `ToolChoice.Auto`). **Live corroboration (audited from `evidence/live-multiturn-capture.txt`, not re-run by me):** A1 on `claude-haiku-4-5` showed `cache_write:6754` on turn 1 and `cache_read:6754` on turn 2. Chat-side automatic caching was seen only incidentally (OpenRouter R2 turn 1 `cache_read:64`); this phase does not assert a Chat cache hit, only that the prefix bytes are stable and no directive is sent.
- **Evidence:** `TEST-...{MultiTurn conformance x3,AnthropicEncoderTest}.xml`; `evidence/live-multiturn-capture.txt`.

## Summary

total: 4
passed: 4
partial: 0
failed: 0
infra: 0

## Notes / anomalies (for the Gate-2 reviewer)

- **Why this log is a backfill:** the Phase 8 execute stage recorded Gate-1 as N/A (`has-uat-criteria` = false), so no SELF-UAT.md and no pending fragment were written. The orchestrator flagged the gap; the milestone master had this headless record produced on 2026-10-01 so the ledger matches Phases 1-6.
- **Not covered here (belongs to Phase 10, VER-02 / VER-03):** everything that needs a real provider or a device. Phase 10's VER-03 is now extended (10-CONTEXT.md Runtime Decisions) to include Chat/OpenRouter multi-turn on device, so the real-API behaviour of multi-turn replay on OpenAI and OpenRouter (and Anthropic) is re-proved there on the TESTER. Phase 8's own in-phase live capture (08-09, `with-test-keys`, bounded) is audited here from its evidence file only; this run made no provider call.
- **Live-capture anomaly carried forward:** the Chat live task ended RED by design because R2 (gpt-oss) was UNMET; see SC3. No code gap was raised by any finding (`index`, `reasoning_details`, `annotations` echoes were all accepted).
- **Carry-forwards to Phase 9 (from the gate file):** the loop uses `ToolChoice.Auto` (reshape is single-turn only); strict-eligible OpenAI tools make a loop one call per turn; duplicate tool-call ids across turns are Phase 9 whole-turn validation; optional OpenRouter `session_id` is deferred.
- Counts changed after the gate file: providers 555 -> 572 tests per leg; the exact cause was not bisected here (the 08-09 capture added 5 manifest rows, each multiplied by the suite's tests, and review fixes followed).
- Minor test-message nit, not a defect: in `everyFixtureConversationRoundTrips` the assertion message reads "the last turn asks for tool calls" while the assertion requires the last turn to have none. The assertion is correct.
- Highest rung used: 1 for all four criteria. No visual rung applies; the phase has no UI. No device was touched.
- No source edits, no tags. `api.txt` is still not committed; it is created at the v1.0.0 cut in Phase 11. Pre-existing dirty files under `.planning/` (graphs, config.json, v1.0-MILESTONE-RUN.md, state.json, stage markers, `.gsd/`, intel) belong to the orchestrator and were not touched.

## Findings routed to gap-closure (if any)

None.

## Verdict

All 4 criteria PASS on the headless JVM harness. Gate-1 (headless) complete; Gate-2 registered as a ledger-completeness fragment (`.planning/uat-pending/08-multi-turn-mappers.md`). Nothing physical or device-bound is deferred from this phase; the real-provider and on-device halves are carried by Phase 10.
