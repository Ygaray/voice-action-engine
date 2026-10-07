package io.github.ygaray.voiceactionengine.sample

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedCredentialSource
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import io.github.ygaray.voiceactionengine.sample.evidence.ALLOW_PATTERN
import io.github.ygaray.voiceactionengine.sample.evidence.LegId
import io.github.ygaray.voiceactionengine.sample.legs.ItemWorld
import io.github.ygaray.voiceactionengine.sample.legs.LegCatalog
import io.github.ygaray.voiceactionengine.sample.legs.PlanLegs
import io.github.ygaray.voiceactionengine.sample.verdict.VerdictKind
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val FIRST_TITLE = "zzgammacanary"
private const val SECOND_TITLE = "zzdeltacanary"
private const val ROUTER_TOOL = "pick_start_tier"

/** The `router_live` leg run through the real runner, tap and budget wrapper over scripted answers (no live call). */
class RouterLegTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val routerUsage = Usage(20, 0, 0, 3)
    private val workUsage = Usage(10, 0, 0, 5)
    private val allow = Regex(ALLOW_PATTERN)

    private fun pick(tier: String) =
        ok(FakeAiProvider.toolCall("call_pick", ROUTER_TOOL, buildJsonObject { put("tier", tier) }, routerUsage))

    private fun createStep(id: String, title: String, parent: String?): JsonObject = buildJsonObject {
        put("id", id)
        put("tool", "create_item")
        put(
            "arguments",
            buildJsonObject {
                put("title", title)
                if (parent != null) put("parent_id", parent)
            },
        )
    }

    private fun twoStepPlan() = ok(
        FakeAiProvider.toolCall(
            "call_plan",
            "submit_plan",
            buildJsonObject {
                put(
                    "steps",
                    buildJsonArray {
                        add(createStep("stepone", FIRST_TITLE, null))
                        add(createStep("steptwo", SECOND_TITLE, "\$stepone.id"))
                    },
                )
            },
            workUsage,
        ),
    )

    private fun singleCall() = ok(
        FakeAiProvider.toolCall(
            "call_single",
            "create_item",
            buildJsonObject { put("title", FIRST_TITLE) },
            workUsage,
        ),
    )

    private fun rigOf(vararg steps: AttemptStep) =
        legRig(folder.newFolder()) { tap -> listOf(AttemptingFake(ProviderId.ANTHROPIC, tap, steps.toList())) }

    @Test
    fun aPickOfThePlanTierPassesAndTheSelectionIsVisibleInTheTrace() = runTest {
        NoNetworkGuard.during {
            val rig = rigOf(pick("plan"), twoStepPlan())

            val result = rig.runner.run(LegId.ROUTER_LIVE)

            assertEquals(result.toString(), VerdictKind.PASS, result.verdict.kind)
            val trace = rig.sink.starting("VAE_TRACE ").single()
            assertTrue(trace, trace.contains(" sel=picked eligible=2 picked_index=1 first_model_index=1 bypassed=1 sel_turns=1 "))
            val verdict = rig.sink.starting("VAE_VERDICT ").single()
            assertTrue(
                verdict,
                verdict.startsWith(
                    "VAE_VERDICT leg=router_live verdict=PASS eligible=2 picked_index=1 sel_turns=1 router_tokens=23 ",
                ),
            )
            // PickContext accounting: the router's 23 tokens are part of the trace's usage, beside the plan's 15.
            val selection = checkNotNull(result.outcome).trace.selection
            assertNotNull(selection)
            assertEquals(23L, selection!!.usage.total)
            assertEquals(38L, result.outcome!!.trace.usage.total)
            // The router call came first and was forced; the plan call second.
            val calls = rig.fake(ProviderId.ANTHROPIC).calls
            assertEquals(2, calls.size)
            assertEquals(ToolChoice.Required(ROUTER_TOOL), calls[0].request.toolChoice)
            assertEquals(ToolChoice.Required("submit_plan"), calls[1].request.toolChoice)
            val routerText = (calls[0].request.messages.first() as UserMessage).text
            assertTrue(routerText, "- single: " in routerText && "- plan: " in routerText)
            assertEquals(1, rig.sink.starting("VAE_BUDGET core=2 ").size)
        }
    }

    @Test
    fun theTranscriptIsNotAGrammarPhrasingSoTheHeadHandsItOn() = runTest {
        NoNetworkGuard.during {
            val rig = rigOf(pick("plan"), twoStepPlan())

            val result = rig.runner.run(LegId.ROUTER_LIVE)

            val attempts = checkNotNull(result.outcome).trace.attempts
            assertEquals("grammar", attempts.first().strategy.value)
            assertEquals("no_match", attempts.first().outcome)
            assertEquals("plan", attempts.last().strategy.value)
            for (prompt in LegCatalog.spec(LegId.ROUTER_LIVE).prompts) {
                assertFalse(prompt, prompt.startsWith("add ") && prompt.endsWith(" to my list"))
            }
        }
    }

    @Test
    fun aPickOfTheFirstModelTierIsInconclusiveNeverAPass() = runTest {
        NoNetworkGuard.during {
            val rig = rigOf(pick("single"), singleCall())

            val result = rig.runner.run(LegId.ROUTER_LIVE)

            assertEquals(result.toString(), VerdictKind.INCONCLUSIVE, result.verdict.kind)
            assertEquals("picked_first", result.verdict.reason)
            val trace = rig.sink.starting("VAE_TRACE ").single()
            assertTrue(trace, trace.contains(" sel=picked eligible=2 picked_index=0 first_model_index=0 bypassed=0 sel_turns=1 "))
            assertTrue(rig.sink.starting("VAE_VERDICT ").single().startsWith("VAE_VERDICT leg=router_live verdict=INCONCLUSIVE reason=picked_first "))
        }
    }

    @Test
    fun anAnswerThatIsNotATierIdIsARouterFallbackAndFailsTheLeg() = runTest {
        NoNetworkGuard.during {
            val rig = rigOf(pick("no_such_tier"), singleCall())

            val result = rig.runner.run(LegId.ROUTER_LIVE)

            assertEquals(result.toString(), VerdictKind.FAIL, result.verdict.kind)
            assertEquals("router_fallback", result.verdict.reason)
            // The walk still started at the first model tier, so the command did not stop for the failed pick.
            val trace = rig.sink.starting("VAE_TRACE ").single()
            assertTrue(trace, trace.contains(" sel=router_fallback eligible=2 picked_index=none first_model_index=0 bypassed=0 "))
            assertTrue(trace, trace.endsWith(",router_fallback]"))
        }
    }

    @Test
    fun aLadderWithOneModelTierMakesNoRouterCallAndNeverPasses() = runTest {
        NoNetworkGuard.during {
            val world = ItemWorld()
            val fake = FakeAiProvider(ProviderId.ANTHROPIC, twoStepPlan().result)
            val engine = SampleEngine(
                listOf(fake),
                ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to FAKE_KEY),
                RecordingCommitSink(),
                null,
            )
            val ladder = PlanLegs.routerLadder(world, withSingle = false)
            val pipeline = engine.pipeline(
                tier = ladder.first(),
                selection = ProviderSelection(ProviderId.ANTHROPIC, "claude-haiku-4-5"),
                policy = TierPolicy { maxIterations = 4 },
            ) {
                ladder.drop(1).forEach { tier(it) }
                selector = PlanLegs.routerSelector()
            }

            val outcome = pipeline.execute(CommandInput(LegCatalog.spec(LegId.ROUTER_LIVE).prompts.first()))

            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            assertNull(outcome.trace.selection)
            // Only the plan call went out: the router was not asked.
            assertEquals(1, fake.callCount)
            val judged = PlanLegs.judgeRouter(outcome)
            assertEquals(VerdictKind.FAIL, judged.verdict.kind)
            assertEquals("no_selection", judged.verdict.reason)
        }
    }

    @Test
    fun aRouterThatNeverRanIsNotAPassEvenWhenTheCommandCompletes() = runTest {
        NoNetworkGuard.during {
            val world = ItemWorld()
            val fake = FakeAiProvider(ProviderId.ANTHROPIC, twoStepPlan().result)
            val engine = SampleEngine(
                listOf(fake),
                ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to FAKE_KEY),
                RecordingCommitSink(),
                null,
            )
            // A plain linear walk: no selector at all, so the trace has no selection.
            val pipeline = engine.pipeline(
                tier = PlanLegs.planTier(world),
                selection = ProviderSelection(ProviderId.ANTHROPIC, "claude-haiku-4-5"),
                policy = TierPolicy { maxIterations = 4 },
            )

            val outcome = pipeline.execute(CommandInput(LegCatalog.spec(LegId.ROUTER_LIVE).prompts.first()))

            assertEquals("no_selection", PlanLegs.judgeRouter(outcome).verdict.reason)
        }
    }

    @Test
    fun noTierIdDescriptionWordTitleOrTranscriptWordReachesAnyLine() = runTest {
        NoNetworkGuard.during {
            val rig = rigOf(pick("plan"), twoStepPlan())

            rig.runner.run(LegId.ROUTER_LIVE)

            val lines = rig.sink.rendered
            val tokens = lines.flatMap { it.split(' ', '=', ',', '[', ']') }.toSet()
            for (id in listOf("single", "plan", "grammar", "start_tier_router")) {
                assertFalse("tier id $id is a field value", id in tokens)
            }
            val text = lines.joinToString("\n").lowercase()
            val leaked = listOf(FIRST_TITLE, SECOND_TITLE, "gamma", "delta", "simple change", "earlier change", "stepone", "\$")
            for (word in leaked) assertFalse("$word in $text", text.contains(word))
            for (line in lines) assertTrue(line, allow.matches(line))
        }
    }

    @Test
    fun theFixtureMakesThePlanTierTheOnlyCorrectPick() = runTest {
        NoNetworkGuard.during {
            val rig = rigOf(pick("plan"), twoStepPlan())
            rig.runner.run(LegId.ROUTER_LIVE)
            val routerText = (rig.fake(ProviderId.ANTHROPIC).calls.first().request.messages.first() as UserMessage).text

            // The first-listed model tier (single) is described as one change with no dependency; the plan tier as the one
            // whose later change needs an earlier change's new id; and every prompt asks for exactly that dependent pair.
            val singleLine = routerText.lines().single { it.startsWith("- single: ") }
            val planLine = routerText.lines().single { it.startsWith("- plan: ") }
            assertTrue(singleLine, "one simple change" in singleLine.lowercase() && "nothing that depends" in singleLine)
            assertTrue(planLine, "later change needs the new id" in planLine)
            for (prompt in LegCatalog.spec(LegId.ROUTER_LIVE).prompts) {
                assertTrue(prompt, prompt.contains("under"))
                assertEquals(prompt, 2, Regex("create an item called", RegexOption.IGNORE_CASE).findAll(prompt).count())
            }
        }
    }

    @Test
    fun theTierDescriptionsAreSyntheticAndCarryNoSecretOrTitle() = runTest {
        NoNetworkGuard.during {
            val rig = rigOf(pick("plan"), twoStepPlan())

            rig.runner.run(LegId.ROUTER_LIVE)

            val routerText = (rig.fake(ProviderId.ANTHROPIC).calls.first().request.messages.first() as UserMessage).text
            val descriptionLines = routerText.lines().filter { it.startsWith("- ") }
            assertEquals(descriptionLines.toString(), 2, descriptionLines.size)
            assertTrue(descriptionLines.toString(), descriptionLines.all { StrategyId(it.substring(2).substringBefore(':')).value in setOf("single", "plan") })
            for (line in descriptionLines) assertFalse(line, line.contains(FAKE_KEY) || line.contains("sk-"))
        }
    }
}
