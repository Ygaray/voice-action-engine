package io.github.ygaray.voiceactionengine.sample

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

/** The sample's single screen; the full harness UI arrives in a later plan. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Samsung Freecess freezes a backgrounded or dimmed app mid-run; keep the screen on while a leg runs.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme {
                Column(Modifier.semantics { testTagsAsResourceId = true }) {
                    Text("vae-sample", Modifier.testTag("title"))
                }
            }
        }
    }
}
