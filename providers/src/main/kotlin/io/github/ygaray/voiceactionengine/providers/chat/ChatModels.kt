package io.github.ygaray.voiceactionengine.providers.chat

import io.github.ygaray.voiceactionengine.core.provider.CachingMode
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilities
import io.github.ygaray.voiceactionengine.providers.anthropic.AnthropicModels

private const val FAMILY_OPENAI = "openai"
private const val FAMILY_ANTHROPIC = "anthropic"
private const val FAMILY_OTHER = "other"

private const val VARIANT_SEPARATOR = ':'
private const val VENDOR_SEPARATOR = '/'

// OpenAI's open-weight models carry the openai/ prefix on a router but are hosted by third parties, not by OpenAI, so
// the OpenAI-hosted rules (strict tool schemas, automatic caching) do not apply to them.
private const val OPEN_WEIGHT_ID_PREFIX = "gpt-oss"

/**
 * Which model family an id belongs to, and the id with the vendor prefix and routing variant removed.
 *
 * @property family one of the three family names: openai, anthropic or other.
 * @property id the model id the family's own rules are keyed on.
 */
internal class ChatModelKey(val family: String, val id: String) {
    override fun toString(): String = "ChatModelKey(family=$family, id=$id)"
}

/**
 * The capability facts and wire rules for a model on a Chat Completions vendor.
 *
 * A vendor that routes ids (`vendor/model`) is resolved by normalizing the id for lookup only: a `:variant` suffix is
 * dropped, then the id is split at the first slash. `openai/<id>` takes the OpenAI rules for `<id>`, except the
 * open-weight `gpt-oss` models, which are unknown; `anthropic/<id>` takes the forced-tool fact of the Anthropic table
 * for `<id>` with every dot replaced by a dash, and never caches, because no cache markers are sent through a router;
 * any other vendor, or an id with no slash, is unknown. The id the app passed stays the wire id and the key an app
 * override is declared on; the normalized form is never sent anywhere.
 *
 * The caching mode stays keyed by provider, so a routed Anthropic model is uncached even though its own provider
 * would cache it.
 */
internal object ChatModels {
    /** The family and normalized id of [model] on [vendor]. */
    fun key(vendor: ChatVendor, model: String): ChatModelKey {
        if (!vendor.routedModelIds) return ChatModelKey(FAMILY_OPENAI, model)
        val withoutVariant = model.substringBefore(VARIANT_SEPARATOR)
        val vendorPrefix = withoutVariant.substringBefore(VENDOR_SEPARATOR, missingDelimiterValue = "")
        // Lowercased for lookup only, like the vendor prefix, because every rule pattern is lowercase.
        val id = withoutVariant.substringAfter(VENDOR_SEPARATOR).lowercase()
        val family = when (vendorPrefix.lowercase()) {
            FAMILY_OPENAI -> if (id.startsWith(OPEN_WEIGHT_ID_PREFIX)) FAMILY_OTHER else FAMILY_OPENAI
            FAMILY_ANTHROPIC -> FAMILY_ANTHROPIC
            else -> FAMILY_OTHER
        }
        return ChatModelKey(family, id)
    }

    /** The capabilities for [model] on [vendor]. */
    fun capabilities(vendor: ChatVendor, model: String): ModelCapabilities {
        val key = key(vendor, model)
        return when (key.family) {
            FAMILY_OPENAI -> openAiCapabilities(key.id, vendor.routedModelIds)
            FAMILY_ANTHROPIC -> routedAnthropicCapabilities(key.id)
            else -> ModelCapabilities.UNKNOWN
        }
    }

    /** The wire-parameter rules for [model] on [vendor]. */
    fun wireRules(vendor: ChatVendor, model: String): ChatWireRules {
        val key = key(vendor, model)
        return if (key.family == FAMILY_OPENAI) {
            OpenAiModelRules.wireRules(key.id, vendor.routedModelIds)
        } else {
            OpenAiModelRules.routedDefaultRules()
        }
    }

    /** True when [model] on [vendor] is an OpenAI model, directly or behind a router. */
    fun routesToOpenAi(vendor: ChatVendor, model: String): Boolean = key(vendor, model).family == FAMILY_OPENAI

    private fun routedAnthropicCapabilities(id: String): ModelCapabilities {
        val forcedToolChoice = AnthropicModels.capabilities(id.replace('.', '-')).supportsForcedToolChoice
        return ModelCapabilities {
            supportsForcedToolChoice = forcedToolChoice
            caching = CachingMode.NONE
        }
    }

    private fun openAiCapabilities(id: String, viaRouter: Boolean): ModelCapabilities = ModelCapabilities {
        supportsTools = OpenAiModelRules.toolsOnChat(id, viaRouter)
        caching = CachingMode.AUTOMATIC
        minCacheablePrefixTokens = OpenAiModelRules.minCacheablePrefixTokens(id)
    }
}
