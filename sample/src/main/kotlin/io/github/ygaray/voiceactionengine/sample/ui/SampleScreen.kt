package io.github.ygaray.voiceactionengine.sample.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.sample.KeyView
import io.github.ygaray.voiceactionengine.sample.UiState
import io.github.ygaray.voiceactionengine.sample.evidence.LegId
import kotlinx.coroutines.delay

private val SECTION_GAP = 16.dp
private val CONTROL_GAP = 8.dp

private const val TICK_MILLIS = 1000L

private val GOOD_COLOR = Color(0xFF2E7D32)
private val BAD_COLOR = Color(0xFFC62828)
private val WARN_COLOR = Color(0xFFB26A00)

/** What the screen can ask for. The view model fills these in. */
internal class SampleActions(
    val runLeg: (LegId) -> Unit,
    val chooseOption: (String) -> Unit,
    val changeKeyField: (ProviderId, String) -> Unit,
    val saveKey: (ProviderId) -> Unit,
    val deleteKey: (ProviderId) -> Unit,
    val importTestKeys: () -> Unit,
    val tick: () -> Unit,
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
 * [UiTags] tag, and the root exposes the tags as resource-ids for uiautomator. [keyFields] is the text typed so far into
 * each key field.
 */
@Composable
internal fun SampleScreen(state: UiState, keyFields: Map<ProviderId, String>, actions: SampleActions) {
    // While the warm window is open the countdown is refreshed every second; the effect ends when the window closes.
    if (state.warmWindow != null) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(TICK_MILLIS)
                actions.tick()
            }
        }
    }
    Column(
        Modifier
            .semantics { testTagsAsResourceId = true }
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SECTION_GAP),
        verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
    ) {
        Header(state)
        Keys(state, keyFields, actions)
        Legs(state, actions)
        Readout(state, actions)
    }
}

@Composable
private fun Header(state: UiState) {
    Column(verticalArrangement = Arrangement.spacedBy(CONTROL_GAP)) {
        Text("vae-sample", Modifier.testTag(UiTags.TITLE), style = MaterialTheme.typography.titleLarge)
        Text(
            state.fixture.text,
            Modifier.testTag(UiTags.FIXTURE_STATE),
            color = toneColor(state.fixture.tone),
            style = MaterialTheme.typography.titleSmall,
        )
        Text("okhttp ${state.okhttp}", Modifier.testTag(UiTags.OKHTTP_VERSION))
        Text(state.budgetText, Modifier.testTag(UiTags.BUDGET_USED))
        val warm = state.warmWindow
        if (warm != null) Text(warm, Modifier.testTag(UiTags.WARM_WINDOW), color = WARN_COLOR)
    }
}

@Composable
private fun Keys(state: UiState, keyFields: Map<ProviderId, String>, actions: SampleActions) {
    Column(verticalArrangement = Arrangement.spacedBy(CONTROL_GAP)) {
        Text("Keys", style = MaterialTheme.typography.titleMedium)
        for (key in state.keys) KeyRow(key, keyFields[key.provider].orEmpty(), actions)
        if (state.importAvailable) {
            Button(onClick = actions.importTestKeys, modifier = Modifier.testTag(UiTags.IMPORT_TEST_KEYS)) {
                Text("Import test keys")
            }
            val status = state.importStatus
            if (status != null) {
                Text(status.text, Modifier.testTag(UiTags.IMPORT_STATUS), color = toneColor(status.tone))
            }
        }
    }
}

@Composable
private fun KeyRow(key: KeyView, typed: String, actions: SampleActions) {
    val provider = key.provider
    Column(verticalArrangement = Arrangement.spacedBy(CONTROL_GAP)) {
        Text("${provider.value}: ${key.text}", Modifier.testTag(UiTags.keyState(provider)), color = toneColor(key.tone))
        OutlinedTextField(
            value = typed,
            onValueChange = { actions.changeKeyField(provider, it) },
            modifier = Modifier.fillMaxWidth().testTag(UiTags.keyField(provider)),
            singleLine = true,
            label = { Text("${provider.value} key") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(CONTROL_GAP)) {
            Button(onClick = { actions.saveKey(provider) }, modifier = Modifier.testTag(UiTags.keySave(provider))) {
                Text("Save")
            }
            Button(onClick = { actions.deleteKey(provider) }, modifier = Modifier.testTag(UiTags.keyDelete(provider))) {
                Text("Delete")
            }
        }
    }
}

@Composable
private fun Legs(state: UiState, actions: SampleActions) {
    Column(verticalArrangement = Arrangement.spacedBy(CONTROL_GAP)) {
        Text("Legs", style = MaterialTheme.typography.titleMedium)
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
