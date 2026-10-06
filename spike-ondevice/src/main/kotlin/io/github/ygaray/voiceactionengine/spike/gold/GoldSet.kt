package io.github.ygaray.voiceactionengine.spike.gold

import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.spike.evidence.Envelope
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

/** A gold file that breaks a rule. The message is a stable code only (never an item's text), so it can be logged. */
internal class GoldFormatException(val code: String) : IllegalArgumentException(code) {
    override fun toString(): String = "GoldFormatException(code=$code)"
}

/** The least number of distinct items an envelope's gold file must carry (THRESHOLDS minima), and the forced-subset size. */
internal data class Minima(val posEn: Int, val posEs: Int, val neg: Int, val forced: Int) {
    companion object {
        val DEFAULT = Minima(posEn = 50, posEs = 50, neg = 30, forced = 20)
    }
}

/** A validated gold file. */
internal class GoldSet(val envelope: Envelope, val fixtureSha256: String?, val items: List<GoldItem>) {
    companion object {
        /** Parses and validates [json] against [tools]; throws [GoldFormatException] with a stable code. */
        fun load(json: String, tools: List<ToolSpec>, minima: Minima = Minima.DEFAULT): GoldSet =
            throw NotImplementedError("GoldSet.load is implemented in the GREEN commit")
    }
}
