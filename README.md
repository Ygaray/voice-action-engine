# voice-action-engine

Bilingual (EN/ES) voice commands for Android apps: a tiered pipeline (local grammar → single-shot → plan-then-execute → agentic loop), multi-provider with bring-your-own key.

**Status:** pre-scaffold. Nothing is built yet. The scope and shared seams are frozen in
[`CROSS-REPO-SCOPE-CONTRACT.md`](CROSS-REPO-SCOPE-CONTRACT.md). This repo's first GSD milestone
(`/gsd-new-milestone`) scaffolds the Gradle/JitPack project against that contract.

Planned coordinate: `com.github.Ygaray:voice-action-engine` (JitPack), modules `:core` (pure Kotlin), `:providers` (OkHttp transports),
`:keystore` (Android AES/GCM BYO-key), `:voice-adapter` (optional `:stt` glue).
