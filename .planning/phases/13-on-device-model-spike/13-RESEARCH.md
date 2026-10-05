# Phase 13: On-Device Model Spike - Research

**Researched:** 2026-10-05
**Domain:** Android on-device LLM runtime (LiteRT-LM + Gemma 4 E2B) measured on a Galaxy S22 Ultra TESTER, behind the engine's existing `ON_DEVICE` seam
**Confidence:** HIGH for toolchain, licensing, alignment, repo seams and gate mechanics (all read or run this session). MEDIUM for runtime API behavior (read from bytecode, never executed). LOW for every performance number (nothing has run on an S22; that is what the spike produces).

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01 [model]:** Gemma 4 E2B (Apache-2.0, ungated) primary on LiteRT-LM 0.17.1, Gemma 3 1B as a light-RAM control on the small envelope only (its 4k KV can't hold the ~7k SB prefix). Verdict states "Gemma-2B" resolved to Gemma 4 E2B on LiteRT-LM (MediaPipe is maintenance-only). _(source: ai-auto)_
- **D-02 [toolchain]:** Standalone compile/dex proof first; pin 0.16.1 on failure; both fail → red ("toolchain"). _(source: ai-auto)_
- **D-03 [harness]:** Separate unpublished spike module, so a LiteRT compile failure can't break `:sample` (needed by Phase 12's PROV-16 smoke in parallel) and gate edits don't collide. Note: run-sample-gate1.sh `PHASE_DIR` still points at the archived `.planning/phases/10-*` path — fix before any :sample run. _(source: ai-auto)_
- **D-04 [path]:** Engine path (SingleShot + ON_DEVICE provider), Route A `ResponseFormat.json` and Route B native tool calls measured side by side. _(source: ai-auto)_
- **D-05 [envelopes]:** Both envelopes; per-envelope verdict (CT reads small, SB 179 reads SB-sized). _(source: ai-auto)_
- **D-06 [accuracy]:** Full gold set (PITFALLS #8 false-green trap). SB-fixture gold labels stay private. _(source: ai-auto)_
- **D-07 [thresholds]:** The voice-usable bar (anchored to the v1.0 cloud SingleShot baseline on the same TESTER: Haiku 612–1,391 ms, gpt-5.4-mini 1,946 ms). Time-box expiry with a gating metric unmeasured → red. _(source: human)_
- **D-08 [ship]:** The `:ondevice` shape above with the documented app-side gating pattern (no core API change); ships in 13 only if it fits the time-box, else 13.1; rebases onto Phase 17 plumbing. Red → nothing ships, SPIKE-03 N/A-deferred. _(source: ai-auto)_
- **D-09 [sc4-gates]:** Minimal hardening (tokens + :providers deny + hygiene patterns). _(source: ai-auto)_
- **D-10 [external]:** Plan-phase research for docs-answerable items (license, alignment, stdlib fallout); empirical for KV reuse, ResponseFormat and GPU — each a measured row in the verdict. _(source: ai-auto)_

### Claude's Discretion

Areas marked `ai-auto` took research's recommendation without operator review; the planner may refine mechanics within the stated decision but must not reverse it without a new discuss pass.

(Operator-reviewed, treat as locked: D-07 [thresholds].)

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope. (Also out of scope per ROADMAP/REQUIREMENTS: agentic on-device, Gemini Nano / AICore = LATER-06, MediaPipe LLM Inference, any change to `:core`'s public API.)
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SPIKE-01 | a bundled Gemma-2B-class model (MediaPipe/LiteRT) is measured on the TESTER: latency, RAM and strict-JSON reliability on SingleShot-shaped prompts. | Toolchain proof (Section "Docs-answerable facts"), runtime API map, measurement ladder with early-exit rules, scoring math (Wilson table), harness shape, device-window protocol |
| SPIKE-02 | the verdict (green/red, with numbers) is messaged to the orchestrator early, before the SB 179 and CT 75 planning needs it. | Verdict-as-code design (reproducible from evidence), relay precedent from Phase 12 (12-08 / RT-01), interim early-red path |
| SPIKE-03 | if green, the on-device provider ships `@Experimental` in its own module behind the `ON_DEVICE` capability gate. If red, nothing ships and the tag isn't blocked (L10). | `:ondevice` shape, shared-plumbing facts (settings, invariants, jitpack, api.txt), red-path deletion checklist, SC4 hardening design |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

Extracted from `./.claude/CLAUDE.md` and `~/.claude/CLAUDE.md`. The planner must verify compliance.

- **Dependency structure:** `:core` depends on no other hub and no HTTP (L7, A7); enforced by classpath allowlist (`verifyCoreDependencyAllowlist`). No ML dependency may enter `:core`/`:providers` (ROADMAP SC4).
- **Toolchain pins (do not move unilaterally):** Kotlin 2.3.20, AGP 9.2.1, Gradle 9.4.1, JDK 17 host, JVM 11 bytecode on published modules, OkHttp 4.12 floor. `scripts/verify-repo-hygiene.sh` section g asserts these pins.
- **API evolution:** strictly additive once tagged. The spike adds no public API to `:core`/`:providers`/`:keystore`.
- **Domain-free library:** published code names no note/card/food and no app. App tool surfaces enter only through §5.2 seams. The SB fixture and its gold labels never enter a committed file.
- **Secrets / privacy:** keys, transcripts, tool args/results never reach logs, telemetry, exceptions or `toString()`. Spike evidence therefore uses a closed vocabulary and an allow-list filter (no prompt, transcript or model output in any committed or logged line).
- **Quality gates:** detekt zero baseline on library modules; most of `:core` JVM-tested; two-gate UAT where device-verifiable.
- **Process:** contract changes only via §10 amendments through the control plane; the orchestrator is the only writer of the §11 ledger; message it, never commit ledger rows.
- **GSD workflow:** file changes go through a GSD command. Planner directive from `two-gate-uat.md`: do not emit a hand-written self-UAT task or a per-phase blocking human-verify checkpoint for device-verifiable criteria; Gate-1 is the `verify_work_agentic_gate`. (See Pitfall 14 for how this interacts with the TESTER window.)
- **Devices (user global):** TESTER = `…-s22-ultra-2`, USB serial `R5CT10XNKQN`, never the personal phone (`100.126.94.47`); always `adb -s`; read `~/.claude/context/devices/common.md` first; never drive a device while it is being tested on; one agent device-tester at a time; never substitute a device; airplane mode is allowed on the wired TESTER only (and drops wifi/tailscale/wireless adb, which must be re-armed over USB).
- **Host memory (user memory + STATE):** host is memory-tight (earlyoom, swap full at research time). Cap Gradle at 2 concurrent Gradle-running plans per wave and use the low-memory GRADLE_OPTS recipe (see Environment Availability).
- **Tailnet URLs:** any doc handed to Yahir goes through `render-doc-for-review`; never a bare localhost URL.

## Summary

The spike's toolchain question (D-02) is already answered on the host, and the answer is better than the milestone research feared. The exact compiler the build uses (`kotlin-compiler-embeddable` 2.3.20, from the Gradle cache) compiles a probe and a full `AiProvider` skeleton against `litertlm-android` 0.17.1 with exit 0 and zero diagnostics at `-jvm-target 11`, and Android SDK D8 8.10.9 dexes both 0.17.1 and 0.16.1 at `--min-api 35`. 0.17.1's classes carry Kotlin metadata 2.4.0 and class-file major 65 (Java 21); 2.3.20 reads exactly one minor ahead, so this works but sits on the edge: a Kotlin 2.2.10 compiler rejects the same jar ("compiler version 2.2.0 can read versions up to 2.3.0"). 0.16.1 is metadata 2.3.0 (readable natively) but still Java 21. Both AARs' arm64-v8a and x86_64 `.so` are 16 KB aligned (`LOAD` align `0x4000`). The remaining toolchain risk is therefore not "does it compile" but (a) consumer runtime fallout of 0.17.1's `kotlin-reflect:2.4.0` (which pulls `kotlin-stdlib:2.4.0` onto consumers' runtime classpath, whereas 0.16.1's `kotlin-reflect:2.2.21` pulls nothing above their 2.3.20) and (b) pinning the exact version forever, since a LiteRT-LM release built with Kotlin 2.5 metadata would break the build.

The hard part is the measurement, and the thresholds in D-07 interact in ways the plan must respect. The SB-sized envelope is an 18-tool, ~7k-token prefix (v1.0 measured `cache_creation_input_tokens=7016`). LiteRT-LM exposes no documented or API-visible way to clone a KV prefix across `Conversation`s (docs are silent; the only hook in the API is `ConversationConfig.prefillPrefaceOnInit`), so every SingleShot call may re-prefill ~7k tokens. Google's S26 Ultra card numbers (CPU prefill 557 tok/s, GPU 3,808 tok/s, 2,048-token context) imply seconds of prefill alone for the SB envelope before any decode, and the S22 (Snapdragon 8 Gen 1) is an older SoC with no prebuilt NPU file. The ladder must therefore be ordered so the gating, cheapest-to-falsify rows (device preflight, prefill throughput at 1k/4k/7k, KV-reuse probe, GPU init) run first, with explicit early-exit-to-red rules, and with a screen-then-confirm structure so the 12-cell matrix (model x backend x route x envelope) does not consume the window. The three D-10 empirical items are KV-prefix reuse, `ResponseFormat.json` schema-feature support (including whether the constraint is actually enforced), and Adreno 730 OpenCL GPU init; each becomes a named verdict row with a pass/fail/unknown disposition.

Repo-side, the seams are all in place and small: `ON_DEVICE` is a `ProviderId` value, `OnDeviceCapability` is a `fun interface` defaulting to `Unavailable("not_implemented")`, `AiProvider` has a three-member contract, and the pipeline builder takes `onDevice`. The SC4 enforcement D-09 refers to today is a single `:core` unit test (`NoHardCodedConstantsTest.noOnDeviceImplementationCode`, tokens `aicore`/`mlkit`/`nano`) that scans only `:core/src/main`. `:providers` and `:keystore` have no such scan, and the shared scanner/detekt configuration applies to every published module, so naively adding LiteRT tokens there would later block the green-path `:ondevice` module. Add the ML denial per module, not globally.

**Primary recommendation:** Run the spike as a separate unpublished `:spike-ondevice` Android application module (litertlm `0.17.1`, exact pin, `arm64-v8a` only) whose harness is a thin throwaway `AiProvider` over the real `SingleShotStrategy`, with the verdict computed by a unit-tested pure-Kotlin rule engine from a closed-vocabulary evidence file (so SPIKE-02's numbers are reproducible without the device), the thresholds locked in a committed file before any device step, one non-autonomous TESTER checkpoint modeled on Phase 12's 12-08, at most two Gradle-running plans per wave, and the D-09 hardening scoped to `core`/`providers`/`keystore` only.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Local LLM inference (weights, KV, decode) | On-device native runtime (`liblitertlm_jni.so`, LiteRT-LM) | Android process (JVM wrapper) | Pure native; the Kotlin API is a JNI shell. A native crash kills the host app, not just the call. |
| Model weights delivery | App / host tooling (spike: `adb push`; production: app download-on-demand) | — | Weights are 2.0 to 2.6 GB; they must never be in a Gradle module, AAR or git. LiteRT-LM needs a filesystem path. |
| `ON_DEVICE` provider adapter (neutral request ↔ LiteRT-LM) | Spike module now; `:ondevice` library if green | `:core` (`AiProvider` contract) | The adapter owns the LiteRT types; no LiteRT type may reach a public API (`implementation`, never `api`). |
| Availability / capability gate | `:core` seam (`OnDeviceCapability`), implemented by the app or `:ondevice` | App (decides the declared fallback) | One gate shared by the policy pre-check and the router; the default is "unavailable". |
| Tier selection, SingleShot semantics, commit gating | `:core` (unchanged) | App (gate + sink) | The spike must exercise the real path, not a re-implementation. Writes stay behind the app's `PreApplyGate`. |
| Write gating for on-device-originated proposals | App (tag via `PendingMutation.context`, per D-08) | — | No core API change in v1.1; documented pattern only. |
| Measurement orchestration (preflight, push, run, capture) | Host script (guarded runner) | TESTER (device) | Same guard model as `run-sample-gate1.sh`: TESTER-only, lock, identity proof, closed-vocabulary evidence. |
| Metric collection (latency, tokens, PSS, thermal, exit reasons) | In-app (Android APIs + `BenchmarkInfo`) | Host (`adb dumpsys` cross-check) | PSS and thermal are cheapest and most accurate in-process; the host sees only process-level facts. |
| Verdict computation | Host JVM (pure Kotlin, unit-tested) | — | Numbers → green/red must be a deterministic function of the committed evidence and the locked thresholds, not prose. |
| SC4 enforcement ("no ML in `:core`/`:providers`") | Gradle invariants + `:core` unit test + hygiene script | Negative-control script | Mechanical, per module, with plants proving each gate goes red. |

## Standard Stack

### Core

| Library / artifact | Version | Purpose | Why Standard |
|--------------------|---------|---------|--------------|
| `com.google.ai.edge.litertlm:litertlm-android` | **0.17.1** (Google Maven `lastUpdated` 20260916171957; `latest`=`release`=0.17.1) | On-device LLM runtime for the spike harness (and `:ondevice` if green) | Google's successor to MediaPipe LLM Inference (maintenance-only); only runtime that loads Gemma 4 `.litertlm`. `[VERIFIED: https://dl.google.com/android/maven2/.../litertlm-android/maven-metadata.xml, fetched 2026-10-05]` |
| Same artifact, fallback pin | **0.16.1** | D-02 fallback | Same `Engine`/`Conversation`/`ResponseFormat`/`ConversationConfig` surface (diffed from `javap`); metadata 2.3.0; `kotlin-reflect:2.2.21`. Lacks `Capabilities.supportsFunctionCalling()` and the embedding classes. `[VERIFIED: local AAR + javap diff, this session]` |
| Gemma 4 E2B `gemma-4-E2B-it.litertlm` | 2,588,147,712 B, sha256 `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c` | Primary model (CPU path and generic) | Apache-2.0, ungated; repo revision `b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1` (lastModified 2026-08-31). `[VERIFIED: huggingface.co/api/models/litert-community/gemma-4-E2B-it-litert-lm, this session]` |
| Gemma 4 E2B `gemma-4-E2B-it-gpu.litertlm` | 2,008,432,640 B, sha256 `a53a59001894c58e6bdb5b9b227709f91a2e3e556baa7d85acf9c55402ba5cf5` | GPU-path variant (smaller) | Same license; pick whichever loads on Adreno 730. `[VERIFIED: same API]` |
| Gemma 3 1B `Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm` | 584,417,280 B (sha256 masked until HF terms accepted) | Light-RAM control, small envelope only (D-01) | `ekv4096` = 4,096-token KV, which cannot hold the ~7k SB prefix. **Gemma Terms of Use, auto-gated on HF.** `[VERIFIED: HF API tree + tags, this session]` |
| kotlinx-serialization-json | 1.11.0 (repo pin) | Parse Route A output; tool schemas are already `JsonObject` | Existing project pin. |
| kotlinx-coroutines-core | 1.11.0 (repo pin) | Bridge blocking `sendMessage` onto a dedicated dispatcher | Existing project pin. |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| JUnit | 4.13.2 (repo pin) | Spike module JVM unit tests (scoring, verdict rules, mappers) | Always. |
| kotlinx-coroutines-test | 1.11.0 (repo pin) | `runTest` for provider/runner logic over a fake backend | Always. |
| `androidx.test` runner / ext-junit | 1.7.0 / 1.3.0 (repo pins) | Only if the harness is run as instrumentation | Optional; an `Activity`-driven harness needs neither. |
| Transitive of litertlm 0.17.1 | gson 2.14.0, kotlin-reflect 2.4.0, kotlinx-coroutines-android 1.11.0 | Pulled in by the AAR POM (`compile` scope) | Do not add yourself. `[VERIFIED: litertlm-android-0.17.1.pom]` |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| LiteRT-LM 0.17.1 | 0.16.1 | 0.16.1 forces nothing above Kotlin 2.3.20 on consumers and reads natively, but 0.17.0 notes a local-attention memory optimization and 0.17.1 a tool-call integer-type fix (both `[CITED: github.com/google-ai-edge/LiteRT-LM/releases, via page summarizer, MEDIUM]`), both relevant to RAM and Route B. Measure 0.17.1 first (D-01/D-02); if green, the planner may still choose 0.16.1 as the shipping pin to avoid the stdlib 2.4.0 uplift. |
| `ResponseFormat.json` (Route A) | native tool calls (Route B) | Measured side by side (D-04). Route B needs `OpenApiTool` (see Pitfall 11); arguments arrive as gson-decoded `Map<String, Any>`. |
| Hand-rolled JSON-Schema validator | networknt / everit | A subset validator over `kotlinx.serialization.json` avoids a new dependency and a legitimacy audit for a throwaway harness. See Don't Hand-Roll for the justified exception. |

**Installation (spike module only; nothing is added to `:core`/`:providers`/`:keystore`):**

```kotlin
// gradle/libs.versions.toml (additions)
// litertlm = "0.17.1"            // EXACT pin, never latest.release (mv 2.4.0 sits on the 2.3.20 compiler's edge)
// litertlm-android = { group = "com.google.ai.edge.litertlm", name = "litertlm-android", version.ref = "litertlm" }

// spike-ondevice/build.gradle.kts
dependencies {
    implementation(project(":core"))
    implementation(libs.litertlm.android)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(testFixtures(project(":core")))
}
android { defaultConfig { ndk { abiFilters += "arm64-v8a" } } }   // S22 is arm64-v8a only; halves the APK delta
```

**Version verification (run this session):** `maven-metadata.xml` lists 0.15.0, 0.16.0, 0.16.1, 0.17.0-alpha1, 0.17.0, 0.17.1 with `release` = 0.17.1 `[VERIFIED: Google Maven]`. Note that the first fetch of the Gradle cache for this artifact will need network (it is not cached); later `--offline` runs work.

## Package Legitimacy Audit

> The `gsd-tools package-legitimacy check` seam accepts `npm|pypi|crates` only (usage error on `maven`, observed this session), so it could not be run for Maven coordinates. Evidence below is manual: the coordinate was discovered from Google's official LiteRT-LM Android page, the POM names the Google repo, and the group resolves on Google Maven.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| `com.google.ai.edge.litertlm:litertlm-android` | Google Maven | POM `inceptionYear` 2025; 24 published versions since `0.0.0-alpha06` | n/a (Google Maven exposes none) | github.com/google-ai-edge/LiteRT-LM (POM `<url>`) | Manual: OK `[CITED: developers.google.com/edge/litert-lm/android]` | Approved. Pin exactly; no `postinstall` concept on Maven. |
| `com.google.code.gson:gson` 2.14.0 / 2.13.2 | Maven Central (transitive) | long-lived | very high | github.com/google/gson | Manual: OK (transitive, well known) | Approved |
| `org.jetbrains.kotlin:kotlin-reflect` 2.4.0 / 2.2.21 | Maven Central (transitive) | long-lived | very high | github.com/JetBrains/kotlin | Manual: OK | Approved, but see consumer-fallout row |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` 1.11.0 / 1.9.0 | Maven Central (transitive) | long-lived | very high | github.com/Kotlin/kotlinx.coroutines | Manual: OK | Approved |

**Packages removed due to [SLOP] verdict:** none (seam not applicable to Maven).
**Packages flagged as suspicious [SUS]:** none.
**Model files (not packages):** pinned by sha256 above (Gemma 4 E2B, GPU variant). Gemma 3 1B sha256 must be recorded at download time (masked while the repo is gated). `[ASSUMED]` that the HF repo is the official Google-published mirror; verify the `base_model:google/gemma-4-E2B-it` tag (present in the API tags, `[VERIFIED]`) before pushing.

## Architecture Patterns

### System Architecture Diagram

```
HOST (Linux, memory-tight)                              TESTER (SM-S908U, R5CT10XNKQN, Android 15, arm64)
─────────────────────────                               ───────────────────────────────────────────────
 gold sets (small: committed synthetic;                  spike-ondevice APK (debug, unpublished)
 SB-sized: private, gitignored)                          ┌──────────────────────────────────────────────┐
        │                                                │ SpikeActivity (foreground, KEEP_SCREEN_ON)  │
        ▼                                                │   reads ladder stage from intent extras      │
 run-spike-ondevice.sh  ──adb -s R5CT10XNKQN──────────► │        │                                     │
  (guard: lock, identity,                                │        ▼                                     │
   window-granted check)                                 │  commandPipeline { onDevice = gate;          │
        │  push model (sha256-checked)                   │    tier(SingleShotStrategy);                 │
        │  am start --es stage ...                       │    provider(SpikeOnDeviceProvider) }         │
        │                                                │        │ ModelRequest (system, tools, user)  │
        │                                                │        ▼                                     │
        │                                                │  SpikeOnDeviceProvider : AiProvider(ON_DEVICE)│
        │                                                │    Route A: ResponseFormat.json(schema)      │
        │                                                │    Route B: OpenApiTool + Message.toolCalls  │
        │                                                │        │ JNI                                 │
        │                                                │        ▼                                     │
        │                                                │  liblitertlm_jni.so ── CPU (XNNPACK) / GPU   │
        │                                                │        │            (OpenCL, Adreno 730?)    │
        │                                                │        ▼                                     │
        │                                                │  Probes: Debug.getMemoryInfo (PSS),          │
        │                                                │   thermal status, BenchmarkInfo, exit reasons│
        │                                                │        │                                     │
        │                                                │        ▼  closed-vocabulary lines only        │
        │  adb logcat -s VaeSpike:I  ◄───────────────────┴─ Log "VAE_SPIKE_*" (no prompt/output/args)    │
        ▼
 spike-evidence-filter.sh (allow-list, key/prompt-shape scan; rejects whole capture on a hit)
        │
        ▼
 evidence/*.txt (committed) ──► VerdictRules (pure Kotlin, JVM-tested, thresholds locked pre-run)
        │                              │
        │                              ▼
        │                       13-VERDICT.md  (per-envelope green/red + numbers + measured rows)
        ▼                              │
 3 D-10 empirical rows ───────────────►│──► message to orchestrator (driver relays; SPIKE-02)
 (KV reuse / ResponseFormat / GPU)     └──► green: :ondevice (D-08)   red: delete spike module, SPIKE-03 N/A-deferred
```

### Recommended Project Structure

```
spike-ondevice/                         # com.android.application, NEVER published, NEVER in jitpack.yml
├── build.gradle.kts                    # AGP 9.2.1 built-in Kotlin, minSdk 35, JVM_11, abiFilters arm64-v8a
├── src/main/AndroidManifest.xml        # uses-native-library libOpenCL.so / libvndksupport.so (required=false)
├── src/main/kotlin/io/github/ygaray/voiceactionengine/spike/
│   ├── backend/   LlmBackend.kt (interface), LiteRtBackend.kt (the only file importing com.google.ai.edge.*)
│   ├── provider/  SpikeOnDeviceProvider.kt (AiProvider), RouteA.kt, RouteB.kt, SchemaMap.kt (JsonObject↔Map)
│   ├── gate/      SpikeOnDeviceCapability.kt (OnDeviceCapability over file-present + init result)
│   ├── ladder/    Ladder.kt (ordered stages + early-exit), Probes.kt (PSS/thermal/exit reasons), Trials.kt
│   ├── evidence/  EvidenceLine.kt (closed grammar), Scoring.kt (Wilson, percentiles, validator subset)
│   └── verdict/   Thresholds.kt (locked values), VerdictRules.kt (pure), VerdictReport.kt
├── src/main/kotlin/.../SpikeActivity.kt
├── src/test/kotlin/...                 # JVM tests over fakes: scoring, verdict truth table, mappers, grammar
└── (NO model file, NO gold labels for the SB envelope, NO api.txt, NO maven-publish)
scripts/
├── run-spike-ondevice.sh               # copy of run-sample-gate1.sh guard preamble + spike subcommands
├── verify-spike-device-guard.sh        # fake-adb refusal scenarios (pattern: verify-sample-device-guard.sh)
├── spike-evidence-filter.sh            # allow-list for VAE_SPIKE_* lines
└── verify-spike-verdict.sh             # recompute verdict from committed evidence; must equal 13-VERDICT.md
.planning/phases/13-on-device-model-spike/
├── 13-THRESHOLDS.md                    # locked BEFORE the first device step (committed, hash recorded in evidence)
├── evidence/                           # filtered closed-vocabulary lines only
├── 13-VERDICT.md                       # numbers + per-envelope verdict + measured rows
└── 13-VERDICT-MESSAGE.md               # the short text the driver relays to the orchestrator
```

### Pattern 1: Engine path through the real SingleShot (D-04)

**What:** Wire `commandPipeline { tier(SingleShotStrategy(...)); provider(SpikeOnDeviceProvider(...)); onDevice = ...; providerSelection = ...}` so the spike measures the byte-identical prompt the cloud path sends. `SingleShotStrategy` builds `ModelRequest(system, [UserMessage], tools, toolChoice, maxTokensPerTurn, CacheDirective(true), true, reasoning)` and forces `ToolChoice.Required(snapshot.singleShotTool)` by default; with `forceTool = false` it sends `ToolChoice.Auto()` and the model chooses among offered tools. `[VERIFIED: core/.../singleshot/SingleShotStrategy.kt:80-107]`

**When to use:** every measured cell. Both shapes matter (see Open Question 2): forced single tool (argument filling) and model-chooses (needed for negation / "no tool applies" gold items).

**Mapping the neutral request:**
- `ToolChoice.Required(name)` → Route A schema = that tool's `inputSchema`; output is the arguments object; synthesize `AssistantPart.ToolCall(id, name, args)`.
- `ToolChoice.Auto()` → Route A schema = `{tool: enum[...], arguments: object}` (constrained to the offered tool names); Route B offers all tools as `OpenApiTool`s.
- `ModelRequest.system` → `ConversationConfig.systemInstruction`; the single `UserMessage` → `Message.user(...)`.
- `ModelRequest.reasoning = OFF` → `ThinkingConfig(false)`; `cache` directive is ignored (no engine-visible prefix cache; KV reuse is a measured row, not an assumption).
- Result → `ModelResult.Success(ModelResponse(AssistantMessage(listOf(ToolCall(...))), StopReason.TOOL_USE, usage))`; failures → `ModelResult.Failure(FailureReason...)` with a stable code only (`isStableCode` = `[a-z0-9_]+`), never message text.

**Example (compiled this session against litertlm 0.17.1 + `core.jar` with kotlinc 2.3.20, exit 0):**

```kotlin
// Source: compile-verified skeleton; LiteRT-LM API read from litertlm-android-0.17.1 classes.jar via javap
@file:OptIn(com.google.ai.edge.litertlm.ExperimentalApi::class)   // ExperimentalApi = @RequiresOptIn(level = ERROR)

class SpikeProvider(private val engine: Engine) : AiProvider {
    override val id: ProviderId = ProviderId.ON_DEVICE
    override val requiresCredential: Boolean = false

    override suspend fun complete(call: ProviderRequest): ModelResult {
        ExperimentalFlags.enableBenchmark = true                  // global flag; needed for benchmarkInfo
        val forced = (call.request.toolChoice as? ToolChoice.Required)?.toolName
        val tool = call.request.tools.first { it.name == forced }
        val schema: Map<String, Any> = /* tool.inputSchema converted JsonObject -> Map */ emptyMap()
        val conv = engine.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(call.request.system),
                samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0, seed = 0),
                enableResponseFormat = true,                      // sendMessage throws unless this is true
                thinkingConfig = ThinkingConfig(false),
            ),
        )
        conv.use { c ->
            val reply = c.sendMessage(Message.user("..."), emptyMap(), responseFormat = ResponseFormat.json(schema))
            val text = reply.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
            val args = Json.parseToJsonElement(text) as JsonObject
            val bench = c.getBenchmarkInfo()                      // a function, not a Kotlin property
            val usage = Usage(bench.lastPrefillTokenCount.toLong(), 0L, 0L, bench.lastDecodeTokenCount.toLong())
            return ModelResult.Success(ModelResponse(
                AssistantMessage(listOf(AssistantPart.ToolCall("call_1", tool.name, args))), StopReason.TOOL_USE, usage))
        }
    }
}
```

### Pattern 2: Verdict as code, reproducible from evidence (SPIKE-02)

**What:** `VerdictRules.evaluate(thresholds, evidenceLines) -> Verdict` is a pure function. `13-THRESHOLDS.md` is committed (and its sha256 printed into the first evidence line) before any device step; `scripts/verify-spike-verdict.sh` recomputes the verdict from the committed evidence and fails if it differs from `13-VERDICT.md`. A gating metric with no evidence line yields red with reason `unmeasured:<metric>` (the D-07 time-box rule), so "time-box expiry" is a code path, not a judgment.

**When to use:** the whole phase. This is also what makes the interim early-red message safe to send: it is the same function over a partial evidence set.

### Pattern 3: Screen then confirm

**What:** Stage S (screen) runs N=20 per cell to pick the backend and route; Stage C (confirm) runs the full N (N>=100 schema, >=50 EN + >=50 ES semantic, >=30 negative) only on the winning cell per envelope. Without this, 12 cells x ~130 trials x seconds-per-trial does not fit a TESTER window.

### Pattern 4: Backend seam inside the spike module only

**What:** `LlmBackend` (interface) with `LiteRtBackend` as the single class importing `com.google.ai.edge.*`. `Engine`, `Conversation` are `final` over JNI and cannot be faked, so JVM tests (mappers, ladder logic, scoring) run against a fake `LlmBackend`. This also keeps the future `:ondevice` extraction trivial.

### Anti-Patterns to Avoid

- **Measuring warm only.** A voice command's cost is cold load plus first call; report cold, warm, and sustained separately (PITFALLS #7). Cold must be defined (see Pitfall 6).
- **Accepting "valid JSON" as success.** Score semantic accuracy against gold labels, with Spanish and negation/no-tool items (PITFALLS #8, D-06).
- **Putting any model, gold label (SB), or ML reference in a published module or in git.** Model files go through `adb push` only.
- **A shared-scope ML denial.** The scanner regex and `config/detekt/detekt.yml` apply to every published module; a global LiteRT token would block `:ondevice` itself later (Pitfall 9).
- **Reusing `:sample`'s runner or LegId machinery** (D-03): `:sample` is needed by other phases and its `LEGS` allow-list, `ALLOW_PATTERN` and `sampleAllowedEdges` are shared gates.
- **`latest.release` for litertlm.** The 2.3.20 compiler reads metadata exactly one minor ahead; a Kotlin-2.5-built LiteRT-LM release would break the build silently at the next resolve.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Inference runtime, KV management, tokenization | anything custom or a JNI wrapper | `litertlm-android` | Native, multi-backend, Google-maintained. |
| Strict-JSON output | a retry/repair loop as the primary mechanism | `ResponseFormat.json(schema)` (Route A), measured with and without | A retry doubles latency on a slow model and hides the raw validity rate. Measure raw first. |
| Tool-call parsing from free text | regex over model output | Route B `Message.toolCalls` or Route A constrained JSON | Free-text tool formats differ per model template. |
| Wilson interval, percentiles | an ad hoc "mean" | a ~15-line pure function with unit tests (formula is closed-form) | Required by D-07; keep it tested against the table in Validation Architecture. |
| JSON-Schema validation for "schema-valid" | a general validator | a **subset validator** over `kotlinx.serialization.json` limited to the keywords the fixture and synthetic tools actually use (`type`, `required`, `properties`, `enum`, `items`, `additionalProperties`) | **Justified exception to "don't hand-roll":** a general validator adds a dependency (Jackson or org.json) plus a legitimacy audit for a throwaway harness. The subset is closed and unit-testable; the planner must first enumerate the keywords present in the tool schemas (the SB fixture is private, so enumerate at run time and fail loudly on an unknown keyword). |
| Device guard / lock / identity | a new guard design | copy the `run-sample-gate1.sh` preamble (TESTER constants, `flock` on the shared lock file, USB-first resolution, `ro.serialno`+model+SDK identity proof, `finish` with a final `<NAME>: <OUTCOME> sub=... key=value` line) | The refusal paths are already proven by a fake-adb scenario script. |
| Evidence leak prevention | grep ad hoc | allow-list filter modeled on `scripts/sample-evidence-filter.sh` (`ALLOW_RE` + key-shape scan, whole-capture rejection) | Prompts, outputs and tool arguments must never reach a committed file. |
| Model integrity | trusting the transfer | sha256 compare on host before push and `sha256sum` via `run-as`/shell after | 2.6 GB pushes over adb can truncate silently. |

**Key insight:** every measurement the spike needs is either a `BenchmarkInfo` field, an Android framework call, or a closed-form statistic. The only novel code is the glue and the rule engine; keep both small and tested so the verdict can be trusted and re-derived.

## Common Pitfalls

### Pitfall 1: `ResponseFormat` silently not enforced (looks green, isn't)
**What goes wrong:** the call returns well-formed JSON because the model usually does, so "constrained decoding works" is inferred from a happy path while the constraint is ignored, mis-set, or only partly supported (enum, nested objects, `oneOf`).
**Why it happens:** `ResponseFormat.json(...)` is undocumented in Google's Android guide; the bytecode only tells us `Conversation.sendMessage` throws "response_format cannot be used unless enableResponseFormat=True was passed to ConversationConfig" and that `ResponseFormat.Type` has `REGEX` and `JSON_OBJECT`. `ExperimentalFlags.enableConversationConstrainedDecoding` is a separate global flag whose interaction is unknown. `[VERIFIED: javap and string table of Conversation.class / ResponseFormat$Type, litertlm 0.17.1]`
**How to avoid:** the ResponseFormat row is a feature-probe matrix (flat object, required, enum, nested object, array, `anyOf/oneOf`, numeric bounds, `additionalProperties:false`) run with the constraint ON and OFF, plus adversarial prompts that would violate the schema unconstrained. Record per feature: enforced / ignored / native error (`LiteRtLmJniException`). Only "enforced" counts as support.
**Warning signs:** ON and OFF invalid rates are equal; no exception was ever thrown for an obviously unsupported schema.

### Pitfall 2: Context length silently truncates or overflows the SB envelope
**What goes wrong:** `EngineConfig.maxNumTokens` defaults to `null` (model default). Google's benchmarks use a 2,048-token context; the SB prefix is ~7k tokens. A too-small context fails or truncates instead of measuring the real envelope.
**How to avoid:** set `maxNumTokens` explicitly (>= prefix + output + margin, for example 8,192 for SB), and assert from `BenchmarkInfo.lastPrefillTokenCount` that the whole prompt was prefilled. Gemma tokenization differs from Anthropic's, so the "~7k" is an estimate; record the real count per envelope. `[CITED: huggingface.co/litert-community/gemma-4-E2B-it-litert-lm, via summarizer: "benchmarks use 2048-token context"]`; the `EngineConfig` default is `[VERIFIED: javap, constructor has nullable Integer maxNumTokens]`.
**Warning signs:** prefill token count below the rendered prompt's estimate; "context window exceeded"-like native errors.

### Pitfall 3: Peak "PSS" is ambiguous with a memory-mapped 2.6 GB model
**What goes wrong:** D-07 says "peak PSS <= 2,000 MB". File-backed mapped weights count toward PSS as they are touched; Google's CPU card shows 1,733 MB peak at 2,048 context on an S26, and a longer context grows KV. A reading taken through a different tool (RSS, `Native Heap`, `Private Dirty`) can differ by hundreds of MB.
**How to avoid:** lock the exact metric in `13-THRESHOLDS.md` before the run: in-process `Debug.getMemoryInfo().totalPss`, sampled at a fixed interval during generation, peak over the run; cross-check once with `adb shell dumpsys meminfo <pkg>` TOTAL PSS. State the sampling interval. Do not change the definition after seeing numbers.
**Warning signs:** the number moves by more than ~10% between tools.

### Pitfall 4: Warm-measure bias and an undefined "cold"
**What goes wrong:** the kernel page cache stays warm after `force-stop`, so "cold" is optimistic; the GPU path also has a first-ever kernel compile that later cold starts skip via `EngineConfig.cacheDir`.
**How to avoid:** report three cold rows: process-cold/page-cache-warm (force-stop then start), first-ever GPU init (empty `cacheDir`), and optionally boot-cold (device reboot; the wired TESTER is passcode-free and self-heals in about 1 minute, but it is a device state change that needs the window grant). Rootless page-cache eviction is not possible; document the limitation instead of claiming true cold.
**Warning signs:** cold init under a second on a 2.6 GB model.

### Pitfall 5: Thermal throttling and Samsung Freecess corrupt later cells
**What goes wrong:** sustained decode throttles clocks (public reports of 30-40% clock drop within two minutes on flagship SoCs; `[CITED: PITFALLS.md #7]`), and a backgrounded or screen-off app can be frozen (looks like a hang).
**How to avoid:** keep the activity foreground with `FLAG_KEEP_SCREEN_ON`, whitelist via `cmd deviceidle whitelist +pkg` (as `run-sample-gate1.sh` does and undoes), record `PowerManager.getCurrentThermalStatus()` per trial block, enforce a cool-down between cells (device below a recorded status before the next cell), and run the sustained row last within a cell. "No SEVERE thermal" in D-07 requires the status to be sampled, not inferred.
**Warning signs:** latency drifts upward monotonically across a block; `THERMAL_STATUS_MODERATE` or worse.

### Pitfall 6: Process death is a measurement, not an exception
**What goes wrong:** `lmkd` or a native fault kills the harness mid-ladder; nothing is written.
**How to avoid:** append-only evidence written (and flushed) per trial in the app's files dir plus a `VAE_SPIKE_TRIAL` line per trial; the ladder is resumable from a stage cursor; the host records `pidof` before/after and, in-app on the next start, `ActivityManager.getHistoricalProcessExitReasons` (API 30+) so "zero process deaths" is proven from `ApplicationExitInfo`, not from absence of logs.
**Warning signs:** a stage ends with fewer trials than planned and no explanation line.

### Pitfall 7: The SB envelope is likely latency-red unless prefix KV reuse works
**What goes wrong:** 18 tools plus system is ~7k tokens (v1.0: `cache_creation_input_tokens=7016`, `prefix_chars=21109`). If every `Conversation` re-prefills that prefix, the prefill alone dominates the warm p50 <= 3.0 s bar; Google's published S26 numbers (CPU 557 tok/s, GPU 3,808 tok/s) imply ~12.6 s (CPU) or ~1.8 s (GPU) for 7k tokens on a 2026 flagship `[CITED: HF model card, S26 Ultra]`; an S22 is an older SoC `[ASSUMED]: slower`. `[CITED: .planning/milestones/v1.0-phases/10-sample-harness-gate-1-docs/10-SELF-UAT.md:104-107]` for the prefix size.
**How to avoid:** put the cheap gating rows first (Pattern 3 ladder) and wire early-exit-to-red. The KV-reuse probe compares `lastPrefillTokenCount` / TTFT for two `Conversation`s created back to back with an identical system+tools preface; the API offers `ConversationConfig.prefillPrefaceOnInit` as the only visible lever and no clone API (`Session` has `runPrefill/runDecode` only). `[VERIFIED: javap of Conversation, Session, ConversationConfig]`
**Warning signs:** TTFT scales linearly with prompt length across back-to-back identical-preface calls.

### Pitfall 8: Silent decode-path drift between Route A and Route B
**What goes wrong:** the two routes differ in what they measure (Route A: constrained decode of one JSON; Route B: model emits native tool-call tokens, then LiteRT-LM parses, with `automaticToolCalling = false`). Comparing a Route A latency to a Route B latency with different `maxOutputToken` or thinking settings is meaningless.
**How to avoid:** identical `SamplerConfig` (seeded, temperature 0), `ThinkingConfig(false)`, identical `maxOutputToken`, identical system/user text; the only varying input is the route. Route B argument numbers arrive as gson-decoded values; coerce explicitly and count a type-mismatch as schema-invalid (see Pitfall 11).

### Pitfall 9: Shared scanner / detekt scope blocks the future `:ondevice`
**What goes wrong:** `gradle/invariants.gradle.kts` is applied to every published module and its `forbidden package` rule and `config/detekt/detekt.yml` `ForbiddenImport` list are global. Adding `com.google.ai.edge` there makes the green-path `:ondevice` module fail its own `scanBannedConstructs`/detekt.
**How to avoid:** scope the ML denial by module (a rule list active only for `core`, `providers`, `keystore`; implement as a module-name guard like the existing `if (project.name == "providers")` blocks, `[VERIFIED: gradle/invariants.gradle.kts:332,422]`), and prefer a classpath-level gate for the dependency half. Do not put LiteRT tokens in the shared `detekt.yml`.

### Pitfall 10: Consumer runtime fallout of the Kotlin 2.4 reflect/stdlib (measure, do not assume)
**What goes wrong:** `litertlm-android:0.17.1` declares `kotlin-reflect:2.4.0` (compile scope), whose POM depends on `kotlin-stdlib:2.4.0`, so a consumer's runtime classpath resolves stdlib 2.4.0 above its own 2.3.20. `[VERIFIED: litertlm-android-0.17.1.pom; kotlin-reflect-2.4.0.pom on Maven Central]` With `implementation` in `:ondevice` the dependency is runtime-scope and does not enter consumers' compile classpath (`[ASSUMED]` from standard Gradle/AGP publishing semantics; confirm with `dependencyInsight` in the spike module, which is a plan step 1 row).
**How to avoid:** record, per pin, the resolved `kotlin-stdlib`/`kotlin-reflect`/`coroutines` versions on `releaseRuntimeClasspath`. 0.16.1 declares `kotlin-reflect:2.2.21` (lower than 2.3.20: no uplift) and `coroutines-android:1.9.0` (below the 1.11.0 pin: no downgrade). `[VERIFIED: litertlm-android-0.16.1.pom]`

### Pitfall 11: Route B tool-call plumbing details
**What goes wrong:** `ToolProvider.provideTools` is an abstract internal-mangled member, so you cannot subclass it; use `tool(OpenApiTool)` (`getToolDescriptionJsonString(): String`, `execute(String): String`). `ToolCall.arguments` is `Map<String, Any>`; numbers can arrive as `Double`. 0.17.1's notes cite a tool-call integer-type fix, which 0.16.1 lacks. `[VERIFIED: javap]` `[CITED: github.com/google-ai-edge/LiteRT-LM/releases v0.17.1, MEDIUM]`
**How to avoid:** with `automaticToolCalling = false`, never execute; read `Message.toolCalls`; convert Map→`JsonObject` with explicit number handling; a mismatch against the tool schema is a schema-invalid trial, not a harness error.

### Pitfall 12: Evidence leakage through logcat
**What goes wrong:** a debug `Log.i` of a prompt, model output, or tool argument lands in a committed file, violating "transcripts, tool args never reach logs".
**How to avoid:** the harness logs only `VAE_SPIKE_<KIND> k=v ...` lines (values `[A-Za-z0-9_.:/,-]{0,96}`), the host filter keeps only lines matching the grammar and rejects the whole capture on any key shape, and gold-set text never leaves the device or the repo's gold file. Unit-test the grammar against `scripts/spike-evidence-filter.sh` the way `verify-sample-device-guard.sh` tests the sample's golden lines.

### Pitfall 13: Gemma 3 1B needs a human HF acceptance and carries the Gemma Terms
**What goes wrong:** the repo is `gated: auto` (license tag `gemma`); an unauthenticated download fails, and an agent must not use Yahir's HF credentials or accept terms on his behalf.
**How to avoid:** a `checkpoint:human-action` ("Yahir accepts the Gemma terms on HF and places the file, or runs the download in his own terminal; the agent never reads the token"). Use is internal measurement only: no redistribution, so Gemma ToU flow-down is not triggered, and the file is never committed or shipped. If Yahir declines, the 1B control row is recorded as `skipped:gated` and does not affect the E2B verdict (D-01 makes it a control).

### Pitfall 14: Gate-1 verify step vs the TESTER window
**What goes wrong:** `execute-phase` runs a blocking `verify_work_agentic_gate` that wants to drive the real app on the TESTER. Phase 13's device run IS the measurement; running a second Gate-1 drive needs a second window grant, and the window must not overlap Phase 12 smoke (done) or Phase 19.
**How to avoid:** make the plan state that the committed, filtered evidence plus the JVM verdict reproduction is the Gate-1 record (Phase 12 precedent: `12-SELF-UAT.md`, headless, 5/5 PASS), and request any extra device drive inside the same granted window. `[CITED: .planning/STATE.md; ~/.claude/context/workflows/two-gate-uat.md]`

### Pitfall 15: JitPack configures every included project
**What goes wrong:** `run-sample-gate1.sh`'s sibling comment in `:sample/build.gradle.kts` notes JitPack configures every included project even though only the install list runs. A spike module left in `settings.gradle.kts` at the `v1.1.0` tag is configured on JitPack. `[VERIFIED: sample/build.gradle.kts:1-2]`
**How to avoid:** the red path deletes the module, the catalog entry and the `include`; the green path replaces it with `:ondevice` (published) and the spike harness either moves into `:sample` (Phase 19) or is deleted.

## Code Examples

### Closed-vocabulary evidence grammar (extend the sample's allow-list, do not reuse its kinds)

```bash
# Source: scripts/sample-evidence-filter.sh (ALLOW_RE, key-shape scan, whole-capture rejection)
ALLOW_RE='^VAE_(ENV|FIXTURE|KEY|TURN|ATTEMPT|CACHE|SMOKE|OUTCOME|VERDICT|BUDGET|AUTORUN)( [a-z0-9_]+=[][A-Za-z0-9_.:/,-]{0,96})+$'
# Spike filter: ^VAE_SPIKE_(ENV|PREFLIGHT|INIT|PREFILL|KVREUSE|SCHEMAPROBE|GPU|TRIAL|BLOCK|SUSTAINED|MEM|THERMAL|EXIT|VERDICT)( [a-z0-9_]+=...)+$
```

### Wilson score lower bound (closed form; use in `Scoring.kt` and test against the table below)

```kotlin
// Source: standard Wilson score interval, z = 1.959964 (95%)
fun wilsonLowerBound(successes: Int, n: Int, z: Double = 1.959964): Double {
    val p = successes.toDouble() / n
    val d = 1.0 + z * z / n
    val c = p + z * z / (2.0 * n)
    val a = z * Math.sqrt(p * (1.0 - p) / n + z * z / (4.0 * n * n))
    return (c - a) / d
}
```

Computed this session (`python3`, same formula) and to be used as unit-test oracles `[VERIFIED: computed locally]`:

| Threshold in D-07 | N | Successes needed |
|---|---|---|
| semantic Wilson LB >= 85% (EN) | 50 | **>= 48 / 50 (96%)** |
| semantic Wilson LB >= 80% (ES) | 50 | **>= 46 / 50 (92%)** |
| schema-valid >= 98% read as a **point estimate** | 100 | <= 2 failures |
| schema-valid >= 98% read as a **Wilson lower bound** | any | needs ~189 trials with zero failures; at N=100, 100/100 gives only 96.3% |
| zero false writes | 30 negatives | 0/30 leaves an upper bound of ~11.4% (Wilson) / 10% (rule of three) |

**Planner action:** `13-THRESHOLDS.md` must state that "schema-valid >= 98% (N>=100)" is a point estimate (the Wilson reading is unsatisfiable at N=100) and must state the exact PSS definition (Pitfall 3). These are clarifications of D-07's wording, not changes to its numbers; surface them to the orchestrator in the lock message.

### Reading litertlm Kotlin metadata and class level without Gradle (reproduces the toolchain proof)

```bash
# Source: run this session. Python class-file parser over classes.jar (kotlin.Metadata mv ints + major version)
# 0.17.1 -> class major {65: 78 classes}, kotlin mv {(2,4,0): 78}
# 0.16.1 -> class major {65: 72 classes}, kotlin mv {(2,3,0): 72}
K2JVMCompiler (kotlin-compiler-embeddable 2.3.20) -jvm-target 11 Probe.kt vs 0.17.1 classes.jar  -> exit 0, no diagnostics
K2JVMCompiler (kotlin-compiler-embeddable 2.2.10) same jar                                        -> exit 1:
  "class 'com.google.ai.edge.litertlm.Engine' was compiled with an incompatible version of Kotlin.
   The actual metadata version is 2.4.0, but the compiler version 2.2.0 can read versions up to 2.3.0."
d8 (build-tools 36.0.0, D8 8.10.9) --release --min-api 35 --lib android-36/android.jar classes.jar -> exit 0 (both versions)
readelf -lW liblitertlm_jni.so | grep LOAD  -> p_align 0x4000 on every LOAD (arm64-v8a 0.17.1, 0.16.1; x86_64 0.17.1)
```

## Docs-Answerable External Facts (D-10): answers

| Fact | Answer | Confidence / provenance |
|------|--------|-------------------------|
| LiteRT-LM 0.17.1 Kotlin metadata and Java level vs our Kotlin 2.3.20 / JVM 11 | Metadata **2.4.0**, class major **65 (Java 21)**, all 78 classes. 2.3.20 compiles against it (exit 0) because the compiler reads one minor ahead; 2.2.x cannot. Bytecode target 11 for our own code is unaffected: reading Java-21 classes is fine, and `verifyBytecodeLevel` (class major 55) checks only a module's own classes. | `[VERIFIED: local class-file parse + kotlinc 2.3.20 and 2.2.10 runs, this session]` |
| Same for 0.16.1 | Metadata **2.3.0**, class major **65**. Reads natively on 2.3.20 and 2.2.x. Same public API for `Engine`/`Conversation`/`ConversationConfig`/`ResponseFormat`. | `[VERIFIED: same]` |
| Dex / D8 on Java-21 classes at minSdk 35 | D8 8.10.9 dexes both versions, exit 0. AGP 9.2.1 bundles its own (newer) R8/D8; confirm inside Gradle in plan step 1. AAR has no `aar-metadata.properties`, so no `minCompileSdk`/`minAgpVersion` gate. | `[VERIFIED: d8 run + unzip of AAR]` (AGP-internal D8 version `[ASSUMED]` newer) |
| Gemma 4 E2B license | **Apache-2.0**, ungated; HF tag `license:apache-2.0`, `base_model:google/gemma-4-E2B-it` marked `finetune` (a conversion). Google's terms page states "For Gemma 4 terms, see the Gemma 4 license" and that all earlier families (1, 1.1, 2, 3, 3n, FunctionGemma, ...) remain under the Gemma Terms of Use. | `[CITED: ai.google.dev/gemma/terms, ai.google.dev/gemma/apache_2]` `[VERIFIED: HF API tags]` |
| Repack NOTICE / attribution for a bundled `.litertlm` | Apache-2.0 sections 4(a)-(d) apply to any redistributor of the converted weights: give recipients a copy of the license, mark that the file is a modified (converted/quantized) derivative of `google/gemma-4-E2B-it`, keep copyright/attribution, and carry any NOTICE. The HF repack repo ships **no LICENSE or NOTICE file** (siblings: README, chat_template.jinja, model files, notebook), so the consuming app supplies them. The library never ships weights, so the burden is the consumer's; the module README must say so. | `[CITED: Apache-2.0 text via ai.google.dev/gemma/apache_2]` `[VERIFIED: HF file list]`; legal reading `[ASSUMED]`, no legal review |
| Gemma 3 1B (control) license | Gemma Terms of Use, `gated: auto`. Redistribution would require the Terms copy, use-restriction flow-down and a Notice file ("Gemma is provided under and subject to the Gemma Terms of Use..."). Spike use is internal measurement, no redistribution. | `[CITED: ai.google.dev/gemma/terms]` `[VERIFIED: HF API]` |
| 16 KB page-size deadline | Google Play: apps targeting API 35+ must support 16 KB pages on 64-bit devices; "Starting **February 1, 2027**" updates that don't are blocked; no extension stated. Automatic alignment needs AGP >= 8.5.1 and NDK >= r28. The vendor `.so` is already aligned (`p_align 0x4000`, arm64-v8a and x86_64, both versions). SB and CT target SDK 36. The S22 itself is a 4 KB-page device, so this is a consumer distribution gate, not a TESTER runtime risk. Also verify the final APK with `zipalign -v -c -P 16 4` (the check Google documents) in plan step 1. | `[CITED: developer.android.com/guide/practices/page-sizes]` `[VERIFIED: readelf]` |
| Consumer stdlib 2.4.0 fallout | 0.17.1 → `kotlin-reflect:2.4.0` → `kotlin-stdlib:2.4.0` on consumers' **runtime** classpath (above their 2.3.20); no compile-classpath leak with `implementation` (assumed, to be confirmed by `dependencyInsight`). 0.16.1 → `kotlin-reflect:2.2.21`, no uplift. Stdlib is backward compatible so the practical risk is low, but it is a visible change to SB/CT/YAT dependency graphs and must be stated in the green-path docs. | `[VERIFIED: POMs]` (compile-leak half `[ASSUMED]`) |
| APK-size cost a consumer pays | `liblitertlm_jni.so` arm64-v8a **21,802,960 B raw** (0.17.1; 21,529,648 B for 0.16.1), ~9.5 MB deflated per the milestone research. AGP stores `.so` **uncompressed** and page-aligned by default for minSdk >= 23, so the **APK file delta is ~21.8 MB**, while the compressed Play **download** delta is nearer the deflated figure. Report both (the milestone research's "~9.5 MB to the APK" understates the on-disk APK delta). Classes are ~0.16 MB. With `abiFilters arm64-v8a` the x86_64 copy (25,968,008 B) is excluded. The weights are a separate on-device download, not APK size. | `[VERIFIED: unzip -l of AAR]` `[CITED: developer.android.com JniLibsPackaging, AGP: uncompressed and page-aligned when minSdk >= 23]` |
| MediaPipe vs LiteRT-LM | MediaPipe LLM Inference is maintenance-only; LiteRT-LM is the supported path and the only one that lists Gemma 4. D-01 already resolves "Gemma-2B (MediaPipe/LiteRT)" to Gemma 4 E2B on LiteRT-LM. | `[CITED: PITFALLS.md #6, STACK.md (vendor pages fetched 2026-10-05)]` |

## Empirical-Only Facts (D-10): each is a measured verdict row

| Row id | Question | How to measure (cheapest discriminating test) | Disposition values | Gating? |
|--------|----------|----------------------------------------------|--------------------|---------|
| `kv_reuse` | Does a second `Conversation` with an identical system+tools preface avoid re-prefilling it? | Two back-to-back conversations, identical preface, enable `ExperimentalFlags.enableBenchmark`; compare `lastPrefillTokenCount`, `timeToFirstTokenInSecond` and wall time for the 2nd vs the 1st; repeat with `prefillPrefaceOnInit = true`. Docs are silent (Google's Android guide has no KV/prefix section, observed this session); there is no clone API. | `reused` / `not_reused` / `unobservable` | Yes for the SB envelope (decides whether a ~7k prefix can meet warm p50 <= 3.0 s); informational for small |
| `rf_enforced` | Is `ResponseFormat.json` enforced, and for which schema features? | Feature-probe matrix ON vs OFF with adversarial prompts (Pitfall 1). Set `enableResponseFormat = true`; record whether `enableConversationConstrainedDecoding` is also needed. | per feature: `enforced` / `ignored` / `native_error` | Yes (it decides Route A viability and the schema-valid rate) |
| `rf_enum_nested` | Do enums and nested objects work (the SB tool surface needs them)? | Subset of the matrix with the actual tool-schema shapes (enumerate keywords at run time from the fixture; fail loudly on unknown) | same | Yes |
| `gpu_adreno730` | Does the GPU (OpenCL) backend initialize and run on the TESTER, and is it faster? | `Backend.GPU()` init with `<uses-native-library libOpenCL.so required=false>` and `libvndksupport.so` in the manifest (Google's documented requirement); on failure record the native error and fall back to `Backend.CPU(...)`; record first-ever (empty `cacheDir`) vs repeat init. The prebuilt NPU files in the repo target `sm8550/8650/8750/8850` and `qcs8275`, not the S22's `sm8450`, so only CPU and GPU are candidates. `[VERIFIED: HF file list]` | `gpu_ok` / `gpu_init_failed:<code>` / `gpu_slower` | Yes if the CPU path cannot meet RAM or latency |
| `route_ab` | Route A vs Route B schema-valid rate and latency | identical settings (Pitfall 8) | numbers | Yes (pick the better route per envelope) |
| `consumer_fallout` | Resolved stdlib/reflect/coroutines on a representative consumer runtime classpath per pin | `dependencyInsight` on the spike module (host, no device) | versions | Informational |

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| MediaPipe LLM Inference (`.task`, `tasks-genai` 0.10.x) | LiteRT-LM (`.litertlm`, `litertlm-android`) | MediaPipe marked maintenance-only; LiteRT-LM 0.15-0.17 active in Aug-Sep 2026 | New code must not target MediaPipe. |
| Gemma 3 family under Gemma Terms of Use | Gemma 4 under Apache-2.0 | Gemma 4 release (2026) | Redistribution burden drops to Apache notices; keeps weights out of the library anyway. |
| 4 KB page assumption for native libs | 16 KB alignment required by Google Play for apps targeting API 35+ | Enforced for updates from 2027-02-01 | Vendor `.so` is already aligned; consumers must still verify their final APK. |
| `latest.release` dependency drift | exact pin | n/a | The 2.3.20 compiler's one-minor window makes an unpinned LiteRT-LM a build break waiting to happen. |

**Deprecated / outdated:**
- `com.google.mediapipe:tasks-genai`: maintenance-only; do not start new work on it.
- Gemini Nano / AICore / ML Kit GenAI: out of scope (LATER-06; the S22 has no AICore).

## Repo Seams the Harness Must Use (read this session)

| Seam | Where | Verbatim facts |
|------|-------|----------------|
| `ProviderId.ON_DEVICE` | `core/.../core/ProviderId.kt:28` | `public val ON_DEVICE: ProviderId = ProviderId("on_device")` |
| `OnDeviceCapability` | `core/.../provider/OnDeviceCapability.kt:13-15` | `public fun interface OnDeviceCapability {` ... `public suspend fun availability(): OnDeviceAvailability` |
| `OnDeviceAvailability.Unavailable` | same file `:51-53` | `public class Unavailable(public val code: String) : OnDeviceAvailability() {` ... `require(isStableCode(code)) { "Unavailable code must be a stable code" }`; `isStableCode` is `Regex("[a-z0-9_]+")` (`core/.../failure/ReasonSupport.kt:4,21`). Only `Available` is usable; `Downloadable`, `Downloading` count as unavailable (`OnDeviceGateTest`). |
| Pipeline default and hook | `core/.../pipeline/PipelineBuilder.kt:19,82` | `private const val NOT_IMPLEMENTED = "not_implemented"`; `public var onDevice: OnDeviceCapability = OnDeviceCapability { OnDeviceAvailability.Unavailable(NOT_IMPLEMENTED) }`. One probe instance feeds both the policy pre-check and the router. |
| `AiProvider` contract | `core/.../provider/AiProvider.kt:18-36` | `public val id: ProviderId`; `public val requiresCredential: Boolean get() = true`; `public fun capabilities(model: String): ModelCapabilities = ModelCapabilities.UNKNOWN`; `public suspend fun complete(call: ProviderRequest): ModelResult`. An on-device provider returns `requiresCredential = false` and receives a null credential. |
| Router gate order | `core/.../provider/ModelRouter.kt` (`resolve`, `usable`) | selection → tier+policy gate → on-device probe (`usable`: probe must be ready AND `ON_DEVICE in providers`) → registration → credential → capabilities. A tier declaring only `ON_DEVICE` fails loudly when unavailable; to fall back, declare both providers on the tier and a fallback on the selection (one-level, policy-checked, offline-only never reaches cloud). |
| SingleShot request | `core/.../singleshot/SingleShotStrategy.kt:80-107` | forced tool unless `forceTool=false`; `ModelRequest(system, [UserMessage], tools, choice, policy.maxTokensPerTurn, CacheDirective(true), true, reasoning)`; one call, first tool call only; extras dropped with trace `extra_tool_calls_dropped`. |
| Stable failure surface | `core/.../failure/FailureReason.kt:215-217` | `ProviderUnavailable(provider, cause)` with `require(cause == null || isStableCode(cause))`. Use it for `model_missing`, `abi_unsupported`, `insufficient_memory`, `gpu_init_failed`. |

**Gate inventory (where SC4 is enforced today, and what D-09 changes):**

| Gate | File | Today | D-09 change |
|------|------|-------|-------------|
| On-device implementation scan | `core/src/test/.../NoHardCodedConstantsTest.kt:57-62,135-137` | tokens `(?i)aicore`, `(?i)mlkit`, `(?i)(?<![A-Za-z0-9])nano(?![A-Za-z0-9])`, `Nano(?![a-z])`; scans **only** `:core/src/main/kotlin`; comment lines exempt | add `(?i)litert`, `(?i)mediapipe`, `com\.google\.ai\.edge` to `onDevice` + positive controls (flagged / passing lists) |
| Classpath allowlist | `gradle/invariants.gradle.kts:332-363` | `:core` only (`coreAllowed`) | none needed for `:core` (an ML artifact already fails) |
| `:providers` / `:keystore` ML dependency | none | no allowlist, no denial | new `verifyNoMlArtifacts` (sibling of `verifyNoDiArtifacts` at `:309-329`, `deniedDiGroups = setOf("com.google.dagger", ...)`) denying `com.google.ai.edge*`, `com.google.mediapipe*`, `org.tensorflow*`, `com.google.mlkit*` on every published module's compile+runtime classpath **except `:ondevice`** |
| Source tokens in published modules | `gradle/invariants.gradle.kts:25-30` `forbidden package (FQ or import)` | OkHttp-internal, mockwebserver3, `android.util.Log`, DI | add the ML tokens **only for `core`/`providers`/`keystore`** (Pitfall 9) |
| Hygiene | `scripts/verify-repo-hygiene.sh:55` `forbidden_specs=('*sb-a10-fixture*' '*baseline*.xml')` | no model patterns | add `'*.litertlm' '*.task' '*.bin'` (and a gold-label pattern for the private SB labels) + matching `.gitignore` entries |
| Negative controls | `scripts/verify-negative-controls.sh:54-69` (per-module loop `for m in core providers keystore`) | plants for DI, Log, okhttp internal, ... | add a plant per module: `import com.google.ai.edge.litertlm.Engine` → expect `:$m:scanBannedConstructs` (and `:core:test` for the `:core` test) red; a dependency plant for `verifyNoMlArtifacts` |
| `:sample` module-graph edges | `gradle/invariants.gradle.kts:272-274` | `allowedEdges = mapOf(":core" ..., ":providers" ..., ":keystore" ...)`; `sampleAllowedEdges = setOf(":core", ":providers", ":keystore")` | the unpublished spike module does not apply `invariants.gradle.kts`, so nothing to add for the spike; `:ondevice` (green) needs `":ondevice" to setOf(":core")` (Phase 17 owns this plumbing) |

Two further facts for the planner: `verify-repo-hygiene.sh` section g asserts the toolchain pins (Gradle 9.4.1, Kotlin 2.3.20, AGP 9.2.1, OkHttp 4.12.0) `[VERIFIED: scripts/verify-repo-hygiene.sh]`, and `jitpack.yml:6` names exactly three `publishReleasePublicationToMavenLocal` tasks and never `:sample`; the spike module must never appear there `[VERIFIED: jitpack.yml:3-6]`.

## The Gate-1 runner and the stale `PHASE_DIR` (D-03)

- **Current state (corrects D-03's wording):** `scripts/run-sample-gate1.sh:41` reads `PHASE_DIR="${VAE_GATE1_PHASE_DIR:-.planning/phases/12-wave-1-seams-w04-fix}"` and `:42` `DECISION_FILE="$PHASE_DIR/12-LIVE-LEG-DECISION.md"`. Phase 12 already retargeted it off the archived Phase 10 directory. It is now stale only in the sense that Phase 12's directory will itself be archived at milestone close, and `12-LIVE-LEG-DECISION.md` reads `decision: consumed`. Because the spike uses its own runner and never a `:sample` push-keys step (no API keys are needed), D-03's "fix before any :sample run" is not on Phase 13's path. Do not edit `run-sample-gate1.sh` in Phase 13; leave the retarget to Phase 19. `[VERIFIED: scripts/run-sample-gate1.sh:41-42; 12-LIVE-LEG-DECISION.md line 3]`
- **Guard to copy (do not weaken):** `TESTER_USB="R5CT10XNKQN"`, `TESTER_WIFI="100.118.21.106:1496"`, `PERSONAL_IP="100.126.94.47"`, `EXPECTED_MODEL="SM-S908U"`, `MIN_SDK=35`, `LOCK_FILE="${XDG_RUNTIME_DIR:-/tmp}/vae-keystore-tester.lock"` shared with `run-keystore-instrumented.sh`; USB first, wireless only as a fallback after `ro.serialno` proves identity; no option or environment variable changes the target; the final line is `NAME: OUTCOME sub=... k=v`; exit codes 0/1/2/3/4. `[VERIFIED: scripts/run-sample-gate1.sh:28-34 and body]`
- **Offline proof of the guard:** `scripts/verify-sample-device-guard.sh` runs the runner against a fake `adb` per scenario and asserts every logged call is `-s R5CT10XNKQN ...` or `connect 100.118.21.106:1496`; it has its own `PHASE_REL` (`:19`). Make `verify-spike-device-guard.sh` the same shape for the spike runner. The spike runner's subcommands (suggested): `preflight`, `build-install`, `push-model <model>` (sha256-checked), `run <stage>`, `capture-save <stage>`, `pull-evidence`, `cleanup` (force-stop, remove model and app data, uninstall, prove gone, undo deviceidle whitelist).
- **`SB fixture` is a host-side private input:** source of truth `/home/yahir/Projects/AndroidApps/Personal/SecondBrain/.planning/cross-repo/sb-a10-fixture.json` (read-only for us, per `10-CONTEXT.md` Runtime Decisions). **It has drifted:** that file is now 37,157 bytes with sha256 prefix `8bc739ed`, while `FixtureLoader.kt:18` pins `ebd3ef4ab509340217e44d88d5d3ccc8718811bc0e029686391b11d79af4ed3e` (a 35,464-byte file, `FixtureLoader.kt` KDoc). `.gitignore:48` ignores `sb-a10-fixture*.json`. The spike must pin its own digest and state which tool surface SB 179 will actually plan against (Open Question 1). `[VERIFIED: ls/sha256sum and FixtureLoader.kt, this session]`

## Device Window and Autonomy Model (Phase 12 12-08 as precedent)

- **Autonomy:** any plan that touches the TESTER is `autonomous: false` with a `checkpoint:human-action` first task (open the window with the orchestrator/Yahir, confirm `adb devices` lists `R5CT10XNKQN` as `device`). In 12-08 the window grant was relayed by the orchestrator and recorded as Runtime Decision RT-01 in `12-CONTEXT.md` (commit `f8d4d73`); the driver, not the executor, announced "device done tester" to the orchestrator (`12-08-SUMMARY.md` lines 19, 55, 60, 63). Copy that: the executor writes the verdict message to a file and a human-action checkpoint has the driver relay it.
- **Grant scope:** a window is a separate grant from spend approval (`12-LIVE-LEG-DECISION.md` "Scope note"). Phase 13 needs **no API keys and no spend** (the D-07 baseline is a recorded v1.0 figure), so the only gate is the device window; no `push-keys`, no `with-test-keys`.
- **Rules from `devices/common.md`:** resolve alias → check availability → act/report; always `adb -s`; never substitute a device; never drive a device someone is testing on; one agent device-tester at a time (lock file); airplane mode is allowed on the wired TESTER only and drops wifi/tailscale/wireless adb (the spike does not need it); `connectedAndroidTest` wipes app data (n/a). `[CITED: ~/.claude/context/devices/common.md]`
- **Overlaps:** Phase 12's live leg is finished (`12-LIVE-LEG-DECISION.md`: window closed 2026-10-05T16:03). Phase 19's Gate-1 must not overlap; the Phase 13 window closes with `cleanup` proving the package is gone and the model file removed, and the device cooled, before Phase 19 ever starts (PITFALLS: the spike can leave a multi-GB model, a stale install, a throttled device).
- **Read-only observation done during this research:** only `adb devices` (host-side) was run; it lists `R5CT10XNKQN device` and `100.118.21.106:1496 device`. No `adb shell` or other device command was issued. All device facts below (RAM, free storage, GPU libs, page size) are therefore unmeasured and are the first preflight rows.

## Suggested Plan Decomposition (planner may refine; the constraints are the point)

Per-wave Gradle cap: **at most 2 Gradle-running plans per wave**, each run with `GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx1536m"`, real exit status captured (never behind `| tail`), never `./gradlew --stop` while another project's daemon is live. `[CITED: ~/.claude/projects/.../memory/vae-release-cut-host-oom.md; 12-RESEARCH.md uses -Dorg.gradle.workers.max=2]`

| Wave | Plan | Autonomous | Gradle? | Content |
|------|------|------------|---------|---------|
| 1 | 13-01 Toolchain & thresholds | yes | yes (AGP app assemble of a tiny module) | Scaffold `:spike-ondevice` (empty harness), add pinned catalog entry, `assembleDebug` + `dependencyInsight releaseRuntimeClasspath` + `zipalign -c -P 16` + APK size delta, verify D8 inside AGP; record `0.17.1` vs `0.16.1` rows (D-02); write and commit `13-THRESHOLDS.md` (D-07 verbatim + the two clarifications) |
| 1 | 13-02 SC4 hardening (D-09) | yes | yes (`:core:test`, scanners, negative controls) | tokens (core test + positive controls), per-module `verifyNoMlArtifacts`, hygiene patterns + `.gitignore`, negative-control plants |
| 2 | 13-03 Scoring & verdict engine | yes | yes (JVM tests only) | `Scoring.kt` (Wilson, percentiles, schema subset validator), `EvidenceLine` grammar, `Thresholds.kt`, `VerdictRules`, `verify-spike-verdict.sh`; gold set (small, synthetic EN/ES/negatives, committed) and the private SB gold-label loader (gitignored path, digest-pinned) |
| 2 | 13-04 Provider, ladder, runner | yes | yes (module build + unit tests) | `LlmBackend`/`LiteRtBackend`, `SpikeOnDeviceProvider` Routes A/B, `SpikeOnDeviceCapability`, ladder + probes + activity, `run-spike-ondevice.sh`, `verify-spike-device-guard.sh`, `spike-evidence-filter.sh` |
| 3 | 13-05 TESTER window and measurement | **no** (checkpoint) | no | Human-action: open window; model downloads on host (Gemma 3 1B needs Yahir's HF acceptance, optional); `preflight` → ladder (below) → `capture-save` → `cleanup`; interim early-red message if a gating row fails decisively |
| 4 | 13-06 Verdict and message | partly | no (`verify-spike-verdict.sh`) | Compute verdict from evidence, write `13-VERDICT.md` + `13-VERDICT-MESSAGE.md`; human-action: driver relays to the orchestrator (SPIKE-02) |
| 5 | 13-07 Disposition | yes | yes (clean-tree gates) | **Red:** delete `spike-ondevice/`, catalog entry, `settings.gradle.kts` include; run D-09 gates; mark SPIKE-03 N/A-deferred. **Green and fits time-box:** `:ondevice` per D-08 (rebases onto Phase 17 plumbing). **Green, overruns:** record 13.1 insertion; SPIKE-02 is never delayed |

**Measurement ladder (order = early-exit order; gating rows first):**

| # | Stage | What is recorded | Early-exit rule |
|---|-------|------------------|-----------------|
| 0 | Preflight (read-only on device) | `getprop` model/SDK/ABI, `/proc/meminfo` total/available, free storage vs model sizes, page size, GPU libs present, battery/thermal start | storage or ABI insufficient → INFRA, not red |
| 1 | Model push + sha256 verify | host and device digests | mismatch → INFRA |
| 2 | Init: cold (process-cold) CPU and GPU, first-ever GPU | init seconds, peak PSS at load, `gpu_adreno730` row | `Engine.initialize()` fails on both backends → red (`toolchain`/`runtime`) |
| 3 | Prefill throughput at ~1k / ~4k / ~7k prompt tokens (use `BenchmarkInfo` or `BenchmarkKt.benchmark`) | prefill tok/s, TTFT | extrapolated SB prefill > 2x the p95 bar on every backend with `kv_reuse = not_reused` → SB envelope red, skip SB trials |
| 4 | `kv_reuse` probe | prefill token count / TTFT, 2nd vs 1st conversation | informational for small |
| 5 | `rf_enforced` / `rf_enum_nested` matrix | per-feature disposition | Route A unsupported → Route B only |
| 6 | Screen (N=20/cell) small envelope: {E2B, 1B} x {CPU, GPU} x {A, B} | schema-valid, latency | drop cells that cannot approach the bar |
| 7 | Confirm winning cell, small envelope | N>=100 schema; >=50 EN, >=50 ES semantic; >=30 negative | any gating metric fails at full N → red for small |
| 8 | Screen + confirm SB-sized envelope (E2B only) | same metrics with the private fixture and private labels | per stage 3 rule |
| 9 | Sustained (60+ consecutive, 2+ minutes) | p50 ratio vs warm, thermal status, PSS | `SEVERE` thermal or p50 > 1.5x warm → red for that envelope |
| 10 | Cleanup | package gone, model removed, evidence pulled | — |

**Declared time-box (SC1 requires one; CONTEXT does not state a number):** propose one TESTER window of at most 4 hours wall-clock, with an unmeasured gating metric at expiry = red (D-07). `[ASSUMED]`, to be confirmed by the orchestrator in the window-grant relay; it must be written into `13-THRESHOLDS.md` before the first device step.

## Assumptions Log

> Claims tagged `[ASSUMED]` need user (or orchestrator) confirmation before they become locked decisions.

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | The S22 (SM-S908U, Snapdragon 8 Gen 1) is slower than Google's S26 Ultra benchmark in prefill and decode, so SB-envelope latency is likely red without KV reuse | Pitfall 7, ladder | Wasted ladder effort if wrong; ordering of rows is cheap to adjust, so low risk |
| A2 | `implementation` keeps litertlm and its Kotlin 2.4 reflect/stdlib off consumers' compile classpath (runtime-only) | Pitfall 10, D-10 table | A compile-classpath leak would force SB/CT to read Kotlin 2.4 metadata; confirmed or refuted by `dependencyInsight` in plan 13-01 |
| A3 | AGP 9.2.1 bundles a D8/R8 at least as new as the SDK D8 8.10.9 that dexed Java-21 classes | D-10 table | Dex failure inside Gradle; falsified cheaply in plan 13-01 (and D-02 handles it: pin 0.16.1) |
| A4 | A 4-hour single-window time-box is acceptable; the orchestrator relays the grant | Decomposition | The window may be shorter or longer; thresholds file must state whatever is granted |
| A5 | The Gemma 4 HF repack (`litert-community`) is the official Google-published mirror and its `.litertlm` is a conversion of `google/gemma-4-E2B-it` | Package audit | Wrong provenance would invalidate the license reading; API tags show the base-model link, so low risk |
| A6 | Apache-2.0 4(a)-(d) is the whole redistribution obligation for the converted weights; no lawyer reviewed this | D-10 table | Only matters if a consumer bundles weights; the library does not |
| A7 | TESTER RAM is 8 GB or 12 GB (S22 Ultra configurations) and has >= 5.3 GB free storage | Ladder stage 0 | Preflight measures it; insufficient storage is INFRA |
| A8 | Pushing the model to `/sdcard/Android/data/<pkg>/files/` with `adb -s` works and is readable by the app on Android 15 (alternative: `run-as ... cat >` as `run-sample-gate1.sh` does for the fixture) | Runner design | Fall back to the `run-as` pipe (slow for 2.6 GB but proven for small files) |
| A9 | `ExperimentalFlags.enableConversationConstrainedDecoding` may need to be true for `ResponseFormat` enforcement | Pitfall 1 | Resolved empirically in the `rf_enforced` row |
| A10 | Gemma tokenization of the SB prefix is within ±25% of Anthropic's 7,016 tokens | Pitfall 2 | Recorded per envelope from `lastPrefillTokenCount`; no plan impact |

## Open Questions

1. **Which SB fixture version and which gold labels does SB 179 plan against?**
   - What we know: the host copy at the SB path is 37,157 B (sha prefix `8bc739ed`), not the 35,464 B `ebd3ef4a` pinned in `FixtureLoader.kt`; D-06 requires private gold labels for the SB envelope.
   - What's unclear: who authors the labels (EN, ES, negatives, no-tool) and whether the fixture changed deliberately.
   - Recommendation: ask the orchestrator in the first message (cheap, early); if labels are unavailable, run the SB envelope on a clearly labeled domain-free synthetic 18-tool surrogate of similar token size and mark the verdict "SB-sized (surrogate)", never green-by-default.

2. **Which SingleShot shape does SB 179 / CT 75 actually use: forced single tool, or model-chooses with a clarification/no-op?**
   - What we know: `SingleShotStrategy` forces `snapshot.singleShotTool` by default; negation/"no tool applies" gold items only make sense when the model can decline.
   - What's unclear: whether SB's real single-shot snapshot names one tool per transcript class.
   - Recommendation: measure `forceTool=false` (model-chooses, schema `{tool: enum, arguments}`) as the gating shape and `forceTool=true` on a subset; record both.

3. **Declared time-box and window length** (SC1 requires a time-box; CONTEXT gives none). Recommendation: 4 hours, relayed by the orchestrator (A4).

4. **Is the Gemma 3 1B control worth a human HF step?** It is a control (D-01) and only for the small envelope. Recommendation: ask Yahir once; if no, record `skipped:gated`.

5. **Does the spike module live on `main` during the phase or on a branch?** `git.branching_strategy` is `none`. Recommendation: commit it on `main` as normal GSD work and delete it in plan 13-07 on red (history preserves it; the verdict records the last harness SHA), so Phases 14-18 never see a stale include.

6. **Metric definitions that D-07 leaves implicit** (PSS source, "schema-valid" point vs bound, cold definition, trial counting for retries). Recommendation: pin them in `13-THRESHOLDS.md` per Pitfalls 3-4 and the Wilson table; send the lock to the orchestrator.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 17 | Gradle, AGP | ✓ | OpenJDK 17.0.19 | — |
| Android SDK (platforms 35/36/36.1/37.0, build-tools 34-37, cmdline-tools) | AGP build, `d8`, `zipalign`, `apkanalyzer` | ✓ | build-tools 36.0.0 D8 8.10.9 | — |
| `adb` | device steps | ✓ | `/usr/bin/adb` | — |
| TESTER `R5CT10XNKQN` | measurement | listed as `device` by host-side `adb devices` (not driven) | SM-S908U per notes | window grant required; INFRA if offline |
| Gradle caches for Kotlin 2.3.20 / AGP 9.2.1 | builds | ✓ (offline-capable) | KGP 2.3.20, compiler-embeddable 2.3.20 in cache | — |
| litertlm 0.17.1 / 0.16.1 in Gradle cache | first resolve | ✗ (not cached) | — | one online resolve; later `--offline` |
| Network: Google Maven, Maven Central, huggingface.co | downloads | ✓ (reached this session) | — | — |
| Host RAM | Gradle | 32 GB total, ~10 GB available at research time; **swap 2,047 MB of 2,047 MB used** | — | cap Gradle (recipe above); if swap is still full, ask Yahir for `sudo swapoff -a && sudo swapon -a` before heavy runs; never `kill` a running `mempalace mine` |
| Host disk | model downloads (2.6 + 2.0 + 0.6 GB) | ✓ ~31 GB free of 458 GB (93% used) | — | download one variant at a time; delete after push |
| `curl`, `python3`, `jq`, `readelf`, `unzip`, `javap`, `sha256sum`, `flock` | scripts | ✓ | — | — |
| HF access token (Gemma 3 1B only) | gated download | ✗ unknown | — | skip the 1B control (Pitfall 13) |
| API keys / `with-test-keys` | — | not needed | — | — |
| `gsd-tools package-legitimacy` for Maven | audit | ✗ (npm/pypi/crates only) | — | manual evidence (done above) |

**Missing dependencies with no fallback:** none blocking planning. The TESTER window grant blocks only plan 13-05.
**Missing dependencies with fallback:** litertlm in the cache (one online resolve); HF token (skip control row); host swap (cap and, if needed, ask for a swap reset).

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 4.13.2 + kotlinx-coroutines-test 1.11.0 (repo standard); hand-written fakes (no MockK/Mockito per STACK) |
| Config file | per-module `build.gradle.kts`; `config/detekt/detekt.yml` (published modules); `gradle/invariants.gradle.kts`; new `scripts/*spike*` |
| Quick run command | `./gradlew :spike-ondevice:testDebugUnitTest --tests '<Class>' --offline -q` (with the low-memory GRADLE_OPTS) |
| Full suite command | `./gradlew :core:test :core:detekt :core:scanBannedConstructs :providers:detekt :providers:scanBannedConstructs :keystore:detekt :keystore:scanBannedConstructs :spike-ondevice:testDebugUnitTest --offline`, then `scripts/verify-repo-hygiene.sh`, `scripts/verify-spike-device-guard.sh`, `scripts/verify-spike-verdict.sh`, and (D-09 gate) `scripts/verify-negative-controls.sh` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SPIKE-01 | Evidence is complete and well-formed: every gating row present for each envelope with N; an absent gating row yields red `unmeasured:<metric>` | unit (JVM) | `./gradlew :spike-ondevice:testDebugUnitTest --tests '*VerdictRulesTest*' --tests '*EvidenceLineTest*'` | ❌ Wave 0 |
| SPIKE-01 | Wilson, percentile and schema-valid scoring match the oracle table | unit (JVM) | `... --tests '*ScoringTest*'` | ❌ Wave 0 |
| SPIKE-01 | Route A / Route B mappers turn a canned `LlmBackend` answer into `ModelResult.Success(ToolCall)` and map failures to stable codes | unit (JVM, fake backend) | `... --tests '*SpikeProviderTest*'` | ❌ Wave 0 |
| SPIKE-01 | The measurement itself (latency, PSS, thermal, accuracy on the TESTER) | **manual-only (device)** | `scripts/run-spike-ondevice.sh preflight/run/capture-save/cleanup` | ❌ Wave 0; justified: only the real S22 produces the numbers |
| SPIKE-01 | Toolchain: AGP assemble, D8, stdlib resolution, 16 KB zipalign, APK delta | build | `./gradlew :spike-ondevice:assembleDebug dependencies ...`, `zipalign -v -c -P 16 4 <apk>` | ❌ Wave 0 |
| SPIKE-01 | Runner never targets another device | script (fake adb) | `scripts/verify-spike-device-guard.sh` | ❌ Wave 0 |
| SPIKE-01 | Evidence filter rejects prompts, outputs, key shapes | script | `scripts/spike-evidence-filter.sh < golden` (+ negative samples) | ❌ Wave 0 |
| SPIKE-02 | `13-VERDICT.md` equals the verdict recomputed from committed evidence under the committed thresholds | script | `scripts/verify-spike-verdict.sh` | ❌ Wave 0 |
| SPIKE-02 | The message file contains the verdict, per-envelope numbers and the three D-10 rows; relay recorded | doc check + human-action | `grep` checks on `13-VERDICT-MESSAGE.md`; relay noted in the SUMMARY | ❌ Wave 0 |
| SPIKE-03 (red) | No spike code or ML reference ships: `spike-ondevice/` absent, no `litertlm` in `libs.versions.toml`, `settings.gradle.kts`, any `build.gradle.kts`, `jitpack.yml`; tokens only in the D-09 denial lists | script | `git ls-files \| grep -i litert` limited to the allowed denial files; `scripts/verify-repo-hygiene.sh` | ❌ Wave 0 |
| SPIKE-03 (green) | `:ondevice` gates: `check`, `api.txt`, opt-in negative compile (`@RequiresOptIn(ERROR)`), unavailable-gate fallback unchanged, no LiteRT type in `api.txt` | build + unit | `./gradlew :ondevice:check apiCheck` + the existing `OnDeviceGateTest` | ❌ Wave 0 (only if green) |
| SC4 | `:core` test flags LiteRT/MediaPipe tokens and passes the monotonic clock line | unit | `./gradlew :core:test --tests '*NoHardCodedConstantsTest*'` | ✅ extend existing |
| SC4 | An ML artifact on `:core`/`:providers`/`:keystore` classpath fails the build | gate | `./gradlew :providers:verifyNoMlArtifacts` (new) + negative-control plant | ❌ Wave 0 |
| SC4 | Plants go red for the right reason | script | `scripts/verify-negative-controls.sh` | ✅ extend existing |

### Sampling Rate

- **Per task commit:** the single touched test class (command above), one Gradle at a time.
- **Per wave merge:** `./gradlew :core:test :spike-ondevice:testDebugUnitTest` plus `scripts/verify-spike-device-guard.sh` and `scripts/verify-repo-hygiene.sh`; never more than two Gradle-running plans concurrently.
- **Phase gate:** full suite above green; `13-VERDICT.md` reproduced by `verify-spike-verdict.sh`; for red, the deletion checklist script passes; then `/gsd-verify-work`.

### Wave 0 Gaps

- [ ] `spike-ondevice/` module scaffold + catalog pin (13-01)
- [ ] `13-THRESHOLDS.md` committed before any device step (13-01)
- [ ] `Scoring`/`EvidenceLine`/`VerdictRules` test classes with the Wilson oracle table (13-03)
- [ ] fake `LlmBackend` + provider tests (13-04)
- [ ] `run-spike-ondevice.sh`, `verify-spike-device-guard.sh`, `spike-evidence-filter.sh`, `verify-spike-verdict.sh` (13-03/13-04)
- [ ] `verifyNoMlArtifacts` task + negative-control plants + hygiene patterns + `.gitignore` entries (13-02)
- [ ] Gold sets: small (committed, synthetic, EN/ES/negatives) and the private SB loader (gitignored path)
- [ ] Framework install: none (JUnit and coroutines-test already pinned)

## Security Domain

> `security_enforcement` is enabled (absent or true in `.planning/config.json`; ASVS level 1).

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | no credentials; `requiresCredential = false` for `ON_DEVICE` |
| V3 Session Management | no | — |
| V4 Access Control | yes | TESTER-only guard + shared lock; write gating stays in the app's `PreApplyGate` (no on-device write ever bypasses it; the spike uses a no-op sink and canned executor, never a real app write path) |
| V5 Input Validation | yes | model output is untrusted: constrained JSON → `kotlinx.serialization` parse → tool-schema validation → the app's `OutcomeResolver`; a schema-invalid answer is a typed outcome, never executed |
| V6 Cryptography | partial | integrity only: sha256 of model files (host and device); no key material in this phase |
| V8 Data Protection | yes | transcripts, prompts, model output and tool args never reach logs or committed files (closed-vocabulary evidence + allow-list filter); SB fixture and SB gold labels stay gitignored and off the committed tree |
| V14 Configuration / supply chain | yes | exact version pin for litertlm; model sha256 pins; no model, `.task`/`.litertlm`/`.bin` in git (hygiene gate) |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Wrong-device action (personal phone) | Tampering | runner copies the proven guard: `-s R5CT10XNKQN`, identity proof, personal-IP refusal, fake-adb scenario proof |
| Model output injected into a write path | Tampering / Elevation | spike never writes; if green, documented app-side gating (`PendingMutation.context` tag) keeps the pre-apply gate in force (D-08) |
| Prompt/output leakage via logcat or evidence file | Information disclosure | closed grammar + allow-list filter + whole-capture rejection; never log text |
| Model file tampering / truncation | Tampering | sha256 before push and after (host and device); pinned digests in this document |
| ML dependency creeping into `:core`/`:providers` | Tampering (supply chain) | `verifyCoreDependencyAllowlist` (exists), `verifyNoMlArtifacts` (new), scanner/test tokens (D-09), negative-control plants |
| Native crash or OOM takes down the host app | Denial of service | measured as `process_death` rows; if green the provider must lazy-load, refuse under low memory (typed `insufficient_memory`), release on `onTrimMemory` (PITFALLS #7) |
| Stale model/APK left on the TESTER | Information disclosure / DoS for the next phase | `cleanup` proves package gone and model removed; device cooled before Phase 19 |

## Sources

### Primary (HIGH confidence)
- Local, this session: `litertlm-android` 0.17.1 and 0.16.1 AAR/POM from `dl.google.com/android/maven2` (class-file parse of `classes.jar`: major 65 and Kotlin metadata 2.4.0 / 2.3.0; `javap` of `Engine`, `EngineConfig`, `Conversation`, `ConversationConfig`, `Session`, `ResponseFormat`, `ExperimentalFlags`, `ExperimentalApi`, `BenchmarkInfo`, `Capabilities`, `OpenApiTool`, `ToolProvider`; `readelf -lW` of `liblitertlm_jni.so`; `d8` run). Scratch artifacts under the session scratchpad.
- Local compiler runs: `kotlin-compiler-embeddable` 2.3.20 (exit 0) and 2.2.10 (exit 1, quoted error) against the AARs; compile-verified `AiProvider` skeleton against `core/build/libs/core.jar`.
- Repo files read this session (paths and line ranges inline above): `gradle/invariants.gradle.kts`, `build.gradle.kts`, `settings.gradle.kts`, `jitpack.yml`, `gradle/libs.versions.toml`, `core/src/main/.../{OnDeviceCapability,AiProvider,ModelResult,ProviderRequest,ModelCapabilities,ModelRouter,PipelineBuilder,SingleShotStrategy,ToolSpec,ModelRequest,ProviderId,ReasonSupport,FailureReason}.kt`, `core/src/test/.../NoHardCodedConstantsTest.kt`, `scripts/{run-sample-gate1,verify-sample-device-guard,sample-evidence-filter,verify-repo-hygiene,verify-negative-controls}.sh`, `config/detekt/detekt.yml`, `sample/build.gradle.kts`, `sample/.../FixtureLoader.kt`, Phase 12 `12-08-PLAN.md`, `12-08-SUMMARY.md`, `12-LIVE-LEG-DECISION.md`, `12-RESEARCH.md` (Validation section).
- Hugging Face API (`huggingface.co/api/models/litert-community/gemma-4-E2B-it-litert-lm` and `/tree/main`, `litert-community/Gemma3-1B-IT`): tags, sizes, LFS sha256, gating.
- Maven Central: `kotlin-reflect-2.4.0.pom` and `kotlin-reflect-2.2.21.pom` (stdlib dependency).

### Secondary (MEDIUM confidence)
- [LiteRT-LM Android guide](https://developers.google.com/edge/litert-lm/android) (via page summarizer): dependency coordinate, `Backend.GPU()` manifest entries, `initialize()` can take up to ~10 s, no KV/prefix reuse text, models named (Gemma3-1B-IT, Gemma3n, FunctionGemma).
- [LiteRT-LM releases](https://github.com/google-ai-edge/LiteRT-LM/releases) (via summarizer; its year labels were wrong, Maven metadata is authoritative for dates): 0.17.0 local-attention memory optimization, 0.17.1 tool-call integer fix.
- [Gemma terms](https://ai.google.dev/gemma/terms), [Gemma 4 / Apache-2.0 page](https://ai.google.dev/gemma/apache_2), [HF model card](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm) and [base card](https://huggingface.co/google/gemma-4-E2B-it) (via summarizer): license split, S26 Ultra benchmark table, 2,048-token benchmark context.
- [Android 16 KB page-size guide](https://developer.android.com/guide/practices/page-sizes): deadline February 1, 2027, AGP >= 8.5.1, NDK >= r28, `zipalign -c -P 16`.
- [AGP JniLibsPackaging](https://developer.android.com/reference/tools/gradle-api/8.3/null/com/android/build/api/dsl/JniLibsPackaging): uncompressed, page-aligned `.so` when minSdk >= 23.
- Milestone research under `.planning/research/` (STACK, PITFALLS, SUMMARY, ARCHITECTURE) and `v1.1-DECISION-MAP.md` Phase 13 (D-07 wording).

### Tertiary (LOW confidence)
- Performance priors for the S22 (A1) and any extrapolation from the S26 card; replaced by measurement.
- Thermal-throttle percentages quoted from PITFALLS #7 (public reports, not re-verified).

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH. Versions, sizes, digests, licenses and POM graphs were read from primary sources this session.
- Toolchain proof: HIGH for compile, class level, alignment and D8 (executed). MEDIUM for AGP-internal behavior and consumer classpath leak (assumed, falsifiable in plan 13-01).
- Architecture / repo seams: HIGH (files read, line ranges and verbatim quotes above).
- Runtime behavior (ResponseFormat enforcement, KV reuse, GPU init, any latency/RAM figure): LOW until measured; that is the phase's purpose and each is a named verdict row.
- Pitfalls: HIGH for repo/gate pitfalls (verified in code), MEDIUM for device behavior (Freecess, thermal, page cache) from project notes.

**Research date:** 2026-10-05
**Valid until:** 2026-10-12 for LiteRT-LM versions and model revisions (fast-moving: 4 releases in ~6 weeks); 30 days for repo seams and gate mechanics unless Phases 14-17 land first.
