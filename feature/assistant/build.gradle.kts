plugins {
    alias(libs.plugins.focuskit.android.feature)
    alias(libs.plugins.focuskit.koin)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "fan.x0.focus.feature.assistant"
}

dependencies {
    implementation(projects.core.mvi)
    implementation(projects.core.error)
    implementation(projects.core.designsystem)
    implementation(projects.core.network)
    implementation(projects.data.assistant)
    implementation(projects.data.sources)
    implementation(libs.koin.core)
    implementation(libs.koin.annotations)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
