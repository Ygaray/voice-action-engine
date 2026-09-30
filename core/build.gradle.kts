import java.io.File

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-test-fixtures`
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
    api(libs.coroutines.core)
    api(libs.serialization.json)
    testFixturesApi(libs.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(testFixtures(project(":core")))
}

// Keep testFixtures out of the published component (otherwise a -test-fixtures jar ships and the .module lists it).
val javaComponent = components["java"] as AdhocComponentWithVariants
javaComponent.withVariantsFromConfiguration(configurations["testFixturesApiElements"]) { skip() }
javaComponent.withVariantsFromConfiguration(configurations["testFixturesRuntimeElements"]) { skip() }

// Proves the variant exclusions above hold: nothing generated for publication may mention test-fixtures.
val verifyNoTestFixturesPublished = tasks.register("verifyNoTestFixturesPublished") {
    group = "verification"
    dependsOn("generateMetadataFileForReleasePublication", "generatePomFileForReleasePublication")
    val dir = layout.buildDirectory.dir("publications/release")
    doLast {
        val files = dir.get().asFile.listFiles { f -> f.name == "module.json" || f.name.endsWith(".xml") }.orEmpty()
        if (files.isEmpty()) throw GradleException("no generated publication metadata found (vacuous)")
        val leaks = files.filter { Regex("(?i)test-?fixtures").containsMatchIn(it.readText()) }.map { it.name }
        if (leaks.isNotEmpty()) throw GradleException("testFixtures leaked into the published component: $leaks")
    }
}

val engineGroup: String by rootProject.extra
val engineVersion: String by rootProject.extra

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = engineGroup
            artifactId = "voice-action-engine-core"
            version = engineVersion
            from(components["java"])
        }
    }
}

apply(from = rootProject.file("gradle/invariants.gradle.kts"))

// detekt negative control. The typed task lives here because applied script plugins cannot see the detekt classes.
val detektControls = tasks.register<io.gitlab.arturbosch.detekt.Detekt>("detektNegativeControls") {
    setSource(rootProject.file("config/negative-controls/detekt"))
    include("**/*.kt")
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    // The control is supposed to produce findings; the verify task compares them.
    ignoreFailures = true
    reports {
        xml.required.set(true)
        xml.outputLocation.set(layout.buildDirectory.file("reports/detekt-controls/detekt.xml"))
        html.required.set(false)
        txt.required.set(false)
        sarif.required.set(false)
        md.required.set(false)
    }
}

// Expected findings are derived from the control file itself as (1-based line, rule) pairs, so the comparison is exact
// set equality rather than a brittle count (one comment can yield several findings).
val verifyDetektControls = tasks.register("verifyDetektControls") {
    group = "verification"
    dependsOn(detektControls)
    val controlDir = rootProject.file("config/negative-controls/detekt")
    val xml = layout.buildDirectory.file("reports/detekt-controls/detekt.xml")
    doLast {
        val trackedRules = setOf("ForbiddenImport", "ForbiddenComment")
        val expected = mutableSetOf<Pair<Int, String>>()
        val expectedFile = File(controlDir, "ForbiddenImports.kt")
        expectedFile.readLines().forEachIndexed { index, line ->
            val trimmed = line.trim()
            when {
                line.startsWith("import ") -> expected.add(Pair(index + 1, "ForbiddenImport"))
                trimmed.startsWith("//") || trimmed.startsWith("/*") -> expected.add(Pair(index + 1, "ForbiddenComment"))
            }
        }
        if (expected.none { it.second == "ForbiddenImport" } || expected.none { it.second == "ForbiddenComment" }) {
            throw GradleException("detekt controls are vacuous: ${expectedFile.name} yields no expected import or comment pair")
        }
        val report = xml.get().asFile
        if (!report.isFile || report.readText().isBlank()) {
            throw GradleException("detekt controls are vacuous: no XML report at $report")
        }
        val errorPattern = Regex("""<error\s[^>]*?line="(\d+)"[^>]*?source="detekt\.([A-Za-z]+)"""")
        val actual = errorPattern.findAll(report.readText())
            .map { Pair(it.groupValues[1].toInt(), it.groupValues[2]) }
            .filter { it.second in trackedRules }
            .toSet()
        val missing = expected - actual
        val unexpected = actual - expected
        if (missing.isNotEmpty() || unexpected.isNotEmpty()) {
            throw GradleException(
                "detekt controls differ from expected (line, rule) set\n  missing: ${missing.sortedBy { it.first }}\n  unexpected: ${unexpected.sortedBy { it.first }}",
            )
        }
    }
}
tasks.named("check") { dependsOn(verifyDetektControls, verifyNoTestFixturesPublished) }
