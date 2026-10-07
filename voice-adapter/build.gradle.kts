plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
    alias(libs.plugins.detekt)
    alias(libs.plugins.metalava)
}

android {
    namespace = "io.github.ygaray.voiceactionengine.voiceadapter"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    defaultConfig {
        minSdk = 35
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    publishing {
        // Without singleVariant the release component does not exist and JitPack's Android publish fails.
        singleVariant("release") { withSourcesJar() }
    }
}

// AGP 9 built-in Kotlin: no org.jetbrains.kotlin.android plugin.
kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        // Restrict the JDK API surface to 11 (see :core); the build JDK is 17.
        freeCompilerArgs.add("-Xjdk-release=11")
    }
}

dependencies {
    api(project(":core"))
    // Compile-only: the speech engine's types must never reach the published POM or module metadata.
    compileOnly(libs.stt.engine)
    // compileOnly is not on the unit-test classpath, so the tests need the speech engine explicitly.
    testImplementation(libs.stt.engine)
    testImplementation(libs.junit)
}

val engineGroup: String by rootProject.extra
val engineVersion: String by rootProject.extra

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = engineGroup
            artifactId = "voice-action-engine-voice-adapter"
            version = engineVersion
            afterEvaluate { from(components["release"]) }
        }
    }
}

apply(from = rootProject.file("gradle/invariants.gradle.kts"))
