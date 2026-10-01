---
phase: "8"
slug: multi-turn-mappers
status: secured
threats_open: 0
asvs_level: 1
audited_head: a0659b4c49a299437ce0390905c0ec928ea8c74e
created: "2026-10-01"
---

# Phase 8 - Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| caller transcript -> mapper pre-flight | A stamped or malformed history is refused before any HTTP request | transcript, replay stamps, tool-call ids |
| mapper -> provider wire | Encoders send stored replays verbatim or rebuild from parts | request bodies |
| provider response -> decoder/replay | Responses are decoded; empty-arg forms repaired | tool arguments, reasoning/signature blocks |
| capture output -> repository | Sanitized conversation goldens and evidence are committed | sanitized bodies, LIVE_CAPTURE lines |
| key store -> live test process | Keys only via with-test-keys, double opt-in, outside check | API keys (never committed) |
| library -> app/logs | Failure reasons carry kind-only codes | fixed codes, no ids/text |

---

## Threat Register

All 32 threats were authored at plan time (08-01..08-09 PLAN threat models). Audit verified mitigations at HEAD a0659b4, after review fixes WR-01..WR-04 and IN-01..IN-04. Evidence is verified code, named tests, and grep sweeps of goldens/evidence. All dispositions are mitigate.

| Threat ID | Category | Component | Severity | Disposition | Mitigation / evidence | Status |
|-----------|----------|-----------|----------|-------------|-----------------------|--------|
| T-08-01 | Tampering | cross-provider/model replay | high | mitigate | ConversationCheck replay_mismatch before first request on both transports; encoder backstop; 0-request suite test | closed |
| T-08-02 | Info disclosure | ids/names/content in failure reasons | medium | mitigate | file-private reason constants, Other(code); canary test on reason and toString | closed |
| T-08-03 | DoS (cost) | malformed conversation billed | low | mitigate | pre-flight before sendWithRetry; 0-request coverage tests | closed |
| T-08-04 | Tampering | guard weakened to pass old tests | medium | mitigate | strict refusal; three reversed tests listed in phase-gate.txt | closed |
| T-08-05 | Tampering | JSON injection via error text | medium | mitigate | buildJsonObject encoding; round-trip test | closed |
| T-08-06 | Tampering | results attributed to wrong call | low | mitigate | resultsInCallOrder in both encoders; reversed-order tests | closed |
| T-08-07 | Repudiation | Chat model unaware of tool failure | medium | mitigate | error wrapped as {"error":...}; encoder test | closed |
| T-08-08 | Tampering | leniency past "empty" args | medium | mitigate | single isEmptyArgumentsForm predicate; strict decode tests kept | closed |
| T-08-09 | Info disclosure | malformed args echoed | low | mitigate | parseArguments drops text; canary test | closed |
| T-08-10 | Tampering | repair rewrites valid replay/id | medium | mitigate | repair limited to missing role / empty or object args / non-object input; determinism tests | closed |
| T-08-11 | Info disclosure | key/auth in golden incl. exempt thinking | high | mitigate | sanitizer scrubs / refuses; hygiene over every manifest row; grep sweep 0 hits | closed |
| T-08-12 | Info disclosure | real provider ids | medium | mitigate | shared id map to GOLDEN<n>; hygiene id rule incl. resp_/rs_/fc_ | closed |
| T-08-13 | Tampering | sanitizer rewrites signature/thinking | high | mitigate | exempt leaves kept untouched; byte-identical test; A2 signature accepted live | closed |
| T-08-14 | Tampering | non-canonical hand-edited fixture | medium | mitigate | canonical check per row; every on-disk golden has a manifest row | closed |
| T-08-15 | Tampering | fixture generated from encoder under test | medium | mitigate | static committed goldens; negative-control tests | closed |
| T-08-16 | Info disclosure | fixture/assertion text leaking data | low | mitigate | assertion messages name case/request/turn only | closed |
| T-08-17 | Repudiation | derived placeholder passed as real | low | mitigate | MANIFEST provenance column; real halves via captured rows | closed |
| T-08-18 | Tampering | response-only field echoed / router field dropped | medium | mitigate | REPLAY_FIELDS allowlist; per-vendor assertions; live acceptance recorded | closed |
| T-08-19 | Info disclosure | reasoning text/ids into fixtures | low | mitigate | ids GOLDEN; hygiene on every row | closed |
| T-08-20 | Tampering | suite weakened per dialect | medium | mitigate | no 08-06 commit touches the shared suite | closed |
| T-08-21 | Tampering | earlier turn rewritten (cache/thinking binding) | medium | mitigate | append-only check on every iteration; negative controls; reshape pin | closed |
| T-08-22 | DoS (cost) | stray/second cache directive | low | mitigate | per-dialect cacheDirectiveViolation each iteration; stray-directive control | closed |
| T-08-23 | Info disclosure | key/header into golden, raw file or console | high | mitigate | save() writes messages + response body only; loopback test asserts key absence | closed |
| T-08-24 | DoS (cost) | unbounded/retried live calls | high | mitigate | MAX_PHASE_REQUESTS=19 enforced in code, counted before send, no retry | closed |
| T-08-25 | Elevation of privilege | live capture inside check | high | mitigate | *Live* excluded from test legs; double opt-in env; live tasks not in check | closed |
| T-08-26 | Tampering | non-replaying golden committed | medium | mitigate | golden written only if replayConversation has no violations | closed |
| T-08-27 | Info disclosure | text printed to console | medium | mitigate | turnLine prints counts/flags only; printing test | closed |
| T-08-28 | Elevation of privilege | capture without relayed approval | high | mitigate | approve-capture relayed 2026-10-01 (19 req / USD 0.20 / with-test-keys), recorded in 08-09-SUMMARY and evidence; process control | closed |
| T-08-29 | Info disclosure | key/header/real id in golden or evidence | high | mitigate | diff sweep 27d2117..HEAD found no key shapes; providers/build gitignored | closed |
| T-08-30 | DoS (cost) | runaway spend | high | mitigate | ceiling + model allow-list in code; actual 14 of 19 requests, about USD 0.033 | closed |
| T-08-31 | Tampering | golden/assertion edited to fit live result | medium | mitigate | 08-09 commits add files only; R2 left unmet, no golden | closed |
| T-08-32 | Repudiation | SC passed silently on derived data | medium | mitigate | SC status block in evidence and 08-09-SUMMARY | closed |

*Status: open / closed*
*Disposition: mitigate / accept / transfer*

---

## Accepted Risks Log

No accepted risks.

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-10-01 | 32 | 32 | 0 | gsd-security-auditor (via execute-phase secure_phase_gate, ASVS 1, block_on high) |

Informational (not threats): KEY_IN_TEXT matches `sk-` shapes only (covers the three providers in scope; a grep for other key shapes found nothing). Gradle was not re-run by the auditor; results taken from phase-gate.txt and 08-REVIEW-FIX.md (check exit 0 after the last fix).

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: secured` set in frontmatter

**Approval:** verified 2026-10-01
