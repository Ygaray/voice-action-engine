---
phase: "17"
slug: "run-level-undo"
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
# audited_head = git HEAD sha at audit time (freshness stamp)
audited_head: f0c7cf0d16237d3eae8a0fa9260f33f698141f3a
created: "2026-10-07"
---

# Phase 17 - Security

> Per-phase security contract: threat register, accepted risks, and audit trail. Register built from the
> `<threat_model>` blocks of plans 17-01..17-10 (33 threats after de-duplication; highest severity kept where
> plans differed). Audited by gsd-security-auditor at ASVS L1, block_on high, after code review and review fixes.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| build scripts to published artifact | hand-maintained module lists to gates; `:undo` zero-dependency promise | module graph, POMs |
| release script to immutable tag | gates 7, 10, 12, 15; published POM to consumer graph | coordinates, api.txt |
| engine to app sinks | `compositeSink` / `guarded` under NonCancellable | commit events (no payloads in errors) |
| app adapter or compensator to journal | `Verifier`, `Restorer`, `Compensating`, claim flag | entity ids, snapshots (never leave the journal) |
| journal to app store / UI / logs | `JournalStore` carries refs and counts only; redacted `toString` | refs, counts, flags |
| engine to app bridge | `UndoCommitSink`, withhold-on-gap, `runClosed` integrity check | run ids, positions |
| docs to integrating agent; phase to v1.1.0 tag | doc/region/parity checks, surface review | public API surface |
| heavy gates to shared host; relay to grant line | quiet-window protocol, relay-only grant | host memory |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-17-01 | Tampering | :undo dependency graph | high | mitigate | empty allowlist + `verifyUndoZeroDeps` wired to check; 3 negative plants; `:undoalone` probe (invariants.gradle.kts:272-409, verify-negative-controls.sh:144-156, jitpack-consumer-probe.sh:73-91) | closed |
| T-17-02 | Elevation | :core gaining an :undo edge | high | mitigate | `coreAllowed` unchanged; core allowlist + module graph gates; no `.undo` import in core | closed |
| T-17-03 | Tampering | hard-coded module lists | medium | mitigate | single manifest read by scripts/lib/modules.sh; planted-module selftest (residual hard-coded lists in verify-docs-coverage.sh and run-sample-gate1.sh are informational, Phase 19 handoff) | closed |
| T-17-04 | Repudiation | Metalava compat skipped for :undo | medium | mitigate | header-only undo/api.txt tracked; verifyApiDumpPresent strict; verify-api-seed.sh | closed |
| T-17-05 | DoS | compositeSink sibling isolation | high | mitigate | each child wrapped in `guarded` (CompositeSink.kt:24-38); CompositeSinkTest; sample S5 | closed |
| T-17-06 | Info disclosure | compositeSink failure text | medium | mitigate | composite throws fixed counts-only message, no child text; canary test | closed |
| T-17-07 | Tampering | heldRunId grouping | medium | mitigate | set only by `HeldCommit.open`; internal constructor; HeldRunIdTest | closed |
| T-17-08 | DoS | caller cancellation swallowed | medium | mitigate | `guarded` rethrows caller cancellation (Guarded.kt:38-64); test | closed |
| T-17-09 | Repudiation | stale tooling headers | low | mitigate | "no api.txt in the real tree" headers corrected | closed |
| T-17-10 | Tampering | gate 7 allowlist | high | mitigate | exact HEAD-manifest `<m>/api.txt` paths (release-cut.sh:111,326-342); verify-release-manifest.sh Part 1 | closed |
| T-17-11 | Tampering | gate 15 published coordinates | high | mitigate | `dependsOnCore` enforced both ways in POM and .module (published_versions.py); Part 2 green/red cases | closed |
| T-17-12 | Repudiation | release gates silently skipping a module | medium | mitigate | MODULES read from HEAD manifest; gate_hygiene runs verify-module-manifest.sh | closed |
| T-17-13 | DoS | release cut blocked by new module | low | accept | gate 12 goes red loudly until Phase 20 adds the new-module branch (owner: Phase 20, 17-SURFACE-REVIEW.md) | closed (accepted) |
| T-17-14 | Tampering | undo clobbering a later edit | high | mitigate | read-only whole-scope verify before any write; atomic `restoreIf(expectedFingerprint)`; refusals write nothing; RefuseLoudlyTest, ChainVerifyTest, PartialRestoreTest, sample S2 | closed |
| T-17-15 | Info disclosure | ids/snapshots/messages in toString/results | high | mitigate | EntityKey prints id length only; results print counts; sanitized class names; no logging sinks; UndoRedactionTest canary sweep | closed |
| T-17-16 | Tampering | malformed keys at the public boundary | medium | mitigate | `requireToken` (non-blank, <=256) and `position >= 0` on every public entry point | closed |
| T-17-17 | DoS | swallowed cancellation / stuck group | medium | mitigate | `guardedCall` rethrows CancellationException; no lock across suspend; claim released in finally; CancelMidUndoTest | closed |
| T-17-18 | Elevation | generic adapter needing an unchecked cast | low | accept | non-generic EntityAdapter with `Any?` snapshots | closed (accepted) |
| T-17-19 | Tampering | double/concurrent undo | high | mitigate | compare-and-set `undoing` flag under journal lock; second call `Refused(IN_PROGRESS)`; IdempotentUndoTest | closed |
| T-17-20 | Repudiation | silent partial undo | high | mitigate | exact `Partial` lists; stop on first failure; retry finishes remainder; PartialRestoreTest | closed |
| T-17-21 | Tampering | compensator re-run or stale run | medium | mitigate | compensators run only when all entry keys restored, marked done after return, never on refusal; CompensatorTest | closed |
| T-17-22 | Repudiation | missed action gives "Undo all (N-1)" | high | mitigate | null/foreign/reused ticket, duplicate, unsealed commit, missing position, late append after eviction all withhold the group; WithheldGroupTest, LimitsTest, sample S6a/S6b (tombstone bound documented, not unconditional) | closed |
| T-17-23 | DoS | unbounded journal growth | medium | mitigate | 50 groups / 1 h defaults, sweep every operation, bounded tombstones; LimitsTest | closed |
| T-17-24 | Info disclosure | snapshots reaching an app store | medium | mitigate | store receives `UndoGroup` only (refs, flags, counts); JournalStoreTest | closed |
| T-17-25 | DoS | failing store breaking undo | medium | mitigate | store calls guarded and counted, published outside the lock, cancellation propagates | closed |
| T-17-26 | Tampering | single-entry undo of an entangled action | medium | mitigate | `undoEntry` only for isolated entries else `ENTANGLED`; IsolationTest | closed |
| T-17-27 | Tampering | held change restored to stale state | medium | mitigate | capture inside `apply` (ItemMutations.kt:43-48); sample S3 | closed |
| T-17-28 | Spoofing | reply merged into the wrong group | medium | mitigate | `heldRunId ?: runId` grouping; reply keeps `parentGroupKey`; sample S4 | closed |
| T-17-29 | Tampering | documented bridge drifting from proven one | medium | mitigate | verify-docs-coverage.sh C06 + UndoBridgeParityTest; auditor diff identical modulo indentation | closed |
| T-17-30 | Repudiation | unreviewed public surface frozen at tag | medium | mitigate | 17-SURFACE-REVIEW.md from real isolated dumps; UndoApiShapeTest | closed |
| T-17-31 | DoS | heavy gates OOM-killing the shared host | high | mitigate | pre-check, low-memory recipe, one run at a time, relayed quiet window (17-QUIET-WINDOW.md) | closed |
| T-17-32 | Elevation | self-authored grant | high | mitigate | grant written only from the relayed answer with relayed_by and date (commit d84843d) | closed |
| T-17-SC | Tampering | dependency installs | low | accept | no package added; `verifyUndoZeroDeps` fails on any new artifact | closed (accepted) |

*Status: open - closed - open below block_on threshold (non-blocking)*
*Severity: critical > high > medium > low - only open threats at or above `high` count toward threats_open*

---

## Accepted Risks Log

| Risk ID | Rationale | Owner |
|---------|-----------|-------|
| T-17-13 | New-module undo seed makes release gate 12 red until Phase 20 adds the new-module branch; loud red preferred to a silent skip | Phase 20 |
| T-17-18 | EntityAdapter is non-generic with `Any?` snapshots; the app casts inside its own adapter | n/a |
| T-17-SC | No dependency installs this phase; zero-dependency gate catches any addition | n/a |

## Non-blocking observations (not threats)

1. Hard-coded module lists remain in `scripts/verify-docs-coverage.sh` and `scripts/run-sample-gate1.sh:275` (informational checks, not code gates). Phase 19 handoff (17-SURFACE-REVIEW.md).
2. Review item IN-04 (skipped): `ActionEvent.toString()` redacts `heldRunId` but prints `parentRunId` (existing v1.0 :core behaviour, outside T-17-15's :undo scope). Deferred to Phase 20 before the v1.1.0 tag (17-VERIFICATION.md deferred_obligations).
3. Tombstone bound for T-17-22 is bounded (max(1000, 20 x maxGroups)), documented in the `UndoJournal.record` KDoc and INTEGRATION section 11, pinned by LimitsTest.
4. Fingerprint contract: a parent's fingerprint must cover snapshotted children; `touches`-only entities are never fingerprint-verified (documented, WR-05).

---

## Security Audit 2026-10-07

| Metric | Count |
|--------|-------|
| Threats found | 33 |
| Closed | 33 |
| Open | 0 |

Auditor verdict: SECURED (ASVS L1, block_on high, audited_head f0c7cf0).
