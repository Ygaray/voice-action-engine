// Inert reference app: never published, and it reads no files or environment at configuration time
// (JitPack configures every included project even though only the install list runs).
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.ygaray.voiceactionengine.sample"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    defaultConfig {
        applicationId = "io.github.ygaray.voiceactionengine.sample"
        minSdk = 35
        targetSdk = 36
        versionCode = 1
        versionName = "0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
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
    implementation(project(":providers"))
    implementation(project(":keystore"))
    // The only place a 5.x OkHttp pin may live: runs the 4.12-compiled library bytecode on the real 5.x android variant.
    implementation("com.squareup.okhttp3:okhttp:5.2.1")

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(testFixtures(project(":core")))
}
