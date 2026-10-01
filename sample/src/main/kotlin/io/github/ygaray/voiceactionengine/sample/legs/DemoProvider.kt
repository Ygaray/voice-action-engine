package io.github.ygaray.voiceactionengine.sample.legs

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.commit.CommitSink
import io.github.ygaray.voiceactionengine.core.commit.ActionEvent
import io.github.ygaray.voiceactionengine.core.commit.RunTermination
import io.github.ygaray.voiceactionengine.core.provider.AiProvider
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilities
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.strategy.ClarificationOption
import io.github.ygaray.voiceactionengine.core.strategy.UserTurnContext
import io.github.ygaray.voiceactionengine.core.strategy.UserTurnRenderer
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantMessage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.ModelResponse
import io.github.ygaray.voiceactionengine.core.transcript.StopReason
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.core.transcript.ToolResultsMessage
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** The provider id of the offline demos. It is not a real provider: no key, no network, no budget. */
internal val DEMO_PROVIDER = ProviderId("demo")

/** The model name the demos report. */
internal const val DEMO_MODEL = "demo-model"

/** The line the follow-up renderer writes before the chosen option; the demo provider looks for it. */
internal const val FOLLOW_UP_MARKER = "The user chose:"

private const val TOOL_ASK = "ask_user"
private const val TOOL_CREATE = "create_item"
private const val QUESTION_TEXT = "Which list should I add it to?"
private const val DONE_TEXT = "Added it to the chosen list."
private const val ID_A = "list-a"
private const val LABEL_A = "List A"
private const val ID_B = "list-b"
private const val LABEL_B = "List B"

/** What a follow-up command carries in `CommandInput.context`: the question that was asked and the option chosen. */
internal class FollowUpContext(val question: String, val option: ClarificationOption) {
    /** Lengths only, so neither the question nor the choice can reach a log. */
    override fun toString(): String =
        "FollowUpContext(questionLength=${question.length}, optionLabelLength=${option.label.length})"
}

/**
 * The sample's user-turn renderer. For a plain command it is the engine's standard rendering. For a follow-up it adds,
 * to the same user message, the question that was asked and the option the user chose, so the choice reaches the model
 * through the user-turn hook and the cached prefix is untouched (D-14). No transcript is resumed.
 */
internal object FollowUpTurnRenderer : UserTurnRenderer {
    private val standard = UserTurnRenderer.standard()

    override suspend fun render(context: UserTurnContext): String {
        val base = standard.render(context)
        val follow = context.input.context as? FollowUpContext ?: return base
        return base + "\nYou asked: ${follow.question}\n$FOLLOW_UP_MARKER ${follow.option.label} (${follow.option.id})"
    }

    override fun toString(): String = "FollowUpTurnRenderer"
}

/** A commit sink that ignores everything, for runs nobody needs to inspect. */
internal object NoOpCommitSink : CommitSink {
    override suspend fun onAction(event: ActionEvent) = Unit

    override suspend fun onRunClosed(runId: String, termination: RunTermination) = Unit

    override fun toString(): String = "NoOpCommitSink"
}

/**
 * An offline scripted provider for the clarification and partial demos. It decides from the request alone, never from a
 * network, and needs no credential.
 */
internal class DemoProvider : AiProvider {
    private val seen = CopyOnWriteArrayList<ProviderRequest>()
    private val counter = AtomicInteger()

    override val id: ProviderId get() = DEMO_PROVIDER

    override val requiresCredential: Boolean get() = false

    /** The requests received, in order; the tests read what the follow-up sent. */
    val calls: List<ProviderRequest> get() = seen.toList()

    override fun capabilities(model: String): ModelCapabilities = ModelCapabilities {
        supportsTools = true
        supportsForcedToolChoice = true
    }

    override suspend fun complete(call: ProviderRequest): ModelResult {
        seen.add(call)
        val request = call.request
        val choice = request.toolChoice
        val first = (request.messages.firstOrNull() as? UserMessage)?.text.orEmpty()
        val parts: List<AssistantPart> = when {
            choice is ToolChoice.Required && choice.toolName == TOOL_CREATE ->
                listOf(createCall("paper"), createCall("pens"))
            request.messages.lastOrNull() is ToolResultsMessage -> listOf(AssistantPart.Text(DONE_TEXT))
            FOLLOW_UP_MARKER in first -> listOf(createCall("paper"))
            else -> listOf(askCall())
        }
        val stop = if (parts.any { it is AssistantPart.ToolCall }) StopReason.TOOL_USE else StopReason.END_TURN
        return ModelResult.Success(ModelResponse(AssistantMessage(parts), stop, Usage.ZERO))
    }

    private fun createCall(title: String): AssistantPart.ToolCall =
        AssistantPart.ToolCall(nextId(), TOOL_CREATE, buildJsonObject { put("title", title) })

    private fun askCall(): AssistantPart.ToolCall = AssistantPart.ToolCall(nextId(), TOOL_ASK, clarificationArguments())

    private fun clarificationArguments(): JsonObject = buildJsonObject {
        put("question", QUESTION_TEXT)
        putJsonArray("options") {
            add(buildJsonObject { put("id", ID_A); put("label", LABEL_A) })
            add(buildJsonObject { put("id", ID_B); put("label", LABEL_B) })
        }
    }

    private fun nextId(): String = "demo_call_${counter.incrementAndGet()}"

    override fun toString(): String = "DemoProvider"
}
