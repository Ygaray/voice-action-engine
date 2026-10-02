package io.github.ygaray.voiceactionengine.core.strategy

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

// Field names are mirrored in TerminalCall.asClarification; the round-trip test keeps the two in step.
private const val QUESTION_FIELD = "question"
private const val OPTIONS_FIELD = "options"
private const val ID_FIELD = "id"
private const val LABEL_FIELD = "label"
private const val TYPE_KEY = "type"
private const val PROPERTIES_KEY = "properties"
private const val REQUIRED_KEY = "required"
private const val ITEMS_KEY = "items"
private const val ADDITIONAL_PROPERTIES_KEY = "additionalProperties"
private const val TYPE_OBJECT = "object"
private const val TYPE_STRING = "string"
private const val TYPE_ARRAY = "array"
private const val DEFAULT_CLARIFICATION_DESCRIPTION =
    "Ask the user to choose one of the listed options instead of guessing."

/**
 * A tool the model may call.
 *
 * A terminal tool ends the run: its call is delivered as the completed outcome's terminal call. A terminal tool must
 * be non-mutating, so the run can never end on an ungated write.
 *
 * The constructor keeps Kotlin default arguments so a call site can name any one optional argument. That freezes its
 * parameter list: later versions add attributes as separate members (a `with...` function), never as a seventh
 * constructor parameter. Java callers pass all six arguments, and no shorter overloads are generated, so the frozen
 * JVM surface is this one constructor plus Kotlin's default-argument stub.
 *
 * @property name the tool name the model calls.
 * @property description what the tool does, shown to the model.
 * @property inputSchema the JSON schema of the tool's arguments.
 * @property mutating true when the tool changes app state, so every call goes through the pre-apply gate.
 * @property terminal true when calling the tool ends the run.
 * @property strict true asks the provider for strict schema adherence, false asks it not to, and null lets the engine
 * decide per provider and model.
 * @throws IllegalArgumentException when [name] is blank, or when the tool is both terminal and mutating.
 */
public class ToolSpec(
    public val name: String,
    public val description: String,
    public val inputSchema: JsonObject,
    public val mutating: Boolean = false,
    public val terminal: Boolean = false,
    public val strict: Boolean? = null,
) {
    init {
        require(name.isNotBlank()) { "a tool name must not be blank" }
        require(!(terminal && mutating)) { "a terminal tool must be non-mutating" }
    }

    /** Prints the name and flags only, never the schema or description. */
    override fun toString(): String = "ToolSpec(name=$name, mutating=$mutating, terminal=$terminal, strict=$strict)"

    /** Builders for the tools every app declares the same way. */
    public companion object {
        /**
         * A terminal, non-mutating tool the model calls to ask the user to choose; read the call with
         * [TerminalCall.asClarification].
         */
        public fun clarification(name: String): ToolSpec = clarification(name, DEFAULT_CLARIFICATION_DESCRIPTION)

        /** Like [clarification] with a custom [description] shown to the model. */
        public fun clarification(name: String, description: String): ToolSpec =
            ToolSpec(name, description, clarificationSchema(), mutating = false, terminal = true)
    }
}

// Key order is part of the contract: the schema string is byte-stable so provider prompt caches keep hitting.
private fun clarificationSchema(): JsonObject = buildJsonObject {
    put(TYPE_KEY, TYPE_OBJECT)
    putJsonObject(PROPERTIES_KEY) {
        putJsonObject(QUESTION_FIELD) { put(TYPE_KEY, TYPE_STRING) }
        putJsonObject(OPTIONS_FIELD) {
            put(TYPE_KEY, TYPE_ARRAY)
            putJsonObject(ITEMS_KEY) {
                put(TYPE_KEY, TYPE_OBJECT)
                putJsonObject(PROPERTIES_KEY) {
                    putJsonObject(ID_FIELD) { put(TYPE_KEY, TYPE_STRING) }
                    putJsonObject(LABEL_FIELD) { put(TYPE_KEY, TYPE_STRING) }
                }
                putJsonArray(REQUIRED_KEY) {
                    add(ID_FIELD)
                    add(LABEL_FIELD)
                }
                put(ADDITIONAL_PROPERTIES_KEY, false)
            }
        }
    }
    putJsonArray(REQUIRED_KEY) {
        add(QUESTION_FIELD)
        add(OPTIONS_FIELD)
    }
    put(ADDITIONAL_PROPERTIES_KEY, false)
}
