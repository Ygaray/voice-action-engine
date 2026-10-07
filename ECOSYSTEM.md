# ECOSYSTEM.md: the voice-action-engine hub & its consumers

> **Audience: coding agents (and humans) working in this hub or in any app that consumes it.**
> The authoritative pin is always each consumer's manifest + Gradle resolution; this doc is a
> best-effort cache. This hub uses **Mechanism B (Android / Gradle / JitPack)**: a consumer repins by changing the
> version in its own dependency declaration to a new immutable release tag, in its own repository.

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
| `:undo` | jar, pure Kotlin/JVM | `com.github.Ygaray.voice-action-engine:voice-action-engine-undo:<version>` | nothing, not even `:core` (only the Kotlin standard library) |
| `:voice-adapter` | aar, minSdk 35 | `com.github.Ygaray.voice-action-engine:voice-action-engine-voice-adapter:<version>` | `api` on `:core`, and `compileOnly` on `:stt` (`com.github.Ygaray.voice-engine-android:voice-engine-android`, v0.7.0 or newer), so the app adds `:stt` itself |

The undo module and the adapter (both rows are in the table above) are new in v1.1 and **not yet published**; the first tag that carries them is v1.1.0.
The `:sample` app module is never published.

The adapter (artifact `voice-action-engine-voice-adapter`) is optional. It turns one final `:stt` segment into a `CommandInput`; the engine core never depends on it or
on `:stt`, so an app that does not add it never pulls `:stt`. The app keeps its own multi-segment session aggregation
(see INTEGRATION.md section 12).

**Phase 1 proof:** all three modules resolve from an empty Gradle cache by commit SHA (first proven at
`7f9db2294461d76832116e33cc0a724f05f445e8`; the final phase-gate SHA is recorded alongside it in
`.planning/phases/01-scaffold-publishing-proof/evidence/jitpack-probe.txt`).

### Machine-reconciled pin matrix

> The rows between the markers are maintained by the repin tooling; do not hand-edit them. Pin files:
> SecondBrain `gradle/libs.versions.toml`, CalTracker `app/build.gradle.kts`.

<!-- repin-matrix:begin -->
| Consumer | Pinned | Latest | Status |
|---|---|---|---|
| CalTracker_Android | v1.0.1 | v1.0.1 | current |
| SecondBrain | v1.0.1 | v1.0.1 | current |
<!-- repin-matrix:end -->

**Repo:** public at `github.com/Ygaray/voice-action-engine` (created 2026-09-29).

**Status:** the v1.0 core engine is released; v1.1 is planned.

**Published tags:** `v1.0.0` (contract + pipeline + providers + keystore + 2 ported strategies, 2026-10-02) and the
`v1.0.x` patch releases after it; the version to pin is named once, in the README ("Version to pin"). The repository's git
tags are the list of releases, and the §11 ledger in `CROSS-REPO-SCOPE-CONTRACT.md` records each one. Staged plan (two
milestones, A4): `v1.0.0`, then `v1.1.0` (grammar / plan / router / on-device spike). See the contract, L8.

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
