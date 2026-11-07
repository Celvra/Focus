package fan.x0.focus.data.plugin

import android.content.Context
import fan.x0.focus.plugin.FocusHost
import fan.x0.focus.plugin.PluginContext
import fan.x0.focus.plugin.PluginLogger
import fan.x0.focus.plugin.PluginManifest
import fan.x0.focus.plugin.PluginResources
import fan.x0.focus.plugin.PluginStorage
import fan.x0.focus.plugin.PluginSystemHost
import java.io.File

class PluginStorageImpl(
    context: Context,
    pluginId: String,
) : PluginStorage {
    private val prefs = context.getSharedPreferences("plugin_${pluginId}_storage", Context.MODE_PRIVATE)

    override fun get(key: String): String? = prefs.getString(key, null)

    override fun put(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun keys(): Set<String> = prefs.all.keys
}

class PluginContextImpl(
    override val manifest: PluginManifest,
    override val appContext: Context,
    override val resources: PluginResources,
    override val host: FocusHost,
    override val storage: PluginStorage,
    override val logger: PluginLogger,
    override val system: PluginSystemHost,
    override val filesDir: File,
    override val cacheDir: File,
) : PluginContext
