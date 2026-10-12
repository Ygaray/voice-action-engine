# Phase 13: On-Device Model Spike - Context

**Gathered:** 2026-10-05
**Status:** Ready for planning
**Discussed via:** `/gsd-discuss-milestone` (mode: mixed)

<domain>
## Phase Boundary

The orchestrator, SB and CT know early, with numbers, whether a bundled ~2B on-device model is good enough for SingleShot-shaped commands on the S22 TESTER. If it is, apps can opt into it as an experimental provider. If not, nothing ships and the tag isn't blocked.

**Requirements:** SPIKE-01, SPIKE-02, SPIKE-03

</domain>

<decisions>
## Implementation Decisions

### model — Spike model set.
- **D-01 [model]:** Gemma 4 E2B (Apache-2.0, ungated) primary on LiteRT-LM 0.17.1, Gemma 3 1B as a light-RAM control on the small envelope only (its 4k KV can't hold the ~7k SB prefix). Verdict states "Gemma-2B" resolved to Gemma 4 E2B on LiteRT-LM (MediaPipe is maintenance-only). _(source: ai-auto)_

### toolchain — Kotlin 2.4 metadata / Java 21 AAR vs our Kotlin 2.3.20 / JVM 11.
- **D-02 [toolchain]:** Standalone compile/dex proof first; pin 0.16.1 on failure; both fail → red ("toolchain"). _(source: ai-auto)_

### harness — Where the spike harness lives.
- **D-03 [harness]:** Separate unpublished spike module, so a LiteRT compile failure can't break `:sample` (needed by Phase 12's PROV-16 smoke in parallel) and gate edits don't collide. Note: run-sample-gate1.sh `PHASE_DIR` still points at the archived `.planning/phases/10-*` path — fix before any :sample run. _(source: ai-auto)_

### path — Prompt path.
- **D-04 [path]:** Engine path (SingleShot + ON_DEVICE provider), Route A `ResponseFormat.json` and Route B native tool calls measured side by side. _(source: ai-auto)_

### envelopes — Prompt envelopes measured.
- **D-05 [envelopes]:** Both envelopes; per-envelope verdict (CT reads small, SB 179 reads SB-sized). _(source: ai-auto)_

### accuracy — Accuracy scoring.
- **D-06 [accuracy]:** Full gold set (PITFALLS #8 false-green trap). SB-fixture gold labels stay private. _(source: ai-auto)_

### thresholds — Green/red bar, locked before the first device run.
- **D-07 [thresholds]:** The voice-usable bar (anchored to the v1.0 cloud SingleShot baseline on the same TESTER: Haiku 612–1,391 ms, gpt-5.4-mini 1,946 ms). Time-box expiry with a gating metric unmeasured → red. _(source: human)_

### ship — Green-path module shape and write gating.
- **D-08 [ship]:** The `:ondevice` shape above with the documented app-side gating pattern (no core API change); ships in 13 only if it fits the time-box, else 13.1; rebases onto Phase 17 plumbing. Red → nothing ships, SPIKE-03 N/A-deferred. _(source: ai-auto)_

### sc4-gates — Make SC4 ("no ML dependency in :core/:providers") mechanically enforced.
- **D-09 [sc4-gates]:** Minimal hardening (tokens + :providers deny + hygiene patterns). _(source: ai-auto)_

### external — Open external facts for the spike plan (KV-prefix reuse across Conversations, ResponseFormat.json enum/nested support, Adreno 730 OpenCL support, consumer stdlib 2.4.0 fallout, 16 KB alignment deadline, repack license NOTICE).
- **D-10 [external]:** Plan-phase research for docs-answerable items (license, alignment, stdlib fallout); empirical for KV reuse, ResponseFormat and GPU — each a measured row in the verdict. _(source: ai-auto)_

### Claude's Discretion
Areas marked `ai-auto` took research's recommendation without operator review; the planner may refine mechanics within the stated decision but must not reverse it without a new discuss pass.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Milestone decisions
- `.planning/v1.1-DECISION-MAP.md` § Phase 13 — source of every decision above (options, recommendation, provisional flags)
- `.planning/cross-repo/R-v1.1-CONSUMER-ANSWERS.md` — SB/CT/stt/orchestrator answers and binding conditions

### Scope
- `.planning/ROADMAP.md` § Phase 13 — goal, success criteria
- `.planning/REQUIREMENTS.md` — SPIKE-01, SPIKE-02, SPIKE-03
- `.planning/PROJECT.md` — constraints (domain-free, additive API, secrets, A1/A7)

### Research
- `.planning/research/SUMMARY.md`, `ARCHITECTURE.md`, `PITFALLS.md`, `FEATURES.md`, `STACK.md` (all under `.planning/research/`) — v1.1 milestone research

</canonical_refs>

<code_context>
## Existing Code Insights

Code-level assets, file:line anchors and integration points are cited inline in the decisions above and in `.planning/research/ARCHITECTURE.md`; the full scout happens at plan time.

</code_context>

<specifics>
## Specific Ideas

Operator-reviewed decision(s) here (source: human) — treat them as locked: [thresholds].

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 13-on-device-model-spike*

## Runtime Decisions

- **RT-01 [tester-window] (2026-10-05):** GRANT, from orchestrator yahir-gsd-control-plane-3b. TESTER R5CT10XNKQN is granted for one window of at most 14400 s, covering 13-08 and then 13-09. `adb -s R5CT10XNKQN` only. The window is released right after 13-08 cleanup, and the master messages "device done tester". Earlier inputs: sb_labels is the current SB fixture 8bc739ed (19 tools, tool count reported); gemma3_1b is skipped_gated (orchestrator ruling).
- **RT-02 [sb-gold-labels] (2026-10-05):** Use the VAE-authored SB gold labels, because SB has no sb-gold.json. In the 13-09 verdict, label SB-envelope accuracy "VAE-authored labels, SB review pending". SB reviews the labels in parallel and raises objections only. Keep the raw per-item outputs host-private (never committed) so the SB rows can be re-scored on the host, with no device re-run, if SB objects.
- **RT-03 [sb-labels-reviewed + grading-tolerance] (2026-10-05):** SB reviewed the VAE-authored sb-gold labels (via orchestrator 3b) and has NO objection to any of the 144 items (tools, args and enums match fixture 8bc739ed and SB's system prompt; neg_018-020 and neg_038-040 are correctly non_mutating because the first call is a read). Supersedes RT-02's wording: the 13-09 verdict labels SB-envelope accuracy "VAE-authored labels, SB-reviewed, no objection". ONE grading tolerance, not a relabel: find_tags does not fold plurals, so for b_en_041, b_en_044, b_es_041, b_es_042 and b_es_044 a singular-stem tag query scores as equally correct; list-item articles ("a scarf" vs "scarf") are acceptable either way. Implemented in the host-side scorer and documented in the verdict; if the device leg was scored before the tolerance, re-score those items on the host from the saved host-private raw outputs, never re-run the device.
