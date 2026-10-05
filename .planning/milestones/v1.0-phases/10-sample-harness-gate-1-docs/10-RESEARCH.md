# Phase 10: Sample Harness, Gate-1 & Docs - Research

**Researched:** 2026-10-01
**Domain:** Android debug harness (Compose `:sample`) driving a JVM/Android engine against live cloud LLMs on a USB-cabled test rig; plus agent-ready docs.
**Confidence:** HIGH on repo state and engine API (read from source this session); MEDIUM on live-model behavior (Haiku band, OpenRouter optionals); the live legs themselves are not yet run.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01 [gate1-drive]:** Agentic Gate-1 tester drives the real :sample UI (proves VER-01's key-through-:keystore path); executor plans build but never run on devices
- **D-02 [evidence]:** SB-format per-turn logcat lines plus the extra fields, committed as evidence; only tool names/counts/fingerprints (public repo — never prompt text, args or keys)
- **D-03 [gate1-model]:** Haiku 4.5 (the model behind SB's 7,016), cold-start run, device-hw: tagged criteria, a tolerance band (e.g. ±5%) around 7,016; warm re-runs within 5 min classified as infra re-runs
- **D-04 [keys]:** Owner-only key files outside the repo, passed by reference so the literal never appears in transcripts/logs; verify last-4 and no plaintext on disk; Mechanism pending Yahir's approval via the orchestrator; plan as if approved, gate the key-dependent tasks.
- **D-05 [fixture-load]:** Manual copy + runtime sha256 check, never a Gradle/config-time check; build Gate-1 from the main checkout or copy the fixture into the worktree
- **D-06 [smoke-shape]:** Use a small committed synthetic tool pair (reusable in public docs/tests, independent of SB's private fixture) with an optional-field EDIT case; OpenAI gpt-5.4-mini (effort none), OpenRouter openai/gpt-5.4-mini
- **D-07 [agent-wire-test]:** Fresh-subagent wiring test against the release SHA (reusing Phase 1's scratch consumer) before the tag; doc set = README + INTEGRATION + API (backup-engine layout)
- **D-08/D-12:** The v1.0.0 cut, release script and `create_tag:false` are Phase 11; this phase ends at green Gate-1 + docs + the agent-wiring test.
- **D-11 [ext-release]:** external research (Haiku 4.5 min cacheable prefix + TTL, gpt-5.4-mini forced tool_choice with effort none, OpenRouter require_parameters, JitPack facts).
- **D-13 [r1-verdict]:** Live legs are key-gated; CLEARED by master 2026-10-01 under the standing test-key policy. Gate-1 runs cold on Haiku 4.5; warm re-runs are infra re-runs.
- **D-14 [a19-clarification]:** README/API document `terminalCall` + `Clarification` rendered as pressable options, and the follow-up pattern (new command with `parentRunId`, choice rendered via the user-turn hook).
- **Runtime Decisions (authoritative):** (1) Gate-2 carry: low-credit/spend-exhausted HTTP 400 maps to `FailureReason.Billing`; accepted API-key charset holds for real keys on all 3 providers. (2) P7/P8 were ruled Gate-1 N/A (JVM-only), so Phase 10 is their device proof: VER-03 covers SingleShot live on 3 clouds; VER-02 covers multi-turn replay on Anthropic; **extend VER-03** with one bounded multi-turn (>=2 turns, tool-result replay) leg on OpenAI Chat and on OpenRouter; assert `disable_parallel_tool_use` + HTTP 200 in the Anthropic smoke; cheapest models, state bounded call count. (3) D-13 key gate CLEARED: use `with-test-keys` (host) / `push-test-key <provider> --device <TESTER serial> --package <sample appId>` (device); cheapest models; bounded call count stated in the plan; nothing key-shaped in logs/commits; report actual count + estimated cost to master; a live-leg checkpoint still routes through the master (plan approve with a defer fallback). (4) Fixture at `/home/yahir/Projects/AndroidApps/Personal/SecondBrain/.planning/cross-repo/sb-a10-fixture.json`: copy manually to the gitignored path, check sha256 at runtime against `ebd3ef4a…af4ed3e`, never reference SB's path in the build; sha mismatch -> ask orchestrator to regenerate, do not use it.

### Claude's Discretion
Anything not listed follows `.planning/research/SUMMARY.md` and this research.

### Deferred Ideas (OUT OF SCOPE)
See REQUIREMENTS.md v2 / LATER items (Responses API, `:undo`, grammar, plan-then-execute, on-device model). No tag, no `api.txt`, no release script in this phase.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| VER-01 | `:sample` loads LE-1 fixture from gitignored path, loud runtime failure, fake `ToolExecutor`, BYO key via `:keystore`, OkHttp 5.2.1 pin | Sections "Current :sample state", "Fixture", "Key reconciliation", "Engine API map" |
| VER-02 | Anthropic agentic >=2 turns, cold, Haiku 4.5, cache write turn 1 / read turn 2+, ~7,016, canned-admit, prefix + min length logged | "Live legs", "Cold-run enforcement", `CacheVerdict` |
| VER-03 | One live single-shot per cloud (+EDIT optional-absent) + extended Chat/OpenRouter multi-turn + Anthropic `disable_parallel_tool_use` + 200 | "Live legs", "External facts" |
| VER-04 | README + INTEGRATION + API good enough for an AI agent to wire the engine | "Docs (VER-04)", "Agent wiring test (D-07)" |
</phase_requirements>

## Summary

`:sample` today is an inert shell: an AGP application module with only `implementation(project(":core"|":providers"|":keystore"))`, the OkHttp 5.2.1 pin, a marker object and a manifest with no activity and no INTERNET permission [VERIFIED: sample/build.gradle.kts:23-29, sample/src/main/AndroidManifest.xml:1-3]. Everything VER-01..03 needs is therefore new: Compose UI, fixture loader, canned `ToolExecutor`, composition root over the three providers, `:keystore` wiring, debug-only test-key import, leg runners, a pure `CacheVerdict`, and the evidence line formatter. The engine surface needed is all public and stable (Phases 2-9); no engine change is expected, and none is allowed to touch `api.txt` (none exists yet).

The load-bearing design choice is to keep the on-device legs **thin** and push every decision into pure Kotlin that the host JVM tests cover with the existing `:core` `testFixtures` fakes: fixture parse + sha, tool classification, canned executor, cache verdict (PASS/WARM/FAIL with the +-5% band), evidence-line allow-list, composition. The device run then proves only what a host cannot: real AndroidKeyStore round trip, real OkHttp 5.2.1 variant, real network, real cache. The Gate-1 agentic tester drives the Compose UI (testTag -> resource-id) and a guarded host script handles install, fixture push, key push, logcat capture, redaction and cleanup.

**Primary recommendation:** Build `:sample` as Compose (STACK.md pick) with a single `SampleEngine` composition root and pure verdict/evidence classes; reconcile `push-test-key` with VER-01 by a debug-only "Import test keys" action that reads `filesDir/test-keys/<provider>.key`, calls `ApiKeyStore.save`, then deletes the plaintext file; write one guarded `scripts/run-sample-gate1.sh` (TESTER-only, copied guard block, lock, cold-stamp) and run the live legs under a stated request ceiling (~33 requests, expected cost ~USD 0.04, ceiling ~USD 0.20).

## Project Constraints (from CLAUDE.md)

- Public repo: no fixture, key, prompt text, tool args in git/logs/evidence; secrets never reach logs/telemetry/exceptions/`toString()`. Only tool names/counts/fingerprints.
- `:core` has no HTTP/Android dep; OkHttp compile floor 4.12 untouched; **never raise `:providers` OkHttp**; the 5.2.1 pin lives only in `:sample`.
- Public API strictly additive; contract changes only via orchestrator amendments; no tag cut here; ledger rows are messaged, never committed.
- detekt zero baseline on **library** modules (sample is not wired to detekt/invariants, see Pitfalls); Kotlin 2.3.20 / AGP 9.2.1 (built-in Kotlin: do NOT apply `org.jetbrains.kotlin.android`) / Gradle 9.4.1 / JDK 17; JVM 11 bytecode.
- GSD workflow enforcement: edits via GSD commands only; device work only on the TESTER, `adb -s`, never the personal phone; two-gate UAT; one device tester at a time.
- User profile: surface UI issues as first-class; make failures loud and visible in the UI; give tailnet `https://` URLs for docs (use `render-doc-for-review` when handing docs for review).

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Fixture load + sha256 check | `:sample` app (runtime) | host script (pre-check) | Must fail at run time, never at Gradle configuration (D-05, JitPack configures `:sample`) |
| Tier ladder / loop / gate / sink | `:core` (engine) | `:sample` composes | Consumer composes its own ladder; sample is the reference wiring |
| Provider HTTP (Anthropic/Chat) | `:providers` | `:sample` supplies OkHttp 5.2.1 | Proves 4.12-compiled bytecode on the 5.x android variant |
| Key storage | `:keystore` (AES/GCM + DataStore) | `:sample` owns the DataStore and `KeySlot` table | KEY-02: app owns names and the DataStore |
| Plaintext test-key hand-off | host tool `push-test-key` + sample debug importer | — | Key must go THROUGH `:keystore`; plaintext file deleted after import |
| Cache/optional-absent verdicts | `:sample` pure classes (JVM-tested) | on-device logcat | Decision logic host-testable; device supplies real numbers |
| Gate-1 orchestration, evidence capture, redaction | host script + agentic tester | — | Guard, lock, cold stamp, allow-list filter live outside the app |
| Docs | repo root `README.md`, `INTEGRATION.md`, `API.md` | `ECOSYSTEM.md` | backup-engine layout (D-07) |

## Current `:sample` state and what to add

[VERIFIED: sample/build.gradle.kts:3-29] plugins `alias(libs.plugins.android.application)` only; `applicationId = "io.github.ygaray.voiceactionengine.sample"` (line 11), `minSdk = 35`, JVM 11, deps on the three modules + `implementation("com.squareup.okhttp3:okhttp:5.2.1")` (line 28). Only source: `SampleModule.kt` (`internal object SampleModule`). Manifest: `<application android:label="vae-sample" />`. `.gitignore:48` has `sb-a10-fixture*.json`; `scripts/verify-repo-hygiene.sh:49` requires `sample/src/debug/assets/sb-a10-fixture.json` to be gitignored, so **that is the gitignored host path**.

To add (all under `io.github.ygaray.voiceactionengine.sample`; hygiene check (a) requires this package root for every sample `.kt`):
1. **Build:** `kotlin-compose` plugin (id `org.jetbrains.kotlin.plugin.compose`, version = Kotlin 2.3.20; present in the local cache [VERIFIED: ~/.gradle caches list 2.3.20]), alias in `gradle/libs.versions.toml` + root `apply false`; Compose BOM 2026.04.01, `activity-compose` 1.13.0, `lifecycle-viewmodel-compose`/`runtime-compose` 2.10.0, material3 [CITED: CalTracker libs.versions.toml lines 8-11; cache has 2026.04.01, 1.13.0, 2.10.0]. `testImplementation(libs.junit)`, `coroutines-test`, `testImplementation(testFixtures(project(":core")))` (same form as `:keystore` build). `kotlinx-serialization-json` comes via `:core` `api`. **No fixture read, `exec` or env access in any `.kts`.**
2. **Manifest:** `INTERNET` permission, `MainActivity` (`exported="true"`, LAUNCHER), `allowBackup="false"`. Keep the debug build debuggable (needed by `run-as`).
3. **Source set split:** `src/main` = engine composition, legs, UI, loaders. `src/debug` = `TestKeyImporter` (reads `filesDir/test-keys`) and an optional intent autorun hook, so release variants cannot contain plaintext-import code.
4. **Fixture:** loader order = `filesDir/fixture/sb-a10-fixture.json` (adb-pushed, no rebuild) then asset `sb-a10-fixture.json` (from the gitignored `sample/src/debug/assets/`); neither present -> typed `FixtureState.Absent` shown as a red banner + `E`-level logcat line, buttons that need it disabled. Tests use a tiny **committed synthetic** fixture in `src/test/resources` (never SB data).

## Fixture (LE-1) structure [VERIFIED: parsed this session, structure only]

File `sb-a10-fixture.json`, 35,464 bytes, sha256 prefix `ebd3ef4a`, suffix `af4ed3e` (matches ROADMAP/CONTEXT). Top-level keys exactly `system` (string, 1,614 chars) and `tools` (list of 18). Each tool has keys `name`, `description`, `input_schema` (object with `type`, `properties`, `required`, `additionalProperties`); **no `strict` key and no mutating/terminal marker**. Compact tools JSON is 19,620 chars. Phase 3 measured 21,109 chars total = ~3.01 chars/token against SB's 7,016 [CITED: 03-RESEARCH.md:60].
- Map to `ToolSpec(name, description, inputSchema = input_schema, mutating = <derived>, terminal = false)` (`strict = null`, engine decides, PROV-12). Derive `mutating` by **name prefix rule** (`get_`/`list_`/`find_`/`search_` = read, else mutating; 5 read / 13 mutating today) in a small `ToolClassifier`; never hard-code the count (E4) and never name SB tools in sample source (public repo; CLN-02 scanner does not scan `:sample`, but keep it clean anyway).
- `prefix_chars` for the SB-format line = `JsonArray(tools).toString().length + system.length` (SB's formula, `AnthropicAgentLoop.logIterationTelemetry`).
- Runtime check: `MessageDigest("SHA-256")` over the raw bytes; compare full 64-hex constant computed once from the verified file (planner: `sha256sum` the file, commit the constant, never the file). Mismatch -> `FixtureState.ShaMismatch` (do not use; ask orchestrator to regenerate).
- Host delivery: `scripts/push-sample-fixture.sh` (new): host `sha256sum` prefix/suffix check, then `adb push` to `/data/local/tmp/<rand>`, `run-as <pkg> sh -c 'mkdir -p files/fixture && cat > files/fixture/sb-a10-fixture.json' < tmp`, `rm tmp` (same mechanism as `push-test-key` [CITED: ~/.local/bin/push-test-key docstring]). Worktree builds lack the file: build/install Gate-1 from the main checkout or rely on the adb push.

## Engine API map (what the sample calls) [VERIFIED: sources read this session]

| Need | Symbol (package `io.github.ygaray.voiceactionengine.*`) | Notes |
|------|-----|-------|
| Ladder | `core.pipeline.commandPipeline { tier(...); provider(...); gate=; commitSink=; policy=; listener=; providerSelection=; credentials= }` | `gate` and `commitSink` are required (no default, `PipelineBuilder.build`); `tier(strategy)` order = ladder |
| Run | `pipeline.execute(CommandInput(transcript, language, context, parentRunId))` -> `CommandOutcome.{Completed(reply, terminalCall, partial) \| Failed(reason, details) \| Unhandled(lastReason)}` | `Completed.partial` must render "did X, couldn't finish" |
| Agentic tier | `core.strategy.agentic.AgenticLoopStrategy(StrategyId("agentic")) { tooling = ToolSpecProvider.fixed(ToolingSnapshot(system, tools, null)); executor = ToolExecutor {..}; userTurn; capabilities }` | Sends `ToolChoice.Auto()`, `CacheDirective(true)`, `singleToolCall=false` (AgenticLoopStrategy.kt:~176-180) so **no** `disable_parallel_tool_use` in agentic bodies |
| SingleShot tier | `core.strategy.singleshot.SingleShotStrategy(StrategyId("single")) { tooling; resolver = OutcomeResolver {..}; forceTool = true }`; `ToolingSnapshot(system, tools, singleShotTool = "<name>")` | Sends `singleToolCall=true` -> Anthropic `disable_parallel_tool_use:true` (AnthropicEncoder.kt:107); first call only, extras dropped -> `partial=true` |
| Fake executor | `ToolExecutor.prepare(call: Extraction, input) -> ToolStep.Finished(name, FinishedKind.READ, StepResult(json))` or `ToolStep.Mutation(PendingMutation{ toolName, apply(): StepResult })` | `StepResult(content, isError, token, targetIds)`; a `Mutation` for a non-`mutating` spec is rejected |
| Canned-admit gate | `PreApplyGate { GateDecision.Admit() }` | Both modes exist: `AwaitingConfirmGate(policy)` (suspend; `pending` StateFlow + `resolve(id, confirmed)`) and defer (`GateDecision.Hold` then `pipeline.commitHeld(held[, amended])`). Gate-1 uses canned admit; README documents both |
| Sink | `CommitSink { onAction(ActionEvent); onRunClosed(runId, RunTermination) }` | Log only `action.kind`/`toolName`/counts |
| Selection / creds | `ProviderSelectionSource { ProviderSelection(ProviderId.ANTHROPIC, "claude-haiku-4-5") }` keyed by `request.strategy`; `CredentialSource` = `KeystoreCredentialSource(apiKeyStore)` | `ProviderSelection(provider, model)`; model id exact (capability table is exact-match) |
| Providers | `AnthropicProvider { httpClient; attemptObserver }`, `ChatCompletionsProvider.openAi { }`, `.openRouter { }` | `attemptObserver` gives per-HTTP-attempt `httpStatus`, `kind` (`INITIAL`/`TRANSIENT_RETRY`/`FORCED_TOOL_RESHAPE`); Chat variant adds `finishReason`, `toolCalls`. Clean client strips interceptors (PROV-11), so request bodies are **not** observable from the app |
| Policy | `TierPolicy { maxIterations; tokenCeiling; maxTokensPerTurn; commandTimeoutMillis }` via `TierPolicySource.fixed` | Defaults 6 / 60,000 / 4,096 (TierPolicy.kt:6-8); `maxIterations >= 2` |
| Telemetry | `PipelineEventListener { PipelineEvent.ProviderCall(runId, strategy, turn: TurnRecord) }`; `TurnRecord(provider, model, stopReason, toolNames, usage: Usage, latencyMillis)`; `Usage(inputUncached, cacheRead, cacheWrite, output)`; `PipelineEvent.CacheNotEngaged`; `outcome.trace.attempts[].turns[]` | Map to SB fields: `cache_creation_input_tokens`=`cacheWrite`, `cache_read_input_tokens`=`cacheRead`, `input_tokens`=`inputUncached` (Anthropic `input_tokens` excludes cached) |
| Min cacheable | `pipeline.capabilityTable.lookup(ProviderId.ANTHROPIC, "claude-haiku-4-5").minCacheablePrefixTokens` | = 4096: "private const val HAIKU_MIN_CACHEABLE_PREFIX_TOKENS = 4096" (AnthropicModels.kt:13) |
| Keystore | `ApiKeyStore(dataStore, listOf(KeySlot(provider, alias, ciphertextKey, ivKey)))`; `.save/.read/.delete/.observe`; `KeyState.{NotConfigured, Ready(last4), KeyMissing, Unreadable(cause)}` | Sample owns `DataStore<Preferences>` (`preferencesDataStore("vae_sample")`) and 3 slots (anthropic/openai/openrouter) with sample-prefixed aliases; cause codes `key_missing`, `keystore_unavailable`, `decrypt_failed`, `stored_value_malformed`, `storage_unreadable` (KeystoreCauses.kt) |
| Clarification | `ToolSpec.clarification(name)` (terminal, non-mutating), `outcome.terminalCall?.asClarification()` -> `Clarification(question, options: List<ClarificationOption(id,label)>)` | Not needed for Gate-1 legs; show in README/sample UI as pressable options (cheap, host-tested) |

`OkHttp.VERSION` is a compile-time constant: read it **reflectively** (`Class.forName("okhttp3.OkHttp").getField("VERSION")`) or the sample reports the compiled 4.12.0 [VERIFIED: providers/.../OkHttpVersionGuardTest.kt:17-20].

## Architecture

```
adb/UI tester ──taps──> MainActivity (Compose, testTags) ──> SampleViewModel(viewModelScope)
                              │                                   │
        Import test keys(dbg) │                                   ├─ FixtureLoader (filesDir | assets, sha256) ─> ToolSpecs
   filesDir/test-keys/*.key ──┴─> ApiKeyStore.save ──> DataStore ◄┤
                                   (delete plaintext)            ├─ SampleEngine.build(): commandPipeline{
                                                                 │     tier(Agentic|SingleShot) provider(Anthropic|OpenAi|OpenRouter)
                                                                 │     credentials=KeystoreCredentialSource  gate=Admit  sink=LogSink
                                                                 │     listener=EvidenceListener }  (OkHttp 5.2.1 client)
                                                                 ├─ LegRunner(leg) ─> pipeline.execute(CommandInput) ─> CommandOutcome
                                                                 └─ CacheVerdict / SmokeVerdict (pure) ─> EvidenceLine ─> Log.i("VaeSample") + UI readout
host: scripts/run-sample-gate1.sh (guard, lock, cold-stamp, build/install, push-fixture, push-keys, logcat -> allow-list -> evidence/)
```

### Recommended structure (`sample/src/main/kotlin/.../sample/`)
`MainActivity.kt`, `SampleViewModel.kt`, `SampleEngine.kt` (composition root, takes injectable `EvidenceSink`, `Clock`, base providers for tests), `fixture/FixtureLoader.kt` + `ToolClassifier.kt`, `tools/CannedToolExecutor.kt`, `tools/SyntheticTools.kt` (committed create/edit pair + lookup tool), `legs/{AgenticLeg,SmokeLeg,MultiTurnLeg}.kt`, `verdict/{CacheVerdict,SmokeVerdict}.kt`, `evidence/EvidenceLine.kt`, `keys/KeySlots.kt`, `ui/*`. `src/debug/.../TestKeyImporter.kt`. Tests in `src/test/kotlin` using `core` `testFixtures` (`FakeAiProvider`, `ScriptedGate`, `RecordingCommitSink`, `RecordingEventListener`, `NoNetworkGuard`) [VERIFIED: core/src/testFixtures/.../testing listing].

### Patterns
- **Pure verdicts.** `CacheVerdict.classify(turns, band=0.05, anchor=7016)`: `WARM` (INFRA, rerun after >=6 min) if turn1 `cacheRead>0`; `FAIL` if turn1 `cacheWrite==0` (cache not engaged: check prefix vs 4096 min) or any turn>=2 `cacheRead==0`; `PASS` if turn1 `cacheWrite` in `[anchor*(1-band), anchor*(1+band)]` and every turn>=2 `cacheRead` == turn1 `cacheWrite` (+-1%; breakpoint parity means reads equal the written prefix) and >=2 provider calls; `PASS_OUT_OF_BAND` is NOT a pass: report measured value and escalate (the number, not the band, is the evidence).
- **Evidence is a closed vocabulary.** `EvidenceLine` can only be built from enums/ints/tool-name lists/hex fingerprints; there is no `String` free-form field, so prompt/args/reply cannot be logged by construction. Host script re-filters with an allow-list regex and key-shape scan.
- **One composition root** reused by tests (fake providers) and the app (real providers) so the unit tests exercise the same wiring the device runs.
- **Run engine in `viewModelScope` with `FLAG_KEEP_SCREEN_ON`**; Samsung Freecess can freeze live coroutines [CITED: ~/.claude/context/devices/test-android.md Quirks]; harness whitelists the package via `cmd deviceidle whitelist +<pkg>` (best effort) and the tester keeps the screen foreground.

## Key reconciliation (push-test-key vs VER-01)

`push-test-key <provider> --device R5CT10XNKQN --package io.github.ygaray.voiceactionengine.sample` writes plaintext to `files/test-keys/<provider>.key` (mode 600) via `run-as`, key never on a command line; debuggable builds only; refuses the personal phone [VERIFIED: ~/.local/bin/push-test-key docstring + `is_personal` check]. VER-01 needs the key to travel **through `:keystore`**. Reconcile in the app, not on the host:
1. Debug-only `TestKeyImporter.importAll()` (button `import_test_keys`): for each provider file present -> `apiKeyStore.save(provider, text)` (`save` trims) -> verify `read()` is `Ready(last4)` -> overwrite-then-delete the file -> self-check: scan `filesDir/datastore/*` bytes for the plaintext key and set `plaintext_in_datastore=false`; log only `import provider=<p> state=Ready plaintext_file_deleted=true plaintext_in_datastore=false`. The last-4 is shown on screen (UI chip) and **never logged**; no host-side key comparison is needed (agents may not read the key dir).
2. Manual BYO field path is also exercised in the UI with a **dummy** value for one provider (`adb shell input text` of a non-secret string) to prove type -> Save -> `Ready(last4)` -> replace -> Delete; the real import then overwrites it. No real key is ever typed through `adb input`.
3. Relaunch-still-Ready check (force-stop, start, chips show `Ready`) proves persistence through DataStore + AndroidKeyStore. Post-run `run-as <pkg> ls files/test-keys` must be empty. End of run: `adb uninstall` (like Phase 6 "test package removed") so no key outlives the run.
4. All three keys are the **spend-uncapped-by-design but unlimited-by-policy** test keys: legs stay bounded by the request ceiling below. Key-charset acceptance (Runtime Decision 1b) is proven implicitly: a real key passing the pre-send header-safety check is any 200 from that provider (log `key_charset=ok` only after a 200).

## Device mechanics and harness script

Facts [VERIFIED: scripts/run-keystore-instrumented.sh:18-27 and body; ~/.claude/context/devices/*.md]: TESTER = USB serial `R5CT10XNKQN` (preferred), wireless `100.118.21.106:1496` only as fallback after `ro.serialno == R5CT10XNKQN`, model `SM-S908U`, sdk >= 35; personal phone `100.126.94.47` refused; every call `adb -s <target>`; lock via `flock` on `${XDG_RUNTIME_DIR:-/tmp}/vae-keystore-tester.lock`; exit codes 0 PASS, 1 FAIL, 2 ERROR, 3 INFRA (offline/busy), 4 INFRA (identity); last line `<NAME>: <OUTCOME> k=v...`. `verify-keystore-device-guard.sh` proves refusals with a fake adb (offline, usb_impostor, wireless_impostor, foreign_android_serial, extra_argument, lock_busy).

**New `scripts/run-sample-gate1.sh`** (copy the guard block verbatim; do **not** refactor the Phase 6 script, its guard test pins it) with subcommands, all behind the same guard + its own lock `vae-sample-tester.lock`:
`preflight` (identity, sdk, foreground app, `ps`/lock) · `build-install` (host fixture sha check -> `./gradlew :sample:assembleDebug --offline` -> `install -r -t`) · `push-fixture` · `push-keys` (calls `push-test-key` x3, never with a key on argv) · `capture-start` (`logcat -c`) · `capture-save <leg>` (`logcat -d -s VaeSample:I` -> allow-list regex -> key-shape scan -> `evidence/<leg>.log`; scan failure deletes the file and exits non-zero) · `cold-stamp check|write` · `cleanup` (force-stop, `rm files/test-keys`, uninstall, print `sample package removed`). Plus **`scripts/verify-sample-device-guard.sh`** (fake-adb scenarios, mirror of the keystore one, plus "no key literal on any logged adb argv"). TESTER offline -> exit 3 `INFRA`; wait for the watchdog (~1 min), `adb connect`, retry once, then record INFRA. **Never an emulator or another phone**: all Phase 10 criteria are tagged `device-hw:` (keys are TESTER-only by policy).

**Driving the UI.** Compose `Modifier.semantics { testTagsAsResourceId = true }` at the root and `testTag` on every control so `uiautomator dump` exposes stable `resource-id`s (`import_test_keys`, `key_field_<p>`, `key_save_<p>`, `run_ver02`, `run_smoke_<p>`, `run_multi_<p>`, `status_<leg>`, `readout`) [ASSUMED: standard Compose behavior, spike in plan 1]. The tester reads `status_<leg>` text (`PASS|FAIL|INFRA|RUNNING`) and logcat; screenshots are the last rung and are not committed if they show model text. An optional debug `am start --es vae_autorun <leg>` is a rerun convenience only and does not replace the UI-driven first run (D-01).

**Cold-run enforcement (D-03).** Anthropic caches live 5 min and are refreshed on each read/write measured from request start [CITED: platform.claude.com prompt-caching docs]. Rules: (1) `cold-stamp` file on the host (`~/.cache/vae-gate1/anthropic-agentic.ts`) written at each agentic run start; a start within 360 s of it is refused (exit 3, `reason=warm_window`); (2) app-side detection is authoritative: turn 1 `cache_read > 0` => `WARM`, recorded as an **infra re-run**, never FAIL/PASS; (3) no other process may send the same prefix to the Haiku key within 5 min (one tester at a time, lock). The synthetic smokes use a different, tiny prefix (< 4096 tokens), so they never warm the SB prefix.

## Live legs: bounded budget (state in the plan and report to master)

All via `:sample` on the TESTER, outside `./gradlew check`, key-gated per Runtime Decision 3 (plan an explicit **approve / defer** decision checkpoint that routes through the master, with a defer fallback that leaves legs `INFRA(deferred)` and registers them as Gate-2).

| Leg | Provider / model | Engine path | Pass condition | Expected / ceiling HTTP requests |
|-----|------------------|-------------|----------------|-------|
| L1 VER-02 | Anthropic `claude-haiku-4-5`, SB fixture | AgenticLoop, canned admit, default policy (6 iters) | `CacheVerdict`=PASS; every attempt 200; prefix + min log lines present | 2-4 / 6 per run; 1 cold run + <=1 infra re-run = 12 |
| L2 VER-03 | same, synthetic EDIT tool | SingleShot forced edit tool | 200 on `INITIAL` attempt, 0 `FORCED_TOOL_RESHAPE`/`TRANSIENT_RETRY`, parsed tool, optional absent | 1 / 3 |
| L3 VER-03 | OpenAI `gpt-5.4-mini` | SingleShot, effort none (engine sends it) | parsed tool + optional-absent check | 1 / 3 |
| L4 VER-03 | OpenRouter `openai/gpt-5.4-mini` | SingleShot (`require_parameters:true` on forced) | parsed tool + optional-absent check | 1 / 3 |
| L5 | OpenAI `gpt-5.4-mini` | AgenticLoop with synthetic read tool, `maxIterations=3` | >=2 provider calls, tool result replayed, 200 each, `Completed` | 2 / 6 |
| L6 | OpenRouter `openai/gpt-5.4-mini` | same | same; **observe** (do not assert) turn-2 `cacheRead` (Phase 5 R3 saw 0) | 2 / 6 |
| L7 optional | OpenAI Responses-only id with `capabilities(...) { supportsTools = true }` override | one forced call | capture status + `FailureReason` only; confirms `RESPONSES_ENDPOINT_MARKER`; 400 bills nothing | 0-1 / 1 |

Totals: expected ~14 requests, **hard ceiling 33** (+1 optional L7). Enforce in-app: leg-level `TierPolicy.maxIterations`, a run-level request counter in the attempt observers (refuse starting further legs past 40), and the host stamp. **Cost** (Haiku 4.5 $1 in / $5 out per MTok, 5m cache write 1.25x, read 0.1x [CITED: platform.claude.com pricing]; gpt-5.4-mini $0.75 in / $0.075 cached / $4.5 out [CITED: developers.openai.com/api/docs/models/gpt-5.4-mini]): L1 turn 1 ~ USD 0.012 (7,016 x 1.25 = $0.0088 + output), later turns ~ USD 0.004 each, so one run ~ USD 0.02-0.03 (worst case 6 turns ~ USD 0.05); L2-L6 ~ USD 0.003-0.005 each. **Expected total ~USD 0.04; ceiling ~USD 0.20** (OpenRouter fee/pass-through assumed ~ upstream [ASSUMED]); for scale, Phase 8's 14 live requests cost ~USD 0.033 [CITED: 08 evidence/live-multiturn-capture.txt]. Report actual counts (from the sample's counter) + this estimate to the master.

**Utterances/prompts** are committed synthetic strings, never SB content. L1 must force a read-then-answer shape (e.g. ask to find items matching a generic word and report how many), so there are >=2 turns by construction; if the model answers in one turn the verdict is `FAIL(single_turn)` on the prompt, not on the engine, and one bounded re-run with a stronger prompt is allowed only after the 6-min cold window.

**EDIT/optional-absent (PROV-12).** Synthetic pair: `create_note(title* , body?, tags?)` and `edit_note(id*, title?, body?, tags?)`; EDIT prompt "change only the body". Assert on the engine's `Extraction.arguments`: keys are a subset of the schema, and no optional key is `""`/`[]`/`null`-filled that the prompt did not mention. If the routed model fills optionals (Phase 5 R2 did on OpenRouter [CITED: 05 evidence/live-chat-capture.txt]) the verdict is `INCONCLUSIVE(model_filled_optional)`, allowed one retry with a stronger instruction, then recorded as a carried Gate-2 item, not a FAIL (it is model behavior, not a decoder defect). OpenAI C3 omitted optionals live [CITED: same file], so L3 should pass.

**Anthropic `disable_parallel_tool_use` + 200 (07-08 carry).** The body is not observable on device (PROV-11 clean client). Evidence is therefore: the host golden asserts the field (JVM, already green) and the live SingleShot request returned 200 on the `INITIAL` attempt (the API rejects a malformed field) with `singleToolCall` set by construction. State this limit in SELF-UAT; do not claim live byte inspection.

**Carried Gate-2 items (disposition):** low-credit 400 -> `Billing` and OpenAI `insufficient_quota`/OpenRouter 402: **not triggerable** without draining credit; unit-covered; register as Gate-2 owner items, mark `NOT EXERCISED`. API-key charset: proven by any 200 per provider. Responses-only 400 text: optional L7. OpenRouter EDIT absent-optional: L4 (may be INCONCLUSIVE). Haiku-via-OpenRouter explicit breakpoint/`cache_write` accounting: **cannot be exercised** (engine sends no explicit breakpoint to OpenRouter in v1.0, LATER-02); only observe L6 cache read.

## External facts (D-11)

| Claim | Result | Source |
|-------|--------|--------|
| Haiku 4.5 min cacheable prompt | **4,096 tokens** | [CITED: platform.claude.com/docs/en/build-with-claude/prompt-caching]; engine table agrees (AnthropicModels.kt:13) |
| Cache TTL | 5 min default, refreshed free on each use; lifetime measured from request **start** | same page |
| Prefix order / fields | `tools`, `system`, `messages`; `input_tokens` = tokens after last breakpoint only; total = read + creation + input | same page |
| `tool_choice` change | invalidates only the messages cache, not tools/system | same page |
| Concurrent requests | entry usable only after first response begins; run turns sequentially | same page |
| Haiku 4.5 price | $1 / $5 per MTok; write 5m $1.25; read $0.10; tool-use system prompt 496 tokens (auto) / 588 (forced) | [CITED: platform.claude.com/docs/en/about-claude/pricing] |
| Tokenizer | 4.7+ models use a ~30% larger tokenizer; Haiku 4.5 predates it (same tokenizer family as SB's 7,016) | [CITED: pricing page; inference for Haiku 4.5] |
| 18-tool prefix ~ 7,016 | Fixture = 21,109 chars (3.01 chars/token) | [CITED: 03-RESEARCH.md:60-61]; Phase 8 real capture showed `cache_write 6754` / `cache_read 6754` on Haiku for a smaller prefix [CITED: 08 evidence] |
| gpt-5.4-mini | `none` is default and supported reasoning effort; function calling supported; 400k context; docs do not state forced `tool_choice` + `none` | [CITED: developers.openai.com/api/docs/models/gpt-5.4-mini]; **live capture C1/C3: 200, `finish_reason tool_calls`, 0 reasoning tokens** [CITED: 05 evidence/live-chat-capture.txt] |
| OpenRouter `require_parameters` | default `false`; `true` means providers lacking any requested parameter are not routed; docs name no error text; live: `parallel_tool_calls` + require_parameters -> 404 "No endpoints found that can handle the requested parameters" | [CITED: openrouter.ai/docs/guides/routing/provider-selection]; [CITED: 05 evidence R5] |
| JitPack (Phase 11 owns) | SHA builds are lazy (`status none` -> `ok`); API `version` for a SHA is the 10-char short SHA; engine reads `VERSION` env first (root build) | [VERIFIED: 01 evidence/jitpack-probe.txt:2-4; build.gradle.kts `engineVersion` block]; build.log success marker and tag-build timing [ASSUMED], re-verify in Phase 11 |

## Docs (VER-04)

Current state: `README.md` is 16 lines (status + coordinates), `ECOSYSTEM.md` 52 lines; no INTEGRATION/API [VERIFIED: wc]. Mirror backup-engine: README (about 80 lines: what/install/usage/links), `INTEGRATION.md` (numbered adoption checklist + gotchas), `API.md` (surface at a glance, seams, safety model, extension points) [VERIFIED: backup-engine heading listing]. `verify-repo-hygiene.sh` (b) forbids the retired aggregator coordinate in README/ECOSYSTEM and requires the three artifact names; keep both true.

Coverage checklist (each item must have a compile-checked snippet or a pointer to the sample file): per-module coordinates (`core`, `providers`, `keystore` with `<version>` = tag; pin immutable tags/SHAs; JitPack repo block); `INTERNET`; minimal pipeline (tool spec, `ToolSpecProvider`, `OutcomeResolver`/`ToolExecutor`, gate, sink, selection, credentials, provider registration); **every seam** (`ToolSpecProvider`, `ToolExecutor`, `OutcomeResolver`, `UserTurnRenderer`, `PreApplyGate`, `CommitSink`, `ProviderSelectionSource`, `CredentialSource`, `TierPolicySource`, `PipelineEventListener`, `OnDeviceCapability`, capability overrides); **both gate modes** (suspend `AwaitingConfirmGate` + UI `pending`/`resolve`; defer `Hold` + `commitHeld`/amended); `else` branches on every open taxonomy (`FailureReason`, `EscalationReason`, `CredentialLookup`, `KeyState`, `ActionKind`, `FinishedKind`, `Resolution`, `PipelineEvent`, `TraceCode`, attempt kinds; closed: `CommandOutcome`, `GateDecision`, `RunTermination`, transcript types); `Completed(partial = true)` renders "did X, couldn't finish", never full success; **Clarification (A19)** as pressable options via `terminalCall?.asClarification()`, follow-up = new `execute(CommandInput(..., parentRunId = previous.runId))` with the choice rendered by a `UserTurnRenderer` into the first user message (prefix unchanged, no transcript resumption); keystore cause UX (`key_missing`/`decrypt_failed`/`stored_value_malformed` = "re-enter key"; `keystore_unavailable`/`storage_unreadable` = "transient, retry") mapped through `FailureReason.CredentialUnreadable`; **uncached/unsupported combos** (OpenRouter `anthropic/*` uncached in v1.0, LATER-02; OpenAI Responses-only ids fail loudly `ModelUnsupported`; forced-tool-unsupported Anthropic models go `auto`); `ExecutedAction` distinguishes `COMMITTED/HELD/PREVIEW/IS_ERROR` via `kind` plus `applied`/`mutating` (Phase 9 seam sign-off item 3 [CITED: 09 evidence/seam-signoff.txt]); Held tool_result bytes `{"applied":false,"status":"held_for_confirmation"}`; secrets rule (never log keys/transcripts/args); OkHttp floor 4.12 / consumer keeps its own; testing a consumer with its own scripted `AiProvider` (the `FakeAiProvider` harness is **not published**, BLD-09); pointer to `:sample` as the working example. API.md may be generated from KDoc headings but must be hand-checked against `review-api-surface.sh` output.

### Agent wiring test (D-07)
Scratch consumer = `scripts/jitpack-consumer-probe.sh` (builds a throwaway Gradle project from an empty cache: `:jvmconsumer` kotlin.jvm + `:app` AGP 9.2.1, JitPack repo, per-module coordinates) [VERIFIED: script read]. New `scripts/agent-wiring-test.sh <sha>`: (1) precondition = docs committed AND commit pushed AND `scripts/jitpack-live-probe.sh <sha>` PASS (JitPack builds the SHA lazily, minutes); (2) create a workspace from the probe skeleton with **no engine sources** and copies of only `README.md`, `INTEGRATION.md`, `API.md`; (3) the orchestrating session dispatches a **fresh subagent** (Agent tool, prompt in `10-.../wiring-test/AGENT-PROMPT.md`: "wire the engine into this scratch app from these three docs only; artifact version = `<sha>`; write `Wire.kt` + a JVM test with a scripted `AiProvider`"); (4) script verifies mechanically: `:jvmconsumer:test` and `:app:compileDebugKotlin` green with `--no-daemon` against the SHA; test asserts a `Completed` with one commit via canned-admit and one `Failed` mapped through an `else`-bearing `when`; static greps on the agent's source: per-module coordinates only, `else ->` in `when` over open taxonomies, `partial` handled, no `io.github...internal`/test-fixture imports, no aggregator coordinate. Verdict recorded in `10-WIRING-TEST.md` with the agent's stumbles listed; any stumble is a doc defect fixed before Phase 11. The SHA tested must be API-identical to the tagged commit (Phase 11 adds only `api.txt` + release script).

## Standard Stack

| Library | Version | Purpose | Why |
|---------|---------|---------|-----|
| Kotlin / AGP / Gradle | 2.3.20 / 9.2.1 / 9.4.1 | existing toolchain | locked [VERIFIED: libs.versions.toml] |
| `org.jetbrains.kotlin.plugin.compose` | 2.3.20 | Compose compiler | must equal Kotlin; present in cache |
| Compose BOM / material3 / activity-compose | 2026.04.01 / (BOM) / 1.13.0 | `:sample` UI | CalTracker's known-good set [CITED: CT toml] |
| lifecycle-viewmodel-compose, runtime-compose | 2.10.0 | ViewModel + `collectAsStateWithLifecycle` | same |
| OkHttp (sample only) | 5.2.1 | runtime pin | existing, do not change |
| kotlinx-serialization-json, coroutines | 1.11.0 (via `:core` api) | fixture parse, scopes | no new dep |
| junit 4.13.2 + `:core` testFixtures | existing | sample unit tests | same as `:keystore` |

**Package legitimacy:** all additions are Google/AndroidX/JetBrains Maven coordinates already resolved by sibling projects; the `package-legitimacy` seam covers npm/pypi/crates only, so it is not applicable to Maven coordinates. No new third-party package is introduced (no Turbine, Robolectric, MockK). | Disposition: Approved (existing, from local Gradle cache; offline resolution of the full Compose graph is [ASSUMED], spike with `./gradlew :sample:assembleDebug --offline` in plan 1).

## Don't Hand-Roll

| Problem | Don't build | Use instead |
|---------|-------------|-------------|
| Test-key transport to device | custom adb/`input text` key typing | `push-test-key` + in-app importer through `ApiKeyStore` |
| Key encryption | own crypto | `:keystore` (`ApiKeyStore`, `KeystoreCredentialSource`) |
| HTTP/transport, retries, cache control | sample-side OkHttp calls | `:providers` via `commandPipeline` |
| Fakes for host tests | new mocks | `:core` `testFixtures` (`FakeAiProvider`, `ScriptedGate`, `RecordingCommitSink`) |
| Device guard | new identity logic | copy the Phase 6 guard block; prove with a fake-adb verifier |
| Evidence redaction | ad-hoc `sed` | closed-vocabulary `EvidenceLine` + allow-list regex + key-shape scan |
| Request-body inspection of Anthropic | interceptor in the sample | not possible by design (PROV-11); use host goldens + 200 |

## Common Pitfalls

1. **Fixture/keys leak into git.** The global gitignore already blocks `*.key`, `test-keys/`; `.gitignore:48` blocks the fixture pattern. Run `scripts/verify-repo-hygiene.sh` and `git-secret-scan` before every commit; evidence goes through the allow-list; never commit uiautomator dumps/screenshots showing model text.
2. **Config-time fixture access breaks JitPack.** JitPack configures `:sample` [CITED: sample/build.gradle.kts:1-2 comment]. Any `file()`/`exec`/env read in a `.kts` fails the clean-clone build. Gate: `scripts/jitpack-dry-run.sh` (clean `git archive`, no fixture) after the sample change.
3. **`:sample:lint` and tests are inside `./gradlew check`** (dry-run lists `:sample:lintDebug`, `:sample:testDebugUnitTest`) [VERIFIED: gradle --dry-run]. New code must pass lint (exported activity, permissions) and unit tests must not need the fixture or network (`NoNetworkGuard`). detekt/`scanBannedConstructs`/Metalava are applied to `:core/:providers/:keystore` only [VERIFIED: invariants.gradle.kts applied in those three build files]; sample is out of their scope, but keep `else` branches and no secrets by convention. Do not add `api.txt`.
4. **Worktree contention.** `use_worktrees: true` plus Gradle daemons: run **one plan per wave, strictly sequential** (Phases 8/9 precedent); fixture is absent in worktrees (use adb push or main-checkout build).
5. **`OkHttp.VERSION` inlined** (see above): read reflectively; also log the jar name. Add a host `OkHttpPinTest` (reflective VERSION == `5.2.1` on the sample test classpath) and a `dependencies --configuration debugRuntimeClasspath` grep, and log the value on device.
6. **Warm cache false results** (cold enforcement above). Also do not rerun L1 back to back while debugging; use the fake provider on host.
7. **Model replies as evidence.** `Completed.reply` is model text: show length only in logs; never persist.
8. **Android 15 / Compose gotchas:** `testTagsAsResourceId`; dedicated `INTERNET`; Freecess; do not set a screen lock on the TESTER (reboot invariant); `connectedAndroidTest`/`pm clear` wipe data, so import keys after the last clear.
9. **Haiku band may be wrong for this fixture.** The +-5% band is D-03's example, not measured on this exact serialization; `cache_write` includes the ~496-token tool-use system prompt [CITED: pricing]. The measured value is the evidence: out-of-band is escalated to the master, not silently widened.
10. **Strict-schema surprises.** Do not set `strict=true` on synthetic optional tools (engine only sends strict when no optionals; PROV-12).

## Code Examples (shape only; no secrets)

```kotlin
// Composition root (names verified against sources above)
val pipeline = commandPipeline {
    tier(AgenticLoopStrategy(StrategyId("agentic")) {
        tooling = ToolSpecProvider.fixed(ToolingSnapshot(fixture.system, fixture.tools, null))
        executor = CannedToolExecutor(fixture.tools)
    })
    provider(AnthropicProvider { httpClient = okHttp; attemptObserver = counter.anthropic })
    gate = PreApplyGate { GateDecision.Admit() }          // canned-admit
    commitSink = LogSink(evidence)                        // kinds/tool names/counts only
    providerSelection = ProviderSelectionSource { ProviderSelection(ProviderId.ANTHROPIC, "claude-haiku-4-5") }
    credentials = KeystoreCredentialSource(apiKeyStore)
    listener = PipelineEventListener { e -> if (e is PipelineEvent.ProviderCall) evidence.turn(e) }
}
// SB-format turn line (D-02): iteration=1 model=.. stop_reason=.. tools=[names] input_tokens=.. output_tokens=..
//   cache_creation_input_tokens=<usage.cacheWrite> cache_read_input_tokens=<usage.cacheRead> prefix_chars=..
// extras on an env line: okhttp=<reflective> fixture_sha=ebd3ef4a…af4ed3e min_cacheable=4096 est_prefix_tokens=<chars/4>
```

## Runtime State Inventory
Not a rename/refactor phase: omitted. (State created by this phase: app-private `files/fixture`, `files/test-keys` (transient), DataStore ciphertext, AndroidKeyStore aliases; all removed by `cleanup`/uninstall.)

## Environment Availability

| Dependency | Required by | Available | Version | Fallback |
|------------|-------------|-----------|---------|----------|
| TESTER (USB `R5CT10XNKQN`) | all legs | yes (`adb devices` lists USB + `100.118.21.106:1496`) | SM-S908U | none: INFRA, never substitute |
| JDK 17 / Gradle 9.4.1 | build | yes (17.0.19) | | |
| `push-test-key`, `with-test-keys` | key hand-off | yes (`~/.local/bin`) | | stop and ask Yahir for the file |
| Test key files (`~/.config/test-keys/{anthropic,openai,openrouter}.key`) | live legs | dir exists (700); contents not read (denied) | | tools fail loudly naming the file |
| Compose artifacts offline | `:sample` build | partially seen (BOM 2026.04.01, activity-compose 1.13.0, lifecycle 2.10.0, plugin 2.3.20) | | online build once |
| Fixture file | L1 | yes (sha prefix/suffix verified) at SB path | | ask orchestrator to regenerate if sha differs |
| Network to JitPack / GitHub | wiring test | public | | |

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4.13.2 + `kotlinx-coroutines-test`, `:core` testFixtures; host shell verifiers |
| Config | `sample/build.gradle.kts` (add), existing `scripts/verify-*.sh` pattern |
| Quick run | `./gradlew :sample:testDebugUnitTest --offline` |
| Full suite | `./gradlew check --offline` + `scripts/verify-repo-hygiene.sh` + `scripts/jitpack-dry-run.sh` + `scripts/verify-sample-device-guard.sh` |

### Phase Requirements -> Test Map
| Req / Decision | Behavior | Type | Command / check | Where |
|----|----------|------|-----------------|-------|
| VER-01 fixture | absent -> typed Absent, no Gradle-time read; wrong sha -> ShaMismatch; good -> 18-tool-shaped parse (synthetic fixture) | unit | `:sample:testDebugUnitTest --tests '*FixtureLoaderTest'` + `jitpack-dry-run.sh` | host |
| VER-01 fake executor | read -> Finished READ; mutating -> Mutation with canned apply; unknown tool handled | unit | `*CannedToolExecutorTest` | host |
| VER-01 key via :keystore | save/read/delete/Ready(last4); importer deletes plaintext + self-scan false | unit (crypto seam) + device | `*TestKeyImporterTest`; UI step on TESTER; `run-as ls files/test-keys` empty | host + device-hw |
| VER-01 OkHttp pin | runtime VERSION 5.2.1 | unit + device log | `*OkHttpPinTest`; env line `okhttp=5.2.1` | host + device-hw |
| VER-02 | verdict PASS/WARM/FAIL, band edges (6,665/7,367), reads == write | unit | `*CacheVerdictTest` (property-style edges) | host |
| VER-02 | real cold run on Haiku, >=2 turns, 200s, prefix/min logged | live | Gate-1 L1 via UI; evidence `evidence/ver02.log` | device-hw |
| VER-03 smokes | parsed tool + optional-absent / INCONCLUSIVE rules; Anthropic INITIAL 200, no reshape | unit (fake provider) + live | `*SmokeVerdictTest`; L2-L4 | host + device-hw |
| VER-03 multi-turn | tool result replayed, >=2 provider calls on Chat | unit (fake) + live | `*MultiTurnLegTest`; L5, L6 | host + device-hw |
| D-02 evidence | closed vocabulary; canary prompt/args/key never appear; allow-list regex | unit + shell | `*EvidenceLineTest`; `capture-save` key-shape scan negative control | host |
| D-04 keys | no key on any adb argv; personal phone refused | shell | `verify-sample-device-guard.sh` | host |
| D-03 cold | warm window refusal; WARM classified INFRA | shell + unit | `cold-stamp` test in guard verifier; `CacheVerdictTest` | host |
| D-05 | no config-time fixture access | script | `jitpack-dry-run.sh`, hygiene (a)-(h) | host |
| VER-04 | docs complete + agent can wire | doc checks + wiring test | headings/coverage grep script; `agent-wiring-test.sh <sha>` | host + network |
| D-14 | Clarification options render; follow-up uses `parentRunId` | unit | `*ClarificationFlowTest` (fake provider) | host |

### Sampling Rate
Per task: `:sample:testDebugUnitTest`. Per wave: `./gradlew check --offline` + hygiene. Phase gate: full suite green, then Gate-1 (`gsd-agentic-tester`, `device-hw:`), then wiring test; SELF-UAT at `.planning/phases/10-.../10-SELF-UAT.md`, fragment `.planning/uat-pending/10-sample-harness-gate-1-docs.md` written by the tester [CITED: verify-work-agentic.md:263-289].

### Wave 0 Gaps
- [ ] Compose/test deps + `testFixtures` in `sample/build.gradle.kts`; synthetic test fixture; `scripts/verify-sample-device-guard.sh`; doc coverage grep script. Framework install: none (existing).

## Security Domain (ASVS L1, `security_enforcement` on)

| ASVS | Applies | Control |
|------|---------|---------|
| V2/V3 Auth/Session | no | n/a (BYO keys, no accounts) |
| V5 Input validation | yes | key `trim`/blank check (`ApiKeyStore.save`), fixture sha256 + JSON parse errors typed, tool args validated by canned executor |
| V6 Cryptography | yes | `:keystore` AES/GCM only; no hand-rolled crypto in sample |
| V7 Logging | yes | closed-vocabulary evidence; no reply/args/key; `toString()` safe types |
| V8 Data protection | yes | transient plaintext file deleted + self-scan; `allowBackup=false`; uninstall at end |
| V14 Config | yes | import code only in `src/debug`; INTERNET only permission; HTTPS-only providers |

| Threat | STRIDE | Mitigation |
|--------|--------|------------|
| Key in argv/logcat/screenshots/git | Info disclosure | `push-test-key` (file path only); last-4 only on screen; allow-list + key-shape scan; global git secret hook |
| Private fixture/prompt in public repo | Info disclosure | gitignored path, hygiene (b)/(c), evidence tool names/counts only |
| Wrong device (personal phone) | Tampering | guard block + fake-adb verifier; no env/arg overrides |
| Runaway spend | DoS/financial | per-leg iteration caps, request counter, 33-request ceiling, cold stamp |
| Stale keystore alias collision with SB/CT | Tampering | sample-prefixed aliases in its own package namespace |

## Suggested plan shape (one plan per wave, sequential; executors never touch a device)
1. Build/manifest/Compose spike + `FixtureLoader`/`ToolClassifier`/`CannedToolExecutor` + OkHttp pin test + dry-run/hygiene regression.
2. `KeySlots`, `ApiKeyStore` wiring, debug `TestKeyImporter`, `SampleEngine` composition root + host tests with fakes.
3. Verdicts + `EvidenceLine` + synthetic tool pair + the three leg runners + request counter (host tests).
4. Compose UI (testTags, readout, loud failure banners, clarification demo) + ViewModel.
5. `scripts/run-sample-gate1.sh`, `push-sample-fixture.sh`, `verify-sample-device-guard.sh`, redaction/leak scan.
6. `GATE1-RUNBOOK.md` (UI steps, SELF-UAT criteria `device-hw:`-tagged, bounded budget, approve/defer checkpoint routed via the master, INFRA rules).
7. `README.md` / `INTEGRATION.md` / `API.md` / `ECOSYSTEM.md` update + coverage grep script.
8. `agent-wiring-test.sh` + `AGENT-PROMPT.md` (dispatch runs after docs are pushed and the SHA is built; recorded in `10-WIRING-TEST.md`).
Gate-1 itself (cold L1 first, then smokes) is run by `gsd-agentic-tester` after execution.

## Assumptions Log

| # | Claim | Section | Risk if wrong |
|---|-------|---------|---------------|
| A1 | The +-5% band around 7,016 holds for this fixture on Haiku 4.5 (includes ~496 tool-use prompt tokens) | Pitfall 9, Patterns | Gate false FAIL; orchestrator decides band; measured value stays the evidence |
| A2 | Compose `testTagsAsResourceId` exposes resource-ids to uiautomator on Android 15 | Device mechanics | Tester falls back to text/content-desc; plan-1 spike |
| A3 | Full Compose graph resolves offline from the local cache | Standard Stack | One online build needed |
| A4 | OpenRouter `openai/gpt-5.4-mini` costs about upstream | Live legs | Cost estimate off by a few cents |
| A5 | Haiku 4.5 uses the pre-4.7 tokenizer | External facts | Band shifts ~30% |
| A6 | JitPack build.log success marker / tag-build timing | External facts | Phase 11 re-verifies |
| A7 | Routed model may omit optionals on L4 | Live legs | INCONCLUSIVE path covers it |
| A8 | Overwrite+delete of the plaintext key file is sufficient on app-private storage | Key reconciliation | Residual flash remnants; device is uninstalled at end |

## Open Questions
1. **Band policy if the measured prefix is outside +-5% but cache works.** Recommend: record, mark `PASS-with-note` only if the orchestrator relays approval; otherwise Gate-2 item. (Orchestrator decision.)
2. **Autorun intent extra** (convenience for re-runs) vs. pure-UI: recommend include in `src/debug` but never for the first Gate-1 run (D-01). Planner discretion.
3. **UI-SPEC** (`ui_phase: true`): the harness UI is a dev tool; recommend a minimal inline UI contract in plan 4 rather than a full `gsd-ui-phase`. Planner/orchestrator call.
4. **Wiring-test timing vs. Phase 11:** the tested SHA must precede the tag; if docs change after the test, re-run it.

## Sources
- Primary (read this session): `sample/*`, `core/src/main/.../{pipeline,strategy,commit,telemetry,provider,failure}`, `providers/.../{anthropic,chat}`, `keystore/.../*`, `scripts/*.sh`, `.gitignore`, `build.gradle.kts`, `gradle/libs.versions.toml`, `.planning/{REQUIREMENTS,STATE,ROADMAP,v1.0-DECISION-MAP}.md`, phase 5/8/9 evidence + SELF-UAT/uat-pending, `~/.claude/context/{devices,workflows}`, `~/.local/bin/push-test-key`, `~/.claude/gsd-core/{agents/gsd-agentic-tester.md,templates/AGENT-DEVICE-TESTING.md,workflows/verify-work-agentic.md}`; SB `AnthropicAgentLoop.kt` telemetry format; fixture parsed for structure only.
- Official docs fetched: platform.claude.com prompt-caching and pricing pages; developers.openai.com gpt-5.4-mini model page; openrouter.ai provider-selection guide.
- Tertiary: training knowledge for Compose/uiautomator behavior (A2).

## Metadata
**Confidence:** Standard stack HIGH (reuses CT/STACK picks); engine API HIGH (source-read); live behavior MEDIUM (A1, A7); docs scope HIGH.
**Research date:** 2026-10-01 · **Valid until:** ~2026-10-31 (model ids/prices drift; re-check Haiku/gpt-5.4-mini availability before the live run).
