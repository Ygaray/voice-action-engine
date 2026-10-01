---
phase: 03-transcript-types-providerrouter-on-device-gate
plan: 05
subsystem: core
status: complete
tags: [kotlin, provider-contract, fake-provider, tool-spec, api-growth, prov-01, prov-02]

requires:
  - phase: 03-transcript-types-providerrouter-on-device-gate
    provides: "ModelRequest, ModelResponse, AssistantMessage, StopReason (03-01); ModelCapabilities, CachingMode (03-02)"
provides:
  - "AiProvider: id, requiresCredential (default true), capabilities(model) (default UNKNOWN), suspend complete(call)"
  - "ProviderCall(model, request, credential, capabilities) with redacted toString"
  - "ModelResult open base with Success(response) and Failure(reason, details) / Failure(reason)"
  - "FakeAiProvider and ProviderStep in core testFixtures"
  - "ToolSpec.strict: Boolean? via @JvmOverloads"
  - "ApiShapeTest growth rule: no default-argument constructor stubs in transcript and provider packages"
affects: [03-06, 03-07, 03-08, 03-09]

plan_head_before: 4f14bd9272f00ad81ec17e2eb1d28a822337c5eb

commits: 3

actuals:
  tokens: 9500   # chars/4 over the realized diff (about 38,000 chars)
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Open result type: abstract class with internal constructor and public nested leaves"
    - "Constructor growth on a Kotlin class that already uses default arguments: @JvmOverloads keeps the existing JVM constructor and adds shorter and longer shapes"
    - "Reflection-based growth rule with a positive control in the same test class"

key-files:
  created:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/AiProvider.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/ProviderCall.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/ModelResult.kt
    - core/src/testFixtures/kotlin/io/github/ygaray/voiceactionengine/core/testing/FakeAiProvider.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/FakeAiProviderTest.kt
  modified:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/ToolSpec.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ApiShapeTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ToolSpecClarificationTest.kt

key-decisions:
  - "FakeAiProvider's primary constructor takes (id, steps, capabilities, requiresCredential) with testFixture defaults; secondary constructors take a vararg of ModelResult or (capabilities, vararg ProviderStep)"
  - "Exhaustion throws AssertionError (an Error) so the engine's guarded collapse does not swallow an unplanned provider call; the remaining-count check runs inside a lock before ScriptedResponses.next"
  - "The growth rule only flags a synthetic int + DefaultConstructorMarker constructor when a real constructor with the leading parameters exists, so value-class stubs are not flagged"

requirements-completed: [PROV-01, PROV-02]

coverage:
  - id: D1
    description: "A single-shot ModelRequest goes through a ProviderCall into FakeAiProvider.complete and returns ModelResult.Success with the scripted text, END_TURN stop reason and usage; the same call instance is recorded"
    requirement: "PROV-02"
    verification:
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/FakeAiProviderTest.kt#singleShotRequestReachesTheFakeAndTheScriptedResponseComesBack"
        status: pass
    human_judgment: false
  - id: D2
    description: "AiProvider defaults (UNKNOWN capabilities, requiresCredential true), the fake's declared capabilities and credential flag, ordered playback, lambda steps seeing the call, and a loud AssertionError on exhaustion"
    requirement: "PROV-02"
    verification:
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/FakeAiProviderTest.kt#aProviderImplementingOnlyIdAndCompleteGetsTheDefaults"
        status: pass
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/FakeAiProviderTest.kt#aCallPastTheScriptThrowsAnAssertionErrorNamingExhaustion"
        status: pass
    human_judgment: false
  - id: D3
    description: "ProviderCall rejects a blank model and its toString carries only the model, counts and credential provider (canary key, system prompt and user text absent); Failure and Success toStrings are redaction-safe"
    requirement: "PROV-02"
    verification:
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/FakeAiProviderTest.kt#providerCallToStringCarriesCountsAndProviderButNoSecret"
        status: pass
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/FakeAiProviderTest.kt#successToStringShowsTheSummaryWithoutTheReplyText"
        status: pass
    human_judgment: false
  - id: D4
    description: "ToolSpec.strict defaults to null, the 3-, 4-, 5- and 6-argument JVM constructors all resolve by reflection, toString prints strict, and terminal-and-mutating is still rejected with strict set"
    requirement: "PROV-01"
    verification:
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ApiShapeTest.kt#toolSpecKeepsItsFiveArgumentConstructorAndAddsTheOtherShapes"
        status: pass
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ToolSpecClarificationTest.kt#terminalAndMutatingTogetherStillFailWhenStrictIsSet"
        status: pass
    human_judgment: false
  - id: D5
    description: "No class in the transcript or provider packages declares a default-argument constructor stub; the check inspects 45 classes including ModelRequest and ProviderCall and flags a deliberate default-argument class"
    requirement: "PROV-01"
    verification:
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ApiShapeTest.kt#noTranscriptOrProviderClassDeclaresADefaultArgumentConstructorStub"
        status: pass
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ApiShapeTest.kt#theGrowthRulePredicateFlagsADefaultArgumentClass"
        status: pass
    human_judgment: false
---

# Phase 3 Plan 05: Provider Contract, FakeAiProvider and ToolSpec.strict Summary

**The AiProvider / ProviderCall / ModelResult contract and a scripted, recording, loud FakeAiProvider now exist in :core, and ToolSpec gained `strict` through @JvmOverloads with a mechanical growth rule guarding the transcript and provider packages.**

## Accomplishments

- A neutral `ModelRequest` reaches a provider through `ProviderCall` and comes back as `ModelResult.Success` carrying the neutral `ModelResponse`; expected failures are `ModelResult.Failure` values, never exceptions.
- `FakeAiProvider` (core testFixtures, public API only) is ready for the router, on-device and cache plans.
- `ToolSpec(name, description, inputSchema, mutating, terminal, strict)`: the existing 5-argument JVM constructor stays, and the 3-, 4- and 6-argument shapes are added.
- `ApiShapeTest` holds the transcript and provider packages to the no-default-argument rule; the check inspected 45 classes.

## Final Signatures (03-06 through 03-09 build on these)

```kotlin
public interface AiProvider {
    public val id: ProviderId
    public val requiresCredential: Boolean get() = true
    public fun capabilities(model: String): ModelCapabilities = ModelCapabilities.UNKNOWN
    public suspend fun complete(call: ProviderCall): ModelResult
}
public class ProviderCall(model: String, request: ModelRequest, credential: Credential?, capabilities: ModelCapabilities)
public abstract class ModelResult internal constructor() {
    public class Success(public val response: ModelResponse)
    public class Failure(public val reason: FailureReason, public val details: FailureDetails?) // + Failure(reason)
}

// testFixtures, package ...core.testing
public typealias ProviderStep = suspend (ProviderCall) -> ModelResult
public class FakeAiProvider(
    override val id: ProviderId,
    steps: List<ProviderStep>,
    capabilities: ModelCapabilities = ModelCapabilities.UNKNOWN,
    override val requiresCredential: Boolean = true,
) : AiProvider {
    constructor(id: ProviderId, vararg results: ModelResult)
    constructor(id: ProviderId, capabilities: ModelCapabilities, vararg steps: ProviderStep)
    val calls: List<ProviderCall>; val callCount: Int
    companion object {
        fun reply(text: String, usage: Usage): ModelResult           // one Text part, END_TURN
        fun toolCall(callId: String, name: String, arguments: JsonObject, usage: Usage): ModelResult // TOOL_USE
    }
}
```

Exhaustion throws `AssertionError("FakeAiProvider <id>: script exhausted after <n> calls")`.

## Task Commits

1. **Task 1 (tracer): provider contract and FakeAiProvider tracer** - `317ead6` (feat)
2. **Task 2: defaults, redaction, lambda steps, toolCall, loudness** - `235fbfb` (test)
3. **Task 3: ToolSpec.strict and the growth rule** - `ca986c1` (feat)

## Deviations from Plan

**1. [Process] Test-first not committed as separate RED commits.** Tests for Tasks 2 and 3 were written and run (Task 3's compile failure confirmed RED) before the implementation, but each task is one commit. Task 2's behavior was already satisfied by the Task 1 main files, so only the fixture additions (typealias, step constructor, `toolCall`) and tests landed there.

**2. [Rule 3 - Blocking, minor] A line wrapped** in FakeAiProviderTest to satisfy the 120-column detekt limit; no suppression.

**Total deviations:** 2 process/format notes. **Impact:** none on scope or public API.

## Verification

- `./gradlew :core:test --tests '*FakeAiProviderTest'`: 11 tests, 0 failures
- `./gradlew :core:test --tests '*ApiShapeTest' --tests '*ToolSpecClarificationTest' --tests '*TranscriptTypesTest'`: pass
- `./gradlew :core:check :providers:check`: pass
- `./gradlew check` (whole repo): pass
- `scripts/review-api-surface.sh`: `API SURFACE OK` (classes=160, sealed set unchanged)
- No api.txt, no new `@Suppress`, no new dependency, no model-id literal in main, no planning id in source.

## Next Plan Readiness

03-06 (router) can implement against `AiProvider`, build `ProviderCall` with overridden capabilities, and test with `FakeAiProvider`. Transport plans in `:providers` can construct `ModelResult` and `ModelResponse` directly.

## Self-Check: PASSED

- Files: all eight listed key-files exist; commits `317ead6`, `235fbfb` and `ca986c1` are in `git log`; `git rev-list --count` from `plan_head_before` is 3 before this SUMMARY commit.
- Acceptance greps: `public interface AiProvider`, `abstract class ModelResult internal constructor`, `AssertionError`, `@JvmOverloads`, `strict: Boolean? = null` all match; `getConstructor` appears 4 times in ApiShapeTest.
