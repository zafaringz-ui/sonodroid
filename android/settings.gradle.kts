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
