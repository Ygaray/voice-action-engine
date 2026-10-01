package io.github.ygaray.voiceactionengine.core.testing

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.provider.AiProvider
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilities
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderCall
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantMessage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.ModelResponse
import io.github.ygaray.voiceactionengine.core.transcript.StopReason
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.CopyOnWriteArrayList

/** One scripted provider call: it sees the [ProviderCall] and must return a result. */
public typealias ProviderStep = suspend (ProviderCall) -> ModelResult

/**
 * A provider that plays a script. Each [complete] records the [ProviderCall] it was given first, then plays the next
 * scripted result. It uses only the public engine API, so a test sees the same calls a real transport would.
 *
 * When the script runs dry, [complete] throws an [AssertionError] on purpose: it is an `Error`, which the engine's
 * never-throw collapse does not swallow, so a test that takes an unplanned provider call fails instead of passing
 * quietly on a collapsed failure.
 *
 * @param id the provider's id.
 * @param steps one step per expected call, in order.
 * @param capabilities what [capabilities] returns for every model id; unknown capabilities unless a test opts in.
 * @param requiresCredential what [requiresCredential] reports.
 */
public class FakeAiProvider(
    override val id: ProviderId,
    steps: List<ProviderStep>,
    private val capabilities: ModelCapabilities = ModelCapabilities.UNKNOWN,
    override val requiresCredential: Boolean = true,
) : AiProvider {
    /** A provider answering with [results] in order. */
    public constructor(id: ProviderId, vararg results: ModelResult) : this(id, results.map { constant(it) })

    /** A provider declaring [capabilities] for every model id and playing [steps] in order. */
    public constructor(id: ProviderId, capabilities: ModelCapabilities, vararg steps: ProviderStep) :
        this(id, steps.toList(), capabilities)

    private val script = ScriptedResponses(steps)
    private val scriptSize = steps.size
    private val lock = Any()
    private val recorded = CopyOnWriteArrayList<ProviderCall>()

    /** Every call received so far, oldest first; a snapshot. */
    public val calls: List<ProviderCall>
        get() = recorded.toList()

    /** How many times [complete] has been called. */
    public val callCount: Int
        get() = recorded.size

    override fun capabilities(model: String): ModelCapabilities = capabilities

    override suspend fun complete(call: ProviderCall): ModelResult {
        recorded.add(call)
        return nextStep()(call)
    }

    private fun nextStep(): ProviderStep = synchronized(lock) {
        if (script.remaining == 0) {
            throw AssertionError("FakeAiProvider ${id.value}: script exhausted after $scriptSize calls")
        }
        script.next()
    }

    /** Builders for the results scripts are made of. */
    public companion object {
        private fun constant(result: ModelResult): ProviderStep = { result }

        /** A successful answer with one text part that ends the turn. */
        public fun reply(text: String, usage: Usage): ModelResult =
            ModelResult.Success(
                ModelResponse(AssistantMessage(listOf(AssistantPart.Text(text))), StopReason.END_TURN, usage),
            )

        /** A successful answer with one tool call part that stops the turn for tool use. */
        public fun toolCall(callId: String, name: String, arguments: JsonObject, usage: Usage): ModelResult =
            ModelResult.Success(
                ModelResponse(
                    AssistantMessage(listOf(AssistantPart.ToolCall(callId, name, arguments))),
                    StopReason.TOOL_USE,
                    usage,
                ),
            )
    }
}
