package fan.x0.focus.plugin

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import androidx.compose.runtime.staticCompositionLocalOf

class PluginResources(
    val resources: Resources,
    val assets: AssetManager,
)

interface PluginStorage {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
    fun keys(): Set<String>
}

interface PluginLogger {
    fun info(message: String)
    fun warn(message: String, error: Throwable? = null)
    fun error(message: String, error: Throwable? = null)
}

interface PluginContext {
    val manifest: PluginManifest
    val appContext: Context
    val resources: PluginResources
    val host: FocusHost
    val storage: PluginStorage
    val logger: PluginLogger
    val system: PluginSystemHost
    val filesDir: java.io.File
    val cacheDir: java.io.File
}

val LocalPluginContext = staticCompositionLocalOf<PluginContext?> { null }
