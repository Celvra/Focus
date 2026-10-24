import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply

// Koin compiler plugin, annotation flavour.
abstract class FocuskitKoinPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "io.insert-koin.compiler.plugin")
        }
    }
}
