---
phase: "4"
slug: anthropic-transport-okhttp-matrix
status: secured
threats_open: 0
asvs_level: 1
audited_head: 201f3ddb679868c4efc90ec075617e0a5b0cdd13
created: "2026-10-01"
---

# Phase 4 - Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| app -> AnthropicProvider | App supplies credential, transcript, tools, capabilities overrides, observer | API key, transcript, tool args/results |
| engine -> api.anthropic.com | Fixed https host (loopback https/http only in tests) over a cleaned OkHttp client | x-api-key header, request body |
| server response -> engine | Untrusted 2xx and error bodies, headers | error.type, request-id, tool_use blocks |
| build -> live API | Opt-in Haiku-only capture outside `check` | Test key via with-test-keys wrapper |

---

## Threat Register

Audited 2026-10-01 by gsd-security-auditor against the post-review-fix tree (register authored at plan time from 04-01..04-08 threat models; 31 distinct IDs plus T-04-SC; severities use the highest across plans). All 32 CLOSED (30 mitigate, 2 accept). Evidence is file:line per threat in the auditor verdict (summarised below).

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-04-01 | Tampering | Hard-coded id set overriding capability choice | medium | mitigate | Transport reads only call.capabilities.supportsForcedToolChoice; app override wins; AnthropicModels is default layer | closed |
| T-04-02 | DoS | Prefix/case-insensitive id matching | low | mitigate | Exact `when(model)`; reactive tool_choice 400 reshape for unseen ids | closed |
| T-04-03 | Tampering | Model ids leaking into :core | low | mitigate | NoHardCodedConstantsTest scan with non-vacuity floor | closed |
| T-04-04 | Info disclosure | x-api-key on cross-host redirect | high | mitigate | followRedirects/followSslRedirects false; 301/302/303/307/308 tests | closed |
| T-04-05 | Info disclosure | App interceptors/listener/authenticators seeing key | high | mitigate | CleanClient clears interceptors, EventListener.NONE, Authenticator.NONE, proxy authenticator replaced (WR-02) | closed |
| T-04-06 | Info disclosure | Late response body left open | medium | mitigate | CallAwait closes body in finally; late response returns early | closed |
| T-04-07 | Tampering | OkHttp silently replaying a POST | medium | mitigate | OneShotBody.isOneShot true; positive control on warm connection | closed |
| T-04-08 | DoS | Hostile error.type / request id | medium | mitigate | SafeFields drops non-conforming values | closed |
| T-04-09 | Info disclosure | Stringified OkHttp types / exception messages | high | mitigate | Code-only messages, text-free IOException, ignored catches | closed |
| T-04-10 | Info disclosure | Cleartext / attacker base URL | high | mitigate | Fixed https constant; internal baseUrl; build() rejects non-https unless loopback | closed |
| T-04-11 | Spoofing | Other provider's key sent to Anthropic | medium | mitigate | Provider mismatch refused before dispatch (requestCount 0) | closed |
| T-04-12 | Info disclosure | Key/transcript/body in toString, failures, exceptions | high | mitigate | Constant messages; key to one header only; header-illegal key answered as Auth before OkHttp can quote it (CR-01) | closed |
| T-04-13 | Tampering | Malformed/hostile 2xx | medium | mitigate | Decoder maps to MalformedToolArgs/MalformedResponse; IllegalArgumentException only | closed |
| T-04-14 | DoS | Very large response body | low | accept | Fixed trusted host, max_tokens always sent, call/read timeouts; body read unbounded (accepted, review IN-06) | closed (accepted) |
| T-04-15 | Info disclosure | Error body echoing prompt/key into failure | high | mitigate | Parse-and-discard; failures carry status, error.type, request-id only | closed |
| T-04-16 | Info disclosure | Exception text in failures | medium | mitigate | Timeout/Network carry no text; ignored exceptions | closed |
| T-04-17 | DoS | Cancelled command leaving HTTP call running | medium | mitigate | invokeOnCancellation cancel(); ensureActive(); runningCallsCount 0 tests | closed |
| T-04-18 | Repudiation | Spend limit misreported as transient | low | mitigate | Billing mapping excluded from retry | closed |
| T-04-19 | Tampering | Retry re-running a tool or committing twice | high | mitigate | Retry only repeats HTTP; pipeline test: 2 requests, 1 apply, 1 commit | closed |
| T-04-20 | DoS | Retry storm / unbounded wait | medium | mitigate | 3-request ceiling, one transient retry, 5 s wait cap, suspend delay | closed |
| T-04-21 | Info disclosure | Observer leaking headers/bodies/text | medium | mitigate | AnthropicAttempt carries number, kind, status only; throwing observer isolated (WR-01) | closed |
| T-04-22 | Tampering | Forced-400 matcher firing on unrelated 400 | medium | mitigate | Three-condition matcher with five negative rows | closed |
| T-04-23 | Tampering | Strict mode making optionals required | high | mitigate | input_schema untouched; strict only when no optional properties | closed |
| T-04-24 | Tampering | Missing tool call fabricated into a parse | medium | mitigate | NoToolCall, non-transient on 2xx | closed |
| T-04-25 | Info disclosure | Key printed/committed from live run | high | mitigate | Live test prints ids/counts only; with-test-keys redaction; evidence file key-free | closed |
| T-04-26 | Info disclosure | check making live calls with ambient key | high | mitigate | *Live* excluded from test and legs; live task opt-in, not wired into check | closed |
| T-04-27 | DoS | Runaway live spend | low | mitigate | Haiku 4.5 only, 6-request ceiling (attempts=4 recorded) | closed |
| T-04-28 | Info disclosure | Raw bodies saved as captures | medium | mitigate | No text/body printed; LIVE_CAPTURE lines only | closed |
| T-04-29 | Info disclosure | Secrets reaching trace/events/toString/failures | high | mitigate | Canary sweep with positive controls on all three OkHttp legs | closed |
| T-04-30 | Tampering | Matrix leg silently using floor mockwebserver | medium | mitigate | OkHttpVersionGuardTest asserts runtime and mockwebserver jar per leg | closed |
| T-04-31 | Tampering | Public type freezing unwanted API | low | mitigate | ProvidersApiShapeTest bans enums, data classes, public static fields, default-arg stubs | closed |
| T-04-SC | Tampering | Package installs | low | accept | gradle/libs.versions.toml unchanged; no dependency added | closed (accepted) |

*Status: open · closed · open - below block_on threshold (non-blocking)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-04-01 | T-04-14 | Response body read whole with no size cap; fixed trusted https host, max_tokens and timeouts bound it | plan disposition (accept) | 2026-10-01 |
| AR-04-02 | T-04-SC | No package added in this phase (verified by git diff) | plan disposition (accept) | 2026-10-01 |

---

## Non-blocking Observations

1. The T-04-19 grep gate, the T-04-25 key-shape check and the T-04-26 dry-run gate were plan-time acceptance commands, not persistent build gates. Candidate for a later hardening phase.
2. T-04-23 guards strict mode added by the engine only; an app-set `ToolSpec.strict = true` passes through as asked (pinned by test).
3. The with-test-keys redactor and `--no-daemon` are procedural controls outside the build.
4. A timeout gets the single transient retry, so the first request may still be billed (documented, review IN-02).

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-10-01 | 32 | 32 | 0 | gsd-security-auditor (via execute-phase secure_phase_gate) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: secured` set in frontmatter

**Approval:** verified 2026-10-01
