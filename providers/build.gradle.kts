plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
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
    api(project(":core"))
    // Plain api: a Gradle minimum, so a consumer's OkHttp 5.x wins conflict resolution. Never strictly()/BOM.
    api(libs.okhttp)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    // Legacy okhttp3.mockwebserver package only: it exists on both the 4.12 floor and 5.x.
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(testFixtures(project(":core")))
}

val engineGroup: String by rootProject.extra
val engineVersion: String by rootProject.extra

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = engineGroup
            artifactId = "voice-action-engine-providers"
            version = engineVersion
            from(components["java"])
        }
    }
}


// A1 matrix. The default `test` task is the 4.12.0 leg; each extra leg re-runs the SAME 4.12.0-compiled test classes on a
// swapped OkHttp runtime, which is exactly what a consumer on OkHttp 5.x does with our precompiled bytecode.
// The versions below are leg resolution overrides only, never main dependencies.
val okhttpLegs = mapOf("Okhttp521" to "5.2.1", "Okhttp550" to "5.5.0")
// Negative-control lever: -PvaeExpectedOkhttp=<v> overrides what the guard expects. It can only make a leg fail,
// because the guard compares it against the real runtime version.
val expectedOverride = providers.gradleProperty("vaeExpectedOkhttp")
val floorVersion = libs.versions.okhttp.get()

tasks.named<Test>("test") {
    systemProperty("expected.okhttp", expectedOverride.getOrElse(floorVersion))
    testLogging { showStandardStreams = true }
}

okhttpLegs.forEach { (legName, legVersion) ->
    val legClasspath = configurations.create("test${legName}RuntimeClasspath") {
        isCanBeConsumed = false
        isCanBeResolved = true
        extendsFrom(configurations.testRuntimeClasspath.get())
        // Explicit JVM attributes: OkHttp 5.x is published as KMP variants and selection is ambiguous without them.
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
            attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named(TargetJvmEnvironment.STANDARD_JVM))
        }
        // okhttp and mockwebserver must move together.
        resolutionStrategy.eachDependency {
            if (requested.group == "com.squareup.okhttp3" && (requested.name == "okhttp" || requested.name == "mockwebserver")) {
                useVersion(legVersion)
            }
        }
    }
    val sourceSets = the<SourceSetContainer>()
    val legTest = tasks.register<Test>("test$legName") {
        group = "verification"
        description = "Runs the 4.12.0-compiled test classes on OkHttp $legVersion (okhttp + mockwebserver swapped together)"
        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = sourceSets["test"].output + sourceSets["main"].output + legClasspath
        systemProperty("expected.okhttp", expectedOverride.getOrElse(legVersion))
        testLogging { showStandardStreams = true }
    }
    tasks.named("check") { dependsOn(legTest) }
}

apply(from = rootProject.file("gradle/invariants.gradle.kts"))
