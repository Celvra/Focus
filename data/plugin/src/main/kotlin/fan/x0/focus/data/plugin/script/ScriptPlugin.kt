package fan.x0.focus.data.plugin.script

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fan.x0.focus.plugin.FocusPlugin
import fan.x0.focus.plugin.PluginContext
import fan.x0.focus.plugin.PluginEvent
import fan.x0.focus.plugin.PluginPage
import fan.x0.focus.plugin.PluginPlacement
import fan.x0.focus.plugin.PluginSkill
import fan.x0.focus.plugin.PluginTool
import fan.x0.focus.plugin.PluginToolKind
import fan.x0.focus.plugin.PluginScaffold
import fan.x0.focus.plugin.PluginToolResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class ScriptPlugin(
    private val context: PluginContext,
    source: String,
) : FocusPlugin {
    private val manifest get() = context.manifest
    private val runtime = ScriptRuntime(context, source)
    private val json = Json { ignoreUnknownKeys = true }

    override fun onCreate(context: PluginContext) = Unit

    override fun onStart() {
        runtime.call("onStart")
    }

    override fun onStop() {
        runtime.call("onStop")
    }

    override fun onDestroy() {
        runtime.call("onDestroy")
    }

    override fun tools(): List<PluginTool> = manifest.tools.map { spec ->
        PluginTool(
            name = spec.name,
            description = spec.description,
            kind = when (spec.kind.lowercase()) {
                "write" -> PluginToolKind.Write
                "gated" -> PluginToolKind.Gated
                else -> PluginToolKind.Read
            },
            parameters = spec.parameters.takeIf { it.isNotEmpty() } ?: buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {}
                put("additionalProperties", false)
            },
            sensitiveArgs = spec.sensitiveArgs.toSet(),
        ) { args ->
            runTool(spec.name, args)
        }
    }

    override fun pages(): List<PluginPage> = manifest.pages.map { page ->
        val placements = page.placement.mapNotNull { value ->
            when (value.lowercase()) {
                "drawer" -> PluginPlacement.Drawer
                "settings" -> PluginPlacement.Settings
                else -> null
            }
        }.toSet().ifEmpty { setOf(PluginPlacement.Settings) }
        PluginPage(
            id = page.id,
            title = page.title,
            placement = placements,
        ) {
            PluginScaffold(title = page.title, onBack = { back() }) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                    ) {
                        Text(page.title, style = MaterialTheme.typography.headlineSmall)
                        Text(page.body, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    override fun skills(): List<PluginSkill> = manifest.skills.map { skill ->
        PluginSkill(
            name = skill.name,
            description = skill.description,
            body = skill.body,
            files = skill.files,
        )
    }

    override suspend fun prompt(): String? = manifest.prompt.takeIf { it.isNotBlank() }

    override suspend fun onEvent(event: PluginEvent) {
        runtime.call("onEvent", listOf(event.type, event.payload.toString()))
    }

    override suspend fun onSchedule(jobId: String) {
        runtime.call("onSchedule", listOf(jobId))
    }

    private suspend fun runTool(name: String, args: JsonObject): PluginToolResult {
        val raw = runtime.call("runTool", listOf(name, args.toString()))
            ?: return PluginToolResult(false, "Script returned nothing", "Script error")
        val objectValue = runCatching {
            json.parseToJsonElement(raw) as? JsonObject
        }.getOrNull() ?: return PluginToolResult(true, raw, raw.take(120))
        val ok = (objectValue["ok"] as? JsonPrimitive)?.booleanOrNull ?: true
        val content = (objectValue["content"] as? JsonPrimitive)?.contentOrNull ?: raw
        val summary = (objectValue["summary"] as? JsonPrimitive)?.contentOrNull ?: content.take(120)
        return PluginToolResult(ok, content, summary)
    }
}
