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

rootProject.name = "HeartRateMonitorMobile"
include(":app")
include(":baselineprofile")
include(":core:model")
include(":core:designsystem")
include(":core:ui")
include(":data:settings")
include(":data:database")
include(":data:repository")
include(":service")
include(":feature:favorite")
include(":feature:webhook")
include(":feature:history")
include(":feature:alarm")
include(":feature:server")
include(":feature:settings")
include(":feature:main")
