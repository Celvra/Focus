package fan.x0.focus.plugin.hello

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusPrimaryButton
import fan.x0.focus.core.designsystem.components.FocusReveal
import fan.x0.focus.plugin.FocusPlugin
import fan.x0.focus.plugin.PluginContext
import fan.x0.focus.plugin.PluginPage
import fan.x0.focus.plugin.PluginPlacement
import fan.x0.focus.plugin.PluginScaffold
import fan.x0.focus.plugin.PluginTool
import fan.x0.focus.plugin.PluginToolKind
import fan.x0.focus.plugin.PluginToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class HelloPlugin : FocusPlugin {
    private lateinit var context: PluginContext

    override fun onCreate(context: PluginContext) {
        this.context = context
        context.logger.info("Hello plugin loaded")
    }

    override fun tools(): List<PluginTool> = listOf(
        PluginTool(
            name = "echo",
            description = "Return the text you pass in.",
            kind = PluginToolKind.Read,
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("text") {
                        put("type", "string")
                        put("description", "Text to return")
                    }
                }
                put("additionalProperties", false)
            },
        ) { args ->
            val text = (args["text"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
            PluginToolResult(
                ok = true,
                content = text.ifBlank { "Hello from a plugin" },
                summary = "Echoed ${text.length} characters",
            )
        },
    )

    override fun pages(): List<PluginPage> = listOf(
        PluginPage(
            id = "home",
            title = "Hello",
            placement = setOf(PluginPlacement.Drawer, PluginPlacement.Settings),
        ) {
            PluginScaffold(title = "Hello", onBack = { back() }) {
                item {
                    FocusReveal {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "This page comes from the Hello plugin.",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            FocusPrimaryButton(
                                text = "Show a message",
                                onClick = { snackbar("Hello from compose") },
                            )
                        }
                    }
                }
            }
        },
    )

    override suspend fun prompt(): String =
        "The Hello plugin is available. Use hello__echo to return text."
}
