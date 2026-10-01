package io.github.ygaray.voiceactionengine.sample

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilities
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.telemetry.TurnRecord
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import io.github.ygaray.voiceactionengine.sample.evidence.BudgetState
import io.github.ygaray.voiceactionengine.sample.evidence.BudgetStore
import io.github.ygaray.voiceactionengine.sample.evidence.BudgetedProvider
import io.github.ygaray.voiceactionengine.sample.evidence.CostEstimate
import io.github.ygaray.voiceactionengine.sample.evidence.FileBudgetStore
import io.github.ygaray.voiceactionengine.sample.evidence.RequestBudget
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

private const val COST_DELTA = 1e-9

/** The spend guard, the warm window and the cost estimate. */
class RequestBudgetTest {

    @get:Rule
    val folder = TemporaryFolder()

    private class MemoryStore(var state: BudgetState = BudgetState.EMPTY) : BudgetStore {
        override fun read(): BudgetState = state
        override fun write(state: BudgetState) {
            this.state = state
        }
    }

    private fun state(core: Int = 0, optional: Int = 0, start: Long? = null): BudgetState =
        BudgetState(core, optional, emptyMap(), start)

    @Test
    fun aLegStartsOnlyWithItsReservation() {
        val budget = RequestBudget(MemoryStore(state(core = 28)))
        assertFalse(budget.canStart(6, optional = false))
        assertTrue(budget.canStart(3, optional = false))
    }

    @Test
    fun aCallIsRefusedBeforeItIsSentWhenHeadroomIsBelowThree() = runTest {
        val refused = FakeAiProvider(ProviderId.ANTHROPIC)
        val tight = BudgetedProvider(refused, RequestBudget(MemoryStore(state(core = 31))), { false })
        val result = tight.complete(request())
        assertTrue(result.toString(), result is ModelResult.Failure)
        assertEquals("sample_budget_exhausted", (result as ModelResult.Failure).reason.code)
        assertEquals(0, refused.callCount)

        val open = FakeAiProvider(ProviderId.ANTHROPIC, FakeAiProvider.reply("ok", Usage.ZERO))
        val roomy = BudgetedProvider(open, RequestBudget(MemoryStore(state(core = 30))), { false })
        assertTrue(roomy.complete(request()) is ModelResult.Success)
        assertEquals(1, open.callCount)
    }

    @Test
    fun theWrapperKeepsTheDelegatesIdentity() {
        val fake = FakeAiProvider(ProviderId.OPENAI, requiresCredential = false, steps = emptyList())
        val wrapped = BudgetedProvider(fake, RequestBudget(MemoryStore()), { false })
        assertEquals(ProviderId.OPENAI, wrapped.id)
        assertFalse(wrapped.requiresCredential)
        assertEquals(fake.capabilities("m"), wrapped.capabilities("m"))
    }

    @Test
    fun theOptionalProbeRunsOnceAndNeverPushesTheTotalPast34() {
        assertTrue(RequestBudget(MemoryStore(state(core = 31))).headroomFor(optional = true))
        assertFalse(RequestBudget(MemoryStore(state(core = 31, optional = 1))).headroomFor(optional = true))
        assertFalse(RequestBudget(MemoryStore(state(core = 32))).headroomFor(optional = true))
        assertTrue(RequestBudget(MemoryStore(state(core = 31))).canStart(3, optional = true))
        assertFalse(RequestBudget(MemoryStore(state(core = 31))).canStart(4, optional = true))
    }

    @Test
    fun recordingCountsCoreOptionalAndPerProvider() {
        val budget = RequestBudget(MemoryStore())
        budget.record(ProviderId.ANTHROPIC, optional = false)
        budget.record(ProviderId.ANTHROPIC, optional = false)
        budget.record(ProviderId.OPENAI, optional = true)
        val snapshot = budget.snapshot()
        assertEquals(2, snapshot.core)
        assertEquals(1, snapshot.optional)
        assertEquals(3, snapshot.total)
        assertEquals(mapOf("anthropic" to 2, "openai" to 1), snapshot.perProvider)
    }

    @Test
    fun countsSurviveARestart() {
        val file = folder.newFile("gate1-budget.txt")
        file.delete()
        val first = RequestBudget(FileBudgetStore(file))
        first.record(ProviderId.ANTHROPIC, optional = false)
        first.record(ProviderId.OPENROUTER, optional = false)
        first.record(ProviderId.OPENAI, optional = true)
        first.markAgenticStart(1_000L)

        val second = RequestBudget(FileBudgetStore(file))
        val snapshot = second.snapshot()
        assertEquals(2, snapshot.core)
        assertEquals(1, snapshot.optional)
        assertEquals(mapOf("anthropic" to 1, "openrouter" to 1, "openai" to 1), snapshot.perProvider)
        assertEquals(60L, second.warmWindowRemaining(1_300L))
        assertFalse(File(file.parentFile, file.name + ".tmp").exists())
    }

    @Test
    fun anUnwrittenFileMeansNothingSpent() {
        val file = File(folder.root, "never-written.txt")
        val budget = RequestBudget(FileBudgetStore(file))
        assertEquals(0, budget.snapshot().total)
        assertEquals(0L, budget.warmWindowRemaining(5L))
    }

    @Test
    fun theWarmWindowIsPersisted() {
        val budget = RequestBudget(MemoryStore())
        assertEquals(0L, budget.warmWindowRemaining(1_000L))
        budget.markAgenticStart(1_000L)
        assertEquals(260L, budget.warmWindowRemaining(1_100L))
        assertEquals(0L, budget.warmWindowRemaining(1_360L))
        assertEquals(0L, budget.warmWindowRemaining(9_999L))
    }

    @Test
    fun costIsEstimatedFromUsage() {
        val haiku = CostEstimate.usd(ProviderId.ANTHROPIC, "claude-haiku-4-5", Usage(40, 0, 7016, 20))
        assertNotNull(haiku)
        assertEquals(0.00891, haiku!!, COST_DELTA)
        val gpt = CostEstimate.usd(ProviderId.OPENAI, "gpt-5.4-mini", Usage(1000, 0, 0, 100))
        assertEquals(0.00120, gpt!!, COST_DELTA)
        val routed = CostEstimate.usd(ProviderId.OPENROUTER, "openai/gpt-5.4-mini", Usage(1000, 0, 0, 100))
        assertEquals(0.00120, routed!!, COST_DELTA)
        assertNull(CostEstimate.usd(ProviderId.OPENAI, "some-unpriced-model", Usage(1, 1, 1, 1)))
    }

    @Test
    fun cacheBucketsAreBilledAtTheirOwnRates() {
        // 1,000,000 cache-read tokens on Haiku cost USD 0.10; on gpt-5.4-mini USD 0.075 (rounded to 5 places: 0.07500).
        assertEquals(0.10, CostEstimate.usd(ProviderId.ANTHROPIC, "claude-haiku-4-5", Usage(0, 1_000_000, 0, 0))!!, COST_DELTA)
        assertEquals(0.075, CostEstimate.usd(ProviderId.OPENAI, "gpt-5.4-mini", Usage(0, 1_000_000, 0, 0))!!, COST_DELTA)
    }

    @Test
    fun aRunCostSumsItsTurnsAndIsUnknownIfAnyTurnIs() {
        val usage = Usage(1000, 0, 0, 100)
        val priced = TurnRecord(ProviderId.OPENAI, "gpt-5.4-mini", "stop", emptyList(), usage, 0)
        val unpriced = TurnRecord(ProviderId.OPENAI, "mystery-model", "stop", emptyList(), usage, 0)
        assertEquals(0.0024, CostEstimate.usd(listOf(priced, priced))!!, COST_DELTA)
        assertNull(CostEstimate.usd(listOf(priced, unpriced)))
        assertEquals("0.00240", CostEstimate.format(0.0024))
        assertEquals("unknown", CostEstimate.format(null))
    }

    private fun request(): ProviderRequest =
        ProviderRequest("m", ModelRequest("system", listOf(UserMessage("placeholder")), 16), null, ModelCapabilities.UNKNOWN)
}
