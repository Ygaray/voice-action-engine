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

// The speech engine is compile-visible only. This reads what is actually published (the POM and the Gradle module
// metadata) and the two resolved release classpaths, and fails when the speech engine would reach a consumer. Every
// check also asserts it saw something (non-vacuity), so an empty POM or classpath can never pass it.
// The match is on the speech engine's own group and artifact, never on the bare owner name: this engine's group shares
// that owner and legitimately appears in the POM. (The same group literal is held by the confinement gate in
// gradle/invariants.gradle.kts; an applied script cannot share a value with a build file.)
val verifyAdapterSttCompileOnly = tasks.register("verifyAdapterSttCompileOnly") {
    group = "verification"
    dependsOn("generatePomFileForReleasePublication", "generateMetadataFileForReleasePublication")
    val sttGroup = "com.github.Ygaray.voice-engine-android"
    val sttArtifact = "voice-engine-android"
    val violation = ":voice-adapter must keep :stt compileOnly: "
    val publications = layout.buildDirectory.dir("publications/release")
    val compileClasspath = configurations.named("releaseCompileClasspath")
    val runtimeClasspath = configurations.named("releaseRuntimeClasspath")
    doLast {
        fun isStt(depGroup: String?, depName: String?): Boolean =
            depGroup == sttGroup || depName == sttArtifact || (depName != null && depName.startsWith("$sttArtifact-"))

        val pom = publications.get().file("pom-default.xml").asFile
        if (!pom.isFile) throw GradleException(":stt gate is vacuous: no generated POM found")
        val pomDeps = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pom)
            .getElementsByTagName("dependency")
        val pomEntries = (0 until pomDeps.length).map { pomDeps.item(it) as org.w3c.dom.Element }.map { dep ->
            dep.getElementsByTagName("groupId").item(0)?.textContent?.trim() to
                dep.getElementsByTagName("artifactId").item(0)?.textContent?.trim()
        }
        if (pomEntries.none { it.second == "voice-action-engine-core" }) {
            throw GradleException(":stt gate is vacuous: the POM does not list voice-action-engine-core")
        }
        val pomHits = pomEntries.filter { isStt(it.first, it.second) }
        if (pomHits.isNotEmpty()) {
            throw GradleException("$violation the published POM lists ${pomHits.map { "${it.first}:${it.second}" }}")
        }

        // Absent only under the module-metadata escape hatch, which publishes the POM alone.
        val module = publications.get().file("module.json").asFile
        if (module.isFile) {
            val variants = (groovy.json.JsonSlurper().parse(module) as Map<*, *>)["variants"] as List<*>
            val apiVariants = variants.map { it as Map<*, *> }
                .filter { (it["attributes"] as? Map<*, *>)?.get("org.gradle.usage") == "java-api" }
            if (apiVariants.isEmpty()) throw GradleException(":stt gate is vacuous: no java-api variant in module.json")
            val lacking = apiVariants.filter { variant ->
                (variant["dependencies"] as? List<*>).orEmpty()
                    .none { (it as Map<*, *>)["module"] == "voice-action-engine-core" }
            }.map { it["name"] }
            if (lacking.isNotEmpty()) {
                throw GradleException(":stt gate is vacuous: voice-action-engine-core is missing from java-api variants $lacking")
            }
            variants.map { it as Map<*, *> }.forEach { variant ->
                (variant["dependencies"] as? List<*>).orEmpty().map { it as Map<*, *> }.forEach { dep ->
                    if (isStt(dep["group"] as? String, dep["module"] as? String)) {
                        throw GradleException("$violation module metadata variant ${variant["name"]} depends on ${dep["group"]}:${dep["module"]}")
                    }
                }
            }
        }

        val compiled = compileClasspath.get().incoming.resolutionResult.allComponents.mapNotNull { it.moduleVersion }
        if (compiled.none { it.group == sttGroup && it.name == sttArtifact }) {
            throw GradleException(":stt gate is vacuous: releaseCompileClasspath does not resolve $sttGroup:$sttArtifact")
        }
        val runtime = runtimeClasspath.get().incoming.resolutionResult.allComponents.mapNotNull { it.moduleVersion }
        if (runtime.isEmpty()) throw GradleException(":stt gate is vacuous: releaseRuntimeClasspath resolved no component")
        val runtimeHits = runtime.filter { it.group == sttGroup }.map { "${it.group}:${it.name}:${it.version}" }
        if (runtimeHits.isNotEmpty()) {
            throw GradleException("$violation releaseRuntimeClasspath resolves $runtimeHits")
        }
    }
}
tasks.named("check") { dependsOn(verifyAdapterSttCompileOnly) }

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
