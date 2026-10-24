import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply

// Composition of the library and Compose plugins for :feature:* modules.
abstract class FocuskitAndroidFeaturePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "focuskit.android.library")
            apply(plugin = "focuskit.android.compose")
        }
    }
}
