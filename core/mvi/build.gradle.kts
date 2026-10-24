plugins {
    alias(libs.plugins.focuskit.android.library)
    alias(libs.plugins.focuskit.android.compose)
}

android {
    namespace = "fan.x0.focus.core.mvi"
}

dependencies {
    implementation(projects.core.error)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.core)
}
