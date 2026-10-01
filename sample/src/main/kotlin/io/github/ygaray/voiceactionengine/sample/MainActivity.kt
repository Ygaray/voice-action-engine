package io.github.ygaray.voiceactionengine.sample

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.MaterialTheme
import io.github.ygaray.voiceactionengine.sample.ui.SampleActions
import io.github.ygaray.voiceactionengine.sample.ui.SampleScreen

/** The sample's single screen: the Gate-1 harness the tester drives. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Samsung Freecess freezes a backgrounded or dimmed app mid-run; keep the screen on while a leg runs.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val viewModel = ViewModelProvider(this, AppGraph.get(this).viewModelFactory())[SampleViewModel::class.java]
        val actions = SampleActions(runLeg = { leg -> viewModel.runLeg(leg) })
        setContent {
            MaterialTheme {
                val state = viewModel.state.collectAsStateWithLifecycle().value
                SampleScreen(state, actions)
            }
        }
    }
}
