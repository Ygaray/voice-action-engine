package io.github.ygaray.voiceactionengine.spike.gold

import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.spike.evidence.Envelope
import io.github.ygaray.voiceactionengine.spike.evidence.ItemKind
import io.github.ygaray.voiceactionengine.spike.evidence.Lang
import io.github.ygaray.voiceactionengine.spike.verdict.SchemaResult
import io.github.ygaray.voiceactionengine.spike.verdict.SchemaSubset
import io.github.ygaray.voiceactionengine.spike.verdict.UnknownKeyword
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

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

private val ID_TOKEN = Regex("[a-z0-9_]{1,32}")

/**
 * A validated gold file: `{version, envelope, fixture_sha256?, items: [{id, lang, kind, transcript, expect, forced_subset?}]}`.
 * [items] are distinct (unique ids and unique normalized transcripts), so N counts distinct items and a repeated item can
 * never inflate it (THRESHOLDS (c)). [toString] shows counts only.
 */
internal class GoldSet(val envelope: Envelope, val fixtureSha256: String?, val items: List<GoldItem>) {
    /** The forced-shape subset: positive items only, informational. */
    val forcedSubset: List<GoldItem> get() = items.filter { it.forcedSubset }

    override fun toString(): String = "GoldSet(envelope=${envelope.wire}, items=${items.size})"

    companion object {
        /**
         * Parses and validates [json] against the envelope's [tools]. Every rule failure is a [GoldFormatException] whose
         * message is a stable code (`not_json`, `bad_envelope`, `bad_items`, `bad_id`, `dup_id`, `bad_lang`, `bad_kind`,
         * `bad_transcript`, `dup_transcript`, `bad_expect`, `missing_expect`, `unknown_tool`, `unknown_arg`,
         * `unknown_keyword:<keyword>`, `args_invalid`, `bad_forced_subset`, `below_minimum:<bucket>`), never an item's text.
         */
        fun load(json: String, tools: List<ToolSpec>, minima: Minima = Minima.DEFAULT): GoldSet {
            val root = try {
                Json.parseToJsonElement(json) as? JsonObject
            } catch (@Suppress("SwallowedException") e: SerializationException) {
                // The malformed text is dropped on purpose: a gold file's content must never reach an exception.
                null
            } ?: throw GoldFormatException("not_json")
            val envelope = Envelope.fromWire(text(root["envelope"])) ?: throw GoldFormatException("bad_envelope")
            val rawItems = root["items"] as? JsonArray ?: throw GoldFormatException("bad_items")
            val ids = HashSet<String>()
            val transcripts = HashSet<String>()
            val items = rawItems.map { parseItem(it, tools, ids, transcripts) }
            checkForcedSubset(items, minima)
            checkMinima(items, minima)
            return GoldSet(envelope, text(root["fixture_sha256"]), items)
        }

        private fun text(element: JsonElement?): String? = (element as? JsonPrimitive)?.takeIf { it.isString }?.content

        private fun parseItem(
            element: JsonElement,
            tools: List<ToolSpec>,
            ids: MutableSet<String>,
            transcripts: MutableSet<String>,
        ): GoldItem {
            val obj = element as? JsonObject ?: throw GoldFormatException("bad_items")
            val id = text(obj["id"])?.takeIf { ID_TOKEN.matches(it) } ?: throw GoldFormatException("bad_id")
            if (!ids.add(id)) throw GoldFormatException("dup_id")
            val lang = Lang.fromWire(text(obj["lang"]))?.takeIf { it == Lang.EN || it == Lang.ES }
                ?: throw GoldFormatException("bad_lang")
            val kind = ItemKind.fromWire(text(obj["kind"])) ?: throw GoldFormatException("bad_kind")
            val transcript = text(obj["transcript"])?.takeIf { it.isNotBlank() } ?: throw GoldFormatException("bad_transcript")
            if (!transcripts.add(GoldMatcher.normalize(transcript))) throw GoldFormatException("dup_transcript")
            val expect = obj["expect"] as? JsonObject ?: throw GoldFormatException("bad_expect")
            val forced = forcedFlag(obj["forced_subset"])
            if (forced && kind != ItemKind.POS) throw GoldFormatException("bad_forced_subset")
            return when (kind) {
                ItemKind.POS -> positive(id, lang, transcript, expect, forced, tools)
                ItemKind.NEG -> {
                    if (expect["tool"] != null) throw GoldFormatException("bad_expect")
                    GoldItem(id, lang, kind, transcript, null, JsonObject(emptyMap()), false)
                }
            }
        }

        private fun forcedFlag(element: JsonElement?): Boolean = when (element) {
            null -> false
            is JsonPrimitive -> element.booleanOrNull ?: throw GoldFormatException("bad_forced_subset")
            else -> throw GoldFormatException("bad_forced_subset")
        }

        private fun positive(
            id: String,
            lang: Lang,
            transcript: String,
            expect: JsonObject,
            forced: Boolean,
            tools: List<ToolSpec>,
        ): GoldItem {
            val name = (expect["tool"] as? JsonPrimitive)?.contentOrNull ?: throw GoldFormatException("missing_expect")
            val tool = tools.firstOrNull { it.name == name } ?: throw GoldFormatException("unknown_tool")
            val args = when (val raw = expect["args"]) {
                null -> JsonObject(emptyMap())
                is JsonObject -> raw
                else -> throw GoldFormatException("bad_expect")
            }
            val properties = tool.inputSchema["properties"] as? JsonObject ?: JsonObject(emptyMap())
            if (args.keys.any { it !in properties }) throw GoldFormatException("unknown_arg")
            when (val result = SchemaSubset.validate(tool.inputSchema, args)) {
                is UnknownKeyword -> throw GoldFormatException("unknown_keyword:${result.name}")
                is SchemaResult.Invalid -> throw GoldFormatException("args_invalid")
                else -> Unit
            }
            return GoldItem(id, lang, ItemKind.POS, transcript, name, args, forced)
        }

        private fun checkForcedSubset(items: List<GoldItem>, minima: Minima) {
            val forced = items.count { it.forcedSubset }
            if (minima.forced in 1 until forced) throw GoldFormatException("bad_forced_subset")
        }

        private fun checkMinima(items: List<GoldItem>, minima: Minima) {
            fun below(bucket: String, have: Int, need: Int) {
                if (have < need) throw GoldFormatException("below_minimum:$bucket")
            }
            below("pos_en", items.count { it.kind == ItemKind.POS && it.lang == Lang.EN }, minima.posEn)
            below("pos_es", items.count { it.kind == ItemKind.POS && it.lang == Lang.ES }, minima.posEs)
            below("neg", items.count { it.kind == ItemKind.NEG }, minima.neg)
            below("forced", items.count { it.forcedSubset }, minima.forced)
        }
    }
}
