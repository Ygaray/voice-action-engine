// Inert spike app: never published, and it reads no files or environment at configuration time
// (JitPack configures every included project even though only the install list runs).
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.github.ygaray.voiceactionengine.spike"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    defaultConfig {
        applicationId = "io.github.ygaray.voiceactionengine.spike"
        minSdk = 35
        targetSdk = 36
        versionCode = 1
        versionName = "0"
        ndk {
            // The TESTER (S22 Ultra) is arm64-v8a only; this also excludes the 26 MB x86_64 copy of the native library.
            abiFilters += "arm64-v8a"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// AGP 9 built-in Kotlin: the legacy Kotlin Android plugin is never applied. An app, so no explicitApi.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.litertlm.android)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(testFixtures(project(":core")))
}

// Verdict reproduction wiring for later plans. Values are path strings / properties only: nothing is read here.
tasks.withType<Test>().configureEach {
    val evidenceDir = providers.gradleProperty("vaeSpikeEvidenceDir").orElse("")
    val verdictOut = providers.gradleProperty("vaeSpikeVerdictOut").orElse("")
    systemProperty("vae.spike.evidenceDir", evidenceDir.get())
    systemProperty("vae.spike.verdictOut", verdictOut.get())
    systemProperty(
        "vae.spike.thresholdsFile",
        rootProject.layout.projectDirectory
            .file(".planning/phases/13-on-device-model-spike/13-THRESHOLDS.md").asFile.absolutePath,
    )
    if (evidenceDir.get().isNotEmpty()) {
        // A verdict recomputation must never be served stale from up-to-date checks or the build cache.
        inputs.dir(evidenceDir.get())
        outputs.upToDateWhen { false }
        outputs.cacheIf { false }
    }
}
