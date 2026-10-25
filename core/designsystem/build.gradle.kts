plugins {
    alias(libs.plugins.focuskit.android.library)
    alias(libs.plugins.focuskit.android.compose)
}

android {
    namespace = "fan.x0.focus.core.designsystem"
}

dependencies {
    implementation(projects.core.error)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.haze)
    implementation(libs.haze.blur)
    debugImplementation(libs.compose.ui.tooling)
}
