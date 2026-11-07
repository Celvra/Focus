package fan.x0.focus.data.plugin

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.AssetManager
import android.content.res.Resources
import java.io.File

class PluginContextWrapper(
    base: Context,
    private val pluginId: String,
    private val pluginResources: Resources,
    private val pluginClassLoader: ClassLoader,
    private val pluginFilesDir: File,
    private val pluginCacheDir: File,
) : ContextWrapper(base) {
    override fun getClassLoader(): ClassLoader = pluginClassLoader

    override fun getResources(): Resources = pluginResources

    override fun getAssets(): AssetManager = pluginResources.assets

    override fun getFilesDir(): File {
        pluginFilesDir.mkdirs()
        return pluginFilesDir
    }

    override fun getCacheDir(): File {
        pluginCacheDir.mkdirs()
        return pluginCacheDir
    }

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        baseContext.getSharedPreferences("plugin_${pluginId}_$name", mode)
}
