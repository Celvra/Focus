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
        maven("https://jitpack.io")
    }
}

includeBuild("build-logic")

include(":app")
include(":plugin-sdk")
include(":data:plugin")
include(":feature:plugin")
include(":core:mvi")
include(":core:error")
include(":core:designsystem")
include(":core:network")
include(":core:notify")
include(":data:assistant")
include(":data:voice")
include(":data:device")
include(":data:sources")
include(":data:bridge")
include(":data:im")
include(":feature:onboarding")
include(":feature:chat")
include(":feature:overlay")
include(":feature:crew")
include(":feature:today")
include(":feature:goals")
include(":feature:library")
include(":feature:bridge")
include(":feature:assistant")
include(":feature:device")
include(":feature:update")
include(":samples:plugin-hello")
