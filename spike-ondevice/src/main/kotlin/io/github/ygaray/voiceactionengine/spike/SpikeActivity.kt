package io.github.ygaray.voiceactionengine.spike

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import io.github.ygaray.voiceactionengine.spike.backend.LiteRtBackend
import io.github.ygaray.voiceactionengine.spike.envelope.SmallEnvelope
import io.github.ygaray.voiceactionengine.spike.evidence.Stage
import io.github.ygaray.voiceactionengine.spike.evidence.androidEvidenceSink
import io.github.ygaray.voiceactionengine.spike.gold.GoldSet
import io.github.ygaray.voiceactionengine.spike.ladder.AndroidProbes
import io.github.ygaray.voiceactionengine.spike.ladder.AppFiles
import io.github.ygaray.voiceactionengine.spike.ladder.Ladder
import io.github.ygaray.voiceactionengine.spike.ladder.LadderEnv
import io.github.ygaray.voiceactionengine.spike.ladder.StateStore
import io.github.ygaray.voiceactionengine.spike.ladder.loadSbState
import io.github.ygaray.voiceactionengine.spike.trial.PrivateRawSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

private const val EXTRA_STAGE = "stage"
private const val SMALL_GOLD_ASSET = "gold/small-gold.json"
private const val MODELS_DIR = "models"
private const val RAW_DIR = "raw"

/**
 * The stage host: `am start -n <pkg>/.SpikeActivity --es stage <wire>` runs one ladder stage in a fresh process, with the
 * screen kept on so the app stays foreground and unfrozen. It reads exactly one extra, `stage`, and accepts only a name from
 * the closed [Stage] list: any other value emits nothing and finishes. It touches only its own directories, and it has no
 * network permission. It is exported because the shell's `am start` cannot reach a non-exported activity (threat T-13-27);
 * the app is a debug build on the TESTER only and the runner's cleanup uninstalls it.
 */
class SpikeActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val stage = Stage.fromWire(intent?.getStringExtra(EXTRA_STAGE))
        if (stage == null) {
            finish()
            return
        }
        setContentView(TextView(this).apply { text = "spike stage ${stage.wire}" })
        scope.launch {
            try {
                ladder().run(stage)
            } finally {
                runOnUiThread { finish() }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun ladder(): Ladder {
        val files = AppFiles(filesDir, getExternalFilesDir(MODELS_DIR))
        files.ensure()
        return Ladder(
            LadderEnv(
                sink = androidEvidenceSink(files.evidence),
                state = StateStore(files.state),
                probes = AndroidProbes(applicationContext, files.files),
                backend = { LiteRtBackend() },
                files = files,
                smallGold = { GoldSet.load(assets.open(SMALL_GOLD_ASSET).use { it.readBytes().toString(Charsets.UTF_8) }, SmallEnvelope.tools) },
                sbState = { loadSbState(files) },
                dispatcher = Dispatchers.IO,
                rawSink = PrivateRawSink(File(files.privateDir, RAW_DIR)),
            ),
        )
    }
}
