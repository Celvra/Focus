package fan.x0.focus.plugin

import androidx.compose.runtime.Composable
import kotlinx.serialization.json.JsonObject

enum class PluginToolKind { Read, Write, Gated }

data class PluginToolResult(
    val ok: Boolean,
    val content: String,
    val summary: String,
)

data class PluginTool(
    val name: String,
    val description: String,
    val kind: PluginToolKind,
    val parameters: JsonObject,
    val sensitiveArgs: Set<String> = emptySet(),
    val execute: suspend (JsonObject) -> PluginToolResult,
)

enum class PluginPlacement { Settings, Drawer }

data class PluginPage(
    val id: String,
    val title: String,
    val iconRes: Int? = null,
    val placement: Set<PluginPlacement> = setOf(PluginPlacement.Settings),
    val content: @Composable PluginPageScope.() -> Unit,
)

data class PluginSkill(
    val name: String,
    val description: String,
    val body: String,
    val files: Map<String, String> = emptyMap(),
)

data class PluginEvent(
    val type: String,
    val conversationId: String = "",
    val payload: JsonObject = JsonObject(emptyMap()),
)

data class PluginArticle(
    val title: String,
    val url: String,
    val content: String,
    val summary: String = "",
    val author: String = "",
    val publishedAt: Long = 0,
)

interface PluginSource {
    val id: String
    val name: String
    suspend fun fetch(): List<PluginArticle>
}

data class PluginWidgetContent(
    val title: String,
    val body: String,
    val icon: android.graphics.Bitmap? = null,
    val pageId: String? = null,
    val args: String? = null,
)

data class PluginTileContent(
    val label: String,
    val subtitle: String = "",
    val active: Boolean = false,
    val icon: android.graphics.Bitmap? = null,
)

interface PluginPageScope {
    val plugin: PluginContext
    val host: FocusHost
    val args: String?
    fun navigate(pageId: String, args: String? = null)
    fun back()
    fun close()
    fun snackbar(message: String)
}

data class PluginPageRef(
    val pluginId: String,
    val pluginName: String,
    val pageId: String,
    val title: String,
    val placement: Set<PluginPlacement>,
)
