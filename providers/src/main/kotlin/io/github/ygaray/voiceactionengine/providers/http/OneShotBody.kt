package io.github.ygaray.voiceactionengine.providers.http

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink

private val JSON_UTF8: MediaType = "application/json; charset=utf-8".toMediaType()

/**
 * A JSON request body that OkHttp will not send twice on its own.
 *
 * A plain byte-array body is replayable, so after the bytes were sent OkHttp may silently re-send a POST on a stale
 * connection and the provider could bill the same request twice. Marking the body one-shot makes that impossible; the
 * caller's own retry loop is the only place a request is ever repeated. The length is known up front, so the request
 * is sent with a content length rather than chunked.
 */
internal class OneShotJsonBody(private val bytes: ByteArray) : RequestBody() {
    override fun contentType(): MediaType = JSON_UTF8

    override fun contentLength(): Long = bytes.size.toLong()

    override fun writeTo(sink: BufferedSink) {
        sink.write(bytes)
    }

    // The caller's loop owns every replay; OkHttp must never repeat this body by itself.
    override fun isOneShot(): Boolean = true
}
