package io.github.ygaray.voiceactionengine.core.strategy

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.failure.FailureDetails
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import kotlinx.serialization.json.JsonObject

/**
 * The app's local resolution step: it turns what the model extracted into prepared steps or a verdict.
 *
 * The resolver validates the arguments itself, because the engine passes them through untouched. It must never write:
 * every write goes through the gate after the resolver returns, as a [ToolStep.Mutation]. It returns
 * [Resolution.NoMatch] only when nothing at all is proposable.
 */
public fun interface OutcomeResolver {
    /** Resolves [extraction] for the command described by [input]. */
    public suspend fun resolve(extraction: Extraction, input: CommandInput): Resolution
}

/**
 * What the model extracted: the tool it called and that call's arguments.
 *
 * Later versions add members (for example an intent and slots from a grammar tier) without changing this constructor.
 *
 * @property toolName the tool the model called.
 * @property arguments the call's arguments, untouched.
 * @throws IllegalArgumentException when [toolName] is blank.
 */
public class Extraction(
    public val toolName: String,
    public val arguments: JsonObject,
) {
    init {
        require(toolName.isNotBlank()) { "an extraction's tool name must not be blank" }
    }

    /** Prints the tool name and the number of arguments, never the argument values. */
    override fun toString(): String = "Extraction(toolName=$toolName, argumentCount=${arguments.size})"
}

/**
 * What an [OutcomeResolver] decided. The set is open (later versions may add verdicts), so keep an `else` branch when
 * switching on one.
 */
public abstract class Resolution internal constructor() {
    /**
     * Steps the strategy submits to the engine, in order. Nothing is applied until the gate admits the mutations.
     *
     * @property reply text to show the user, or null. It is prepared before the gate decides, so it cannot describe
     * the result. The tier drops it when applying the mutations reported an error, and the command's outcome (its
     * executed list) says what happened instead. It is kept when the gate holds the mutations, so word it so that it
     * also reads correctly for a change that is waiting for confirmation.
     * @throws IllegalArgumentException when the list of steps is empty.
     */
    public class Steps(steps: List<ToolStep>, public val reply: String?) : Resolution() {
        /** A copy of the steps. */
        public val steps: List<ToolStep> = steps.toList()

        init {
            require(this.steps.isNotEmpty()) { "Steps needs at least one step" }
        }

        /** Steps with no reply text. */
        public constructor(steps: List<ToolStep>) : this(steps, null)

        /** Prints the number of steps and the reply length only, never the reply. */
        override fun toString(): String = "Resolution.Steps(steps=${steps.size}, replyLength=${reply?.length})"
    }

    /** Nothing at all is proposable. The next tier starts fresh. */
    public class NoMatch : Resolution() {
        override fun toString(): String = "Resolution.NoMatch"
    }

    /**
     * The resolver cannot finish and hands the command to the next tier.
     *
     * @property reason why it handed up.
     * @property carry an opaque object for the next tier to start from, or null. The engine never inspects it.
     */
    public class Escalate(
        public val reason: EscalationReason,
        public val carry: Any?,
    ) : Resolution() {
        /** Prints the reason and the carry's class name only. */
        override fun toString(): String =
            "Resolution.Escalate(reason=$reason, carry=${carry?.let { it::class.simpleName }})"
    }

    /**
     * The resolver failed and the command stops here.
     *
     * @property reason why it failed.
     * @property details transport facts that go with the failure, or null.
     */
    public class Failed(
        public val reason: FailureReason,
        public val details: FailureDetails?,
    ) : Resolution() {
        /** Prints the reason only. */
        override fun toString(): String = "Resolution.Failed(reason=$reason)"
    }
}
