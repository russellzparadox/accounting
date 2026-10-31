pluginManagement {
    repositories {
        google()
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

rootProject.name = "accounting"

include(":app")
include(":core:domain")
include(":core:data")
include(":core:ui")
include(":core:persian")
include(":feature:entry")
include(":feature:ledger")
include(":feature:reports")
include(":feature:settings")
