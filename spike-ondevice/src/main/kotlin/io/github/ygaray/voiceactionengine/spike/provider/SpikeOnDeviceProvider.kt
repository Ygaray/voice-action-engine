package io.github.ygaray.voiceactionengine.spike.provider

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.provider.AiProvider
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilities
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantMessage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.ModelResponse
import io.github.ygaray.voiceactionengine.core.transcript.StopReason
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import io.github.ygaray.voiceactionengine.spike.backend.BackendAnswer
import io.github.ygaray.voiceactionengine.spike.backend.BackendFailure
import io.github.ygaray.voiceactionengine.spike.backend.BackendRequest
import io.github.ygaray.voiceactionengine.spike.backend.BenchFacts
import io.github.ygaray.voiceactionengine.spike.backend.LlmBackend
import io.github.ygaray.voiceactionengine.spike.backend.isStableCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/** How the neutral request is mapped onto the runtime (D-04): both are measured side by side. */
internal enum class ProviderRoute {
    /** Route A: constrained JSON through `ResponseFormat.json`. */
    CONSTRAINED_JSON,

    /** Route B: the runtime's native tool calls. */
    NATIVE_TOOLS,
}

/** Stable failure codes of the spike provider. A runtime code outside `[a-z0-9_]+` becomes [NATIVE_ERROR]. */
internal const val MALFORMED_OUTPUT = "malformed_output"
internal const val SCHEMA_TYPE_MISMATCH = "schema_type_mismatch"
internal const val NATIVE_ERROR = "native_error"

private const val CALL_ID = "call_1"

/**
 * The throwaway `ON_DEVICE` provider the spike measures (D-04). The real `SingleShotStrategy`, router and on-device gate
 * call it with the same `ModelRequest` the cloud path sends, and it maps that request onto a [LlmBackend] through the
 * chosen [route].
 *
 * It never executes a tool and never writes: it only returns the model's proposed call, and every write goes through the
 * app's gate. Every failure is a [ModelResult.Failure] with a stable code and no text; cancellation is rethrown.
 * [toString] shows the route only, never request content.
 */
internal class SpikeOnDeviceProvider(
    private val backend: LlmBackend,
    private val route: ProviderRoute,
    private val dispatcher: CoroutineDispatcher,
) : AiProvider {
    override val id: ProviderId = ProviderId.ON_DEVICE

    override val requiresCredential: Boolean = false

    override fun capabilities(model: String): ModelCapabilities = ModelCapabilities {
        supportsTools = true
        supportsForcedToolChoice = true
    }

    override suspend fun complete(call: ProviderRequest): ModelResult {
        val request = backendRequest(call) ?: return failure(NATIVE_ERROR)
        return try {
            val answer = withContext(dispatcher) { backend.generate(request) }
            parse(call, answer)
        } catch (e: CancellationException) {
            throw e
        } catch (e: BackendFailure) {
            failure(e.code)
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
            // Native faults must be a measured row, never a harness error; the message is dropped on purpose.
            failure(NATIVE_ERROR)
        }
    }

    // Dispatch on (route, tool choice): Route A forced, Route A auto, or Route B. Every shape carries the request's own
    // per-turn token limit, so both routes are measured under the same output limit.
    private fun backendRequest(call: ProviderRequest): BackendRequest? {
        val request = call.request
        val user = request.messages.filterIsInstance<UserMessage>().lastOrNull()?.text ?: return null
        val choice = request.toolChoice
        return when {
            route == ProviderRoute.NATIVE_TOOLS ->
                RouteB.nativeRequest(request.system, user, request.tools, request.maxTokens)
            choice is ToolChoice.Required -> request.tools.firstOrNull { it.name == choice.toolName }
                ?.let { RouteA.forcedRequest(request.system, user, it, request.maxTokens) }
            else -> RouteA.autoRequest(request.system, user, request.tools, request.maxTokens)
        }
    }

    private fun parse(call: ProviderRequest, answer: BackendAnswer): ModelResult {
        val choice = call.request.toolChoice
        return when {
            route == ProviderRoute.NATIVE_TOOLS -> RouteB.parse(answer, call.request.tools)
            choice is ToolChoice.Required -> RouteA.parseForced(answer, choice.toolName)
            else -> RouteA.parseAuto(answer, call.request.tools)
        }
    }

    /** The route only. */
    override fun toString(): String = "SpikeOnDeviceProvider(route=$route)"

    companion object {
        /** The dispatcher the provider runs blocking native calls on: one thread, so calls never overlap. */
        fun newDispatcher(): CoroutineDispatcher =
            Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "spike-ondevice-jni") }
                .asCoroutineDispatcher()
    }
}

/** A typed failure: the on-device provider with one stable [code], or [NATIVE_ERROR] when the code is not stable. */
internal fun failure(code: String): ModelResult.Failure {
    val stable = if (isStableCode(code)) code else NATIVE_ERROR
    return ModelResult.Failure(FailureReason.ProviderUnavailable(ProviderId.ON_DEVICE, stable))
}

/** A successful answer: [parts] with the [stop] reason and the runtime's token counts as usage. */
internal fun success(parts: List<AssistantPart>, stop: StopReason, bench: BenchFacts): ModelResult.Success {
    val usage = Usage(
        inputUncached = (bench.prefillTokens ?: 0).toLong(),
        cacheRead = 0L,
        cacheWrite = 0L,
        output = (bench.decodeTokens ?: 0).toLong(),
    )
    return ModelResult.Success(ModelResponse(AssistantMessage(parts), stop, usage))
}

internal fun toolCall(name: String, arguments: kotlinx.serialization.json.JsonObject): AssistantPart.ToolCall =
    AssistantPart.ToolCall(CALL_ID, name, arguments)
