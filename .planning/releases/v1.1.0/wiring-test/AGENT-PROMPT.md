# Task: wire the voice-action-engine library into a scratch consumer, using only its documentation

You are in a scratch Android/Kotlin Gradle workspace (the current directory). It has two modules, `:jvmconsumer`
(plain Kotlin/JVM) and `:app` (an Android application), and no dependency on the engine yet. The engine is a library
published on JitPack; the version to use is `{{VERSION}}` (a commit SHA).

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
   say, plus anything else those modules need that the docs call for.
2. In `jvmconsumer/src/main/kotlin/wire/Wire.kt` (package `wire`) build a pipeline with a SingleShot tier over two
   tools you define (one mutating create tool, one read tool), an admitting gate, a `CommitSink` that records actions,
   a fixed provider selection and a credential source. Add a `render(outcome)` function that handles every
   `CommandOutcome`, renders a partial completion as "did X, couldn't finish", renders a clarification's options, and
   uses `else` for open taxonomies.
3. In `jvmconsumer/src/test/kotlin/wire/WireTest.kt` write your own scripted `AiProvider` and two tests: a forced
   create call ends `Completed` with one committed action, and a provider failure ends `Failed` and renders through
   the `else` branch. Do not use the engine's own fake provider or any engine test fixture.
4. In `app/src/main/kotlin/wire/AppWire.kt` wire the keystore (an app-owned DataStore, a `KeySlot` table and the
   credential source) so it compiles.
5. Run `./gradlew :jvmconsumer:test :app:compileDebugKotlin` until it is green (fix your own code; if a doc gap is
   the cause, record it).
6. Write `STUMBLES.md`: every place the docs were missing, unclear or wrong, one bullet each with the document, the
   section and what you needed (or the single word `none`). Write `CONSULTED.md`: every file you read, one path per
   line.

Finish with a short message: whether the build is green and how many stumbles you recorded.
