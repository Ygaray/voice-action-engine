---
phase: "12"
slug: "wave-1-seams-w04-fix"
status: verified
threats_open: 0
asvs_level: 1
audited_head: ac7152328cc7990f5c679824f8ba30e1a8c6e4b9
created: "2026-10-05"
---

# Phase 12 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| provider HTTP answer -> ChatErrors | Untrusted JSON error bodies become a typed reason | status, `param`/`code` identifiers (message never kept) |
| app capability override -> wire rules | App patches decide whether a pre-call refusal is skipped | capability flags per exact model id |
| app hook code -> strategy | `onFailed` is app code running inside the tier | FailureReason, FailureDetails, plus the transcript if the hook escalates |
| ModelRequest -> provider encoders | The new reasoning field enters the wire layer | ReasoningMode token (no wire effect in v1.1) |
| strategy -> walk / trace -> app telemetry | Trace facts must come from the engine, with no content | carryIn boolean, cappedByPolicy boolean |
| consumer app code -> :keystore key custody | Public opt-in seam that supplies device-key access | SecretKey handles, encrypted API keys |
| provider answer -> engine -> app sink / undo journal | Provider-supplied tool-call id handed to app code | providerCallId / callId string |
| public docs -> integrating agents | Copied snippets become app code | keystore-fake snippet |
| host runner -> TESTER | Only path to the phone | APK, test keys by file reference, UI driving |
| decision file -> runner | A text line gates whether keys may move | `decision: approved` line |
| test-key files -> host process | Spend-capped key enters only the with-test-keys child | OPENAI_API_KEY env (never argv) |
| OpenAI API -> host / app | Live answers come back | HTTP status plus four classified fields; closed-vocabulary evidence lines |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-12-01 | Info disclosure | ChatErrors.refine new arm | medium | mitigate | Arm reads only `param`/`code` via `textField`, never the message (ChatErrors.kt:147-148); no new ChatErrorInfo field; canary test ChatErrorMapTest.kt:131-138 | closed |
| T-12-02 | Tampering | ChatErrors.refine | medium | mitigate | Matches only 400 + `reasoning_effort` + `unsupported_value`; negative tests ChatErrorMapTest.kt:116-128 | closed |
| T-12-03 | DoS | OpenAiModelRules.wireRules | medium | mitigate | Deny-list only (OpenAiModelRules.kt:16,19,96,113-114); working ids keep `none` (OpenAiModelRulesTest.kt:65); golden diff additive only | closed |
| T-12-04 | Info disclosure | ModelRequest / ReasoningMode toString | low | mitigate | toString adds only the mode token; TranscriptTypesTest.kt:113 | closed |
| T-12-05 | DoS | encoders via ModelRequest.reasoning | high | mitigate | No encoder changed; ReasoningWireParityTest pins OFF/PROVIDER_DEFAULT to v1.0 bytes on all three providers | closed |
| T-12-06 | EoP (policy bypass) | Builder.onFailed | medium | mitigate | Hook only on the `else` arm (SingleShotOutcomes.kt:40); binding refusals return before `complete()`; SingleShotOutcomeMappingTest.kt:479-505 | closed |
| T-12-07 | Info disclosure | TierAttempt.carryIn | medium | mitigate | Boolean only, engine-computed (TierWalk.kt:76); TraceTest.kt:369 | closed |
| T-12-08 | Repudiation / tampering | Unhandled.cappedByPolicy | low | mitigate | Derived only from PolicyPreCheck, no strategy input | closed |
| T-12-09 | EoP | KeyAccess / 3-arg ApiKeyStore | high | mitigate | RequiresOptIn(ERROR) on interface and constructor; two red plants + one green control (verify-keyaccess-opt-in.sh) | closed |
| T-12-10 | Info disclosure | new constructor path | medium | mitigate | No toString/log added in keystore; fixed non-secret test key | closed |
| T-12-11 | Tampering | opt-in flag in keystore build | low | mitigate | Flag scoped to keystore test compilations only (after WR-02) | closed |
| T-12-12 | Tampering (wrong device) | run-sample-gate1.sh | high | mitigate | Runner device guards unchanged; guard test covers impostor, foreign serial, personal phone | closed |
| T-12-13 | Spoofing (approval) | 12-LIVE-LEG-DECISION.md | medium | mitigate | Flipped only with relayed_by/date lines (76f896c); runner requires exact `decision: approved` | closed |
| T-12-14 | Info disclosure | evidence lines | medium | mitigate | Filter, EvidenceLine and golden unchanged; reason words are constants | closed |
| T-12-15 | Info disclosure | ExecutedAction / HeldProposal / Extraction | medium | mitigate | No toString prints the id; RedactionCanaryTest.kt:543 | closed |
| T-12-16 | Tampering (misattribution) | CommitCoordinator / HeldCommit | medium | mitigate | Each call stamps its own id; held proposal carries it to commitHeld; tests a1/a2 and plain/amended commitHeld | closed |
| T-12-17 | EoP | CommandSession overload | low | mitigate | `internal abstract submit(step, providerCallId)`; public submit records null; no api.txt change | closed |
| T-12-18 | EoP | INTEGRATION.md keystore-fake prose | medium | mitigate | "Tests only" stated; `@OptIn` visible in snippet | closed |
| T-12-19 | Info disclosure | docs and snippets | medium | mitigate | No key-shaped text or home paths; verify-docs-coverage C25 | closed |
| T-12-20 | Info disclosure (weak crypto) | keystore-fake region | low | mitigate | AES-256 KeyGenerator in snippet; AesGcm.kt untouched | closed |
| T-12-21 | Info disclosure | Probe C (host) | high | mitigate | Key only inside with-test-keys, header via curl --config stdin from builtin, only HTTP code printed, probe dir removed | closed |
| T-12-22 | Info disclosure | key on the TESTER | high | mitigate | push-test-key by file reference; verify-keys-gone `keys_gone=yes` twice; cleanup uninstall OK | closed |
| T-12-23 | Tampering (wrong device) | device steps | high | mitigate | Guarded runner only; RT-01 window grant; evidence `target=R5CT10XNKQN` | closed |
| T-12-24 | DoS (spend) | live calls | medium | mitigate | One-request optional pool; 2 of 4 requests spent; USD 0.00 | closed |
| T-12-25 | Info disclosure | committed evidence | medium | mitigate | `sample-evidence-filter.sh` re-scan: FILTER OK kept=5 dropped=1 | closed |
| T-12-SC | Tampering | dependency installs | low | accept | No packages installed; only 9 lines of opt-in task config in keystore/build.gradle.kts | closed (accepted) |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above workflow.security_block_on count toward threats_open*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-12-01 | T-12-SC | Phase installs no packages; no supply-chain surface added | plan disposition (accept) | 2026-10-05 |
| AR-12-02 | T-12-09 | `DelicateKeyAccess` gate is Kotlin-compiler-only; Java callers, reflection and the internal 4-arg constructor (public in bytecode) bypass it. Documented in DelicateKeyAccess.kt KDoc; a guardrail against accidental use, not an in-process barrier | auditor residual note (below block threshold) | 2026-10-05 |
| AR-12-03 | T-12-06 | A bound on-device provider's runtime failures reach `onFailed`; an app hook that always escalates can move such a failure to a cloud tier. Documented in the onFailed KDoc (WR-01); behaviour change needs a D-01 design call | auditor residual note (below block threshold) | 2026-10-05 |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-10-05 | 26 | 26 | 0 | gsd-security-auditor (ASVS 1, block_on high) |

Non-blocking observations from the audit:
- T-12-06: the refused-binding test covers a missing credential only, not an explicit on-device binding refusal (same `model.refusal` short-circuit).
- T-12-09: `verify-keyaccess-opt-in.sh` / `verify-negative-controls.sh` Part 4 were re-run green by the phase verifier after WR-02 and WR-03.
- T-12-13: `12-LIVE-LEG-DECISION.md` still reads `decision: approved` after the TESTER window closed; the runner would pass the decision gate again with no new approval. Bounded by the TESTER-only guards, the in-app budget and push-test-key refusing the personal phone. Consider flipping the line to a non-approved value after each window (follow-up for the orchestrator).

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-10-05
