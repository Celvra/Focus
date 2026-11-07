plugins {
    alias(libs.plugins.focuskit.android.library)
    alias(libs.plugins.focuskit.android.compose)
    alias(libs.plugins.focuskit.koin)
}

android {
    namespace = "fan.x0.focus.data.voice"
}

dependencies {
    implementation(projects.core.error)
    implementation(projects.core.network)
    implementation(projects.data.assistant)
    implementation(libs.koin.core)
    implementation(libs.koin.annotations)
    implementation(libs.koin.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.datastore.preferences)
    implementation(libs.commons.compress)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation("com.github.k2-fsa.sherpa-onnx:sherpa-onnx:1.13.8@aar")
    debugImplementation(libs.compose.ui.tooling)
}
