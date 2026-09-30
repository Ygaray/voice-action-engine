package io.github.ygaray.voiceactionengine.core.testing

import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/**
 * Makes "zero network" observable in a test. The classpath half proves no HTTP stack is even loadable; the runtime
 * half installs a tripwire that fails any code asking the JVM for a proxy route, which every JDK connection does first.
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
