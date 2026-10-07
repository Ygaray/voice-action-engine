package io.github.ygaray.voiceactionengine.sample

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.commit.ActionEvent
import io.github.ygaray.voiceactionengine.core.commit.CommitSink
import io.github.ygaray.voiceactionengine.core.commit.RunTermination
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.provider.CredentialLookup
import io.github.ygaray.voiceactionengine.core.provider.CredentialSource
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.sample.evidence.ALLOW_PATTERN
import io.github.ygaray.voiceactionengine.sample.evidence.FileBudgetStore
import io.github.ygaray.voiceactionengine.sample.evidence.LegId
import io.github.ygaray.voiceactionengine.sample.evidence.RequestBudget
import io.github.ygaray.voiceactionengine.sample.legs.GRAMMAR_EN_TRANSCRIPT
import io.github.ygaray.voiceactionengine.sample.legs.GRAMMAR_ES_TRANSCRIPT
import io.github.ygaray.voiceactionengine.sample.legs.GrammarCase
import io.github.ygaray.voiceactionengine.sample.legs.GrammarLeg
import io.github.ygaray.voiceactionengine.sample.legs.GrammarLegRig
import io.github.ygaray.voiceactionengine.sample.legs.NoOpCommitSink
import io.github.ygaray.voiceactionengine.sample.net.AttemptTap
import io.github.ygaray.voiceactionengine.sample.verdict.AttemptRecord
import io.github.ygaray.voiceactionengine.sample.verdict.VerdictKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

private const val CANARY_TITLE = "zzcanarytitle"
private const val CANARY_WORD = "qqcanaryword"

/** Reports one HTTP attempt to the request tap when a change is committed: a stand-in for a transport that sent a request. */
private class AttemptOnCommit(private val tap: AttemptTap) : CommitSink {
    override suspend fun onAction(event: ActionEvent) {
        tap.record(AttemptRecord(ProviderId.ANTHROPIC, 1, "initial", 200, null, 0))
    }

    override suspend fun onRunClosed(runId: String, termination: RunTermination) = Unit

    override fun toString(): String = "AttemptOnCommit"
}

/** The offline grammar leg (VER-06, D-05), run with no provider reachable and every way it could pass vacuously tested. */
class GrammarLegTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val allow = Regex(ALLOW_PATTERN)
    private val noKeys = CredentialSource { CredentialLookup.Missing() }

    private fun rig() = legRig(folder.newFolder()) { emptyList() }

    private fun tapOver(sink: ListSink): AttemptTap =
        AttemptTap(RequestBudget(FileBudgetStore(File(folder.newFolder(), "budget.txt"))), sink)

    // A grammar rig that also registers a fake "real" provider behind the tap: any call to it would throw.
    private fun rigWithRealProvider(
        tap: AttemptTap,
        commits: CommitSink = NoOpCommitSink,
    ): Pair<GrammarLegRig, AttemptingFake> {
        val real = AttemptingFake(ProviderId.ANTHROPIC, tap, emptyList())
        return GrammarLegRig.create(listOf(real), noKeys, commits) to real
    }

    @Test
    fun theThreeCasesPassWithZeroCallsAndOneVerdict() = runTest {
        NoNetworkGuard.during {
            val rig = rig()

            val result = rig.runner.run(LegId.GRAMMAR_OFFLINE)

            assertEquals(result.toString(), VerdictKind.PASS, result.verdict.kind)
            val traces = rig.sink.starting("VAE_TRACE ")
            assertEquals(traces.toString(), 3, traces.size)
            assertTrue(traces[0], traces[0].contains(" case=1 kind=completed capped=none "))
            assertTrue(traces[0], traces[0].contains(" provider_turns=0 attempts=0 tripwire_calls=0 matched_lang=en "))
            assertTrue(traces[1], traces[1].contains(" case=2 kind=completed capped=none "))
            assertTrue(traces[1], traces[1].contains(" provider_turns=0 attempts=0 tripwire_calls=0 matched_lang=es "))
            assertTrue(traces[2], traces[2].contains(" case=3 kind=unhandled capped=true "))
            assertTrue(traces[2], traces[2].contains(" provider_turns=0 attempts=0 tripwire_calls=0 matched_lang=none "))
            assertTrue(traces[2], traces[2].contains("tier_skipped_policy"))
            val verdicts = rig.sink.starting("VAE_VERDICT ")
            assertEquals(verdicts.toString(), 1, verdicts.size)
            assertTrue(
                verdicts.single(),
                verdicts.single().startsWith(
                    "VAE_VERDICT leg=grammar_offline verdict=PASS en=1 es=1 near_miss_capped=1 " +
                        "provider_turns=0 attempts=0 tripwire_calls=0 trigger=ui",
                ),
            )
            assertEquals(1, rig.sink.starting("VAE_OUTCOME leg=grammar_offline kind=unhandled ").size)
            for (line in rig.sink.rendered) assertTrue(line, allow.matches(line))
        }
    }

    @Test
    fun theNearMissIsHandedOnWithNothingDoneAndNeverMatchedPartially() = runTest {
        NoNetworkGuard.during {
            val rig = rig()

            val result = rig.runner.run(LegId.GRAMMAR_OFFLINE)

            val outcome = result.outcome as CommandOutcome.Unhandled
            assertTrue(outcome.cappedByPolicy)
            assertTrue(outcome.executed.isEmpty())
            assertTrue(outcome.commits.isEmpty())
            // Only the two exact commands committed anything.
            assertEquals(2, rig.sink.starting("VAE_TRACE ").count { it.contains("kind=completed") })
        }
    }

    @Test
    fun aPolicyLeakReachesOnlyTheTripwireAndFailsTheLeg() = runTest {
        NoNetworkGuard.during {
            val sink = ListSink()
            val tap = tapOver(sink)
            val (grammarRig, real) = rigWithRealProvider(tap)

            // No offlineOnly: the single-shot tier is no longer dropped, so the near-miss reaches it.
            val run = GrammarLeg.run(grammarRig, tap, sink, LegId.GRAMMAR_OFFLINE, policy = TierPolicy { maxIterations = 3 })

            assertEquals(VerdictKind.FAIL, run.verdict.kind)
            assertEquals("provider_called", run.verdict.reason)
            assertEquals(1, grammarRig.tripwire.calls)
            assertTrue("a real provider was reached", real.calls.isEmpty())
            assertEquals(1L, run.extras["tripwire_calls"])
            assertEquals(0L, run.extras["attempts"])
        }
    }

    @Test
    fun anHttpAttemptSeenByTheTapFailsTheLegEvenWithZeroTurnsAndZeroTripwireCalls() = runTest {
        NoNetworkGuard.during {
            val sink = ListSink()
            val tap = tapOver(sink)
            val (grammarRig, _) = rigWithRealProvider(tap, AttemptOnCommit(tap))

            val run = GrammarLeg.run(grammarRig, tap, sink, LegId.GRAMMAR_OFFLINE)

            assertEquals(VerdictKind.FAIL, run.verdict.kind)
            assertEquals("http_attempted", run.verdict.reason)
            assertEquals(0L, run.extras["tripwire_calls"])
            assertEquals(0L, run.extras["provider_turns"])
            assertTrue(run.extras["attempts"]!! > 0L)
        }
    }

    @Test
    fun aSpanishPhrasingSentAsEnglishIsNotCompletedAndAMatchInTheWrongLanguageIsNamed() = runTest {
        NoNetworkGuard.during {
            val sink = ListSink()
            val tap = tapOver(sink)
            val (grammarRig, _) = rigWithRealProvider(tap)
            val spanishAsEnglish = listOf(GrammarCase(GRAMMAR_ES_TRANSCRIPT, "en", "en"))

            val unmatched = GrammarLeg.run(grammarRig, tap, sink, LegId.GRAMMAR_OFFLINE, cases = spanishAsEnglish)
            assertEquals("en_not_completed", unmatched.verdict.reason)

            val readBoth = GrammarLeg.run(
                grammarRig,
                tap,
                sink,
                LegId.GRAMMAR_OFFLINE,
                cases = spanishAsEnglish,
                pack = GrammarLeg.pack(tryOtherLanguage = true),
            )
            assertEquals(VerdictKind.FAIL, readBoth.verdict.kind)
            assertEquals("wrong_language", readBoth.verdict.reason)

            val spanishUnmatched = GrammarLeg.run(
                grammarRig,
                tap,
                sink,
                LegId.GRAMMAR_OFFLINE,
                cases = listOf(GrammarCase(GRAMMAR_EN_TRANSCRIPT, "es", "es")),
            )
            assertEquals("es_not_completed", spanishUnmatched.verdict.reason)
        }
    }

    @Test
    fun aNearMissThatCompletesIsNotCapped() = runTest {
        NoNetworkGuard.during {
            val sink = ListSink()
            val tap = tapOver(sink)
            val (grammarRig, _) = rigWithRealProvider(tap)

            val run = GrammarLeg.run(
                grammarRig,
                tap,
                sink,
                LegId.GRAMMAR_OFFLINE,
                cases = listOf(GrammarCase(GRAMMAR_EN_TRANSCRIPT, "en", null)),
            )

            assertEquals(VerdictKind.FAIL, run.verdict.kind)
            assertEquals("near_miss_not_capped", run.verdict.reason)
        }
    }

    @Test
    fun noTitleNoTranscriptWordAndNoPhrasingReachesAnyLine() = runTest {
        NoNetworkGuard.during {
            val sink = ListSink()
            val tap = tapOver(sink)
            val (grammarRig, _) = rigWithRealProvider(tap)
            val cases = listOf(
                GrammarCase("add $CANARY_TITLE to my list", "en", "en"),
                GrammarCase("add $CANARY_TITLE to my list $CANARY_WORD", "en", null),
            )

            val run = GrammarLeg.run(grammarRig, tap, sink, LegId.GRAMMAR_OFFLINE, cases = cases)

            assertEquals(run.toString(), VerdictKind.PASS, run.verdict.kind)
            val text = sink.rendered.joinToString("\n").lowercase()
            assertFalse(text, text.contains(CANARY_TITLE))
            assertFalse(text, text.contains(CANARY_WORD))
            for (word in listOf("paper", "papel", "agrega", "list")) assertFalse(text, text.contains(word))
            for (line in sink.rendered) assertTrue(line, allow.matches(line))
        }
    }
}
