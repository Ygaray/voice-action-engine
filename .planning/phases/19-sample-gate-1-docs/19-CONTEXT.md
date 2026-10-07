# Phase 19: Sample Gate-1 & Docs - Context

**Gathered:** 2026-10-05
**Status:** Ready for planning
**Discussed via:** `/gsd-discuss-milestone` (mode: mixed)

<domain>
## Phase Boundary

The new tiers are proven end to end on the TESTER, and an AI agent can wire every new tier, seam and module from the docs alone.

**Requirements:** VER-06, DOC-02

</domain>

<decisions>
## Implementation Decisions

### legs — Which legs run and what spends money?
- **D-01 [legs]:** Plan + router live only, cheapest models, new decision file; v1.0 legs not re-run (P12's seam changes are JVM-proven). _(source: human)_ _(amended by D-13: W04 smoke added)_
- **D-13 [legs/w04-smoke]:** Add a live `gpt-6-astra` smoke for the W04 fix (P12 D-05) to P19's live legs, alongside plan + router. One bounded OpenAI call on the TESTER, re-proving PROV-16 at the P19 SHA. It must return the typed `ModelUnsupported`, never `http_error`. The W04 fix classifies astra's 400 and does not make it succeed. Reuse P12 plan 12-08's evidence line shape (`VAE_VERDICT leg=responses_probe verdict=PASS reason=model_unsupported …`) instead of minting a new one. Same rules as D-08: no key, no transcript, no tool args. Key via the test-keys workflow (`~/.claude/context/workflows/test-keys.md`): `push-test-key openai --device <TESTER> --package <sample appId>`, TESTER only. Never inline or paste a key. A missing key is a loud stop, not a skip. _(source: human — Yahir ruling via orchestrator 3b, 2026-10-05, control-plane 4146165)_

### router-leg — Router leg on device.
- **D-02 [router-leg]:** Live engine Router leg (classifier prompt, cheap-model selection, PickContext accounting run on a device before the immutable tag); ladder needs ≥2 LLM tiers or D-ROUTER-SKIP hides the picker. _(provisional — refresh at execution; depends on Phase 16)_ _(source: ai-auto)_

### plan-cache — The deferred `submit_plan` cache-prefix measurement.
- **D-03 [plan-cache]:** Defer explicitly in the carry register unless a fixture-backed leg can extend LE-7 redaction cheaply; never drop silently (SB 177 wants the number). _(provisional — refresh at execution; depends on Phase 15)_ _(source: ai-auto)_

### store — State behind the new legs.
- **D-04 [store]:** Stateful store (CannedToolExecutor has no targetIds, so binding and undo would pass vacuously). _(source: ai-auto)_

### offline-proof — What "offline, zero calls" means for the grammar leg.
- **D-05 [offline-proof]:** offlineOnly + explicit zero-attempt counts + a tripwire provider; no airplane mode needed. Grammar leg: one EN match, one ES match, one near-miss → `Unhandled(cappedByPolicy=true)` with zero calls. _(source: ai-auto)_

### undo-leg — Undo-all leg source.
- **D-06 [undo-leg]:** Offline scripted command + refusal sub-case; N counts committed only. _(provisional — refresh at execution; depends on Phase 17)_ _(source: ai-auto)_

### paths — Runner/guard/wiring paths broken by the v1.0 archive.
- **D-07 [paths]:** One shared stable per-release path; fix lands in P12 (first runner use, PROV-16) and P19/P20 inherit it. _(provisional — refresh at execution; depends on Phase 12)_ _(source: ai-auto)_

### evidence — Evidence lines for the new proofs.
- **D-08 [evidence]:** New line types in lockstep; no tier ids or slot values (Pitfall 26). _(source: ai-auto)_

### docs-state — Docs at the wiring SHA.
- **D-09 [docs-state]:** Final state at the wiring SHA (gate 7 forbids doc edits after it; v1.0.1 did the same). _(source: ai-auto)_

### snippets — Doc snippet compilation and coverage gate.
- **D-10 [snippets]:** All snippets in `:sample` DocSnippetsTest + generalized coverage gate from the module manifest (else C03 fails on undo coordinates and C20 passes vacuously). _(provisional — refresh at execution; depends on Phase 17)_ _(source: ai-auto)_

### wiring — Isolated wiring test scope.
- **D-11 [wiring]:** Four surfaces + keystore; voice-adapter covered by docs coverage, not wiring. Re-confirm the CLI still supports the isolation flags. _(source: ai-auto)_

### api-review — Consolidated frozen-surface API review.
- **D-12 [api-review]:** P19 artifact before the wiring SHA (a shape fix at P20 forces a doc + wiring rerun; after the tag it's frozen). _(source: ai-auto)_

### Claude's Discretion
Areas marked `ai-auto` took research's recommendation without operator review; the planner may refine mechanics within the stated decision but must not reverse it without a new discuss pass.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Milestone decisions
- `.planning/v1.1-DECISION-MAP.md` § Phase 19 — source of every decision above (options, recommendation, provisional flags)
- `.planning/cross-repo/R-v1.1-CONSUMER-ANSWERS.md` — SB/CT/stt/orchestrator answers and binding conditions

### Scope
- `.planning/ROADMAP.md` § Phase 19 — goal, success criteria
- `.planning/REQUIREMENTS.md` — VER-06, DOC-02
- `.planning/PROJECT.md` — constraints (domain-free, additive API, secrets, A1/A7)

### Research
- `.planning/research/SUMMARY.md`, `ARCHITECTURE.md`, `PITFALLS.md`, `FEATURES.md`, `STACK.md` (all under `.planning/research/`) — v1.1 milestone research

</canonical_refs>

<code_context>
## Existing Code Insights

Code-level assets, file:line anchors and integration points are cited inline in the decisions above and in `.planning/research/ARCHITECTURE.md`; the full scout happens at plan time.

### Cross-phase dependencies
Provisional decisions depend on Phase(s) 12, 15, 16, 17 — refresh them against that phase's real output at execution.

</code_context>

<specifics>
## Specific Ideas

Operator-reviewed decision(s) here (source: human) — treat them as locked: [legs], [legs/w04-smoke].

</specifics>

<deferred>
## Deferred Ideas

- `submit_plan` cache-prefix measurement — defer explicitly in the carry register unless a fixture-backed leg is cheap (see [plan-cache]).

</deferred>

---

*Phase: 19-sample-gate-1-docs*

## Runtime Decisions

- **RT-01 [ref-regex-doc] (2026-10-06, orchestrator 3b pre-tag ruling on P15 IN-04):** Accept the ASCII-only \s in the plan reference regex, since a missed near-reference stays literal, which is the safe direction. Document this in API.md (the PlanThenExecute reference-syntax section) at the wiring SHA.

- **RT-02 [p17-jitpack-rerun] (2026-10-06, master; P17 deferred obligation):** Before the v1.1.0 cut, in the P19 gate run, re-run scripts/jitpack-dry-run.sh and scripts/jitpack-live-probe.sh (the :undoalone probe) on the final tree. P17 IN-05/IN-06/WR-08 edited these scripts after the 17-10 quiet window, and the edits were checked only by bash -n and the manifest gates. Running the heavy scripts needs an orchestrator-granted quiet window ("quiet window 19-<plan>" handshake), with MemAvailable >= 5 GiB.

- **RT-02 pre-grant (2026-10-06, orchestrator 3b):** The quiet window for the RT-02 JitPack dry-run and :undoalone probe re-run is PRE-GRANTED on the same terms as P17 17-10: the master messages the orchestrator first, the orchestrator takes the VAE build lock and confirms, and only then does the run start; the master sends "quiet done" after.

- **RT-03 [p18-carries] (2026-10-07, master; from the P18 result):** Before the wiring SHA: (a) add the clean-cache :adapteralone consumer probe, which resolves voice-adapter with core and no :stt; (b) run scripts/verify-stt-confinement.sh with --selftest by hand; (c) re-run verify-negative-controls.sh and :voice-adapter:check on the final SHA. (a) and (c) are heavy and go in the same orchestrator quiet window as RT-02. Also drain the P18 Gate-2 fragment (uat-pending/18-voice-adapter.md) at milestone Gate-2.
