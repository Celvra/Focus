plugins {
    `kotlin-dsl`
}

group = "fan.x0.focus.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.android.gradleApi)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.compilerPlugin)
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "focuskit.android.library"
            implementationClass = "FocuskitAndroidLibraryPlugin"
        }
        register("androidCompose") {
            id = "focuskit.android.compose"
            implementationClass = "FocuskitAndroidComposePlugin"
        }
        register("androidFeature") {
            id = "focuskit.android.feature"
            implementationClass = "FocuskitAndroidFeaturePlugin"
        }
        register("koin") {
            id = "focuskit.koin"
            implementationClass = "FocuskitKoinPlugin"
        }
    }
}
