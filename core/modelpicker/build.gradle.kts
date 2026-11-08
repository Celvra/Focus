plugins {
    alias(libs.plugins.focuskit.android.library)
    alias(libs.plugins.focuskit.android.compose)
}

android {
    namespace = "fan.x0.focus.core.modelpicker"
}

dependencies {
    api(projects.core.designsystem)
    api(projects.data.assistant)
    implementation(projects.core.network)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)
}
