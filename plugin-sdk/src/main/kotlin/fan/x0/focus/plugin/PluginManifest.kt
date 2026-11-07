package fan.x0.focus.plugin

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class PluginJob(
    val id: String,
    val intervalMinutes: Int = 60,
    val requiresNetwork: Boolean = true,
)

@Serializable
data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val versionCode: Long,
    val apiVersion: Int,
    val mainClass: String = "",
    val runtime: String = "dex",
    val entry: String = "main.js",
    val description: String = "",
    val author: String = "",
    val homepage: String = "",
    val minHostVersion: String = "0.0.0",
    val composeVersion: String = "",
    val kotlinVersion: String = "",
    val jobs: List<PluginJob> = emptyList(),
    val tools: List<PluginToolManifest> = emptyList(),
    val prompt: String = "",
    val skills: List<PluginSkillManifest> = emptyList(),
    val pages: List<PluginPageManifest> = emptyList(),
)

@Serializable
data class PluginToolManifest(
    val name: String,
    val description: String,
    val kind: String = "read",
    val parameters: JsonObject = JsonObject(emptyMap()),
    val sensitiveArgs: List<String> = emptyList(),
)

@Serializable
data class PluginSkillManifest(
    val name: String,
    val description: String,
    val body: String,
    val files: Map<String, String> = emptyMap(),
)

@Serializable
data class PluginPageManifest(
    val id: String,
    val title: String,
    val body: String = "",
    val placement: List<String> = listOf("settings"),
)

object PluginRuntimeKind {
    const val DEX = "dex"
    const val SCRIPT = "script"
}

object PluginApi {
    const val VERSION = 1
    const val MANIFEST_ENTRY = "assets/focus-plugin.json"
    const val TOOL_SEPARATOR = "__"
    const val MAX_PLUGIN_TOOLS = 20
    const val MAX_TOTAL_TOOLS = 64
    const val MAX_PROMPT_CHARS = 4000
    const val MAX_PLUGIN_PROMPT_CHARS = 2000
}
