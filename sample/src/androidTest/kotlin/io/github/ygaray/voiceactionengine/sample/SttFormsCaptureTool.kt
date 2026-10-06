package io.github.ygaray.voiceactionengine.sample

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Opt-in capture tool (a tool, not a test), plan 14-09 / D-12: what does the platform speech recognizer emit for the
 * synthetic Phase 14 prompts (digits versus words, accents, punctuation, decimal marks)?
 *
 * Every row of `stt-prompts.tsv` (in the app's own external files dir, where `adb push` leaves it readable) is synthesized with the on-device
 * text-to-speech, strictly one utterance at a time (the locale is engine-global), then fed to the platform recognizer
 * as file audio through [RecognizerIntent.EXTRA_AUDIO_SOURCE]. One JSON line per prompt is appended to
 * `stt-forms.jsonl` (same dir): `id`, `lang`, `status` (`ok`, `error`, `tts_unavailable`) and, for `ok`,
 * `recognized` (for `error`, a short `code`). A header line names the device model, the SDK and the recognizer service.
 *
 * It does nothing unless run with `-e captureSttForms true`, so any ordinary connected run skips it. The only sanctioned
 * driver is `scripts/run-stt-capture.sh`. No log line is ever written (prompt and recognized text stay in the JSONL on
 * the device until the runner pulls and filters them), each WAV is deleted after use, and the tool holds no key.
 */
@RunWith(AndroidJUnit4::class)
class SttFormsCaptureTool {

    @Test
    fun captureRecognizerForms() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(
            "SttFormsCaptureTool is opt-in; run it with -e captureSttForms true",
            args.getString("captureSttForms") == "true",
        )

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val dir = requireNotNull(context.getExternalFilesDir(null)) { "external files dir unavailable" }
        val prompts = readPrompts(File(dir, PROMPTS_FILE))
        check(prompts.isNotEmpty()) { "no prompts found" }
        val results = File(dir, RESULTS_FILE)
        results.delete()
        results.appendText(header(context).toString() + "\n")

        val speaker = Speaker(context)
        try {
            for (prompt in prompts) {
                val row = captureOne(context, speaker, dir, prompt)
                results.appendText(row.toString() + "\n")
            }
        } finally {
            speaker.close()
        }
    }

    private fun captureOne(context: Context, speaker: Speaker, dir: File, prompt: Prompt): JSONObject {
        val row = JSONObject().put("id", prompt.id).put("lang", prompt.lang)
        val locale = if (prompt.lang == "es") Locale("es", "US") else Locale.US
        val wav = File(dir, "${prompt.id}.wav")
        try {
            if (!speaker.ready || speaker.setLanguage(locale) < TextToSpeech.LANG_AVAILABLE) {
                return row.put("status", "tts_unavailable")
            }
            if (!speaker.synthesize(prompt.text, wav)) {
                return row.put("status", "error").put("code", "tts")
            }
            val audio = readWav(wav) ?: return row.put("status", "error").put("code", "wav")
            val outcome = recognize(context, "${prompt.lang}-US", audio)
            row.put("status", outcome.status)
            if (outcome.code != null) row.put("code", outcome.code)
            if (outcome.text != null) row.put("recognized", outcome.text)
            return row
        } finally {
            wav.delete()
        }
    }

    private fun header(context: Context): JSONObject {
        val service = Settings.Secure.getString(context.contentResolver, "voice_recognition_service")
        return JSONObject()
            .put("kind", "header")
            .put("model", Build.MODEL)
            .put("sdk", Build.VERSION.SDK_INT)
            .put("recognizer", service ?: "unknown")
    }

    private class Prompt(val id: String, val lang: String, val text: String)

    private fun readPrompts(file: File): List<Prompt> {
        check(file.isFile) { "prompt list missing on the device" }
        return file.readLines(Charsets.UTF_8).drop(1).mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size >= PROMPT_COLUMNS) Prompt(parts[0], parts[1], parts[2]) else null
        }
    }

    private class Audio(val pcm: ByteArray, val rate: Int)

    /** PCM16 mono data and the sampling rate from a TTS WAV; null when the file is not that shape. */
    private fun readWav(file: File): Audio? {
        val bytes = file.readBytes()
        if (bytes.size < WAV_HEADER_MIN || String(bytes, 0, 4, Charsets.US_ASCII) != "RIFF") return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var pos = RIFF_PREAMBLE
        var rate = 0
        var ok = false
        while (pos + CHUNK_HEADER <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            val size = buf.getInt(pos + 4)
            val body = pos + CHUNK_HEADER
            if (id == "fmt ") {
                val format = buf.getShort(body).toInt() and 0xFFFF
                val channels = buf.getShort(body + 2).toInt()
                rate = buf.getInt(body + 4)
                val bits = buf.getShort(body + 14).toInt()
                ok = (format == PCM_FORMAT || format == WAVE_EXTENSIBLE) && channels == 1 && bits == PCM_BITS
            } else if (id == "data") {
                if (!ok || rate <= 0) return null
                // A streamed WAV may carry a zero or oversized length: take what is there.
                val end = if (size <= 0 || body + size > bytes.size) bytes.size else body + size
                val pcm = resample(bytes.copyOfRange(body, end), rate)
                // Lead-in and a half second of trailing silence so the recognizer sees both endpoints.
                return Audio(ByteArray(LEAD_SILENCE_BYTES) + pcm + ByteArray(TAIL_SILENCE_BYTES), TARGET_RATE)
            }
            if (size < 0) return null
            pos = body + size + (size and 1)
        }
        return null
    }

    /** Linear-interpolation resample of PCM16 mono little-endian audio to [TARGET_RATE]. */
    private fun resample(pcm: ByteArray, rate: Int): ByteArray {
        if (rate == TARGET_RATE) return pcm
        val src = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val count = src.limit()
        if (count == 0) return pcm
        val outCount = (count.toLong() * TARGET_RATE / rate).toInt()
        val out = ByteBuffer.allocate(outCount * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until outCount) {
            val pos = i.toDouble() * rate / TARGET_RATE
            val i0 = minOf(pos.toInt(), count - 1)
            val a = src.get(i0).toInt()
            val b = src.get(minOf(i0 + 1, count - 1)).toInt()
            out.putShort((a + (b - a) * (pos - i0)).toInt().toShort())
        }
        return out.array()
    }

    private class Outcome(val status: String, val code: String?, val text: String?)

    private class Holder {
        @Volatile var status = "error"

        @Volatile var code: String? = "timeout"

        @Volatile var text: String? = null
        val segments = java.util.Collections.synchronizedList(mutableListOf<String>())
        val done = CountDownLatch(1)
    }

    private fun recognize(context: Context, languageTag: String, audio: Audio): Outcome {
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) return Outcome("error", "no_on_device_recognizer", null)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val pipe = ParcelFileDescriptor.createPipe()
        val reader = pipe[0]
        val writer = pipe[1]
        val feeder = Thread {
            ParcelFileDescriptor.AutoCloseOutputStream(writer).use { out ->
                try {
                    out.write(audio.pcm)
                } catch (_: java.io.IOException) {
                    // The recognizer closed its end early (it already has what it needs).
                }
            }
        }
        val holder = Holder()
        var recognizer: SpeechRecognizer? = null
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, reader)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, android.media.AudioFormat.ENCODING_PCM_16BIT)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, audio.rate)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
        try {
            instrumentation.runOnMainSync {
                val created = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                recognizer = created
                created.setRecognitionListener(listener(holder))
                created.startListening(intent)
            }
            feeder.start()
            holder.done.await(RECOGNIZE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } finally {
            instrumentation.runOnMainSync {
                recognizer?.cancel()
                recognizer?.destroy()
            }
            runCatching { reader.close() }
            feeder.join(FEEDER_JOIN_MS)
        }
        return Outcome(holder.status, holder.code, holder.text)
    }

    private fun listener(holder: Holder) = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onSegmentResults(segmentResults: Bundle) {
            val first = segmentResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!first.isNullOrBlank()) holder.segments.add(first.trim())
        }

        override fun onEndOfSegmentedSession() = finish(holder.segments.joinToString(" "))

        override fun onResults(results: Bundle?) {
            val first = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            finish(if (first.isNullOrBlank()) holder.segments.joinToString(" ") else first)
        }

        override fun onError(error: Int) {
            holder.status = "error"
            holder.code = error.toString()
            holder.done.countDown()
        }

        private fun finish(text: String) {
            if (text.isBlank()) {
                holder.status = "error"
                holder.code = "empty"
            } else {
                holder.status = "ok"
                holder.code = null
                holder.text = text
            }
            holder.done.countDown()
        }
    }

    /** One TextToSpeech instance; the locale is engine-global, so callers synthesize strictly one utterance at a time. */
    private class Speaker(context: Context) {
        private var engine: TextToSpeech? = null
        val ready: Boolean get() = engine != null

        init {
            engine = open(context, GOOGLE_TTS_PACKAGE) ?: open(context, null)
        }

        private fun open(context: Context, pkg: String?): TextToSpeech? {
            val init = CountDownLatch(1)
            var status = TextToSpeech.ERROR
            val created = TextToSpeech(
                context,
                { s ->
                    status = s
                    init.countDown()
                },
                pkg,
            )
            val ok = init.await(INIT_TIMEOUT_SECONDS, TimeUnit.SECONDS) && status == TextToSpeech.SUCCESS
            if (!ok) created.shutdown()
            return if (ok) created else null
        }

        fun setLanguage(locale: Locale): Int = engine?.setLanguage(locale) ?: TextToSpeech.ERROR

        fun synthesize(text: String, out: File): Boolean {
            val tts = engine ?: return false
            val done = CountDownLatch(1)
            var failed = false
            tts.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) = done.countDown()

                    @Deprecated("required override", ReplaceWith(""))
                    override fun onError(utteranceId: String?) {
                        failed = true
                        done.countDown()
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        failed = true
                        done.countDown()
                    }
                },
            )
            val queued = tts.synthesizeToFile(text, null, out, "u")
            return queued == TextToSpeech.SUCCESS && done.await(SYNTH_TIMEOUT_SECONDS, TimeUnit.SECONDS) && !failed
        }

        fun close() {
            engine?.stop()
            engine?.shutdown()
            engine = null
        }
    }

    private companion object {
        const val PROMPTS_FILE = "stt-prompts.tsv"
        const val RESULTS_FILE = "stt-forms.jsonl"
        const val GOOGLE_TTS_PACKAGE = "com.google.android.tts"
        const val PROMPT_COLUMNS = 5
        const val INIT_TIMEOUT_SECONDS = 30L
        const val SYNTH_TIMEOUT_SECONDS = 60L
        const val RECOGNIZE_TIMEOUT_SECONDS = 30L
        const val FEEDER_JOIN_MS = 2_000L
        const val WAV_HEADER_MIN = 44
        const val RIFF_PREAMBLE = 12
        const val CHUNK_HEADER = 8
        const val PCM_FORMAT = 1
        const val WAVE_EXTENSIBLE = 0xFFFE
        const val PCM_BITS = 16
        const val TARGET_RATE = 16_000
        const val LEAD_SILENCE_BYTES = 9_600 // 0.3 s of 16 kHz PCM16
        const val TAIL_SILENCE_BYTES = 16_000 // 0.5 s of 16 kHz PCM16
    }
}
