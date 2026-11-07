plugins {
    alias(libs.plugins.focuskit.android.library)
    alias(libs.plugins.focuskit.android.compose)
    alias(libs.plugins.focuskit.koin)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "fan.x0.focus.data.plugin"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("String", "COMPOSE_VERSION", "\"${libs.versions.compose.bom.get()}\"")
        buildConfigField("String", "KOTLIN_VERSION", "\"${libs.versions.kotlin.get()}\"")
    }
}

dependencies {
    implementation(project(":plugin-sdk"))
    implementation(projects.core.error)
    implementation(projects.core.network)
    implementation(projects.core.notify)
    implementation(projects.data.assistant)
    implementation(projects.data.sources)
    implementation(projects.data.im)
    implementation(projects.data.bridge)
    implementation(libs.koin.core)
    implementation(libs.koin.annotations)
    implementation(libs.koin.android)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.savedstate)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.datastore.preferences)
    implementation(libs.ktor.client.core)
    implementation(libs.work.runtime)
    implementation(libs.rhino)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
}
