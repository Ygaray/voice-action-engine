# voice-action-engine

Bilingual (EN/ES) voice commands for Android apps: a tiered pipeline (local grammar → single-shot → plan-then-execute → agentic loop), multi-provider with bring-your-own key.

**Status:** the four-module scaffold exists (`:core`, `:providers`, `:keystore`, `:sample`) and publishing is proven
by commit SHA on JitPack, but nothing is tagged yet. The scope and shared seams are frozen in
[`CROSS-REPO-SCOPE-CONTRACT.md`](CROSS-REPO-SCOPE-CONTRACT.md).

## Coordinates (JitPack, per module)

- `com.github.Ygaray.voice-action-engine:voice-action-engine-core` (pure Kotlin/JVM)
- `com.github.Ygaray.voice-action-engine:voice-action-engine-providers` (OkHttp transports, 4.12.0 compile floor)
- `com.github.Ygaray.voice-action-engine:voice-action-engine-keystore` (Android AES/GCM bring-your-own-key store)

Planned for v1.1, not yet published: `voice-action-engine-undo` and `voice-action-engine-voice-adapter`.
The `:sample` app is never published. See [`ECOSYSTEM.md`](ECOSYSTEM.md) for the full table.
