plugins {
    alias(libs.plugins.focuskit.android.library)
    alias(libs.plugins.focuskit.koin)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "fan.x0.focus.data.im"
}

dependencies {
    implementation(projects.core.error)
    implementation(projects.core.notify)
    implementation(projects.data.assistant)
    implementation(libs.koin.core)
    implementation(libs.koin.annotations)
    implementation(libs.koin.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.websockets)
    testImplementation(libs.junit)
}
