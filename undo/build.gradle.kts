// The module depends on nothing but the Kotlin standard library.
// The gates verifyModuleGraph and verifyUndoZeroDeps enforce it.
plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
    alias(libs.plugins.detekt)
    alias(libs.plugins.metalava)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    withSourcesJar()
}

kotlin {
    explicitApi()
    // Mandatory: without it Kotlin targets the running JDK (17) and Gradle rejects the mismatch with Java's 11.
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        // Restrict the JDK API surface to 11: the build JDK is 17, and without this JDK 12-17 calls compile to major 55
        // and fail only at runtime (NoSuchMethodError) on a consumer running an older JDK.
        freeCompilerArgs.add("-Xjdk-release=11")
    }
}

dependencies {
    testImplementation(libs.junit)
}

val engineGroup: String by rootProject.extra
val engineVersion: String by rootProject.extra

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = engineGroup
            artifactId = "voice-action-engine-undo"
            version = engineVersion
            from(components["java"])
        }
    }
}

apply(from = rootProject.file("gradle/invariants.gradle.kts"))
