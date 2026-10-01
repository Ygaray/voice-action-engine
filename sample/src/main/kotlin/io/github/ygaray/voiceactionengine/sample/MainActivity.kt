package io.github.ygaray.voiceactionengine.sample

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ygaray.voiceactionengine.sample.ui.SampleActions
import io.github.ygaray.voiceactionengine.sample.ui.SampleScreen

/** The sample's single screen: the Gate-1 harness the tester drives. */
class MainActivity : ComponentActivity() {
    private lateinit var viewModel: SampleViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Samsung Freecess freezes a backgrounded or dimmed app mid-run; keep the screen on while a leg runs.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val model = ViewModelProvider(this, AppGraph.get(this).viewModelFactory())[SampleViewModel::class.java]
        viewModel = model
        val actions = SampleActions(
            runLeg = { leg -> model.runLeg(leg) },
            chooseOption = { id -> model.chooseOption(id) },
            changeKeyField = { provider, text -> model.onKeyFieldChange(provider, text) },
            saveKey = { provider -> model.saveKey(provider) },
            deleteKey = { provider -> model.deleteKey(provider) },
            importTestKeys = { model.importTestKeys() },
            tick = { model.tick() },
        )
        setContent {
            MaterialTheme {
                val state = model.state.collectAsStateWithLifecycle().value
                val keyFields = model.keyFields.collectAsStateWithLifecycle().value
                SampleScreen(state, keyFields, actions)
            }
        }
        // A recreated activity (rotation, process restore) must not rerun a leg for the intent it was first started with.
        if (savedInstanceState == null) rerunFrom(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        rerunFrom(intent)
    }

    // Debug builds only: a rerun convenience for a leg already driven once through the screen. A release build answers null.
    private fun rerunFrom(intent: Intent?) {
        val leg = DebugTools.autorunLeg(intent) ?: return
        viewModel.runLeg(leg, TRIGGER_AUTORUN)
    }
}
