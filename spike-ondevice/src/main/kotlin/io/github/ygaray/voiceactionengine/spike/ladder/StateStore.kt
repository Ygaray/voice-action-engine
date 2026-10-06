package io.github.ygaray.voiceactionengine.spike.ladder

import io.github.ygaray.voiceactionengine.spike.evidence.BackendKind
import io.github.ygaray.voiceactionengine.spike.evidence.Envelope
import io.github.ygaray.voiceactionengine.spike.evidence.ModelKey
import io.github.ygaray.voiceactionengine.spike.evidence.SpikeLine
import java.io.File

private val STATE_KEY = Regex("[a-z0-9_]+")

/**
 * The ladder's memory between process starts (every stage is its own process): small `key=value` files under `files/state/`.
 * Keys are `[a-z0-9_]+`; a value is a token of the evidence alphabet (anything else is stored as `invalid_token`), so
 * the store can hold no prompt, output or label text. A read of an absent key is null. [toString] shows nothing but the class.
 */
internal class StateStore(private val dir: File) {
    /** Stores [value] under [key], replacing any earlier value (written to a temp file, then renamed). */
    fun put(key: String, value: String) {
        require(STATE_KEY.matches(key)) { "a state key is [a-z0-9_]+" }
        dir.mkdirs()
        val temp = File(dir, "$key.tmp")
        temp.writeText("$key=${SpikeLine.token(value)}\n", Charsets.UTF_8)
        if (!temp.renameTo(File(dir, key))) {
            File(dir, key).writeText(temp.readText(Charsets.UTF_8), Charsets.UTF_8)
            temp.delete()
        }
    }

    /** The value stored under [key], or null when there is none. */
    fun get(key: String): String? {
        if (!STATE_KEY.matches(key)) return null
        val file = File(dir, key)
        if (!file.isFile) return null
        return file.readText(Charsets.UTF_8).trim().removePrefix("$key=").takeIf { it.isNotEmpty() }
    }

    /** Forgets everything (a new prepare starts a new run). */
    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    override fun toString(): String = "StateStore"

    companion object {
        const val PREPARE_EPOCH_MS = "prepare_epoch_ms"

        /** The key of the stored winning cell of [envelope]. */
        fun winnerKey(envelope: Envelope): String = "winner_${envelope.wire}"

        /** The key of the stored init outcome (`ok` or a stable failure code) of [model] on [backend]. */
        fun initKey(model: ModelKey, backend: BackendKind): String = "init_${model.wire}_${backend.wire}"
    }
}
