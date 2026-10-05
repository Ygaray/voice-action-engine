package io.github.ygaray.voiceactionengine.keystore

/**
 * Marks the key-custody seam that lets a caller supply its own device-key access ([KeyAccess]) to [ApiKeyStore].
 *
 * It is meant for tests that need a software key on the JVM. Production code keeps the platform key store, which the
 * two ordinary [ApiKeyStore] constructors use. Using a marked declaration without an explicit
 * `@OptIn(DelicateKeyAccess::class)` is a compile error.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This replaces the device key store with caller-supplied key access and is meant for tests only.",
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.CONSTRUCTOR)
public annotation class DelicateKeyAccess
