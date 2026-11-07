plugins {
    alias(libs.plugins.focuskit.android.library)
    alias(libs.plugins.focuskit.android.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "fan.x0.focus.plugin"
}

dependencies {
    api(projects.core.designsystem)
    api(projects.core.network)
    api(projects.core.notify)
    api(projects.data.assistant)
    api(projects.data.sources)
    api(projects.data.im)
    api(projects.data.bridge)
    api(platform(libs.compose.bom))
    api(libs.compose.runtime)
    api(libs.compose.foundation)
    api(libs.compose.ui)
    api(libs.compose.material3)
    api(libs.compose.material.icons.core)
    api(libs.androidx.lifecycle.runtime.compose)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(libs.ktor.client.core)
}
