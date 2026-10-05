# Pitfalls Research

**Domain:** Adding grammar, plan, router, undo, an on-device spike and a hub adapter to an already-published, Metalava-frozen Kotlin/JitPack multi-module library (voice-action-engine v1.0.1, consumed by SecondBrain and CalTracker).
**Milestone:** v1.1 (Phases 12-20, cuts `v1.1.0`)
**Researched:** 2026-10-05 (supersedes the v1.0 file, which stays in git history; the v1.0 pitfalls that are still live are listed in "Carried forward" below)
**Confidence:** HIGH for the API-evolution, module-plumbing, undo and pipeline-interaction pitfalls (read directly from the repo at `v1.0.1`: `core/api.txt`, `TierWalk.kt`, `CommitSink.kt`, `TierPolicy.kt`, `scripts/*`). MEDIUM for grammar/STT behavior (domain knowledge, not measured on this engine's STT). MEDIUM for on-device (vendor docs read 2026-10-05; the spike itself is what produces real numbers).

**How to read the phase column.** `P12`..`P20` are the ROADMAP phases. "Owner" is the phase that must prevent the pitfall; "Also" phases must not reintroduce it.

**The four findings that matter most** (each is expanded below):

1. Release tooling hard-codes the module list `core providers keystore` in about 15 scripts. Several fail loudly on a new module, several would stay green while silently skipping it (Pitfall 9).
2. Metalava cannot see Kotlin's synthetic `$default` constructors, and `apiDump` can overwrite a baseline after an accidental removal. Both make "strictly additive" look green when it is not (Pitfalls 1, 2).
3. `tokenCeiling` / `maxIterations` are **advisory**: the engine never stops a tier. Plan's replan and the Router's extra call must enforce their own bounds (Pitfalls 12, 15).
4. `CommitSink.onAction` fires **after** apply, and a held change can be applied much later. A before-state captured at prepare time (or after apply) makes undo restore the wrong state or clobber a later edit (Pitfall 16).

---

## Critical Pitfalls

### Pitfall 1: "Additive" constructor growth that Metalava does not catch (default arguments, value classes, `$default` synthetics)

**What goes wrong:**
Adding a parameter with a default value to an existing public class removes the old JVM constructor. Keeping the old constructor with `@JvmOverloads` is not enough either: a Kotlin call site that omits defaults compiles to the synthetic `<init>(..., int mask, DefaultConstructorMarker)`, and that descriptor changes when the parameter list grows. `NoSuchMethodError` then hits any precompiled caller. Metalava's signature file does not contain synthetic members, so `apiCheck` stays green. The v1.1 sites that grow an existing class are: `ModelRequest` (four public constructors today, gaining `reasoning`), `Extraction` (2-arg gaining `callId`), `ApiKeyStore` (gaining `keyAccess`), `SingleShot/AgenticLoop/Plan Builder`, `Unhandled`/`TierAttempt` (internal constructors, so safe). A value-class parameter (`ReasoningMode`) has its own trap: a constructor taking an inline class is compiled with a `DefaultConstructorMarker`, so it is not callable from Java at all, and any function taking one is name-mangled in the signature.

**Why it happens:**
The reflex is "add `reasoning: ReasoningMode = ReasoningMode.OFF`". It compiles, consumers recompile, Metalava says nothing. v1.0 already hit this (`ToolSpec`: commits `ca986c1`, `11fdb0f`, the Phase 11 "pre-dump API review" note).

**How to avoid:**
- Rule: an existing public class never gains a defaulted parameter. Add an explicit new overload with **no defaults**, and leave every existing constructor body-for-body intact (the 7-arg `ModelRequest` delegates to the 8-arg). The same for `Builder` properties: add a `var`, never change a constructor.
- Add a binary check Metalava lacks: a script that runs `javap -p -s` over the v1.0.1 published jar/AAR and the HEAD build, and fails if any v1.0.1 public/protected constructor or method descriptor (synthetics included) is missing at HEAD. It is a 30-line diff of sorted lines; run it per phase, not only at the cut.
- New public types (grammar, plan, picker, journal, adapter) are frozen for good at `v1.1.0`. Design them so they never need constructor growth: internal constructors plus a builder/DSL (`TierPolicy { }` is the in-repo model), final classes with explicit `equals`/`toString`, **no `data class`** (`copy`/`componentN` freeze the property order), open value classes (`TraceCode`, `ReasoningMode`) for extensible vocabularies.
- `PickContext` must be a final class with an internal constructor (it is the growth point of `StartTierPicker`, a `fun interface` that can never gain a second abstract method).

**Warning signs:** a `= default` appears in a diff of an existing public constructor; a PR touches `api.txt` with `-` lines; `ModelRequest(` call sites in sample code stop compiling after a rename; the `ApiShapeTest` constructor-growth lint is edited instead of satisfied.

**Phase to address:** P12 (all existing-class growth). P14-P18 (design new types growth-proof). P19 (API surface review before the cut, not at P20).

---

### Pitfall 2: The Metalava baseline is overwritten, or never exists, while development proceeds

**What goes wrong:**
`metalavaCheckCompatibility` compares HEAD against the committed `api.txt` treated as "released". During v1.1 that file is the v1.0.1 baseline. If anyone runs `apiDump` after an accidental removal or signature change and commits the result, the check passes forever and the break ships. The root build only requires `api.txt` to exist once any `v*` tag exists. For the two **new** modules (`undo`, `voice-adapter`) there is no released baseline at all, so nothing protects their API until the tag. The release-cut script's gate 12 does catch the first problem, but only at P20, after nine phases of drift.

**Why it happens:** `apiDump` is the natural "make the build green" button. Nine phases touch `core`/`providers`/`keystore`, each tempted to refresh the dump.

**How to avoid:**
- Per-phase gate (add in P12, run in every phase verification): `git diff v1.0.1 -- core/api.txt providers/api.txt keystore/api.txt` has zero `-` lines (ignore pure reordering). Script it as `scripts/verify-api-additive.sh`, reuse the same logic in release-cut gate 12.
- Treat `apiDump` as a human-reviewed `+`-only operation; verification fails if a phase commit deletes a line from a baseline file.
- For new modules: commit `api.txt` in the commit that creates the module, and say plainly in the phase VERIFICATION that the module's API is unprotected until `v1.1.0`. Do an explicit API review of both modules in P19.
- Keep the in-flight `api.txt` for new modules out of the "released" `verifyApiDumpPresent` logic only if the script knows the module is new (see Pitfall 9).

**Warning signs:** `api.txt` in a phase diff with deletions; a phase summary that says "regenerated api.txt"; `metalavaCheckCompatibility` is green on a branch that renamed a type.

**Phase to address:** P12 (add the gate). Every phase (run it). P20 (gate 12 is the backstop).

---

### Pitfall 3: A new subclass or abstract method on a type consumers switch over or implement

**What goes wrong:**
These are sealed in `api.txt` today, so a new subclass breaks every consumer's exhaustive `when`: `CommandOutcome`, `StrategyOutcome`, `RunTermination`, `GateDecision`, `ToolStep`, `AssistantPart`, `Message`. SB and CT implement `CommitSink`, `ToolExecutor`, `PreApplyGate`, `PendingMutation`, `OutcomeResolver`, `ToolSpecProvider`: a new abstract method breaks them (`AbstractMethodError` at runtime for precompiled implementers). v1.1 temptations, each of which is wrong:
- A `CapRefused` outcome (already rejected by the brief; `cappedByPolicy` is the additive flag).
- A `StrategyOutcome.Replan` / `PartiallyCompleted` for Plan. Use `Completed(partial = true)` and the existing `escalation_suppressed` code.
- `AssistantPart.Thinking` when `ReasoningMode.PROVIDER_DEFAULT` makes a provider return reasoning blocks. The transcript is sealed (`Text`, `ToolCall`); provider-opaque reasoning must travel through `NativeReplay`, as v1.0 does, never as a new part.
- `CommitSink.onBeforeApply(...)` for undo. If a hook is needed it must be a **default-bodied** method (Kotlin 2.3 compiles interface defaults as real JVM `default` methods, which `api.txt` confirms by showing `default`), or a separate optional interface.
- A `ToolStep.Undo`, `RunTermination.Undone`, a new `GateDecision`.

**How to avoid:**
- A grep-able invariant, enforced by a test: the set of `sealed` classes in `api.txt` has no new subclass versus v1.0.1 (the Pitfall 2 script already shows it as `+` lines under a sealed parent; flag any `+` class whose `extends` is a sealed parent).
- Extensible vocabularies are open: `TraceCode` (add `router_fallback`), `EscalationReason`/`FailureReason` interfaces with `Other(code)`, `ReasoningMode`, `ProviderId`. KDoc each with "keep an `else` branch".
- Interface growth: default body only, never abstract; never add a second member to a `fun interface` (`StartTierPicker`, `KeyAccess`, `ConfirmationPolicy`).

**Warning signs:** a diff adds `: CommandOutcome()` / `: StrategyOutcome()` / `: AssistantPart()`; an interface in `core` gains a `suspend fun` with no body.

**Phase to address:** P12 (`PROVIDER_DEFAULT` replay shape, `cappedByPolicy`). P15 (Plan outcomes). P16 (picker/router). P17 (undo hook).

---

### Pitfall 4: `@RequiresOptIn` seam that is not actually gated (`DelicateKeyAccess`, and the on-device `@Experimental`)

**What goes wrong:**
- Java callers ignore Kotlin opt-in entirely, so `new ApiKeyStore(ds, slots, keyAccess)` compiles from Java with no marker.
- Metalava may not record the annotation in `api.txt`, in which case removing or weakening the marker later is invisible to `apiCheck`.
- Marker details are easy to get wrong: wrong `@Target` (does not cover constructors), `@Retention(SOURCE)` (not enforced for precompiled consumers), `level = WARNING` (a warning is not a gate), and giving the **new constructor a default** for `keyAccess`, which makes an opt-in-free call ambiguous with the kept 2-arg constructor.
- The public `fun interface KeyAccess` is itself ungated. If any other public API accepts a `KeyAccess`, the gate is bypassed. The ctor must be the only entry.
- "`@Experimental`" in the roadmap is not a Kotlin annotation. For the on-device module use a dedicated `@RequiresOptIn(level = ERROR)` marker (for example `ExperimentalOnDevice`), and note it is fixed at ERROR: lowering later is allowed, raising later breaks consumers.

**How to avoid:** `@RequiresOptIn(level = ERROR)`, `@Retention(BINARY)`, `@Target(CONSTRUCTOR, FUNCTION, CLASS)`. Prove it with a compile-fail negative control (a sample/test source calling the constructor without `@OptIn` must fail to compile; the repo already has a `negative-controls` harness for exactly this style). Grep `api.txt` for the marker on the constructor; if Metalava drops it, assert it in a reflection test instead. Document "Java callers are not gated". Redact `KeyAccess` in `toString`.

**Warning signs:** a unit test constructs the store without `@OptIn` and passes; the 3-arg constructor has `= ...`.

**Phase to address:** P12 (SEAM-07). P13 only if the spike turns green.

---

### Pitfall 5: Freezing a wrong shape at the `v1.1.0` tag

**What goes wrong:**
P14 (`GrammarPack` DSL, `normalize` type), P15 (plan schema, binding syntax), P16 (`StartTierPicker`/`PickContext`), P17 (journal/adapter API), P18 (adapter surface) all publish brand-new public API that is immutable from the tag on. The roadmap's phases are research-flagged, but the only freeze review in v1.0 happened at the last moment (Phase 11 "pre-dump API review"), and a wrong guess then costs a `v2`.

**How to avoid:** every new-API phase ends with a short "frozen-surface" review: list each public type, each constructor, each lambda type and ask "what is the one thing a consumer will ask for next, and does this shape let us add it without removal?". Concretely:
- A function-typed public parameter freezes its arity. The requirement says `normalize: (raw, language) -> String?`; if the shape is still negotiable, make it a `fun interface SlotNormalizer` with the same call shape, and note an added parameter is impossible later (or take a small context object).
- Prefer capabilities declared by data (builder properties, open value classes) over subclassing.
- Do not let SB/CT's immediate needs (SB 176-178, CT 75) decide the long-term shape alone: run the API sketch past the orchestrator the way the R-v1.1 brief did.

**Warning signs:** a public constructor with more than ~4 positional parameters in a new type; a new public `interface` (rather than `fun interface` or abstract class with internal ctor) that consumers are expected to implement and that is likely to grow.

**Phase to address:** P14, P15, P16, P17, P18 (each, at plan time). P19 (consolidated review).

---

### Pitfall 6: A bundled on-device model cannot ride the library, and the stack under it is moving

**What goes wrong:**
- **The weights must never be in the artifact.** A ~1.3 GB Gemma-2B-class model cannot be a JitPack AAR (JitPack's FAQ documents a **15-minute** build limit and public artifacts become immutable after 7 days; GitHub's per-file limit and the public repo make committing the file impossible). The library ships code only; the app delivers the model.
- **Delivery has hard limits.** Google Play (support.google.com 9859372, read 2026-10-05): base module 500 MB, each asset pack 1.5 GB, install-time packs 4 GB cumulative; users get a data warning above 200 MB. An APK asset that is compressed is also copied at install (about 2x on-disk), and a model that is memory-mapped must be stored uncompressed (`noCompress`), otherwise the first load decompresses 1+ GB. Copying it into `filesDir` on first launch doubles storage and adds a long first-run.
- **The runtime is changing.** Google's MediaPipe LLM Inference API is now **maintenance-only**; new work goes to **LiteRT-LM** (`com.google.ai.edge.litertlm:litertlm-android`, Kotlin API marked stable, `.litertlm` model files, tool-use support). The roadmap text says "MediaPipe / LiteRT" and "Gemma-2B-class". Building a productized module on the deprecated runtime, or measuring only the 2B-v1 model, would give a verdict about yesterday's stack.
- **Native libs.** The module cannot compile NDK code on JitPack's `openjdk17` image, so it must consume the vendor's prebuilt AAR. Google Play requires 16 KB page-size support for apps targeting Android 15+ (the Android docs page read 2026-10-05 states a **February 1, 2027** deadline; verify the date again at plan time), so every `.so` in the chosen AAR must be 16 KB aligned (`llvm-objdump -p lib.so | grep LOAD` shows `align 2**14`). SB and CT target SDK 36. ABI: the S22 is arm64-v8a only; a library cannot force `abiFilters`, so document that consumers should filter, or every ABI of the native libs lands in their APK.
- **Kotlin metadata.** A vendor AAR built with a newer Kotlin than 2.3.20 can be unreadable by the consumers' compiler (the compiler reads only one minor ahead). Check the AAR's Kotlin metadata version before choosing it.

**How to avoid:** the spike (P13) answers these with numbers and a documented decision, in this order: (1) pick LiteRT-LM unless a blocker is found, and name the model and its `.litertlm`/`.task` file format; (2) keep weights out of every Gradle module and out of git (add the model path to `.gitignore` and the repo-hygiene gate; push with `adb push` to the TESTER's app-private dir); (3) record APK/AAR size delta of the runtime alone, and the 16 KB / ABI / metadata check results; (4) state the delivery recommendation (download-on-demand or asset pack) in the verdict.

**Warning signs:** `git status` shows a `.task`/`.litertlm`/`.bin` file; a Gradle `assets` entry for the model; the verdict mentions only "MediaPipe".

**Phase to address:** P13 (owner). P19/P20 (the install list and the JitPack build time must still pass if a green module is added).

---

### Pitfall 7: On-device runtime behavior: RAM kill, native crash, thermal, cold load

**What goes wrong:**
- **RAM and OOM are process death, not exceptions.** A 2B int4 model needs roughly 1-1.5 GB (the contract's estimate) plus KV cache, on top of the host app. Mmapped weights are file-backed (reclaimable), but anonymous KV/activations are not; `lmkd` kills the process, often while the app is backgrounded. A native crash (SIGSEGV in the inference library) takes down **the consumer app**, and the engine's never-throw collapse cannot catch it.
- **Cold load is the real latency.** First load reads the whole file and may compile GPU shaders; it can take many seconds the first time and again after the process is killed. Spike numbers taken warm are misleading for a voice command.
- **Thermal.** Sustained inference throttles clocks (public measurements report 30-40% clock drops within two minutes on flagship SoCs). A 20-prompt back-to-back test run is not representative in either direction.
- **First token vs decode.** Prefill dominates for a long tool-schema prompt; the same prompt as SingleShot sends (system + tool specs) may be thousands of tokens on a 2B model.

**How to avoid:**
- Measure, and record in SPIKE-01: cold start (process just killed), warm, and after 5+ consecutive runs; p50/p95 latency; peak PSS from `dumpsys meminfo` (native heap, not the Java heap); the thermal status (`PowerManager.getCurrentThermalStatus()`); battery state.
- Fix the verdict thresholds **before** running (propose in the spike plan, for example cold load under N s, warm command under M s, peak PSS under X MB, zero process deaths over the run). A threshold chosen after seeing the numbers is a rationalization.
- If green, the provider must: load lazily on first `ON_DEVICE` use (never at app start), refuse to load when `ActivityManager.MemoryInfo.lowMemory` or available memory is under a floor (typed `unavailable`, not an attempt), release on `onTrimMemory`, and document the crash-isolation limit (a separate process is the only real isolation and is probably out of scope).

**Warning signs:** the spike harness is one long loop in the foreground; no cold-start row; the numbers table has no PSS column.

**Phase to address:** P13 (owner). If green and productized: the phase that ships the module (P13 or 13.1).

---

### Pitfall 8: A 2B model's "JSON works" is not "the command is right", and it breaks the never-guess rule

**What goes wrong:**
SPIKE-01 measures "schema-valid tool-call JSON". A schema-valid call can name the wrong tool or carry a wrong argument (a different quantity, an entity that does not exist). Because the on-device path is meant to be a cheap-first SingleShot that goes through the same gate, a green number on validity alone lets a write through that the user never said. Small models are weakest on Spanish and on negatives ("don't add X"). Sub-3B models also fail strict schemas a meaningful fraction of the time on the first attempt without constrained decoding; the retry then doubles latency.

**How to avoid:**
- The spike's prompt set includes: a gold-labelled correct tool and arguments (score **semantic** accuracy, not just validity); Spanish prompts; negation and near-miss prompts that must not produce a write; and a "no tool applies" set. Report trial counts and a lower confidence bound, not a single rate.
- Prefer the runtime's constrained decoding / tool-use support if the chosen runtime has it; measure with and without.
- If it ships: restrict the on-device SingleShot to reads/previews, or require a confirm gate for writes, and say so in the module KDoc and INTEGRATION. The ON_DEVICE capability gate must also stay explicit in the trace: a cloud-to-on-device fallback that silently degrades quality is a Pitfall-13 (v1.0) privacy-and-correctness issue, not a convenience.

**Warning signs:** a verdict that says "97% valid JSON" with no accuracy figure; no Spanish or negative prompts; trial count under about 50.

**Phase to address:** P13.

---

### Pitfall 9: Release tooling is hard-wired to three modules; a new module is either rejected loudly or skipped silently

**What goes wrong:**
Grepped at `v1.0.1`: the literal list `core providers keystore` (or the three coordinates, or "three api.txt") appears in `settings.gradle.kts`, `jitpack.yml`, `scripts/jitpack-dry-run.sh` (exact-set equality and a `core:jar providers:jar keystore:aar` loop), `jitpack-live-probe.sh`, `jitpack-consumer-probe.sh`, `release-cut.sh` (the `MODULES` list, gate 7's allowlist of three `api.txt` paths, gate 12, gate 15's `providers/keystore -> core` check, the sandbox `git add`/`rm` of three files), `api-dump-isolated.sh`, `verify-api-dump.sh`, `review-api-surface.sh`, `verify-negative-controls.sh`, `verify-repo-hygiene.sh`, `verify-docs-coverage.sh` (public-symbol scan over three source roots, coordinate regex), and `agent-wiring-test.sh` (coordinate regex). Two kinds of failure:
- **Loud (good):** the dry-run's exact-set check and release gate 7 reject a new module's artifacts and `api.txt`. These will break P17's first dry run, which is the right moment.
- **Silent (dangerous):** the per-module loops in the API dump, hygiene scan, docs-coverage symbol scan, negative controls and agent wiring test simply do not look at `undo`/`voice-adapter`. They stay green while the new module has no API check, no hygiene scan and no docs coverage.

**How to avoid:**
- P17 (which the roadmap already makes the owner of "add a published module" plumbing) introduces **one** source of truth, for example `scripts/modules.list` with `name packaging artifactId`, read by every script and cross-checked against `include(...)`, `jitpack.yml`, and every module that applies `maven-publish`. A consistency gate fails if they disagree.
- Add a planted negative control to `verify-negative-controls.sh` / the release-cut selftest: add a dummy sixth module and assert each gate goes red or the consistency gate fails. This repo already proves "each rule bites"; extend it to modules.
- Gate 15 must also assert `voice-adapter -> core` carries the tag version, and the new modules' POM/`.module` metadata, not only `providers`/`keystore`.
- Sample and docs: `verify-docs-coverage.sh`'s "every public symbol is documented" must include the new roots, or DOC-02 passes vacuously.

**Warning signs:** a phase summary says "added module X" and no script diff accompanies it; `verify-docs-coverage` runtime does not change after adding a module.

**Phase to address:** P17 (owner, per the roadmap). P18 and P13 (rebase onto it). P20 (selftest).

---

### Pitfall 10: JitPack coordinates, inter-module POMs and a cross-hub dependency for the new modules

**What goes wrong:**
- **Wrong artifactId or group** burns an immutable tag. The coordinates are fixed by E5/E7: `com.github.Ygaray.voice-action-engine:voice-action-engine-undo` and `…-voice-adapter`; the artifactId must be set explicitly in the publication (the default is the Gradle project name `undo`).
- **Inter-module POMs.** `voice-adapter -> :core` must publish as `com.github.Ygaray.voice-action-engine:voice-action-engine-core:<tag>`. JitPack's `.module` rewriting has known issues (jitpack#4112, #4476). Keep the `vaeDisableModuleMetadata` escape hatch and live-probe the new modules' POMs, as v1.0 Pitfall 1 required.
- **`:stt` lives in another JitPack group and a mirror repo.** From the ledger: `com.github.Ygaray.voice-engine-android:voice-engine-android:v0.7.0` (mirror `Ygaray/voice-engine-android`), five modules, and "do not pin the plain aggregator". A typo or the aggregator coordinate fails resolution **during VAE's own JitPack build**, and a failed build for an immutable tag means a new patch tag.
- **Repositories.** `settings.gradle.kts` uses `RepositoriesMode.FAIL_ON_PROJECT_REPOS` with google + mavenCentral only. `:voice-adapter` needs jitpack.io added. Add it with a `content { includeGroup("com.github.Ygaray.voice-engine-android") }` filter so no other GitHub user's artifact can ever resolve from it (dependency confusion), and mirror that in the consumer-probe and dry-run settings.
- **Build time.** JitPack's documented limit is 15 minutes for all install commands. Two more modules (one an AGP library) plus a possible on-device AAR in one `./gradlew` line raise wall time and memory. Measure the dry run from an empty cache and keep headroom; the install list publishes only, it never runs `check`.
- **Aggregator POM.** The retired aggregator will now also list `undo`, `voice-adapter` and, through the latter, `:stt`. Consumers must not use it (E5); say so again in the README coordinates table.

**How to avoid:** the dry-run asserts the exact artifact set (updated list), the expected extension per module, and POM dependency coordinates by reading the POM XML (not by `grep` on the filename). Run the live probe on a SHA before the cut (as v1.0 Phase 1 did), including a consumer that depends on core+providers+keystore only and asserts **no** `voice-engine-android` appears in its resolved graph, and one that adds `:voice-adapter` and does get it.

**Phase to address:** P17 (plumbing), P18 (stt coordinate and repository), P20 (live resolution from an empty cache, gate 13).

---

### Pitfall 11: Dependency direction and version coupling for `:voice-adapter`

**What goes wrong:**
- **Transitive pull.** `api(stt)` or `implementation(stt)` publishes `:stt` (and its own dependencies, including OkHttp 5.2.1) into the adapter's POM. An app that adds the adapter inherits `:stt`'s dependency graph and a minimum version it may not want, which is the A1 "never force a consumer's dependency" problem in a new place.
- **Binary coupling to another hub's types.** The adapter is compiled against `:stt` v0.7.0 types (`FinalSegment`). If `:stt` later changes a type, the adapter fails at runtime (`NoSuchMethodError`/`NoClassDefFoundError`) in a consumer that upgraded `:stt`, with no compile-time signal in this repo.
- **Packaging.** `:stt` is an AAR, so `:voice-adapter` must be an Android library, minSdk not below `:stt`'s (33) and compatible with consumers (35).
- **Language semantics.** `:stt` emits a bare `en`/`es` only at confidence 3, otherwise `null`, and the label is **per segment** and can flip mid-session (A15). Mapping `en-US` to `en`, defaulting `null` to `en`, caching the last language, or merging segments are all "guesses". `CommandInput.language` is `"en"|"es"|null` and the grammar tier uses it to pick its rule set (Pitfall 13).

**How to avoid:**
- `compileOnly` the `:stt` dependency (apps that call the adapter already have `:stt`), document the minimum `:stt` version, and test it with a consumer-probe that adds both.
- Keep every `:stt`-typed symbol in one small file and put language normalization (`"en"`/`"es"`/anything else to `null`, no region tags, no default) in a `:stt`-free function that is JVM-unit-tested without `:stt` on the classpath. Consider offering the plain `(text, languageLabel)` overload so apps that want no coupling at all can skip the `FinalSegment` overload.
- One segment maps to one `CommandInput`; the adapter holds no state between segments.
- `:core` must stay `:stt`-free. The module-graph gate and `:core` classpath allowlist already exist; add a negative control that plants `:stt` into `:core` and `:providers` and goes red, and a positive test that the adapter is the only module whose resolved classpath contains `voice-engine-android`.
- Confirm the `FinalSegment` constructor visibility: if it is internal in `:stt`, adapter tests need a fake via a documented factory or the type is only reachable through a real recognizer.

**Warning signs:** the adapter's `.pom` lists `voice-engine-android` as a runtime dependency; `en-US` becomes `en` in a test; the adapter exposes a `Flow<CommandInput>` that merges segments.

**Phase to address:** P18 (owner). P20 (graph and POM gates).

---

### Pitfall 12: The grammar tier guesses, and a guess here writes without any model in the loop

**What goes wrong:**
A grammar match has no confidence signal and no second opinion: it completes with zero provider calls and goes straight to the gate and the app's write. Fuzzy behavior that is harmless in a search box becomes a silent wrong write: substring or "contains" matching ("no agregues leche" matches "agrega leche"; "don't add milk" matches "add milk"), partial matches ("add milk and also call mom" binds only the first clause), first-rule-wins when two rules match, free-text slots that swallow trailing words ("add milk to my list please" becomes the item `milk to my list please`), slots resolved by "nearest" candidate, and a `normalize` hook returning a best-effort string.

**How to avoid (design rules for P14, to be written into the phase CONTEXT as non-negotiable):**
- **Whole-utterance, anchored match only.** The rule must consume the entire normalized transcript. Optional filler words ("please", "por favor") are an explicit per-language list. No scoring, no threshold, no stemming, no edit distance.
- **Ambiguity is `NoMatch`.** Two rules match, one rule has two parses, a number slot parses two ways, or (when `language == null`) the EN and ES rule sets both match with different results, all return `NoMatch`. If both languages match with the **same** tool call and slot values, that is one match.
- **One rule is one tool call** in v1.1; no multi-clause or conjunction handling (that is Plan's job and escalates cleanly).
- **App resolver rejection, `normalize` returning null, or `normalize` throwing, all give `NoMatch`** with a trace code; the tier never throws and never returns `Failed` for an unmatched input.
- Free-text slots must be bounded by literal anchors in the rule or by an app-declared max token count; otherwise the rule is rejected at build time.
- Build-time validation of the pack (fail fast at composition, like `TierPolicy`'s `require`): a rule needs at least one literal token, no empty alternatives, no duplicate rules, and each rule's generated example utterances must parse back to **that rule uniquely** (a built-in round-trip/ambiguity self-check an app can run in a unit test).
- Matching is a linear-time token matcher, not user-composed regex (catastrophic backtracking on a long dictated paragraph blocks the caller's thread; cap the token count and return `NoMatch` above it).
- Precision beats recall: the cost of a miss is one cheaper-tier LLM call; the cost of a false positive is a wrong write. Never tune matching looser to raise the hit rate; use the trace's `no_match` counts to add rules instead.
- Keep v1.1 slot types to integer, decimal, enum/choice and bounded free text. Dates and times ("a las tres y media", "mañana", "el 15") are a separate hazard class; defer them or ship them as app-resolver territory.

**Warning signs:** the matcher has a score, a threshold, `contains`, `startsWith` on the transcript, or a "best match"; a test for "returns the closest rule" exists; the near-miss corpus is empty.

**Phase to address:** P14 (owner). P16 (grammar must remain the free pre-pass and still hand over cleanly). P19 (Gate-1 includes a near-miss that must go to the next tier).

---

### Pitfall 13: Bilingual STT text: accents, punctuation, number words, the language label

**What goes wrong:**
- **Accents and the tilde.** Folding diacritics with NFD-and-strip turns `ñ` into `n` ("año" becomes "ano", "cañas" becomes "canas"). `más`/`mas`, `sí`/`si`, `qué`/`que`, `él`/`el`, `tú`/`tu` are different words. STT output is inconsistent about accents. Normalize to NFC and lowercase with `Locale.ROOT`-style invariant lowercasing only; fold an accent only where the rule author lists the unaccented variant as an explicit alternative, never globally.
- **Punctuation and casing.** Recognizers insert commas, periods, question marks and `¿`; trailing punctuation must be stripped, interior punctuation (a decimal comma) must not.
- **Digits versus words versus formats.** The same utterance can arrive as `3`, `three`, `tres`, `3.5`, `3,5`, `tres coma cinco`, `tres punto cinco`, `1.000` (one thousand in ES, one in EN), `1,000`. Number format depends on the language **label**, which may be `null` or wrong (Pitfall 11).
- **Spanish number words** are the classic source of silent errors: `veintiuno`/`veintiún`/`veintiuna` (apocope before a noun), `cien` (exactly 100) versus `ciento` + n, `y` only between tens and units from 31 to 99 (`treinta y uno`, not `veinte y uno`), `quinientos`/`setecientos`/`novecientos` irregulars, gender (`doscientas`), `mil` takes no `un` (`un mil` is wrong), `millón` needs `de` before a noun, `medio`/`media`, `docena`. STT also emits non-canonical forms (`treinta uno`, `veinte y uno`). English: `a hundred`, `one hundred and five`, `oh five`, `twenty one` versus `twenty-one`, `point`.
- **Cross-language false friends** when the label is `null` and both rule sets run: `once` (EN adverb, ES eleven), `no`, `sin`, `me`, `a`, `pie`.
- **Imperative and clitic variants** in ES (`agrega/agregue/agregá`, `agrégame`, `ponlo`, infinitive used as a command) are a coverage problem, not a correctness one, as long as Pitfall 12's rules hold. Do not promise coverage.

**How to avoid:**
- A table-driven number parser per language with a strict grammar: it must consume the **whole** token span, and anything it does not recognize returns "not a number" (so the rule fails). `cien cinco` is not 105.
- Test it two independent ways: a generator that spells every integer 0-999,999 (and decimals up to a stated precision) and checks parse(spell(n)) == n, plus a hand-written list of malformed and non-canonical forms with the expected result for each (`NoMatch` unless explicitly accepted).
- Slots carry min/max bounds so a mis-parse is rejected by range, not trusted.
- Test fixtures cover: tilde words, homograph pairs, trailing and interior punctuation, digit/word/decimal-comma mixtures, `null` and wrong language labels, `once`, and mixed-language utterances (the stt plan-149 probe found the miss was intra-sentence code-switching: that must be `NoMatch`).
- Language rules: if the label is `en` or `es`, run that rule set only; if `null`, run both and require agreement (Pitfall 12). Never default a `null` label.
- Library main code names no unit or domain word (CLN-02 scan): units like grams or calories are app-supplied; only number words and neutral filler live in the engine.

**Warning signs:** `Normalizer.Form.NFD` plus `\p{M}` stripping anywhere; `String.toLowerCase()` without `Locale.ROOT` (deprecated, locale-dependent); a number parser that returns a partial value; tests only use canonical spellings.

**Phase to address:** P14 (owner). P18 (language label contract). P19 (one ES Gate-1 command).

---

### Pitfall 14: Plan binding resolves against a step that did not commit

**What goes wrong:**
PLAN-02 lets step N use step M's write output (`ExecutedAction.targetIds`). That map is app-defined and is only meaningful for an action that was **committed**. Failure modes: binding to a step that was held (no id yet, the confirm may never come), previewed, rejected or errored (`applied` true but `isError`); binding to a step that does not exist, a forward reference, or itself; ambiguous keys when one step touched several entities; a binding written as string interpolation (`"${step1.noteId}"`) colliding with literal text the user dictated; an LLM-produced reference to a key the app never returned. Plan runs in order, so a hold in the middle also leaves every later step unexecuted and unresumable: `commitHeld` applies only the held proposal, not the rest of the plan.

**How to avoid:**
- **Validate the whole plan before executing anything:** every tool exists and was offered, arguments pass the tool's schema, step count under a cap, every reference points to an earlier step and to a mutating step, no read tools (Pitfall 15 lookup rule). A validation failure is then a clean pre-commit `Escalate` with zero side effects. Most failures should land here.
- **Structured binding, not text templating:** a JSON reference object in a typed argument position (`{"$ref": {"step": 1, "key": "noteId"}}`); only declared-bindable arguments accept it; the engine substitutes the string value from `targetIds[key]` of a committed, non-error action; anything else fails that step.
- A held step ends the plan as a partial `Completed` with the remaining steps unexecuted and visible in the trace; do **not** continue "independent" steps. Document it prominently, because SB's confirm-gate-per-mutation plus a multi-step plan yields half-plans. This is a design question for the P15 discuss step: either apps gate the whole plan once, or Plan is documented as gate-per-step with no resume.
- The one planning call produces all steps, so every step's `ExecutedAction.providerCallId` (SEAM-06) would be the **same** planning call id. SB 178 groups by this id for run-undo; define explicitly what Plan puts there (the planning call's id, with `position` as the discriminator) and say so in KDoc.

**Warning signs:** a binding helper that reads `targetIds` without checking `kind == COMMITTED`; string `replace("${")` anywhere; a plan test with only the happy path.

**Phase to address:** P15 (owner). P12 (the `providerCallId` definition). P17 (consumes it).

---

### Pitfall 15: Replan and escalation after a commit, and unbounded model calls

**What goes wrong:**
- The pipeline already suppresses an `Escalate`/`NoMatch` once the run applied or held anything (`TierWalk.hasWorked()` → partial `Completed` + `escalation_suppressed`), so no later tier repeats the write. But **Plan can still spend a replan call before the pipeline suppresses**: step 2 fails after step 1 committed, the strategy makes its one replan call, then returns `Escalate`, which is suppressed. That is a paid model call with no possible benefit.
- Worse, a replan that is allowed to plan again after a commit can re-emit already-committed steps (a duplicate write that the gate admits as a new proposal).
- `maxIterations` and `tokenCeiling` are **advisory** in `TierPolicy` ("the engine counts tokens but never stops a tier"). The strategy must enforce them itself, or a runaway plan is unbounded. The hard "at most one replan" must be a constant in the strategy, not a policy field an app can raise.
- A large plan or a `max_tokens` stop truncates the plan JSON: a malformed plan must be detected (stop reason) and become `MalformedExtraction`, not parsed leniently.
- A step "that needs a lookup" cannot be detected by asking the model. A deterministic rule: any read (non-mutating) tool in the plan, or any reference to a non-mutating step, means escalate **before** executing anything (contract §4: Plan is for lookup-free commands).
- `onFailed` (PLAN-05) must mirror SingleShot exactly: provider failures only, never ceiling/gate/`strategy_error`, hook exceptions guarded, and the hook's outcome must not turn a post-commit failure into an `Escalate` (the pipeline suppresses it anyway, but the trace must say so).

**How to avoid:** replan only when nothing has committed or been held; after any commit a failure ends partial `Completed` without a model call. If a replan is ever allowed after a commit, its prompt must include the done steps as facts and its output is validated to contain none of them. Check `session.tokensUsed` against the ceiling before the replan call. Count and assert model calls in tests ("exactly 1" for success, "exactly 2" for a pre-commit replan, "exactly 1" for the post-commit failure). The success criteria in ROADMAP SC3 and SC4 interact (SC3 says a failed step triggers one replan, SC4 says post-commit failures end partial): resolve it explicitly in the P15 CONTEXT as above.

**Warning signs:** a Plan test that never checks the model-call count; a replan prompt that includes the original steps unchanged; Plan reading `maxIterations` as a step cap with no separate cap.

**Phase to address:** P15 (owner). P12 (`onFailed` shape).

---

### Pitfall 16: The Router makes every command slower and dearer, and its savings number lies

**What goes wrong:**
- A router adds one model round trip **before** any tier runs. Linear's first LLM tier is already the cheap SingleShot, so the router only pays off when it avoids a wasted escalation or sends a complex command straight to the right tier. For a short, simple command it is a pure cost.
- Misroutes are asymmetric. Routing too low is recoverable (the lower tier escalates, you paid router + wasted tier). Routing too high is silent (an AgenticLoop run where SingleShot would have done; you simply pay more). A "tiers saved versus Linear" counter is counterfactual: it cannot know whether Linear would have succeeded at tier 1. Reporting it as savings will overstate.
- The picker/router call is a provider call, so every v1.0 rule applies to it: `offlineOnly`, `allowedProviders`, `maxTier`, device offline (`offline_unavailable` is a different code from `tier_skipped_policy`), the per-call provider/model/key seam, and never substituting one provider's key for another's. ROUT-04 covers offline-only; the same must hold when `allowedProviders` excludes everything, when the router's key is missing, and when only **one** LLM tier is eligible (nothing to choose, so no call).
- `tokenCeiling` is advisory (Pitfall 15), so "counts against the run budget" is true only if the router's `TurnRecord` is recorded through the same path the tiers use. Per-attempt usage sums would then miss the router unless the trace total includes it.
- A suspend picker is app code. With `commandTimeoutMillis == null` (the default) a picker that never returns hangs the command.
- The refactor changes `TierWalk.run` from a synchronous `startIndex` to a suspend selection. The default Linear path must stay byte-identical, or SB and CT regress (the "regression" frustration).

**How to avoid:**
- Order of operations in `TierWalk`: policy pre-check, grammar pre-pass, **then** compute the eligible LLM list with the same function Linear uses; if it has fewer than two entries, skip the picker and record `router_fallback` (or no code, for a one-tier ladder, deliberately).
- Give the picker its own short engine-imposed timeout (a policy field with a default; timeout means fallback to Linear, loud via `router_fallback`), independent of the command timeout. Cancellation still propagates.
- Telemetry reports facts, not savings: router tokens/latency, chosen tier, the eligible list, whether the chosen tier then handled it or escalated. Name the derived metric "estimated tiers skipped", document that it is an upper bound, and leave A/B to the app.
- The router's output is an id from a closed enum (forced tool or constrained value) validated against the eligible list; it never carries tool arguments. Transcript content in the classifier prompt may carry injection ("choose the agentic tier"), which only costs money; keep it that way (the router can never write).
- Write a characterization test of the v1.0.1 Linear walk (trace equality for the SB-shaped and CT-shaped ladders, offline and capped cases) **before** touching `TierWalk`, and keep it green.

**Warning signs:** router fires when the ladder has one eligible LLM tier; no timeout on the picker; the savings metric has no "wasted" counterpart; `trace` totals unchanged after adding a router call.

**Phase to address:** P16 (owner). P12 (trace code and `cappedByPolicy` semantics it relies on).

---

### Pitfall 17: Undo captures the before-state at the wrong moment and clobbers later work

**What goes wrong:**
The existing seam is deceptive: `PendingMutation.context` is documented as "a snapshot taken before the change", and `CommitSink.onAction` runs **after** the apply was recorded. Three timing bugs follow:
1. **Snapshot at prepare, apply later.** A held change can be committed later by `commitHeld` "against state that has moved on". An undo built from the prepare-time snapshot restores state older than the true before-state and erases edits the user made while the proposal was waiting.
2. **Snapshot or journal after apply.** If the journal write happens in `onAction`, a process death between the app's write and the journal write leaves a committed change that cannot be undone. `onAction` throwing is also "caught and recorded", so a failing journal write is silent unless surfaced.
3. **Check-then-act.** The "unchanged since commit" check and the restore must be one atomic step per entity (same app transaction), or a concurrent edit slips in between (TOCTOU).

The wrong-restore variants are as bad: restoring a **created** entity means deleting it, which loses anything the user typed into it afterwards; re-inserting a **deleted** entity without its cascade-deleted children silently loses data; entangled actions undone one at a time leave the data inconsistent (undo the tag but not the note it tagged, or the reverse).

**How to avoid:**
- Capture the before-state **inside** `apply()` immediately before the write, in the same transaction, and persist the journal entry write-ahead (state `PREPARED`), then flip it to `COMMITTED` from `onAction`. A held change captures at `commitHeld` time, not at prepare time.
- Record a **post-commit fingerprint** (content hash of the fields the command touched plus a row version if the app has one) with the snapshot. The unchanged check compares the live fingerprint to it; do not rely on `updatedAt` alone (ms collisions, background sync and reminder arming touch it and cause false "changed", while untracked columns cause false "unchanged"). Prefer a false refusal over a false restore.
- **Two-phase undo:** first verify every entity in the group, then restore; refuse the **whole** group (listing every blocker) if any entity changed, unless the adapter declares the actions independent. "Complete or an exact list of what could not be restored" (UNDO-03) means no silent partial state.
- Adapters declare their cascade footprint (children included) and the engine groups actions by that footprint (A18). `targetIds` is an opaque map; the engine cannot infer entanglement from it, so grouping needs the adapter's declared entity keys.
- Reverse order: by `position` descending within a run, and the DB restore before compensators or the reverse, decided and documented once (an alarm re-armed for a note that was not restored is worse than a note restored without its alarm).
- Journal entries carry a schema version. A snapshot written by an older entity shape returns "incompatible, refuse loudly", never a crash or a half-restore.

**Warning signs:** a snapshot built in `prepare`; undo code that reads `ExecutedAction.context` after `commitHeld`; no test where a second command edits the same entity between commit and undo; no FK-cascade fixture.

**Phase to address:** P17 (owner). P15 (Plan's steps are many actions per run). P19 (Gate-1 "Undo all (N)" includes a later-edit refusal case).

---

### Pitfall 18: Undo that is not idempotent, not durable, or not concurrency-safe

**What goes wrong:**
- **Compensators** for out-of-database effects (alarms, notifications, files) must tolerate being run twice and being run after the effect is already gone. Cancel-alarm and delete-file are naturally idempotent. "Re-arm", "post notification", "append", "send", "increment" are not.
- **Process death mid-undo.** Undo is a multi-step operation. Without durable progress, a kill leaves entity A restored, B not, and no record, so the next "Undo all" either double-applies A's compensator or refuses forever.
- **Double invocation.** The user taps "Undo all (3)" twice, or taps it while a new voice command is writing the same entities.
- **Lifetime.** An in-memory journal vanishes with the process. A voice app is routinely killed in the background, and an "Undo all" offered minutes later against a journal that survived but whose entities have since changed is a stale snapshot (Pitfall 17).
- **Privacy.** The journal holds snapshots of user data (note text, food entries): it must live in app-private storage, be purged on expiry, be excluded from logs, and have a redacted `toString` (the project's secrets constraint).

**How to avoid:**
- The journal entry is a small state machine with compare-and-set transitions: `PREPARED → COMMITTED → UNDOING(k) → UNDONE | PARTIAL(reasons)`. A second undo on `UNDONE` is a no-op returning the same result. On restart, `UNDOING` resumes or reports `PARTIAL`, never re-runs completed compensators.
- A per-journal mutex (and an app-supplied transaction hook around DB restores) so undo and a new command cannot interleave on one entity.
- A defined undo window (until the next command for that run group, or a TTL) and a `JournalStore` seam so the app persists the journal (Room), with an in-memory default documented as "does not survive process death".
- The module stays dependency-free (UNDO-01), so any persistence and coroutine locking must be written with the stdlib only (`suspend` needs no kotlinx dependency; a mutex does, so use an app-supplied serializer or `synchronized`/atomic CAS), or the roadmap's "depends on nothing" claim fails the module-graph gate. Decide this in the P17 discuss step.

**Warning signs:** a compensator interface without the word "idempotent" in its KDoc and a double-run test; undo state held in a local variable of the undo function; no test that kills the coroutine mid-undo.

**Phase to address:** P17 (owner). P19.

---

### Pitfall 19: Where the pipeline integration lives (`:undo` depends on nothing, `:core` must not depend on `:undo`)

**What goes wrong:**
UNDO-01 says `:undo` depends on nothing, "not even `:core`", and the module-graph gate proves it. UNDO-04 says the pipeline journals every committed action by `runId`. Those two cannot both be met by an edge in either direction: `:undo -> :core` breaks the first, `:core -> :undo` makes every `:core` consumer pull `:undo` and weakens the `:core` classpath allowlist. A tempting third option, a hidden coupling by reflection or a shared "bridge" published inside `:core` that mentions undo concepts, also breaks "domain-free".

**How to avoid:** use what v1.0.1 already provides. `runId`, `parentRunId`, `ActionEvent`, `ExecutedAction.position`, `PendingMutation.context` and `CommitSink` are all public. The bridge is a ~30-line `CommitSink` implementation in the app (shipped as documented glue and compiled by `:sample`/`DocSnippetsTest`, like every other doc snippet), or a clearly-named third artifact only if the discuss step decides the glue is too error-prone to leave to apps (that adds a module and plumbing, Pitfall 9). Record the decision in P17 CONTEXT before any code. Group by `runId` plus the `parentRunId` chain: a `commitHeld` child run and a clarification-reply run (`CommandInput.parentRunId`) belong to the original command's "Undo all (N)"; N counts committed actions only, and a pending held proposal must be shown as pending, not counted.

**Warning signs:** a `project(":core")` line in `undo/build.gradle.kts`; `undo` mentioned in `core/src/main`; "Undo all (3)" displayed while one proposal is still held.

**Phase to address:** P17 (decision in discuss/plan). P19 (doc snippet of the glue).

---

### Pitfall 20: A Wave-1 seam whose semantics are subtly wrong

**What goes wrong:** the seams are small, but each is a contract that SB and CT code against.
- **`onFailed`** returning `Completed` after a provider failure (pretending success) or `Escalate` after the run committed. The hook must be guarded (an exception falls back to the original `Failed` plus a trace code, never escapes) and `FailureDetails` must never gain a response body (bodies can echo transcripts or keys).
- **`Unhandled.cappedByPolicy`** must be exactly "at least one `tier_skipped_policy` **and** no tier handled it". `offline_unavailable` (the device is offline) and `on_device_unavailable` are different trace codes, so a device-offline run is `cappedByPolicy == false`. A test matrix of (each skip code) x (Unhandled, Failed, Completed) pins it. A run that ends `Failed` is a `CommandOutcome.Failed`, not `Unhandled`, so the flag does not exist there.
- **`carryIn`** is presence only; `NoMatch` clears the carry, so `carryIn` after a grammar `NoMatch` is false.
- **`providerCallId`** is null for zero-call tiers (grammar) and, for Plan, see Pitfall 14.
- **`ReasoningMode.OFF`** must produce today's exact bytes (goldens on all three providers). `PROVIDER_DEFAULT` with Anthropic thinking is incompatible with a forced tool choice (SB/CT noted the SingleShot+thinking conflict, R1), so the capability table must refuse it with the typed `capability_refused` outcome rather than letting the provider 400. Thinking blocks in multi-turn must be replayed intact (v1.0 Pitfall 7).
- **`claude-sonnet-5` row.** The Anthropic table matches ids **exactly** (the code comment says a dated, suffixed or differently cased id is not recognized), which protects `claude-sonnet-5-5` (forced tool choice rejected) from matching the new row. Keep it exact, add a test that `claude-sonnet-5-5` does not resolve to the `claude-sonnet-5` row, and read `minCacheablePrefixTokens` from Anthropic's docs at plan time (minimums are not monotonic across models).

**How to avoid:** KDoc each seam with its exact truth table; test the table, not the happy path.

**Phase to address:** P12 (owner). P14/P15/P16 (consume them).

---

### Pitfall 21: The W04 fix repeats its own bug class (family regexes and message matching)

**What goes wrong:**
W04 happened because `OpenAiModelRules.wireRules` maps the whole `GPT_6_FAMILY` regex (`^gpt-6(?:[.-].*)?$`) to `reasoning_effort: "none"`, and OpenAI's `gpt-6-astra` rejects `"none"` (the captured 400: `param=reasoning_effort code=unsupported_value`, no `v1/responses` text, so the message-marker classifier missed it). Fixing only the one id leaves the structure: any future `gpt-6.2` or new family member is assumed to accept `"none"` and the next live model repeats W04. And widening the classifier to "any `unsupported_value`" misreads other parameters.

**How to avoid:**
- **Invert the default.** Send `reasoning_effort` only when the table positively says the id accepts a specific value; an unknown or newly matched id sends nothing. Then `ReasoningMode.OFF` means "omit" where `"none"` is not known-good.
- A table-driven test enumerates every id the rules mention and every regex family with representative future ids, asserting the OFF wire value is either omitted or in that model's accepted set (and that no Responses-only id ever receives it).
- Classify on **structured fields** (`param == "reasoning_effort"` and `code == "unsupported_value"`) to `ModelUnsupported`, with the status guard, and keep the message-marker path for the other shape. Do not match on wording alone (that is what failed). Do not classify every `unsupported_value` as `ModelUnsupported`.
- PROV-16 is a live call: it uses the spend-capped key through the test-keys workflow, a fixed small request count, and logs the evidence line; per the v1.0 retrospective lesson, replay the engine's exact wire body from the host first (a two-call curl) before spending a device leg.

**Phase to address:** P12.

---

### Pitfall 22: Parallel phases, the host, and the release cut

**What goes wrong:**
The roadmap allows 14 ‖ 15 ‖ 17 ‖ 18 and 12 ‖ 13. On this host that means several Gradle daemons plus Kotlin daemons (and a possible AGP build for `:voice-adapter`) at once. The v1.0.1 cut was earlyoom-killed five times with full swap; `gradle.properties` caps each daemon at `-Xmx2048m` but the Kotlin daemon and test workers are on top. The v1.1.0 cut runs more modules through gate 9 (`check`), gate 13 (dry run) and the selftest sandboxes, and any other repo's daemon contends for the same memory. `./gradlew --stop` kills another repo's live daemon (retrospective lesson 2).

**How to avoid:** run at most two Gradle-using phases at once; never run the cut, a dry run or `check` alongside another build. Before any cut: check free swap, run in a quiet window, use a single-use daemon (`--no-daemon` or `org.gradle.daemon=false` via an environment override for that invocation), never `--stop`. Run `release-cut.sh preflight` well before P20 to learn gate failures early (it is non-irreversible). Keep the dry-run wall time on record (Pitfall 10).

**Phase to address:** P20 (owner). P17/P18 (their dry runs).

---

## Moderate Pitfalls

### Pitfall 23: Process friction carried over from v1.0
**What goes wrong:** (a) Every VERIFICATION fingerprints `REQUIREMENTS.md`, so ticking any checkbox stales every earlier phase's verification (retrospective, seeded to the technician). With nine phases, ticking requirements as each closes re-stales the previous ones. (b) After a `needs_human` pause (the PROV-16 live smoke, the spike verdict, Gate-1 on the TESTER, the SB/CT replies) the stage-marker barrier is advisory (INC-2026-09-08-02): `.gsd-stage-*.done.json` files and `.planning/milestone.lock` are untracked right now, and a resumed run can believe a stage is done or not done wrongly. (c) The release-cut `clean` gate rejects untracked files outside the orchestrator bookkeeping paths. (d) The milestone archive move dropped a gitignored `.log` (lesson 3).
**Prevention:** tick requirements in one batch at milestone close, or accept re-verification explicitly in the plan; after any pause, verify state by reading `STATE.md` and the phase artifacts, never by the barrier alone; before P20, decide the status of `.gsd/`, the stage markers and the lock (ignored or allowed by the cut's bookkeeping list) and run `preflight`; after the milestone archive, diff deleted versus re-added paths.
**Phase:** every phase (a, b); P20 (c); milestone close (d).

### Pitfall 24: The TESTER and the keys are shared resources
**What goes wrong:** P12 (PROV-16), P13 (spike) and P19 (Gate-1) all drive the wired TESTER; the spike can leave a 1+ GB model, a stale app install, or a thermally throttled device. The lock file (`vae-keystore-tester.lock`) is shared by the sample and keystore runners; the spike harness must use it too.
**Prevention:** the spike uses the same lock and `adb -s <serial>`; it removes the model from the device after the run; wait for the device to cool before the next phase's measurement; never the personal phone (`~/.claude/context/devices/common.md`); keys only through the test-keys workflow, with the request count bounded in the plan.
**Phase:** P12, P13, P19.

### Pitfall 25: Plan prompt, schema and cache economics
**What goes wrong:** a plan schema that carries every tool's argument shape makes the prefix large and the plan-call output long. Provider strict modes restrict nested schemas (no recursive refs, limited `anyOf`, `additionalProperties:false` everywhere), so a generic `steps[].arguments: object` loses per-tool validation and the engine must validate against the `ToolSpec` itself. The plan call and the replan call should share an identical tools+system prefix (cache hit) with only the tail differing; moving anything dynamic into the prefix repeats v1.0 Pitfall 6. The forced-tool 400 on current Anthropic models (v1.0 Pitfall 5) applies to Plan too.
**Prevention:** reuse the capability and `strict` machinery from SingleShot; byte-identical prefix test between the plan and replan requests; schema size checked against `maxTokensPerTurn`.
**Phase:** P15.

### Pitfall 26: Secrets and user content on the new surfaces
**What goes wrong:** new types hold transcript fragments (grammar slot text, plan arguments, router input, journal snapshots). A `toString`, an exception message, a trace field or a `PipelineEvent` that includes them leaks what v1.0 worked to keep out of every sink. The `normalize` hook receives raw slot text from the transcript.
**Prevention:** every new public type gets a redacted `toString` (counts, lengths and class names only), new trace codes carry no payload, and the existing leak-scan and `ForbiddenImport`/`ForbiddenMethodCall` detekt rules cover the new modules (they are applied per module: confirm `undo` and `voice-adapter` get the same `detekt` wiring, config and zero baseline, Pitfall 9). Add a leak fixture per new module to the negative controls.
**Phase:** P14-P18.

### Pitfall 27: Docs and wiring test lag the code
**What goes wrong:** DOC-02 requires an agent to wire grammar, plan, router and undo-all from the docs alone. v1.0's isolated wiring test caught real stumbles. Every Kotlin block in the docs is a byte-equal copy of `DocSnippetsTest`, so a doc that describes the new surface needs new compiled regions, and `verify-docs-coverage.sh`'s fixed region list and its three-module scan (Pitfall 9) will not demand them.
**Prevention:** add the new regions and coverage checks in each feature phase, not at P19; keep the wiring test's coordinate regexes in the module list; re-run the isolated wiring test on the final SHA.
**Phase:** P14-P18 (regions), P19, P20.

---

## Minor Pitfalls

### Pitfall 28: `ReasoningMode` naming and docs
`OFF` means "the engine sends its pinned default", which on OpenAI after Pitfall 21 may be "omit" rather than literally "none". Name and KDoc it as "engine default", not "no reasoning", or SB will assume reasoning is disabled when the model is merely using its own default.

### Pitfall 29: Test fixtures that are not representative
Fake-provider tests pass for the grammar/Plan/Router logic but do not see real-model malformed plans, real-STT punctuation, or real number formats. Include recorded real STT transcripts (anonymized, no secrets) as fixtures in the P14 corpus, and a few real planning responses captured from the cheap model in P15.

### Pitfall 30: Stale ECOSYSTEM and pin documentation
`ECOSYSTEM.md` says the two new modules are "Planned for v1.1, not yet published" and has machine-reconciled pin rows. Update the planned text and the coordinates table at the cut, and leave the repin-matrix markers to the tooling.

---

## Carried forward from v1.0 (still live; see git history for the full text)

| v1.0 pitfall | Why it matters in v1.1 | Phase to re-check |
|---|---|---|
| 4 OkHttp 4.12/5.x matrix proves A1 | Any new HTTP use (the router call, plan calls) stays in `:providers` on the 4.12 API; `:voice-adapter` must not pull a forced OkHttp | P16, P18 |
| 5 Forced tool call 400s on current Anthropic models | Plan and Router both rely on a forced tool or a constrained value | P15, P16 |
| 6 Silent cache invalidation | Plan/replan prefix identity; router prompt is a separate small request (below the cache minimum, expected) | P15, P16 |
| 9 Mutations duplicated by escalation / held reported as success | Grammar, Plan and undo all interact with `hasWorked()`; a held grammar action is held, never success | P14, P15 |
| 10 Cancellation swallowed | The picker, the `normalize` hook, `onFailed`, compensators: app code that must let cancellation propagate | P12, P14, P16, P17 |
| 12 "Additive" API that breaks consumers | Pitfalls 1-4 above are this, specialized | P12-P18 |
| 13 Offline-only leaks to cloud / wrong key sent | The router call and any on-device fallback | P13, P16 |
| 14 Secrets via `toString`/exceptions/trace | Pitfall 26 | P14-P18 |
| 15 Zero-baseline detekt cleaned by skipped rules | New modules need the same config, no baseline, no new suppressions | P17, P18 |

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Add `= default` to an existing constructor | One line | Removed JVM constructor and changed `$default` descriptor, invisible to Metalava | Never on a published class |
| `apiDump` to turn a red build green | Fast green | Baseline silently rewritten, break ships | Only as a reviewed `+`-only diff |
| Copy-paste the module list into one more script | Quick | Silent skip of the next module | Never; read `modules.list` |
| Fuzzy/"contains" grammar matching | Higher hit rate | Silent wrong writes | Never |
| Snapshot in `prepare` | Simple adapter | Stale restore after a hold | Never |
| In-memory journal as the only store | No persistence code | Undo lost on process death | Only as a documented default beside a `JournalStore` seam |
| `api(stt)` in the adapter | One line | Forces `:stt` graph and version on apps | Never; `compileOnly` |
| Skipping the cold-start row in the spike | Faster spike | Verdict is wrong for real use | Never |
| Putting the model in a Gradle module "just for the spike" | Easy install | Un-pushable repo, JitPack timeout | Never; `adb push` |
| A Router that always runs | Simpler walk | Latency/cost on every command | Never; skip when nothing to choose |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| JitPack, new module | Default artifactId; trusting filename greps | Explicit artifactId; read POM XML in the dry run; live-probe by SHA |
| JitPack, `:stt` dependency | Plain aggregator coordinate; unfiltered jitpack.io repo | `com.github.Ygaray.voice-engine-android:voice-engine-android:v0.7.0`, repo filtered by `includeGroup` |
| `:stt` final segment | Mapping `en-US` to `en`, defaulting `null`, caching language | Bare `en`/`es` or `null`, per segment, no state |
| SB/CT as consumers | Assuming they will recompile against changed constructors | Keep every v1.0.1 descriptor; they repin to `v1.1.0` and must not need source changes except opt-in |
| Anthropic `claude-sonnet-5` | Prefix match swallowing `claude-sonnet-5-5` | Exact-match row, test the neighbor id |
| OpenAI `reasoning_effort` | Family regex assumes `"none"` is valid | Positive allow-table; omit when unknown |
| Vendor on-device AAR | Trusting it is 16 KB aligned and Kotlin-compatible | Verify with `llvm-objdump` and the metadata version before adopting |
| App Room DB, undo | Snapshot without FK-cascade children | Adapter declares footprint; cascade fixture test |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Router call on every command | Added 300-1500 ms before any work, extra cost | Skip when fewer than two eligible tiers; short timeout; grammar first | Every command |
| Regex-based grammar on long transcripts | UI hang / ANR from backtracking | Linear token matcher, token cap | One dictated paragraph |
| Cold model load | Multi-second first on-device command | Lazy load, show state, measure cold | First use / after process kill |
| Thermal throttling | Latency doubles after repeated on-device runs | Measure sustained; consider a cooldown policy | After ~2 minutes of use |
| Large plan prefix | Cache miss or sub-minimum prefix, slow plan | Byte-stable prefix, schema size budget | Many tools / long plans |
| JitPack build time | Build killed at 15 min, tag burned | Measure dry run; publish-only install list | Adding modules/AARs |
| Parallel Gradle builds on the host | earlyoom kills the build | At most two at once; quiet window for the cut | Cut and dry runs |

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Journal snapshots in logs or exceptions | User data leak | Redacted `toString`, app-private store, TTL purge |
| `KeyAccess` implemented in production | Raw key exposure | `@RequiresOptIn(ERROR)`, only the gated constructor accepts it, redacted `toString` |
| Unfiltered jitpack.io repository | Dependency confusion | `content { includeGroup(...) }` filter |
| Transcript text in trace/telemetry (grammar slots, router input, plan args) | Privacy | Counts and codes only; leak fixtures |
| Redistributing Gemma weights without terms | License breach | Gemma Terms of Use: give recipients the Agreement, ship the notice text file, flow down the Prohibited Use Policy restrictions in the app's terms; verify the license of the exact model chosen at spike time (terms differ by model family) |
| Router prompt injection | Cost only, if the router cannot write | Closed enum output validated against eligible ids; never arguments |
| On-device model auto-selected as a silent fallback | Quality and privacy change unseen | Explicit in trace (`fallbackFrom`); app opt-in |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| "Undo all (3)" while one proposal is still held | Wrong count, partial surprise | Count committed only; show pending separately |
| Undo refusal with no reason | Feels broken | Return the exact entities and why; make failures loud |
| Partial plan reported as done | User thinks everything happened | Partial `Completed` rendered as partial, with which steps ran |
| Cold on-device model with no feedback | Looks hung | App shows a loading state; library exposes the state |
| `cappedByPolicy` rendered as "no network" | Wrong guidance | Distinguish policy cap, device offline, and no match |
| Grammar miss that feels slow | Cheap tier is silent, then cloud delay | Trace shows `no_match`; apps may show "thinking" on handover |

## "Looks Done But Isn't" Checklist

- [ ] **Additive API:** `apiCheck` green, but is the `javap` descriptor diff against the v1.0.1 jar empty of removals? Are there `-` lines in any baseline `api.txt`?
- [ ] **New module plumbing:** the dry run passes, but do `verify-api-dump`, `verify-docs-coverage`, `verify-repo-hygiene` and the negative controls actually iterate the new module (planted-module control)?
- [ ] **Release gate 15:** does it assert `voice-adapter -> core` at the tag version and the new modules' metadata?
- [ ] **Grammar:** is there a near-miss corpus (negation, extra clause, wrong-language word, tilde word, partial number) that all returns `NoMatch`? Does the number parser round-trip 0-999,999 in both languages?
- [ ] **Grammar zero-call:** the no-network guard proves zero provider calls and `providerCallId == null`; the held grammar action is held, not success.
- [ ] **Plan:** exact model-call counts asserted in every path; a binding to a held/errored step fails; post-commit failure makes no replan call; `onFailed` provider-failures-only.
- [ ] **Router:** off by default is trace-identical to v1.0.1 Linear (characterization test); no call when fewer than two eligible tiers, offline-only, or empty `allowedProviders`; picker timeout falls back loudly; router tokens in the trace total.
- [ ] **Undo:** a test edits the entity between commit and undo and expects refusal; a held change committed later restores correctly; double "Undo all" is a no-op; process-kill mid-undo resumes; FK cascade restores children.
- [ ] **Undo module graph:** `undo/build.gradle.kts` has no `project(...)` and no third-party dependency; the module-graph gate proves it.
- [ ] **Voice adapter:** `:core`/`:providers`/`:keystore` graphs contain no `voice-engine-android`; the adapter's POM does not either (compileOnly); `en-US` and unknown labels give `null`.
- [ ] **On-device spike:** verdict has cold/warm/sustained rows, PSS, semantic accuracy with trial counts, Spanish and negative prompts, APK/AAR delta, 16 KB and ABI results, license note; no model file in git.
- [ ] **Seams:** `cappedByPolicy` truth table; `ReasoningMode.OFF` goldens on three providers; `claude-sonnet-5` exact row plus neighbor test; W04 allow-table test over every rules id.
- [ ] **Opt-in:** a compile-fail control proves `ApiKeyStore(ds, slots, keyAccess)` without `@OptIn` does not compile.
- [ ] **Docs:** new compiled doc regions exist for every new tier/seam/module; the isolated wiring test passes on the final SHA.
- [ ] **Cut hygiene:** `.gsd/`, stage markers and `milestone.lock` are accounted for in the `clean` gate; swap checked; single-use daemon.

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Removed constructor shipped in `v1.1.0` | HIGH | New patch tag re-adding the descriptor, superseded ledger row; consumers repin (tags are immutable) |
| Burned tag from a failed JitPack build (wrong coordinate, timeout) | MEDIUM | Fix, new patch tag, superseded row; avoid by dry run plus live probe on a SHA first |
| Grammar false positive found in a consumer | MEDIUM | App-side: drop the rule; engine: add the failing utterance to the near-miss corpus, tighten, patch tag |
| Undo wrote a wrong restore | HIGH | Disable "Undo all" in the consumer, ship a fix with a stricter fingerprint; patch tag; journal entries remain readable by version |
| Binding resolved to the wrong id | MEDIUM | Patch tag tightening the binding validation; consumers keep the gate on |
| Spike verdict wrong (red but really green, or the reverse) | LOW | Verdict messages are cheap to reissue; on-device ships behind an opt-in marker, so a wrong green is containable |
| Build OOM during the cut | LOW | Free swap, quiet window, single-use daemon, rerun (no tag was created before the final gate) |

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| 1 Constructor growth / value classes | P12 (P14-P18 design) | `javap` descriptor diff vs v1.0.1 jar has no removals; `ApiShapeTest` |
| 2 Baseline overwritten | P12 (every phase) | `verify-api-additive.sh` zero `-` lines per phase; gate 12 at P20 |
| 3 Sealed/abstract growth | P12, P15, P16, P17 | No new subclass of a sealed parent in `api.txt`; interfaces only gain default bodies |
| 4 Opt-in gating | P12 (P13) | Compile-fail negative control; reflection assertion on the marker |
| 5 Freezing a wrong shape | P14-P18, P19 | Per-phase frozen-surface review; P19 consolidated review |
| 6 On-device packaging / runtime choice | P13 | Verdict lists runtime, format, sizes, 16 KB, ABI, metadata, license; no model in git |
| 7 On-device RAM / thermal / cold | P13 | Cold/warm/sustained rows, PSS, thresholds set before the run |
| 8 JSON validity vs correctness | P13 | Semantic accuracy with counts, ES and negative prompts |
| 9 Hard-wired module list | P17 (P18, P13 rebase, P20 selftest) | `modules.list` consistency gate; planted-module negative control |
| 10 JitPack coordinates / POMs / build time | P17, P18, P20 | Dry run reads POM XML; live probe by SHA; wall-time recorded; clean-cache resolution |
| 11 Adapter direction and pinning | P18 (P20) | Graph gates; adapter POM free of `:stt`; `compileOnly`; language tests |
| 12 Grammar guessing | P14 | Near-miss corpus all `NoMatch`; ambiguity tests; build-time pack self-check |
| 13 Bilingual STT text | P14 (P18, P19) | Number round-trip 0-999,999 both languages; tilde/homograph/punctuation fixtures; null-label tests |
| 14 Plan binding | P15 (P12, P17) | Held/errored/forward/self reference tests; whole-plan pre-validation; `providerCallId` KDoc |
| 15 Replan, bounds, lookup | P15 (P12) | Model-call counts asserted; post-commit no replan; deterministic lookup rule |
| 16 Router cost/latency/misroute | P16 (P12) | Linear characterization test; no-call matrix; timeout fallback; honest telemetry |
| 17 Undo timing / clobber | P17 (P15, P19) | Later-edit refusal; held-then-commit; cascade fixture; two-phase verify |
| 18 Undo idempotence / durability / concurrency | P17 (P19) | Double-undo no-op; kill-mid-undo resume; compensator double-run |
| 19 Pipeline integration location | P17 | Module-graph gate; CONTEXT records the decision; glue compiled in `DocSnippetsTest` |
| 20 Seam semantics | P12 | Truth-table tests per seam; goldens; neighbor-id test |
| 21 W04 bug class | P12 | Allow-table test over all rules ids; structured 400 classification; live smoke evidence |
| 22 Parallel / host / cut | P20 (P17, P18) | Swap check, quiet window, single-use daemon, `preflight` early |
| 23 Process friction | every phase / P20 / close | State read, not barrier; batch requirement ticks; archive path diff |
| 24 TESTER / keys | P12, P13, P19 | Shared lock; model removed; bounded requests |
| 25 Plan prompt/cache | P15 | Byte-identical plan/replan prefix test |
| 26 Secrets on new surfaces | P14-P18 | Leak fixtures per module; detekt applied to new modules |
| 27 Docs lag | P14-P18, P19, P20 | New regions per phase; coverage script covers new roots; wiring test on final SHA |

## Open Questions (for the discuss steps; not resolvable by research)

1. **P15:** is a replan allowed after any commit? Recommended no (Pitfall 15), which also resolves the SC3/SC4 tension. What does a held step do to the rest of the plan (end partial, as recommended) and is whole-plan gating something SB wants (ask via the orchestrator)?
2. **P17:** where does the `CommitSink` glue live (app code compiled in docs, recommended) versus a third artifact, and does the journal need a persistence seam in v1.1?
3. **P14:** which slot types ship (recommend integer, decimal, choice, bounded free text; no dates/times) and whether `normalize` stays a bare lambda type.
4. **P13:** which runtime/model to test (recommend LiteRT-LM plus the current ~2B model, and the Gemma-2B in the roadmap as a comparison row), and the numeric thresholds, fixed before the run.
5. **P18:** `FinalSegment` constructor visibility in `:stt` v0.7.0 and its minSdk, to settle test fakes and the adapter's minSdk (ask stt-engine-46 through the orchestrator).
6. **P12:** the exact Anthropic minimum cacheable prefix for `claude-sonnet-5`, and the list of `OpenAiModelRules` ids that reject `"none"` (the allow-table makes the second list unnecessary for correctness).

## Sources

- Repo (HIGH, read 2026-10-05): `.planning/PROJECT.md`, `ROADMAP.md`, `REQUIREMENTS.md`, `RETROSPECTIVE.md`, `.planning/cross-repo/RECONVENE-BRIEF-R-v1.1.md`, `.planning/releases/v1.0-close/W04-host-wording-check.txt`; `core/api.txt` (sealed list, `default` interface members, four `ModelRequest` constructors); `core/.../pipeline/TierWalk.kt` (`hasWorked`, `startFresh`, `suppressed`), `TierSelector.kt`, `TierPolicy.kt` (advisory ceilings), `commit/CommitSink.kt`, `commit/ToolStep.kt` (`PendingMutation.context`, held-apply warning), `telemetry/TraceCode.kt`; `providers/.../chat/OpenAiModelRules.kt`, `ChatErrors.kt`, `anthropic/AnthropicModels.kt` (exact-id table); `jitpack.yml`, `settings.gradle.kts`, `build.gradle.kts` (`verifyApiDumpPresent`), `scripts/*.sh` (hard-coded module lists, gate list in `release-cut.sh`), `ECOSYSTEM.md`, `CROSS-REPO-SCOPE-CONTRACT.md` (L7, A15, A18, E5, E7, §11 `:stt` v0.7.0 row).
- v1.0 commits `ca986c1`, `11fdb0f` and `.planning/milestones/v1.0-phases/11-cut-v1-0-0/11-CONTEXT.md` (default-argument constructor precedent).
- [JitPack FAQ](https://docs.jitpack.io/faq/) (MEDIUM): 15-minute build timeout, public artifacts immutable after 7 days; no size limit documented.
- [Google Play app size limits](https://support.google.com/googleplay/android-developer/answer/9859372) (MEDIUM): base module 500 MB, asset pack 1.5 GB, install-time packs 4 GB cumulative.
- [Android: 16 KB page size support](https://developer.android.com/guide/practices/page-sizes) (MEDIUM): ELF alignment requirement and the stated Google Play deadline (February 1, 2027); re-verify at plan time.
- [Google AI Edge: LLM Inference guide](https://developers.google.com/edge/mediapipe/solutions/genai/llm_inference) (MEDIUM): MediaPipe LLM Inference in maintenance mode; migrate to LiteRT-LM.
- [LiteRT-LM on Android](https://developers.google.com/edge/litert-lm/android) and [github.com/google-ai-edge/LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM) (MEDIUM): `com.google.ai.edge.litertlm:litertlm-android`, Kotlin API stable, tool use, `.litertlm` models.
- [Gemma Terms of Use](https://ai.google.dev/gemma/terms) (MEDIUM): redistribution requires the Agreement, a notice file and flow-down of the Prohibited Use Policy; check the exact model's license.
- Public write-ups on grammar-constrained decoding and mobile thermal throttling (LOW-MEDIUM, secondary): small models fail strict schemas without constrained decoding; sustained inference throttles clocks. Treated as motivation for measuring, not as numbers to rely on.
- Domain knowledge (MEDIUM): Spanish numeral morphology, `ñ`/diacritic folding, STT punctuation and number formatting. Not measured against this project's STT; the P14 fixtures should be built from real `:stt` transcripts.

---
*Pitfalls research for: voice-action-engine v1.1 (additive features on a frozen, published multi-module Kotlin/JitPack library)*
*Researched: 2026-10-05*
