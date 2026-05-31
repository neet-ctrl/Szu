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
        maven {
            url = uri("https://jitpack.io")
            credentials {
                // GitHub token passed via JITPACK_TOKEN env var in CI (prevents JitPack 401 on shared runners)
                username = System.getenv("JITPACK_TOKEN") ?: ""
            }
        }
        maven { url = uri("https://api.xposed.info/") }
    }
}

rootProject.name = "AndroidControlCenterUltimate"
include(":app")
