package io.github.ygaray.voiceactionengine.spike.gold

import io.github.ygaray.voiceactionengine.spike.evidence.ItemKind
import io.github.ygaray.voiceactionengine.spike.evidence.Lang
import kotlinx.serialization.json.JsonObject

/**
 * One labeled gold item. A positive item names the tool the model should call and its expected arguments; a negative item
 * has no expected tool and must be declined (no mutating proposal). The [id] is opaque (never a transcript or a tool name).
 * [toString] shows the id only, so neither the transcript nor the labels can reach a log.
 */
internal class GoldItem(
    val id: String,
    val lang: Lang,
    val kind: ItemKind,
    val transcript: String,
    val expectTool: String?,
    val expectArgs: JsonObject,
    val forcedSubset: Boolean,
) {
    override fun toString(): String = "GoldItem(id=$id)"
}
