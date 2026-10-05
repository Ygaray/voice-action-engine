---
phase: "2"
slug: core-contract-pipeline-commit-seam
status: secured
threats_open: 0
asvs_level: 1
audited_head: e56784056e64cc57cdc38957a4ae64ac24983696
created: "2026-09-30"
---

# Phase 2 - Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| app -> engine input types | CommandInput and Credential enter the engine | transcript text, opaque app context, API key |
| engine -> logs / telemetry / exceptions | any toString, trace, event or failure value may be logged by the app | must be ids, codes, counts, tool names and exception class names only |
| repository -> frozen public API | whatever is public at the v1.0.0 cut can only grow | public signatures (Metalava dump) |
| model output -> engine | terminal-call arguments are model-generated JSON | untrusted JSON (TerminalCall.arguments) |
| app callbacks -> engine | gate, apply, sink, listener, policy source, hooks and descriptor getters run inside the engine coroutine | arbitrary exceptions and cancellation types |
| strategy -> engine | strategies request writes only as ToolStep values and report StrategyOutcome | write requests, escalation carry, turn/usage records |
| engine -> app gate / apply / sink | the gate decides, apply writes, the sink is the undo journal | proposals, actions, run termination |
| engine -> app (outcome, events) | everything returned or delivered may be logged | outcomes, traces, events |
| app policy source / settings -> engine | the policy decides which tiers and providers may run | TierPolicy |
| engine -> network (future tiers) | offlineOnly must hold with zero HTTP calls | transcript to cloud provider |
| strategy result -> ladder | Escalate/NoMatch could hand a partly-written command to another tier | carry, applied/held counts |
| app UI -> commitHeld / gate resolve | user taps (possibly double or stale) resolve held or pending writes | confirm/decline answer, confirmation id |
| app policy / amend hook -> gate | app code decides what needs confirmation and may amend the write | confirmation subject, amended mutations |
| engine -> undo journal | runs must close exactly once and never write after close | run-closed signal, action events |
| gate scripts -> real tree | isolated-copy dumps must never land in module directories | api.txt |

---

## Threat Register

Verified by the gsd-security-auditor against the final tree (including the 15 committed code-review fixes). 42 of 42 closed; every threat has disposition mitigate. Evidence is test names plus file references; full per-threat evidence was returned by the auditor and is summarised by group.

| Threat ID | Category | Severity | Disposition | Mitigation (evidence) | Status |
|-----------|----------|----------|-------------|-----------------------|--------|
| T-02-01, T-02-02, T-02-08, T-02-13, T-02-33, T-02-35, T-02-42 | Info disclosure (secrets, transcripts, tool args in toString/trace/events/exceptions/source) | high | mitigate | Redacted toString on every engine value (length/class/ids only); EngineFault holds errorClass only; ProviderUnavailable.cause restricted to stable codes; RedactionCanaryTest sweeps 25+ outputs; PipelineSpineTest canary; planning-id and banned-construct rules in invariants.gradle.kts and detekt | closed |
| T-02-03, T-02-05, T-02-39, T-02-41 | Tampering / Repudiation (frozen public API shape, api.txt leakage) | high | mitigate | ApiShapeTest; scripts/review-api-surface.sh runs in an isolated copy and guards --out; sealed allow-list (--expect-sealed-complete); explicit API strict; internal constructors on all engine-produced types; no api.txt on disk or tracked | closed |
| T-02-04, T-02-19, T-02-34, T-02-38 | DoS / Tampering (limits, hangs, usage misreport) | medium | mitigate | TierPolicy init validation; engine deadline (Timeout); 120 s default confirm window; Usage normalised buckets with saturating sums; engine-computed tokensUsed. Residual: see AR-3 | closed |
| T-02-06, T-02-09 | DoS / EoP (model-shaped terminal args) | medium | mitigate | asClarification type-checked and null on mismatch; terminal-and-mutating rejected at construction | closed |
| T-02-07, T-02-18, T-02-21 | Tampering / Repudiation (cancel semantics) | high | mitigate | guarded rethrows cancellation on the non-uncancellable path; admitCaller ensureActive at entry and after gate; cancel mid-apply journaled first (journalCancelled) then rethrown; GuardedTest, NeverThrowTest, WriteGuardsTest, CommitPathTest | closed |
| T-02-10, T-02-11, T-02-20, T-02-23, T-02-30, T-02-31, T-02-32 | EoP / Spoofing (write without or against the gate) | high | mitigate | single write path via ToolStep -> gate -> ApplyStep; no auto-commit default (requireNotNull gate and sink); gate throw/timeout/decline holds; only resolve(id,true) admits; stale/replayed ids no-op; confirmations serialized; is_error recorded as applied error never a commit | closed |
| T-02-12, T-02-22, T-02-27, T-02-29, T-02-36, T-02-37 | Repudiation / DoS (journal integrity, sink and listener faults) | high | mitigate | NonCancellable delivery and close; exactly-once close in finally; sink/listener faults recorded as trace codes via guardedUncancellable and never retried; closed runs refuse late writes; single append point for ledger and events | closed |
| T-02-14, T-02-24 | Tampering (ordering, batch isolation) | medium | mitigate | mutex-serialized submit; positions assigned in the ledger; per-item guarded apply; BatchIsolationTest | closed |
| T-02-15, T-02-16 | Info disclosure / Tampering (offlineOnly bypass, silent climb to cloud) | high | mitigate | PolicyPreCheck before the walk; exact ON_DEVICE-only capability; default probe unavailable; ladder ends instead of climbing; tests run under NoNetworkGuard. Residual: see AR-5 | closed |
| T-02-17 | DoS (exception escaping execute) | high | mitigate | per-tier and top-level collapse to Failed(Unexpected(errorClass)); guarded run setup; NeverThrowTest, RunSetupGuardTest. Residual: see AR-4 | closed |
| T-02-25, T-02-26, T-02-28 | Tampering / Spoofing (duplicate write via escalation or double commitHeld, partial shown as success) | high | mitigate | escalation guard reads coordinator applied+held counts, never strategy claims; commitHeld atomic compareAndSet claim; Completed.partial is a required public field with render rule | closed |
| T-02-40 | Repudiation (Phase 1 gates regressed) | medium | mitigate | phase-gate.txt: check, negative controls, hygiene and api-dump proof all exit 0; single @Suppress in Guarded.kt; no baseline | closed |

*Status: open · closed · open - below high threshold (non-blocking)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-1 | T-02-27, T-02-18 | close() does not wait for an in-flight apply; waiting under NonCancellable could stall onRunClosed behind a hung apply(). A leaked coroutine late write is outside the documented contract; post-gate admitCaller re-check covers the realistic window (review WR-01 declined sub-part) | phase plan / code-review fix | 2026-09-30 |
| AR-2 | T-02-23 | commitHeld returns Completed with is_error actions when a held apply fails (locked plan decision; DeferModeTest pins it); KDoc tells callers to read commits and executed | phase plan | 2026-09-30 |
| AR-3 | T-02-04, T-02-38 | maxIterations, tokenCeiling and maxTokensPerTurn are advisory in :core (strategy enforces); TierPolicy KDoc says so. Engine-side token enforcement would change policy semantics | phase plan / code-review fix | 2026-09-30 |
| AR-4 | T-02-17 | JVM Errors (AssertionError, StackOverflowError, OOM) propagate by design; the run still closes once as Failed(Unexpected("Error")); KDoc corrected | phase plan / code-review fix | 2026-09-30 |
| AR-5 | T-02-15 | offlineOnly relies on each tier static capability declaration; NoNetworkGuard is a tripwire not proof; mixed ON_DEVICE-plus-cloud refusal is code-verified but has no dedicated test | phase plan | 2026-09-30 |

*Non-register observation O-1 (advisory, not blocking): CommitCoordinator.applyAll has no per-item cancellation check inside one admitted batch; if the caller is cancelled after item N applies, item N+1 apply() is still invoked unless the app apply reaches a cancellable suspension point. Suggested hardening for a later phase: ensureActive per item plus a test.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-09-30 | 42 | 42 | 0 | gsd-security-auditor (execute-phase secure_phase_gate) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: secured` set in frontmatter

**Approval:** verified 2026-09-30
