plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
    alias(libs.plugins.detekt)
    alias(libs.plugins.metalava)
}

android {
    namespace = "io.github.ygaray.voiceactionengine.keystore"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    defaultConfig {
        minSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    api(libs.datastore.prefs)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(testFixtures(project(":core")))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

// check compiles and lints the instrumented sources without needing a device.
tasks.named("check") { dependsOn("assembleDebugAndroidTest") }

// The DataStore type is in the public constructor, so consumers need it on their compile classpath. This reads what is
// actually published (the POM and the Gradle module metadata) and fails when the dependency is no longer an api one.
val verifyDatastoreIsApi = tasks.register("verifyDatastoreIsApi") {
    group = "verification"
    dependsOn("generatePomFileForReleasePublication", "generateMetadataFileForReleasePublication")
    val dir = layout.buildDirectory.dir("publications/release")
    doLast {
        val pom = dir.get().file("pom-default.xml").asFile
        if (!pom.isFile) throw GradleException("no generated POM found (vacuous)")
        val deps = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pom)
            .getElementsByTagName("dependency")
        val scopes = (0 until deps.length).map { deps.item(it) as org.w3c.dom.Element }
            .filter { dep ->
                dep.getElementsByTagName("groupId").item(0).textContent.trim() == "androidx.datastore" &&
                    dep.getElementsByTagName("artifactId").item(0).textContent.trim() == "datastore-preferences"
            }
            .map { dep -> dep.getElementsByTagName("scope").item(0)?.textContent?.trim() ?: "none" }
        if (scopes != listOf("compile")) {
            throw GradleException(
                "datastore-preferences is not an api dependency of :keystore: POM scope found $scopes, expected [compile]",
            )
        }
        // Absent only under the module-metadata escape hatch, which publishes the POM alone.
        val module = dir.get().file("module.json").asFile
        if (module.isFile) {
            val variants = (groovy.json.JsonSlurper().parse(module) as Map<*, *>)["variants"] as List<*>
            val apiVariants = variants.map { it as Map<*, *> }
                .filter { (it["attributes"] as? Map<*, *>)?.get("org.gradle.usage") == "java-api" }
            if (apiVariants.isEmpty()) throw GradleException("no java-api variant in module.json (vacuous)")
            val lacking = apiVariants.filter { variant ->
                (variant["dependencies"] as? List<*>).orEmpty().none { (it as Map<*, *>)["module"] == "datastore-preferences" }
            }.map { it["name"] }
            if (lacking.isNotEmpty()) {
                throw GradleException("datastore-preferences is not an api dependency of :keystore: missing from variants $lacking")
            }
        }
    }
}
tasks.named("check") { dependsOn(verifyDatastoreIsApi) }

// The app owns the only DataStore on its file; a second one on the same file throws at runtime. So no main source may
// create one: the app injects it.
val verifyNoDataStoreCreation = tasks.register("verifyNoDataStoreCreation") {
    group = "verification"
    val mainSources = fileTree("src/main") { include("**/*.kt") }
    doLast {
        val creation = Regex("""\b(preferencesDataStore|PreferenceDataStoreFactory|DataStoreFactory)\b""")
        val hits = mainSources.files.sorted().flatMap { file ->
            file.readLines().withIndex().filter { (_, line) ->
                val text = line.trim()
                !text.startsWith("//") && !text.startsWith("*") && !text.startsWith("/*") && creation.containsMatchIn(text)
            }.map { (index, _) -> "${file.name}:${index + 1}" }
        }
        if (hits.isNotEmpty()) throw GradleException("keystore main source creates a DataStore: $hits")
    }
}
tasks.named("check") { dependsOn(verifyNoDataStoreCreation) }

val engineGroup: String by rootProject.extra
val engineVersion: String by rootProject.extra

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = engineGroup
            artifactId = "voice-action-engine-keystore"
            version = engineVersion
            afterEvaluate { from(components["release"]) }
        }
    }
}

apply(from = rootProject.file("gradle/invariants.gradle.kts"))
