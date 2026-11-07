plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.focuskit.android.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.koin.compiler)
}

val releaseKeystoreFile = System.getenv("FOCUS_KEYSTORE_FILE")
val releaseKeystorePassword = System.getenv("FOCUS_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("FOCUS_KEY_ALIAS")
val releaseKeyPassword = System.getenv("FOCUS_KEY_PASSWORD")
val releaseVersionName = System.getenv("FOCUS_VERSION_NAME")
val releaseVersionCode = System.getenv("FOCUS_VERSION_CODE")?.toIntOrNull()
val requestedAbis = providers.gradleProperty("focus.abis").orNull
val targetAbis = when (requestedAbis) {
    null -> listOf("arm64-v8a")
    "all" -> emptyList()
    else -> requestedAbis.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

android {
    namespace = "fan.x0.focus"
    compileSdk = 37

    defaultConfig {
        applicationId = "fan.x0.focus"
        minSdk = 26
        targetSdk = 37
        versionCode = releaseVersionCode ?: 2
        versionName = releaseVersionName ?: "1.0.1"
        if (targetAbis.isNotEmpty()) {
            ndk {
                abiFilters += targetAbis
            }
        }
    }

    signingConfigs {
        if (releaseKeystoreFile != null) {
            create("release") {
                storeFile = file(releaseKeystoreFile)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (releaseKeystoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    compileOnly(libs.libxposed.api)
    implementation(projects.core.mvi)
    implementation(projects.core.error)
    implementation(projects.core.designsystem)
    implementation(projects.core.network)
    implementation(projects.core.notify)
    implementation(projects.pluginSdk)
    implementation(projects.data.plugin)
    implementation(projects.data.assistant)
    implementation(projects.data.voice)
    implementation(projects.data.device)
    implementation(projects.data.sources)
    implementation(projects.data.bridge)
    implementation(projects.data.im)
    implementation(projects.feature.onboarding)
    implementation(projects.feature.chat)
    implementation(projects.feature.overlay)
    implementation(projects.feature.crew)
    implementation(projects.feature.today)
    implementation(projects.feature.goals)
    implementation(projects.feature.library)
    implementation(projects.feature.bridge)
    implementation(projects.feature.assistant)
    implementation(projects.feature.device)
    implementation(projects.feature.plugin)
    implementation(projects.feature.update)
    implementation(libs.koin.core)
    implementation(libs.koin.annotations)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.work.runtime)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
