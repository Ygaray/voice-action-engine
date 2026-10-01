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
// A blank VERSION (other tools export that name too) is ignored rather than producing a publication with a blank version.
val engineVersion: String = providers.environmentVariable("VERSION")
    .map { it.trim() }
    .filter { it.isNotEmpty() }
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
            // testFixtures are consumed by later phases and instrumented tests run on a device, so both are linted
            // like main and test.
            source.setFrom(
                project.files(
                    "src/main/kotlin",
                    "src/test/kotlin",
                    "src/testFixtures/kotlin",
                    "src/androidTest/kotlin",
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
        // The skip above is correct only before the first release. Once a `v*` tag exists the dump must exist too, or the
        // additive-only API guarantee (§11 rule 2) would silently become a no-op while `check` stays green.
        // -PvaeAssumeReleased is a negative-control lever: it can only make this check stricter, never weaker.
        val assumeReleased = providers.gradleProperty("vaeAssumeReleased").isPresent
        val releaseTags = providers.exec {
            commandLine("git", "-C", rootProject.projectDir.path, "tag", "--list", "v*")
            isIgnoreExitValue = true
        }.standardOutput.asText.map { it.trim() }
        val verifyApiDumpPresent = tasks.register("verifyApiDumpPresent") {
            group = "verification"
            val dump = project.file("api.txt")
            val modulePath = project.path
            doLast {
                // No git binary / not a checkout: nothing to key on, so stay in the pre-release (skip) state.
                val tags = try { releaseTags.get() } catch (e: Exception) { "" }
                val released = assumeReleased || tags.isNotEmpty()
                if (released && !dump.isFile) {
                    throw GradleException(
                        "$modulePath: a release exists (${if (assumeReleased) "-PvaeAssumeReleased" else "tags: " + tags.lines().joinToString()})" +
                            " but api.txt is missing, so the additive-only API compatibility check would be skipped. Restore ${dump.name}.",
                    )
                }
            }
        }
        tasks.matching { it.name == "check" }.configureEach { dependsOn(verifyApiDumpPresent) }
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
