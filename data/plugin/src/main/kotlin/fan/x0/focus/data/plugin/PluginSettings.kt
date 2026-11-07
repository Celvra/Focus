package fan.x0.focus.data.plugin

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

private val Context.pluginDataStore by preferencesDataStore(name = "plugin_settings")

@Single
class PluginSettings(@Provided private val context: Context) {
    private object Keys {
        val registryUrl = stringPreferencesKey("registry_url")
        val developerMode = booleanPreferencesKey("developer_mode")
    }

    val registryUrl: Flow<String> = context.pluginDataStore.data.map { prefs ->
        prefs[Keys.registryUrl]?.takeIf { it.isNotBlank() } ?: DEFAULT_REGISTRY
    }

    val developerMode: Flow<Boolean> = context.pluginDataStore.data.map { prefs ->
        prefs[Keys.developerMode] ?: false
    }

    suspend fun registryUrlOnce(): String = registryUrl.first()

    suspend fun developerModeOnce(): Boolean = developerMode.first()

    suspend fun setRegistryUrl(url: String) {
        context.pluginDataStore.edit { prefs ->
            prefs[Keys.registryUrl] = url.trim()
        }
    }

    suspend fun setDeveloperMode(enabled: Boolean) {
        context.pluginDataStore.edit { prefs ->
            prefs[Keys.developerMode] = enabled
        }
    }

    companion object {
        const val DEFAULT_REGISTRY = "https://raw.githubusercontent.com/Celvra/awesome-focus/main/index.json"
    }
}
