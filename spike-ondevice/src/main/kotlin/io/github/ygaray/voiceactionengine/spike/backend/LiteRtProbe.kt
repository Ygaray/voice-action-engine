@file:OptIn(com.google.ai.edge.litertlm.ExperimentalApi::class)

package io.github.ygaray.voiceactionengine.spike.backend

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ResponseFormat
import com.google.ai.edge.litertlm.SamplerConfig

/**
 * Compile probe only: it forces the Kotlin 2.3.20 compiler to read the AAR's Kotlin 2.4.0 metadata and D8 to dex its
 * Java-21 classes. Plan 13-04 replaces it with the real backend.
 */
internal object LiteRtProbe {
    fun typeNames(): List<String> = listOf(
        Engine::class.simpleName.orEmpty(),
        EngineConfig::class.simpleName.orEmpty(),
        ConversationConfig::class.simpleName.orEmpty(),
        ResponseFormat::class.simpleName.orEmpty(),
        SamplerConfig::class.simpleName.orEmpty(),
        Backend::class.simpleName.orEmpty(),
    )
}
