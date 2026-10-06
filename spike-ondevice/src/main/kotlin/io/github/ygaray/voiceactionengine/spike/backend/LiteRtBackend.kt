@file:OptIn(com.google.ai.edge.litertlm.ExperimentalApi::class)

package io.github.ygaray.voiceactionengine.spike.backend

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.LiteRtLmJniException
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.ResponseFormat
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import com.google.ai.edge.litertlm.ToolProvider
import com.google.ai.edge.litertlm.tool
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.spike.provider.jsonToMap
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

private const val NANOS_PER_MILLI = 1_000_000L
private const val MILLIS_PER_SECOND = 1000.0

// Words a native context-length failure uses. The message is read for this classification only and is never kept.
private val CONTEXT_MARKERS = listOf("max_num_tokens", "context length", "context window", "token limit", "too long")

/**
 * The single LiteRT-LM adapter: the only file that imports `com.google.ai.edge`. It loads the engine on the CPU or GPU
 * backend, runs one conversation per call in constrained-JSON mode (Route A) or native-tool mode (Route B), and reads
 * the runtime's own benchmark counters after every call.
 *
 * Determinism: greedy sampling (`topK = 1`, `topP = 1.0`, `temperature = 0.0`, `seed = 0`) and thinking off, so a trial's
 * output depends on the prompt and the model only. The context length is always explicit, never the runtime's default,
 * which silently truncates a long prompt.
 *
 * There is no KV clone API, so KV reuse across calls is measured by the ladder (the `prefillPrefaceOnInit` flag in
 * [BackendConfig] is the probe's lever), never assumed.
 *
 * Blocking JNI runs on the caller's dispatcher; this class creates no thread. Failures are [BackendFailure] with a stable
 * code only: the runtime's message is classified and then dropped, so no prompt, output or argument text leaves.
 * It is not unit-tested on the JVM (native); its first real run is on the TESTER in plan 13-08.
 */
internal class LiteRtBackend : LlmBackend {
    private var engine: Engine? = null
    private var config: BackendConfig? = null

    override suspend fun initialize(config: BackendConfig): InitOutcome {
        val started = System.nanoTime()
        val code = loadEngine(config)
        val elapsedMs = (System.nanoTime() - started) / NANOS_PER_MILLI
        if (code == null) this.config = config
        return InitOutcome(code, elapsedMs)
    }

    // Returns null on success, else the stable failure code. The exception message is dropped on purpose.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun loadEngine(config: BackendConfig): String? {
        if (!File(config.modelPath).isFile) return MODEL_MISSING
        ExperimentalFlags.enableBenchmark = true
        val failure = if (config.gpu) GPU_INIT_FAILED else INIT_FAILED
        return try {
            val backend = if (config.gpu) Backend.GPU() else Backend.CPU()
            val loaded = Engine(
                EngineConfig(
                    modelPath = config.modelPath,
                    backend = backend,
                    maxNumTokens = config.maxNumTokens,
                    cacheDir = config.cacheDir,
                ),
            )
            loaded.initialize()
            engine?.close()
            engine = loaded
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: OutOfMemoryError) {
            INSUFFICIENT_MEMORY
        } catch (e: LiteRtLmJniException) {
            failure
        } catch (e: Exception) {
            failure
        }
    }

    override suspend fun generate(request: BackendRequest): BackendAnswer {
        val loaded = engine ?: throw BackendFailure(INIT_FAILED)
        val settings = config ?: throw BackendFailure(INIT_FAILED)
        return try {
            run(loaded, settings, request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: BackendFailure) {
            throw e
        } catch (e: OutOfMemoryError) {
            throw BackendFailure(INSUFFICIENT_MEMORY)
        } catch (e: LiteRtLmJniException) {
            throw BackendFailure(nativeCode(e.message))
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            throw BackendFailure(nativeCode(e.message))
        }
    }

    private fun run(engine: Engine, settings: BackendConfig, request: BackendRequest): BackendAnswer {
        ExperimentalFlags.enableBenchmark = true
        val constrained = request.mode is BackendMode.Constrained && request.constraintOn
        // Pitfall 1: the engine's constrained-decoding flag is read when a conversation is created, so it follows the
        // ON/OFF arm; otherwise the ON arm could silently measure an unconstrained model. The rf_matrix overrides it
        // (engineFlag) to learn whether the flag is needed at all.
        ExperimentalFlags.enableConversationConstrainedDecoding = request.engineFlag ?: constrained
        engine.createConversation(conversationConfig(settings, request)).use { conversation ->
            val reply = send(conversation, request, constrained)
            return BackendAnswer(
                text = reply.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text },
                rawToolCalls = reply.toolCalls.map { RawToolCall(it.name, it.arguments) },
                bench = benchFacts(conversation),
            )
        }
    }

    private fun conversationConfig(settings: BackendConfig, request: BackendRequest): ConversationConfig =
        ConversationConfig(
            systemInstruction = Contents.of(request.system),
            tools = toolProviders(request.mode),
            samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0, seed = 0),
            automaticToolCalling = false,
            prefillPrefaceOnInit = settings.prefillPrefaceOnInit,
            maxOutputToken = request.maxOutputTokens,
            thinkingConfig = ThinkingConfig(false),
            enableResponseFormat = request.mode is BackendMode.Constrained,
        )

    // Route B hands every offered tool over as an OpenApiTool whose execute is never called (automatic calling is off).
    private fun toolProviders(mode: BackendMode): List<ToolProvider> = when (mode) {
        is BackendMode.NativeTools -> mode.tools.map { tool(DeclaredTool(it)) }
        is BackendMode.Constrained -> emptyList()
    }

    private fun send(conversation: Conversation, request: BackendRequest, constrained: Boolean): Message {
        val message = Message.user(request.user)
        val mode = request.mode
        return if (constrained && mode is BackendMode.Constrained) {
            conversation.sendMessage(message, emptyMap(), responseFormat = ResponseFormat.json(jsonToMap(mode.schema)))
        } else {
            conversation.sendMessage(message)
        }
    }

    // The counters describe the call that just ended. A backend that cannot report them yields nulls, never an error.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun benchFacts(conversation: Conversation): BenchFacts = try {
        val info = conversation.getBenchmarkInfo()
        BenchFacts(
            prefillTokens = info.lastPrefillTokenCount,
            decodeTokens = info.lastDecodeTokenCount,
            ttftMs = info.timeToFirstTokenInSecond * MILLIS_PER_SECOND,
            prefillTokensPerSecond = info.lastPrefillTokensPerSecond,
            decodeTokensPerSecond = info.lastDecodeTokensPerSecond,
        )
    } catch (e: Exception) {
        BenchFacts.NONE
    }

    private fun nativeCode(message: String?): String {
        val text = message?.lowercase().orEmpty()
        return if (CONTEXT_MARKERS.any { it in text }) CONTEXT_OVERFLOW else NATIVE_ERROR
    }

    override fun close() {
        engine?.close()
        engine = null
        config = null
    }

    /** One offered tool as the runtime's OpenAPI description; it is declared to the model and never executed. */
    private class DeclaredTool(private val spec: ToolSpec) : OpenApiTool {
        override fun getToolDescriptionJsonString(): String = buildJsonObject {
            put("name", spec.name)
            put("description", spec.description)
            put("parameters", spec.inputSchema)
        }.toString()

        // Automatic tool calling is off, so this is never reached; the engine proposes, only the app's gate writes.
        override fun execute(paramsJsonString: String): String = "{}"
    }

    companion object {
        const val MODEL_MISSING = "model_missing"
        const val INIT_FAILED = "init_failed"
        const val GPU_INIT_FAILED = "gpu_init_failed"
        const val INSUFFICIENT_MEMORY = "insufficient_memory"
        const val NATIVE_ERROR = "native_error"
        const val CONTEXT_OVERFLOW = "context_overflow"
    }
}
