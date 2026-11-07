package fan.x0.focus.data.assistant

import kotlinx.serialization.json.JsonObject

class ToolProviderGroup(private val providers: List<ExternalToolProvider>) {
    fun specs(): List<ToolSpec> = providers.flatMap { it.specs() }

    fun find(name: String): ToolSpec? =
        providers.firstNotNullOfOrNull { it.find(name) }

    suspend fun execute(name: String, args: JsonObject, conversationId: String): ToolResult? {
        for (provider in providers) {
            provider.execute(name, args, conversationId)?.let { return it }
        }
        return null
    }

    fun redactArguments(name: String, arguments: String): String? {
        for (provider in providers) {
            provider.redactArguments(name, arguments)?.let { return it }
        }
        return null
    }

    fun label(name: String?): String? =
        providers.firstNotNullOfOrNull { it.label(name) }

    fun approval(name: String, args: JsonObject): ToolApprovalDraft? =
        providers.firstNotNullOfOrNull { it.approval(name, args) }
}
