plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.metalava) apply false
}

// Publication coordinates, shared by every published module.
// JitPack exports VERSION per build; locally the engineVersion Gradle property is the default.
val engineGroup: String = providers.gradleProperty("engineGroup").get()
val engineVersion: String = providers.environmentVariable("VERSION")
    .orElse(providers.gradleProperty("engineVersion"))
    .get()
extra["engineGroup"] = engineGroup
extra["engineVersion"] = engineVersion

// Escape hatch: publish POM-only (no Gradle module metadata) if JitPack's .module rewriting misbehaves.
val disableModuleMetadata: Boolean =
    providers.gradleProperty("vaeDisableModuleMetadata").isPresent ||
        providers.environmentVariable("VAE_DISABLE_MODULE_METADATA").isPresent

subprojects {
    plugins.withId("io.gitlab.arturbosch.detekt") {
        extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
            buildUponDefaultConfig = true
            allRules = false
            config.setFrom(rootProject.file("config/detekt/detekt.yml"))
            // testFixtures are consumed by later phases, so they are linted like main and test.
            source.setFrom(
                project.files(
                    "src/main/kotlin",
                    "src/test/kotlin",
                    "src/testFixtures/kotlin",
                ),
            )
            // deliberately NO `baseline` property: zero-baseline policy, fix or tune, never bank debt
        }
    }
    plugins.withId("me.tylerbwong.gradle.metalava") {
        // Metalava hard-fails without api.txt, so the compat checks only run once a release commits the dump.
        tasks.matching { it.name.startsWith("metalavaCheckCompatibility") }.configureEach {
            onlyIf("api.txt exists") { project.file("api.txt").isFile }
        }
        tasks.register("apiDump") {
            group = "verification"
            dependsOn(tasks.matching { it.name.startsWith("metalavaGenerateSignature") && !it.name.contains("Debug") })
        }
        tasks.register("apiCheck") {
            group = "verification"
            dependsOn(tasks.matching { it.name.startsWith("metalavaCheckCompatibility") && !it.name.contains("Debug") })
        }
    }
    tasks.withType<GenerateModuleMetadata>().configureEach {
        enabled = !disableModuleMetadata
    }
}
