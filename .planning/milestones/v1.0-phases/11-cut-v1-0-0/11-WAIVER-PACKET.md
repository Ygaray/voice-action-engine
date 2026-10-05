# voice-action-engine v1.0.0 - pre-cut waiver packet (Phase 11, plan 11-01)

Date: 2026-10-02 (UTC). HEAD when written: `ff4ffab` (docs only on top of `e2aa3e1`).

Purpose: collect a Yahir waiver for every uat-pending item that cannot be exercised in v1.0, plus the accepted-by-evidence item, the Gate-2 carries and the one pre-freeze API confirmation, so Yahir answers once. Routed through `yahir-gsd-control-plane-f2` by the milestone master. The master's earlier packet (`.planning/cross-repo/v1.0-waiver-packet.md`, committed at `aae93b3`) is the input; this packet reconciles against it and does not rewrite it.

## 1. Contract reading (what is and is not a tag precondition)

Read from `CROSS-REPO-SCOPE-CONTRACT.md`, section 11 and amendments A12 and A14:

- Rule 1 (`CROSS-REPO-SCOPE-CONTRACT.md:271`): before a tag, "the milestone's verification is green (Gate-1 where device-verifiable; full unit suite; detekt clean)". Gate-1 is the agentic tester's self-UAT. Human Gate-2 is not named.
- Rule 4 (`CROSS-REPO-SCOPE-CONTRACT.md:274`): the tagged commit is pushed and JitPack builds it successfully; resolving the coordinates from a clean Gradle cache counts. A tag whose JitPack build fails is not cut.
- Rule 5 (`CROSS-REPO-SCOPE-CONTRACT.md:277`): replaced by A14. After the cut, the full row is messaged to the orchestrator, who commits the ledger.
- Rule 6 (`CROSS-REPO-SCOPE-CONTRACT.md:278`): tags are immutable. A defect gets a new patch tag and a ledger row marking the old one superseded.
- A12 (`CROSS-REPO-SCOPE-CONTRACT.md:210`): all tag cuts are waived across the whole effort; each repo's session cuts its tag on green verification and the agents own tag correctness.
- A14 (`CROSS-REPO-SCOPE-CONTRACT.md:237`): the control plane is the orchestrator and the sole section 11 ledger writer.

Conclusion: human Gate-2 UAT is NOT a section 11 tag precondition. It runs at milestone close, after the tag (`gsd-verify-milestone` drains `.planning/uat-pending/`). This pre-cut packet exists because of the orchestrator's 2026-10-01 runtime decision ("P11 waiver packet"): items that cannot be exercised in v1.0 need a Yahir waiver on record before the cut, and the pre-freeze row needs an answer before the public API freezes.

The committed cross-repo packet says the tag cut is "held until every pending human-UAT item is answered". That is stricter than the reading above. The release gate in plan 11-04 (gate waiver) requires the answer block below to reach `packet_status: accepted` either way, so both readings are satisfied.

**ORCHESTRATOR RULING (yahir-gsd-control-plane-f2, 2026-10-01, overrides the reading above for this cut): the tag cut HOLDS until EVERY uat-pending item for phases 01 to 10 is resolved or waived by Yahir (xrepo shows TAG-GATE-OPEN).** Yahir's answers to this packet are therefore a HARD tag precondition, even where section 11 alone would not require Gate-2 before the tag. The release gates (11-04 gate waiver and gate prefreeze, 11-08 preflight) enforce `packet_status: accepted` with no pending row. Note for the relay: W13 (the three wiring doc stumbles) is labeled WAIVE in the published cross-repo packet; it is already satisfied by being fixed in Phase 11 (plan 11-03) and re-proven by the isolated wiring rerun (11-06), so it needs no waiver once that rerun passes.

## 2. Items

Categories (exactly one per row): A = cannot be exercised in v1.0 (waiver needed before the cut). AE = accepted by evidence (exercised, inconclusive; NOT a pass). B = Gate-2 carry (Yahir can exercise it; runs at milestone close after the tag). C = PRE-FREEZE API confirmation (must be answered ok, accept or waive before the `api.txt` baseline in 11-07; needs-fix changes the public API and loops the phase). D = resolved inside Phase 11 (informational).

| ID | Category | Source | What | Why not exercised (or why Gate-2) | Evidence | Asked of Yahir | Proposed answer |
|---|---|---|---|---|---|---|---|
| W01 | A | C4; uat-pending 05 item 3(c); Phase 10 G1-11 (L6) | OpenRouter cache-write accounting via the router (`prompt_tokens` includes `cache_write_tokens`) | The engine sends no `cache_control` to OpenRouter in v1.0 (LATER-02), so a cache write cannot occur. Only `turn2_cache_read=0` was observed. Not closed, not a pass. | `.planning/phases/10-sample-harness-gate-1-docs/evidence/gate1-multi_openrouter.txt`, `.planning/phases/10-sample-harness-gate-1-docs/evidence/gate2-carry-register.txt` (C4) | Waive for v1.0 (the work is LATER-02)? | waive |
| W02 | AE | C5; uat-pending 05 item 3(a); Phase 10 G1-09 (L4) | OpenRouter EDIT-shaped call omitting optional arguments: INCONCLUSIVE(`model_filled_optional`) on both runs. ACCEPTED BY EVIDENCE, NOT a PASS. | The routed model filled every optional, so the omission could not be observed on OpenRouter. Accepted by the orchestrator 2026-10-01: the shared chat decoder passed the live OpenAI omitted-optional check (`optional_absent=true`), and host tests prove no default-filling. | `.planning/phases/10-sample-harness-gate-1-docs/evidence/gate1-smoke_openrouter.txt`, `.planning/phases/10-sample-harness-gate-1-docs/evidence/gate1-smoke_openai.txt`, `providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatAbsentOptionalTest.kt`, `providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatGoldenReplayTest.kt` | Confirm the acceptance by evidence (it stays recorded as NOT a pass), or ask for a rerun of L4 on a different OpenRouter model? | accept |
| W03 | A | C1; uat-pending 04 item 3(a); uat-pending 05 item 3(d); Phase 10 carry | Low-credit or spend-exhausted rejection maps to `FailureReason.Billing` on a live account (Anthropic, OpenAI insufficient quota, OpenRouter 402) | Cannot be triggered without draining the account credit. Covered by unit tests (`AnthropicErrorMapTest`, `ChatErrorMapTest`). Residual risk: real provider wording differs and arrives as `HttpError` instead of `Billing`, still a loud typed failure. | `.planning/phases/10-sample-harness-gate-1-docs/evidence/gate2-carry-register.txt` (C1), `providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicErrorMapTest.kt`, `providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatErrorMapTest.kt` | Waive the live check (unit-tested only)? | waive |
| W04 | B | C3; uat-pending 05 item 3(b); Phase 10 L7 | Real OpenAI Responses-only 400 (model gpt-6-astra): does the body text match `RESPONSES_ENDPOINT_MARKER`? | L7 captured `http=400 reason=http_error` and billed nothing, but the body text is not visible on the device (LE-7), so the marker match is unverified; the generic `http_error` path fired. Exercisable by Yahir at milestone close. | `.planning/phases/10-sample-harness-gate-1-docs/evidence/gate1-responses_probe.txt`, `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatErrors.kt` (marker at line 42, match at line 154) | Accept that a generic `HttpError` is fine for v1.0, or carry a host-side wording check to Gate-2? | carry-to-gate-2 |
| W05 | C | uat-pending 05 item 4b; review IN-03 | The public `@JvmInline value class` attempt kind `ChatCompletionsAttemptKind` (internal constructor, constants `INITIAL` and `TRANSIENT_RETRY`, open vocabulary): freeze this representation forever? | Not a test: an API-shape decision. A public value class fixes the underlying `String` forever and mangles the Java accessor names. It mirrors `AnthropicAttemptKind` and the house open-vocabulary pattern (`ProviderId`); changing one transport and not the other would diverge them. Plan 11-02's review keeps it as is. | `.planning/phases/05-openai-openrouter-transports/05-REVIEW-FIX.md` (IN-03), `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatCompletionsAttemptObserver.kt` (lines 60-78) | PRE-FREEZE: reply ok, accept or waive to freeze it in the first `api.txt`, or needs-fix to change it before the baseline? | ok |
| W06 | A | uat-pending 06 note | No single device test does `ApiKeyStore.save` followed by `KeystoreCredentialSource.credential` == `Present` | Hardening opportunity, not a defect: both halves are hardware-proven separately on the TESTER and the composite is JVM-proven in `KeystorePipelineTest`. Deferred to a later patch. | `.planning/phases/06-keystore/06-07-SELF-UAT.md`, `.planning/phases/06-keystore/evidence/keystore-instrumented-run-gate1.txt`, `keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/KeystorePipelineTest.kt` | Waive (defer the composite device test)? | waive |
| W07 | A | uat-pending 07 note | CalTracker confirm scenarios S1-S10 ran against CT-shaped fakes, not CT's own resolver | Real consumer flows belong to the SecondBrain and CalTracker migrations (post-tag), not to v1.0. | `.planning/phases/07-singleshot-strategy/07-SELF-UAT.md` | Waive (proven in the consumer migrations)? | waive |
| W08 | A | uat-pending 08 note | Live OpenRouter conversation R2 (gpt-oss-120b) ended UNMET under the 3-request cap, so no gpt-oss golden exists | Known gap, not a failure: every R2 request returned 200; the real half of SC3 is met on A1, A2, O1, R1 and R3. | `.planning/phases/08-multi-turn-mappers/evidence/live-multiturn-capture.txt`, `.planning/phases/08-multi-turn-mappers/08-SELF-UAT.md` | Waive (known gap)? | waive |
| W09 | A | uat-pending 09 item 4 | Real-model parallel tool calls, live | Not exercised live in Phase 10. Parallel calls are proven on fixtures (`MultiTurnConformanceSuite`, `AgenticLoopWireTest` on all three OkHttp legs). | `.planning/phases/09-agentic-loop-strategy/09-SELF-UAT.md`, `.planning/phases/09-agentic-loop-strategy/evidence/mandate-coverage.txt` | Waive (fixture-proven only)? | waive |
| W10 | A | uat-pending 09 note (CLN-02) | The CLN-02 scanner is a deny-list plus a pattern rule, so it cannot catch an unlisted app-domain name | Known limitation, documented; no better mechanical check exists for v1.0. | `.planning/phases/09-agentic-loop-strategy/09-SELF-UAT.md` | Waive (known limitation)? | waive |
| W11 | A | C7; Phase 10 carry register | Deferred live legs | Not applicable: the live-leg decision on record is approved and the legs ran (G1-05..G1-11 have results). | `.planning/phases/10-sample-harness-gate-1-docs/evidence/gate2-carry-register.txt` (C7), `.planning/phases/10-sample-harness-gate-1-docs/10-LIVE-LEG-DECISION.md` | Waive (nothing to check)? | waive |
| W12 | A | uat-pending 10 note | Gate-1 harness tooling defects: `scripts/run-sample-gate1.sh` `package_installed` returns 141 under pipefail; runbook G1-01 step order | Dev-harness only; no shipped artifact is affected and none changes a criterion verdict. Routed to gap-closure. | `.planning/phases/10-sample-harness-gate-1-docs/10-SELF-UAT.md` ("Tooling defects") | Waive (dev harness only)? | waive |
| W13 | D | uat-pending 10 note; wiring rerun | Three minor documentation stumbles from the isolated wiring rerun | Resolved inside Phase 11: fixed in plan 11-03 and re-proven by the isolated wiring rerun in plan 11-06 (which is required anyway after any doc or API change). | `.planning/phases/10-sample-harness-gate-1-docs/evidence/wiring-rerun-stumbles.txt`, `.planning/phases/10-sample-harness-gate-1-docs/10-WIRING-TEST.md` | Informational; reply ok | ok |

Beyond the starting set: nothing was added. The fragments hold no further unexercised item. C2 (key charset) and C6 (`disable_parallel_tool_use`) are already satisfied by live 200s and host goldens, so they are not rows. The pre-dump API items decided in plan 11-02 (the `ToolSpec` constructor shape and the public keystore cause-code constants) are the orchestrator's directed decisions, not Yahir waivers; their final shape is fixed in 11-02 and frozen with the baseline in 11-07.

## 3. Reconciliation with the committed cross-repo packet (`aae93b3`)

Every line whose classification changes:

| Committed packet line | Becomes | Reason |
|---|---|---|
| Phase 04: "Accept the two function-level `@Suppress` (`AnthropicTransport.notify`, `CallAwait.onResponse`)" (Gate-2) | already satisfied, no row | Stale. Commit `a6def62` removed both suppressions; `AnthropicTransport.kt` and `CallAwait.kt` carry none today. Proof: `.planning/phases/11-cut-v1-0-0/evidence/preconditions-audit.txt` section 3. |
| Phase 10: three wiring doc stumbles (WAIVE) | W13, category D | Fixed in Phase 11 (plan 11-03) and re-proven by the isolated wiring rerun (plan 11-06), rather than left as a waiver. |
| Phase 05 item 4b (Gate-2) | W05, category C | Becomes a pre-freeze row: it must be answered before the `api.txt` baseline, not at milestone close, because the API freezes first. |
| C3 (Gate-2, three lines) | W04, category B | Unchanged in substance; now an explicit Gate-2 carry that runs at milestone close. |

Duplicates collapsed to one W-id each (committed packet lines to W-id):

- C4: explicit item, Phase 05 item 3(c), Phase 10 C4 line -> W01.
- C5: explicit item, Phase 05 item 3(a), Phase 10 G1-09 / C5 line -> W02.
- C1: explicit item, Phase 04 live low-credit line, Phase 05 item 3(d) Billing line, Phase 10 C1 line -> W03.
- C3: explicit item, Phase 05 item 3(b), Phase 10 C3 line -> W04.
- Phase 05 item 4b (also repeated in "What Yahir needs to do") -> W05.
- Phase 06 composite device test -> W06. Phase 07 CT scenarios -> W07. Phase 08 R2 gpt-oss -> W08. Phase 09 live parallel tool calls -> W09. Phase 09 CLN-02 deny-list -> W10. Phase 10 C7 -> W11. Phase 10 tooling defects -> W12. Phase 10 three doc stumbles -> W13.

Lines whose classification is unchanged: the 58 `already-satisfied` lines in the committed packet (phases 01 to 10, including C2 and C6) are unchanged and are not repeated here.

## 4. Answer block

Reply per row with: waive, accept, carry-to-gate-2, ok or needs-fix. A category C row accepts only ok, accept, waive or needs-fix. Rows are only ever filled from relayed answers, never inferred.

## Answer block
packet_status: accepted
answered_by: Yahir (relayed by yahir-gsd-control-plane-f2 via the milestone master; all rows by=Yahir, "all as proposed", relayed 2026-10-02)
answered_at: 2026-10-02T17:33:27Z
answers:
- W01: waive
- W02: accept
- W03: waive
- W04: carry-to-gate-2
- W05: ok
- W06: waive
- W07: waive
- W08: waive
- W09: waive
- W10: waive
- W11: waive
- W12: waive
- W13: ok

Relay note (verbatim): W04=carry means carry-to-gate-2 (the milestone-close Gate-2). W02 stays ACCEPTED BY EVIDENCE, NOT a PASS. Source: p11-waiver-answer.txt, rows stamped by=Yahir at=2026-10-02T17:33:27Z.

### Rules for the answer block

- `packet_status` is one of: pending (nothing answered); partial (some rows answered, none needs-fix, others still pending; answers may arrive in more than one relay, pre-freeze rows first); accepted (every row answered with waive, accept, carry-to-gate-2 or ok, and every category C row with ok, accept or waive); rejected (any row answered needs-fix).
- The `api.txt` baseline (plan 11-07, gate prefreeze) waits for every category C row to be answered ok, accept or waive. The cut (plan 11-08, gate waiver) waits for `packet_status: accepted`.
- A needs-fix on a category C row changes the public API, so it voids the wiring pass: loop plan 11-02, then the plan 11-03 docs gate, then a new wiring SHA and isolated rerun in plan 11-06, then plan 11-07. carry-to-gate-2 is not a valid answer for a category C row, because the API freezes before Gate-2.
- A needs-fix on any other row stops the phase for the orchestrator.
