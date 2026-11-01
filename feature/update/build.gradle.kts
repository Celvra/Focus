plugins {
    alias(libs.plugins.focuskit.android.feature)
    alias(libs.plugins.focuskit.koin)
}

android {
    namespace = "fan.x0.focus.feature.update"
}

dependencies {
    implementation(projects.core.error)
    implementation(projects.core.designsystem)
    implementation(projects.data.assistant)
    implementation(libs.koin.core)
    implementation(libs.koin.annotations)
    implementation(libs.koin.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.kotlinx.coroutines.core)
}
