package io.github.ygaray.voiceactionengine.sample.ui

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.sample.evidence.LegId

/**
 * The stable `testTag` of every control and readout. The root sets `testTagsAsResourceId`, so uiautomator sees each tag as
 * a resource-id, and the Gate-1 runbook and tester address the screen by these exact strings.
 */
internal object UiTags {
    const val TITLE = "title"
    const val OKHTTP_VERSION = "okhttp_version"
    const val FIXTURE_STATE = "fixture_state"
    const val BUDGET_USED = "budget_used"
    const val WARM_WINDOW = "warm_window"
    const val IMPORT_TEST_KEYS = "import_test_keys"
    const val IMPORT_STATUS = "import_status"
    const val READOUT = "readout"
    const val FAILURE_BANNER = "failure_banner"
    const val CLARIFY_QUESTION = "clarify_question"

    /** The button that starts [leg]. */
    fun run(leg: LegId): String = "run_" + leg.wire

    /** The text that shows [leg]'s status word and reason. */
    fun status(leg: LegId): String = "status_" + leg.wire

    /** The text that shows the key state of [provider]. */
    fun keyState(provider: ProviderId): String = "key_state_" + provider.value

    /** The masked field where the key of [provider] is typed. */
    fun keyField(provider: ProviderId): String = "key_field_" + provider.value

    /** The button that saves the typed key of [provider]. */
    fun keySave(provider: ProviderId): String = "key_save_" + provider.value

    /** The button that deletes the stored key of [provider]. */
    fun keyDelete(provider: ProviderId): String = "key_delete_" + provider.value

    /** The button for the clarification option [optionId]. */
    fun clarifyOption(optionId: String): String = "clarify_option_" + optionId

    /** Every tag that does not depend on a model-chosen id, for [legs] and [providers]. */
    fun all(legs: List<LegId>, providers: List<ProviderId>): List<String> =
        listOf(
            TITLE,
            OKHTTP_VERSION,
            FIXTURE_STATE,
            BUDGET_USED,
            WARM_WINDOW,
            IMPORT_TEST_KEYS,
            IMPORT_STATUS,
            READOUT,
            FAILURE_BANNER,
            CLARIFY_QUESTION,
        ) + legs.flatMap { listOf(run(it), status(it)) } +
            providers.flatMap { listOf(keyState(it), keyField(it), keySave(it), keyDelete(it)) }
}
