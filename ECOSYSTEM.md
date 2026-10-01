# ECOSYSTEM.md: the voice-action-engine hub & its consumers

> **Audience: coding agents (and humans) working in this hub or in any app that consumes it.**
> The authoritative pin is always each consumer's manifest + Gradle resolution; this doc is a
> best-effort cache. This hub uses **Mechanism B (Android / Gradle / JitPack)**. Repin procedure:
> `~/.claude/context/workflows/repin.md`; ecosystem map: `~/.claude/context/deps/_index.md`.

## The shape: hub + spokes

- **The hub, `voice-action-engine`** (this repo; JitPack group `com.github.Ygaray.voice-action-engine`,
  one coordinate per published module, see the table below).
  Generic, domain-agnostic: turns a transcript (+ detected language) into an app action through a
  consumer-composed tier ladder of strategies (LocalGrammar / SingleShot / PlanThenExecute /
  AgenticLoop) over pluggable providers (Anthropic / OpenAI / OpenRouter / on-device). Names no note,
  card, or food; consumers inject tools, grammar, resolvers, gates, and undo sinks at their
  composition root. STT-agnostic (depends on no other hub); the optional `:voice-adapter` bridges `:stt`.
- **Sibling hubs (no dependency edge in `:core`):** `stt-engine` (`:stt` bilingual capture),
  `yahirandroidtaste` (presentational AI-voice UI).

## Coordinates (per module, D-01)

Consumers depend on the per-module coordinates only. The old two-segment aggregator coordinate
(group `com.github.Ygaray`, artifact `voice-action-engine`) is retired and must not be used.

| Module | Packaging | Coordinate | Depends on |
|--------|-----------|------------|------------|
| `:core` | jar, pure Kotlin/JVM | `com.github.Ygaray.voice-action-engine:voice-action-engine-core:<version>` | nothing in the hub |
| `:providers` | jar | `com.github.Ygaray.voice-action-engine:voice-action-engine-providers:<version>` | `api` on `:core` and on OkHttp (4.12.0 compile floor; consumers keep their own OkHttp) |
| `:keystore` | aar | `com.github.Ygaray.voice-action-engine:voice-action-engine-keystore:<version>` | `api` on `:core` |

Planned for v1.1, **not yet published**: `voice-action-engine-undo` and `voice-action-engine-voice-adapter`.
The `:sample` app module is never published.

**Phase 1 proof:** all three modules resolve from an empty Gradle cache by commit SHA (first proven at
`7f9db2294461d76832116e33cc0a724f05f445e8`; the final phase-gate SHA is recorded alongside it in
`.planning/phases/01-scaffold-publishing-proof/evidence/jitpack-probe.txt`). Nothing is tagged yet.

| Consumer | Dev checkout | Pins hub at | Pin file |
|----------|--------------|-------------|----------|
| SecondBrain | `~/Projects/AndroidApps/Personal/SecondBrain` | *(not yet: Wave 1)* | `gradle/libs.versions.toml` |
| CalTracker | `~/Projects/AndroidApps/Personal/CalTracker_Android` | *(not yet: Wave 1)* | `app/build.gradle.kts` |

**Repo:** public at `github.com/Ygaray/voice-action-engine` (created 2026-09-29).

**Status:** v1.0 in verification (Phase 10: sample harness, Gate-1, docs; the tag is cut in Phase 11).

**Current published tag:** none. Staged plan (two milestones, A4): `v1.0.0` (contract + pipeline + providers + keystore +
2 ported strategies), `v1.1.0` (grammar / plan / router / on-device spike). See the contract, L8.

## The doc set an integrating agent receives

| Doc | What it holds |
|-----|---------------|
| [`README.md`](README.md) | Purpose, install (JitPack, per-module coordinates), a minimal compiled pipeline, links |
| [`INTEGRATION.md`](INTEGRATION.md) | Numbered adoption steps from repository to a rendered outcome, ending in notes and gotchas |
| [`API.md`](API.md) | The public surface at a glance, one section per area, extension points, safety model |

Every Kotlin block in these docs is a byte-equal copy of a region of
`sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/docs/DocSnippetsTest.kt`, which compiles and runs
them over the public API; `scripts/verify-docs-coverage.sh` checks that and the rest of the checklist. The working
example is the never-published `:sample` app in `sample/` (composition root:
`sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/SampleEngine.kt`).

## Invariants

- Consumers import the hub; the hub never imports a consumer.
- Public API grows **strictly additively** once tagged.
- The cross-repo contract (`CROSS-REPO-SCOPE-CONTRACT.md`) is frozen; changes = numbered amendments.
