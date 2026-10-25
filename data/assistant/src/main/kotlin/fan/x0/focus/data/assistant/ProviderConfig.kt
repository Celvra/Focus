package fan.x0.focus.data.assistant

import fan.x0.focus.core.network.openai.ChatProtocol
import kotlinx.serialization.Serializable

const val LEGACY_PROVIDER_ID = "default"

@Serializable
data class ProviderConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val model: String = "",
    val protocol: ChatProtocol = ChatProtocol.ChatCompletions,
)

object ProviderCatalog {
    val presets = listOf(
        ProviderConfig(id = "openai", name = "OpenAI", baseUrl = "https://api.openai.com/v1"),
        ProviderConfig(id = "openrouter", name = "OpenRouter", baseUrl = "https://openrouter.ai/api/v1"),
        ProviderConfig(id = "deepseek", name = "DeepSeek", baseUrl = "https://api.deepseek.com/v1"),
        ProviderConfig(
            id = "anthropic",
            name = "Anthropic",
            baseUrl = "https://api.anthropic.com/v1",
            protocol = ChatProtocol.AnthropicMessages,
        ),
    )

    fun isPreset(id: String): Boolean = presets.any { it.id == id }
}
