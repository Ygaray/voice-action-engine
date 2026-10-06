package io.github.ygaray.voiceactionengine.spike.envelope

import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.spike.evidence.Envelope
import io.github.ygaray.voiceactionengine.spike.gold.GoldFormatException
import io.github.ygaray.voiceactionengine.spike.gold.GoldSet
import io.github.ygaray.voiceactionengine.spike.gold.Minima
import io.github.ygaray.voiceactionengine.spike.verdict.SchemaSubset
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.io.IOException
import java.security.MessageDigest

private const val HEX_PREFIX_LENGTH = 8
private const val HEX_HIGH_NIBBLE_SHIFT = 4
private const val HEX_LOW_NIBBLE_MASK = 0x0f
private const val BYTE_MASK = 0xff
private const val HEX_DIGITS = "0123456789abcdef"

private const val KEY_SYSTEM = "system"
private const val KEY_TOOLS = "tools"
private const val KEY_NAME = "name"
private const val KEY_DESCRIPTION = "description"
private const val KEY_INPUT_SCHEMA = "input_schema"
private const val KEY_TOOL_META = "tool_meta"
private const val KEY_FIXTURE_SHA = "fixture_sha256"
private const val META_MUTATING = "mutating"
private const val META_TERMINAL = "terminal"
private const val META_READ = "read"

/** What loading the SB-sized envelope produced. No state's `toString` shows a tool name, a label or a digest past 8 hex. */
internal sealed interface SbState {
    /** The fixture and its labels matched and parsed. */
    class Loaded(
        val envelope: EnvelopeSnapshot,
        val gold: GoldSet,
        val fixtureSha: String,
        val goldSha: String,
    ) : SbState {
        /** The count of tools in the envelope, a reported dimension of the results (never a fixed assumption). */
        val toolCount: Int get() = envelope.tools.size

        override fun toString(): String =
            "Loaded(tools=$toolCount, items=${gold.items.size}, fixture=${fixtureSha.take(HEX_PREFIX_LENGTH)}, " +
                "gold=${goldSha.take(HEX_PREFIX_LENGTH)})"
    }

    /** The private fixture or labels are not in app-private storage; the ladder ends every sb stage `sb_fixture_absent`. */
    data object Absent : SbState

    /** The files are there but unusable; [code] is a stable code (never file text). The sb envelope ends red. */
    class Invalid(val code: String) : SbState {
        override fun toString(): String = "Invalid(code=$code)"
    }
}

/**
 * The SB-sized envelope (D-05), parsed at run time from private files that are never in the repository or the APK:
 * `files/private/sb-fixture.json` (`{system, tools: [{name, description, input_schema}]}`, SB's own surface) and
 * `files/private/sb-gold.json` (the labels, digest-pinned to that fixture by `fixture_sha256`, with a `tool_meta` map of
 * tool name to `mutating`, `terminal` or `read`, because the fixture does not carry it). The runner (13-06) pushes both into
 * the app's private storage; nothing here reads assets.
 *
 * Loading fails loudly rather than substitute anything: an unsupported schema keyword is `unknown_keyword:<keyword>`, a
 * label file written for another fixture is `fixture_mismatch`, and an absent fixture is [SbState.Absent], never a silent
 * green. Evidence about this envelope uses opaque item ids only.
 */
internal object SbEnvelope {
    /** The app-private subdirectory (under `filesDir`, that is `files/private`) the private files are pushed into. */
    const val PRIVATE_DIR = "private"
    const val FIXTURE_FILE = "sb-fixture.json"
    const val GOLD_FILE = "sb-gold.json"

    /** Loads from a private directory (`context.filesDir/private`); either file missing is [SbState.Absent]. */
    fun fromPrivateDir(dir: File, minima: Minima = Minima.DEFAULT): SbState {
        val fixture = readOrNull(File(dir, FIXTURE_FILE)) ?: return SbState.Absent
        val gold = readOrNull(File(dir, GOLD_FILE)) ?: return SbState.Absent
        return load(fixture, gold, minima)
    }

    /** Loads from bytes. Never throws: every problem is an [SbState.Invalid] with a stable code. */
    fun load(fixtureBytes: ByteArray, goldBytes: ByteArray, minima: Minima = Minima.DEFAULT): SbState =
        try {
            parse(fixtureBytes, goldBytes, minima)
        } catch (e: GoldFormatException) {
            SbState.Invalid(e.code)
        }

    private fun parse(fixtureBytes: ByteArray, goldBytes: ByteArray, minima: Minima): SbState {
        val fixture = parseObject(fixtureBytes) ?: return SbState.Invalid("fixture_not_json")
        val goldRoot = parseObject(goldBytes) ?: return SbState.Invalid("gold_not_json")
        val fixtureSha = sha256Hex(fixtureBytes)
        val pinned = (goldRoot[KEY_FIXTURE_SHA] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (pinned == null || !pinned.equals(fixtureSha, ignoreCase = true)) return SbState.Invalid("fixture_mismatch")
        val system = (fixture[KEY_SYSTEM] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: return SbState.Invalid("fixture_missing_system")
        val rawTools = fixture[KEY_TOOLS] as? JsonArray ?: return SbState.Invalid("fixture_missing_tools")
        val meta = goldRoot[KEY_TOOL_META] as? JsonObject ?: return SbState.Invalid("tool_meta_missing")
        val tools = rawTools.map { toSpec(it, meta) ?: return SbState.Invalid("fixture_bad_tool") }
        tools.forEach { tool ->
            SchemaSubset.keywordsIn(tool.inputSchema).unknown.firstOrNull()?.let {
                return SbState.Invalid("unknown_keyword:${it.name}")
            }
        }
        val gold = GoldSet.load(goldBytes.toString(Charsets.UTF_8), tools, minima)
        if (gold.envelope != Envelope.SB) return SbState.Invalid("bad_envelope")
        return SbState.Loaded(EnvelopeSnapshot(Envelope.SB, system, tools), gold, fixtureSha, sha256Hex(goldBytes))
    }

    private fun toSpec(element: JsonElement, meta: JsonObject): ToolSpec? {
        val tool = element as? JsonObject ?: return null
        val name = (tool[KEY_NAME] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() } ?: return null
        val description = (tool[KEY_DESCRIPTION] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        val schema = tool[KEY_INPUT_SCHEMA] as? JsonObject ?: return null
        val kind = (meta[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (kind != META_MUTATING && kind != META_TERMINAL && kind != META_READ) return null
        return ToolSpec(name, description, schema, mutating = kind == META_MUTATING, terminal = kind == META_TERMINAL, strict = null)
    }

    private fun parseObject(bytes: ByteArray): JsonObject? =
        try {
            Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)) as? JsonObject
        } catch (@Suppress("SwallowedException") e: SerializationException) {
            // The malformed text is dropped on purpose: private content must never reach a code or a log.
            null
        }

    // An unreadable file counts as absent: the ladder reports sb_fixture_absent and nothing is substituted.
    private fun readOrNull(file: File): ByteArray? =
        try {
            if (file.isFile) file.readBytes() else null
        } catch (@Suppress("SwallowedException") e: IOException) {
            null
        }

    internal fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val out = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and BYTE_MASK
            out.append(HEX_DIGITS[v shr HEX_HIGH_NIBBLE_SHIFT]).append(HEX_DIGITS[v and HEX_LOW_NIBBLE_MASK])
        }
        return out.toString()
    }
}
