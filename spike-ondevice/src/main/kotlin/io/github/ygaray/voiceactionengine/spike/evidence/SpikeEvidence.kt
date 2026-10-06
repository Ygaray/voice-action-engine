package io.github.ygaray.voiceactionengine.spike.evidence

private const val MAX_TOKEN_LENGTH = 96
private const val INVALID_TOKEN = "invalid_token"
private const val INVALID_KEY = "invalid_key"
private const val LINE_PREFIX = "VAE_SPIKE_"

/**
 * The full-line grammar of every spike evidence line. The host filter (`scripts/spike-evidence-filter.sh`) keeps exactly
 * the lines that match, and `EvidenceGrammarTest` proves its copy of this string is byte-identical. The value class has
 * no space, quote, bracket or newline, so no sentence (prompt, model output, tool argument) can be a value.
 */
internal const val ALLOW_PATTERN = "^VAE_SPIKE_(ENV|TOOLCHAIN|PREFLIGHT|MODEL|INIT|PREFILL|KVREUSE|SCHEMAPROBE|GPU|TRIAL|MEM|THERMAL|EXIT|STAGE)( [a-z0-9_]+=[A-Za-z0-9_.:/,-]{0,96})+$"

private val ALLOW_REGEX = Regex(ALLOW_PATTERN)
private val VALUE_TOKEN = Regex("[A-Za-z0-9_.:/,-]{0,$MAX_TOKEN_LENGTH}")
private val FIELD_KEY = Regex("[a-z0-9_]+")

/** The line kinds, by the word that follows the `VAE_SPIKE_` prefix. */
internal enum class SpikeKind {
    ENV,
    TOOLCHAIN,
    PREFLIGHT,
    MODEL,
    INIT,
    PREFILL,
    KVREUSE,
    SCHEMAPROBE,
    GPU,
    TRIAL,
    MEM,
    THERMAL,
    EXIT,
    STAGE,
}

/**
 * One line of spike evidence: a closed vocabulary by construction. The constructor is private, the kind is an enum, and
 * every value passes through [token], so anything outside the alphabet (or longer than 96 characters) renders as
 * `invalid_token` and no sentence can be rendered. [parse] returns null for any text that does not match [ALLOW_PATTERN].
 */
internal class SpikeLine private constructor(
    val kind: SpikeKind,
    val fields: List<Pair<String, String>>,
) {
    /** The first value stored under [key], or null. */
    operator fun get(key: String): String? = fields.firstOrNull { it.first == key }?.second

    /** Every value stored under [key], in line order. */
    fun all(key: String): List<String> = fields.filter { it.first == key }.map { it.second }

    /** `VAE_SPIKE_<KIND>` then ` key=value` pairs in insertion order. */
    fun render(): String = buildString {
        append(LINE_PREFIX).append(kind.name)
        for ((key, value) in fields) append(' ').append(key).append('=').append(value)
    }

    override fun toString(): String = render()

    companion object {
        /** A line of [kind] with [fields] in order; each value is sanitised to the token alphabet. */
        fun of(kind: SpikeKind, vararg fields: Pair<String, String>): SpikeLine = SpikeLine(
            kind,
            fields.map { (key, value) -> (if (FIELD_KEY.matches(key)) key else INVALID_KEY) to token(value) },
        )

        /** A value as a token: itself when it fits the alphabet and length, else `invalid_token`. */
        fun token(value: String): String = if (VALUE_TOKEN.matches(value)) value else INVALID_TOKEN

        /**
         * The line [text] encodes, or null for anything that is not a full grammar line (a comment starting with `#`,
         * free text, a line with a space in a value). A trailing carriage return or newline is ignored.
         */
        fun parse(text: String): SpikeLine? {
            val clean = text.trimEnd('\r', '\n')
            if (clean.startsWith("#") || !ALLOW_REGEX.matches(clean)) return null
            val parts = clean.split(' ')
            val kind = SpikeKind.valueOf(parts[0].removePrefix(LINE_PREFIX))
            val fields = parts.drop(1).map { part ->
                val at = part.indexOf('=')
                part.substring(0, at) to part.substring(at + 1)
            }
            return SpikeLine(kind, fields)
        }

        /** Every grammar line of [text], in order; comments and non-grammar lines are skipped. */
        fun parseAll(text: String): List<SpikeLine> = text.lineSequence().mapNotNull(::parse).toList()
    }
}
