package io.github.ygaray.voiceactionengine.sample

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.sample.evidence.LegId
import io.github.ygaray.voiceactionengine.sample.ui.UiTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private val PROVIDERS = listOf(ProviderId.ANTHROPIC, ProviderId.OPENAI, ProviderId.OPENROUTER)
private val TAG_SHAPE = Regex("^[a-z0-9_]+$")

/** The tag vocabulary the runbook and the tester address the screen by. */
class UiTagsTest {

    @Test
    fun everyLegHasRunAndStatusTagsAndTagsAreUnique() {
        for (leg in LegId.entries) {
            assertEquals("run_" + leg.wire, UiTags.run(leg))
            assertEquals("status_" + leg.wire, UiTags.status(leg))
        }
        for (provider in PROVIDERS) {
            assertEquals("key_state_" + provider.value, UiTags.keyState(provider))
            assertEquals("key_field_" + provider.value, UiTags.keyField(provider))
            assertEquals("key_save_" + provider.value, UiTags.keySave(provider))
            assertEquals("key_delete_" + provider.value, UiTags.keyDelete(provider))
        }
        val all = UiTags.all(LegId.entries, PROVIDERS)
        assertEquals(all.toString(), all.size, all.toSet().size)
        for (tag in all) assertTrue(tag, TAG_SHAPE.matches(tag))
        val expectedFixed = listOf(
            "import_test_keys",
            "import_status",
            "fixture_state",
            "okhttp_version",
            "budget_used",
            "warm_window",
            "readout",
            "failure_banner",
            "clarify_question",
            "undo_all",
            "undo_label",
        )
        assertTrue(all.toString(), all.containsAll(expectedFixed))
        assertEquals("clarify_option_list-a", UiTags.clarifyOption("list-a"))
    }
}
