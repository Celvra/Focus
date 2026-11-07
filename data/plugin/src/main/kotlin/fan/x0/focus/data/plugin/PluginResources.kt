package fan.x0.focus.data.plugin

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import fan.x0.focus.plugin.PluginResources
import java.io.Closeable
import java.io.File

class LoadedPluginResources(
    val plugin: PluginResources,
    private val closeable: Closeable?,
) : Closeable {
    override fun close() {
        runCatching { closeable?.close() }
    }
}

object PluginResourcesLoader {
    fun load(context: Context, apk: File): LoadedPluginResources {
        val assets = runCatching {
            val manager = AssetManager::class.java.getDeclaredConstructor().newInstance()
            val method = AssetManager::class.java.getDeclaredMethod("addAssetPath", String::class.java)
            method.isAccessible = true
            val cookie = method.invoke(manager, apk.absolutePath) as Int
            if (cookie == 0) null else manager
        }.getOrNull()
        if (assets != null) {
            val resources = Resources(
                assets,
                context.resources.displayMetrics,
                context.resources.configuration,
            )
            return LoadedPluginResources(PluginResources(resources, assets), null)
        }
        val resources = Resources(
            context.assets,
            context.resources.displayMetrics,
            context.resources.configuration,
        )
        return LoadedPluginResources(PluginResources(resources, context.assets), null)
    }
}
