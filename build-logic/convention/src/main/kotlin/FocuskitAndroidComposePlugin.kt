import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

// Compose compiler for both app and library modules.
abstract class FocuskitAndroidComposePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "org.jetbrains.kotlin.plugin.compose")
            extensions.findByType(LibraryExtension::class.java)?.buildFeatures?.compose = true
            extensions.findByType(ApplicationExtension::class.java)?.buildFeatures?.compose = true
            extensions.configure<ComposeCompilerGradlePluginExtension> {
                stabilityConfigurationFiles.add(
                    rootProject.layout.projectDirectory.file("build-logic/compose-stability.conf"),
                )
            }
        }
    }
}
