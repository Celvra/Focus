plugins {
    alias(libs.plugins.focuskit.android.library)
    alias(libs.plugins.focuskit.koin)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "fan.x0.focus.data.assistant"
}

dependencies {
    implementation(projects.core.error)
    implementation(projects.core.network)
    implementation(projects.data.sources)
    implementation(projects.data.bridge)
    implementation(libs.koin.core)
    implementation(libs.koin.annotations)
    implementation(libs.koin.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime)
    implementation(libs.jsoup)
    testImplementation(libs.junit)
}
