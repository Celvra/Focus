plugins {
    alias(libs.plugins.focuskit.android.library)
    alias(libs.plugins.focuskit.koin)
}

android {
    namespace = "fan.x0.focus.core.notify"
}

dependencies {
    implementation(libs.koin.core)
    implementation(libs.koin.annotations)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.datastore.preferences)
}
