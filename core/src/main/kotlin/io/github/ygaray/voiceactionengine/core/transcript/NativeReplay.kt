package io.github.ygaray.voiceactionengine.core.transcript

import io.github.ygaray.voiceactionengine.core.ProviderId
import kotlinx.serialization.json.JsonElement

/**
 * A provider's own assistant turn, kept verbatim so it can be sent back unchanged.
 *
 * The raw turn may hold thinking blocks whose signatures a provider checks on replay, and rebuilding it would also
 * break the prompt cache. So it is stored by reference, never rebuilt, never mutated and never printed. A mapper sends
 * it back only to the same provider and model that produced it (see [AssistantMessage.nativeFor]).
 *
 * @property provider the provider that produced the turn.
 * @property model the model id that produced the turn.
 * @property raw the provider's assistant turn exactly as received.
 * @throws IllegalArgumentException when [model] is blank.
 */
public class NativeReplay(
    public val provider: ProviderId,
    public val model: String,
    public val raw: JsonElement,
) {
    init {
        require(model.isNotBlank()) { "a native replay model must not be blank" }
    }

    /** Prints the provider and model only, never the raw turn. */
    override fun toString(): String = "NativeReplay(provider=$provider, model=$model)"
}
