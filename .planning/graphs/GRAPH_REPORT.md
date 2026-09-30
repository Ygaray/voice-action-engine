# Graph Report - voice-action-engine  (2026-09-29)

## Corpus Check
- 20 files · ~114,030 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 339 nodes · 324 edges · 19 communities
- Extraction: 100% EXTRACTED · 0% INFERRED · 0% AMBIGUOUS
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `e9b078f5`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- [[_COMMUNITY_Cross-Repo Scope Contract — Bilingual Voice Commands + `voice-action-engine`|Cross-Repo Scope Contract — Bilingual Voice Commands + `voice-action-engine`]]
- [[_COMMUNITY_HANDOFF voice-action-engine, multi-repo milestone effort vae-bilingual|HANDOFF: voice-action-engine, multi-repo milestone effort "vae-bilingual"]]
- [[_COMMUNITY_voice-action-engine|voice-action-engine]]
- [[_COMMUNITY_5. Shared contract — seams (the coherence anchor)|5. Shared contract — seams (the coherence anchor)]]
- [[_COMMUNITY_ECOSYSTEM.md the voice-action-engine hub & its consumers|ECOSYSTEM.md: the voice-action-engine hub & its consumers]]
- [[_COMMUNITY_Requirements|Requirements]]
- [[_COMMUNITY_CROSS-REPO-SCOPE-CONTRACT|CROSS-REPO-SCOPE-CONTRACT.md]]
- [[_COMMUNITY_6. Per-repo milestone slices|6. Per-repo milestone slices]]
- [[_COMMUNITY_Table Stakes (consumers can't migrate without these)|Table Stakes (consumers can't migrate without these)]]
- [[_COMMUNITY_Pitfalls Research|Pitfalls Research]]
- [[_COMMUNITY_Architecture Research|Architecture Research]]
- [[_COMMUNITY_Anti-Patterns|Anti-Patterns]]
- [[_COMMUNITY_3. PreApplyGate + CommitSink one seam for SingleShot and AgenticLoop|3. PreApplyGate + CommitSink: one seam for SingleShot and AgenticLoop]]
- [[_COMMUNITY_Architectural Patterns|Architectural Patterns]]
- [[_COMMUNITY_2. Provider-neutral transcript model and per-provider mappers|2. Provider-neutral transcript model and per-provider mappers]]
- [[_COMMUNITY_Project Research Summary|Project Research Summary]]
- [[_COMMUNITY_Implications for Roadmap|Implications for Roadmap]]
- [[_COMMUNITY_2. Provider-neutral transcript model and per-provider mappers|2. Provider-neutral transcript model and per-provider mappers]]
- [[_COMMUNITY_Project State|Project State]]

## God Nodes (most connected - your core abstractions)
1. `Communities (21 total, 0 thin omitted)` - 22 edges
2. `Architecture Research` - 19 edges
3. `A1: COMPILE floor for :providers. Never raise this — consumers pick their own OkHttp.` - 16 edges
4. `Critical Pitfalls` - 16 edges
5. `Stack Research` - 16 edges
6. `Implications for Roadmap` - 13 edges
7. `v1 Requirements (milestone v1.0 → tag `v1.0.0`)` - 12 edges
8. `Pitfalls Research` - 12 edges
9. `Cross-Repo Scope Contract — Bilingual Voice Commands + `voice-action-engine`` - 12 edges
10. `Phase Details` - 11 edges

## Surprising Connections (you probably didn't know these)
- None detected - all connections are within the same source files.

## Import Cycles
- None detected.

## Communities (19 total, 0 thin omitted)

### Community 0 - "Cross-Repo Scope Contract — Bilingual Voice Commands + `voice-action-engine`"
Cohesion: 0.10
Nodes (18): 10. Amendments, 11. Tag protocol & ledger (A12), 1. Mission, 2. Locked decisions, 3. Core model — two orthogonal axes, 4. The approaches (what `voice-action-engine` ships), 5.1 Engine-owned types (`:core`, pure Kotlin, JVM-testable), 5.2 App-injected seams (plug-ins each consumer designs) (+10 more)

### Community 1 - "HANDOFF: voice-action-engine, multi-repo milestone effort "vae-bilingual""
Cohesion: 0.22
Nodes (8): Dependencies, HANDOFF: voice-action-engine, multi-repo milestone effort "vae-bilingual", How this effort runs (same text in every repo), Peers, RECONVENE-BRIEF.md template, Repo facts, What Yahir confirmed (and where), Your slice: §6.2, the NEW engine hub → two milestones, two tags (A4) (Wave 0, the long pole)

### Community 2 - "voice-action-engine"
Cohesion: 0.15
Nodes (12): Active, Constraints, Context, Core Value, Evolution, Key Decisions, Out of Scope (this milestone), Requirements (+4 more)

### Community 3 - "5. Shared contract — seams (the coherence anchor)"
Cohesion: 0.12
Nodes (16): Critical Pitfalls, Pitfall 10: Cancellation swallowed, timeouts misclassified, responses leaked, Pitfall 11: `:keystore` generalization strands existing users' keys, Pitfall 12: Public API "strictly additive" that still breaks consumers (sealed/enum growth, data classes), Pitfall 13: Privacy and correctness in provider fallback (offline-only leaks to cloud; wrong key sent), Pitfall 14: Secrets and user content leaking through `toString`, exceptions, error bodies and the trace, Pitfall 15: A "zero-baseline" detekt that's clean only because rules are skipped or suppressed, Pitfall 1: The advertised JitPack coordinate is the wrong artifact (multi-module groupId coercion + aggregator POM) (+8 more)

### Community 4 - "ECOSYSTEM.md: the voice-action-engine hub & its consumers"
Cohesion: 0.50
Nodes (3): ECOSYSTEM.md: the voice-action-engine hub & its consumers, Invariants, The shape: hub + spokes

### Community 5 - "Requirements"
Cohesion: 0.18
Nodes (10): Community Hubs (Navigation), Corpus Check, God Nodes (most connected - your core abstractions), Graph Freshness, Graph Report - voice-action-engine  (2026-09-29), Import Cycles, Knowledge Gaps, Suggested Questions (+2 more)

### Community 6 - "CROSS-REPO-SCOPE-CONTRACT.md"
Cohesion: 0.09
Nodes (22): Communities (21 total, 0 thin omitted), Community 0 - "Cross-Repo Scope Contract — Bilingual Voice Commands + `voice-action-engine`", Community 10 - "Architecture Research", Community 11 - "Anti-Patterns", Community 12 - "3. PreApplyGate + CommitSink: one seam for SingleShot and AgenticLoop", Community 13 - "Architectural Patterns", Community 14 - "2. Provider-neutral transcript model and per-provider mappers", Community 15 - "Project Research Summary" (+14 more)

### Community 7 - "6. Per-repo milestone slices"
Cohesion: 0.08
Nodes (25): A. Pipeline & outcomes (`:core`, step 2), Add After Validation (v1.x), Anti-Features (deliberately NOT building), B. Tool declaration & choice (steps 3a/3b/5), C. Agentic loop (steps 6a/6b), Competitor Feature Analysis, D. Typed failure reasons (the "loud, specific" Core Value), Dependency Notes (+17 more)

### Community 8 - "Table Stakes (consumers can't migrate without these)"
Cohesion: 0.09
Nodes (21): Decision Map — v1.0 Core Engine, Gray Areas, Gray Areas, Gray Areas, Gray Areas, Gray Areas, Gray Areas, Gray Areas (+13 more)

### Community 9 - "Pitfalls Research"
Cohesion: 0.17
Nodes (11): Integration Gotchas, "Looks Done But Isn't" Checklist, Open Questions (flag to orchestrator / later phases), Performance Traps, Pitfall-to-Phase Mapping, Pitfalls Research, Recovery Strategies, Security Mistakes (+3 more)

### Community 10 - "Architecture Research"
Cohesion: 0.05
Nodes (38): 0. The answer in one screen, 1. Module boundaries and dependency direction, 2.1 Wire-shape comparison (what the mappers translate), 2.2 Neutral → wire mapping rules (put these in `:providers` golden tests), 2.3 Which OpenAI API to target: Chat Completions for OpenAI and OpenRouter in v1.0, 2.4 Where prompt caching lives, 2. Provider-neutral transcript model and per-provider mappers, 4. The §5.2 seam set (plus the resolver seams) (+30 more)

### Community 11 - "Anti-Patterns"
Cohesion: 0.33
Nodes (6): Architectural Patterns, Pattern 1: Per-call resolution through app seams, with no DI and no storage reads, Pattern 2: Neutral transcript with native replay, Pattern 3: Prepare → Gate → Commit (the unified PreApplyGate/CommitSink seam), Pattern 4: Capabilities are data, and apps can override them, Pattern 5: "Derive, don't trust" the app's OkHttpClient

### Community 12 - "3. PreApplyGate + CommitSink: one seam for SingleShot and AgenticLoop"
Cohesion: 0.29
Nodes (7): 3.1 The two source shapes, and why they reconcile, 3.2 The engine types, 3.3 The coordinator: the only code path that writes, 3.4 How each strategy exercises it, 3.5 Invariants the pipeline enforces (carried over from both ports), 3.6 The generic suspending gate helper (ports SB `VoiceConfirmGate`), 3. PreApplyGate + CommitSink: one seam for SingleShot and AgenticLoop

### Community 13 - "Architectural Patterns"
Cohesion: 0.11
Nodes (18): Agentic Loop Strategy (step 6b), Build, Publishing & Quality Gates (step 1), Commit, Gate & Undo Seam (steps 2, 6b; A2, A6, A17, E1), Core Contract & Pipeline (step 2), Keystore (step 4), Later additive options, Multi-turn Mappers (step 6a), Out of Scope (+10 more)

### Community 14 - "2. Provider-neutral transcript model and per-provider mappers"
Cohesion: 0.09
Nodes (22): A1: OkHttp compile floor 4.12, green on 4.12.x AND 5.x (must-pass), Alternatives Considered, Build Configuration Sketch, Core Technologies, detekt Configuration: a genuinely clean zero baseline, Development Tools, How to build the matrix (recommended: in-build, one `./gradlew check` runs every leg), Installation (dependency declarations) (+14 more)

### Community 15 - "Project Research Summary"
Cohesion: 0.07
Nodes (27): Architecture Approach, Confidence Assessment, Conflicts Reconciled, Critical Pitfalls, Executive Summary, Expected Features, Gaps to Address, Implications for Roadmap (+19 more)

### Community 16 - "Implications for Roadmap"
Cohesion: 0.06
Nodes (31): A1: COMPILE floor for :providers. Never raise this — consumers pick their own OkHttp., A1: OkHttp compile floor 4.12, green on 4.12.x AND 5.x (must-pass), Alternatives Considered, Architecture, Build Configuration Sketch, Constraints, Conventions, Core Technologies (+23 more)

### Community 17 - "2. Provider-neutral transcript model and per-provider mappers"
Cohesion: 0.11
Nodes (18): Dependencies & Parallelism, Milestones, Overview, Phase 10: Sample Harness, Gate-1 & Tag, Phase 1: Scaffold & Publishing Proof, Phase 2: Core Contract, Pipeline & Commit Seam, Phase 3: Transcript Types, ProviderRouter & On-Device Gate, Phase 4: Anthropic Transport & OkHttp Matrix (+10 more)

### Community 18 - "Project State"
Cohesion: 0.18
Nodes (10): Accumulated Context, Blockers/Concerns, Current Position, Decisions, Deferred Items, Pending Todos, Performance Metrics, Project Reference (+2 more)

## Knowledge Gaps
- **265 isolated node(s):** `Constraints`, `Technology Stack`, `TL;DR: the prescriptive picks`, `Core Technologies`, `Module Layout` (+260 more)
  These have ≤1 connection - possible missing edges or undocumented components.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Architecture Research` connect `Architecture Research` to `Anti-Patterns`, `3. PreApplyGate + CommitSink: one seam for SingleShot and AgenticLoop`?**
  _High betweenness centrality (0.021) - this node is a cross-community bridge._
- **Why does `Communities (21 total, 0 thin omitted)` connect `CROSS-REPO-SCOPE-CONTRACT.md` to `Requirements`?**
  _High betweenness centrality (0.008) - this node is a cross-community bridge._
- **What connects `Constraints`, `Technology Stack`, `TL;DR: the prescriptive picks` to the rest of the system?**
  _265 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Cross-Repo Scope Contract — Bilingual Voice Commands + `voice-action-engine`` be split into smaller, more focused modules?**
  _Cohesion score 0.1 - nodes in this community are weakly interconnected._
- **Should `5. Shared contract — seams (the coherence anchor)` be split into smaller, more focused modules?**
  _Cohesion score 0.125 - nodes in this community are weakly interconnected._
- **Should `CROSS-REPO-SCOPE-CONTRACT.md` be split into smaller, more focused modules?**
  _Cohesion score 0.09090909090909091 - nodes in this community are weakly interconnected._
- **Should `6. Per-repo milestone slices` be split into smaller, more focused modules?**
  _Cohesion score 0.07692307692307693 - nodes in this community are weakly interconnected._