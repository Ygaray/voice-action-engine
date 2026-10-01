package io.github.ygaray.voiceactionengine.providers.http

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val SUCCESS_MIN = 200
private const val SUCCESS_MAX = 299

/**
 * An immutable snapshot of an HTTP answer: the status, the headers and the whole body as text (null when the answer
 * carried no body). It holds no connection, so nothing needs closing.
 */
internal class HttpReply(val code: Int, val headers: Headers, val body: String?) {
    val isSuccessful: Boolean get() = code in SUCCESS_MIN..SUCCESS_MAX

    // The status only: headers and body can carry keys or user text and must never reach a string.
    override fun toString(): String = "HttpReply(code=$code)"
}

/**
 * Runs this call and suspends until its answer is fully read.
 *
 * The body is read inside the OkHttp callback and the response is closed there, so an OkHttp response never leaves this
 * function. Cancelling the awaiting coroutine cancels the call, which also aborts a body that is still streaming; an
 * answer that arrives after cancellation is closed unread, and a failure after cancellation is ignored.
 */
internal suspend fun Call.await(): HttpReply = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body
                try {
                    if (!continuation.isActive) return
                    val text = body?.string()
                    if (continuation.isActive) continuation.resume(HttpReply(response.code, response.headers, text))
                } catch (e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                } finally {
                    body?.close()
                }
            }
        },
    )
}
