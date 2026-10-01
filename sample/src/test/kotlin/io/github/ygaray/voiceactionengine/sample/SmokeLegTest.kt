package io.github.ygaray.voiceactionengine.sample

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import io.github.ygaray.voiceactionengine.keystore.KeyState
import io.github.ygaray.voiceactionengine.sample.evidence.BudgetedProvider
import io.github.ygaray.voiceactionengine.sample.evidence.LegId
import io.github.ygaray.voiceactionengine.sample.net.ProviderFactory
import io.github.ygaray.voiceactionengine.sample.verdict.VerdictKind
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val EDIT_TOOL = "edit_item"

/** The three single-shot EDIT smokes, run through the real runner, tap and budget wrapper with fake transports. */
class SmokeLegTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val usage = Usage(10, 0, 0, 5)

    private fun args(withTitle: Boolean = false): JsonObject = buildJsonObject {
        put("id", "c-42")
        put("body", "buy more paper")
        if (withTitle) put("title", "")
    }

    private fun editCall(withTitle: Boolean = false) =
        ok(FakeAiProvider.toolCall("call_1", EDIT_TOOL, args(withTitle), usage))

    private fun anthropicRig(vararg steps: AttemptStep) =
        legRig(folder.newFolder()) { tap -> listOf(AttemptingFake(ProviderId.ANTHROPIC, tap, steps.toList())) }

    @Test
    fun theAnthropicEditSmokePassesWithOptionalsAbsent() = runTest {
        NoNetworkGuard.during {
            val rig = anthropicRig(editCall())

            val result = rig.runner.run(LegId.SMOKE_ANTHROPIC)

            assertEquals(result.toString(), VerdictKind.PASS, result.verdict.kind)
            val request = rig.fake(ProviderId.ANTHROPIC).calls.single().request
            assertEquals(ToolChoice.Required(EDIT_TOOL), request.toolChoice)
            assertTrue(request.singleToolCall)
            val lines = rig.sink.rendered
            assertTrue(
                lines.toString(),
                "VAE_ATTEMPT leg=smoke_anthropic provider=anthropic n=1 kind=initial http=200 finish=none tool_calls=0" in lines,
            )
            assertTrue(
                lines.toString(),
                "VAE_SMOKE leg=smoke_anthropic tool=edit_item arg_keys=[body,id] optional_absent=true prompt_variant=0" in lines,
            )
            assertTrue(
                lines.toString(),
                "VAE_VERDICT leg=smoke_anthropic verdict=PASS key_charset=ok trigger=ui" in lines,
            )
            assertEquals(1, rig.sink.starting("VAE_BUDGET core=1 ").size)
        }
    }

    @Test
    fun aFilledOptionalIsInconclusiveAndTheRerunUsesTheStrongerPrompt() = runTest {
        NoNetworkGuard.during {
            val rig = anthropicRig(editCall(withTitle = true), editCall())

            val first = rig.runner.run(LegId.SMOKE_ANTHROPIC)
            val second = rig.runner.run(LegId.SMOKE_ANTHROPIC)

            assertEquals(VerdictKind.INCONCLUSIVE, first.verdict.kind)
            assertEquals("model_filled_optional", first.verdict.reason)
            assertEquals(VerdictKind.PASS, second.verdict.kind)
            val calls = rig.fake(ProviderId.ANTHROPIC).calls
            val firstText = (calls[0].request.messages.first() as UserMessage).text
            val secondText = (calls[1].request.messages.first() as UserMessage).text
            assertTrue(firstText, "change only the body" in firstText)
            assertTrue(secondText, "do not include title or tags at all" in secondText)
            val smokeLines = rig.sink.starting("VAE_SMOKE ")
            assertTrue(smokeLines.toString(), smokeLines[0].endsWith("optional_absent=inconclusive prompt_variant=0"))
            assertTrue(smokeLines.toString(), smokeLines[1].endsWith("optional_absent=true prompt_variant=1"))
        }
    }

    @Test
    fun aReshapedAnthropicSmokeFails() = runTest {
        NoNetworkGuard.during {
            val reshaped = AttemptStep(
                listOf("initial" to 400, "forced_tool_reshape" to 200),
                FakeAiProvider.toolCall("call_1", EDIT_TOOL, args(), usage),
            )
            val rig = anthropicRig(reshaped)

            val result = rig.runner.run(LegId.SMOKE_ANTHROPIC)

            assertEquals(VerdictKind.FAIL, result.verdict.kind)
            assertEquals("reshape_seen", result.verdict.reason)
            assertEquals(1, rig.sink.starting("VAE_VERDICT leg=smoke_anthropic verdict=FAIL reason=reshape_seen").size)
        }
    }

    @Test
    fun theChatSmokesPassOnOpenAiAndOpenRouter() = runTest {
        NoNetworkGuard.during {
            val rig = legRig(folder.newFolder()) { tap ->
                listOf(
                    AttemptingFake(ProviderId.OPENAI, tap, listOf(editCall())),
                    AttemptingFake(ProviderId.OPENROUTER, tap, listOf(editCall())),
                )
            }

            val openAi = rig.runner.run(LegId.SMOKE_OPENAI)
            val openRouter = rig.runner.run(LegId.SMOKE_OPENROUTER)

            assertEquals(openAi.toString(), VerdictKind.PASS, openAi.verdict.kind)
            assertEquals(openRouter.toString(), VerdictKind.PASS, openRouter.verdict.kind)
            assertEquals("gpt-5.4-mini", rig.fake(ProviderId.OPENAI).calls.single().model)
            assertEquals("openai/gpt-5.4-mini", rig.fake(ProviderId.OPENROUTER).calls.single().model)
            assertEquals(2, rig.budget.snapshot().core)
        }
    }

    @Test
    fun refusalsSendNothing() = runTest {
        NoNetworkGuard.during {
            val noKey = anthropicRig(editCall())
            noKey.vault.set(ProviderId.ANTHROPIC, KeyState.NotConfigured())

            val refusedForKey = noKey.runner.run(LegId.SMOKE_ANTHROPIC)

            assertEquals(VerdictKind.REFUSED, refusedForKey.verdict.kind)
            assertEquals("key_NotConfigured", refusedForKey.verdict.reason)
            assertEquals(0, noKey.fake(ProviderId.ANTHROPIC).calls.size)
            assertEquals(
                listOf("VAE_VERDICT leg=smoke_anthropic verdict=REFUSED reason=key_NotConfigured trigger=ui"),
                noKey.sink.rendered,
            )

            val spent = anthropicRig(editCall())
            repeat(31) { spent.budget.record(ProviderId.ANTHROPIC, false) }

            val refusedForBudget = spent.runner.run(LegId.SMOKE_ANTHROPIC)

            assertEquals("budget", refusedForBudget.verdict.reason)
            assertEquals(0, spent.fake(ProviderId.ANTHROPIC).calls.size)
            assertEquals(31, spent.budget.snapshot().core)
        }
    }

    @Test
    fun productionProvidersAreBudgetWrappedAndNeedNoNetworkToBuild() = runTest {
        NoNetworkGuard.during {
            val rig = anthropicRig(editCall())

            val providers = ProviderFactory.create(rig.tap, rig.budget)

            assertEquals(listOf("anthropic", "openai", "openrouter"), providers.map { it.id.value })
            assertTrue(providers.all { it is BudgetedProvider })
        }
    }

    @Test
    fun theResponsesProbeIsCapturedNotPassed() = runTest {
        NoNetworkGuard.during {
            val rejected = AttemptStep(listOf("initial" to 400), ModelResult.Failure(FailureReason.ModelUnsupported()))
            val rig = legRig(folder.newFolder()) { tap ->
                listOf(AttemptingFake(ProviderId.OPENAI, tap, listOf(rejected, rejected)))
            }

            val result = rig.runner.run(LegId.RESPONSES_PROBE)

            assertEquals(result.toString(), VerdictKind.CAPTURED, result.verdict.kind)
            assertEquals("model_unsupported", result.verdict.reason)
            assertTrue(
                rig.sink.rendered.toString(),
                "VAE_VERDICT leg=responses_probe verdict=CAPTURED reason=model_unsupported http=400 trigger=ui" in
                    rig.sink.rendered,
            )
            assertEquals("gpt-6-astra", rig.fake(ProviderId.OPENAI).calls.single().model)
            // It spent from the optional pool, and the optional pool allows one probe only.
            assertEquals(0, rig.budget.snapshot().core)
            assertEquals(1, rig.budget.snapshot().optional)

            val again = rig.runner.run(LegId.RESPONSES_PROBE)

            assertEquals(VerdictKind.REFUSED, again.verdict.kind)
            assertEquals("budget", again.verdict.reason)
            assertEquals(1, rig.fake(ProviderId.OPENAI).calls.size)
        }
    }
}
