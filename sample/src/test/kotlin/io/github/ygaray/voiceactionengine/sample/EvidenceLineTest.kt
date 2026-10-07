package io.github.ygaray.voiceactionengine.sample

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.telemetry.TurnRecord
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.sample.evidence.ALLOW_PATTERN
import io.github.ygaray.voiceactionengine.sample.evidence.BudgetSnapshot
import io.github.ygaray.voiceactionengine.sample.evidence.EvidenceLine
import io.github.ygaray.voiceactionengine.sample.evidence.LegId
import io.github.ygaray.voiceactionengine.sample.evidence.TraceFacts
import io.github.ygaray.voiceactionengine.sample.evidence.UndoFacts
import io.github.ygaray.voiceactionengine.sample.evidence.fixtureBacked
import io.github.ygaray.voiceactionengine.sample.fixture.FixtureState
import io.github.ygaray.voiceactionengine.sample.keys.ImportReport
import io.github.ygaray.voiceactionengine.sample.verdict.AttemptRecord
import io.github.ygaray.voiceactionengine.sample.verdict.OutcomeSummary
import io.github.ygaray.voiceactionengine.sample.verdict.Verdict
import io.github.ygaray.voiceactionengine.sample.verdict.VerdictKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val CANARY_PROMPT_TEXT = "CANARY_PROMPT_TEXT with spaces, \"quotes\" and\na newline"
private const val GOLDEN_RESOURCE = "evidence-lines.golden.txt"
private const val MAX_TOKEN = 96
private const val HEX_REST = 56

/** The evidence vocabulary contract: nothing free-form reaches a line, and every line fits the allow pattern. */
class EvidenceLineTest {

    private val allow = Regex(ALLOW_PATTERN)

    private fun turnWith(model: String?, stop: String?, tools: List<String>): TurnRecord =
        TurnRecord(ProviderId.ANTHROPIC, model, stop, tools, Usage(40, 0, 7016, 20), 812)

    /** One line of every type, rendered from fixed synthetic inputs. */
    private fun oneOfEach(): List<EvidenceLine> = listOf(
        EvidenceLine.env("5.2.1", "0123abcd", 4, 4096, 21109, 5277),
        EvidenceLine.fixture(FixtureState.Absent(listOf("files", "asset"))),
        EvidenceLine.key(ImportReport(ProviderId.ANTHROPIC, ImportReport.READY, null, true, false)),
        EvidenceLine.turn(LegId.VER02, 1, turnWith("claude-haiku-4-5", "tool_use", listOf("find_items")), 21109),
        EvidenceLine.turn(LegId.MULTI_OPENAI, 1, turnWith("gpt-5-mini", "tool_calls", listOf("find_items")), null),
        EvidenceLine.attempt(LegId.VER02, AttemptRecord(ProviderId.ANTHROPIC, 1, "initial", 200, null, 0)),
        EvidenceLine.cache(LegId.VER02, ProviderId.ANTHROPIC, "claude-haiku-4-5"),
        EvidenceLine.smoke(LegId.SMOKE_OPENROUTER, "edit_item", setOf("id", "body"), "true", 0),
        EvidenceLine.outcome(
            LegId.VER02,
            OutcomeSummary(OutcomeSummary.COMPLETED, false, null, 1, 1, 0, 4, null),
        ),
        EvidenceLine.verdict(
            LegId.VER02,
            Verdict.pass(),
            linkedMapOf("turn1_write" to 7016L, "min_read" to 7016L, "calls" to 2L),
            true,
            "ui",
        ),
        EvidenceLine.budget(BudgetSnapshot(14, 0, mapOf("anthropic" to 3, "openai" to 5, "openrouter" to 6)), "0.01234"),
        EvidenceLine.autorun(LegId.VER02),
        EvidenceLine.trace(
            LegId.GRAMMAR_OFFLINE,
            1,
            TraceFacts(
                kind = "completed",
                capped = null,
                tiersRun = 1,
                providerTurns = 0,
                attempts = 0,
                tripwireCalls = 0,
                matchedLang = "en",
                codes = listOf(TraceCode.TIER_SKIPPED_POLICY),
            ),
        ),
        EvidenceLine.undo(
            LegId.UNDO_ALL,
            1,
            UndoFacts(phase = "counted", n = 3, committed = 3, pending = 0, withheld = false, partial = false, remaining = 0),
        ),
    )

    private fun golden(): List<String> =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(GOLDEN_RESOURCE)) { "missing golden resource" }
            .use { it.readBytes().toString(Charsets.UTF_8) }
            .lines()
            .filter { it.isNotEmpty() }

    @Test
    fun noFreeTextCanReachALine() {
        val canary = CANARY_PROMPT_TEXT
        val lines = listOf(
            EvidenceLine.env(canary, canary, 1, 1, 1, 1),
            EvidenceLine.fixture(FixtureState.Loaded(canary, emptyList(), canary, canary, 1, 1)),
            EvidenceLine.fixture(FixtureState.Absent(listOf(canary))),
            EvidenceLine.fixture(FixtureState.ShaMismatch(canary, canary)),
            EvidenceLine.fixture(FixtureState.Malformed(canary, canary)),
            EvidenceLine.key(ImportReport(ProviderId(canary), canary, canary, true, false)),
            EvidenceLine.turn(LegId.VER02, 1, turnWith(canary, canary, listOf(canary, "find_items")), 1),
            EvidenceLine.attempt(LegId.VER02, AttemptRecord(ProviderId(canary), 1, canary, 200, canary, 0)),
            EvidenceLine.cache(LegId.VER02, ProviderId(canary), canary),
            EvidenceLine.smoke(LegId.SMOKE_OPENAI, canary, setOf(canary, "id"), canary, 0),
            EvidenceLine.outcome(LegId.VER02, OutcomeSummary(canary, false, canary, 0, 0, 0, 0, canary)),
            EvidenceLine.verdict(LegId.VER02, Verdict(VerdictKind.FAIL, canary), mapOf(canary to 1L), null, canary),
            EvidenceLine.budget(BudgetSnapshot(1, 0, emptyMap()), canary),
        )
        for (line in lines) {
            val text = line.render()
            assertFalse(text, text.contains("CANARY_PROMPT_TEXT"))
            assertFalse(text, text.contains("with spaces") || text.contains('"'))
            assertTrue(text, text.contains("invalid_token") || text.contains("invalid_key"))
            assertTrue(text, allow.matches(text))
        }
    }

    @Test
    fun aValueIsKeptOnlyWhenItFitsTheAlphabetAndTheLength() {
        assertEquals("a-b_c.d:e/f,g[h]", EvidenceLine.token("a-b_c.d:e/f,g[h]"))
        assertEquals("x".repeat(MAX_TOKEN), EvidenceLine.token("x".repeat(MAX_TOKEN)))
        assertEquals("invalid_token", EvidenceLine.token("x".repeat(MAX_TOKEN + 1)))
        assertEquals("invalid_token", EvidenceLine.token("two words"))
        assertEquals("invalid_token", EvidenceLine.token("a=b"))
        assertEquals("invalid_token", EvidenceLine.token("tab\there"))
    }

    @Test
    fun aToolNameThatBreaksTheListIsReplacedNotSplit() {
        val line = EvidenceLine.turn(LegId.MULTI_OPENAI, 1, turnWith("m", "s", listOf("a,b", "c]d", "ok")), null).render()
        assertTrue(line, line.contains("tools=[invalid_token,invalid_token,ok]"))
        assertTrue(line, line.contains("prefix_chars=none"))
    }

    @Test
    fun theFixtureLegNeverNamesATool() {
        val names = listOf("zz_private_tool_one", "zz_private_tool_two")
        val turn = EvidenceLine.turn(LegId.VER02, 1, turnWith("m", "s", names), 1).render()
        assertTrue(turn, turn.contains(" tools=redacted tool_count=2 "))
        val outcome = OutcomeSummary(OutcomeSummary.COMPLETED, false, null, 1, 1, 0, 4, names.first())
        val outcomeLine = EvidenceLine.outcome(LegId.VER02, outcome).render()
        assertTrue(outcomeLine, outcomeLine.contains(" terminal_tool=redacted"))
        for (text in listOf(turn, outcomeLine)) {
            assertFalse(text, text.contains("zz_private"))
            assertTrue(text, allow.matches(text))
        }
        // Another leg keeps its (synthetic) names: only the fixture leg is redacted.
        val other = EvidenceLine.turn(LegId.MULTI_OPENAI, 1, turnWith("m", "s", names), 1).render()
        assertTrue(other, other.contains("tools=[zz_private_tool_one,zz_private_tool_two] tool_count=2"))
    }

    @Test
    fun theFixtureLegLogsOnlyTheEightHexDigestPrefix() {
        val full = "0123abcd" + "f".repeat(HEX_REST)
        val env = EvidenceLine.env("5.2.1", full, 4, 4096, 21109, 5277).render()
        assertTrue(env, env.contains(" fixture_sha=0123abcd "))
        assertFalse(env, env.contains("fff"))
        val loaded = EvidenceLine.fixture(FixtureState.Loaded("sys", emptyList(), full, "files", 1, 1)).render()
        assertTrue(loaded, loaded.contains(" sha=0123abcd "))
        val mismatch = EvidenceLine.fixture(FixtureState.ShaMismatch("files", full)).render()
        assertTrue(mismatch, mismatch.endsWith(" actual=0123abcd"))
    }

    @Test
    fun loudLinesAreTheFailuresAndTheProblems() {
        assertTrue(EvidenceLine.fixture(FixtureState.Absent(listOf("files"))).loud)
        assertTrue(EvidenceLine.fixture(FixtureState.ShaMismatch("files", "0123abcd")).loud)
        assertTrue(EvidenceLine.fixture(FixtureState.Malformed("files", "bad_tool")).loud)
        assertTrue(EvidenceLine.cache(LegId.VER02, ProviderId.ANTHROPIC, null).loud)
        val fail = Verdict.fail("single_turn")
        assertTrue(EvidenceLine.verdict(LegId.VER02, fail, emptyMap(), null, "ui").loud)
        val refused = Verdict(VerdictKind.REFUSED, "budget")
        assertTrue(EvidenceLine.verdict(LegId.VER02, refused, emptyMap(), null, "ui").loud)
        assertFalse(EvidenceLine.verdict(LegId.VER02, Verdict.pass(), emptyMap(), null, "ui").loud)
        assertTrue(EvidenceLine.key(ImportReport(ProviderId.OPENAI, ImportReport.READY, null, false, null)).loud)
        assertTrue(EvidenceLine.key(ImportReport(ProviderId.OPENAI, ImportReport.READY, null, true, true)).loud)
        assertFalse(EvidenceLine.key(ImportReport(ProviderId.OPENAI, ImportReport.ABSENT_FILE, null, true, null)).loud)
    }

    @Test
    fun aTraceLineCarriesOnlyCountsCodesAndIndexes() {
        // Would-be field values that are a tier id, a transcript word and a slot value: all fit the token alphabet.
        val facts = TraceFacts(
            kind = "tier-single-id",
            capped = true,
            tiersRun = 2,
            providerTurns = 0,
            attempts = 0,
            tripwireCalls = 0,
            matchedLang = "paper",
            sel = "single",
            eligible = 2,
            pickedIndex = 1,
            firstModelIndex = 1,
            bypassed = 1,
            selTurns = 1,
            codes = listOf(TraceCode.TIER_SKIPPED_POLICY, TraceCode.ROUTER_FALLBACK),
        )
        val text = EvidenceLine.trace(LegId.GRAMMAR_OFFLINE, 3, facts).render()
        for (leaked in listOf("tier-single-id", "paper", "single")) assertFalse(text, text.contains(leaked))
        assertTrue(text, text.contains(" kind=invalid_token "))
        assertTrue(text, text.contains(" matched_lang=invalid_token sel=invalid_token "))
        assertTrue(text, text.contains(" eligible=2 picked_index=1 first_model_index=1 bypassed=1 sel_turns=1 "))
        assertTrue(text, text.endsWith(" codes=[tier_skipped_policy,router_fallback]"))
        assertTrue(text, allow.matches(text))
    }

    @Test
    fun anUndoLineCarriesOnlyCountsBooleansAndStableCodes() {
        // Would-be field values that fit the token alphabet: a phase word, an item id, a title and a result word.
        val facts = UndoFacts(
            phase = "item-1",
            n = 3,
            committed = 3,
            pending = 1,
            withheld = false,
            partial = true,
            remaining = 1,
            result = "seeded-title",
            restored = 2,
            blockers = 1,
            reason = "changed_since",
            storeOk = true,
        )
        val text = EvidenceLine.undo(LegId.UNDO_ALL, 3, facts).render()
        for (leaked in listOf("item-1", "seeded-title")) assertFalse(text, text.contains(leaked))
        assertTrue(text, text.contains(" phase=invalid_token n=3 committed=3 pending=1 withheld=false partial=true remaining=1 "))
        assertTrue(text, text.endsWith(" result=invalid_token restored=2 blockers=1 reason=changed_since store_ok=true"))
        assertTrue(text, allow.matches(text))
        assertFalse(EvidenceLine.undo(LegId.UNDO_ALL, 1, facts).loud)
        assertFalse(LegId.UNDO_ALL.fixtureBacked)
    }

    @Test
    fun aTraceLineIsNeverLoudAndNeverFixtureRedacted() {
        val facts = TraceFacts("unhandled", true, 1, 0, 0, 0, null)
        val line = EvidenceLine.trace(LegId.GRAMMAR_OFFLINE, 1, facts)
        assertFalse(line.loud)
        assertTrue(line.render(), line.render().contains(" matched_lang=none "))
        assertFalse(LegId.GRAMMAR_OFFLINE.fixtureBacked)
    }

    @Test
    fun theLegVocabularyIsTheThirteenWireStrings() {
        assertEquals(
            listOf(
                "ver02", "smoke_anthropic", "smoke_openai", "smoke_openrouter", "multi_openai",
                "multi_openrouter", "responses_probe", "demo_clarify", "demo_partial", "grammar_offline",
                "plan_live", "router_live", "undo_all",
            ),
            LegId.values().map { it.wire },
        )
    }

    @Test
    fun everyRenderedLineMatchesTheAllowPattern() {
        val lines = oneOfEach()
        assertEquals(14, lines.size)
        for (line in lines) {
            val text = line.render()
            assertTrue(text, allow.matches(text))
        }
    }

    @Test
    fun theGoldenFileIsCurrent() {
        val expected = golden()
        assertEquals(oneOfEach().map { it.render() }, expected)
        assertEquals(13, expected.map { it.substringBefore(' ') }.toSet().size)
    }
}
