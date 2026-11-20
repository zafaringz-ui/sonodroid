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
        google()
        // JNA is published only to Maven Central, and the artifact we need is the `.aar`
        // (it carries libjnidispatch.so for each ABI), not the jar. Pinning the group
        // guarantees `mavenCentral()` is the repository that gets asked, so the group can
        // never be resolved from, or cached as missing by, another repository.
        exclusiveContent {
            forRepository { mavenCentral() }
            filter { includeGroup("net.java.dev.jna") }
        }
        mavenCentral()
    }
}

rootProject.name = "sonora-android"

include(
    ":app",
    ":core-bridge",
    ":design-system",
    ":feature-auth",
    ":feature-home",
    ":feature-library",
    ":feature-player",
    ":feature-search",
    ":feature-settings",
    ":platform-secure",
    ":platform-webview",
    ":service-playback",
)
