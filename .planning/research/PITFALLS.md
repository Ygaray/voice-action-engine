# Pitfalls Research

**Domain:** Kotlin/Android JitPack library (multi-module: pure-Kotlin `:core`, OkHttp `:providers`, Android `:keystore`, debug `:sample`) that ports two apps' LLM tool-calling code (SB agentic loop on Anthropic, CT single-shot on Anthropic/OpenAI/OpenRouter) into one provider-neutral engine.
**Researched:** 2026-09-29
**Confidence:** HIGH overall. Most findings come from the port sources and sibling hubs on this machine (read directly). Anthropic facts come from the bundled claude-api reference (cached 2026-09-25), and CT's own live-verified notes back them up. OkHttp, OpenAI and OpenRouter facts come from web sources, cross-checked where marked.

**How to read the phase column.** Phases are keyed to contract §6.2 steps. Step **1** is the scaffold/publishing/detekt/fake harness. **2** is the `:core` contract + pipeline + gates. **3a** is the Anthropic transport + caching + OkHttp matrix + ON_DEVICE slot. **3b** is OpenAI + OpenRouter. **4** is `:keystore`. **5** is SingleShot. **6a** is the neutral transcript + mappers. **6b** is AgenticLoop. **7** is `:sample` + Gate-1 + tag.

---

## Critical Pitfalls

### Pitfall 1: The advertised JitPack coordinate is the wrong artifact (multi-module groupId coercion + aggregator POM)

**What goes wrong:**
PROJECT.md and the contract both name `com.github.Ygaray:voice-action-engine`. On a multi-module repo, JitPack serves that exact coordinate as a **synthesized aggregator POM**, and that POM depends on *every* module that published to mavenLocal. So a consumer that wants only `:core` + `:providers` gets `:keystore` as well, plus anything else that published. The per-module artifacts also resolve **only** under `com.github.Ygaray.voice-action-engine:<artifactId>:<tag>`. JitPack ignores the `groupId` in each module's publication block, and the "obvious" coordinate `com.github.Ygaray:voice-action-engine-core:v1.0.0` returns **401**.

**Why it happens:**
Every existing hub except stt-engine is single-module: backup-engine publishes `:backup` only, drive-auth publishes `:auth` only, and YAT is root-as-module. Its copied `publishing { groupId = "com.github.Ygaray" }` block looks authoritative, and nobody probes the per-module coordinate until a consumer fails.

**Evidence (HIGH, sibling repo):** stt-engine commit `b8c9fdb` ("correct JitPack secondary-module coordinates to real groupId"). The fix came from live HTTP probing in its `137-01-SUMMARY.md`. The contract reference is jitpack/jitpack.io#2872. stt-engine's INTEGRATION.md now carries a "groupId note" on every secondary module.

**How to avoid:**
- Decide the artifactIds in step 1 (e.g. `voice-action-engine-core`, `-providers`, `-keystore`). Treat them as one-way decisions, because they get baked into immutable tags.
- Before cutting v1.0.0, push a throwaway pre-release tag (e.g. `v0.0.1-probe`) and probe every module's POM **and** the aggregator over HTTP (`curl -sI https://jitpack.io/com/github/Ygaray/voice-action-engine/<artifactId>/<tag>/<artifactId>-<tag>.pom`). Then resolve each module from a scratch consumer project with an empty `GRADLE_USER_HOME`.
- Check the POM of `:providers` to see what groupId it declares for its `:core` dependency. If it lists `com.github.Ygaray:voice-action-engine-core`, which 401s, transitive resolution breaks. This needs a live probe; it is unverified for this repo.
- Put the real coordinates in the README and INTEGRATION. The engine's "done" bar is that an AI agent can wire it from the README, and a wrong coordinate fails that bar immediately.
- Message the orchestrator a proposed erratum for the coordinate the contract names (§6.2 heading, PROJECT.md line 5).

**Warning signs:** 401/404 on per-module coordinates. The README shows one coordinate for a 3-module library. `./gradlew :sample:dependencies` looks fine only because `:sample` uses `project(":core")` rather than the published coordinates.

**Phase to address:** Step 1 (design + probe tag). Re-verify in step 7 before the tag row goes to the orchestrator (§11 item 4).

---

### Pitfall 2: A non-published module (`:sample`) breaks the JitPack build or leaks into the aggregator

**What goes wrong:**
JitPack runs Gradle **configuration of every included project**, even though `jitpack.yml` only runs `:x:publishReleasePublicationToMavenLocal`. Suppose `:sample` reads `local.properties`, needs a fixture file that isn't committed, needs a signing config, or reads an API key from a `BuildConfig` env var. Then configuration fails on JitPack and **no module publishes**. A failed JitPack build for a tag is cached, and per §11 tags are immutable, so the recovery is a new patch tag. The other way to break it: someone copies YAT's root-level `./gradlew publishReleasePublicationToMavenLocal` form. If `:sample` ever applies `maven-publish`, it then ships inside the aggregator.

**Why it happens:** backup-engine has no sample module to copy from. stt-engine's `jitpack.yml` comment exists exactly because of this ("the reference host app ... must never appear in this install list"; "a mismatch is an unrecoverable failure discovered only after the mirror tag is immutable").

**How to avoid:**
- Use an explicit per-module install list in `jitpack.yml` (`:core`, `:providers`, `:keystore`), never the root task.
- `:sample` never applies `maven-publish`. It configures cleanly with no secrets, no `local.properties` and no env vars, and the key is typed at runtime in the BYO-key field. The A10 fixture JSON is committed (it contains no secrets).
- Add a CI step that simulates JitPack: a fresh clone, `JAVA_HOME` set to 17, the exact `jitpack.yml` commands, with `~/.m2` and `~/.gradle` empty.

**Warning signs:** `jitpack.yml` uses a root task. `:sample/build.gradle.kts` references `project.findProperty("ANTHROPIC_KEY")`. The first JitPack log shows "configuring project :sample" failing.

**Phase to address:** Step 1. Re-check in step 7 when `:sample` gains its fixture.

---

### Pitfall 3: Publishing the pure-Kotlin `:core` (and possibly `:providers`) the Android-library way

**What goes wrong:**
backup-engine's recipe is `singleVariant("release") { withSourcesJar() }` plus `from(components["release"])` inside `afterEvaluate`, and it is **Android-library-only**. Copy it into a `kotlin("jvm")` module and it fails with "release component not found". The JVM form is `java { withSourcesJar() }` + `from(components["java"])`. A second failure mode: a `jvmToolchain(21)` or `jvmTarget = 21` in a JVM module. JitPack's `openjdk17` image either can't provision it (no foojay resolver configured) or produces class files that the consumer's JDK-17 D8 still accepts, while the JitPack build itself fails. A third: mixing JAR (`:core`) and AAR (`:keystore`) is fine for Gradle consumers, but the aggregator POM then lists `<type>aar</type>` and `<type>jar</type>` entries. Anyone probing by hand must look for `.jar` vs `.aar` accordingly.

**How to avoid:**
- Pin `jvmTarget = 17` / `JavaVersion.VERSION_17` in **every** module, matching backup-engine and JitPack's `openjdk17`. Use no toolchain auto-provisioning.
- Use one convention for publications. A small `buildSrc`/convention-plugin function picks `components["java"]` vs `components["release"]` by module type, so the recipe isn't copy-pasted three times with drift.
- Decide `:providers`' module type deliberately. It has no Android API dependency, so `kotlin("jvm")` keeps its tests plain-JVM and fast. Android library is only needed if it ever touches `android.*`. The ON_DEVICE capability gate is the likely temptation: keep the *probe* behind a `:core` interface that the app or `:keystore`-side supplies, not inside `:providers`.

**Warning signs:** "SoftwareComponent with name 'release' not found". The JitPack log shows "Toolchain ... not found". The JAR is missing `-sources.jar`, and the README says "IDE shows decompiled code".

**Phase to address:** Step 1.

---

### Pitfall 4: An OkHttp 4.12/5.x "matrix" that doesn't actually prove A1

**What goes wrong:** There are several ways to get a green A1 matrix that proves nothing, or a red one that is spurious:
1. **Recompiling in the 5.x leg** (just bumping the catalog version) tests *5.x-compiled* bytecode. A1's real risk is *4.12-compiled bytecode running on 5.x*, which is SB's runtime and CT's after A11. That is the thing to prove.
2. **`Response.body` nullability.** In 4.12, `body` is `ResponseBody?`, so the code must use `body?.string()`; SB's `body.string()` is 5.x-only source (PROJECT.md already flags this). Compiled against 5.x, `body?.string()` produces an "unnecessary safe call" warning. With `allWarningsAsErrors` or detekt's type-resolved `UnnecessarySafeCall`, the 5.x leg goes red for a non-bug.
3. **MockWebServer version skew.** MockWebServer uses OkHttp internals. Running `mockwebserver:4.12.0` against `okhttp:5.x` at runtime (or the reverse) gives `NoSuchMethodError`/`NoClassDefFoundError`, which looks like an engine incompatibility. `mockwebserver3` (new package, immutable `MockResponse.Builder`, JUnit5) does not exist in 4.12. Only the legacy `okhttp3.mockwebserver` API exists in both lines (A11 confirms 5.x kept it).
4. **Catalog creep.** Someone bumps `okhttp` in `libs.versions.toml` to 5.x "because SB uses it". Now the engine compiles against 5.x, forces 5.x on any 4.12 consumer through the POM (Gradle picks the highest), and silently violates A1.
5. **Leaking okio or OkHttp internals.** Public signatures that use `okio.BufferedSource`, or any `okhttp3.internal.*` call (e.g. `closeQuietly`, `toHeaders`), break across the lines. 4.12 ships okio 3.6; 5.x ships okio 3.1x.
6. **Using 5.x-only conveniences.** `okhttp-coroutines` `executeAsync` and the 5.x `Call.executeAsync()` don't exist in 4.12. Keep CT's hand-rolled `Call.await()`.

**Why it happens:** "CI matrix on both versions" reads like a one-line Gradle property. Also, the ecosystem's belief that other hubs already prove 4.12→5.x is weaker than A11 implies. stt-engine's catalog pins **okhttp 5.2.1** (`android/gradle/libs.versions.toml:16`), so `:stt` is a 5.x-compiled AAR, not a 4.12 one. backup-engine (4.12.0 compiled, running on SB's 5.2.1) is the only real precedent.

**How to avoid:**
- Main source sets compile against **4.12.0 strictly**. Add a build assertion that fails if `:providers:compileClasspath` resolves anything other than `4.12.x`. Declare the OkHttp dependency normally so consumers' higher versions win.
- In the 5.x leg, **substitute only the test runtime classpath**: `configurations.testRuntimeClasspath { resolutionStrategy.force("com.squareup.okhttp3:okhttp:$v5", "com.squareup.okhttp3:mockwebserver:$v5") }` behind `-PokhttpRuntime=5.2.1`. Main bytecode stays 4.12-compiled. Always move okhttp and mockwebserver **together**.
- Write all transport tests against legacy `okhttp3.mockwebserver.MockWebServer` + `MockResponse().setBody()`.
- Add a JVM smoke test that asserts which OkHttp version is actually on the classpath at runtime (`okhttp3.OkHttp.VERSION`). A leg that silently ran 4.12 twice is then caught.
- Keep OkHttp types out of `:core`, per A7. In `:providers`, expose at most `OkHttpClient` as an optional constructor parameter, and if you do, declare it `api`.

**Warning signs:** The 5.x leg "passes" without the runtime-version assertion. `import mockwebserver3` appears. `body.string()` appears without `?`. The catalog says `okhttp = "5.`.

**Phase to address:** Step 3a (must-pass per A1). The harness skeleton (the Gradle property + version assertion) can land in step 1 so 3b inherits it.

---

### Pitfall 5: SingleShot's "forced tool call" is a 400 on current Anthropic models, and CT's guard list is already stale

**What goes wrong:**
SingleShot = "one forced-tool call" (PROJECT.md, CT port). **Claude Opus 5.5, Claude Sonnet 5.5, Claude Fable 5.1 and Claude Mythos 5.1 reject `tool_choice: {type:"tool"|"any"}` with HTTP 400** (`tool_choice: type "tool" and "any" are not supported for this model.`). The workaround is `tool_choice:auto` + `strict:true` on the tool + an explicit instruction naming the tool, and because `auto` doesn't guarantee a call, the code must **check a call was made**. CT handles this with a hard-coded `AnthropicKnownTool400Ids.SET = {claude-opus-5-5, claude-fable-5-1, claude-mythos-5-1}`, verified 2026-09-27. **That set is already missing `claude-sonnet-5-5`**, which the current reference also lists as rejecting forced tool use. Port it verbatim and SingleShot hard-fails the moment a user picks the current Sonnet.

**Why it happens:** Model-capability tables are hand-maintained constants, and the per-call model seam lets users pick any model id.

**How to avoid:**
- Model forced-tool support as a **provider capability resolved per model**. Use a bumpable default table in `:providers`, overridable from policy/config, never a `const` buried in a request builder.
- **Add a reactive fallback.** On a 400 whose `error.message` matches the forced-tool rejection, retry once with `auto` + `strict` + an instruction. Record it in the trace as a typed event (`ForcedToolUnsupportedRetried`), so the table gets bumped from evidence.
- Treat "no tool call returned" under `auto` as `NoMatch`/`Escalate(reason = NO_TOOL_CALL)`, never as `Completed` with empty data.
- `strict:true` requires `additionalProperties:false` + `required` on every object. The neutral ToolSpec must be able to emit that, or strict silently can't be used.
- The same applies to OpenAI. Forced `tool_choice:{type:"function"}` works on gpt-4o-mini (CT default), but newer reasoning models have other parameter restrictions (see Pitfall 8).

**Warning signs:** `tool_choice` built unconditionally as `{type:"tool"}`. A model-id `setOf(...)` in a request builder. No test for "auto returned text only".

**Phase to address:** Step 3a (capability model + 400 classification), step 5 (SingleShot fallback + no-call handling).

---

### Pitfall 6: Turn-2 `cache_read_input_tokens == 0` on the A10 Gate-1 (silent cache invalidation)

**What goes wrong:** Caching fails silently: requests succeed, `cache_read_input_tokens` stays 0, and the bill is higher. For this engine, the concrete ways to lose the SB parity number (7,016) are:
1. **Something dynamic moves into the prefix.** SB puts `Current local date-time` and the transcript in the **first user message**, not the system prompt, which is correct. A generalized `ToolSpecProvider.systemPrompt(input)` that lets apps interpolate the date, language or user name into `system` busts the cache on every request. `CommandInput.language` is the likely culprit ("reply in Spanish" appended to system).
2. **Tool serialization order isn't deterministic.** SB's `toolDefinitions` is a `JsonArray` built in a fixed order. A neutral `ToolSpec` held in a `Set`/`HashMap` and re-serialized by a per-provider mapper can reorder tools or keys. Tools render at position 0, so any reorder invalidates **everything**.
3. **The model changes mid-loop.** Caches are model-scoped. If the loop re-queries the "active provider/model/key" seam on **every iteration** ("a switch takes effect on the next call"), a settings change or a router decision mid-command switches model: cache miss plus a mixed-provider transcript.
4. **thinking/effort varies between turns.** The effect is model-specific: this invalidates the messages cache always, and the tools/system cache on some models. Pin them per command.
5. **The prefix is below the model minimum.** Minimums are **not monotonic**: 512 tokens on Opus 5.5/Sonnet 5.5/Fable 5.x/Opus 5; 1024 on Sonnet 5/Opus 4.8/Sonnet 4.6; 2048 on Opus 4.7; **4096 on Haiku 4.5**, SB's default model. The ~7k fixture clears 4096. A trimmed or "cleaned up" fixture, or a real app with a small tool set on Haiku, silently won't cache: no error, `cache_creation_input_tokens: 0`.
6. **More than 5 minutes between turns.** TTL is 5 min by default and counts from request *start*. A `PreApplyGate` that **suspends waiting for the user's confirmation** (SB `VoiceConfirmGate`) can easily exceed 5 minutes. The next turn then legitimately reads 0. Gate-1 must not misread that as a regression, and a scripted Gate-1 run must not include a slow manual confirm.
7. **Breakpoint moved rather than added.** A10 requires at least SB's placement: one `cache_control:ephemeral` on the last system block, which caches tools+system. A mapper that puts the breakpoint only on the last message (or only uses top-level auto-caching) drops the guaranteed read point. Adding a moving message breakpoint is allowed; keep the total ≤ 4.
8. **Wrong usage math.** Anthropic's `input_tokens` is the *uncached remainder*. Total prompt = `input + cache_creation + cache_read`. Reading only `input_tokens` makes caching look broken, or makes it look like it works when it doesn't.
9. **Parallel first requests.** A cache entry becomes readable only once the first response starts. Two commands fired concurrently both pay full write. This isn't a loop bug, but it confuses telemetry.

**How to avoid:**
- Split `ToolSpecProvider` into a **frozen prefix** (tools + system, byte-stable, provided once per pipeline) and **per-command context** that the engine injects into the first user message. Document that nothing per-request may enter `system`.
- Serialize tools as an ordered `List`. Add a unit test: build the request body twice from equal inputs and assert **byte-identical** tools+system JSON. Add another: across iterations 1..N of a fake loop, the tools+system prefix is byte-identical.
- Snapshot `(provider, model, key, thinking/effort)` **once per command execution**. The seam is re-read per command, not per iteration.
- In the A10 Gate-1 script, assert turn 1 `cache_creation_input_tokens > 0` **and** turn 2 `cache_read_input_tokens > 0`. Log `prefix_chars` like SB does. Use a model whose minimum is known (Haiku 4.5 → need ≥ 4096 prefix tokens). Put the confirm-gated tool calls in canned "admit" mode for the automated run.
- In the trace, record `cache_creation` and `cache_read` separately per iteration. Record gate wait time too, so a TTL expiry can be explained.
- Optional, for debugging only: the Claude API's cache diagnostics beta (`cache-diagnosis-2026-04-07`, `diagnostics.previous_message_id`) names the divergence point. Keep it off by default.

**Warning signs:** `system` built with string templates that include `input`. Tools stored as `Map<String, ToolSpec>`. The provider seam called inside the loop body. `cache_creation_input_tokens` near the full prefix on every turn.

**Phase to address:** Step 3a (caching capability + usage parsing), step 6a (mapper determinism + breakpoint placement), step 6b (per-command snapshot), step 7 (Gate-1 script).

---

### Pitfall 7: The neutral transcript drops provider-opaque content or reorders tool results

**What goes wrong:**
SB's loop echoes the assistant `content` array **verbatim** (`put("content", rawContent)`). A "clean" neutral model (`Text | ToolCall | ToolResult`) that re-renders assistant turns from neutral parts loses:
- **`thinking`/`redacted_thinking` blocks and their signatures.** SB's Sonnet 5 fallback runs adaptive thinking by default. Opus 5.5/Sonnet 5.5 also return between-tool progress as `thinking` blocks. When tools are involved, thinking blocks must be passed back unchanged. Under "preserved thinking" (accounts created on or after 2026-08-31 are enforced), an edited history gets a **400**, or dropped blocks plus a messages-cache miss.
- **Exact bytes of earlier turns** (key order, escaping). Any re-encoding changes the prefix → cache miss from that turn on.
- **OpenRouter `reasoning_details`** (reasoning models routed via OpenRouter expect it echoed back). This is less certain (MEDIUM); verify in 3b.

The mappers also get ordering rules wrong:
- **Anthropic:** every `tool_use` in an assistant turn needs a `tool_result` in the **single next user message**, and `tool_result` blocks come **before** any text. Splitting results across messages silently trains the model away from parallel calls.
- **OpenAI:** an assistant message with `tool_calls` must be followed by **one `role:"tool"` message per `tool_call_id`**. A missing one is a 400. There is no `is_error` field, so error status has to be encoded in the content.

**How to avoid:**
- Every assistant turn in the neutral transcript carries a `providerNative` payload: the raw JSON the provider returned, opaque to `:core`. The same-provider mapper replays it **verbatim**, and the neutral parts are a *derived view* for the loop's logic.
- Transcripts never cross providers. `Escalate.carry` carries **semantic** partial work (extracted entities), never a transcript or tool-call ids. (`toolu_…` and `call_…` ids are provider-specific and meaningless to the other side.)
- Mapper golden tests: a round-trip fixture per provider with thinking blocks, parallel tool calls, an error result, and an empty-args call (`{}` vs `""`/`null`).

**Warning signs:** The assistant turn is stored as `List<NeutralPart>` only. The mapper rebuilds `content` from parts. No test fixture contains a `thinking` block.

**Phase to address:** Step 6a (the design decision; expensive to retrofit after 6b). Verify in step 6b with a Sonnet-5-style fixture.

---

### Pitfall 8: OpenAI/OpenRouter mapping mismatches that survive JVM fakes

**What goes wrong:** Each of these passes a fake-provider test and fails live:

| Mismatch | Anthropic | OpenAI Chat Completions | Consequence if unmapped |
|---|---|---|---|
| Tool args | `input` is a JSON **object** | `function.arguments` is a JSON **string**; may be invalid/truncated JSON, or `""` for no args | Crash/`MALFORMED`; CT double-decodes with its own catch, keep that |
| Stop signal | `stop_reason`: `tool_use`/`end_turn`/`stop_sequence`/`max_tokens`/`refusal`/`pause_turn`/`model_context_window_exceeded` | `finish_reason`: `tool_calls`/`stop`/`length`/`content_filter` (+ OpenRouter `error`, raw value in `native_finish_reason`) | Wrong typed reason; decide "tool turn" by **presence of tool_calls**, and treat `finish_reason` as a cross-check (some routed providers report `stop` with tool_calls: MEDIUM/LOW, verify in 3b) |
| Refusal | `stop_reason:"refusal"` at **HTTP 200** (+ `stop_details.category` on 4.7+) | `message.refusal` non-null with `finish_reason:"stop"`, or `content_filter` | Refusal shown as "no match" or success; must be typed `REFUSAL` on both |
| Output cap | `max_tokens` (thinking counts toward it; SB raised to 4096 for this) | Reasoning models **reject `max_tokens`** ("use `max_completion_tokens`"); reasoning tokens count toward it | 400 on newer OpenAI models; truncated → `length` |
| System role | top-level `system` | `system`, or `developer` on newer reasoning models | Mostly tolerated; keep it mapper-configurable |
| Parallel calls | default on; results batched in one user msg | `parallel_tool_calls` default true; one `tool` msg per id | For SingleShot set `parallel_tool_calls:false` / `disable_parallel_tool_use:true` |
| Strict schema | `strict:true` needs `additionalProperties:false`, all `required` | Strict subset strips keywords (CT's "strict-mode keyword strip") | Schema valid for one provider 400s on the other |
| Token usage | `input_tokens` **excludes** cached; `cache_read_input_tokens`, `cache_creation_input_tokens` separate | `prompt_tokens` **includes** `prompt_tokens_details.cached_tokens`; OpenRouter adds `cache_write_tokens`, `cache_discount` | Double-counted tokens → `TOKEN_CEILING` trips early; wrong trace |
| Caching | explicit `cache_control` (or top-level auto) | automatic, ≥1024 tokens, nothing to send | Sending `cache_control` to OpenAI is ignored/400; OpenRouter→Anthropic **needs** it |
| Model ids | `claude-haiku-4-5` | OpenRouter needs vendor-prefixed `openai/gpt-4o-mini` (CT note: a bare id routes to an unknown third-party host) | Tool-choice unsupported on the routed host |

**OpenRouter specifics:**
- Provider routing can land an `anthropic/*` model on a different upstream, and the cache is per upstream. Sticky routing keys on a hash of the opening messages, or on `session_id` (10-min inactivity expiry). For an agentic loop, send a stable `session_id` per command.
- Cache fields live under `usage.prompt_tokens_details`.
- `HTTP-Referer`/`X-Title` are optional (CT omits them).

**How to avoid:**
- Use one `NeutralUsage(inputUncached, cacheRead, cacheWrite, output)` with per-provider normalizers. A unit test per provider asserts `total = sum` with no double count.
- The `ProviderResult` stop reason is a neutral enum mapped per provider. Unknown values map to `UNKNOWN_STOP_REASON` carrying the raw string in the trace, the way SB does it. Never map an unknown value to success.
- Keep a per-model parameter policy (`maxTokensParamName`, `supportsForcedToolChoice`, `supportsParallelToolCallsFlag`) in the same bumpable capability table as Pitfall 5.
- Contract tests use **recorded real response bodies** (sanitized) per provider, not hand-written fakes.

**Phase to address:** Step 3a (neutral usage/stop types), step 3b (OpenAI/OpenRouter mappers + fixtures), step 6a (multi-turn tool message ordering).

---

### Pitfall 9: Mutations duplicated by escalation, or "held" reported as success

**What goes wrong:**
- **Escalating after a side effect.** The pipeline rule is "`Escalate`/`NoMatch` → next tier". Suppose a strategy has already committed through `CommitSink`/`ToolExecutor` (the agentic loop created a card, then hit `TOOL_FAILURE` or `BudgetExceeded`) and still returns `Escalate`. The next tier re-executes the command and **duplicates the mutation**. SB's discipline ("`executedTools` populated on every variant; a mutation is never hidden behind a failure") covers reporting but not re-execution. Escalation is new with the pipeline.
- **Held shown as success.** SB returns `{"applied":false,"status":"held_for_confirmation"}` to the model, and the model replies "needs your confirmation" → `AgentLoopResult.Done(text)`. If the engine maps `Done` → `Completed(result)` without surfacing held calls, a consumer's success sheet renders a non-applied action as done.
- **Double gating or no gating.** A6 puts `PreApplyGate` at pipeline level, but SB gates *inside* the loop, per tool, before dispatch (a pipeline can't gate a commit that already happened mid-loop). If both run, the user gets asked twice. If each assumes the other runs it, nothing is gated.
- **Tool runs on the last iteration.** SB's "no tool on the final permitted iteration" guard depends on `iteration == MAX_ITERATIONS`. With policy-driven limits, `maxIterations = 1` makes the agentic tier unable to ever run a tool. Validate `maxIterations >= 2` at pipeline build time, and fail loudly.
- **Token ceiling counts cache reads at full weight.** SB's `turnTokenTotal` sums input + output + cache_creation + cache_read against 60,000. With a 7k cached prefix × 6 turns that is 42k, most of the budget spent on tokens that cost 0.1× (0.05× on Opus 5.5). A consumer with a 15k prefix trips `TOKEN_CEILING` by turn 4.

**How to avoid:**
- Make it a pipeline invariant, enforced in `:core` and unit-tested: **once any commit has happened in a tier, that tier's outcome is terminal** (`Completed` or `Failed(committed = …)`). An `Escalate` returned after a commit is converted to `Failed(PARTIAL_COMMIT_THEN_<reason>)` and trace-flagged.
- Make "held / needs-confirmation" first-class in the outcome, e.g. `Completed(result, pending: List<HeldAction>)` or a dedicated variant, decided **before** the v1.0 tag (see Pitfall 12). Never leave it as a text convention between the tool_result and the model.
- One gate contract, two call sites. `PreApplyGate.admit(action)` is called by the **ToolExecutor dispatch path** for agentic tools and by the **pipeline commit path** for SingleShot. Each action carries a `gated = true` marker so the pipeline doesn't re-ask.
- The ceiling policy either counts cache reads separately (`maxUncachedTokens`, `maxCacheReadTokens`) or uses a cost-weighted budget. Document SB's current semantics as the default so SB parity holds.

**Phase to address:** Step 2 (invariant + outcome shape + gate contract), step 6b (loop guards under policy-driven limits).

---

### Pitfall 10: Cancellation swallowed, timeouts misclassified, responses leaked

**What goes wrong:**
- `runCatching { … }` and `catch (e: Exception)` / `catch (e: Throwable)` catch `CancellationException`. The never-throw collapse then turns a user cancel into `Failed(NETWORK)`, and the coroutine keeps running (e.g. it dispatches a tool after the user backed out). This is the #1 port hazard, because the "never throw" contract invites broad catches, and detekt's `TooGenericExceptionCaught` pushes people toward `runCatching`, which is worse.
- **`withTimeout` inside the engine** throws `TimeoutCancellationException`, which *is a* `CancellationException`. "Always rethrow cancellation" then propagates an engine-owned timeout to the caller as a cancel instead of `Failed(TIMEOUT)`.
- **The `Call.await()` bridge leaks.** If `onResponse` fires after the continuation was cancelled, `continuation.resume(response)` drops the `Response` without `close()` → connection/pool leak. This affects CT's and SB's bridge as currently written. Fix: resume with an `onCancellation` handler that closes the response (the current kotlinx.coroutines `resume(value) { _, value, _ -> value.close() }` form, since coroutines 1.11 is in use).
- **Timeout shape.** OkHttp read/call timeouts surface as `InterruptedIOException`, which gets mapped to `NETWORK`. The user then sees "network error" when the real cause was a slow model turn. SB uses a 60s call timeout because Sonnet-5 thinking turns are slow; CT uses 10–20s for single-shot. One shared client with CT's timeouts makes agentic flaky.

**How to avoid:**
- Ban `runCatching` in library code; `ForbiddenMethodCall` in detekt config can enforce it. Every broad catch is preceded by `catch (e: CancellationException) { throw e }`. Add a unit test per strategy: cancel mid-request, assert the coroutine cancels **and** no ToolExecutor call happens after the cancel.
- Use `withTimeoutOrNull` for engine-owned deadlines and map `null` → `Failed(TIMEOUT)`. Or catch `TimeoutCancellationException` *before* the general cancellation rethrow, only where the engine owns the timeout.
- Make `TIMEOUT` a typed reason distinct from `NETWORK`. Timeouts come from policy per strategy (single-shot short, agentic long) via `client.newBuilder()` derived clients, which share the connection pool.

**Phase to address:** Step 2 (outcome-collapse helper + tests), step 3a (`Call.await` + timeouts).

---

### Pitfall 11: `:keystore` generalization strands existing users' keys

**What goes wrong:** Existing stored keys become undecryptable, and every user is forced to re-enter their API keys (a silent regression that SB/CT Gate-1 would catch late) if the library:
- derives aliases from a formula (e.g. `"${app}_${provider}_api_key"`),
- creates its own DataStore file, or
- changes the encoding.

The existing schemas are asymmetric, which a formula can't express:

| App | Provider | Keystore alias | DataStore keys | DataStore file | Base64 |
|---|---|---|---|---|---|
| SB | Anthropic | `secondbrain_anthropic_api_key_v1` | `anthropic_api_key_ct` / `_iv` | SB `app_preferences` (via `ThemePreferenceManager`) | `java.util.Base64` (standard, no wrap) |
| CT | Anthropic | `caltracker_api_key_v1` (no provider in name) | `anthropic_api_key_ct` / `_iv` | CT prefs | `android.util.Base64.NO_WRAP` |
| CT | OpenAI | `caltracker_openai_api_key_v1` | `openai_api_key_ct` / `_iv` | CT prefs | same |
| CT | OpenRouter | `caltracker_openrouter_api_key_v1` | `openrouter_api_key_ct` / `_iv` | CT prefs | same |

Note that **both apps use the same DataStore key names under different aliases**. CT's own code calls this "Pitfall 3 ... an explicit exhaustive `when`, NEVER a naming formula".

Related traps:
- **Two DataStores on one file.** If `:keystore` calls `preferencesDataStore(name = "app_preferences")` while the app already has one → `IllegalStateException: multiple DataStores active for the same file`.
- **Decrypt path creates keys.** Both ports call `getOrCreateKey` on **decrypt**. When the key is missing (fresh device after a cloud restore, or keystore wiped), decrypt *creates a new key* and then fails with `AEADBadTagException`. The typed state becomes "corrupt" instead of "key missing". **SB has `allowBackup="true"` with template (empty) backup rules**, so its DataStore ciphertext *is* restored to new devices while keystore keys never are. CT has `allowBackup="false"`.
- **Supplying an IV at encrypt** → `InvalidAlgorithmParameterException` (randomized encryption is required; read back `cipher.iv`). Reusing a GCM IV is catastrophic. Both ports get this right; don't "simplify" it.
- **StrongBox.** Adding `setIsStrongBoxBacked(true)` for new keys throws `StrongBoxUnavailableException` on devices without StrongBox. Key params also can't change for an existing alias, so it only affects new keys → inconsistent security story. Out of scope for v1.0.
- **JVM tests.** `android.util.Base64` and `org.json` are stubs on the plain JVM (they return defaults silently; stt-engine hit this with `org.json`). Robolectric has no `AndroidKeyStore` provider.

**How to avoid:**
- `:keystore` takes an app-supplied **explicit** table: `KeySlot(provider, alias, ctPrefKey, ivPrefKey)`, plus the app's **existing** `DataStore<Preferences>` instance. This is backup-engine's `BackupConfig.dataStore` precedent (`api(libs.datastore.prefs)`). The library ships no default aliases that could collide.
- Encode with `java.util.Base64` standard/no-wrap. Its output is byte-identical to `android.util.Base64.NO_WRAP` for the standard alphabet, so it reads both apps' data. Decode leniently.
- Keep separate `getKeyOrNull` (decrypt path, never creates) and `getOrCreateKey` (encrypt path, `@Synchronized`, as both ports do). Typed read states: `NotConfigured | Ready | KeyMissing | Unreadable`. `KeyMissing` must render loudly as "re-enter your key", never as a network error.
- In the README, tell consumers to exclude the key prefs from Auto Backup (or explain why `KeyMissing` appears after restore).
- `:sample` Gate-1 does an on-device round-trip. It also runs a **compat test**: write with a copy of the app's legacy code path (alias + pref keys + Base64), then read through `:keystore`.

**Phase to address:** Step 4 (schema + seam), step 7 (on-device round-trip in `:sample`). Consumers verify their migration in Wave-1 Gate-1, but the engine must make stranding impossible by construction.

---

### Pitfall 12: Public API "strictly additive" that still breaks consumers (sealed/enum growth, data classes)

**What goes wrong:**
- **Adding a variant** to `StrategyOutcome`, or a value to a failure-reason enum or `ProviderId`, is *additive* to Metalava/BCV, so the §11 item 2 gate passes. But every consumer `when (outcome) { … }` without `else` becomes a **compile error** on repin (sealed), or throws `NoWhenBranchMatchedException` at runtime if it was compiled against the old version. `StrategyOutcome`, the typed failure reasons, and the telemetry event types are exactly the types consumers exhaustively `when` over to drive YAT sheets.
- **Adding a field to a public `data class`**, even with a default, changes the constructor, `copy()` and `componentN` → a **binary break**, and the API dump shows a removal, so the §11 gate would (correctly) fail. backup-engine did "4 additive defaulted BackupConfig members". Consumers recompiled so it didn't bite, but it isn't "strictly additive" by the letter.
- **Tooling doesn't work on this stack as expected.** YAT found that Kotlin's built-in `abiValidation` and classic `binary-compatibility-validator` **don't work on AGP-9 built-in-Kotlin** Android modules, so it uses Metalava (`me.tylerbwong.gradle.metalava` 0.5.0) with `apiDump`/`apiCheck` wrapper tasks. Metalava also leaked Dagger `_Factory` classes into `api.txt` (KI-2026-09-02-01). VAE has no DI, so it avoids that, but the lesson holds: generated symbols pollute dumps.

**How to avoid:**
- Before v1.0.0, **enumerate v1.1's needs** and put them in v1.0's closed hierarchies now: held/needs-confirmation, plan-step failure, router decision, on-device reasons.
- Give every reason enum an `UNKNOWN`/`OTHER` member. Document in the README ("consumers MUST include `else`"), and show `else` in `:sample`.
- Prefer a sealed interface with an open `Other(code: String)` leaf for failure reasons, so new reasons can appear without an enum constant.
- Avoid `data class` for public config/result types that will grow. Use a regular class with `val`s + a builder/DSL (the pipeline is already a DSL), or accept that growth means a minor bump with a documented break.
- Pick the API tool per module in step 1:
  - Pure-JVM `:core`/`:providers`: BCV or Kotlin `abiValidation` works on `kotlin("jvm")`.
  - `:keystore` (Android): Metalava as YAT does.
  - Commit dumps from the first scaffold, so v1.0.0 has a baseline for v1.0.x patches.
- Set `explicitApi()` on all published modules so nothing becomes public by accident. `internal` is the default for mappers, DTOs and helpers.

**Warning signs:** `enum class …Reason` with no catch-all. `data class` in a public config. No `api/` dump committed before the tag.

**Phase to address:** Step 1 (tooling + `explicitApi`), step 2 (outcome/reason hierarchy design, the main decision), step 7 (the §11 additive-diff gate).

---

### Pitfall 13: Privacy and correctness in provider fallback (offline-only leaks to cloud; wrong key sent)

**What goes wrong:**
- A5 says "ON_DEVICE absent → clean cloud fallback". If the fallback lives in `ProviderRouter` and ignores `TierPolicy.offlineOnly`, a user who chose offline-only has their transcript **sent to a cloud provider** because the S22 has no AICore. That is a privacy violation that no test catches, since the fake harness has no network.
- `allowedProviders`, and the per-call key seam ("never substitutes one provider's key for another's"), get violated during fallback. The router falls back from OPENAI to ANTHROPIC and reuses the fetched key, or sends an `sk-or-` key to OpenAI. The result is a 401 that gets classified as "bad key", or worse, a key sent to the wrong vendor.

**How to avoid:**
- Resolve policy **before** routing. `offlineOnly` ⇒ the provider set is `{ON_DEVICE}` ∩ available. If that is empty, return a typed outcome `Failed(OFFLINE_UNAVAILABLE)` (loud). Never fall back to cloud.
- Keys are fetched **by the provider id actually being called**, at call time. A missing key returns `Failed(MISSING_KEY(provider))` and never borrows another provider's key.
- Add JVM tests: offlineOnly + no ON_DEVICE → zero HTTP calls (fake transport asserts it was never invoked). A fallback path → the key-seam spy was asked for the fallback provider's id.

**Phase to address:** Step 2 (policy resolution), step 3a (router + capability gate).

---

### Pitfall 14: Secrets and user content leaking through `toString`, exceptions, error bodies and the trace

**What goes wrong:**
- Data classes print every field. `CommandInput(transcript, …, context)`, provider request types, `Escalate.carry` (extracted entities = user data), and tool-call records with `input` all reach logs through string templates or crash reports.
- OkHttp `Request`/`Headers` `toString()` has historically included header values. Any built-in redaction covers standard auth headers, not the custom `x-api-key`. Wrapping `IOException("failed $request")` leaks the key (MEDIUM; don't rely on OkHttp redaction either way).
- Provider **error bodies** can echo request content (validation errors quoting message content). Putting `response.body` into `Failed(message)` or the trace leaks transcript/tool args.
- A logging interceptor added "temporarily" for 3b debugging. The consumer's own OkHttpClient passed in with *their* `HttpLoggingInterceptor` at BODY level.
- The A10 BYO key in `:sample` gets persisted in plain SharedPreferences or logged in debug logcat.

**How to avoid:**
- Redacted `toString()` on every public type holding transcript/key/args/carry. SB's pattern is `transcriptLength=` only. Add a unit test that reflects over public types and asserts `toString()` of a populated instance contains none of the sentinel values.
- The engine constructs its own client **derived** from the consumer's via `newBuilder()`, and **strips interceptors**, or refuses clients that have them. Either way, document it. SB's rule is: "No interceptor may ever be attached to a client carrying this qualifier".
- Typed failures carry `httpStatus` + provider `error.type` only, never the body text.
- `:sample` stores the key only via `:keystore`, which is part of what it's proving.

**Phase to address:** Step 2 (types + test), step 3a/3b (transports), step 7 (`:sample`).

---

### Pitfall 15: A "zero-baseline" detekt that's clean only because rules are skipped or suppressed

**What goes wrong:**
- The plain `detekt` task **skips type-resolution rules** (`UnnecessarySafeCall`, `UnreachableCode`, `IgnoredReturnValue`, `UnsafeCallOnNullableType`, …). Only `detektMain`/`detektTest` run them. A green `./gradlew detekt` then isn't the full ruleset. detekt 1.23.8 (YAT's version) embeds an older Kotlin compiler, so type-resolved runs against Kotlin 2.3 sources can error or be flaky. Decide deliberately, and don't discover it at tag time.
- The port's intentional patterns trip rules. The never-throw collapse trips `TooGenericExceptionCaught`/`SwallowedException`. Stop-reason mapping trips `ReturnCount`/`CyclomaticComplexMethod`. HTTP codes and token limits trip `MagicNumber`. Long schema strings trip `MaxLineLength`. SB's `LoopState` bundle exists *because of* `LongParameterList`. The easy way out is sprinkled `@Suppress`, which is baseline debt that doesn't live in the baseline file.
- Running `detektBaseline` once "to get green" silently creates the debt file the requirement forbids.

**How to avoid:**
- Start from the shared strict config YAT already layers (`config/detekt/detekt.yml`). Tune rules **up front** with written rationale: allow `CancellationException`-rethrow + `IOException`/`SerializationException` catches, and configure `ignoreNumbers` for HTTP statuses.
- Add a CI check: the baseline file is absent or contains 0 `<ID>` (YAT's has 0), **and** `@Suppress` occurrences in library `src/main` are counted and each needs a `// detekt:` justification.
- Choose, and document, whether type-resolved detekt runs. If it doesn't, the matching compiler warnings (`-Werror` on the 4.12 compile leg) cover `UnnecessarySafeCall`-class issues.

**Phase to address:** Step 1 (config + gate). Applies to every later step.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Port CT's `AnthropicKnownTool400Ids` set verbatim | Zero design work | Already stale (`claude-sonnet-5-5` missing); SingleShot 400s on model switch | Never without the reactive 400 fallback |
| Keep SB's model IDs / `MAX_ITERATIONS=6` / `60_000` / `4096` as `const val` | Byte-parity with SB | Violates the "limits from policy" requirement; next app can't tune | Only as **defaults** in a policy object |
| Neutral assistant turn without the raw provider payload | Simpler types | Loses thinking blocks/signatures → 400s and cache misses on Sonnet 5+/Opus 5.5 | Never (Pitfall 7) |
| `kotlinx.serialization.json.JsonObject` in public seam signatures (SB `MutationGate.admit(toolName, JsonObject?)`) | Direct port of SB shape | Consumers pinned to a serialization major; `:core` gains a hard `api` dep | Acceptable if declared `api` and documented. Both consumers already use it. Decide in step 2 |
| One shared OkHttpClient for all strategies | Less config | CT's 10–20s timeouts break agentic; SB's 60s makes single-shot hang | Only with per-strategy `newBuilder()` timeouts |
| `@Suppress` instead of rule tuning | Green detekt now | Hidden debt the zero-baseline requirement forbids | Only with a written justification per site |
| Fake-provider tests only for OpenAI/OpenRouter (A8 allows it) | No keys needed in CI | Fakes encode our assumptions, not the wire format (Pitfall 8) | Acceptable **if** fakes replay recorded real bodies |
| Documenting only the aggregator coordinate | One-line install | Consumers pull `:keystore` + everything; wrong for JVM-only use | Never; list per-module coordinates |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| JitPack (multi-module) | Trust the `groupId` in the publish block | Per-module coordinate is `com.github.Ygaray.voice-action-engine:<artifactId>:<tag>`; probe before tagging |
| JitPack builds | Assume a tag builds on push | JitPack builds **lazily** on first request and caches the result forever. Poll/backoff after tagging, fail loud; a failed tag needs a new patch tag (§11 immutability) |
| JitPack snapshots | Use `main-SNAPSHOT` in `:sample` or docs | `:sample` uses `project(...)` deps; docs pin tags only (sibling hub doctrine) |
| Anthropic caching | Breakpoint only on the last message / auto-caching only | Explicit `cache_control` on the last **system** block (A10), optionally plus a moving tail breakpoint; ≤ 4 total |
| Anthropic usage | Read `input_tokens` as the prompt size | Total = `input + cache_creation + cache_read` |
| Anthropic forced tools | `tool_choice:{type:"tool"}` everywhere | Per-model capability + reactive fallback to `auto` + `strict` + a "was a call made?" check |
| Anthropic thinking | Re-render assistant turns from parsed parts | Replay the raw content array verbatim (SB does) |
| OpenAI | Send `max_tokens` to every model | Per-model param name (`max_completion_tokens` for reasoning models) |
| OpenAI | Parse `arguments` as an object | It is a JSON **string**; double-decode with its own catch (CT) |
| OpenAI tool results | Batch results in one message | One `role:"tool"` message per `tool_call_id`, right after the assistant message |
| OpenRouter | Bare model ids; no `session_id` | Vendor-prefixed ids; stable `session_id` per command for sticky routing/cache; `cache_control` required for `anthropic/*` caching |
| OkHttp 4.12 vs 5.x | Recompile against 5.x to "test 5.x" | Force only the test runtime classpath (okhttp + mockwebserver together) |
| AndroidKeyStore | Test with Robolectric | Seam + in-memory fake on JVM; real round-trip only on device (`:sample` Gate-1) |
| DataStore | Library creates `preferencesDataStore(...)` | Accept the app's existing `DataStore<Preferences>` (backup-engine precedent) |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| New `OkHttpClient` per call/provider | Thread/socket growth, slower TLS | Share the pool via `newBuilder()` from one base client | Tens of commands per session |
| Token ceiling counts cache reads at full weight | `BudgetExceeded(TOKEN_CEILING)` on legitimate 4–5 turn commands | Separate uncached/cached budgets or cost-weight them | Prefix > ~10k tokens × ≥ 4 turns |
| Cache below the model minimum | `cache_creation_input_tokens: 0` always, full price every turn | Assert the prefix size vs. the model minimum in `:sample`; surface in the trace | Haiku 4.5 with < 4096-token prefix; Sonnet 5 < 1024 |
| Confirm-gate wait > TTL | Turn after confirmation re-writes the whole prefix | Expected; record gate wait in the trace; optional `ttl:"1h"` via policy for confirm-heavy apps | User takes > 5 min to confirm |
| Concurrent commands on a cold cache | Both pay a full write | Not worth engineering for personal apps; just don't misread telemetry | Rapid double-invocations |
| `maxIterations` too low | Agentic never runs a tool | Validate `>= 2` at pipeline build | `maxIterations = 1` from a settings slider |

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Offline-only falls through to cloud when ON_DEVICE is absent | Transcript leaves device against user choice | Policy resolved before routing; typed `OFFLINE_UNAVAILABLE` (Pitfall 13) |
| Key borrowed across providers on fallback | Key disclosed to wrong vendor | Key fetched by the exact provider id per call; `MISSING_KEY(provider)` |
| Base URL override public and unrestricted | Key sent over cleartext / to an attacker host | HTTPS-only; override constructor `internal`/`@VisibleForTesting`, loopback-only for tests |
| Consumer-supplied client with a logging interceptor | Key + transcript in logcat | Strip or refuse interceptors on engine clients; document |
| Error body copied into `Failed`/trace | Transcript/tool args in logs/UI | Status + `error.type` only |
| SB Auto Backup restores ciphertext without keys | Confusing "corrupt key" state (not a leak, but a support trap) | `KeyMissing` typed state + README backup-rules guidance |
| Tool `input` from the model trusted as-is | Malformed/destructive calls executed | Keep SB's whole-turn validation (non-blank unique ids, object input) before any dispatch |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| CT-style single opaque `Unavailable` | "Something went wrong" with no action | SB-granularity typed reasons: `BAD_KEY`, `MISSING_KEY`, `KEY_MISSING_AFTER_RESTORE`, `NETWORK`, `TIMEOUT`, `RATE_LIMITED`, `MALFORMED`, `REFUSAL`, `MAX_TOKENS`, `BUDGET`, `OFFLINE_UNAVAILABLE`, `UNKNOWN` |
| Held action rendered as success | User thinks a delete/edit happened | First-class pending/held in the outcome (Pitfall 9) |
| Timeout shown as "network error" | User checks wifi instead of switching model | Separate `TIMEOUT`; the trace shows which tier/model was slow |
| Partial commit hidden behind failure | Duplicate re-tries, orphan data | `Failed(committed = …)` so the app offers undo via `CommitSink` |
| Silent escalation cost | Surprise bill | Trace per-tier attempts + tokens (feeds YAT's "handled by" indicator) |

## "Looks Done But Isn't" Checklist

- [ ] **JitPack publish:** every module resolves from an empty Gradle cache under the *probed* coordinate, the inter-module POM dependency resolves, and the aggregator doesn't contain `:sample`.
- [ ] **A1 matrix:** the 5.x leg's runtime assertion shows `OkHttp.VERSION` 5.x while main classes were compiled against 4.12; mockwebserver matches in each leg.
- [ ] **Caching:** byte-identical tools+system across iterations (unit test), **and** turn-1 creation > 0 / turn-2 read > 0 on the TESTER with a prefix ≥ the model minimum.
- [ ] **SingleShot on current models:** a test for the forced-tool 400 → auto+strict retry, and for "auto returned no tool call".
- [ ] **Transcript:** a fixture with `thinking` blocks round-trips byte-exact through the Anthropic mapper.
- [ ] **Usage normalization:** OpenAI `prompt_tokens` containing `cached_tokens` isn't double-counted.
- [ ] **Escalation invariant:** a tier that committed can't escalate (unit test).
- [ ] **Cancellation:** cancel mid-HTTP and mid-tool → no further dispatch, no leaked `Response` (test).
- [ ] **Keystore:** legacy-format compat test on device; decrypt with a missing key returns `KeyMissing`, not "corrupt".
- [ ] **offlineOnly:** zero HTTP calls when ON_DEVICE is absent.
- [ ] **toString redaction:** reflective test over public types.
- [ ] **API:** `explicitApi()` on; dumps committed; every reason enum has a catch-all; README shows `else`.
- [ ] **detekt:** 0 baseline IDs; `@Suppress` count gated; decision on type-resolved detekt recorded.
- [ ] **README:** an agent can wire `:core`+`:providers`+`:keystore` from it alone, using the correct coordinates.

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Wrong coordinate documented at the tag | LOW | Docs fix + orchestrator broadcast (stt-engine did exactly this); no new tag needed |
| JitPack build failed for a tag | MEDIUM | Fix, cut `v1.0.1`, message the orchestrator a superseding ledger row; never move the tag |
| Stale forced-tool list shipped | LOW | Reactive fallback already covers it if built; else patch tag with the updated table |
| Transcript model lost thinking blocks | HIGH | Redesign 6a to carry raw payloads; ripples into 6b and the mappers. Avoid by deciding in 6a |
| Sealed/enum grew after the tag | MEDIUM | Consumers add `else`/branches on repin; coordinate via the orchestrator; strictly speaking a contract-visible break |
| Keys stranded in a consumer | MEDIUM (per user) | Users re-enter keys. Hot-fix the `KeySlot` table in the consumer; the engine is fine if the table is app-supplied |
| Duplicate mutation from escalation | HIGH (data) | Undo via `CommitSink`; patch the pipeline invariant; add a regression test |

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| 1 JitPack coordinates / aggregator | Step 1 (probe tag); step 7 | HTTP probe of every POM + clean-cache resolve from a scratch project |
| 2 `:sample` breaks/leaks publish | Step 1; step 7 | JitPack-simulation CI job; aggregator POM inspection |
| 3 JVM-module publication + JDK | Step 1 | JitPack log green on openjdk17; `-sources.jar` present |
| 4 OkHttp matrix validity | Step 3a (skeleton in 1) | Runtime-version assertion per leg; compileClasspath pin check |
| 5 Forced tool_choice 400 | Step 3a, step 5 | Unit test for the 400 → retry path; no-call → NoMatch test |
| 6 Cache read = 0 | Step 3a, 6a, 6b, 7 | Byte-stability tests; TESTER Gate-1 creation>0 then read>0 |
| 7 Neutral transcript fidelity | Step 6a | Thinking-block round-trip fixture; OpenAI tool-message ordering test |
| 8 OpenAI/OpenRouter mismatches | Step 3a/3b/6a | Recorded-body contract tests; usage normalization tests |
| 9 Escalation duplicates / held-as-success | Step 2, 6b | Pipeline invariant tests; held → pending outcome test |
| 10 Cancellation/timeout/leaks | Step 2, 3a | Cancel tests; `TIMEOUT` vs `NETWORK` tests; Response closed on cancel |
| 11 Keystore stranding | Step 4, 7 | Legacy-format compat test on device; `KeyMissing` test |
| 12 API growth | Step 1 (tooling), step 2 (design), step 7 (gate) | `apiCheck` green; reason enums have a catch-all; no public data classes on growth paths |
| 13 Offline-only / key substitution | Step 2, 3a | Zero-HTTP test; key-seam spy test |
| 14 Secret leakage | Step 2, 3a/3b, 7 | Reflective `toString` test; no interceptor on engine clients |
| 15 detekt false-clean | Step 1 | Baseline 0 IDs; `@Suppress` gate |

## Open Questions (flag to orchestrator / later phases)

- **Contract coordinate.** `com.github.Ygaray:voice-action-engine` is JitPack's aggregator on a multi-module repo. Propose an erratum listing per-module coordinates once the step-1 probe confirms them.
- **A11 factual premise.** A11 says `:stt` is an AAR "built against 4.12". stt-engine's catalog pins OkHttp **5.2.1**, and CT currently excludes `:stt`'s okhttp (so 5.x-compiled code has been running on 4.12 in CT). This doesn't change VAE's A1 obligation, but backup-engine is the only true "4.12-compiled on 5.x" precedent. Worth telling the orchestrator.
- **Inter-module POM groupId on JitPack.** Does `:providers`' POM reference `:core` under the coerced groupId? Only a live probe answers this (step 1).
- **Type-resolved detekt on Kotlin 2.3.20 with detekt 1.23.8.** Works or not? Decide in step 1. detekt 2.x may be needed for `detektMain`.
- **OpenAI `finish_reason` under forced `tool_choice`** and **OpenRouter `reasoning_details` echo requirements.** Verify against live responses in step 3b (MEDIUM/LOW).
- **JSON type in public seams** (`JsonObject` vs an engine-neutral map/string). A step-2 API decision with long-term coupling cost.

## Sources

- **Port sources (HIGH, read directly):** SB `core/agent/AnthropicAgentLoop.kt` (verbatim content echo, system-block breakpoint, final-iteration guard, `turnTokenTotal`, `Call.await`), `AgentLoopResult.kt`, `MutationGate.kt`, `KeystoreCrypto(.kt|Seam.kt)`, `AnthropicApiKeyRepository.kt`, `core/di/AgentModule.kt` (60s timeouts), `AndroidManifest.xml` + `res/xml/backup_rules.xml` (allowBackup true, template rules). CT `ai/AnthropicKnownTool400Ids.kt`, `ai/BaseAiProvider.kt`, `ai/OpenAiLogFoodRequestBuilder.kt`, `ai/OpenRouterProvider.kt`, `data/security/KeystoreCrypto.kt`, `data/repository/ApiKeyRepository.kt` (explicit `KeySchema` table), `di/ScanModule.kt`.
- **Sibling hubs (HIGH):** backup-engine `jitpack.yml`, `backup/build.gradle.kts`, `gradle/libs.versions.toml` (okhttp 4.12.0, AGP 8.13); stt-engine `android/jitpack.yml`, commit `b8c9fdb` (JitPack groupId coercion + aggregator POM), `android/gradle/libs.versions.toml` (okhttp/mockwebserver 5.2.1), and the `org.json` JVM-stub finding; YAT `build.gradle.kts` (Metalava because BCV/abiValidation fail on AGP-9; detekt 1.23.8; zero-ID baseline), commit `13e5998` (Metalava `_Factory` leak); control-plane notes on JitPack lazy builds/immutable caching.
- **Anthropic API (HIGH; bundled claude-api reference, cached 2026-09-25, corroborated by CT's 2026-09-27 live verification):** prompt-caching minimums per model, TTL/pricing, invalidation hierarchy, 20-block lookback, usage-field semantics, forced `tool_choice` rejection on Opus 5.5/Sonnet 5.5/Fable 5.1/Mythos 5.1, preserved-thinking rules.
- **OkHttp 5 (MEDIUM, web, consistent with A11's live CT test):** [OkHttp CHANGELOG](https://github.com/square/okhttp/blob/master/CHANGELOG.md) — 5.x intends zero backwards-incompatible changes vs 4.12; published as KMP with Gradle-metadata variant selection; [changelog_4x](https://github.com/square/okhttp/blob/master/docs/changelogs/changelog_4x.md).
- **OpenRouter (MEDIUM, official docs):** [Prompt caching guide](https://openrouter.ai/docs/guides/best-practices/prompt-caching) — `prompt_tokens_details.cached_tokens` / `cache_write_tokens` / `cache_discount`, sticky routing + `session_id` (10-min expiry), explicit `cache_control` needed for Anthropic; OpenAI automatic caching ≥ 1024 tokens.
- **OpenAI (MEDIUM, cross-checked across multiple reports):** [Chat Completions reference](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create); reasoning models reject `max_tokens` in favor of `max_completion_tokens` ([genkit #6320](https://github.com/genkit-ai/genkit/issues/6320), [simonw/llm #724](https://github.com/simonw/llm/issues/724)).

---
*Pitfalls research for: provider-neutral LLM voice-command engine (Kotlin/Android, JitPack, multi-module)*
*Researched: 2026-09-29*
