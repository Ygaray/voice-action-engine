package io.github.ygaray.voiceactionengine.spike.evidence

import android.util.Log
import java.io.File
import java.io.FileOutputStream

private const val LOG_TAG = "VaeSpike"
private const val EVIDENCE_SUFFIX = ".txt"

/**
 * Where evidence leaves the harness. The only input is a [SpikeLine], whose values are a closed token alphabet by
 * construction, so no prompt, model output, tool argument or SB tool name can be handed to a sink: there is no string
 * overload on purpose.
 */
internal interface EvidenceSink {
    /** Appends [line] to the evidence of [stage], durably per line. */
    fun emit(stage: Stage, line: SpikeLine)

    /** Every grammar line already written for [stage] (the ladder reads earlier stages' rows back; comments are skipped). */
    fun lines(stage: Stage): List<SpikeLine>
}

/**
 * The sink: an append-only `<dir>/<stage wire>.txt`, one unbuffered write per line (so a process death keeps every
 * finished line), and the same rendered text handed to [echo]. [echo] exists so the file behaviour is testable without the
 * Android framework; the app passes [androidEvidenceSink]'s logcat echo. [toString] shows nothing but the class.
 */
internal class FileEvidenceSink(private val dir: File, private val echo: (String) -> Unit) : EvidenceSink, AutoCloseable {
    private val streams = HashMap<Stage, FileOutputStream>()

    @Synchronized
    override fun emit(stage: Stage, line: SpikeLine) {
        val text = line.render()
        val stream = streams.getOrPut(stage) {
            dir.mkdirs()
            FileOutputStream(fileOf(stage), true)
        }
        stream.write((text + "\n").toByteArray(Charsets.UTF_8))
        stream.flush()
        echo(text)
    }

    @Synchronized
    override fun lines(stage: Stage): List<SpikeLine> {
        val file = fileOf(stage)
        return if (file.isFile) SpikeLine.parseAll(file.readText(Charsets.UTF_8)) else emptyList()
    }

    @Synchronized
    override fun close() {
        streams.values.forEach { it.close() }
        streams.clear()
    }

    private fun fileOf(stage: Stage): File = File(dir, stage.wire + EVIDENCE_SUFFIX)

    override fun toString(): String = "FileEvidenceSink"
}

/** The app's sink: files under [dir] (`files/evidence`) and logcat under the tag `VaeSpike`. The only `android.util.Log` use. */
internal fun androidEvidenceSink(dir: File): EvidenceSink = FileEvidenceSink(dir) { Log.i(LOG_TAG, it) }
