package fan.x0.focus.data.assistant

import kotlinx.serialization.Serializable

const val LEGACY_PROVIDER_ID = "default"

@Serializable
data class ProviderConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val model: String = "",
)

object ProviderCatalog {
    val presets = listOf(
        ProviderConfig(id = "openai", name = "OpenAI", baseUrl = "https://api.openai.com/v1"),
        ProviderConfig(id = "openrouter", name = "OpenRouter", baseUrl = "https://openrouter.ai/api/v1"),
        ProviderConfig(id = "deepseek", name = "DeepSeek", baseUrl = "https://api.deepseek.com/v1"),
    )

    fun isPreset(id: String): Boolean = presets.any { it.id == id }
}
