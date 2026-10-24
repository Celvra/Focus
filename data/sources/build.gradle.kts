plugins {
    alias(libs.plugins.focuskit.android.library)
    alias(libs.plugins.focuskit.koin)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "fan.x0.focus.data.sources"
}

dependencies {
    implementation(projects.core.error)
    implementation(projects.core.network)
    implementation(libs.koin.core)
    implementation(libs.koin.annotations)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.jsoup)
    implementation(libs.work.runtime)
    implementation(libs.androidx.documentfile)
    implementation(libs.datastore.preferences)
    testImplementation(libs.junit)
    testImplementation(libs.kxml2)
}
