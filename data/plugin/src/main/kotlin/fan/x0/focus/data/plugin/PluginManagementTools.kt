package fan.x0.focus.data.plugin

import fan.x0.focus.data.assistant.ToolKind
import fan.x0.focus.data.assistant.ToolSpec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object PluginManagementTools {
    const val LIST = "plugin_list"
    const val SEARCH = "plugin_search"
    const val INSTALL = "plugin_install"
    const val CREATE = "plugin_create"
    const val ENABLE = "plugin_enable"
    const val DISABLE = "plugin_disable"
    const val UNINSTALL = "plugin_uninstall"
    const val RELOAD = "plugin_reload"
    const val LOGS = "plugin_logs"
    const val SET_REGISTRY = "plugin_set_registry"
    const val CAPABILITY = "plugin_capability"
    const val OPEN_PAGE = "plugin_open_page"

    val specs: List<ToolSpec> = listOf(
        ToolSpec(
            name = LIST,
            description = "List installed plugins, their state, tools, pages, skills and capabilities.",
            kind = ToolKind.Read,
            parameters = schema {},
        ),
        ToolSpec(
            name = SEARCH,
            description = "Search the plugin marketplace by name, id or description.",
            kind = ToolKind.Read,
            parameters = schema {
                put("query", string("Search text, empty lists everything"))
            },
        ),
        ToolSpec(
            name = INSTALL,
            description = "Install a plugin from the marketplace id or a direct URL. Requires approval.",
            kind = ToolKind.Gated,
            parameters = schema {
                put("id", string("Marketplace plugin id"))
                put("url", string("Direct .focusplugin or .apk URL"))
            },
        ),
        ToolSpec(
            name = CREATE,
            description = "Create and install a script plugin from a manifest and JavaScript source. Requires approval. The plugin can add tools, prompt text, skills and text pages.",
            kind = ToolKind.Gated,
            parameters = schema(required = listOf("manifest", "main_js")) {
                put("manifest", string("Plugin manifest JSON. runtime must be script, entry defaults to main.js"))
                put("main_js", string("JavaScript source. Define runTool(name, argsJson) and optionally onEvent(type, payloadJson)"))
            },
        ),
        ToolSpec(
            name = ENABLE,
            description = "Enable an installed plugin.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("id")) {
                put("id", string("Plugin id"))
            },
        ),
        ToolSpec(
            name = DISABLE,
            description = "Disable an installed plugin and unload its tools and pages.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("id")) {
                put("id", string("Plugin id"))
            },
        ),
        ToolSpec(
            name = UNINSTALL,
            description = "Uninstall a plugin and delete its data. Requires approval.",
            kind = ToolKind.Gated,
            parameters = schema(required = listOf("id")) {
                put("id", string("Plugin id"))
            },
        ),
        ToolSpec(
            name = RELOAD,
            description = "Reload one plugin after an update.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("id")) {
                put("id", string("Plugin id"))
            },
        ),
        ToolSpec(
            name = LOGS,
            description = "Read the recent log lines of one plugin.",
            kind = ToolKind.Read,
            parameters = schema(required = listOf("id")) {
                put("id", string("Plugin id"))
            },
        ),
        ToolSpec(
            name = SET_REGISTRY,
            description = "Set the marketplace index URL.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("url")) {
                put("url", string("Index JSON URL"))
            },
        ),
        ToolSpec(
            name = CAPABILITY,
            description = "Enable or disable a system capability for one plugin.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("id", "capability", "enabled")) {
                put("id", string("Plugin id"))
                put(
                    "capability",
                    string(PluginCapability.all.joinToString(", ")),
                )
                put("enabled", boolean("true to enable, false to disable"))
            },
        ),
        ToolSpec(
            name = OPEN_PAGE,
            description = "Open a plugin page in the app.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("id", "page")) {
                put("id", string("Plugin id"))
                put("page", string("Page id"))
                put("args", string("Optional page arguments"))
            },
        ),
    )

    private fun schema(required: List<String> = emptyList(), build: JsonObjectBuilder.() -> Unit): JsonObject =
        buildJsonObject {
            put("type", "object")
            putJsonObject("properties") { build() }
            if (required.isNotEmpty()) {
                putJsonArray("required") { required.forEach { add(JsonPrimitive(it)) } }
            }
            put("additionalProperties", false)
        }

    private fun string(description: String): JsonObject = buildJsonObject {
        put("type", "string")
        put("description", description)
    }

    private fun boolean(description: String): JsonObject = buildJsonObject {
        put("type", "boolean")
        put("description", description)
    }
}
