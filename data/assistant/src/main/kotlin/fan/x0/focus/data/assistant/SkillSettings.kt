package fan.x0.focus.data.assistant

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

private val Context.skillDataStore by preferencesDataStore(name = "skill_settings")

@Single
class SkillSettings(@Provided private val context: Context) {
    private val key = stringSetPreferencesKey("disabled_ids")

    suspend fun disabledOnce(): Set<String> = context.skillDataStore.data.first()[key].orEmpty()

    suspend fun setEnabled(id: String, enabled: Boolean) {
        if (id.isBlank()) return
        context.skillDataStore.edit { prefs ->
            val current = prefs[key].orEmpty()
            prefs[key] = if (enabled) current - id else current + id
        }
    }
}
