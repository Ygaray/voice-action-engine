package io.github.ygaray.voiceactionengine.spike.backend

import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import kotlinx.serialization.json.JsonObject

/** The only shape a failure code may take: lower snake case, never a message (mirrors the engine's stable code rule). */
private val STABLE_CODE = Regex("[a-z0-9_]+")

/** True when [value] is a stable `[a-z0-9_]+` code, so free text can never ride a failure into a sink. */
internal fun isStableCode(value: String): Boolean = STABLE_CODE.matches(value)

/**
 * The seam between the spike's provider and the native runtime. `Engine` and `Conversation` are final classes over JNI
 * and cannot be faked, so everything above this interface (the routes, the provider, the ladder) is JVM-tested against a
 * fake, and the single real adapter is `LiteRtBackend`.
 *
 * Rules for every implementation: blocking native work runs on the caller's dispatcher (the backend never creates
 * threads), a failure is a [BackendFailure] carrying a stable code only, and nothing logs a prompt, an output or an
 * argument.
 */
internal interface LlmBackend : AutoCloseable {
    /** Loads the model. A failure is reported in the outcome, never thrown. */
    suspend fun initialize(config: BackendConfig): InitOutcome

    /** Runs one generation and returns the answer, or throws a [BackendFailure]. */
    suspend fun generate(request: BackendRequest): BackendAnswer
}

/**
 * What the engine is loaded with.
 *
 * @property modelPath absolute path of the model file on the device.
 * @property gpu true for the GPU backend, false for the CPU backend.
 * @property maxNumTokens the explicit context length; never the runtime's default (that silently truncates).
 * @property cacheDir directory for the runtime's compiled-kernel cache, or null.
 * @property prefillPrefaceOnInit whether the conversation preface is prefilled at creation, for the KV-reuse probe.
 */
internal class BackendConfig(
    val modelPath: String,
    val gpu: Boolean,
    val maxNumTokens: Int,
    val cacheDir: String?,
    val prefillPrefaceOnInit: Boolean = false,
) {
    /** The path and flags only. */
    override fun toString(): String = "BackendConfig(gpu=$gpu, maxNumTokens=$maxNumTokens)"
}

/**
 * How the engine came up.
 *
 * @property failureCode a stable code when the load failed, null when it succeeded.
 * @property initMs wall time of the load in milliseconds, from a monotonic clock.
 */
internal class InitOutcome(val failureCode: String?, val initMs: Long) {
    init {
        require(failureCode == null || isStableCode(failureCode)) { "an init failure must be a stable code" }
    }

    val ok: Boolean get() = failureCode == null

    override fun toString(): String = "InitOutcome(failureCode=$failureCode, initMs=$initMs)"
}

/** How a generation is steered. */
internal sealed class BackendMode {
    /** Route A: the answer is one JSON document that must satisfy [schema]. */
    class Constrained(val schema: JsonObject) : BackendMode() {
        override fun toString(): String = "Constrained"
    }

    /** Route B: the runtime is offered [tools] natively and returns structured tool calls. */
    class NativeTools(val tools: List<ToolSpec>) : BackendMode() {
        override fun toString(): String = "NativeTools(tools=${tools.map { it.name }})"
    }
}

/**
 * One generation request.
 *
 * @property system the system instruction.
 * @property user the user message text.
 * @property mode Route A or Route B.
 * @property constraintOn whether the runtime enforces the schema in [BackendMode.Constrained] mode; false is the
 * rf_matrix OFF arm, which measures the raw validity rate.
 * @property maxOutputTokens the per-turn output limit, the same for both routes.
 * @property engineFlag the engine's global constrained-decoding flag for this call: null follows [constraintOn] (the
 * default, Pitfall 1), a value overrides it, which is how the rf_matrix measures whether the flag is needed at all.
 */
internal class BackendRequest(
    val system: String,
    val user: String,
    val mode: BackendMode,
    val constraintOn: Boolean,
    val maxOutputTokens: Int,
    val engineFlag: Boolean? = null,
) {
    /** Lengths and flags only, never the prompt text. */
    override fun toString(): String =
        "BackendRequest(systemLength=${system.length}, userLength=${user.length}, mode=$mode, " +
            "constraintOn=$constraintOn, maxOutputTokens=$maxOutputTokens, engineFlag=$engineFlag)"
}

/** One tool call as the runtime returned it, before it is checked against the offered schema. */
internal class RawToolCall(val name: String, val arguments: Map<String, Any?>) {
    /** The tool name and the argument count only. */
    override fun toString(): String = "RawToolCall(name=$name, argumentCount=${arguments.size})"
}

/** The runtime's own measurements of one call; a field is null when the backend cannot say. */
internal class BenchFacts(
    val prefillTokens: Int?,
    val decodeTokens: Int?,
    val ttftMs: Double?,
    val prefillTokensPerSecond: Double?,
    val decodeTokensPerSecond: Double?,
) {
    override fun toString(): String =
        "BenchFacts(prefillTokens=$prefillTokens, decodeTokens=$decodeTokens, ttftMs=$ttftMs, " +
            "prefillTokensPerSecond=$prefillTokensPerSecond, decodeTokensPerSecond=$decodeTokensPerSecond)"

    companion object {
        /** A call whose backend reported nothing. */
        val NONE: BenchFacts = BenchFacts(null, null, null, null, null)
    }
}

/** What one generation produced: the text, any native tool calls and the measurements. */
internal class BackendAnswer(
    val text: String,
    val rawToolCalls: List<RawToolCall>,
    val bench: BenchFacts,
) {
    /** Lengths only, never the model's text. */
    override fun toString(): String =
        "BackendAnswer(textLength=${text.length}, toolCalls=${rawToolCalls.map { it.name }}, bench=$bench)"
}

/**
 * A generation or load that failed. It carries one stable [code] and no message text, so nothing a model or a user said
 * can leave through an exception.
 */
internal class BackendFailure(val code: String) : RuntimeException(code) {
    init {
        require(isStableCode(code)) { "a backend failure must be a stable code" }
    }

    override fun toString(): String = "BackendFailure(code=$code)"
}
