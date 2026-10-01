package io.github.ygaray.voiceactionengine.sample.fixture

import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import java.security.MessageDigest

/**
 * The SHA-256 of the 35,464-byte LE-1 fixture, computed by the planner from the verified file (ROADMAP prefix
 * ebd3ef4a, suffix af4ed3e). The file itself is never committed. A mismatch means "ask the orchestrator to regenerate
 * the fixture; do not use these bytes".
 */
internal const val FIXTURE_SHA256 = "ebd3ef4ab509340217e44d88d5d3ccc8718811bc0e029686391b11d79af4ed3e"

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

/** Where fixture bytes may come from. Returns null when this source holds nothing. */
internal fun interface FixtureSource {
    /** The fixture bytes, or null when this source holds none. */
    fun open(): ByteArray?
}

/** A [FixtureSource] with a short [label] that states name the source by. */
internal class NamedFixtureSource(val label: String, val source: FixtureSource)

/**
 * What loading the fixture produced. Every outcome is a specific state; no state's [toString] prints the system
 * prompt, a tool description or more than an 8-hex digest prefix.
 */
internal sealed interface FixtureState {
    /**
     * The fixture matched the expected digest and parsed.
     *
     * @property prefixChars the compact tools-array length plus the system length (the LE-1 prefix formula).
     */
    class Loaded(
        val system: String,
        val tools: List<ToolSpec>,
        val sha256: String,
        val source: String,
        val byteCount: Int,
        val prefixChars: Int,
    ) : FixtureState {
        override fun toString(): String =
            "Loaded(source=$source, tools=${tools.size}, bytes=$byteCount, prefixChars=$prefixChars, " +
                "sha256=${sha256.take(HEX_PREFIX_LENGTH)})"
    }

    /** No source held bytes; [searched] names every source that was tried, in order. */
    class Absent(val searched: List<String>) : FixtureState {
        override fun toString(): String = "Absent(searched=$searched)"
    }

    /** The first source holding bytes held the wrong ones. [actualPrefix] is the first 8 hex of what was found. */
    class ShaMismatch(val source: String, val actualPrefix: String) : FixtureState {
        override fun toString(): String = "ShaMismatch(source=$source, actualPrefix=$actualPrefix)"
    }

    /** The bytes had the right digest but not the expected shape; [code] is one of the closed codes below. */
    class Malformed(val source: String, val code: String) : FixtureState {
        override fun toString(): String = "Malformed(source=$source, code=$code)"
    }

    /** The closed set of [Malformed] codes. */
    companion object Codes {
        const val NOT_JSON = "not_json"
        const val MISSING_SYSTEM = "missing_system"
        const val MISSING_TOOLS = "missing_tools"
        const val BAD_TOOL = "bad_tool"
    }
}

/**
 * Loads the fixture from the first source that holds bytes. A digest mismatch is final: it never falls through to a
 * later source, because a wrong file silently replaced by another would void the proof. The loader never throws.
 */
internal class FixtureLoader(
    private val sources: List<NamedFixtureSource>,
    private val expectedSha256: String = FIXTURE_SHA256,
) {
    /** Reads, verifies and parses the fixture. */
    fun load(): FixtureState {
        for (named in sources) {
            val bytes = readOrNull(named) ?: continue
            val actual = sha256Hex(bytes)
            if (actual != expectedSha256) return FixtureState.ShaMismatch(named.label, actual.take(HEX_PREFIX_LENGTH))
            return parse(named.label, bytes, actual)
        }
        return FixtureState.Absent(sources.map { it.label })
    }

    // An unreadable source counts as holding nothing; Absent still names it, so the failure stays visible.
    private fun readOrNull(named: NamedFixtureSource): ByteArray? = try {
        named.source.open()
    } catch (unreadable: IOException) {
        null
    }

    private fun parse(label: String, bytes: ByteArray, sha256: String): FixtureState {
        val root = try {
            Json.parseToJsonElement(bytes.toString(Charsets.UTF_8))
        } catch (notJson: SerializationException) {
            return FixtureState.Malformed(label, FixtureState.NOT_JSON)
        }
        val obj = root as? JsonObject ?: return FixtureState.Malformed(label, FixtureState.NOT_JSON)
        val system = stringOrNull(obj[KEY_SYSTEM]) ?: return FixtureState.Malformed(label, FixtureState.MISSING_SYSTEM)
        val rawTools = obj[KEY_TOOLS] as? JsonArray
            ?: return FixtureState.Malformed(label, FixtureState.MISSING_TOOLS)
        val tools = rawTools.map { toSpec(it) ?: return FixtureState.Malformed(label, FixtureState.BAD_TOOL) }
        val prefixChars = JsonArray(rawTools).toString().length + system.length
        return FixtureState.Loaded(system, tools, sha256, label, bytes.size, prefixChars)
    }

    private fun toSpec(element: JsonElement): ToolSpec? {
        val tool = element as? JsonObject ?: return null
        val name = stringOrNull(tool[KEY_NAME])?.takeIf { it.isNotBlank() } ?: return null
        val description = stringOrNull(tool[KEY_DESCRIPTION]) ?: return null
        val schema = tool[KEY_INPUT_SCHEMA] as? JsonObject ?: return null
        return ToolSpec(name, description, schema, mutating = ToolClassifier.isMutating(name), terminal = false, strict = null)
    }

    private fun stringOrNull(element: JsonElement?): String? =
        (element as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val out = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and BYTE_MASK
            out.append(HEX_DIGITS[v shr HEX_HIGH_NIBBLE_SHIFT]).append(HEX_DIGITS[v and HEX_LOW_NIBBLE_MASK])
        }
        return out.toString()
    }
}
