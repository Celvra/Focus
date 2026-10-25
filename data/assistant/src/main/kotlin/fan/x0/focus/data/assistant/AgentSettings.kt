package fan.x0.focus.data.assistant

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.math.abs

data class AgentConfig(
    val maxRounds: Int = AgentDefaults.MAX_ROUNDS,
    val unlimited: Boolean = false,
) {
    val roundLimit: Int get() = if (unlimited) Int.MAX_VALUE else maxRounds
}

object AgentDefaults {
    const val MAX_ROUNDS = 8
    val ROUND_PRESETS = listOf(8, 16, 32, 64, 128)

    fun nearestRounds(value: Int): Int =
        ROUND_PRESETS.minByOrNull { abs(it - value) } ?: MAX_ROUNDS
}

private val Context.agentDataStore by preferencesDataStore(name = "agent_settings")

@Single
class AgentSettings(@Provided private val context: Context) {
    private object Keys {
        val maxRounds = intPreferencesKey("max_rounds")
        val unlimited = booleanPreferencesKey("unlimited_rounds")
    }

    val config: Flow<AgentConfig> = context.agentDataStore.data.map { prefs ->
        AgentConfig(
            maxRounds = AgentDefaults.nearestRounds(prefs[Keys.maxRounds] ?: AgentDefaults.MAX_ROUNDS),
            unlimited = prefs[Keys.unlimited] ?: false,
        )
    }

    suspend fun configOnce(): AgentConfig = config.first()

    suspend fun setMaxRounds(value: Int) {
        context.agentDataStore.edit { prefs ->
            prefs[Keys.maxRounds] = AgentDefaults.nearestRounds(value)
        }
    }

    suspend fun setUnlimited(value: Boolean) {
        context.agentDataStore.edit { prefs -> prefs[Keys.unlimited] = value }
    }
}
