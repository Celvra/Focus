package fan.x0.focus.data.assistant

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

data class AssistantConfig(
    val providers: List<ProviderConfig>,
    val activeProviderId: String,
    val onboardingDone: Boolean,
) {
    val activeProvider: ProviderConfig?
        get() = providers.firstOrNull { it.id == activeProviderId } ?: providers.firstOrNull()

    val baseUrl: String get() = activeProvider?.baseUrl ?: AssistantSettings.DEFAULT_BASE_URL
    val model: String get() = activeProvider?.model.orEmpty()
}

interface SecretStore {
    suspend fun apiKey(providerId: String = LEGACY_PROVIDER_ID): String?
    suspend fun setApiKey(providerId: String, value: String)
    suspend fun clearApiKey(providerId: String = LEGACY_PROVIDER_ID)
}

private val Context.assistantDataStore by preferencesDataStore(name = "assistant_settings")

@Single
class AssistantSettings(@Provided private val context: Context) {
    private object Keys {
        val providers = stringPreferencesKey("providers_json")
        val activeProviderId = stringPreferencesKey("active_provider_id")
        val baseUrl = stringPreferencesKey("base_url")
        val model = stringPreferencesKey("model")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val providersSerializer = ListSerializer(ProviderConfig.serializer())

    val config: Flow<AssistantConfig> = context.assistantDataStore.data.map { prefs ->
        val providers = readProviders(prefs)
        val active = prefs[Keys.activeProviderId]
            ?.takeIf { id -> providers.any { it.id == id } }
            ?: providers.firstOrNull { it.id == LEGACY_PROVIDER_ID && it.model.isNotBlank() }?.id
            ?: providers.first().id
        AssistantConfig(
            providers = providers,
            activeProviderId = active,
            onboardingDone = prefs[Keys.onboardingDone] ?: false,
        )
    }

    val onboardingDone: Flow<Boolean> = config.map { it.onboardingDone }

    suspend fun configOnce(): AssistantConfig = config.first()

    suspend fun updateProvider(provider: ProviderConfig, makeActive: Boolean = false) {
        context.assistantDataStore.edit { prefs ->
            val current = readProviders(prefs)
            val index = current.indexOfFirst { it.id == provider.id }
            val next = if (index >= 0) {
                current.toMutableList().also { it[index] = provider }
            } else {
                current + provider
            }
            prefs[Keys.providers] = json.encodeToString(providersSerializer, next)
            if (makeActive || prefs[Keys.activeProviderId].isNullOrBlank()) {
                prefs[Keys.activeProviderId] = provider.id
            }
        }
    }

    suspend fun setActiveProvider(providerId: String) {
        context.assistantDataStore.edit { prefs ->
            prefs[Keys.activeProviderId] = providerId
        }
    }

    suspend fun removeProvider(providerId: String) {
        context.assistantDataStore.edit { prefs ->
            val next = readProviders(prefs)
                .filterNot { it.id == providerId }
                .ifEmpty { ProviderCatalog.presets }
            prefs[Keys.providers] = json.encodeToString(providersSerializer, next)
            if (prefs[Keys.activeProviderId] == providerId || prefs[Keys.activeProviderId].isNullOrBlank()) {
                prefs[Keys.activeProviderId] = next.first().id
            }
        }
    }

    suspend fun setModel(baseUrl: String, model: String) {
        context.assistantDataStore.edit { prefs ->
            val current = readProviders(prefs)
            val activeId = prefs[Keys.activeProviderId] ?: current.first().id
            val next = current.map {
                if (it.id == activeId) {
                    it.copy(baseUrl = baseUrl.trim().trimEnd('/'), model = model.trim())
                } else {
                    it
                }
            }
            prefs[Keys.providers] = json.encodeToString(providersSerializer, next)
            if (prefs[Keys.activeProviderId].isNullOrBlank()) prefs[Keys.activeProviderId] = activeId
        }
    }

    suspend fun completeOnboarding() {
        context.assistantDataStore.edit { prefs -> prefs[Keys.onboardingDone] = true }
    }

    suspend fun resetOnboarding() {
        context.assistantDataStore.edit { prefs -> prefs[Keys.onboardingDone] = false }
    }

    private fun readProviders(prefs: Preferences): List<ProviderConfig> {
        val stored = prefs[Keys.providers]
            ?.let { runCatching { json.decodeFromString(providersSerializer, it) }.getOrNull() }
            ?.takeIf { it.isNotEmpty() }
        if (stored != null) return stored
        val legacyBase = prefs[Keys.baseUrl]
        val legacyModel = prefs[Keys.model].orEmpty()
        val legacy = if (legacyModel.isNotBlank() || (legacyBase != null && legacyBase != DEFAULT_BASE_URL)) {
            ProviderConfig(
                id = LEGACY_PROVIDER_ID,
                name = "Default",
                baseUrl = legacyBase ?: DEFAULT_BASE_URL,
                model = legacyModel,
            )
        } else {
            null
        }
        return listOfNotNull(legacy) + ProviderCatalog.presets
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
    }
}
