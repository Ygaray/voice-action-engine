package io.github.ygaray.voiceactionengine.spike.evidence

/** The two measurement envelopes: the 4-tool synthetic one read by CT, and the SB-sized one read by SB. */
internal enum class Envelope(val wire: String) {
    SMALL("small"),
    SB("sb"),
    ;

    companion object {
        fun fromWire(wire: String?): Envelope? = entries.firstOrNull { it.wire == wire }
    }
}

/** The models. Only E2B decides an envelope (D-01); the Gemma 3 1B rows are a control. */
internal enum class ModelKey(val wire: String) {
    E2B("e2b"),
    G3_1B("g3_1b"),
    ;

    companion object {
        fun fromWire(wire: String?): ModelKey? = entries.firstOrNull { it.wire == wire }
    }
}

internal enum class BackendKind(val wire: String) {
    CPU("cpu"),
    GPU("gpu"),
    ;

    companion object {
        fun fromWire(wire: String?): BackendKind? = entries.firstOrNull { it.wire == wire }
    }
}

/** Route A is constrained JSON (`ResponseFormat`); Route B is the native tool-call path. */
internal enum class Route(val wire: String) {
    A("a"),
    B("b"),
    ;

    companion object {
        fun fromWire(wire: String?): Route? = entries.firstOrNull { it.wire == wire }
    }
}

/** `auto` is model-chooses (the gating shape); `forced` forces a tool and is informational only. */
internal enum class Shape(val wire: String) {
    AUTO("auto"),
    FORCED("forced"),
    ;

    companion object {
        fun fromWire(wire: String?): Shape? = entries.firstOrNull { it.wire == wire }
    }
}

/** Item language, or `none` for a language-neutral item. */
internal enum class Lang(val wire: String) {
    EN("en"),
    ES("es"),
    NONE("none"),
    ;

    companion object {
        fun fromWire(wire: String?): Lang? = entries.firstOrNull { it.wire == wire }
    }
}

/** A positive item has a gold answer; a negative item must be declined (no mutating proposal). */
internal enum class ItemKind(val wire: String) {
    POS("pos"),
    NEG("neg"),
    ;

    companion object {
        fun fromWire(wire: String?): ItemKind? = entries.firstOrNull { it.wire == wire }
    }
}

/** Which part of an envelope's measurement a stage is. */
internal enum class StagePhase { SCREEN, CONFIRM, SUSTAINED }

/**
 * The ladder stages, in early-exit order. [envelope] and [phase] are set for the per-envelope stages only.
 */
internal enum class Stage(val wire: String, val envelope: Envelope? = null, val phase: StagePhase? = null) {
    PREPARE("prepare"),
    PREFLIGHT("preflight"),
    INIT("init"),
    PREFILL("prefill"),
    KV_REUSE("kv_reuse"),
    RF_MATRIX("rf_matrix"),
    SCREEN_SMALL("screen_small", Envelope.SMALL, StagePhase.SCREEN),
    CONFIRM_SMALL("confirm_small", Envelope.SMALL, StagePhase.CONFIRM),
    SCREEN_SB("screen_sb", Envelope.SB, StagePhase.SCREEN),
    CONFIRM_SB("confirm_sb", Envelope.SB, StagePhase.CONFIRM),
    SUSTAINED_SMALL("sustained_small", Envelope.SMALL, StagePhase.SUSTAINED),
    SUSTAINED_SB("sustained_sb", Envelope.SB, StagePhase.SUSTAINED),
    EXIT_REASONS("exit_reasons"),
    ;

    companion object {
        fun fromWire(wire: String?): Stage? = entries.firstOrNull { it.wire == wire }

        /** The stage of [phase] for [envelope]. */
        fun of(envelope: Envelope, phase: StagePhase): Stage =
            entries.first { it.envelope == envelope && it.phase == phase }
    }
}

/** One measured configuration: `<model>.<backend>.<route>.<shape>`. */
internal data class Cell(val model: ModelKey, val backend: BackendKind, val route: Route, val shape: Shape) {
    val wire: String get() = "${model.wire}.${backend.wire}.${route.wire}.${shape.wire}"

    override fun toString(): String = wire

    companion object {
        private const val PART_COUNT = 4

        fun fromWire(wire: String?): Cell? {
            val parts = wire?.split('.') ?: return null
            if (parts.size != PART_COUNT) return null
            return Cell(
                ModelKey.fromWire(parts[0]) ?: return null,
                BackendKind.fromWire(parts[1]) ?: return null,
                Route.fromWire(parts[2]) ?: return null,
                Shape.fromWire(parts[3]) ?: return null,
            )
        }
    }
}

/**
 * One trial of one gold item. The item id is opaque (never a transcript or a tool name); [outcome] is a stable code.
 */
internal data class TrialRecord(
    val env: Envelope,
    val stage: Stage,
    val cell: Cell,
    val item: String,
    val lang: Lang,
    val kind: ItemKind,
    val schemaValid: Boolean,
    val toolMatch: Boolean,
    val argsMatch: Boolean,
    val falseWrite: Boolean,
    val latencyMs: Long,
    val ttftMs: Long?,
    val prefillTokens: Int,
    val decodeTokens: Int,
    val firstInProcess: Boolean,
    val outcome: String,
) {
    /** True when the model picked the gold tool with the gold arguments. */
    val correct: Boolean get() = toolMatch && argsMatch

    /** `VAE_SPIKE_TRIAL` with exactly the closed key list, booleans as 0/1. */
    fun toLine(): SpikeLine = SpikeLine.of(
        SpikeKind.TRIAL,
        "env" to env.wire,
        "stage" to stage.wire,
        "cell" to cell.wire,
        "item" to item,
        "lang" to lang.wire,
        "kind" to kind.wire,
        "schema_valid" to bit(schemaValid),
        "tool_match" to bit(toolMatch),
        "args_match" to bit(argsMatch),
        "false_write" to bit(falseWrite),
        "latency_ms" to latencyMs.toString(),
        "ttft_ms" to (ttftMs?.toString() ?: NONE),
        "prefill_tokens" to prefillTokens.toString(),
        "decode_tokens" to decodeTokens.toString(),
        "first_in_process" to bit(firstInProcess),
        "outcome" to outcome,
    )

    companion object {
        private const val NONE = "none"

        private fun bit(value: Boolean): String = if (value) "1" else "0"

        private fun bool(value: String?): Boolean? = when (value) {
            "1" -> true
            "0" -> false
            else -> null
        }

        /** The trial a `VAE_SPIKE_TRIAL` line encodes, or null when the line is not a complete trial. */
        fun fromLine(line: SpikeLine): TrialRecord? {
            if (line.kind != SpikeKind.TRIAL) return null
            return TrialRecord(
                env = Envelope.fromWire(line["env"]) ?: return null,
                stage = Stage.fromWire(line["stage"]) ?: return null,
                cell = Cell.fromWire(line["cell"]) ?: return null,
                item = line["item"] ?: return null,
                lang = Lang.fromWire(line["lang"]) ?: return null,
                kind = ItemKind.fromWire(line["kind"]) ?: return null,
                schemaValid = bool(line["schema_valid"]) ?: return null,
                toolMatch = bool(line["tool_match"]) ?: return null,
                argsMatch = bool(line["args_match"]) ?: return null,
                falseWrite = bool(line["false_write"]) ?: return null,
                latencyMs = line["latency_ms"]?.toLongOrNull() ?: return null,
                ttftMs = line["ttft_ms"].let { if (it == NONE) null else it?.toLongOrNull() ?: return null },
                prefillTokens = line["prefill_tokens"]?.toIntOrNull() ?: return null,
                decodeTokens = line["decode_tokens"]?.toIntOrNull() ?: return null,
                firstInProcess = bool(line["first_in_process"]) ?: return null,
                outcome = line["outcome"] ?: return null,
            )
        }
    }
}
