package fan.x0.focus.data.assistant

import fan.x0.focus.core.network.ProviderAuth
import fan.x0.focus.core.network.ProviderHeader
import fan.x0.focus.core.network.ProviderRequestConfig
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
    val requiresKey: Boolean = true,
    val userAgent: String = "",
    val headers: List<ProviderHeader> = emptyList(),
    val auth: ProviderAuth = ProviderAuth(),
    val icon: String = "",
)

fun ProviderConfig.requestConfig(apiKey: String): ProviderRequestConfig = ProviderRequestConfig(
    apiKey = apiKey,
    auth = auth,
    userAgent = userAgent,
    headers = headers,
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
        ProviderConfig(
            id = PolicyDefaults.JUDGE_PROVIDER_ID,
            name = "TypeSafe",
            baseUrl = "https://api.typesafe.ai/v1",
            model = PolicyDefaults.JEV_MODEL,
        ),
    )

    fun isPreset(id: String): Boolean = presets.any { it.id == id }
}
