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
            val jitpackToken = System.getenv("JITPACK_TOKEN") ?: ""
            if (jitpackToken.isNotEmpty()) {
                credentials(HttpHeaderCredentials::class) {
                    name = "Authorization"
                    value = "Token $jitpackToken"
                }
                authentication {
                    create<HttpHeaderAuthentication>("header")
                }
            }
        }
        maven { url = uri("https://api.xposed.info/") }
    }
}

rootProject.name = "AndroidControlCenterUltimate"
include(":app")
