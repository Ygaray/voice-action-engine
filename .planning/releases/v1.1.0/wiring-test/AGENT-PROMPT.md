# Task: wire the voice-action-engine library into a scratch consumer, using only its documentation

You are in a scratch Android/Kotlin Gradle workspace (the current directory). It has two modules, `:jvmconsumer`
(plain Kotlin/JVM) and `:app` (an Android application), and no dependency on the engine yet. The engine is a library
published to the Maven repository already declared in `settings.gradle.kts` (do not change the `repositories` block);
the version to use is `{{VERSION}}`.

## Rules

- Read ONLY files under this directory. Start with `docs/README.md`, then `docs/INTEGRATION.md` and `docs/API.md` (`docs/ECOSYSTEM.md` has the coordinate table).
  Do not read any path outside this directory, do not browse the network, and do not open any engine source, any
  other copy of the engine, or any Gradle cache contents. Build output under this directory is fine to read.
- Do not guess an API the docs do not show. If you need something the docs do not give (a package name, a dependency,
  a signature, a step), record the gap in `STUMBLES.md` and then resolve it the least invasive way you can, noting
  how.
- Use the literal version `{{VERSION}}` in every engine dependency and the per-module coordinates from the docs, never
  one aggregate coordinate, never a variable.
- Domain-free names only (items, titles); do not invent an app domain.

## Do this

1. Add the per-module engine dependencies to `jvmconsumer/build.gradle.kts` and `app/build.gradle.kts`, as the docs
   say, plus anything else those modules need that the docs call for. The undo module is its own dependency.
2. In `jvmconsumer/src/main/kotlin/wire/Wire.kt` (package `wire`) build a ladder of tiers, in this order:
   - a `LocalGrammarStrategy` at the head, with a `GrammarPack` that has one English and one Spanish phrasing for one
     mutating create tool you define (a free tier: it must call no provider);
   - a SingleShot tier over your tools (one mutating create tool, one read tool);
   - a `PlanThenExecuteStrategy` tier whose later steps can use the id an earlier step returned;
   and select the start tier with `TierSelector.Router` (give it `tierDescriptions`, and map the router's own model
   request id in your provider-selection source). Also provide:
   - an admitting gate, a credential source and a fixed provider selection;
   - an `UndoJournal` with an `EntityAdapter` over an in-memory store, the undo bridge, and a `CommitSink` of your own
     composed with the bridge (journal first), so one command can be undone as a whole;
   - a `render(outcome)` function that handles every `CommandOutcome`, renders a partial completion as "did X,
     couldn't finish", renders a clarification's options, and uses `else` for open taxonomies;
   - an `undoAll` function that calls the journal's `undoAll` for a command's group key and renders every
     `UndoResult` (and an `else` for the open `UndoReason`).
3. In `jvmconsumer/src/test/kotlin/wire/WireTest.kt` write your own scripted `AiProvider` and six tests:
   1. an English grammar command completes with zero provider calls;
   2. a Spanish grammar command completes;
   3. the plan's second step receives the first step's id;
   4. a scripted router answer starts the walk at the plan tier, and the selection shows in the trace;
   5. `undoAll` restores the store;
   6. a provider failure ends `Failed` and renders through the `else` branch.
   Do not use the engine's own fake provider or any engine test fixture.
4. In `app/src/main/kotlin/wire/AppWire.kt` wire the keystore (an app-owned DataStore, a `KeySlot` table and the
   credential source) so it compiles.
5. Run `./gradlew :jvmconsumer:test :app:compileDebugKotlin` until it is green (fix your own code; if a doc gap is
   the cause, record it).
6. Write `STUMBLES.md`: every place the docs were missing, unclear or wrong, one bullet each with the document, the
   section and what you needed (or the single word `none`). Write `CONSULTED.md`: every file you read, one path per
   line.

Finish with a short message: whether the build is green and how many stumbles you recorded.
