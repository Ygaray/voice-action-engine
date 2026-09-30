---
phase: "1"
slug: scaffold-publishing-proof
status: secured
threats_open: 0
asvs_level: 1
audited_head: a40f8319ca859c01d98450de853956d7a3c28bf2
created: "2026-09-30"
---

# Phase 1 - Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| working tree -> public origin/main | Everything pushed is public and feeds JitPack; the tree holds unrelated graphs/graphify-out changes | source, build scripts, evidence |
| jitpack.io -> consumer probe | Probe resolves and compiles third-party-served artifacts | POM, .module, jar/aar |
| build/gate scripts -> source tree | Negative controls plant violations and must restore | temporary plants |
| peers -> origin/main | Other sessions commit contract amendments to the same branch | contract docs |

---

## Threat Register

Verified by the gsd-security-auditor (29 of 30 closed at the audit; T-01-29 closed afterwards, see the audit trail). Full per-threat evidence was returned by the auditor and is summarised here by threat group.

| Threat ID | Category | Severity | Disposition | Mitigation (evidence) | Status |
|-----------|----------|----------|-------------|-----------------------|--------|
| T-01-01, T-01-05, T-01-26 | Info disclosure (wrong files pushed) | high | mitigate | .gitignore ignores fixture/graphify-out; explicit-path commits; pre-push file-list assertion; verify-repo-hygiene.sh | closed |
| T-01-02, T-01-SC | Tampering (supply chain) | high | mitigate | Catalog-pinned coordinates all Approved in RESEARCH package audit; wrapper distributionSha256Sum pinned; no BOM/strictly | closed |
| T-01-03 | Elevation of privilege (JitPack install scope) | medium | mitigate | jitpack.yml names exactly the three publish tasks; :sample not published; no secrets read at configuration time | closed |
| T-01-04 | Info disclosure (local.properties) | low | accept | Accepted risk: holds only an SDK path; ignored and never tracked | closed (accepted) |
| T-01-06 | Tampering (tags) | high | mitigate | Probes by SHA only; no tag local/remote; hygiene script fails on any tag | closed |
| T-01-07, T-01-08 | Tampering / Repudiation (JitPack served output) | medium | mitigate | Live probe asserts module set, POM group, no test-fixtures, aggregator, clean-cache consumer; each ref appended to evidence | closed |
| T-01-09 | Elevation of privilege (F4/F5 unilateral) | high | mitigate | F4/F5 not implemented; Task 3 never reached; modules still kotlin.jvm | closed |
| T-01-10 | Info disclosure (evidence file) | low | accept | Accepted risk: JitPack build logs are public, evidence holds only coordinates/statuses | closed (accepted) |
| T-01-11 - T-01-15 | Static gates (detekt, banned-construct scanner, baseline) | high/medium | mitigate | Syntax-only detekt with exact-set controls; scanBannedConstructs on all modules; verifyNoDetektBaseline | closed |
| T-01-16 - T-01-20 | Structural gates (module graph, core allowlist, OkHttp floor, bytecode, DI, api dump) | high/medium | mitigate | verify* tasks wired to check, each with a negative control | closed |
| T-01-21 - T-01-24 | Test matrix / fixtures | high/medium | mitigate | OkHttp 4.12/5.2.1/5.5.0 legs with reflective version guard; testFixtures not published | closed |
| T-01-25, T-01-28 | Repudiation/Tampering (negative-control harness) | high | mitigate | EXIT-trap restore, cmp -s restore assertion, marker-checked red-for-right-reason | closed |
| T-01-27 | Spoofing (coordinates in docs) | medium | mitigate | README/ECOSYSTEM list per-module coordinates; hygiene gate | closed |
| T-01-29 | Tampering (final live probe of final SHA) | medium | mitigate | Re-probed after the 16 review-fix commits: LIVE PROBE PASS ref=a40f8319ca (evidence/jitpack-probe.txt, rung=final-post-review) | closed |

*Status: open · closed. Only open threats at or above block_on (high) count toward threats_open.*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-01 | T-01-04 | local.properties holds only a machine SDK path, is gitignored and never tracked | plan 01-01 threat model | 2026-09-30 |
| AR-02 | T-01-10 | JitPack build logs are public; the probe prints only coordinates, statuses and dependency lines | plan 01-02 threat model | 2026-09-30 |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-09-30 | 30 | 29 | 1 (T-01-29, medium, non-blocking) | gsd-security-auditor |
| 2026-09-30 | 30 | 30 | 0 | orchestrator: T-01-29 closed by a live JitPack re-probe of the pushed review-fixed HEAD (a40f8319ca PASS) |

Informational notes from the auditor: the git tag lookup in build.gradle.kts is lazy and read-only (T-01-03 stays closed); .planning/graphs/ has been tracked since a pre-phase commit and is unchanged.

## Sign-Off

- [x] All threats have a disposition
- [x] Accepted risks documented
- [x] threats_open: 0 confirmed
- [x] status: secured set in frontmatter
