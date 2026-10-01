package io.github.ygaray.voiceactionengine.sample.verdict

import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.telemetry.TurnRecord
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val HTTP_OK = 200
private const val HTTP_SUCCESS_FIRST = 200
private const val HTTP_SUCCESS_LAST = 299
private const val KIND_INITIAL = "initial"
private const val KIND_RETRY = "transient_retry"
private const val KIND_RESHAPE = "forced_tool_reshape"
private const val MIN_CALLS = 2
private const val KEY_PROPERTIES = "properties"
private const val KEY_REQUIRED = "required"

/** `optional_absent` values for the smoke evidence line. */
internal object OptionalAbsent {
    /** No optional key the prompt did not ask for appeared. */
    const val TRUE = "true"

    /** The call broke the tool's schema, so the question was not settled in its favour. */
    const val FALSE = "false"

    /** The run could not settle the question (a model-filled optional, or the call was not evaluable). */
    const val INCONCLUSIVE = "inconclusive"
}

/** A smoke classification plus the `optional_absent` word for the smoke line. */
internal class SmokeResult(val verdict: Verdict, val optionalAbsent: String) {
    override fun toString(): String = "SmokeResult(verdict=$verdict, optionalAbsent=$optionalAbsent)"
}

/**
 * The VER-03 single-shot rule: the provider returned the forced tool with exactly the keys its schema allows, and (on
 * Anthropic) did so on one initial request that answered 200, so a reshape or a retry cannot be counted as proof of
 * `disable_parallel_tool_use` (07-08 carry).
 */
internal object SmokeVerdict {
    /**
     * Classifies one single-shot leg, in this order:
     * 1. FAIL on a final attempt that was not 2xx, or on an outcome that is not a completed one.
     * 2. With [anthropicStrict]: FAIL `reshape_seen`, `retry_seen` or `not_initial_200` unless there was exactly one
     *    attempt, of kind `initial`, with HTTP 200.
     * 3. FAIL `no_tool_call`, `wrong_tool`, `required_missing` or `unknown_key`.
     * 4. INCONCLUSIVE `model_filled_optional` when an optional key the prompt did not ask for ([requestedOptionals])
     *    appeared. That is not a failure: the model chose to fill a field.
     * 5. PASS.
     *
     * The required and optional key sets are read from [schema], never written down here.
     *
     * @param argKeys the argument key names the model sent (never their values).
     */
    fun classify(
        expectedTool: String,
        schema: ToolSpec,
        toolName: String?,
        argKeys: Set<String>,
        requestedOptionals: Set<String>,
        attempts: List<AttemptRecord>,
        outcome: OutcomeSummary,
        anthropicStrict: Boolean,
    ): SmokeResult {
        val final = attempts.lastOrNull()
        val transport = transportFailure(attempts, final, outcome, anthropicStrict)
        if (transport != null) return SmokeResult(Verdict.fail(transport), OptionalAbsent.INCONCLUSIVE)

        val properties = (schema.inputSchema[KEY_PROPERTIES] as? JsonObject)?.keys.orEmpty()
        val required = requiredKeys(schema.inputSchema)
        val optional = properties - required
        val unrequested = argKeys.intersect(optional) - requestedOptionals
        return when {
            toolName == null -> SmokeResult(Verdict.fail("no_tool_call"), OptionalAbsent.INCONCLUSIVE)
            toolName != expectedTool -> SmokeResult(Verdict.fail("wrong_tool"), OptionalAbsent.INCONCLUSIVE)
            !argKeys.containsAll(required) -> SmokeResult(Verdict.fail("required_missing"), OptionalAbsent.FALSE)
            !properties.containsAll(argKeys) -> SmokeResult(Verdict.fail("unknown_key"), OptionalAbsent.FALSE)
            unrequested.isNotEmpty() ->
                SmokeResult(Verdict(VerdictKind.INCONCLUSIVE, "model_filled_optional"), OptionalAbsent.INCONCLUSIVE)
            else -> SmokeResult(Verdict.pass(), OptionalAbsent.TRUE)
        }
    }

    private fun transportFailure(
        attempts: List<AttemptRecord>,
        final: AttemptRecord?,
        outcome: OutcomeSummary,
        anthropicStrict: Boolean,
    ): String? {
        val finalStatus = final?.httpStatus
        return when {
            final != null && (finalStatus == null || finalStatus !in HTTP_SUCCESS_FIRST..HTTP_SUCCESS_LAST) ->
                "http_${finalStatus ?: "none"}"
            !outcome.isCompleted -> outcome.failureCode()
            !anthropicStrict -> null
            attempts.any { it.kind == KIND_RESHAPE } -> "reshape_seen"
            attempts.any { it.kind == KIND_RETRY } -> "retry_seen"
            attempts.size != 1 || final?.kind != KIND_INITIAL || finalStatus != HTTP_OK -> "not_initial_200"
            else -> null
        }
    }

    private fun requiredKeys(inputSchema: JsonObject): Set<String> =
        (inputSchema[KEY_REQUIRED] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { primitive -> primitive.isString }?.content }
            ?.toSet()
            .orEmpty()
}

/** A multi-turn classification plus the turn-2 cache read, which is recorded and never asserted. */
internal class MultiTurnResult(val verdict: Verdict, val turn2CacheRead: Long?) {
    /** The measured numbers for the verdict line; absent numbers are left out. */
    fun extras(calls: Int): Map<String, Long> {
        val out = LinkedHashMap<String, Long>()
        out["calls"] = calls.toLong()
        if (turn2CacheRead != null) out["turn2_cache_read"] = turn2CacheRead
        return out
    }

    override fun toString(): String = "MultiTurnResult(verdict=$verdict, turn2CacheRead=$turn2CacheRead)"
}

/** The VER-03 multi-turn rule: the model reads, then answers in a final turn that calls no tool. */
internal object MultiTurnVerdict {
    /**
     * Classifies one multi-turn leg, in this order: FAIL on any attempt that was not HTTP 200 or an outcome that is not
     * completed; FAIL `partial`; FAIL `single_turn` (fewer than two provider calls); FAIL `read_tool_not_called` (turn
     * 1 did not call [readTool]); FAIL `no_final_answer` (the last turn still called tools); PASS. The turn-2 cache
     * read is always returned for the record.
     */
    fun classify(
        readTool: String,
        turns: List<TurnRecord>,
        attempts: List<AttemptRecord>,
        outcome: OutcomeSummary,
    ): MultiTurnResult {
        val turn2CacheRead = turns.getOrNull(1)?.usage?.cacheRead
        val badAttempt = attempts.firstOrNull { it.httpStatus != HTTP_OK }
        val code = when {
            badAttempt != null -> "http_${badAttempt.httpStatus ?: "none"}"
            !outcome.isCompleted -> outcome.failureCode()
            outcome.partial -> "partial"
            turns.size < MIN_CALLS -> "single_turn"
            readTool !in turns.first().toolNames -> "read_tool_not_called"
            turns.last().toolNames.isNotEmpty() -> "no_final_answer"
            else -> null
        }
        return MultiTurnResult(if (code == null) Verdict.pass() else Verdict.fail(code), turn2CacheRead)
    }
}
