package io.github.ygaray.voiceactionengine.providers.chat

import io.github.ygaray.voiceactionengine.core.provider.CachingMode
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilities

/**
 * The capability facts for models on a Chat Completions vendor. A vendor whose ids are not routed is answered from
 * [OpenAiModelRules]; a routed vendor is not resolved yet and answers [ModelCapabilities.UNKNOWN].
 */
internal object ChatModels {
    /** The capabilities for [model] on [vendor]. */
    fun capabilities(vendor: ChatVendor, model: String): ModelCapabilities =
        if (vendor.routedModelIds) {
            ModelCapabilities.UNKNOWN
        } else {
            ModelCapabilities {
                supportsTools = OpenAiModelRules.toolsOnChat(model, viaRouter = false)
                caching = CachingMode.AUTOMATIC
                minCacheablePrefixTokens = OpenAiModelRules.minCacheablePrefixTokens(model)
            }
        }
}
