package fan.x0.focus.data.sources

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

private val Context.sourceDataStore by preferencesDataStore(name = "sources")

data class SourceSettings(
    val fileTreeUri: String? = null,
    val refreshIntervalHours: Int = 6,
)

@Single
class SourcePreferences(@Provided private val context: Context) {
    private object Keys {
        val fileTree = stringPreferencesKey("file_tree_uri")
        val refreshHours = intPreferencesKey("refresh_interval_hours")
    }

    val settings: Flow<SourceSettings> = context.sourceDataStore.data.map { prefs ->
        SourceSettings(
            fileTreeUri = prefs[Keys.fileTree],
            refreshIntervalHours = prefs[Keys.refreshHours] ?: 6,
        )
    }

    suspend fun setFileTreeUri(uri: String?) {
        context.sourceDataStore.edit { prefs ->
            if (uri.isNullOrBlank()) prefs.remove(Keys.fileTree) else prefs[Keys.fileTree] = uri
        }
    }

    suspend fun setRefreshIntervalHours(hours: Int) {
        context.sourceDataStore.edit { prefs ->
            prefs[Keys.refreshHours] = hours.coerceIn(1, 24)
        }
    }
}
