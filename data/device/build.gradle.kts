plugins {
    alias(libs.plugins.focuskit.android.library)
    alias(libs.plugins.focuskit.koin)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "fan.x0.focus.data.device"

    buildFeatures {
        aidl = true
    }
}

dependencies {
    implementation(projects.core.error)
    implementation(projects.core.network)
    implementation(projects.core.notify)
    implementation(projects.data.assistant)
    implementation(libs.koin.core)
    implementation(libs.koin.annotations)
    implementation(libs.koin.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
    implementation(libs.datastore.preferences)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
}
