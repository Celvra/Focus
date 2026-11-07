plugins {
    alias(libs.plugins.focuskit.android.feature)
    alias(libs.plugins.focuskit.koin)
}

android {
    namespace = "fan.x0.focus.feature.overlay"
}

dependencies {
    implementation(projects.core.error)
    implementation(projects.core.mvi)
    implementation(projects.core.designsystem)
    implementation(projects.core.notify)
    implementation(projects.data.assistant)
    implementation(projects.data.voice)
    implementation(projects.data.device)
    implementation(libs.koin.core)
    implementation(libs.koin.annotations)
    implementation(libs.koin.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.savedstate)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
}
