package fan.x0.focus.data.plugin

import fan.x0.focus.data.assistant.ExternalToolProvider
import fan.x0.focus.data.assistant.ToolApprovalDraft
import fan.x0.focus.data.assistant.ToolKind
import fan.x0.focus.data.assistant.ToolResult
import fan.x0.focus.data.assistant.ToolSpec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.koin.core.annotation.Single

@Single
class PluginToolProvider(
    private val manager: PluginManager,
    private val registry: PluginRegistry,
    private val creator: PluginCreator,
) : ExternalToolProvider {
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = false }
    override fun specs(): List<ToolSpec> = PluginManagementTools.specs + registry.toolSpecs()

    override fun find(name: String): ToolSpec? =
        PluginManagementTools.specs.firstOrNull { it.name == name } ?: registry.findToolSpec(name)

    override suspend fun execute(name: String, args: JsonObject, conversationId: String): ToolResult? =
        when (name) {
            PluginManagementTools.LIST -> list()
            PluginManagementTools.SEARCH -> search(args)
            PluginManagementTools.INSTALL -> install(args)
            PluginManagementTools.CREATE -> create(args)
            PluginManagementTools.ENABLE -> toggle(args, true)
            PluginManagementTools.DISABLE -> toggle(args, false)
            PluginManagementTools.UNINSTALL -> uninstall(args)
            PluginManagementTools.RELOAD -> reload(args)
            PluginManagementTools.LOGS -> logs(args)
            PluginManagementTools.SET_REGISTRY -> setRegistry(args)
            PluginManagementTools.CAPABILITY -> capability(args)
            PluginManagementTools.OPEN_PAGE -> openPage(args)
            else -> registry.executeTool(name, args, conversationId)
        }

    override fun redactArguments(name: String, arguments: String): String? = registry.redact(name, arguments)

    override fun label(name: String?): String? =
        if (name in PluginManagementTools.specs.map { it.name }) "Plugins" else registry.toolLabel(name)

    override fun approval(name: String, args: JsonObject): ToolApprovalDraft? = when (name) {
        PluginManagementTools.INSTALL -> {
            val id = args.text("id")
            val url = args.text("url")
            if (id == null && url == null) return null
            ToolApprovalDraft(
                title = "Install plugin",
                summary = listOfNotNull(id?.let { "ID: $it" }, url?.let { "URL: $it" }).joinToString("\n"),
                label = "Install ${id ?: url}",
            )
        }
        PluginManagementTools.CREATE -> {
            val manifest = args.text("manifest") ?: return null
            val parsed = runCatching {
                json.decodeFromString(fan.x0.focus.plugin.PluginManifest.serializer(), manifest)
            }.getOrNull()
            ToolApprovalDraft(
                title = "Create plugin",
                summary = parsed?.let { "${it.name} ${it.version}" } ?: "New script plugin",
                label = "Create ${parsed?.id ?: "plugin"}",
            )
        }
        PluginManagementTools.UNINSTALL -> {
            val id = args.text("id") ?: return null
            ToolApprovalDraft(
                title = "Uninstall plugin",
                summary = "Uninstall $id and delete its data",
                label = "Uninstall: $id",
            )
        }
        else -> null
    }

    private suspend fun list(): ToolResult {
        val statuses = manager.statuses.value.values.sortedBy { it.manifest.name.lowercase() }
        val body = buildJsonObject {
            put("count", statuses.size)
            putJsonArray("plugins") {
                statuses.forEach { status ->
                    add(
                        buildJsonObject {
                            put("id", status.manifest.id)
                            put("name", status.manifest.name)
                            put("version", status.manifest.version)
                            put("version_code", status.manifest.versionCode)
                            put("enabled", status.enabled)
                            put("state", status.state.name.lowercase())
                            put("error", status.error)
                            put("source", status.source)
                            putJsonArray("capabilities") { status.capabilities.sorted().forEach { add(JsonPrimitive(it)) } }
                            putJsonArray("tools") { status.tools.forEach { add(JsonPrimitive(it)) } }
                            putJsonArray("pages") { status.pages.forEach { add(JsonPrimitive(it.pageId)) } }
                            putJsonArray("skills") { status.skills.forEach { add(JsonPrimitive(it)) } }
                        },
                    )
                }
            }
        }
        return ToolResult(ok = true, content = body.toString(), summary = "${statuses.size} plugins")
    }

    private suspend fun search(args: JsonObject): ToolResult {
        val entries = manager.listMarketplace(args.text("query").orEmpty())
        val body = buildJsonObject {
            put("count", entries.size)
            putJsonArray("plugins") {
                entries.forEach { entry ->
                    add(
                        buildJsonObject {
                            put("id", entry.id)
                            put("name", entry.name)
                            put("version", entry.version)
                            put("version_code", entry.versionCode)
                            put("summary", entry.summary)
                            put("author", entry.author)
                            put("download", entry.download)
                        },
                    )
                }
            }
        }
        return ToolResult(ok = true, content = body.toString(), summary = "${entries.size} marketplace plugins")
    }

    private suspend fun install(args: JsonObject): ToolResult {
        val url = args.text("url")
        if (url != null) {
            val installed = runCatching { manager.installUrl(url) }.getOrElse { return fail(it.message) }
            return ToolResult(ok = true, content = installed.manifest.id, summary = "Installed ${installed.manifest.name}")
        }
        val id = args.text("id") ?: return fail("id or url is required")
        val entry = runCatching { manager.listMarketplace(id).firstOrNull { it.id == id } }
            .getOrElse { return fail(it.message) }
            ?: return fail("Plugin $id is not in the marketplace")
        val installed = runCatching { manager.installMarket(entry) }.getOrElse { return fail(it.message) }
        return ToolResult(ok = true, content = installed.manifest.id, summary = "Installed ${installed.manifest.name}")
    }

    private suspend fun create(args: JsonObject): ToolResult {
        val manifestText = args.text("manifest") ?: return fail("manifest is required")
        val mainJs = args.text("main_js") ?: return fail("main_js is required")
        val manifest = runCatching {
            json.decodeFromString(fan.x0.focus.plugin.PluginManifest.serializer(), manifestText)
        }.getOrElse { return fail("Invalid manifest: ${it.message}") }
        val runtime = manifest.runtime.lowercase()
        val asScript = if (
            runtime == fan.x0.focus.plugin.PluginRuntimeKind.DEX &&
            manifest.mainClass.isBlank()
        ) {
            manifest.copy(runtime = fan.x0.focus.plugin.PluginRuntimeKind.SCRIPT)
        } else {
            manifest.copy(runtime = runtime)
        }
        val existingVersion = manager.status(asScript.id)?.manifest?.versionCode ?: 0
        val normalized = asScript.copy(
            name = asScript.name.ifBlank { asScript.id },
            version = asScript.version.ifBlank { "1.0.0" },
            versionCode = asScript.versionCode.takeIf { it > 0 } ?: (existingVersion + 1).coerceAtLeast(1),
        )
        if (normalized.runtime != fan.x0.focus.plugin.PluginRuntimeKind.SCRIPT) {
            return fail("Created plugins must use runtime script")
        }
        val installed = runCatching { creator.create(normalized, mainJs) }.getOrElse { return fail(it.message) }
        manager.setEnabled(installed.manifest.id, true)
        val status = manager.status(installed.manifest.id)
        return ToolResult(
            ok = status?.state == PluginRuntimeState.Loaded,
            content = status?.error.orEmpty().ifBlank { installed.manifest.id },
            summary = "Created ${installed.manifest.name}",
        )
    }

    private suspend fun toggle(args: JsonObject, enabled: Boolean): ToolResult {
        val id = args.text("id") ?: return fail("id is required")
        manager.setEnabled(id, enabled)
        return ToolResult(
            ok = true,
            content = id,
            summary = if (enabled) "Enabled $id" else "Disabled $id",
        )
    }

    private suspend fun uninstall(args: JsonObject): ToolResult {
        val id = args.text("id") ?: return fail("id is required")
        manager.uninstall(id)
        return ToolResult(ok = true, content = id, summary = "Uninstalled $id")
    }

    private suspend fun reload(args: JsonObject): ToolResult {
        val id = args.text("id") ?: return fail("id is required")
        manager.reload(id)
        val status = manager.status(id)
        return ToolResult(
            ok = status?.state == PluginRuntimeState.Loaded,
            content = status?.error.orEmpty(),
            summary = if (status?.state == PluginRuntimeState.Loaded) "Reloaded $id" else "Reload failed",
        )
    }

    private fun logs(args: JsonObject): ToolResult {
        val id = args.text("id") ?: return fail("id is required")
        val text = manager.logs(id)
        return ToolResult(ok = true, content = text.ifBlank { "(no logs)" }, summary = "Logs for $id")
    }

    private suspend fun setRegistry(args: JsonObject): ToolResult {
        val url = args.text("url") ?: return fail("url is required")
        manager.setRegistryUrl(url)
        return ToolResult(ok = true, content = url, summary = "Marketplace URL updated")
    }

    private suspend fun capability(args: JsonObject): ToolResult {
        val id = args.text("id") ?: return fail("id is required")
        val capability = args.text("capability") ?: return fail("capability is required")
        if (capability !in PluginCapability.all) return fail("Unknown capability: $capability")
        val enabled = args["enabled"] as? JsonPrimitive ?: return fail("enabled is required")
        val value = enabled.booleanOrNull ?: return fail("enabled must be true or false")
        manager.setCapability(id, capability, value)
        return ToolResult(ok = true, content = capability, summary = "Updated $capability for $id")
    }

    private fun openPage(args: JsonObject): ToolResult {
        val id = args.text("id") ?: return fail("id is required")
        val page = args.text("page") ?: return fail("page is required")
        PluginNavigation.open(id, page, args.text("args"))
        return ToolResult(ok = true, content = page, summary = "Opened $id/$page")
    }

    private fun fail(message: String?): ToolResult =
        ToolResult(ok = false, content = "Error: ${message.orEmpty()}", summary = message ?: "Plugin operation failed")

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}
