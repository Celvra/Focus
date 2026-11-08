package fan.x0.focus.data.assistant

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

data class PromptConfig(
    val main: String = "",
    val side: String = "",
)

private val Context.promptDataStore by preferencesDataStore(name = "prompt_settings")

@Single
class PromptSettings(@Provided private val context: Context) {
    private object Keys {
        val main = stringPreferencesKey("main_prompt")
        val side = stringPreferencesKey("side_prompt")
    }

    val config: Flow<PromptConfig> = context.promptDataStore.data.map { prefs ->
        PromptConfig(
            main = prefs[Keys.main].orEmpty(),
            side = prefs[Keys.side].orEmpty(),
        )
    }

    suspend fun configOnce(): PromptConfig = config.first()

    suspend fun setMain(value: String) {
        context.promptDataStore.edit { prefs -> prefs[Keys.main] = value.take(MAX_PROMPT_CHARS) }
    }

    suspend fun setSide(value: String) {
        context.promptDataStore.edit { prefs -> prefs[Keys.side] = value.take(MAX_PROMPT_CHARS) }
    }

    companion object {
        const val MAX_PROMPT_CHARS = 8_000
    }
}
