---
phase: "15"
slug: planthenexecute-strategy
status: verified
threats_open: 0
asvs_level: 1
audited_head: 707531967f186af4c84af48a2bf4fcec2a7f8932
created: "2026-10-06"
---

# Phase 15 - Security

> Per-phase security contract: threat register, accepted risks, and audit trail. Register authored at plan time (PLAN threat_model blocks of 15-01..15-07). Audit depth: ASVS L1, block_on high. Verified by gsd-security-auditor against the post-review-fix HEAD.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Model answer -> PlanThenExecute | The model's `submit_plan` call is untrusted input to the plan parser, binder and step loop | Step ids, tool names, argument trees (never logged or printed) |
| Plan tier -> write path | Each planned step becomes one proposal through the app gate, commit and sink path | Typed tool arguments |
| Earlier step result -> later step argument | A committed step's target ids are substituted into a later step | App-owned ids, bound as strings |
| Plan tier -> next tier (TierWalk) | A hand-up lets a later tier run the same command | Carry, no remaining step ids |
| Engine -> app outcome | Never-run step ids reach the app as data | `Completed.remainingStepIds` (model-written text) |
| Engine -> model (replan) | A fixed-vocabulary digest goes back to the model | Engine codes and an index only |
| Host -> paid provider APIs | The opt-in live probe sends real requests | Spend-capped test keys, counts-only output |
| Orchestrator relay -> decision file | Only a relayed answer may allow a live call | `decision:` line with relayed_by and date |
| Phase 15 additions -> frozen public API | Internal plan types must not leak into the tagged surface | api.txt, Metalava dump |

---

## Threat Register

Status is closed for every row. Evidence is file:line at the audited HEAD; tests are under core/src/test/kotlin/io/github/ygaray/voiceactionengine/core unless noted.

| Threat ID | Category | Component | Severity | Disposition | Mitigation (evidence) | Status |
|-----------|----------|-----------|----------|-------------|-----------------------|--------|
| T-15-01 | Elevation of privilege | PlanRun step loop | high | mitigate | Each step goes alone through `session.submit` (PlanRun.kt:108-124); only mutating non-terminal tools pass parsePlan (PlanParse.kt:97,125-126); PlanThenExecuteRunTest.kt:66-67 asserts gate.calls == 2 with one mutation each | closed |
| T-15-02 | Tampering | parsePlan | high | mitigate | Pure, safe-cast-only parse (PlanParse.kt:74-137); grep of strategy/plan finds no `!!`, unsafe `as`, `throw` or `error(` beyond the builder `require` (PlanThenExecuteStrategy.kt:88); PlanParseTest.kt:53,65,98,129 pin malformed, empty and direct-call rejection | closed |
| T-15-03 | DoS | plan size | medium | mitigate | `maxSteps` default 8, `require(>= 1)` (PlanThenExecuteStrategy.kt:35,87-89); `steps.size > maxSteps` rejected (PlanParse.kt:88); PlanSchemaTest.kt:154, PlanThenExecuteLimitsTest.kt:90,108,120 | closed |
| T-15-04 | Info disclosure | toString and guard codes | medium | mitigate | PlanStep and ParsedPlan print tool name and counts only (PlanParse.kt:30,36); strategy prints id and limit (PlanThenExecuteStrategy.kt:134, PlanSchemaTest.kt:173-177); trace codes match `[a-z_]+` (PlanThenExecuteRedactionTest.kt:155) | closed |
| T-15-05 | Tampering | AgenticDispatch dedupe | medium | mitigate | Own commit e80fa93 touches AgenticDispatch.kt only; fault bytes defined once (PreparedStep.kt:10, only `tool_error` in main); AgenticDispatch delegates (AgenticDispatch.kt:125-128); AgenticLoopGateTest.kt:39 pins the bytes | closed |
| T-15-06 | DoS | executor throw | low | mitigate | prepareGuarded turns a throw into an error step with a fixed notice and TOOL_PREPARE_ERROR (PreparedStep.kt:19-34); PlanThenExecuteSuppressionTest.kt:129 | closed |
| T-15-SC | Tampering | dependencies | low | accept | No new dependency (RESEARCH package audit); providers/build.gradle.kts adds a Test task only | closed (accepted) |
| T-15-07 | Tampering | forged, forward or self references | high | mitigate | Static earlier-only check before step 1 (PlanParse.kt:129, `declared` set at :112-118); unresolved key returns null, step never prepared (PlanBinding.kt:62-68, PlanRun.kt:99-106); PlanParseTest.kt:148-153, PlanBindingTest.kt:120, PlanThenExecuteBindingTest.kt:151 | closed |
| T-15-08 | Tampering | dictated text that looks like a reference | medium | mitigate | Letter-leading ids, whole-value regex, `matchEntire` (PlanBinding.kt:10,13,19-23); keys and non-strings untouched (PlanBinding.kt:62-84); PlanBindingTest.kt:44,106, PlanParseTest.kt:156 | closed |
| T-15-09 | Elevation of privilege | unknown, terminal or read tools | high | mitigate | unknown_tool and terminal_tool rejected (PlanParse.kt:125-126); read tool gives NeedsLookup before any run (PlanParse.kt:97,133-134); PlanThenExecuteLookupTest.kt:56-80 asserts executor, gate and sink all zero | closed |
| T-15-10 | Tampering | binding from held, preview or errored step | high | mitigate | Results written only when every action is COMMITTED (PlanRun.kt:114-118); loop stops at first non-committed step (PlanRun.kt:119-123); PlanThenExecuteBindingTest.kt:184,199,212 | closed |
| T-15-11 | Info disclosure | codes, reasons, toString | medium | mitigate | Fixed constants (PlanParse.kt:17-25, PlanRun.kt:17-19, PlanThenExecuteStrategy.kt:36-39); rejection carries engine index only (PlanParse.kt:48); canary sweep PlanThenExecuteRedactionTest.kt:127-157 | closed |
| T-15-12 | DoS | oversized or nested plans | low | mitigate | Size cap precedes per-step work (PlanParse.kt:88-89); request carries maxTokensPerTurn (PlanThenExecuteStrategy.kt:126); PlanParseTest.kt:91-95 (see residual R4) | closed |
| T-15-13 | Tampering | replan after a commit | high | mitigate | One predicate `replans < 1 && !worked && ceilingReached == null` (PlanThenExecuteStrategy.kt:280-281, :259,:269); fresh PlanRun per answer (:267); PlanThenExecuteReplanTest.kt:241-254 (one call, no plan_replanned), PlanThenExecuteLimitsTest.kt:277 | closed |
| T-15-14 | Info disclosure | replan digest | medium | mitigate | rejectionDigest built from engine code and index only (PlanReplan.kt:48-53); byte-pinned PlanThenExecuteReplanTest.kt:36,113-140,162-178,204-222 and providers PlanThenExecuteWireTest.kt:67,220-230 on three wires; no canary in digest (PlanThenExecuteRedactionTest.kt:180-200) | closed |
| T-15-15 | DoS | runaway model calls | medium | mitigate | REPLAN_LIMIT = 1 (PlanThenExecuteStrategy.kt:42,281); maxTokens copied to replan (PlanReplan.kt:36); ceiling checks (:93,:237,:281); ReplanTest.kt:181-201 asserts callCount == 2 on a second rejection, :270-280, LimitsTest.kt:175 | closed |
| T-15-16 | Repudiation | unanswered tool calls in continuation | low | mitigate | Every call id answered in order, extras get ignored_call (PlanReplan.kt:29-31); unbuildable continuation returns null and ends MalformedExtraction (PlanReplan.kt:27-28, PlanThenExecuteStrategy.kt:285); ReplanTest.kt:143-159 (see residual R3) | closed |
| T-15-17 | Info disclosure | live probe keys and bodies | high | mitigate | Keys read from env only inside the test (PlanBindingLiveProbeTest.kt:214-217); only PLAN_PROBE codes and counts printed (:87-90,225-227); task opt-in via VAE_LIVE_PLAN, `onlyIf`, never up to date (providers/build.gradle.kts:114-125); `test` and both OkHttp legs exclude `*Live*` (build.gradle.kts:65,156); no live call in 15-03 (commit 8ea1a0d: decision pending) | closed |
| T-15-18 | DoS | probe spend | low | mitigate | Budget checked before each scenario, used + 4 <= 8 (PlanBindingLiveProbeTest.kt:51-54,127-130); post-run ceiling assert (:228); transport retries once at most (AnthropicProvider.kt:86), so worst case 4 per scenario holds | closed |
| T-15-19 | Tampering | double write via a later tier | high | mitigate | TierWalk suppresses any Escalate or NoMatch when coordinator appliedCount + heldCount > 0 (TierWalk.kt:93-101,122,129-137); hold after commit is terminal Completed (PlanRun.kt:47-52); PlanThenExecuteSuppressionTest.kt:107-176 (six failure kinds plus post-commit hold, next.executions == 0, one provider call) | closed |
| T-15-20 | Elevation of privilege | steps after a hold | high | mitigate | Held returns at once, later steps never prepared (PlanRun.kt:119-123); commitHeld outcome has no remaining ids and never resumes (HeldCommit.kt:72); PlanThenExecuteHoldTest.kt:80-96,158-172 (mutations[2].applyCount == 0, executor count 2) | closed |
| T-15-21 | Repudiation | held reported as done, steps hidden | medium | mitigate | Hold ends partial with HELD kind and remaining ids (PlanRun.kt:44-56, PlanThenExecuteStrategy.kt:270); public KDoc says never render as full success (CommandOutcome.kt:95-109); PlanThenExecuteHoldTest.kt:80-155 | closed |
| T-15-22 | DoS | gate fault mid-plan | low | accept | Gate fault is an is_error action; after a commit the plan ends suppressed partial (PlanThenExecuteSuppressionTest.kt:139-153) | closed (accepted) |
| T-15-23 | Tampering | truncated plan acted on | high | mitigate | MAX_TOKENS intercepted before decideResult and parsing on both calls (PlanThenExecuteStrategy.kt:237,245-246); PlanThenExecuteOutcomeMappingTest.kt:335 (truncated plan, executor never called), :362 (truncated replan, no third call) | closed |
| T-15-24 | DoS | unbounded cost | medium | mitigate | ceilingReached before the call (:93), ceilingCrossed after each call and before any step (:237), maxSteps cap, maxTokensPerTurn on both requests (:126, PlanReplan.kt:36); PlanThenExecuteLimitsTest.kt:120-216 | closed |
| T-15-25 | Info disclosure | trace, events, reasons, toString, digest | high | mitigate | Canary sweep over five runs including a post-commit hold (PlanThenExecuteRedactionTest.kt:163,180,204,220,234); non-vacuity: >= 12 distinct values (:130) and canary reaches executor (:172-175); only remainingStepIds hands over a never-run id and its toString prints a count (:253-258) | closed |
| T-15-26 | Repudiation | hook divergence from SingleShot | medium | mitigate | Shared OutcomeHooks and decideResult (PlanThenExecuteStrategy.kt:226-237); 15-case parity matrix PlanThenExecuteOutcomeMappingTest.kt:96-384 including on-device and replan-call failures | closed |
| T-15-27 | Tampering | accidental public API | medium | mitigate | Only top-level public declaration in strategy/plan is PlanThenExecuteStrategy (grep, PlanThenExecuteStrategy.kt:73); explicit API on (core/build.gradle.kts:18); core/api.txt holds no Plan entry; 15-SURFACE-REVIEW records `API SURFACE OK`, +-only diff (two added lines); ApiShapeTest present | closed |
| T-15-28 | Tampering | api.txt drift before the tag | medium | mitigate | `git diff main...HEAD -- '*api.txt'` empty; only c43c65e (v1.0.0 baseline) ever touched api.txt | closed |
| T-15-29 | Repudiation | frozen decision not relayed | low | mitigate | OI-1..OI-7 with default and overturn cost (15-SURFACE-REVIEW.md:171-177); OI-1 and OI-2 recorded as relayed rulings, OI-3 PASS | closed |
| T-15-30 | Info disclosure | review notes | low | accept | Review records engine-owned strings and codes only; key-shape scan of the phase directory is clean | closed (accepted) |
| T-15-31 | Info disclosure | keys in output or records | high | mitigate | `grep -E 'sk-(ant\|proj\|or)-'` on 15-LIVE-PROBE.md returns nothing; wider key-shape scan over phase dir, providers/src, core/src, API.md clean; record holds PLAN_PROBE lines only (15-LIVE-PROBE.md:105-109); harness prints codes and counts only | closed |
| T-15-32 | Elevation of privilege | self-authored approval | high | mitigate | Decision line set only by commit b687551 with relayed_by, date and quoted relayed_answer (15-LIVE-PROBE.md:3-6); later commits 9ac8c5a and bc95c94 only add the result and consume it | closed |
| T-15-33 | DoS | spend beyond approval | medium | mitigate | One run recorded, `consumed: 4 requests (limit 8), one run, exit 0, no retries` (15-LIVE-PROBE.md:9); harness budget guard (PlanBindingLiveProbeTest.kt:127-130) | closed |
| T-15-34 | Tampering | probe failure leading to silent syntax change | medium | mitigate | Plan 15-07 commits bc95c94 and 9ac8c5a touch only 15-LIVE-PROBE.md and 15-SURFACE-REVIEW.md; probe PASS 4/4 so no syntax change was needed; description frozen (PlanSchema.kt:28-37, pinned by PlanSchemaTest.kt:95) | closed |
| T-15-35 | Info disclosure | model-written step ids in remainingStepIds | medium | mitigate | Ids live in an internal field and the public getter only (StrategyOutcome.kt:27,61); toStrings print counts or nothing (CommandOutcome.kt:118-120, StrategyOutcome.kt:43-44,69-70); RemainingStepIdsTest.kt:144-163, PlanThenExecuteRedactionTest.kt:234-259 | closed |
| T-15-36 | Tampering | ids from a handed-up tier attributed to a later tier | low | mitigate | handUp drops ids (TierWalk.kt:104-109); only the ending branches pass them (:95,:100); RemainingStepIdsTest.kt:70-122 | closed |

*Severity: critical > high > medium > low. Only open threats at or above high count toward threats_open.*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-15-01 | T-15-SC | No new dependency in the phase | plan-time disposition | 2026-10-06 |
| AR-15-02 | T-15-22 | A gate fault mid-plan is an is_error action, never a hold; after a commit the plan ends suppressed partial, before one the single replan predicate applies | plan-time disposition | 2026-10-06 |
| AR-15-03 | T-15-30 | The surface review records engine-owned strings and codes only; no keys, transcripts or app data | plan-time disposition | 2026-10-06 |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-10-06 | 37 | 37 | 0 | gsd-security-auditor (verdict SECURED, L1, block_on high) |

Unregistered flags: none from the SUMMARYs (15-01..15-07 carry no Threat Flags section). Review items WR-01 (probe pass condition), WR-02 (carry on prose answer) and WR-03 (preview as failed step, decision OI-6) were fixed or documented in 15-REVIEW-FIX and map to T-15-17/T-15-33, no threat, and T-15-13/T-15-15.

Residual non-blocking notes:
- R1 review IN-04 (skipped, D-04 frozen): `\S` is ASCII-only, so a trailing newline after a reference leaves a literal string reaching the app; an NBSP suffix fails closed as plan_binding_unresolved. Maps to T-15-08.
- R2 review IN-05 (skipped, frozen id grammar): step ids have no length cap and appear verbatim in `remainingStepIds`; the engine prints only the count, an app that logs the list would log model-derived text (KDoc says treat as data). Maps to T-15-35.
- R3 T-15-16: the unbuildable-continuation branch (blank or repeated call id) is in code (PlanReplan.kt:27-28) but has no dedicated test.
- R4 T-15-12: recursion in `referencedStepIds` and `bindArguments` has no explicit depth cap, only the maxTokensPerTurn bound; `guarded` does not catch JVM Errors, and no test uses deeply nested arguments. Theoretical, low.
- R5 T-15-32/T-15-33: the live-probe approval is a process control (decision line plus VAE_LIVE_PLAN opt-in), not enforced by the harness; the WR-01 stricter assertion was compile- and detekt-checked only and the probe is consumed, so any rerun needs a new relayed approval.
- R6 T-15-31: with-test-keys redaction and the global secret-scan hook are external controls not verified here; repo-side evidence is the key-shape scan and counts-only output.

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-10-06
