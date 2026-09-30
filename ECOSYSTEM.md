# ECOSYSTEM.md: the voice-action-engine hub & its consumers

> **Audience: coding agents (and humans) working in this hub or in any app that consumes it.**
> The authoritative pin is always each consumer's manifest + Gradle resolution; this doc is a
> best-effort cache. This hub uses **Mechanism B (Android / Gradle / JitPack)**. Repin procedure:
> `~/.claude/context/workflows/repin.md`; ecosystem map: `~/.claude/context/deps/_index.md`.

## The shape: hub + spokes

- **The hub, `voice-action-engine`** (this repo; JitPack `com.github.Ygaray:voice-action-engine`).
  Generic, domain-agnostic: turns a transcript (+ detected language) into an app action through a
  consumer-composed tier ladder of strategies (LocalGrammar / SingleShot / PlanThenExecute /
  AgenticLoop) over pluggable providers (Anthropic / OpenAI / OpenRouter / on-device). Names no note,
  card, or food; consumers inject tools, grammar, resolvers, gates, and undo sinks at their
  composition root. STT-agnostic (depends on no other hub); the optional `:voice-adapter` bridges `:stt`.
- **Sibling hubs (no dependency edge in `:core`):** `stt-engine` (`:stt` bilingual capture),
  `yahirandroidtaste` (presentational AI-voice UI).

| Consumer | Dev checkout | Pins hub at | Pin file |
|----------|--------------|-------------|----------|
| SecondBrain | `~/Projects/AndroidApps/Personal/SecondBrain` | *(not yet: Wave 1)* | `gradle/libs.versions.toml` |
| CalTracker | `~/Projects/AndroidApps/Personal/CalTracker_Android` | *(not yet: Wave 1)* | `app/build.gradle.kts` |

**Current published tag:** none. Staged plan: `v1.0.0` (contract + pipeline + providers + keystore +
2 ported strategies), `v1.1.0` (grammar / plan / router / on-device spike). See the contract, L8.

## Invariants

- Consumers import the hub; the hub never imports a consumer.
- Public API grows **strictly additively** once tagged.
- The cross-repo contract (`CROSS-REPO-SCOPE-CONTRACT.md`) is frozen; changes = numbered amendments.
