plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.focuskit.android.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.koin.compiler)
}

android {
    namespace = "fan.x0.focus"
    compileSdk = 37

    defaultConfig {
        applicationId = "fan.x0.focus"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(projects.core.mvi)
    implementation(projects.core.error)
    implementation(projects.core.designsystem)
    implementation(projects.core.network)
    implementation(projects.data.assistant)
    implementation(projects.data.sources)
    implementation(projects.data.bridge)
    implementation(projects.feature.onboarding)
    implementation(projects.feature.chat)
    implementation(projects.feature.today)
    implementation(projects.feature.goals)
    implementation(projects.feature.library)
    implementation(projects.feature.bridge)
    implementation(projects.feature.assistant)
    implementation(libs.koin.core)
    implementation(libs.koin.annotations)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.work.runtime)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
