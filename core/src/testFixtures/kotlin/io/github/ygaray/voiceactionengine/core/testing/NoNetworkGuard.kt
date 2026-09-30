package io.github.ygaray.voiceactionengine.core.testing

import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/**
 * Makes "zero network" observable in a test. The classpath half proves no HTTP stack is even loadable; the runtime
 * half installs a tripwire on the JVM default [ProxySelector], so it catches code that resolves a proxy route at
 * connect time while [during] is active.
 *
 * Limits (this is a tripwire, not proof of zero network):
 * - It does not see clients that never consult the default selector: NIO `SocketChannel`, a `java.net.http.HttpClient`
 *   with its own selector, direct `InetAddress` DNS lookups, pooled keep-alive connections, or an `OkHttpClient`
 *   built before [during] (it captures the default selector at build time).
 * - It mutates process-global state without synchronisation, so [during] is not safe under parallel test execution.
 * - The [AssertionError] it throws can be swallowed by a `catch (Throwable)` in the code under test.
 *
 * Transport tests that need a stronger guarantee must add their own layer (for example an `OkHttpClient` whose `Dns`
 * throws); that belongs next to the transport, because `:core` has no HTTP dependency.
 */
public object NoNetworkGuard {
    private val httpStackClasses = listOf(
        "okhttp3.OkHttpClient",
        "io.ktor.client.HttpClient",
        "org.apache.http.client.HttpClient",
        "retrofit2.Retrofit",
    )

    /** Fails with an [AssertionError] naming the first HTTP client class that is loadable on this classpath. */
    public fun assertNoHttpStackOnClasspath() {
        for (name in httpStackClasses) {
            val loadable = try {
                Class.forName(name, false, NoNetworkGuard::class.java.classLoader)
                true
            } catch (expected: ClassNotFoundException) {
                false
            }
            if (loadable) throw AssertionError("HTTP stack on the classpath: $name")
        }
    }

    /** Swaps in the tripwire for the duration of [block]; the previous selector is restored even if [block] throws. */
    public inline fun <R> during(block: () -> R): R {
        val previous = installTripwire()
        try {
            return block()
        } finally {
            ProxySelector.setDefault(previous)
        }
    }

    /** Installs the tripwire and returns the selector it replaced; pair with [ProxySelector.setDefault]. */
    @PublishedApi
    internal fun installTripwire(): ProxySelector? {
        val previous = ProxySelector.getDefault()
        ProxySelector.setDefault(Tripwire)
        return previous
    }

    private object Tripwire : ProxySelector() {
        override fun select(uri: URI?): List<Proxy> = throw AssertionError("network attempt: $uri")

        override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
    }
}
