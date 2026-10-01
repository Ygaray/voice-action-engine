package io.github.ygaray.voiceactionengine.sample.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import io.github.ygaray.voiceactionengine.sample.UiState
import io.github.ygaray.voiceactionengine.sample.evidence.LegId

private val SECTION_GAP = 16.dp
private val CONTROL_GAP = 8.dp

private val GOOD_COLOR = Color(0xFF2E7D32)
private val BAD_COLOR = Color(0xFFC62828)
private val WARN_COLOR = Color(0xFFB26A00)

/** What the screen can ask for. The view model fills these in. */
internal class SampleActions(
    val runLeg: (LegId) -> Unit,
    val chooseOption: (String) -> Unit,
) {
    override fun toString(): String = "SampleActions"
}

@Composable
private fun toneColor(tone: Tone): Color = when (tone) {
    Tone.GOOD -> GOOD_COLOR
    Tone.BAD -> BAD_COLOR
    Tone.WARN -> WARN_COLOR
    Tone.NEUTRAL -> MaterialTheme.colorScheme.onSurface
}

/**
 * The sample's single screen: header, keys, legs and readout, top to bottom. Every control and readout carries its
 * [UiTags] tag, and the root exposes the tags as resource-ids for uiautomator.
 */
@Composable
internal fun SampleScreen(state: UiState, actions: SampleActions) {
    Column(
        Modifier
            .semantics { testTagsAsResourceId = true }
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SECTION_GAP),
        verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
    ) {
        Header(state)
        Legs(state, actions)
        Readout(state, actions)
    }
}

@Composable
private fun Header(state: UiState) {
    Column(verticalArrangement = Arrangement.spacedBy(CONTROL_GAP)) {
        Text("vae-sample", Modifier.testTag(UiTags.TITLE), style = MaterialTheme.typography.titleLarge)
        Text("okhttp ${state.okhttp}", Modifier.testTag(UiTags.OKHTTP_VERSION))
    }
}

@Composable
private fun Legs(state: UiState, actions: SampleActions) {
    Column(verticalArrangement = Arrangement.spacedBy(CONTROL_GAP)) {
        for (row in state.legs) {
            Button(
                onClick = { actions.runLeg(row.leg) },
                enabled = state.runEnabled,
                modifier = Modifier.testTag(UiTags.run(row.leg)),
            ) {
                Text(row.leg.wire)
            }
            Text(row.text, Modifier.testTag(UiTags.status(row.leg)), color = toneColor(row.tone))
        }
    }
}

@Composable
private fun Readout(state: UiState, actions: SampleActions) {
    val view = state.readout ?: return
    Column(verticalArrangement = Arrangement.spacedBy(CONTROL_GAP)) {
        Text(view.headline, Modifier.testTag(UiTags.READOUT), color = toneColor(view.tone))
        val banner = view.banner
        if (banner != null) {
            Text(
                banner,
                Modifier.testTag(UiTags.FAILURE_BANNER),
                color = BAD_COLOR,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        val question = view.question
        if (question != null) Text(question, Modifier.testTag(UiTags.CLARIFY_QUESTION))
        for (option in view.options) {
            Button(
                onClick = { actions.chooseOption(option.id) },
                enabled = state.runEnabled,
                modifier = Modifier.testTag(UiTags.clarifyOption(option.id)),
            ) {
                Text(option.label)
            }
        }
    }
}
