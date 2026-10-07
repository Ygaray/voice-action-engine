pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // The speech-engine AAR is the only thing served from this repository: the filter admits that one exact group
        // and never the aggregator group, so no other coordinate can be resolved from here.
        exclusiveContent {
            forRepository { maven { url = uri("https://jitpack.io") } }
            filter { includeGroup("com.github.Ygaray.voice-engine-android") }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "voice-action-engine"

include(":core", ":providers", ":keystore", ":undo", ":voice-adapter", ":sample")
