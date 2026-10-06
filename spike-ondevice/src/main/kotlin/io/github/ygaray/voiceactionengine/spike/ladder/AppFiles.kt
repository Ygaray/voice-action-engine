package io.github.ygaray.voiceactionengine.spike.ladder

import io.github.ygaray.voiceactionengine.spike.evidence.ModelKey
import java.io.File

/** The model files the runner places (`scripts/run-spike-ondevice.sh` pins the same names). */
internal enum class ModelFile(val fileName: String, val model: ModelKey, val gpuVariant: Boolean) {
    E2B_GENERIC("gemma-4-E2B-it.litertlm", ModelKey.E2B, false),
    E2B_GPU("gemma-4-E2B-it-gpu.litertlm", ModelKey.E2B, true),
    G3_1B("Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm", ModelKey.G3_1B, false),
}

/**
 * Everything the app reads or writes, all under its own directories: `files/evidence`, `files/private`, `files/state`,
 * `files/engine-cache`, and the models directory (the external files directory first, `files/models` after it, which is
 * where the runner's fallback push lands). The activity never touches a path outside these.
 */
internal class AppFiles(val files: File, private val externalModels: File?) {
    val evidence: File = File(files, "evidence")
    val privateDir: File = File(files, "private")
    val state: File = File(files, "state")
    val engineCache: File = File(files, "engine-cache")
    private val internalModels: File = File(files, "models")

    /** Creates the directories of a run. */
    fun ensure() {
        listOf(evidence, privateDir, state, engineCache, internalModels).forEach { it.mkdirs() }
        externalModels?.mkdirs()
    }

    /** The absolute path of [file] when it is present, else null. */
    fun modelPath(file: ModelFile): String? =
        listOfNotNull(externalModels, internalModels)
            .map { File(it, file.fileName) }
            .firstOrNull { it.isFile }
            ?.absolutePath

    /** The size in bytes of [file], or 0 when it is absent. */
    fun modelSize(file: ModelFile): Long = modelPath(file)?.let { File(it).length() } ?: 0L

    override fun toString(): String = "AppFiles"
}
