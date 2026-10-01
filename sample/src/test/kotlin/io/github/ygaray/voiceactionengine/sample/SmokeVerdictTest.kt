package io.github.ygaray.voiceactionengine.sample

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.telemetry.TurnRecord
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.sample.tools.SyntheticTools
import io.github.ygaray.voiceactionengine.sample.verdict.AttemptRecord
import io.github.ygaray.voiceactionengine.sample.verdict.MultiTurnVerdict
import io.github.ygaray.voiceactionengine.sample.verdict.OptionalAbsent
import io.github.ygaray.voiceactionengine.sample.verdict.OutcomeSummary
import io.github.ygaray.voiceactionengine.sample.verdict.SmokeResult
import io.github.ygaray.voiceactionengine.sample.verdict.SmokeVerdict
import io.github.ygaray.voiceactionengine.sample.verdict.VerdictKind
import org.junit.Assert.assertEquals
import org.junit.Test

/** The VER-03 smoke and multi-turn rules. */
class SmokeVerdictTest {

    private val completed = OutcomeSummary(OutcomeSummary.COMPLETED, false, null, 1, 1, 0, 0, null)

    private fun attempt(provider: ProviderId, kind: String, status: Int?, number: Int = 1): AttemptRecord =
        AttemptRecord(provider, number, kind, status, null, 0)

    private val anthropicInitial = listOf(attempt(ProviderId.ANTHROPIC, "initial", 200))

    private fun anthropic(
        tool: String? = "edit_item",
        keys: Set<String> = setOf("id", "body"),
        requested: Set<String> = setOf("body"),
        attempts: List<AttemptRecord> = anthropicInitial,
        outcome: OutcomeSummary = completed,
    ): SmokeResult = SmokeVerdict.classify(
        expectedTool = "edit_item",
        schema = SyntheticTools.editItem,
        toolName = tool,
        argKeys = keys,
        requestedOptionals = requested,
        attempts = attempts,
        outcome = outcome,
        anthropicStrict = true,
    )

    private fun assertFail(reason: String, result: SmokeResult) {
        assertEquals(result.toString(), VerdictKind.FAIL, result.verdict.kind)
        assertEquals(result.toString(), reason, result.verdict.reason)
    }

    @Test
    fun smokePassesOnTheForcedToolWithOnlyRequestedKeys() {
        val result = anthropic()
        assertEquals(result.toString(), VerdictKind.PASS, result.verdict.kind)
        assertEquals(OptionalAbsent.TRUE, result.optionalAbsent)
    }

    @Test
    fun aFilledOptionalIsInconclusive() {
        val result = anthropic(keys = setOf("id", "body", "title"))
        assertEquals(VerdictKind.INCONCLUSIVE, result.verdict.kind)
        assertEquals("model_filled_optional", result.verdict.reason)
        assertEquals(OptionalAbsent.INCONCLUSIVE, result.optionalAbsent)
    }

    @Test
    fun wrongToolMissingRequiredOrUnknownKeyFail() {
        assertFail("wrong_tool", anthropic(tool = "create_item"))
        assertFail("required_missing", anthropic(keys = setOf("body")))
        assertFail("unknown_key", anthropic(keys = setOf("id", "body", "color")))
        assertFail("no_tool_call", anthropic(tool = null, keys = emptySet()))
    }

    @Test
    fun theKeySetsComeFromTheSchemaNotFromTheVerdict() {
        // create_item requires title, so {title} passes and {id} is an unknown key; the verdict knows neither name.
        val ok = SmokeVerdict.classify(
            "create_item", SyntheticTools.createItem, "create_item", setOf("title"), emptySet(),
            anthropicInitial, completed, anthropicStrict = true,
        )
        assertEquals(VerdictKind.PASS, ok.verdict.kind)
        val unknown = SmokeVerdict.classify(
            "create_item", SyntheticTools.createItem, "create_item", setOf("title", "id"), emptySet(),
            anthropicInitial, completed, anthropicStrict = true,
        )
        assertFail("unknown_key", unknown)
    }

    @Test
    fun anthropicNeedsOneInitial200() {
        assertEquals(VerdictKind.PASS, anthropic().verdict.kind)
        assertFail(
            "reshape_seen",
            anthropic(
                attempts = listOf(
                    attempt(ProviderId.ANTHROPIC, "initial", 400),
                    attempt(ProviderId.ANTHROPIC, "forced_tool_reshape", 200, number = 2),
                ),
            ),
        )
        assertFail(
            "retry_seen",
            anthropic(
                attempts = listOf(
                    attempt(ProviderId.ANTHROPIC, "initial", 529),
                    attempt(ProviderId.ANTHROPIC, "transient_retry", 200, number = 2),
                ),
            ),
        )
        assertFail("not_initial_200", anthropic(attempts = emptyList()))
    }

    @Test
    fun aNon2xxFinalAttemptOrNonCompletedOutcomeFailsFirst() {
        assertFail("http_529", anthropic(attempts = listOf(attempt(ProviderId.ANTHROPIC, "initial", 529))))
        assertFail("http_none", anthropic(attempts = listOf(attempt(ProviderId.ANTHROPIC, "initial", null))))
        val failed = OutcomeSummary(OutcomeSummary.FAILED, false, "rate_limited", 0, 0, 0, 0, null)
        assertFail("outcome_rate_limited", anthropic(outcome = failed))
    }

    @Test
    fun chatLegsMayRetry() {
        val retried = listOf(
            attempt(ProviderId.OPENAI, "initial", 429),
            attempt(ProviderId.OPENAI, "transient_retry", 200, number = 2),
        )
        val result = SmokeVerdict.classify(
            "edit_item", SyntheticTools.editItem, "edit_item", setOf("id", "body"), setOf("body"),
            retried, completed, anthropicStrict = false,
        )
        assertEquals(result.toString(), VerdictKind.PASS, result.verdict.kind)
    }

    // ---- multi-turn ----

    private fun turn(tools: List<String>, cacheRead: Long = 0): TurnRecord =
        TurnRecord(ProviderId.OPENAI, "gpt-5.4-mini", "x", tools, Usage(10, cacheRead, 0, 5), 0)

    private val ok200 = listOf(attempt(ProviderId.OPENAI, "initial", 200), attempt(ProviderId.OPENAI, "initial", 200))

    @Test
    fun multiTurnPassAndFailures() {
        val good = listOf(turn(listOf("find_items")), turn(emptyList(), cacheRead = 1024))
        val pass = MultiTurnVerdict.classify("find_items", good, ok200, completed)
        assertEquals(pass.toString(), VerdictKind.PASS, pass.verdict.kind)
        assertEquals(1024L, pass.turn2CacheRead)

        fun reason(turns: List<TurnRecord>, outcome: OutcomeSummary = completed, attempts: List<AttemptRecord> = ok200) =
            MultiTurnVerdict.classify("find_items", turns, attempts, outcome).verdict.reason

        assertEquals("single_turn", reason(listOf(turn(listOf("find_items")))))
        assertEquals("read_tool_not_called", reason(listOf(turn(listOf("create_item")), turn(emptyList()))))
        assertEquals("no_final_answer", reason(listOf(turn(listOf("find_items")), turn(listOf("find_items")))))
        val partial = OutcomeSummary(OutcomeSummary.COMPLETED, true, null, 1, 1, 0, 0, null)
        assertEquals("partial", reason(good, partial))
        assertEquals("http_429", reason(good, attempts = listOf(attempt(ProviderId.OPENAI, "initial", 429))))
        val failed = OutcomeSummary(OutcomeSummary.FAILED, false, "timeout", 0, 0, 0, 0, null)
        assertEquals("outcome_timeout", reason(good, failed))
    }

    @Test
    fun theTurnTwoCacheReadIsRecordedNeverAsserted() {
        val cold = listOf(turn(listOf("find_items")), turn(emptyList(), cacheRead = 0))
        val result = MultiTurnVerdict.classify("find_items", cold, ok200, completed)
        assertEquals(VerdictKind.PASS, result.verdict.kind)
        assertEquals(0L, result.turn2CacheRead)
        assertEquals(mapOf("calls" to 2L, "turn2_cache_read" to 0L), result.extras(cold.size))
    }
}
