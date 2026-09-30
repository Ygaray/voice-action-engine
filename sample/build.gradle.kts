// Inert reference app: never published, and it reads no files or environment at configuration time
// (JitPack configures every included project even though only the install list runs).
plugins {
    alias(libs.plugins.android.application)
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
}

dependencies {
    implementation(project(":core"))
    implementation(project(":providers"))
    implementation(project(":keystore"))
    // The only place a 5.x OkHttp pin may live: runs the 4.12-compiled library bytecode on the real 5.x android variant.
    implementation("com.squareup.okhttp3:okhttp:5.2.1")
}
