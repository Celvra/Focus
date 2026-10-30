package fan.x0.focus.data.assistant

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

enum class WebSearchProvider {
    DeepSeek,
    Tavily,
    TinyFish,
    Exa,
    Bing,
    DuckDuckGo,
    Yandex,
    ;

    val id: String get() = name.lowercase()

    val requiresKey: Boolean
        get() = this in setOf(DeepSeek, Tavily, TinyFish, Exa)

    companion object {
        fun fromId(value: String): WebSearchProvider? =
            entries.firstOrNull { it.id == value.lowercase() || it.name.equals(value, ignoreCase = true) }
    }
}

@Serializable
data class WebSearchProviderConfig(
    val provider: WebSearchProvider,
    val enabled: Boolean = true,
)

@Serializable
data class WebSearchConfig(
    val defaultProvider: WebSearchProvider = WebSearchProvider.DeepSeek,
    val providers: List<WebSearchProviderConfig> = WebSearchProvider.entries.map { WebSearchProviderConfig(it) },
) {
    fun enabled(provider: WebSearchProvider): Boolean =
        providers.firstOrNull { it.provider == provider }?.enabled ?: true

    fun normalized(): WebSearchConfig {
        val known = providers.map { it.provider }.distinct()
        return copy(
            providers = providers.distinctBy { it.provider } +
                WebSearchProvider.entries.filterNot { it in known }.map { WebSearchProviderConfig(it) },
        )
    }
}

private val Context.webSearchDataStore by preferencesDataStore(name = "web_search_settings")

@Single
class WebSearchSettings(@Provided private val context: Context) {
    private val key = stringPreferencesKey("config_json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val config: Flow<WebSearchConfig> = context.webSearchDataStore.data.map { prefs ->
        prefs[key]?.let { raw ->
            runCatching { json.decodeFromString(WebSearchConfig.serializer(), raw) }
                .getOrNull()
                ?.normalized()
        } ?: WebSearchConfig()
    }

    suspend fun configOnce(): WebSearchConfig = config.first()

    suspend fun setDefault(provider: WebSearchProvider) {
        context.webSearchDataStore.edit { prefs ->
            val current = read(prefs[key])
            prefs[key] = json.encodeToString(
                WebSearchConfig.serializer(),
                current.copy(defaultProvider = provider).normalized(),
            )
        }
    }

    suspend fun setEnabled(provider: WebSearchProvider, enabled: Boolean) {
        context.webSearchDataStore.edit { prefs ->
            val current = read(prefs[key]).normalized()
            val next = current.copy(
                providers = current.providers.map {
                    if (it.provider == provider) it.copy(enabled = enabled) else it
                },
            )
            prefs[key] = json.encodeToString(WebSearchConfig.serializer(), next)
        }
    }

    private fun read(raw: String?): WebSearchConfig = raw
        ?.let { runCatching { json.decodeFromString(WebSearchConfig.serializer(), it) }.getOrNull() }
        ?.normalized()
        ?: WebSearchConfig()
}

object WebSearchSecrets {
    fun key(provider: WebSearchProvider): String = "web_search_${provider.id}"
}
