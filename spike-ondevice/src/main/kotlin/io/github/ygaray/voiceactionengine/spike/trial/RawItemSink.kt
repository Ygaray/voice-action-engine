package io.github.ygaray.voiceactionengine.spike.trial

import io.github.ygaray.voiceactionengine.spike.backend.RawToolCall
import io.github.ygaray.voiceactionengine.spike.evidence.Cell
import io.github.ygaray.voiceactionengine.spike.evidence.Stage
import io.github.ygaray.voiceactionengine.spike.evidence.TrialRecord
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * What the model answered for one trial, kept only for host-private re-scoring (13-CONTEXT RT-02). It never reaches the
 * evidence log: the answer text and the tool arguments are fixture data. [toString] shows presence flags only.
 *
 * @property text the model's raw answer text.
 * @property rawToolCalls the native tool calls the runtime returned, if any.
 * @property firstCallName the name of the first call the provider mapped, or null.
 * @property firstCallArguments the arguments of that call, or null.
 * @property stopReason the provider's stop reason, or null.
 * @property failureCode the stable failure code, or null.
 */
internal class RawItemAnswer(
    val text: String?,
    val rawToolCalls: List<RawToolCall>,
    val firstCallName: String?,
    val firstCallArguments: JsonObject?,
    val stopReason: String?,
    val failureCode: String?,
) {
    override fun toString(): String = "RawItemAnswer(text=${text != null}, calls=${rawToolCalls.size}, call=${firstCallName != null})"
}

/** Receives one scored trial with the model's raw answer. Implementations keep it private. */
internal fun interface RawItemSink {
    fun record(stage: Stage, cell: Cell, trial: TrialRecord, answer: RawItemAnswer)
}

/**
 * Appends one JSON line per trial to `<dir>/<stage wire>.jsonl`, inside the app's private storage. The runner's
 * `pull-private-raw` copies the directory to a host-private path before cleanup removes it; it is never committed.
 * [toString] never shows a path content.
 */
internal class PrivateRawSink(private val dir: File) : RawItemSink {
    override fun record(stage: Stage, cell: Cell, trial: TrialRecord, answer: RawItemAnswer) {
        dir.mkdirs()
        val line = buildJsonObject {
            put("stage", stage.wire)
            put("cell", cell.wire)
            put("item", trial.item)
            put("kind", trial.kind.name)
            put("lang", trial.lang.name)
            put("outcome", trial.outcome)
            put("schema_valid", trial.schemaValid)
            put("tool_match", trial.toolMatch)
            put("args_match", trial.argsMatch)
            put("false_write", trial.falseWrite)
            put("latency_ms", trial.latencyMs)
            put("text", answer.text?.let { JsonPrimitive(it) } ?: JsonNull)
            put("raw_tool_calls", buildJsonArray { answer.rawToolCalls.forEach { add(toJson(it)) } })
            put("first_call_name", answer.firstCallName?.let { JsonPrimitive(it) } ?: JsonNull)
            put("first_call_arguments", answer.firstCallArguments ?: JsonNull)
            put("stop_reason", answer.stopReason?.let { JsonPrimitive(it) } ?: JsonNull)
            put("failure_code", answer.failureCode?.let { JsonPrimitive(it) } ?: JsonNull)
        }
        File(dir, "${stage.wire}.jsonl").appendText(line.toString() + "\n")
    }

    private fun toJson(call: RawToolCall): JsonElement = buildJsonObject {
        put("name", call.name)
        put("arguments", anyToJson(call.arguments))
    }

    private fun anyToJson(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to anyToJson(v) })
        is Iterable<*> -> JsonArray(value.map { anyToJson(it) })
        else -> JsonPrimitive(value.toString())
    }

    override fun toString(): String = "PrivateRawSink"
}
