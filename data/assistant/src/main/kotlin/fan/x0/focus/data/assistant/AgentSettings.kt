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
    val retry: RetryConfig = RetryConfig(),
) {
    val roundLimit: Int get() = if (unlimited) Int.MAX_VALUE else maxRounds
}

data class RetryConfig(
    val maxRetries: Int = 4,
    val baseDelayMs: Long = 800,
    val maxDelayMs: Long = 16_000,
) {
    fun delayFor(attempt: Int, retryAfterSeconds: Long?): Long {
        if (retryAfterSeconds != null) return retryAfterSeconds.coerceIn(1, 120) * 1000
        val exponential = baseDelayMs * (1L shl (attempt - 1).coerceIn(0, 10))
        val capped = exponential.coerceAtMost(maxDelayMs)
        val jitter = (capped / 5).coerceAtLeast(1)
        return capped + kotlin.random.Random.nextLong(0, jitter)
    }
}

object AgentDefaults {
    const val MAX_ROUNDS = 8
    val ROUND_PRESETS = listOf(8, 16, 32, 64, 128)
    val RETRY_PRESETS = listOf(0, 2, 4, 6, 8)

    fun nearestRounds(value: Int): Int =
        ROUND_PRESETS.minByOrNull { abs(it - value) } ?: MAX_ROUNDS

    fun nearestRetries(value: Int): Int =
        RETRY_PRESETS.minByOrNull { abs(it - value) } ?: 4
}

private val Context.agentDataStore by preferencesDataStore(name = "agent_settings")

@Single
class AgentSettings(@Provided private val context: Context) {
    private object Keys {
        val maxRounds = intPreferencesKey("max_rounds")
        val unlimited = booleanPreferencesKey("unlimited_rounds")
        val maxRetries = intPreferencesKey("max_retries")
        val baseDelayMs = intPreferencesKey("retry_base_delay_ms")
    }

    val config: Flow<AgentConfig> = context.agentDataStore.data.map { prefs ->
        AgentConfig(
            maxRounds = AgentDefaults.nearestRounds(prefs[Keys.maxRounds] ?: AgentDefaults.MAX_ROUNDS),
            unlimited = prefs[Keys.unlimited] ?: false,
            retry = RetryConfig(
                maxRetries = AgentDefaults.nearestRetries(prefs[Keys.maxRetries] ?: 4),
                baseDelayMs = (prefs[Keys.baseDelayMs] ?: 800L.toInt()).coerceIn(100, 10_000).toLong(),
            ),
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

    suspend fun setMaxRetries(value: Int) {
        context.agentDataStore.edit { prefs ->
            prefs[Keys.maxRetries] = AgentDefaults.nearestRetries(value)
        }
    }
}
