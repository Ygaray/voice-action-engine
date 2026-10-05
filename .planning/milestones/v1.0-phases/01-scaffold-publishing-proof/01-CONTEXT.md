# Phase 1: Scaffold & Publishing Proof - Context

**Gathered:** 2026-09-29
**Status:** Ready for planning (after orchestrator GO — A13)

<domain>
## Phase Boundary

A consumer can resolve every published engine module from JitPack at its per-module coordinate. From the first commit on, one `./gradlew check` enforces the library's structural invariants and provides the test harnesses every later phase builds on.

</domain>

<decisions>
## Implementation Decisions

### artifact-ids
- **D-01 [artifact-ids]:** Prefixed artifactIds everywhere: `com.github.Ygaray.voice-action-engine:voice-action-engine-{core,providers,keystore}` (v1.1: `voice-action-engine-undo`, `voice-action-engine-voice-adapter`). Orchestrator ruling; E7 aligns A18. Fix ECOSYSTEM.md/README.md aggregator coordinate in this phase. _(source: human)_

### jitpack-metadata
- **D-02 [jitpack-metadata]:** Explicit E5 groupId, keep .module metadata, prove jar→jar and AAR→jar resolution by commit SHA from a clean cache (stt v0.6.0 proves only AAR→AAR on a tag); fallback: disable module metadata on JitPack _(source: ai-auto)_

### sample-fixture
- **D-03 [sample-fixture]:** Inert com.android.application shell (no publish, no config-time reads), excluded from jitpack.yml; fixture at sample/src/debug/assets/sb-a10-fixture.json (gitignored) with a runtime loud error, plus a repo-wide glob ignore for sb-a10-fixture*.json and /graphify-out/ _(source: ai-auto)_

### detekt-config
- **D-04 [detekt-config]:** buildUponDefaultConfig=true, maxIssues: 0, no baseline file (and a check that none exists); tune rules deliberately (e.g. allow the broad catch only in the single collapse helper) rather than banking debt _(source: ai-auto)_

### invariant-scan
- **D-05 [invariant-scan]:** detekt ForbiddenImport + ForbiddenComment for what works syntax-only, plus a source-scan Gradle task under check for runCatching/println/printStackTrace/FQ DI annotations/baseline-file existence, each proven with a planted negative control _(source: ai-auto)_

### structural-checks
- **D-06 [structural-checks]:** Allowlist + bytecode scan + compile-floor assertion as Gradle checks under check (the compile-floor assertion also closes Phase 4's catalog-creep gap) _(source: ai-auto)_

### metalava
- **D-07 [metalava]:** Apply Metalava with apiDump/apiCheck aliases (YAT), guard compatibility with onlyIf { api.txt exists } so enforcement switches on automatically when the cut commits the dump; add a javap supplement for value-class members at the cut _(source: ai-auto)_

### explicit-api
- **D-08 [explicit-api]:** -Xexplicit-api=strict compiler flag uniformly (no dependence on AGP 9's partial kotlin {} extension), verified by a planted public-without-modifier negative control _(source: ai-auto)_

### test-fixtures
- **D-09 [test-fixtures]:** java-test-fixtures on :core with the testFixtures variants skipped from components["java"], verified by inspecting the published POM/.module (no -test-fixtures artifact); Phase 1 is scaffolding only (no public placeholder types) _(source: ai-auto)_

### matrix
- **D-10 [matrix]:** In-build extra Test tasks (okhttp + legacy mockwebserver moved together, okhttp-jvm variant attributes), reflective version guard per leg; fallback to the -P switch if attribute plumbing fails; Call.await bridge stays in Phase 4 _(source: ai-auto)_

### ext-build
- **D-11 [ext-build]:** Needs external research/probe: kotlin.jvm 2.3.20 next to AGP 9.2.1 built-in Kotlin; JitPack jar→jar/AAR→jar on commit SHA (and whether the aggregator lists :sample); okhttp-jvm variant attributes on Gradle 9.4.1; Metalava 0.5.1 on kotlin.jvm with a missing api.txt; detekt ForbiddenComment regex on planted planning ids _(source: ai-auto)_



### r1-verdict
- **D-12 [r1-verdict]:** Publishing probes (jar→jar and AAR→jar via JitPack by commit SHA, Metalava on kotlin.jvm) run FIRST; if any fallback changes consumer coordinates or module shape, message the orchestrator before Phase 2. _(source: human — orchestrator R1 GO-WITH-CHANGES)_

### Claude's Discretion
Anything not listed above follows `.planning/research/SUMMARY.md` and the phase's own research; Source `ai-auto` decisions took research's recommendation (orchestrator accepted the auto-resolved remainder).

</decisions>

<canonical_refs>
## Canonical References

- `CROSS-REPO-SCOPE-CONTRACT.md` — §6.2, §5.1–5.2, §10 (A1–A18, E1–E7), §11 (authoritative; never edited here)
- `.planning/ROADMAP.md` — this phase's goal, requirements, success criteria
- `.planning/REQUIREMENTS.md` — requirement text
- `.planning/v1.0-DECISION-MAP.md` — § Phase 1 (source of these decisions)
- `.planning/research/SUMMARY.md` (+ STACK/FEATURES/ARCHITECTURE/PITFALLS.md)
- `.planning/cross-repo/HANDOFF.md` — cross-repo rules, orchestrator, devices

</canonical_refs>

<code_context>
## Existing Code Insights

Greenfield repo; port sources are read-only in SecondBrain (`app/src/main/java/com/example/secondbrain/core/agent/`) and CalTracker (`app/src/main/java/com/caltracker/app/ai/`, `mcp/`, `ui/voice/`). File:line evidence per decision is in the decision map's analyzer sources; concrete reuse is surfaced at plan time.

</code_context>

<specifics>
## Specific Ideas

None beyond the decisions above.

</specifics>

<deferred>
## Deferred Ideas

See REQUIREMENTS.md v2 / LATER items.

</deferred>
