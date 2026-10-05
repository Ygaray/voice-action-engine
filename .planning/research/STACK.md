# Technology Stack: v1.1 additions

**Project:** voice-action-engine
**Milestone:** v1.1 (Grammar, Plan, Router, Undo, Spike, Adapter), tag `v1.1.0`
**Researched:** 2026-10-05
**Scope:** only what is NEW for Phases 12-20. The v1.0 stack (Kotlin 2.3.20, AGP 9.2.1, Gradle 9.4.1, JDK 17 build / JVM 11 bytecode, OkHttp 4.12 floor, kotlinx 1.11.0, detekt 1.23.8 syntax-only, Metalava 0.5.1, DataStore 1.2.1) is unchanged and is not re-researched. See the project CLAUDE.md "Technology Stack".
**Overall confidence:** HIGH for Phases 12, 17, 18 and 14 (primary vendor docs or local source read). MEDIUM for Phase 13 (the artifacts are verified; the on-device numbers on an S22 Ultra are unmeasured by definition, which is the point of the spike).

Confidence tags below follow the seam: vendor artifacts and vendor `.md` docs fetched directly (Maven metadata, AAR/POM inspection, Anthropic and OpenAI docs, HF API) are primary. Anything that came only through a search-result summary is tagged MEDIUM or LOW.

## TL;DR: the prescriptive picks

| Phase | Decision |
|---|---|
| 12 | No new libraries. Add the `claude-sonnet-5` row at **1,024** tokens (not 512: that is `claude-sonnet-5-5`). Fix W04 by deleting the blanket `GPT_6_FAMILY -> "none"` rule: only `gpt-6-sol`, `gpt-6-luna`, `gpt-5.4+` (non-pro, non-codex) accept `none`. |
| 13 | **LiteRT-LM** (`com.google.ai.edge.litertlm:litertlm-android:0.17.1`), not MediaPipe (maintenance-only). Primary candidate model **Gemma 4 E2B** (`.litertlm`, Apache-2.0, ungated). New Android library module `:ondevice`. The library ships **no weights**: the app supplies a file path. |
| 14 | **Zero new dependencies.** Hand-roll the EN/ES number-word parser and the matcher in `:core`. |
| 17 | `:undo` = Kotlin/JVM module, stdlib only (not even kotlinx.coroutines). Copy `:core`'s build conventions. |
| 18 | `:voice-adapter` **must be an Android library (AAR), minSdk 33**, because `:stt` is an AAR with minSdk 33. Depend on `:stt` with `compileOnly`, and add a content-filtered JitPack repo to `settings.gradle.kts`. |
| Plumbing | Four hard-coded gates in `gradle/invariants.gradle.kts` and root `build.gradle.kts` break the moment a 4th/5th/6th published module appears. See "Build plumbing the new modules trip". |

## Recommended Stack

### Core Technologies (new)

| Technology | Version | Purpose | Why | Confidence |
|---|---|---|---|---|
| `com.google.ai.edge.litertlm:litertlm-android` | **0.17.1** (Google Maven, lastUpdated 2026-09-16) | On-device LLM runtime for `:ondevice` (Phase 13) | Google's successor to MediaPipe LLM Inference, which is now maintenance-only. Kotlin API is marked Stable. Apache-2.0. Native `Engine`/`Conversation`, `cancelProcess()`, function-calling and a JSON/regex `ResponseFormat`. AAR minSdk 24. | HIGH |
| Gemma 4 E2B `gemma-4-E2B-it.litertlm` (HF `litert-community/gemma-4-E2B-it-litert-lm`) | model file, 2,588 MB (GPU-specific `gemma-4-E2B-it-gpu.litertlm` 2,008 MB) | Spike model, "~2B class" | "Effective 2B", **Apache-2.0**, ungated on HF, native LiteRT-LM format, 32k context. The Gemma 3 / 3n files are under the Gemma Terms of Use and are gated. See "Phase 13". | HIGH (license, size), LOW (S22 behavior) |
| `:ondevice` module | new, `com.android.library`, minSdk 35 | `AiProvider` + `OnDeviceCapability` implementation, `@Experimental` | Keeps `:core`/`:providers` free of ML deps (spike criterion 4). Publishes only if the verdict is green. | HIGH (design) |
| `:undo` module | new, `kotlin.jvm`, JVM 11, explicitApi | Run-level journal (Phase 17) | Mirrors `:core` conventions. No dependencies at all. | HIGH |
| `:voice-adapter` module | new, `com.android.library`, **minSdk 33** | `:stt` `FinalSegment` to `CommandInput` (Phase 18) | `:stt` is an AAR, so a plain jar module cannot depend on it. | HIGH |
| `com.github.Ygaray.voice-engine-android:voice-engine-android:v0.7.0` (`:stt`) | **v0.7.0** (mirror tag `0108c6d9`, ledger row 2026-10-01) | Compile-time type source for `:voice-adapter` | It is the ledgered `:stt` release. POM resolves on JitPack (HTTP 200, checked 2026-10-05). | HIGH |

### Supporting Libraries (new, test-scope or conditional)

| Library | Version | Purpose | When to Use |
|---|---|---|---|
| `kotlinx-coroutines-core` | 1.11.0 (existing pin) | Bridge `Conversation.sendMessageAsync` callbacks to `suspend` in `:ondevice` (`suspendCancellableCoroutine`, call `cancelProcess()` on cancel) | `:ondevice` main (already arrives via `api(project(":core"))`). **Not** in `:undo`. |
| `kotlinx-coroutines-test` | 1.11.0 (existing pin) | `runTest` for `:undo` and `:voice-adapter` tests | Test scope only. |
| `androidx.test` runner / ext-junit | 1.7.0 / 1.3.0 (existing pins) | Device-side smoke for `:ondevice` | Only if the spike turns green and the module ships. The spike itself can live in `:sample`. |
| `org.jetbrains.kotlin:kotlin-reflect` | 2.4.0 (transitive of litertlm) | Pulled in by `litertlm-android` | Do not add it yourself. See compatibility risk below. |

Test double for stt: do **not** depend on the real recognizer. `FinalSegment(text, segmentId, language)` is a plain public class with a public constructor, so `:voice-adapter` JVM tests construct it directly (the unit tests need `:stt` on the test classpath, so use `testImplementation` of the same coordinate).

### Development Tools (changes)

| Tool | Purpose | Notes |
|---|---|---|
| Metalava 0.5.1 on `:undo` (JVM) and `:voice-adapter`/`:ondevice` (Android) | `api.txt` per module | Same plugin, same setup as `:core` / `:keystore`. **Gotcha:** see `verifyApiDumpPresent` below. |
| detekt 1.23.8 syntax-only, zero baseline | Lint on the three new modules | Apply the root `subprojects { plugins.withId(detekt) }` block automatically. No config change. |
| `config/negative-controls/*` | Keep green | New modules inherit the scanner; no new control needed unless a new gate is added. |

## Phase 12: Wave-1 seams and W04 (no new libraries)

### Anthropic: minimum cacheable prefix (HIGH, fetched 2026-10-05 from `platform.claude.com/docs/en/build-with-claude/prompt-caching.md`)

| Model id | Min cacheable prefix | Forced tool choice | Notes |
|---|---|---|---|
| `claude-sonnet-5` (legacy, still available) | **1,024 tokens** | allowed | The id Phase 12 asks for. Not in Anthropic's "forced tool use not supported" list. |
| `claude-sonnet-5-5` (current) | 512 | **rejected** (400) | Already in `AnthropicModels.kt` with 512 and `supportsForcedToolChoice = false`: correct. |
| `claude-opus-5-5`, `claude-fable-5-1`, `claude-mythos-5-1` | 512 | rejected | Already correct. |
| `claude-haiku-4-5` | 4,096 | allowed | Already correct. |
| `claude-opus-5`, `claude-fable-5`, `claude-mythos-5` (legacy) | 512 | allowed | Not in the table yet. Optional additive rows. |
| `claude-opus-4-8`, `claude-sonnet-4-6`, `claude-sonnet-4-5` | 1,024 | allowed | Not in the table. Optional. |

So the new row is `ModelCapabilities { caching = EXPLICIT_BREAKPOINTS; minCacheablePrefixTokens = 1_024 }` (forced choice left at the default, allowed). Add a second named constant next to `STANDARD_MIN_CACHEABLE_PREFIX_TOKENS = 512`. Do not reuse the 512 constant: it would make the cache diagnostic say "sure it should have cached" for a 600-token prefix that legitimately cannot cache on Sonnet 5. The constant `STANDARD_MIN_...` name is also misleading now; name the new one `SONNET_5_MIN_CACHEABLE_PREFIX_TOKENS`.

### Anthropic: `ReasoningMode` wire mapping (HIGH, same docs set, relevant to SEAM-02)

- Default `ReasoningMode.OFF` must send **no `thinking` field**, to keep goldens byte-identical to v1.0.
- Do not translate `OFF` to `thinking: {"type":"disabled"}`. Claude Sonnet 5.5 rejects `disabled` with a 400 ("send `between_tools` instead"), and it has thinking on by default when the field is absent. So on 5.5 the true lowest setting is `{"type":"between_tools"}`, accepted only at effort `high` or below, and effort cannot change mid-conversation under it. An explicit non-default `ReasoningMode` therefore needs a per-model mapping, and `between_tools` is a 400 on every other model.
- The existing encoder comment ("a multi-turn loop uses `ToolChoice.Auto`") stays correct; thinking blocks must round-trip unchanged (preserved-thinking prefix check applies to Fable 5.1 / Opus 5.5 / Sonnet 5.5).

### OpenAI: which models reject `reasoning_effort: "none"` (HIGH, `developers.openai.com/api/docs/models/*.md` and `guides/reasoning.md`, `guides/latest-model.md`, fetched 2026-10-05)

| Model id | `none` accepted? | Chat Completions with tools? | What the engine should send (direct OpenAI) |
|---|---|---|---|
| `gpt-6-astra` | **No** (HTTP 400) | **No** (Responses only). Docs list `low, medium, high, xhigh, max`; the live 400 text on 2026-10-05 lists `low, medium, high, xhigh`. | Omit effort. Refuse before the call by default (`toolsOnChat` false); under the `supportsTools` override the API answers with the `/v1/responses` text, which the existing marker already maps to `ModelUnsupported`. |
| `gpt-6.1-sol` | **No** (`none` and `minimal` both rejected) | **No** (Responses only) | Same as Astra. |
| `gpt-6-sol`, `gpt-6-luna` | Yes | Yes, **only with `reasoning_effort: "none"`** | `"none"` (current rule is right for these two). |
| `gpt-5.6-sol/-luna/-terra` | Yes | Yes | `"none"` via the existing `gpt-5.N, N>=4` rule. |
| `gpt-5.4`, `-mini`, `-nano`, `gpt-5.5` | Yes | Yes | `"none"` (current). |
| `gpt-5.4-pro`, `gpt-5.5-pro`, `gpt-5.2-pro` | **No** (`medium`+ only) | Responses only | The current `isLaterGpt5` rule would send `"none"` to 5.4-pro and 5.5-pro. Exclude any id ending `-pro`. |
| `gpt-5.2-codex`, `gpt-5.3-codex` | **No** (`low`+) | Responses | Currently safe (minor < 4). Exclude `-codex` so a future `gpt-5.4-codex` stays safe. |
| `gpt-5`, `-mini`, `-nano` | **No** (`minimal, low, medium, high`) | Yes | Current rule (omit) is right. `gpt-5-pro` is `high` only. |
| `gpt-5.1`, `gpt-5.2` | Yes (default is `none`) | Yes | Current (omit, default `none`) is right. |
| o-series | not verified (model pages carry no effort line) | n/a | Leave as is. |

**Fix shape (internal, no API change):** in `OpenAiModelRules.wireRules`, replace `GPT_6_FAMILY -> "none"` with a rule that (a) gives `RESPONSES_ONLY` ids on direct OpenAI no effort (Probe A in `W04-host-wording-check.txt` showed that wording matches the marker), (b) keeps `"none"` for other `gpt-6*` ids, and (c) excludes `-pro` and `-codex` suffixes from the `gpt-5.N>=4` rule. Add the classifier backstop `param=reasoning_effort` + `code=unsupported_value` to `ModelUnsupported` as the second line of defense. The encoder golden must cover `gpt-6-astra`, `gpt-6.1-sol`, `gpt-5.5-pro` and the still-`none` positives `gpt-6-sol`, `gpt-6-luna`, `gpt-5.5`.

Also worth knowing for DOC-01: OpenAI's minimum cacheable prefix stays 1,024 tokens (unchanged in `OpenAiModelRules`); a single-tool SingleShot prefix will not cache on OpenAI or Haiku (4,096), as the roadmap already says.

## Phase 13: On-device model spike

### Runtime choice: LiteRT-LM over MediaPipe (HIGH)

| Criterion | LiteRT-LM (`litertlm-android` 0.17.1) | MediaPipe LLM Inference (`tasks-genai` 0.10.35) |
|---|---|---|
| Status | Active: 0.15 (Aug 4), 0.16 (Aug 11), 0.17.0 (Sep 9), 0.17.1 (Sep 16). Kotlin API "Stable". | **Maintenance-only**; Google's page says migrate to LiteRT-LM. Latest on Maven Central 0.10.35 (2026-04-27). |
| Where | Google Maven | Maven Central |
| Model format | `.litertlm` (+ HF `litert-community`) | `.task` (and `.litertlm` only for sample-app imports) |
| Gemma 4 | Yes (E2B, E4B, 12B, 26B-A4B, 31B published as `.litertlm`) | Not listed (Gemma 3 1B, Gemma 2, Gemma-3n) |
| Structured output | `ResponseFormat.json(schema \| Map)` / `.regex(...)` in `sendMessage*`, `ConversationConfig.enableResponseFormat`; native `@Tool` / `OpenApiTool` and `Capabilities.supportsFunctionCalling()` (all read from the 0.17.1 bytecode with `javap`; **the Android guide does not document `ResponseFormat`**, so the spike must prove it end to end) | None (prompt-only JSON) |
| minSdk | 24 (AAR manifest) | high-end devices ("Pixel 8 and Samsung S23 or later" per Google) |
| Native libs | `jni/arm64-v8a` + `jni/x86_64` only (no armeabi-v7a) | arm64 + others; AAR is 42 MB |
| Transitive deps | gson 2.14.0, kotlin-reflect 2.4.0, kotlinx-coroutines-android 1.11.0 | androidx.annotation, guava 27.0.1-android, protobuf-javalite 4.26.1 |
| License | Apache-2.0 | Apache-2.0 |

Pick LiteRT-LM. The "MediaPipe / LiteRT" wording in SPIKE-01 resolves to LiteRT-LM; there is no reason to start new code on a maintenance-only API.

Other runtimes considered and not recommended for this spike (MEDIUM/LOW, from general knowledge, not re-fetched): llama.cpp via JNI (new native build chain on JitPack, no Google-maintained Kotlin API), MLC-LLM (heavyweight toolchain), ExecuTorch (Gemma/Qwen support less turnkey), ML Kit GenAI / AICore (Gemini Nano: S22 Ultra has no AICore, explicit L10/LATER-06 non-goal).

### Integration with the existing seams

- `:ondevice` implements `AiProvider` (id `ProviderId.ON_DEVICE`, `requiresCredential = false`) and `OnDeviceCapability` from `:core`. It depends on `api(project(":core"))` **only**, never `:providers` (that would drag OkHttp in).
- `litertlm-android` goes in as `implementation`, never `api`: no LiteRT type may reach the public API (so `kotlin-reflect 2.4.0`, gson and the 22 MB `.so` stay off consumers' compile classpath).
- `OnDeviceCapability.availability()` maps: model file present and `Engine.initialize()` succeeded -> `Available`; file absent -> `Unavailable("model_missing")` (or `Downloadable` if the app wires a downloader); non-arm64 -> `Unavailable("abi_unsupported")`; low-RAM guard -> `Unavailable("insufficient_memory")`. Codes must match `[a-z0-9_]+` (`isStableCode`).
- `Engine.initialize()` blocks (~10 s per Google's guide): run on a dedicated single-thread dispatcher, keep one `Engine` per process, create a fresh `Conversation` per call, and call `conversation.cancelProcess()` from `invokeOnCancellation`. Never log prompt, transcript or output (CLN rules and the banned-construct scanner apply to this module too).
- SingleShot shape ("forced tool call"): two routes to try in the spike, measured side by side. **Route A**: `ResponseFormat.json(schema)` where the schema is `{tool, arguments}`; parse with kotlinx.serialization. **Route B**: LiteRT-LM tool declarations with `automaticToolCalling = false` and read `Message.toolCalls`. Report schema-valid rate and latency for each; pick the better. Route A is the more likely winner because it is constrained decoding, but it is undocumented, so verify it first.
- `Conversation.tokenCount` and `benchmarkInfo` give prefill/decode numbers for SPIKE-01 without extra instrumentation.

### Model candidates (HF API, 2026-10-05; sizes in MB, all single `.litertlm` files)

| Model | File | Size | License | Gated | Use in the spike |
|---|---|---|---|---|---|
| **Gemma 4 E2B** | `gemma-4-E2B-it.litertlm` | 2,588 | **Apache-2.0** | no | Primary. Google's card (S26 Ultra): GPU 3,808 tok/s prefill, 52.1 tok/s decode, 0.3 s TTFT, peak RAM 676 MB; CPU 557 / 46.9 / 1.8 s, peak 1,733 MB. These are a 2026 flagship, not an S22. |
| Gemma 4 E2B GPU variant | `gemma-4-E2B-it-gpu.litertlm` | 2,008 | Apache-2.0 | no | Smaller download if the GPU path wins on the S22. |
| Gemma 3 1B IT int4 | `gemma3-1b-it-int4.litertlm` (also a 584 MB `Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm`) | 584 | Gemma Terms of Use | **auto-gated** (HF acceptance) | The light fallback if E2B is too heavy for the S22's RAM. Carries Gemma ToU obligations (below). |
| Gemma 3n E2B | `gemma-3n-E2B-it-int4.litertlm` (`google/gemma-3n-E2B-it-litert-lm`) | 3,656 | Gemma Terms of Use | **manually gated** | Not recommended: bigger, gated, older license. |
| Qwen3.5-2B | `Qwen3.5-2B_int8.litertlm` | 2,117 | Apache-2.0 | no | Cross-check for "is Gemma 4 specifically good at JSON". |
| Qwen2.5-1.5B Instruct | `..._q8_ekv4096.litertlm` | 1,598 | Apache-2.0 | no | Smaller non-Gemma control. |
| FunctionGemma 270M mobile-actions | `mobile_actions_q8_ekv1024.litertlm` | 289 | Gemma ToU | auto-gated | A function-calling-tuned tiny model. Worth one trial row, since SingleShot is exactly a function-call task. |

The Gemma-3-class "Gemma-2B" in SPIKE-01 no longer matches the best option: Gemma 4 E2B is the "~2B on-device" model that is both newest and freely redistributable. Say so in the verdict message.

### Gemma license terms for a bundled model (MEDIUM-HIGH)

- **Gemma 4: Apache-2.0** (HF license tag on `litert-community/gemma-4-*-litert-lm`; Google's `ai.google.dev/gemma/terms` states Gemma 4 "uses a separate Apache 2.0 license"; press coverage agrees). Redistribution needs the usual Apache-2.0 notice: ship a `LICENSE`/`NOTICE` with the app. No use-restriction flow-down.
- **Gemma 1 to 3n: Gemma Terms of Use.** To redistribute (a bundled copy counts), you must: pass the Section 3.2 use restrictions on as binding terms, give recipients a copy of the Terms, mark modified files, and include a notice file stating "Gemma is provided under and subject to the Gemma Terms of Use found at ai.google.dev/gemma/terms". The Prohibited Use Policy applies. Google claims no rights in outputs. Converting or quantizing a Gemma 3 model is a "modification".
- LiteRT-LM itself (library) is Apache-2.0 and its AAR ships `LICENSE` and a 2.1 MB `THIRD_PARTY_NOTICE.txt`; consumers' license screens should carry it.
- The library never redistributes weights (below), so the legal burden falls on the consumer app that downloads or bundles them. Say so in the module README.

### Model delivery: do not bundle weights in the APK, and do not bundle them in the library (MEDIUM; the Play limits are from memory and flagged)

- LiteRT-LM takes `EngineConfig(modelPath = "...")`, a **filesystem path**. An APK asset must first be copied to `filesDir`, so a bundled model costs 2x storage plus a multi-GB first-run copy.
- 2.0 to 2.6 GB is far beyond the usual APK budgets (ASSUMED, not verified here: Google Play base-APK limit 200 MB; install-time asset packs have much lower per-pack caps than 2.6 GB). A personal sideloaded APK can in principle hold it, but zip/signing tooling gets fragile near 2 GiB.
- **Recommendation:** `:ondevice` exposes a `ModelFileSource`-style seam (a `fun interface` returning a `java.io.File` or `null`) and no weights. The spike uses `adb push` to the app's external files dir on the TESTER. For production the app owns delivery: download-on-demand into `filesDir` with SHA-256 check (HF `resolve/main/<file>` URL), surfaced as `OnDeviceAvailability.Downloadable` / `Downloading`. This also keeps the Gemma 3 gating out of the library.
- **APK-size cost a consumer pays (SPIKE-01 asks for it):** measured from the 0.17.1 AAR: `liblitertlm_jni.so` arm64-v8a is 21.8 MB raw, **9.5 MB deflated** (x86_64: 26.0 MB raw / 10.5 MB deflated); AAR total 20.5 MB with both ABIs; Kotlin classes 0.16 MB. With `ndk { abiFilters += "arm64-v8a" }` the runtime adds roughly **9.5 MB to the APK** (the spike should confirm with a real build). The weights are an on-device download, not APK size.

### S22 Ultra caveats the spike must measure, not assume

- TESTER is `SM-S908U` (Snapdragon 8 Gen 1, Android 15). All Google benchmark numbers above are for a Galaxy S26 Ultra. The prebuilt NPU-tuned Gemma files target newer chips (`sm8550/8650/8750/8850`, Tensor G5/G6), not `sm8450`, so expect GPU (OpenCL) or CPU.
- GPU backend needs `<uses-native-library android:name="libvndksupport.so" android:required="false"/>` and `libOpenCL.so` (`required=false`) in the app manifest. Treat a GPU init failure as "fall back to CPU", and record both.
- Samsung Freecess can freeze coroutines (from the device notes): exclude the test app from battery optimization during the spike.
- Peak RAM: CPU path was 1.7 GB on the S26; measure with `dumpsys meminfo` on the S22 while generating.
- Agentic on-device stays out of scope (SingleShot/Plan shapes only).

### Compatibility risk to test first (HIGH priority, plan 1 of Phase 13)

`litertlm-android` 0.17.1 is built with **Kotlin 2.4.0 metadata (`mv=[2,4,0]`) and Java 21 bytecode (class major 65)**, and pulls `kotlin-reflect:2.4.0`. The repo compiles at Kotlin 2.3.20. A compiler reads metadata at most one minor ahead, so 2.3.20 reading 2.4.0 is at the edge of the supported window. Prove `:ondevice` compiles and `dexes` under AGP 9.2.1 in the first plan. If it does not: pin `0.16.1` (the previous stable) for the spike, or bump Kotlin in lockstep with SB/CT/YAT (not unilaterally). The `verifyBytecodeLevel` gate (class major 55) checks only the module's own classes, so Java-21 classes in the dependency do not trip it.

## Phase 17: `:undo` (pure Kotlin/JVM, zero dependencies)

Copy `:core/build.gradle.kts` conventions exactly; they are the repo's proven recipe (HIGH, read locally):

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
    alias(libs.plugins.detekt)
    alias(libs.plugins.metalava)
}
java { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11; withSourcesJar() }
kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        freeCompilerArgs.add("-Xjdk-release=11")
    }
}
dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
// publication: artifactId = "voice-action-engine-undo", groupId/version from rootProject.extra, from(components["java"])
apply(from = rootProject.file("gradle/invariants.gradle.kts"))
```

- **No `api(...)` or `implementation(...)` at all.** `suspend` in a public signature needs only the Kotlin stdlib (`kotlin.coroutines`), and stdlib's `kotlin.coroutines.cancellation.CancellationException` carries cancellation. Do not add `kotlinx-coroutines-core`: it is not needed for an adapter-driven, sequential restore and would turn "depends on nothing" into "depends on a Maven artifact". For in-memory journal mutation use `synchronized`/`java.util.concurrent` in short non-suspending critical sections; never hold a lock across a suspend call.
- Test fixtures: an in-memory `UndoEntityAdapter` fake is valuable for the Phase 17 JVM app test and for Phase 19. Put it in `src/test` unless another module needs it; if it must be shared, copy `:core`'s `java-test-fixtures` skip-from-publication recipe verbatim (including `verifyNoTestFixturesPublished`).
- No `kotlinx.serialization`: the journal's persistence format is the app's concern (the app persists snapshots through its own adapter). If a durable journal is later needed, add it additively.
- Integration (UNDO-04) is in `:core`, which must not depend on `:undo` (the criterion says `:undo` depends on nothing, and `:core` is hub-free). Wire the pipeline hook as a `:core` seam beside `CommitSink` that the app implements by delegating to `:undo`, or have the app compose them. Do not add `project(":undo")` to `:core`, and do not add `:undo` to the `:core` classpath allowlist.

## Phase 18: `:voice-adapter` (Android library)

### What `:stt` v0.7.0 publishes (HIGH, read from `~/Projects/Reusable/stt-engine/android` and the §11 ledger row)

- **It is an Android AAR.** `:stt` is `com.android.library`, `minSdk = 33`, `compileSdk = 36`, jvmTarget 17, built with AGP 8.13.0 and Kotlin 2.3.20, `explicitApi()`, API frozen with BCV (`stt/api/stt.api`).
- Mirror repo `github.com/Ygaray/voice-engine-android`, tags `v0.2.0` ... `v0.7.0` (`ls-remote` confirms `v0.7.0` = `0108c6d9601dc53261bf415545f46e97d81ee6c9`). Five published modules. The coordinate to use is the per-module one: **`com.github.Ygaray.voice-engine-android:voice-engine-android:v0.7.0`** (note the `.voice-engine-android` group suffix). The plain `com.github.Ygaray:voice-engine-android:v0.7.0` is JitPack's aggregator of all five modules; the ledger and INTEGRATION.md both say not to pin it.
- Runtime deps of `:stt` (in its POM): `kotlinx-coroutines-android`, `api(:corrections)`, and `implementation` **OkHttp 5.2.1** (its WebSocket transport).
- Final segment API (public, frozen): `io.github.ygaray.sttengine.FinalSegment(text: String, segmentId: Int, language: String? = null)`, plain class (not a `data class`), getters `getText()`, `getSegmentId()`, `getLanguage()`. Delivered on `SttEngine.finals: Flow<FinalSegment>` (lossless, **single consumer**) and as `SttEvent.Final(segment)` on `events`.
- Language semantics: `language` is bare `"en"` / `"es"` or `null`. It is non-null only when a bilingual `"auto"` session labelled the segment: the server backend (when the frame carried a label) or, since v0.7.0, the native on-device backend on **API 34+**. **`API.md` contradicts itself**: the "Model types" section still says language is "always `null` on the native backend", but the NBIL section and `SttEngineImpl.kt:751` show native finals are labelled. Treat the code as truth, and make the adapter not depend on either reading.
- `segmentId` is opaque, per-session, and can reset on a server-to-native handoff. The adapter must **drop it** and never use it for ordering or identity.

### Decisions for `:voice-adapter`

| Item | Decision | Why |
|---|---|---|
| Plugin | `com.android.library` (AGP 9 built-in Kotlin, no `kotlin.android`), `maven-publish`, detekt, metalava, `singleVariant("release") { withSourcesJar() }` | A jar module cannot consume an AAR. Copy `:keystore`'s build file shape. |
| `minSdk` | **33** | Must be >= `:stt`'s 33, or manifest merging fails the library build. SB/CT are 35, so no consumer is affected. |
| Namespace / package | `io.github.ygaray.voiceactionengine.voiceadapter` | Matches the other modules. |
| Dependencies | `api(project(":core"))` (it returns `CommandInput`); `compileOnly("com.github.Ygaray.voice-engine-android:voice-engine-android:v0.7.0")`; test: `testImplementation` the same coordinate | `compileOnly` keeps `:stt`'s OkHttp 5.2.1, corrections AAR and coroutines-android out of the engine's published POM, so the adapter never forces an `:stt` version or an OkHttp version on a consumer (A1 spirit). Any app that has a `FinalSegment` already has `:stt`. Use `api` instead only if you want the adapter to install `:stt` for the app (not recommended). MEDIUM confidence: this is a judgment call the phase should confirm. |
| Public API | `FinalSegment.toCommandInput(context: Any? = null, parentRunId: String? = null): CommandInput`, plus `SttEvent.Final.toCommandInput(...)` and a `List<FinalSegment>` joiner (join `text` with a single space; language = the common label if every segment agrees on one, else `null`) | `CommandInput(transcript, language, context, parentRunId)` is the v1.0 shape (Metalava `api.txt`). Keep the adapter additive. |
| Language rule | Lowercase with `Locale.ROOT`, take the primary subtag (`en-US` -> `en`), accept only `en`/`es`, else `null` | ROADMAP: "never a guess". Cheap defense against the doc contradiction and against a future `stt` label like `en-US`. |
| JitPack repo | Add a **content-filtered** repo in `settings.gradle.kts` | `FAIL_ON_PROJECT_REPOS` and only google + mavenCentral are declared today, so the `:stt` coordinate cannot resolve at all (locally or on JitPack's build). Use `exclusiveContent { forRepository { maven("https://jitpack.io") }; filter { includeGroup("com.github.Ygaray.voice-engine-android") } }`, so no other dependency can ever resolve from JitPack (dependency-confusion hygiene). |
| Tests | JVM `Test` only | `FinalSegment` is constructible on the JVM. No Robolectric. |
| Bytecode | `jvmTarget` 11 | `:stt` classes are JVM 17. Kotlin reads newer class files without error; there are no Java sources in the module. Confirm in the first plan (LOW risk). |

Version-skew note: FinalSegment is in `stt.api`, which is apiCheck-frozen (BCV), so getters will not change underneath the adapter in a minor release. Pin the adapter's compile coordinate to the ledgered `v0.7.0` and bump it only when §11 lists a newer `:stt` row.

## Phase 14: grammar and number words: add nothing

The answer to "what should Phase 14 NOT add" is: **every dependency.** `:core`'s classpath allowlist (`coreAllowed` in `invariants.gradle.kts`) permits only stdlib, annotations, coroutines-core and serialization-json, and the gate fails the build on anything else. Keep it that way.

| Do NOT add | Why |
|---|---|
| ICU4J (`com.ibm.icu:icu4j` 78.3, 15.2 MB jar) or `android.icu` `RuleBasedNumberFormat` | `:core` is pure JVM, so `android.icu` is unavailable. ICU4J would be 15 MB for two languages and would break the allowlist. It can *format* spelled-out numbers but spoken-form *parsing* (`veintiuno`, `ciento treinta y dos`, "twenty one") is fragile in it anyway. |
| Fuzzy/phonetic matching (Apache Commons Text, Lucene, Levenshtein/Jaro-Winkler libs, soundex) | The grammar tier must "never return a best-guess match". Fuzziness is the opposite of that contract and also unbounded risk of a wrong committed action. Misses go to the next tier. |
| NLP stacks (Stanford CoreNLP, OpenNLP, Duckling ports, Microsoft Recognizers-Text) | Hundreds of MB or unpublished on Central, domain-opinionated, and they would make `:core` non-hermetic. |
| Regex libraries (RE2/J, kotlin-grammar parsers such as better-parse, ANTLR) | `java.util.regex` plus a small tokenizer is enough for fixed phrase templates with typed slots. A parser-generator adds a runtime and a Kotlin-version coupling for a grammar a consumer writes in a DSL, not in BNF. |
| `kotlin-reflect` | Not needed, bloats, and the `:core` classpath allowlist would reject it. |
| `java.text.Normalizer` is fine (JDK), but do not use `String.lowercase()` without `Locale.ROOT` | Turkish-locale `i` bugs. Use `lowercase(Locale.ROOT)` and NFD accent folding so `dieciséis`/`dieciseis` and `veintiún`/`veintiuno` match. |

Hand-roll in `:core` (data-driven, one table per language, pure functions, fully JVM-tested): EN cardinals 0 to 999,999 plus "a/an/one" and "half" optionals as the pack needs; ES cardinals with the irregular forms (`dieciséis` ... `diecinueve`, `veintiuno/veintiún/veintiuna`, `veintidós` ... `veintinueve`, tens with `y` as in `treinta y uno`, `cien`/`ciento`, `quinientos`/`setecientos`/`novecientos`, `mil`, and the gender/apocope variants). Digits-in-transcript (`"2 cups"`) must work without the number-word path. Per the roadmap, the open item is the matching strictness model, not a library.

## Build plumbing the new modules trip (HIGH; read from the repo)

These are not library choices but they decide whether the new modules can land at all. Phase 17 should own them (the roadmap already says Phase 17 lands the "add a published module" plumbing first).

| # | Where | Problem | Fix |
|---|---|---|---|
| 1 | `gradle/invariants.gradle.kts`, `allowedEdges = mapOf(":core"..., ":providers"..., ":keystore"...)` | `allowedEdges.getValue(modulePath)` throws `NoSuchElementException` for any other module. | Add `":undo" to emptySet()`, `":voice-adapter" to setOf(":core")`, `":ondevice" to setOf(":core")`. |
| 2 | Root `build.gradle.kts`, `verifyApiDumpPresent` | Since `v1.0.0` and `v1.0.1` tags exist, `check` **fails for any module that has no committed `api.txt`**. A brand-new module cannot go green until it has one, but ROADMAP Phase 20 plans to create the undo/voice-adapter `api.txt` at the cut. | Commit a seed `api.txt` at module scaffold time (run `apiDump`) and **regenerate it freely until the v1.1.0 cut** (it is not a released baseline until then; note `metalavaCheckCompatibility` would otherwise block removals inside the milestone). Or add an explicit `unreleasedModules` set the gate skips, removed at Phase 20. Decide once, in Phase 17. |
| 3 | `invariants.gradle.kts`, `verifyCoreDeps` / `coreAllowed` | Classpath allowlist exists only for `:core`. | Generalize to a per-module map: `:core` (unchanged), `:undo` = stdlib + annotations only, `:voice-adapter` = `:core` set plus the `:stt` artifact(s) on the compile classpath, `:ondevice` = `:core` set plus `litertlm-android`, gson, kotlin-reflect, coroutines-android. This turns the "`:undo` has no dependencies" claim into a mechanical check like the module-graph gate. |
| 4 | `jitpack.yml` install list; `scripts/jitpack-dry-run.sh` ("exactly the three engine artifacts") | Hard-coded three modules. | Append `:undo:...`, `:voice-adapter:...`, and `:ondevice:...` (if green) `publishReleasePublicationToMavenLocal` to the **single** `./gradlew` invocation; update the dry-run's expected-artifact count and `verify-docs-coverage.sh`/ECOSYSTEM coordinates. Never list `:sample`. |
| 5 | `settings.gradle.kts` | `include(":core", ":providers", ":keystore", ":sample")`; no JitPack repo. | Add the three includes and the content-filtered JitPack repo (Phase 18). |
| 6 | `verifyModuleGraph` `:sample` edges | `sampleRequiredEdges`/`sampleAllowedEdges` list only core/providers/keystore. | Phase 19 will want `:sample` to depend on `:undo`, `:voice-adapter` and `:ondevice`; extend `sampleAllowedEdges` in the same change. |
| 7 | `:sample` Gate-1 on TESTER | For the spike and for Phase 19, the model file lives on the device only. | Never reference the model path at build time; fail with a clear in-app message when it is missing (same discipline as the SB fixture, LE-7). |

## Installation

```kotlin
// gradle/libs.versions.toml additions (only :ondevice needs a new catalog entry)
[versions]
litertlm = "0.17.1"
[libraries]
litertlm-android = { group = "com.google.ai.edge.litertlm", name = "litertlm-android", version.ref = "litertlm" }
# stt is a compile-only type source for :voice-adapter; keep the tag in sync with the §11 ledger row
stt-engine = { group = "com.github.Ygaray.voice-engine-android", name = "voice-engine-android", version = "v0.7.0" }
```

```kotlin
// :ondevice/build.gradle.kts (only if the spike is green; mirror :keystore otherwise)
android { namespace = "io.github.ygaray.voiceactionengine.ondevice"; defaultConfig { minSdk = 35 }
          publishing { singleVariant("release") { withSourcesJar() } } }
dependencies {
    api(project(":core"))
    implementation(libs.litertlm.android)   // implementation: no LiteRT type in the public API
}

// :voice-adapter/build.gradle.kts
android { defaultConfig { minSdk = 33 } /* ... same shape as :keystore ... */ }
dependencies {
    api(project(":core"))
    compileOnly(libs.stt.engine)
    testImplementation(libs.stt.engine)
}
```

```bash
# Spike model delivery on the TESTER (never the personal phone; read context/devices/common.md first)
adb -s <TESTER-usb-serial> push gemma-4-E2B-it.litertlm /sdcard/Android/data/<pkg>/files/
```

## Alternatives Considered

| Category | Recommended | Alternative | Why Not |
|---|---|---|---|
| On-device runtime | LiteRT-LM 0.17.1 | MediaPipe `tasks-genai` 0.10.35 | Maintenance-only; no Gemma 4; `.task` format; no constrained decoding. |
| Spike model | Gemma 4 E2B (Apache-2.0) | Gemma 3n E2B / Gemma 3 1B | Gated, Gemma ToU flow-down, larger (3n) or weaker (1B). Keep Gemma 3 1B int4 (584 MB) as the light-RAM control only. |
| Weights delivery | App supplies a path (download-on-demand) | APK asset / bundled in the AAR | 2.0 to 2.6 GB; LiteRT-LM needs a real file path (copy doubles storage); Gemma 3 licensing would transfer to every consumer. |
| `:voice-adapter` module type | Android library, minSdk 33 | Pure JVM jar | Cannot depend on an AAR. |
| `:stt` dependency scope | `compileOnly` | `api` | `api` re-exports `:stt`'s OkHttp 5.2.1 + corrections into the engine's published POM and couples the version; viable if you want the adapter to install `:stt`. |
| `:undo` concurrency | stdlib + `synchronized` | `kotlinx-coroutines-core` `Mutex` | Breaks "depends on nothing" for a sequential restore that does not need it. |
| Number words | Hand-rolled tables in `:core` | ICU4J / NLP library | See Phase 14 table. |
| `:undo` -> `:core` wiring | App/pipeline seam in `:core` that delegates to `:undo` | `:core` depends on `:undo` | Would put an edge into the one module that must stay hub-free, and contradicts UNDO-01. |

## What NOT to Use

| Avoid | Why | Use Instead |
|---|---|---|
| `com.google.mediapipe:tasks-genai` for new code | Maintenance-only; Google says migrate. | LiteRT-LM |
| `litertlm-android` as an `api` dependency or any LiteRT type in public signatures | Exposes Kotlin 2.4 / Java 21 / kotlin-reflect / gson to every consumer's compile classpath, and freezes LiteRT types into Metalava `api.txt` forever. | `implementation`, with a private adapter layer |
| `litertlm-jvm` | Desktop JVM build; irrelevant for the S22 and for JVM unit tests (you cannot run a 2.6 GB model in CI anyway). | Fakes for the provider in `:ondevice` JVM tests |
| Gemma 3 / 3n as the default | Gemma ToU flow-down and HF gating. | Gemma 4 (Apache-2.0) unless the spike shows E2B is too heavy |
| The AICore / ML Kit GenAI / Gemini Nano route | S22 has no AICore; L10/LATER-06. | Out of scope |
| `thinking: {"type":"disabled"}` to implement `ReasoningMode.OFF` | Claude Sonnet 5.5 returns 400. | Send nothing for OFF; `between_tools` is the Sonnet 5.5 floor |
| A blanket `gpt-6*` -> `reasoning_effort: "none"` | Astra and 6.1 Sol reject it (this is W04). | Per-id rule above |
| `-pro` / `-codex` ids in the `gpt-5.N>=4` "none" rule | They reject `none`. | Explicit exclusion |
| Reading `FinalSegment.segmentId` as identity or order | Opaque, per-session, resets on server-to-native handoff. | Ignore it |
| The `com.github.Ygaray:voice-engine-android` aggregator coordinate | Pulls all five `:stt` modules. | The `voice-engine-android:voice-engine-android` per-module coordinate |
| A project-wide JitPack repo (no content filter) | Lets any dependency resolve from JitPack. | `exclusiveContent` filtered to `com.github.Ygaray.voice-engine-android` |
| Fuzzy matching, NLP libs, ICU4J in `:core` | See Phase 14. | Hand-rolled tables |
| `kotlinx-coroutines-android` in `:core`/`:undo` | Already banned; note `litertlm-android` brings it transitively into `:ondevice` only. | n/a |

## Stack Patterns by Variant

**If the spike verdict is green:**
- Add `:ondevice` (AAR, minSdk 35), `@Experimental` marker annotation (opt-in), register in the Phase 17 plumbing, ship `voice-action-engine-ondevice` in the v1.1.0 install list, and document that the app owns model delivery and the Gemma/Apache notices.

**If the verdict is red:**
- Ship nothing: no module, no catalog entry, no `litertlm` reference anywhere in the repo, `SPIKE-03` dispositioned N/A-deferred. Keep the spike code on a throwaway branch or under `:sample` only and delete it. `:core` and `:providers` are untouched either way (`no-on-device-implementation` scan stays green).

**If `:ondevice` does not compile at Kotlin 2.3.20 against litertlm 0.17.1:**
- Try `0.16.1`. If that also fails, run the spike from a scratch Android app on a newer Kotlin toolchain, record the numbers, and let the verdict stand without shipping the module in v1.1.0 (bumping Kotlin alone is a cross-repo change with SB/CT/YAT).

## Version Compatibility

| Package | Compatible With | Notes |
|---|---|---|
| `litertlm-android` 0.17.1 | Android minSdk 24, arm64-v8a / x86_64 only | Needs a real 64-bit device. 32-bit-only configs would crash at load. Metadata Kotlin 2.4.0, class major 65: test against our Kotlin 2.3.20 first. |
| `litertlm-android` 0.17.1 transitive | `kotlinx-coroutines-android` 1.11.0 (matches our pin), `gson` 2.14.0, `kotlin-reflect` 2.4.0 | kotlin-reflect 2.4.0 is newer than our Kotlin; runtime-only on consumers (it stays off their compile classpath with `implementation`). |
| `:stt` v0.7.0 AAR | minSdk 33, compileSdk 36, jvmTarget 17, AGP 8.13 build | Consumable from AGP 9.2.1 modules. Its native language auto is API 34+. |
| `:voice-adapter` | `:core` 1.1.0, `:stt` v0.7.0 | Version of `:stt` is a build-time type source only (`compileOnly`). |
| `:undo` | none | stdlib 2.3.20 only. |
| Gemma 4 E2B `.litertlm` | LiteRT-LM (any recent) | Needs a runtime that knows Gemma 4 (0.15+ lists it). |

## Open Questions (for phase-level research)

1. Does `ResponseFormat.json(schema)` constrain decoding for Gemma 4 E2B in practice, and does `enableResponseFormat` need to be set at `ConversationConfig` time? It is in the 0.17.1 bytecode but absent from the guide. First experiment of Phase 13 (LOW until run).
2. Real S22 Ultra numbers (GPU/OpenCL vs CPU, peak RAM, load time): unknown until measured.
3. Play Store limits on asset packs and APK size for a multi-GB model are stated from memory here; verify if a consumer ever wants to bundle.
4. `claude-sonnet-5`'s forced-tool-choice support is inferred from its absence in Anthropic's rejection list (four models named: Opus 5.5, Sonnet 5.5, Fable 5.1, Mythos 5.1). The live-key smoke in Phase 12 can confirm it cheaply.
5. Whether the `OpenAiModelRules` o-series rows need effort handling: OpenAI's model pages for `o1`..`o4-mini` carry no effort line; left unchanged.
6. `:voice-adapter` `compileOnly` vs `api` for `:stt`: decide in Phase 18 discuss.

## Sources

- Anthropic prompt caching, minimum cacheable prompt length list: `https://platform.claude.com/docs/en/build-with-claude/prompt-caching.md` (fetched 2026-10-05, HIGH).
- Anthropic forced tool use and Sonnet 5.5 thinking rules: `.../agents-and-tools/tool-use/define-tools.md`, `.../api/errors.md`, `.../build-with-claude/thinking.md`; model IDs: `.../about-claude/models/overview.md`, `.../models/sonnet-5/overview.md` (fetched 2026-10-05, HIGH).
- OpenAI reasoning effort per model: `https://developers.openai.com/api/docs/guides/reasoning.md`, `.../guides/latest-model.md`, `.../models/{gpt-6-astra,gpt-6.1-sol,gpt-6-sol,gpt-6-luna,gpt-5,gpt-5.1,gpt-5.2,gpt-5.4,gpt-5.4-pro,gpt-5.5,gpt-5.5-pro,gpt-5.6-*}.md` (fetched 2026-10-05, HIGH). Live evidence: `.planning/releases/v1.0-close/W04-host-wording-check.txt`.
- LiteRT-LM: Google Maven `maven-metadata.xml` (release 0.17.1, 2026-09-16), `litertlm-android-0.17.1.pom`/`.aar` (inspected locally: manifest minSdk 24, `jni/`, `javap` of `Engine`, `Conversation`, `ConversationConfig`, `ResponseFormat`, `Capabilities`), `https://developers.google.com/edge/litert-lm/android`, `https://github.com/google-ai-edge/LiteRT-LM` (+ releases page) (HIGH for artifacts, MEDIUM for guide text via page summarizer).
- MediaPipe deprecation banner and `tasks-genai:0.10.27` snippet: `https://developers.google.com/edge/mediapipe/solutions/genai/llm_inference/android`; Maven Central metadata 0.10.35 (2026-04-27) (HIGH).
- Models, sizes, licenses, gating: Hugging Face API `litert-community/*` and `google/gemma-3n-E2B-it-litert-lm` (2026-10-05); Gemma 4 E2B card benchmarks `huggingface.co/litert-community/gemma-4-E2B-it-litert-lm` (via summarizer, MEDIUM).
- Gemma Terms of Use obligations and Gemma 4 Apache-2.0 statement: `https://ai.google.dev/gemma/terms` (MEDIUM-HIGH via summarizer) cross-checked with the HF license tags and press coverage (gigazine 2026-04-03, noze.it).
- ICU4J size: Maven Central `com/ibm/icu/icu4j/78.3/icu4j-78.3.jar` (15.2 MB, HIGH).
- Local, authoritative (HIGH): `~/Projects/Reusable/stt-engine/android/{stt/build.gradle.kts, stt/api/stt.api, API.md, INTEGRATION.md, jitpack.yml, settings.gradle.kts, gradle/libs.versions.toml}`; `git ls-remote https://github.com/Ygaray/voice-engine-android.git`; JitPack POM HEAD 200 for `voice-engine-android-v0.7.0.pom`; `CROSS-REPO-SCOPE-CONTRACT.md` §11 stt-engine v0.7.0 row; this repo's `core/build.gradle.kts`, `keystore/build.gradle.kts`, `build.gradle.kts`, `gradle/invariants.gradle.kts`, `settings.gradle.kts`, `jitpack.yml`, `core/.../provider/{AiProvider,OnDeviceCapability}.kt`, `providers/.../{AnthropicModels,OpenAiModelRules}.kt`.
- Device facts: `~/.claude/context/devices/test-android.md` (SM-S908U, Android 15).

---
*Stack research for: voice-action-engine v1.1*
*Researched: 2026-10-05*
