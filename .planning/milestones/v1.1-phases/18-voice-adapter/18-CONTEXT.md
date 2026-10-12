# Phase 18: Voice Adapter - Context

**Gathered:** 2026-10-05
**Status:** Ready for planning
**Discussed via:** `/gsd-discuss-milestone` (mode: mixed)

<domain>
## Phase Boundary

An app that captures speech with `:stt` can turn a final transcript segment into a `CommandInput` with one call, and `:core` still never depends on another hub.

**Requirements:** ADPT-01

</domain>

<decisions>
## Implementation Decisions

### minsdk — `:voice-adapter` minSdk.
- **D-01 [minsdk]:** 35. Below API 34 `:stt` "auto" stamps a literal "en" without detection (SttEngineImpl.kt:1018), which the adapter can't distinguish from a real detection; lowering later is additive, raising after the tag breaks consumers. Module is a `com.android.library` AAR on `:keystore`'s recipe with explicit artifactId `voice-action-engine-voice-adapter`; JVM tests only (FinalSegment has a public ctor). — **Reversibility:** one-way — raising minSdk after the tag breaks consumers (lowering is additive) _(source: ai-auto)_

### stt-scope — How `:stt` is declared.
- **D-02 [stt-scope]:** compileOnly + POM-absence gate + documented minimum (avoids pushing OkHttp 5.2.1/corrections/coroutines-android and an :stt floor onto consumers — the A1 problem again). Repo: `exclusiveContent` jitpack.io filtered to `com.github.Ygaray.voice-engine-android`, per-module coordinate, never the aggregator. _(provisional — refresh at execution; depends on Phase 17)_ _(source: ai-auto)_

### gate — Who owns the "only :voice-adapter depends on :stt" proof (SC-2)?
- **D-03 [gate]:** Phase 18 owns the :stt-specific gate and repo; Phase 17 the generic include/jitpack.yml/allowedEdges/manifest. _(provisional — refresh at execution; depends on Phase 17)_ _(source: ai-auto)_

### lang-norm — Language label normalization.
- **D-04 [lang-norm]:** Closed set (matches SB `SttAutoLanguage.normalizeLabel` and CT `normalizeDetectedLanguage` today, so adopting the adapter changes no behavior). _(source: ai-auto)_

### surface — Single segment or a multi-segment joiner?
- **D-05 [surface]:** Single segment only (SB longest-wins and CT last-non-null differ; a frozen joiner fits neither). Update ROADMAP/consumer map: apps keep their own session aggregation. _(source: ai-auto)_

### mapping — Mapping behavior.
- **D-06 [mapping]:** Pure and total (CommandInput performs no validation; apps already guard blank text). _(source: ai-auto)_

### stt-semantics — Are `:stt` labels "detections"? (server path always emits en/es with an "en" fallback at probability 0.0; sub-34 native stamps "en").
- **D-07 [stt-semantics]:** Pass through + document now; raise the detected-vs-fallback signal to stt-engine via the orchestrator as a future `:stt` item (not this phase's scope). Also find the first `:stt` tag whose FinalSegment has `getLanguage()` for the documented minimum. _(source: ai-auto)_
  - **Consumer condition (binding):** stt answer #9: detected-vs-fallback signal deferred to stt v3.2 seed 7b1f660; nothing blocks v1.1. At minSdk 35, native `language == null` means undetected → pass labels through, treat null as unknown; document the server path's fallback-to-"en".

### Claude's Discretion
Areas marked `ai-auto` took research's recommendation without operator review; the planner may refine mechanics within the stated decision but must not reverse it without a new discuss pass.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Milestone decisions
- `.planning/v1.1-DECISION-MAP.md` § Phase 18 — source of every decision above (options, recommendation, provisional flags)
- `.planning/cross-repo/R-v1.1-CONSUMER-ANSWERS.md` — SB/CT/stt/orchestrator answers and binding conditions

### Scope
- `.planning/ROADMAP.md` § Phase 18 — goal, success criteria
- `.planning/REQUIREMENTS.md` — ADPT-01
- `.planning/PROJECT.md` — constraints (domain-free, additive API, secrets, A1/A7)

### Research
- `.planning/research/SUMMARY.md`, `ARCHITECTURE.md`, `PITFALLS.md`, `FEATURES.md`, `STACK.md` (all under `.planning/research/`) — v1.1 milestone research

</canonical_refs>

<code_context>
## Existing Code Insights

Code-level assets, file:line anchors and integration points are cited inline in the decisions above and in `.planning/research/ARCHITECTURE.md`; the full scout happens at plan time.

### Cross-phase dependencies
Provisional decisions depend on Phase(s) 17 — refresh them against that phase's real output at execution.

</code_context>

<specifics>
## Specific Ideas

No specific requirements beyond the decisions above — open to standard approaches.

</specifics>

<deferred>
## Deferred Ideas

- stt detected-vs-fallback label signal (`languageSource`/`languageDetected`) — deferred to stt v3.2 seed 7b1f660.

</deferred>

---

*Phase: 18-voice-adapter*

## Runtime Decisions

- **[stt-scope] refreshed (2026-10-06, ai-auto; dependency P17 complete):** Unchanged: compileOnly on the stt engine, a POM-absence gate, and a documented minimum. That avoids pushing OkHttp 5.2.1, corrections, coroutines-android or an :stt floor onto consumers (A1 again). Repo: exclusiveContent jitpack.io filtered to com.github.Ygaray.voice-engine-android, per-module coordinate, never the aggregator.
- **[gate] refreshed (2026-10-06, ai-auto; dependency P17 complete):** P17 shipped the generic plumbing, so P18 only ADDS rows to it. It adds the adapter module row to scripts/modules.list (columns: name packaging artifactId kotlinPackage dependsOnCore; every script reads it through scripts/lib/modules.sh) and its entry in gradle/invariants.gradle.kts allowedEdges (plus sampleRequiredEdges/sampleAllowedEdges if :sample wires it). jitpack.yml, verify-module-manifest.sh and verify-release-manifest.sh pick it up from the manifest, so P18 does not re-implement them. P18 owns ONLY the :stt-specific parts: the POM-absence gate (the stt artifact must not appear in the published POM) and the exclusiveContent repo. Release-cut gates 10/12 and selftest step 4 are P20 work (20-CONTEXT RT-02) and also need the new module branch.
- **RT-01 [actionevent-tostring] (2026-10-06, orchestrator 3b IN-04 ruling, see 20-CONTEXT RT-03):** If it fits in P18, implement redact-by-default ActionEvent.toString() (ids, type, tier, status and counts only; never arg values, utterance text, model output or keys, which render as <redacted:N chars>; any debug accessor is opt-in) plus one sentinel-never-in-toString test. Otherwise P20 does it.

- **RT-02 [quiet-window] (2026-10-06):** CONFIRMED by orchestrator yahir-gsd-control-plane-3b, which holds the VAE build lock (control-plane 5e8eca1), after Yahir reset swap. Host: MemAvailable 8.7 GiB, swap 1.1 GB free, a mempalace mine (3.3 GB) still running. Rules: at most one Gradle daemon (or --no-daemon); check memory between steps; STOP if MemAvailable < 5 GiB. If earlyoom kills a step, re-run that step ONCE only, then report. The master sends "quiet done" when 18-08 finishes.
