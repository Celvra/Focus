package fan.x0.focus.data.plugin

import android.content.Context
import dalvik.system.DexClassLoader
import fan.x0.focus.plugin.FocusHost
import fan.x0.focus.plugin.FocusPlugin
import fan.x0.focus.plugin.PluginManifest
import fan.x0.focus.plugin.PluginPage
import fan.x0.focus.plugin.PluginPageRef
import fan.x0.focus.plugin.PluginSkill
import fan.x0.focus.plugin.PluginSource
import fan.x0.focus.plugin.PluginSystemHost
import fan.x0.focus.plugin.PluginTool
import fan.x0.focus.plugin.PluginRuntimeKind
import fan.x0.focus.data.im.ImChannelFactory
import fan.x0.focus.data.plugin.script.ScriptPlugin
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.io.File
import java.util.zip.ZipFile

class LoadedPlugin(
    val installed: InstalledPlugin,
    val instance: FocusPlugin,
    val context: PluginContextImpl,
    val resources: LoadedPluginResources,
    val classLoader: ClassLoader,
    val tools: List<PluginTool>,
    val pages: List<PluginPage>,
    val skills: List<PluginSkill>,
    val channels: List<ImChannelFactory>,
    val sources: List<PluginSource>,
) {
    val manifest: PluginManifest get() = installed.manifest

    fun pageRefs(): List<PluginPageRef> = pages.map { page ->
        PluginPageRef(
            pluginId = manifest.id,
            pluginName = manifest.name,
            pageId = page.id,
            title = page.title,
            placement = page.placement,
        )
    }
}

@Single
class PluginLoader(
    @Provided private val context: Context,
    private val logs: PluginLogStore,
) {
    fun load(
        installed: InstalledPlugin,
        host: FocusHost,
        system: PluginSystemHost,
        apk: File,
    ): LoadedPlugin {
        val manifest = installed.manifest
        val root = File(context.filesDir, "plugins/${manifest.id}/${manifest.versionCode}")
        val dataDir = File(context.filesDir, "plugins/${manifest.id}/data")
        val cacheDir = File(context.cacheDir, "plugins/${manifest.id}")
        dataDir.mkdirs()
        cacheDir.mkdirs()
        val optimized = File(context.codeCacheDir, "plugins/${manifest.id}/${manifest.versionCode}")
        optimized.mkdirs()
        val resources = PluginResourcesLoader.load(context, apk)
        val parent = context.classLoader
        val classLoader = if (manifest.runtime == PluginRuntimeKind.SCRIPT) {
            parent
        } else {
            DexClassLoader(apk.absolutePath, optimized.absolutePath, null, parent)
        }
        val pluginContext = PluginContextImpl(
            manifest = manifest,
            appContext = PluginContextWrapper(
                base = context,
                pluginId = manifest.id,
                pluginResources = resources.plugin.resources,
                pluginClassLoader = classLoader,
                pluginFilesDir = dataDir,
                pluginCacheDir = cacheDir,
            ),
            resources = resources.plugin,
            host = host,
            storage = PluginStorageImpl(context, manifest.id),
            logger = logs.logger(manifest.id),
            system = system,
            filesDir = dataDir,
            cacheDir = cacheDir,
        )
        val instance = if (manifest.runtime == PluginRuntimeKind.SCRIPT) {
            ScriptPlugin(pluginContext, readScript(apk, manifest.entry))
        } else {
            val type = Class.forName(manifest.mainClass, true, classLoader)
            type.getDeclaredConstructor().newInstance() as? FocusPlugin
                ?: error("${manifest.mainClass} does not implement FocusPlugin")
        }
        instance.onCreate(pluginContext)
        return LoadedPlugin(
            installed = installed,
            instance = instance,
            context = pluginContext,
            resources = resources,
            classLoader = classLoader,
            tools = instance.tools().take(20),
            pages = instance.pages(),
            skills = instance.skills(),
            channels = instance.channels(),
            sources = instance.sources(),
        )
    }

    private fun readScript(apk: File, entry: String): String = ZipFile(apk).use { zip ->
        val target = zip.getEntry("assets/$entry") ?: zip.getEntry(entry)
            ?: error("Script entry is missing")
        zip.getInputStream(target).bufferedReader().use { it.readText() }
    }
}
