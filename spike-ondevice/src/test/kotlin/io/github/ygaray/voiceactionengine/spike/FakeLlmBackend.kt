package io.github.ygaray.voiceactionengine.spike

import io.github.ygaray.voiceactionengine.spike.backend.BackendAnswer
import io.github.ygaray.voiceactionengine.spike.backend.BackendConfig
import io.github.ygaray.voiceactionengine.spike.backend.BackendFailure
import io.github.ygaray.voiceactionengine.spike.backend.BackendRequest
import io.github.ygaray.voiceactionengine.spike.backend.BenchFacts
import io.github.ygaray.voiceactionengine.spike.backend.InitOutcome
import io.github.ygaray.voiceactionengine.spike.backend.LlmBackend
import io.github.ygaray.voiceactionengine.spike.backend.RawToolCall
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A scripted [LlmBackend]: each call plays the next scripted answer or failure, and every request is recorded so a test
 * can assert on the count, the mode and the constraint flag. It never logs. A script that runs dry fails the test on
 * purpose.
 */
internal class FakeLlmBackend(vararg script: Step) : LlmBackend {
    /** One scripted result. */
    sealed class Step {
        class Answer(val answer: BackendAnswer) : Step()
        class Failure(val code: String) : Step()
        class Boom(val error: Throwable) : Step()
    }

    private val steps = script.toList()
    private val seen = CopyOnWriteArrayList<BackendRequest>()

    /** Every request received, in order. */
    val requests: List<BackendRequest> get() = seen.toList()

    /** How many generations were asked for. */
    val calls: Int get() = seen.size

    var closed: Boolean = false
        private set

    override suspend fun initialize(config: BackendConfig): InitOutcome = InitOutcome(null, 0L)

    override suspend fun generate(request: BackendRequest): BackendAnswer {
        val index = seen.size
        seen.add(request)
        if (index >= steps.size) throw AssertionError("script exhausted: all ${steps.size} scripted steps were used")
        return when (val step = steps[index]) {
            is Step.Answer -> step.answer
            is Step.Failure -> throw BackendFailure(step.code)
            is Step.Boom -> throw step.error
        }
    }

    override fun close() {
        closed = true
    }

    companion object {
        val BENCH = BenchFacts(prefillTokens = 120, decodeTokens = 9, ttftMs = 80.0, prefillTokensPerSecond = 900.0, decodeTokensPerSecond = 20.0)

        fun text(text: String): Step = Step.Answer(BackendAnswer(text, emptyList(), BENCH))

        fun tools(text: String, vararg calls: RawToolCall): Step =
            Step.Answer(BackendAnswer(text, calls.toList(), BENCH))

        fun fail(code: String): Step = Step.Failure(code)
    }
}
