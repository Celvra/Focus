rootProject.name = "Focus"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

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

includeBuild("build-logic")

include(":app")
include(":core:mvi")
include(":core:error")
include(":core:designsystem")
include(":core:network")
include(":data:assistant")
include(":data:sources")
include(":data:bridge")
include(":feature:onboarding")
include(":feature:chat")
include(":feature:today")
include(":feature:goals")
include(":feature:library")
include(":feature:bridge")
include(":feature:assistant")
