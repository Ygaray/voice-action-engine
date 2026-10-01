---
phase: "7"
slug: singleshot-strategy
status: secured
threats_open: 0
asvs_level: 1
audited_head: c405245c9f84ddd8229d83c1f0daf26079af9be4
created: "2026-10-01"
---

# Phase 7 - Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| app -> SingleShot seams | The app supplies ToolSpecProvider, OutcomeResolver and UserTurnRenderer; the engine supplies the transcript | transcript, system text, tool schemas, tool arguments, opaque context |
| strategy -> provider transport | One request per command with the single-tool-call flag | request body (tools, system, user turn) |
| strategy -> gate -> CommitSink | The only write path; mutations merged into one gated proposal | prepared ToolSteps |
| library -> app/logs | toString, failure codes, trace codes | names, counts, lengths, fixed wire codes (no payloads) |
| tests/evidence -> repository | Wire tests (MockWebServer only), evidence files | fake key sk-test-key, test names |

---

## Threat Register

All 36 threats were authored at plan time in 07-01..07-08 PLAN threat models. Evidence is the verified code and named tests (auditor read the sources and tests; Gradle result from 07-VERIFICATION.md).

| Threat ID | Category | Component | Severity | Disposition | Mitigation / evidence | Status |
|-----------|----------|-----------|----------|-------------|-----------------------|--------|
| T-07-01 | Info disclosure | ModelRequest.toString flag | medium | mitigate | boolean only; TranscriptTypesTest, RedactionCanaryTest | closed |
| T-07-02 | Tampering | flag lost tier to provider | medium | mitigate | SingleShotPlumbingTest provider-side assertion | closed |
| T-07-03 | Info disclosure | recordCode side channel | low | mitigate | internal, TraceCode-only, internal constructor | closed |
| T-07-04 | Tampering | ModelRequest constructor break | medium | mitigate | 6-arg ctor kept; reflection test | closed |
| T-07-05 | DoS | OpenRouter 404 on parallel_tool_calls | high | mitigate | vendor flag false; ChatEncoderTest OpenRouter case | closed |
| T-07-06 | DoS | o-series 400 on parallel_tool_calls | high | mitigate | OpenAiModelRules gate; OpenAiModelRulesTest, ChatEncoderTest | closed |
| T-07-07 | Tampering | Anthropic multi tool_use after reshape | medium | mitigate | flag in both shapes; first call only | closed |
| T-07-08 | DoS | cached prefix busted by new field | low | accept | field in tool_choice outside tools/system; byte test; live cache check carried to Phase 10 VER-03 | closed (accepted) |
| T-07-09 | Info disclosure | seam type toString | high | mitigate | names/lengths only; SingleShotSeamTypesTest, canary sweep | closed |
| T-07-10 | Tampering | resolver writing before gate | high | mitigate | seam returns ToolSteps only; holding-gate test applyCount 0 | closed |
| T-07-11 | Tampering | per-command text in cached prefix | medium | mitigate | renderer output is the user turn only; byte-equality test | closed |
| T-07-12 | Elevation | unconfirmed public API frozen | high | mitigate | seam-signoff.txt SIGNOFF: APPROVE precedes 07-04 | closed |
| T-07-13 | Spoofing | forced tool not offered | low | mitigate | ToolingSnapshot init require | closed |
| T-07-14 | Tampering | write without gate | high | mitigate | session.submit only; one merged proposal | closed |
| T-07-15 | Spoofing | model calls unoffered tool | high | mitigate | snapshot lookup, MalformedExtraction before resolver | closed |
| T-07-16 | Tampering | truncated/refused output resolved | high | mitigate | stop reason decided before tool call read | closed |
| T-07-17 | Tampering | several calls, duplicate writes | medium | mitigate | first call only + extra_tool_calls_dropped | closed |
| T-07-18 | Elevation | refusal escalating to cloud | medium | mitigate | refusal is Failed, never escalates | closed |
| T-07-19 | Info disclosure | strategy toString/codes | medium | mitigate | id + forceTool only; fixed codes; canary run | closed |
| T-07-20 | DoS | budget burned past ceiling | high | mitigate | pre-call ceiling stage; SingleShotLimitsTest | closed |
| T-07-21 | DoS | oversized output | medium | mitigate | maxTokens from policy; tested default/custom | closed |
| T-07-22 | DoS | hidden re-ask loop | high | mitigate | one complete call; exactly-one tests at 2 and 6 | closed |
| T-07-23 | Tampering | acting on over-budget response | medium | mitigate | post-call stage before resolver/submit | closed |
| T-07-24 | Tampering | transcript in system prefix | medium | mitigate | system only from snapshot; containment tests | closed |
| T-07-25 | Tampering | weak/batch written without confirm | high | mitigate | S2/S3 applyCount 0 until commitHeld | closed |
| T-07-26 | Tampering | bad row corrupting batch | medium | mitigate | S5 per-item isolation | closed |
| T-07-27 | Repudiation | deferred commit unlinked | medium | mitigate | parentRunId, own close, idempotent repeat | closed |
| T-07-28 | Tampering | held tier then later tier writes | high | mitigate | aHeldProposalNeverReachesTheNextTier | closed |
| T-07-29 | Info disclosure | leak from SingleShot run | high | mitigate | noCanaryLeaksFromASingleShotRun sweep | closed |
| T-07-30 | DoS | OpenRouter 404 if flag leaks | high | mitigate | SingleShotWireTest OpenRouter body assertion | closed |
| T-07-31 | Info disclosure | real key/network in tests | medium | mitigate | sk-test-key + MockWebServer only | closed |
| T-07-32 | Tampering | wire shape differs across OkHttp legs | medium | mitigate | all three legs green (430 tests each) | closed |
| T-07-33 | Tampering | api.txt leaking pre-cut | high | mitigate | isolated-copy dump; hygiene gate HYGIENE OK | closed |
| T-07-34 | Repudiation | hand-typed limits evidence | medium | mitigate | results read from JUnit XML | closed |
| T-07-35 | Elevation | live call without approval | high | mitigate | no live leg; carried to Phase 10 under with-test-keys | closed |
| T-07-36 | Tampering | surface drift | medium | mitigate | review-api-surface.sh --expect-sealed-complete | closed |

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-07-01 | T-07-08 | The single-call field lives in tool_choice, outside the cached tools/system bytes (encoder test proves unchanged prefix). The live cache_read check is carried to Phase 10 VER-03. | plan 07-02 threat model | 2026-10-01 |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-10-01 | 36 | 36 | 0 | gsd-security-auditor (read-only, ASVS L1, block_on high) |

Notes: post-review fixes (WR-01..04, IN-01/03/04/06) weakened no mitigation. WR-03 reply policy and IN-06 lowercasing are behavior choices flagged for pre-tag sign-off in 07-VERIFICATION.md. Phase-gate and limits evidence files were stamped before the review fixes; the fresh `./gradlew check` at 06f839c (07-VERIFICATION.md) and the post-fix surface review cover the delta.

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: secured` set in frontmatter

**Approval:** verified 2026-10-01
