package fan.x0.focus.data.assistant

import kotlinx.serialization.json.JsonObject

data class ToolApprovalDraft(
    val title: String,
    val summary: String,
    val label: String,
)

interface ExternalToolProvider {
    fun specs(): List<ToolSpec>
    fun find(name: String): ToolSpec?
    suspend fun execute(name: String, args: JsonObject, conversationId: String): ToolResult?
    fun redactArguments(name: String, arguments: String): String?
    fun label(name: String?): String?
    fun approval(name: String, args: JsonObject): ToolApprovalDraft?
}

interface ExternalPromptProvider {
    suspend fun sections(): List<String>
}

interface AssistantEventSink {
    fun emit(type: String, conversationId: String = "", payload: JsonObject = JsonObject(emptyMap()))
}

interface PluginSkillProvider {
    suspend fun skills(): List<PluginSkillContribution>
}

data class PluginSkillContribution(
    val pluginId: String,
    val pluginName: String,
    val name: String,
    val description: String,
    val body: String,
    val files: Map<String, String> = emptyMap(),
)
