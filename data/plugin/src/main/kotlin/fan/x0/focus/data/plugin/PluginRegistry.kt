package fan.x0.focus.data.plugin

import android.content.Context
import fan.x0.focus.data.assistant.PluginSkillContribution
import fan.x0.focus.data.assistant.ToolResult
import fan.x0.focus.data.assistant.ToolKind
import fan.x0.focus.data.assistant.ToolSpec
import fan.x0.focus.data.im.ImChannelFactory
import fan.x0.focus.data.im.ImChannelRegistry
import fan.x0.focus.plugin.FocusHost
import fan.x0.focus.plugin.FocusPlugin
import fan.x0.focus.plugin.PluginAccessibilityHandler
import fan.x0.focus.plugin.PluginApi
import fan.x0.focus.plugin.PluginBootHandler
import fan.x0.focus.plugin.PluginEvent
import fan.x0.focus.plugin.PluginNotificationHandler
import fan.x0.focus.plugin.PluginPage
import fan.x0.focus.plugin.PluginPageRef
import fan.x0.focus.plugin.PluginSource
import fan.x0.focus.plugin.PluginSystemHost
import fan.x0.focus.plugin.PluginTileHandler
import fan.x0.focus.plugin.PluginTool
import fan.x0.focus.plugin.PluginToolKind
import fan.x0.focus.plugin.PluginWidgetHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class PluginPageHandle(
    val plugin: LoadedPlugin,
    val page: PluginPage,
)

data class LoadedSource(
    val pluginId: String,
    val pluginName: String,
    val source: PluginSource,
)

@Single
class PluginRegistry(
    @Provided private val context: Context,
    private val store: PluginStore,
    private val loader: PluginLoader,
    private val logs: PluginLogStore,
    private val settings: PluginSettings,
    private val host: FocusHost,
    private val system: PluginSystemHost,
    private val channels: ImChannelRegistry,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val loaded = ConcurrentHashMap<String, LoadedPlugin>()
    private val channelMap = ConcurrentHashMap<String, List<ImChannelFactory>>()

    private val _statuses = MutableStateFlow<Map<String, PluginStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, PluginStatus>> = _statuses.asStateFlow()

    suspend fun loadAll() {
        mutex.withLock {
            val plugins = store.list()
            _statuses.value = plugins.associate { it.manifest.id to statusOf(it) }
            plugins.filter { it.enabled }.forEach { plugin ->
                if (!loaded.containsKey(plugin.manifest.id)) {
                    loadOne(plugin)
                }
            }
            val all = plugins.filter { it.enabled }.flatMap { it.capabilities }.toSet()
            PluginComponents.sync(context, all)
        }
        runCatching { fan.x0.focus.data.plugin.system.PluginTileService.current?.refresh() }
        runCatching { fan.x0.focus.data.plugin.system.PluginWidgetProvider.updateAll(context) }
    }

    suspend fun reload(id: String) = mutex.withLock {
        val installed = store.get(id) ?: return@withLock
        unloadLocked(id)
        if (installed.enabled) {
            loadOne(installed)
        } else {
            _statuses.update { it + (id to statusOf(installed)) }
        }
    }

    suspend fun setEnabled(id: String, enabled: Boolean) = mutex.withLock {
        val installed = store.get(id) ?: return@withLock
        store.setEnabled(id, enabled)
        val updated = installed.copy(enabled = enabled)
        if (enabled) {
            unloadLocked(id)
            loadOne(updated)
        } else {
            unloadLocked(id)
            _statuses.update { it + (id to statusOf(updated)) }
        }
        val all = _statuses.value.values
            .filter { it.enabled }
            .flatMap { it.capabilities }
            .toSet()
        PluginComponents.sync(context, all)
    }

    suspend fun setCapability(id: String, capability: String, enabled: Boolean) = mutex.withLock {
        val installed = store.get(id) ?: return@withLock
        val next = if (enabled) installed.capabilities + capability else installed.capabilities - capability
        store.setCapabilities(id, next)
        _statuses.update { it + (id to statusOf(installed.copy(capabilities = next))) }
        val all = _statuses.value.values
            .filter { it.enabled }
            .flatMap { it.capabilities }
            .toSet()
        PluginComponents.sync(context, all)
    }

    suspend fun uninstall(id: String) = mutex.withLock {
        val installed = store.get(id)
        unloadLocked(id)
        store.delete(id)
        logs.delete(id)
        _statuses.update { it - id }
        val all = _statuses.value.values
            .filter { it.enabled }
            .flatMap { it.capabilities }
            .toSet()
        PluginComponents.sync(context, all)
        if (installed == null) PluginComponents.disable(context, id)
    }

    fun status(id: String): PluginStatus? = _statuses.value[id]

    fun loadedPlugin(id: String): LoadedPlugin? = loaded[id]

    fun loadedPlugins(): List<LoadedPlugin> = loaded.values.sortedBy { it.manifest.name.lowercase() }

    fun toolSpecs(): List<ToolSpec> = loadedPlugins().flatMap { plugin ->
        plugin.tools.map { tool -> tool.toToolSpec(plugin) }
    }.take(PluginApi.MAX_TOTAL_TOOLS)

    fun findToolSpec(name: String): ToolSpec? {
        val (pluginId, toolName) = splitToolName(name) ?: return null
        val plugin = loaded[pluginId] ?: return null
        return plugin.tools.firstOrNull { it.name == toolName }?.toToolSpec(plugin)
    }

    fun toolLabel(name: String?): String? {
        val (pluginId, toolName) = splitToolName(name ?: return null) ?: return null
        val plugin = loaded[pluginId] ?: return null
        return "${plugin.manifest.name}: ${toolName.replace('_', ' ')}"
    }

    fun redact(name: String, arguments: String): String? {
        val (pluginId, toolName) = splitToolName(name) ?: return null
        val plugin = loaded[pluginId] ?: return null
        val tool = plugin.tools.firstOrNull { it.name == toolName } ?: return null
        if (tool.sensitiveArgs.isEmpty()) return arguments
        val objectArgs = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(arguments) as? JsonObject
        }.getOrNull() ?: return arguments
        if (objectArgs.keys.none { it in tool.sensitiveArgs }) return arguments
        val next = objectArgs.filterKeys { it !in tool.sensitiveArgs }
        return next.toString()
    }

    suspend fun executeTool(name: String, args: JsonObject, conversationId: String): ToolResult? {
        val (pluginId, toolName) = splitToolName(name) ?: return null
        val plugin = loaded[pluginId] ?: return null
        val tool = plugin.tools.firstOrNull { it.name == toolName } ?: return null
        return try {
            withTimeout(60_000) { tool.execute(args) }.let {
                ToolResult(ok = it.ok, content = it.content, summary = it.summary)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logs.write(pluginId, "E", "Tool $toolName failed", e)
            ToolResult(
                ok = false,
                content = "Plugin error: ${e.message ?: e.javaClass.simpleName}",
                summary = "Plugin error",
            )
        }
    }

    suspend fun promptSections(): List<String> = loadedPlugins().mapNotNull { plugin ->
        if (!plugin.installed.enabled) return@mapNotNull null
        val text = withTimeoutOrNull(2_000) { plugin.instance.prompt() }
            ?.trim()
            ?.take(PluginApi.MAX_PLUGIN_PROMPT_CHARS)
            ?.takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
        "Plugin ${plugin.manifest.name}:\n$text"
    }

    suspend fun skills(): List<PluginSkillContribution> = loadedPlugins().flatMap { plugin ->
        plugin.skills.map { skill ->
            PluginSkillContribution(
                pluginId = plugin.manifest.id,
                pluginName = plugin.manifest.name,
                name = skill.name,
                description = skill.description,
                body = skill.body,
                files = skill.files,
            )
        }
    }

    fun pageRefs(): List<PluginPageRef> = loadedPlugins().flatMap { it.pageRefs() }

    fun drawerPages(): List<PluginPageRef> =
        pageRefs().filter { fan.x0.focus.plugin.PluginPlacement.Drawer in it.placement }

    fun page(pluginId: String, pageId: String): PluginPageHandle? {
        val plugin = loaded[pluginId] ?: return null
        val page = plugin.pages.firstOrNull { it.id == pageId } ?: return null
        return PluginPageHandle(plugin, page)
    }

    fun dispatch(type: String, conversationId: String = "", payload: JsonObject = JsonObject(emptyMap())) {
        loadedPlugins().forEach { plugin ->
            scope.launch {
                runCatching {
                    withTimeout(5_000) {
                        plugin.instance.onEvent(PluginEvent(type, conversationId, payload))
                    }
                }.onFailure {
                    logs.write(plugin.manifest.id, "W", "Event $type failed", it)
                }
            }
        }
    }

    fun sources(): List<LoadedSource> = loadedPlugins().flatMap { plugin ->
        plugin.sources.map { LoadedSource(plugin.manifest.id, plugin.manifest.name, it) }
    }

    fun accessibilityHandlers(): List<Pair<LoadedPlugin, PluginAccessibilityHandler>> =
        handlers(PluginCapability.ACCESSIBILITY)

    fun notificationHandlers(): List<Pair<LoadedPlugin, PluginNotificationHandler>> =
        handlers(PluginCapability.NOTIFICATION_LISTENER)

    fun bootHandlers(): List<Pair<LoadedPlugin, PluginBootHandler>> =
        handlers(PluginCapability.BOOT)

    fun tileHandler(): Pair<LoadedPlugin, PluginTileHandler>? = handlers<PluginTileHandler>(PluginCapability.TILE).firstOrNull()

    fun widgetHandler(): Pair<LoadedPlugin, PluginWidgetHandler>? =
        handlers<PluginWidgetHandler>(PluginCapability.WIDGET).firstOrNull()

    fun capability(id: String, capability: String): Boolean =
        _statuses.value[id]?.capabilities?.contains(capability) == true

    fun appVersion(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    fun composeVersion(): String = BuildConfig.COMPOSE_VERSION

    fun kotlinVersion(): String = BuildConfig.KOTLIN_VERSION

    fun host(): FocusHost = host

    fun system(): PluginSystemHost = system

    fun log(): PluginLogStore = logs

    suspend fun runJob(pluginId: String, jobId: String) {
        val plugin = loaded[pluginId] ?: return
        runCatching {
            withTimeout(60_000) { plugin.instance.onSchedule(jobId) }
        }.onFailure {
            if (it is CancellationException) throw it
            logs.write(pluginId, "E", "Job $jobId failed", it)
        }
    }

    private suspend fun loadOne(installed: InstalledPlugin) {
        val id = installed.manifest.id
        val apk = File(context.filesDir, "plugins/$id/${installed.manifest.versionCode}/plugin.apk")
        if (!apk.isFile) {
            val message = "Plugin file is missing"
            logs.write(id, "E", message, null)
            store.setError(id, message)
            updateStatus(installed, PluginRuntimeState.Failed, message)
            return
        }
        updateStatus(installed, PluginRuntimeState.Loading, "")
        try {
            val plugin = loader.load(installed, host, system, apk)
            loaded[id] = plugin
            val factories = plugin.channels
            if (factories.isNotEmpty()) {
                channelMap[id] = factories
                factories.forEach { channels.register(it) }
            }
            runCatching { plugin.instance.onStart() }
            updateStatus(installed, PluginRuntimeState.Loaded, "")
        } catch (e: Throwable) {
            val message = "${e.javaClass.simpleName}: ${e.message.orEmpty()}"
            logs.write(id, "E", "Load failed", e)
            store.setError(id, message)
            updateStatus(installed, PluginRuntimeState.Failed, message)
        }
    }

    private fun unloadLocked(id: String) {
        runCatching { system.overlay.hide(id) }
        runCatching { system.foreground.stop(id) }
        val plugin = loaded.remove(id) ?: return
        runCatching { plugin.instance.onStop() }
        runCatching { plugin.instance.onDestroy() }
        channelMap.remove(id)?.forEach { channels.unregister(it) }
        runCatching { plugin.resources.close() }
    }

    private fun updateStatus(installed: InstalledPlugin, state: PluginRuntimeState, error: String) {
        _statuses.update { current ->
            current + (installed.manifest.id to statusOf(installed, state, error))
        }
    }

    private fun statusOf(
        installed: InstalledPlugin,
        state: PluginRuntimeState = if (loaded.containsKey(installed.manifest.id)) {
            PluginRuntimeState.Loaded
        } else if (!installed.enabled) {
            PluginRuntimeState.Disabled
        } else {
            PluginRuntimeState.Loading
        },
        error: String = installed.lastError,
    ): PluginStatus {
        val plugin = loaded[installed.manifest.id]
        return PluginStatus(
            manifest = installed.manifest,
            enabled = installed.enabled,
            source = installed.source,
            capabilities = installed.capabilities,
            state = state,
            error = error,
            tools = plugin?.tools?.map { "${installed.manifest.id}${PluginApi.TOOL_SEPARATOR}${it.name}" }.orEmpty(),
            pages = plugin?.pageRefs().orEmpty(),
            skills = plugin?.skills?.map { it.name }.orEmpty(),
        )
    }

    private fun PluginTool.toToolSpec(plugin: LoadedPlugin): ToolSpec = ToolSpec(
        name = "${plugin.manifest.id}${PluginApi.TOOL_SEPARATOR}$name",
        description = "${plugin.manifest.name}: $description",
        kind = when (kind) {
            PluginToolKind.Read -> ToolKind.Read
            PluginToolKind.Write -> ToolKind.Write
            PluginToolKind.Gated -> ToolKind.Gated
        },
        parameters = parameters,
    )

    private fun splitToolName(name: String): Pair<String, String>? {
        val index = name.indexOf(PluginApi.TOOL_SEPARATOR)
        if (index <= 0) return null
        return name.substring(0, index) to name.substring(index + PluginApi.TOOL_SEPARATOR.length)
    }

    private inline fun <reified T> handlers(capability: String): List<Pair<LoadedPlugin, T>> =
        loadedPlugins().mapNotNull { plugin ->
            if (capability !in plugin.installed.capabilities) return@mapNotNull null
            val handler = plugin.instance as? T ?: return@mapNotNull null
            plugin to handler
        }
}
