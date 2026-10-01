package io.github.ygaray.voiceactionengine.sample.net

private const val OKHTTP_CLASS = "okhttp3.OkHttp"
private const val VERSION_FIELD = "VERSION"
private const val UNKNOWN = "unknown"

/**
 * Reads the OkHttp version that is actually loaded at runtime.
 *
 * The version is read reflectively on purpose: a direct reference to the constant is inlined by the compiler, so it
 * would report the compile-time value instead of the runtime one.
 */
internal object OkHttpRuntime {
    /** The loaded OkHttp version, or "unknown" when it cannot be read. Never throws. */
    fun version(loader: ClassLoader? = OkHttpRuntime::class.java.classLoader): String =
        try {
            Class.forName(OKHTTP_CLASS, true, loader).getField(VERSION_FIELD).get(null) as? String ?: UNKNOWN
        } catch (_: ReflectiveOperationException) {
            UNKNOWN
        } catch (_: LinkageError) {
            UNKNOWN
        }
}
