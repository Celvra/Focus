plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.focuskit.android.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "fan.x0.focus.plugin.hello"
    compileSdk = 37

    defaultConfig {
        applicationId = "fan.x0.focus.plugin.hello"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }
}

dependencies {
    compileOnly(project(":plugin-sdk"))
    compileOnly(platform(libs.compose.bom))
    compileOnly(libs.compose.runtime)
    compileOnly(libs.compose.foundation)
    compileOnly(libs.compose.ui)
    compileOnly(libs.compose.material3)
    compileOnly(libs.compose.material.icons.core)
    compileOnly(libs.kotlinx.coroutines.core)
    compileOnly(libs.kotlinx.serialization.json)
}
