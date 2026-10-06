package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.CommandPipeline
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicySource
import io.github.ygaray.voiceactionengine.core.pipeline.TierSelector
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.strategy.CommandSession
import io.github.ygaray.voiceactionengine.core.strategy.CommandStrategy
import io.github.ygaray.voiceactionengine.core.strategy.StrategyCapabilities
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TierPolicyTest {

    @Test
    fun defaultPolicyHasTheDocumentedLimits() {
        val policy = TierPolicy.DEFAULT
        assertFalse(policy.offlineOnly)
        assertNull(policy.maxTier)
        assertNull(policy.allowedProviders)
        assertEquals(SIX, policy.maxIterations)
        assertEquals(SIXTY_THOUSAND, policy.tokenCeiling)
        assertEquals(FOUR_K, policy.maxTokensPerTurn)
        assertNull(policy.commandTimeoutMillis)
        assertEquals(2_000L, policy.pickerTimeoutMillis)
    }

    @Test
    fun builderOverridesOnlyWhatIsSet() {
        val policy = TierPolicy {
            maxIterations = EIGHT
            maxTier = StrategyId("cloud")
            allowedProviders = setOf(ProviderId.ANTHROPIC)
        }
        assertEquals(EIGHT, policy.maxIterations)
        assertEquals(StrategyId("cloud"), policy.maxTier)
        assertEquals(setOf(ProviderId.ANTHROPIC), policy.allowedProviders)
        assertFalse(policy.offlineOnly)
        assertEquals(SIXTY_THOUSAND, policy.tokenCeiling)
        assertEquals(FOUR_K, policy.maxTokensPerTurn)
        assertNull(policy.commandTimeoutMillis)
    }

    @Test
    fun emptyBlockEqualsDefaults() {
        val policy = TierPolicy { }
        assertEquals(TierPolicy.DEFAULT.toString(), policy.toString())
    }

    @Test
    fun fewerThanTwoIterationsIsRejected() {
        val failure = assertThrows(IllegalArgumentException::class.java) { TierPolicy { maxIterations = 1 } }
        assertTrue(failure.message.orEmpty(), failure.message.orEmpty().contains("maxIterations"))
    }

    @Test
    fun nonPositiveLimitsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { TierPolicy { tokenCeiling = 0 } }
        assertThrows(IllegalArgumentException::class.java) { TierPolicy { maxTokensPerTurn = 0 } }
        assertThrows(IllegalArgumentException::class.java) { TierPolicy { commandTimeoutMillis = 0 } }
        assertEquals(1L, TierPolicy { commandTimeoutMillis = 1 }.commandTimeoutMillis)
        listOf(0L, -1L).forEach { bad ->
            val failure = assertThrows(IllegalArgumentException::class.java) { TierPolicy { pickerTimeoutMillis = bad } }
            assertTrue(failure.message.orEmpty(), failure.message.orEmpty().contains("pickerTimeoutMillis"))
        }
    }

    @Test
    fun pickerTimeoutIsOverridable() {
        assertEquals(500L, TierPolicy { pickerTimeoutMillis = 500 }.pickerTimeoutMillis)
    }

    @Test
    fun allowedProvidersIsDefensivelyCopied() {
        val mutable = mutableSetOf(ProviderId.OPENAI)
        val policy = TierPolicy { allowedProviders = mutable }
        mutable.add(ProviderId.ANTHROPIC)
        assertEquals(setOf(ProviderId.OPENAI), policy.allowedProviders)
    }

    @Test
    fun fixedSourceReturnsTheSameInstanceEveryCall() = runTest {
        val policy = TierPolicy { maxIterations = 3 }
        val source = TierPolicySource.fixed(policy)
        assertSame(policy, source.current())
        assertSame(policy, source.current())
    }

    @Test
    fun toStringNamesEveryField() {
        val text = TierPolicy.DEFAULT.toString()
        listOf(
            "offlineOnly", "maxTier", "allowedProviders", "maxIterations", "tokenCeiling", "maxTokensPerTurn",
            "commandTimeoutMillis", "pickerTimeoutMillis",
        ).forEach { assertTrue("$it missing from $text", text.contains(it)) }
    }

    // ---- per-execute pre-check (CORE-04) ----

    private fun tier(
        id: String,
        capabilities: StrategyCapabilities,
        outcome: StrategyOutcome = StrategyOutcome.Escalate(EscalationReason.NoToolCall()),
    ) = ScriptedStrategy(StrategyId(id), capabilities, { _, _ -> outcome })

    private fun caps(provider: ProviderId) = StrategyCapabilities(setOf(provider))

    private fun ladder(
        vararg tiers: ScriptedStrategy,
        policy: TierPolicySource = TierPolicySource.fixed(TierPolicy.DEFAULT),
        selected: TierSelector = TierSelector.Linear,
        onDevice: suspend () -> Boolean = { false },
    ): CommandPipeline = commandPipeline {
        tiers.forEach { tier(it) }
        this.policy = policy
        selector = selected
        onDeviceAvailability = onDevice
        gate = ScriptedGate.admitAll()
        commitSink = RecordingCommitSink()
    }

    private val onDeviceUnavailable = FailureReason.ProviderUnavailable(ProviderId.ON_DEVICE, "on_device_unavailable")

    private fun CommandOutcome.codes(): List<String> = trace.codes.map { it.value }

    @Test
    fun policyIsReadOncePerExecuteBeforeTheFirstStrategyRuns() = runTest {
        NoNetworkGuard.during {
            val log = mutableListOf<String>()
            val source = TierPolicySource {
                log.add("policy")
                TierPolicy.DEFAULT
            }
            val only = ScriptedStrategy(
                StrategyId("only"),
                { _, _ ->
                    log.add("strategy")
                    StrategyOutcome.Completed("x")
                },
                { _, _ ->
                    log.add("strategy")
                    StrategyOutcome.Completed("x")
                },
                { _, _ ->
                    log.add("strategy")
                    StrategyOutcome.Completed("x")
                },
            )
            val pipeline = ladder(only, policy = source)
            repeat(THREE) { pipeline.execute(CommandInput("hi")) }
            assertEquals(List(THREE) { listOf("policy", "strategy") }.flatten(), log)
        }
    }

    @Test
    fun maxTierCutsTheLadderAfterThatTier() = runTest {
        NoNetworkGuard.during {
            val a = tier("a", StrategyCapabilities.ANY_PROVIDER)
            val b = tier("b", StrategyCapabilities.ANY_PROVIDER)
            val c = tier("c", StrategyCapabilities.ANY_PROVIDER)
            val outcome = ladder(
                a, b, c,
                policy = TierPolicySource.fixed(TierPolicy { maxTier = StrategyId("a") }),
            ).execute(CommandInput("hi"))
            assertTrue(outcome is CommandOutcome.Unhandled)
            assertEquals(listOf(1, 0, 0), listOf(a, b, c).map { it.executions })
            assertEquals(listOf("tier_skipped_policy", "tier_skipped_policy"), outcome.codes())
        }
    }

    @Test
    fun maxTierNotOnTheLadderIsALoudNoEligibleTier() = runTest {
        NoNetworkGuard.during {
            val a = tier("a", StrategyCapabilities.ANY_PROVIDER)
            val outcome = ladder(a, policy = TierPolicySource.fixed(TierPolicy { maxTier = StrategyId("nope") }))
                .execute(CommandInput("hi"))
            assertEquals(FailureReason.NoEligibleTier(), (outcome as CommandOutcome.Failed).reason)
            assertEquals(0, a.executions)
            assertEquals(listOf("max_tier_unknown"), outcome.codes())
        }
    }

    @Test
    fun allowedProvidersSkipsTiersThatNeedAnotherProvider() = runTest {
        NoNetworkGuard.during {
            val openai = tier("openai", caps(ProviderId.OPENAI))
            val anthropic = tier("anthropic", caps(ProviderId.ANTHROPIC), StrategyOutcome.Completed("ok"))
            val outcome = ladder(
                openai, anthropic,
                policy = TierPolicySource.fixed(TierPolicy { allowedProviders = setOf(ProviderId.ANTHROPIC) }),
            ).execute(CommandInput("hi"))
            assertEquals("ok", (outcome as CommandOutcome.Completed).reply)
            assertEquals(0, openai.executions)
            assertEquals(1, anthropic.executions)
            assertEquals(listOf("tier_skipped_policy"), outcome.codes())
        }
    }

    @Test
    fun emptyAllowedProvidersLetsOnlyAProviderlessTierRun() = runTest {
        NoNetworkGuard.during {
            val cloud = tier("cloud", caps(ProviderId.ANTHROPIC))
            val grammar = tier("grammar", StrategyCapabilities.NO_PROVIDER, StrategyOutcome.Completed("local"))
            val outcome = ladder(
                cloud, grammar,
                policy = TierPolicySource.fixed(TierPolicy { allowedProviders = emptySet() }),
            ).execute(CommandInput("hi"))
            assertEquals("local", (outcome as CommandOutcome.Completed).reply)
            assertEquals(0, cloud.executions)
        }
    }

    @Test
    fun offlineOnlyWithOnlyCloudTiersFailsLoudlyWithZeroExecutions() = runTest {
        NoNetworkGuard.during {
            val a = tier("a", caps(ProviderId.ANTHROPIC))
            val b = tier("b", StrategyCapabilities.ANY_PROVIDER)
            val outcome = ladder(
                a, b,
                policy = TierPolicySource.fixed(TierPolicy { offlineOnly = true }),
                onDevice = { true },
            ).execute(CommandInput("hi"))
            val failed = outcome as CommandOutcome.Failed
            assertEquals(FailureReason.ProviderUnavailable(ProviderId.ON_DEVICE, "offline_unavailable"), failed.reason)
            assertEquals(0, a.executions + b.executions)
            assertTrue(outcome.codes().contains("offline_unavailable"))
        }
    }

    @Test
    fun offlineOnlyRunsAProviderlessTier() = runTest {
        NoNetworkGuard.during {
            val cloud = tier("cloud", caps(ProviderId.ANTHROPIC))
            val grammar = tier("grammar", StrategyCapabilities.NO_PROVIDER, StrategyOutcome.Completed("local"))
            val outcome = ladder(
                grammar, cloud,
                policy = TierPolicySource.fixed(TierPolicy { offlineOnly = true }),
            ).execute(CommandInput("hi"))
            assertEquals("local", (outcome as CommandOutcome.Completed).reply)
            assertEquals(0, cloud.executions)
        }
    }

    @Test
    fun offlineOnlyRunsAnAvailableOnDeviceTierAndRefusesAnUnavailableOne() = runTest {
        NoNetworkGuard.during {
            val policy = TierPolicySource.fixed(TierPolicy { offlineOnly = true })
            val ready = tier("device", caps(ProviderId.ON_DEVICE), StrategyOutcome.Completed("on device"))
            val up = ladder(ready, policy = policy, onDevice = { true }).execute(CommandInput("hi"))
            assertEquals("on device", (up as CommandOutcome.Completed).reply)

            val notReady = tier("device", caps(ProviderId.ON_DEVICE), StrategyOutcome.Completed("on device"))
            val down = ladder(notReady, policy = policy, onDevice = { false }).execute(CommandInput("hi"))
            val failed = down as CommandOutcome.Failed
            assertEquals(FailureReason.ProviderUnavailable(ProviderId.ON_DEVICE, "offline_unavailable"), failed.reason)
            assertEquals(0, notReady.executions)
        }
    }

    @Test
    fun anUnavailableOnDeviceOnlyTierNeverClimbsToTheCloud() = runTest {
        NoNetworkGuard.during {
            val device = tier("device", caps(ProviderId.ON_DEVICE))
            val cloud = tier("cloud", caps(ProviderId.ANTHROPIC), StrategyOutcome.Completed("cloud"))
            val outcome = ladder(device, cloud).execute(CommandInput("hi"))
            val failed = outcome as CommandOutcome.Failed
            assertEquals(onDeviceUnavailable, failed.reason)
            assertEquals(0, device.executions)
            assertEquals(0, cloud.executions)
            assertTrue(outcome.codes().contains("on_device_unavailable"))
        }
    }

    @Test
    fun anAvailableOnDeviceOnlyTierRunsNormally() = runTest {
        NoNetworkGuard.during {
            val device = tier("device", caps(ProviderId.ON_DEVICE), StrategyOutcome.Completed("local model"))
            val outcome = ladder(device, onDevice = { true }).execute(CommandInput("hi"))
            assertEquals("local model", (outcome as CommandOutcome.Completed).reply)
        }
    }

    @Test
    fun aThrowingOnDeviceHookCountsAsUnavailable() = runTest {
        NoNetworkGuard.during {
            val device = tier("device", caps(ProviderId.ON_DEVICE))
            val outcome = ladder(device, onDevice = { error("hook broke") }).execute(CommandInput("hi"))
            val failed = outcome as CommandOutcome.Failed
            assertEquals(onDeviceUnavailable, failed.reason)
            assertTrue("the probe fault must be visible", outcome.codes().contains("on_device_probe_error"))
        }
    }

    @Test
    fun theOnDeviceHookDefaultsToUnavailable() = runTest {
        NoNetworkGuard.during {
            val device = tier("device", caps(ProviderId.ON_DEVICE))
            val pipeline = commandPipeline {
                tier(device)
                gate = ScriptedGate.admitAll()
                commitSink = RecordingCommitSink()
            }
            val outcome = pipeline.execute(CommandInput("hi"))
            assertTrue(outcome is CommandOutcome.Failed)
            assertEquals(0, device.executions)
        }
    }

    // ---- Unhandled.cappedByPolicy ----

    private fun CommandOutcome.capped(): Boolean = (this as CommandOutcome.Unhandled).cappedByPolicy

    @Test
    fun maxTierCuttingTheLadderThenNoHandlerIsCapped() = runTest {
        NoNetworkGuard.during {
            val outcome = ladder(
                tier("a", StrategyCapabilities.ANY_PROVIDER),
                tier("b", StrategyCapabilities.ANY_PROVIDER),
                policy = TierPolicySource.fixed(TierPolicy { maxTier = StrategyId("a") }),
            ).execute(CommandInput("hi"))
            assertTrue(outcome.capped())
        }
    }

    @Test
    fun aProviderRestrictionSkippingATierThenNoHandlerIsCapped() = runTest {
        NoNetworkGuard.during {
            val outcome = ladder(
                tier("openai", caps(ProviderId.OPENAI)),
                tier("anthropic", caps(ProviderId.ANTHROPIC)),
                policy = TierPolicySource.fixed(TierPolicy { allowedProviders = setOf(ProviderId.ANTHROPIC) }),
            ).execute(CommandInput("hi"))
            assertTrue(outcome.capped())
        }
    }

    @Test
    fun offlineOnlyDroppingACloudTierThenNoHandlerIsCapped() = runTest {
        NoNetworkGuard.during {
            val outcome = ladder(
                tier("grammar", StrategyCapabilities.NO_PROVIDER, StrategyOutcome.NoMatch()),
                tier("cloud", caps(ProviderId.ANTHROPIC)),
                policy = TierPolicySource.fixed(TierPolicy { offlineOnly = true }),
            ).execute(CommandInput("hi"))
            assertTrue(outcome.capped())
        }
    }

    @Test
    fun nothingSkippedByPolicyIsNotCappedWhetherTheLastTierHandsUpOrFindsNoMatch() = runTest {
        NoNetworkGuard.during {
            val handsUp = ladder(
                tier("a", StrategyCapabilities.ANY_PROVIDER),
                tier("b", StrategyCapabilities.ANY_PROVIDER),
            ).execute(CommandInput("hi"))
            assertFalse(handsUp.capped())

            val noMatch = ladder(
                tier("a", StrategyCapabilities.ANY_PROVIDER),
                tier("b", StrategyCapabilities.ANY_PROVIDER, StrategyOutcome.NoMatch()),
            ).execute(CommandInput("hi"))
            assertFalse(noMatch.capped())
        }
    }

    @Test
    fun aLadderWithEveryTierSkippedByPolicyFailsInsteadOfBeingUnhandled() = runTest {
        NoNetworkGuard.during {
            val capped = ladder(
                tier("a", caps(ProviderId.OPENAI)),
                policy = TierPolicySource.fixed(TierPolicy { allowedProviders = setOf(ProviderId.ANTHROPIC) }),
            ).execute(CommandInput("hi"))
            assertEquals(FailureReason.NoEligibleTier(), (capped as CommandOutcome.Failed).reason)

            val offline = ladder(
                tier("a", caps(ProviderId.ANTHROPIC)),
                policy = TierPolicySource.fixed(TierPolicy { offlineOnly = true }),
            ).execute(CommandInput("hi"))
            assertEquals(
                FailureReason.ProviderUnavailable(ProviderId.ON_DEVICE, "offline_unavailable"),
                (offline as CommandOutcome.Failed).reason,
            )
        }
    }

    @Test
    fun aTierSkippedByPolicyDoesNotMakeALaterCompletionUnhandled() = runTest {
        NoNetworkGuard.during {
            val outcome = ladder(
                tier("openai", caps(ProviderId.OPENAI)),
                tier("anthropic", caps(ProviderId.ANTHROPIC), StrategyOutcome.Completed("ok")),
                policy = TierPolicySource.fixed(TierPolicy { allowedProviders = setOf(ProviderId.ANTHROPIC) }),
            ).execute(CommandInput("hi"))
            assertTrue(outcome is CommandOutcome.Completed)
        }
    }

    @Test
    fun aSelectorStartingPastEarlierTiersIsNotAPolicySkip() = runTest {
        NoNetworkGuard.during {
            val outcome = ladder(
                tier("a", StrategyCapabilities.ANY_PROVIDER),
                tier("b", StrategyCapabilities.ANY_PROVIDER),
                selected = TierSelector.Fixed(StrategyId("b")),
            ).execute(CommandInput("hi"))
            assertFalse(outcome.capped())
        }
    }

    @Test
    fun unhandledToStringShowsTheFlag() = runTest {
        NoNetworkGuard.during {
            val outcome = ladder(tier("a", StrategyCapabilities.ANY_PROVIDER)).execute(CommandInput("hi"))
            assertTrue(outcome.toString().contains("cappedByPolicy=false"))
        }
    }

    @Test
    fun fixedTierCutByMaxTierFailsWithNoEligibleTier() = runTest {
        NoNetworkGuard.during {
            val a = tier("a", StrategyCapabilities.ANY_PROVIDER)
            val b = tier("b", StrategyCapabilities.ANY_PROVIDER)
            val outcome = ladder(
                a, b,
                policy = TierPolicySource.fixed(TierPolicy { maxTier = StrategyId("a") }),
                selected = TierSelector.Fixed(StrategyId("b")),
            ).execute(CommandInput("hi"))
            assertEquals(FailureReason.NoEligibleTier(), (outcome as CommandOutcome.Failed).reason)
            assertEquals(0, a.executions + b.executions)
        }
    }

    @Test
    fun aThrowingPolicySourceFailsWithPolicyUnavailableAndRunsNothing() = runTest {
        NoNetworkGuard.during {
            val a = tier("a", StrategyCapabilities.ANY_PROVIDER)
            val outcome = ladder(a, policy = TierPolicySource { error("settings gone") }).execute(CommandInput("hi"))
            assertEquals(FailureReason.PolicyUnavailable(), (outcome as CommandOutcome.Failed).reason)
            assertEquals(0, a.executions)
            assertEquals(listOf("policy_source_error"), outcome.codes())
        }
    }

    @Test
    fun aStrategyThatDeclaresNothingMeansAnyProvider() {
        val undeclared = object : CommandStrategy {
            override val id = StrategyId("undeclared")
            override suspend fun execute(input: CommandInput, session: CommandSession) = StrategyOutcome.NoMatch()
        }
        assertEquals(StrategyCapabilities.ANY_PROVIDER, undeclared.capabilities)
        assertEquals(
            setOf(ProviderId.ANTHROPIC, ProviderId.OPENAI, ProviderId.OPENROUTER, ProviderId.ON_DEVICE),
            StrategyCapabilities.ANY_PROVIDER.providers,
        )
        assertTrue(StrategyCapabilities.NO_PROVIDER.providers.isEmpty())
    }

    @Test
    fun capabilitiesCompareAndCopyByProviders() {
        val mutable = mutableSetOf(ProviderId.OPENAI)
        val declared = StrategyCapabilities(mutable)
        mutable.add(ProviderId.ANTHROPIC)
        assertEquals(setOf(ProviderId.OPENAI), declared.providers)
        assertEquals(StrategyCapabilities(setOf(ProviderId.OPENAI)), declared)
        assertEquals(StrategyCapabilities(setOf(ProviderId.OPENAI)).hashCode(), declared.hashCode())
    }

    private companion object {
        const val THREE = 3
        const val SIX = 6
        const val EIGHT = 8
        const val FOUR_K = 4_096
        const val SIXTY_THOUSAND = 60_000L
    }
}
