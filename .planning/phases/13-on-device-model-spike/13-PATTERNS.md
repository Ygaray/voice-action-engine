# Phase 13: On-Device Model Spike - Pattern Map

**Mapped:** 2026-10-05
**Files analyzed:** 14 new/modified
**Analogs found:** 13 / 14 (all analog paths verified git-tracked; no `.gsd/` mirror paths used)

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `spike-ondevice/build.gradle.kts` | config | build | `sample/build.gradle.kts` | exact |
| `settings.gradle.kts` (+`include(":spike-ondevice")`) | config | build | same file line 22 | exact |
| `gradle/libs.versions.toml` (+litertlm pin) | config | build | existing catalog entries | exact |
| `spike-ondevice/.../provider/SpikeOnDeviceProvider.kt` | provider (AiProvider) | request-response | `providers/.../chat/ChatCompletionsProvider.kt`, `sample/.../legs/DemoProvider.kt` | role-match |
| `spike-ondevice/.../gate/SpikeOnDeviceCapability.kt` | provider/seam | request-response | `core/.../provider/OnDeviceCapability.kt` (the interface) | role-match |
| `spike-ondevice/.../evidence/EvidenceLine.kt` | utility (closed grammar) | transform | `sample/.../evidence/EvidenceLine.kt` | exact |
| `spike-ondevice/.../verdict/VerdictRules.kt`, `Thresholds.kt` | utility (pure rules) | transform | `sample/.../verdict/SmokeVerdict.kt`, `CacheVerdict.kt` | exact |
| `spike-ondevice/src/test/...` | test | transform | `core/src/testFixtures/.../FakeAiProvider.kt` + `sample/src/test` | role-match |
| `scripts/run-spike-ondevice.sh` | script (device guard) | request-response | `scripts/run-sample-gate1.sh` | exact |
| `scripts/verify-spike-device-guard.sh` | test script | request-response | `scripts/verify-sample-device-guard.sh` | exact |
| `scripts/spike-evidence-filter.sh` | utility | streaming/filter | `scripts/sample-evidence-filter.sh` | exact |
| `scripts/verify-spike-verdict.sh` | script | batch | `scripts/verify-api-dump.sh` (shape only) | partial |
| `gradle/invariants.gradle.kts` (ML denial per module) | config/gate | batch | `if (project.name == "providers")` block, lines 422-440 | exact |
| `core/.../NoHardCodedConstantsTest.kt`, `scripts/verify-repo-hygiene.sh`, `scripts/verify-negative-controls.sh`, `config/detekt/detekt.yml`, `config/negative-controls/*.kt.txt` | gate/test | batch | themselves (extend) | exact |
| optional `:ondevice` module | library | request-response | `keystore/build.gradle.kts` (Android lib publish form) | role-match |

## Pattern Assignments

### `spike-ondevice/build.gradle.kts` (config)
**Analog:** `sample/build.gradle.kts` lines 1-47. Copy verbatim, then drop compose and the `:providers`/`:keystore`/OkHttp lines.
```kotlin
// Inert app: never published, reads no files or environment at configuration time (JitPack configures every included project).
plugins { alias(libs.plugins.android.application) }   // no kotlin.android: AGP 9 built-in Kotlin
android {
    namespace = "io.github.ygaray.voiceactionengine.spike"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    defaultConfig { applicationId = "..."; minSdk = 35; targetSdk = 36; versionCode = 1; versionName = "0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) } }   // app: no explicitApi
dependencies {
    implementation(project(":core"))
    testImplementation(libs.junit); testImplementation(libs.coroutines.test)
    testImplementation(testFixtures(project(":core")))
}
```
Add `ndk { abiFilters += "arm64-v8a" }` and `implementation(libs.litertlm.android)` per RESEARCH. Do NOT `apply(from = invariants.gradle.kts)` (the script is for published modules; it needs `allowedEdges.getValue(modulePath)`, which would throw for an unknown path). Package root must stay `io.github.ygaray.voiceactionengine.*` (hygiene section a).

### `settings.gradle.kts` / graph gates
Line 22: `include(":core", ":providers", ":keystore", ":sample")`. Add the spike include in the same file. `verifyModuleGraph` (`invariants.gradle.kts:272-305`) only inspects `:sample`'s edges, so the spike module needs no graph change; the spike must depend on `:core` only. Red path deletes the include, catalog entry and module dir (RESEARCH Pitfall 15).

### `SpikeOnDeviceProvider.kt` (AiProvider, request-response)
**Analog:** `ChatCompletionsProvider.kt:47-59` (members: `id`, `capabilities(model)`, `complete`, redacted `toString`). `FakeAiProvider.kt:1-50` for import set.
```kotlin
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.provider.{AiProvider, ModelCapabilities, ModelResult, ProviderRequest}
override val id: ProviderId = ProviderId.ON_DEVICE      // core/ProviderId.kt:28
override val requiresCredential: Boolean = false
override suspend fun complete(call: ProviderRequest): ModelResult
override fun toString(): String = "SpikeOnDeviceProvider(model=$id)"   // never include prompt/output
```
Core/ result types: `ModelResult.Success(ModelResponse(AssistantMessage(listOf(AssistantPart.ToolCall(...))), StopReason.TOOL_USE, Usage(...)))`; failures carry only `[a-z0-9_]+` stable codes (`isStableCode`). Inference is blocking JNI: wrap on a dedicated dispatcher like `ChatCompletionsProvider.Builder.ioDispatcher` (rethrow `CancellationException`).
Test analog: `FakeAiProvider` (public fake in `:core` testFixtures; script runs dry -> `AssertionError` on purpose).

### `SpikeOnDeviceCapability.kt` (gate)
**Analog:** `core/.../provider/OnDeviceCapability.kt:13-16`: `public fun interface OnDeviceCapability { suspend fun availability(): OnDeviceAvailability }`. Return `OnDeviceAvailability.Available()` / `Downloadable()` / `Unavailable(code)`; code must be a stable code. Default in core is `Unavailable("not_implemented")`.

### `evidence/EvidenceLine.kt` (closed grammar)
**Analog:** `sample/.../evidence/EvidenceLine.kt:56-58`; host mirror `scripts/sample-evidence-filter.sh:15`.
```kotlin
internal const val ALLOW_PATTERN =
    "^VAE_(ENV|FIXTURE|...)" + "( [a-z0-9_]+=[A-Za-z0-9_.:/,\\[\\]-]{0,96})+$"
```
```bash
ALLOW_RE='^VAE_(ENV|...)( [a-z0-9_]+=[][A-Za-z0-9_.:/,-]{0,96})+$'
```
Use a distinct prefix `VAE_SPIKE_(KIND)` and keep the Kotlin regex and the bash ERE in parity (the guard verifier proves golden lines pass both). Note the bash class omits `[]`-escape differences; keep byte-equivalent char sets.

### `verdict/VerdictRules.kt`, `Thresholds.kt` (pure)
**Analog:** `sample/.../verdict/SmokeVerdict.kt`, `CacheVerdict.kt`, `VerdictTypes.kt` (pure functions over evidence, JVM-tested). Unmeasured gating metric -> red `unmeasured:<metric>` (D-07). Magic-number rule: thresholds live in `Thresholds.kt` as named constants; this module is outside detekt/library scans, but keep them in one file.

### `scripts/run-spike-ondevice.sh` (device guard)
**Analog:** `scripts/run-sample-gate1.sh`. Copy lines 1-93 preamble: TESTER constants (`R5CT10XNKQN`, `100.118.21.106:1496`, `PERSONAL_IP`, `SM-S908U`, `MIN_SDK=35`), same `LOCK_FILE`, `ADB` override, `ROOT`, `finish()`:
```bash
LOCK_FILE="${XDG_RUNTIME_DIR:-/tmp}/vae-keystore-tester.lock"      # SAME lock as keystore + sample runners
finish() { local code="$1" outcome="$2" details="$3"; rm_staging; rm_tmp
  echo "SAMPLE_GATE1: $outcome sub=$SUB $details"; exit "$code"; }   # rename to SPIKE_ONDEVICE:
```
Exit codes 0 OK, 1 FAIL, 2 ERROR, 3 INFRA busy/offline, 4 INFRA refused. Every adb call `-s <target>`. Retarget `PHASE_DIR` through an env var like `VAE_GATE1_PHASE_DIR` (default currently the archived-path hazard noted in D-03: set the spike's own default to `.planning/phases/13-on-device-model-spike`). Replace `PKG`, `LEGS`, `SUBCOMMANDS` (preflight, build-install, push-model with sha256 compare, run-stage, capture-save, cleanup). Cap adb push verification via sha256 on both ends.

### `scripts/verify-spike-device-guard.sh`
**Analog:** `scripts/verify-sample-device-guard.sh:1-30`: fake adb + fake push-test-key in a throwaway repo skeleton, flock check, `PHASE_REL`/`PKG` constants, final line `... GUARD OK scenarios=<n>`; assert every logged adb call starts with `-s R5CT10XNKQN ` or `-s <wifi> ` and refusals never reach install/push/run-as/gradle. Key shapes assembled from fragments (`KEY_SHAPE="...s""k-..."`) so the secret hook does not trip.

### `scripts/spike-evidence-filter.sh`
**Analog:** `scripts/sample-evidence-filter.sh:14-40` (ALLOW_RE, fragment-assembled `KEY_RE`, `tr -d '\r'`, `mktemp` + `trap`, "FILTER OK kept=<n> dropped=<m>" on stderr, `LEAK SCAN FAIL` exit 1 with empty stdout). Drop the ver02 `fixture_leak` block (or replace with spike-specific "no gold-label text" checks).

### `gradle/invariants.gradle.kts` (D-09 ML denial, module-scoped)
**Analog:** lines 332 / 422 module-name guards, and the dependency-denial task at 309-330 (`verifyNoDiArtifacts`: `classpathNames`, `incoming.resolutionResult.allComponents`, `hits` -> `GradleException`).
```kotlin
val deniedMlGroups = setOf("com.google.ai.edge.litertlm", "com.google.mediapipe", "com.google.mlkit" /* + ai-edge/tflite */)
// register verifyNoMlArtifacts exactly like verifyNoDiArtifacts; wrap in
if (project.name in setOf("core", "providers", "keystore")) { ... tasks.named("check") { dependsOn(it) } }
```
Pitfall 9: do NOT add LiteRT tokens to the shared `bannedRules` list (line 13-33) or `config/detekt/detekt.yml:26-36` `ForbiddenImport`; those apply to the future `:ondevice` too. Source half, if wanted, is a separate `Rule` list gated by the same module-name guard and feeding `scanText`. Note the invariants file is never scanned by itself (the "deny list lives in a .kts" comment, line 36-39).

### `NoHardCodedConstantsTest.kt` (SC4 token test)
**Analog:** same file lines 57-62 and 75-82, 134-137 (`onDevice` regex list, `onDeviceHit` skips comment lines, `noOnDeviceImplementationCode` over `:core` main only). Extend by adding `litert`, `gemma` regexes to the list (case-insensitive, same shape) and add a synthetic-file assertion in the `scanFlagsAViolatingSyntheticFileAndPassesACleanOne` style (lines 140-147). Scope stays `:core/src/main`; extending to `:providers` means a parallel scan of that module (a separate test in `:providers`, or the gradle-side task above). Keep the existing exemption of comments so KDoc may state "no LiteRT".

### `scripts/verify-repo-hygiene.sh`
**Analog:** itself. Section a line 27: `find core providers keystore sample ...` (add `spike-ondevice` so the package-root check covers it); section f lines 78-81 `jitpack.yml` regex: extend the `sample` alternation with `spike-ondevice`; section c (forbidden tracked files): add the model extensions (`*.litertlm`, `*.task`, `*.tflite`) and the SB gold label path. Pattern: `violate "<letter>: <message>"` then `echo "HYGIENE OK"` only on zero.

### `scripts/verify-negative-controls.sh`
**Analog:** Part 1 `expect_red "<label>" $m '<kotlin body>' ":$m:detekt" ":$m:scanBannedConstructs"` (lines 54-72) for source plants; Part 2 `backup <file>; printf '\ndependencies { ... }\n' >> <file>; expect_task_red "<label>" "<marker>" :task; restore <file>` (lines ~96-100).
```bash
backup providers/build.gradle.kts
printf '\ndependencies { implementation("com.google.ai.edge.litertlm:litertlm-android:0.17.1") }\n' >> providers/build.gradle.kts
expect_task_red "providers gains an ML dependency" "ML artifact" :providers:verifyNoMlArtifacts
restore providers/build.gradle.kts
```
Marker text must match the new GradleException message. Add a source plant `import com.google.ai.edge.litertlm.Engine` per `$m` only if a source-level rule is added. Update the Part header comment. Plants must be restored by the trap (byte-identical assertion at end).

### Optional `:ondevice` (green path)
**Analog:** `keystore/build.gradle.kts:1-25` (`android.library` + `maven-publish` + detekt + metalava, `singleVariant("release") { withSourcesJar() }`, `apply(from = rootProject.file("gradle/invariants.gradle.kts"))` at line 129). Would need: `allowedEdges` entry `":ondevice" to setOf(":core")` (invariants line 272), `jitpack.yml` install list, `api.txt` at tag cut, `verify-api-dump`/hygiene lists, and `implementation` (never `api`) for litertlm. Defer to 13.1 if outside the time-box (D-08).

## Shared Patterns

### Never-log / closed vocabulary
**Sources:** `scripts/sample-evidence-filter.sh:15-17`, `EvidenceLine.kt:56`. Apply to every harness log line, evidence file, `toString()`: `VAE_SPIKE_<KIND> k=v` only; no prompt, output, tool args; `Log` is allowed in the unpublished spike (ForbiddenImport applies to published modules only) but only via the evidence emitter.

### Device guard
**Source:** `scripts/run-sample-gate1.sh:25-93`. Apply to any script touching adb: TESTER-only, shared flock, `-s` on every call, never the personal phone, final single status line.

### Gate-red proofs
**Source:** `scripts/verify-negative-controls.sh` Parts 1-2. Every new gate (ML artifact denial, hygiene patterns) gets a plant that must go red for the right marker.

### Module-scoped gates
**Source:** `invariants.gradle.kts:332,422` (`if (project.name == ...)`). Any rule not valid for all published modules is name-guarded, never global.

## No Analog Found

| File | Role | Reason |
|---|---|---|
| `LiteRtBackend.kt` / `LlmBackend.kt` (JNI wrapper, benchmark probes, PSS/thermal sampling) | adapter | No on-device or Android-framework-probe code exists; use RESEARCH Pattern 1/4 skeleton (compile-verified) |
| `Scoring.kt` (Wilson interval, percentiles, subset JSON-schema validator) | utility | No statistics code exists; use RESEARCH Validation Architecture table, unit-test first |
| `scripts/verify-spike-verdict.sh` | script | Only loose shape from `verify-api-dump.sh`; recomputes verdict via a Gradle JVM task |

## Metadata

**Analog search scope:** `core/`, `providers/`, `keystore/`, `sample/`, `scripts/`, `gradle/`, `config/`
**Pattern extraction date:** 2026-10-05
