package fan.x0.focus.data.assistant

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

data class AgentEventConfig(
    val sources: Map<AgentEventSource, Boolean>,
    val speculative: Map<AgentEventSource, Boolean>,
    val maxConcurrentSideChats: Int,
    val watchdogEnabled: Boolean,
    val rateLimitPerMinute: Int,
) {
    fun enabled(source: AgentEventSource): Boolean = sources[source] ?: true

    fun speculative(source: AgentEventSource): Boolean = speculative[source] ?: false

    companion object {
        const val DEFAULT_MAX_CONCURRENT = 3
        const val DEFAULT_RATE_LIMIT = 60
        const val MAX_CONCURRENT_LIMIT = 8

        fun defaults(): AgentEventConfig = AgentEventConfig(
            sources = AgentEventSource.entries.associateWith { true },
            speculative = AgentEventSource.entries.associateWith {
                it == AgentEventSource.Background ||
                    it == AgentEventSource.Reminder ||
                    it == AgentEventSource.Feed ||
                    it == AgentEventSource.Device
            },
            maxConcurrentSideChats = DEFAULT_MAX_CONCURRENT,
            watchdogEnabled = true,
            rateLimitPerMinute = DEFAULT_RATE_LIMIT,
        )
    }
}

@Serializable
private data class AgentEventConfigPayload(
    val sources: Map<String, Boolean> = emptyMap(),
    val speculative: Map<String, Boolean> = emptyMap(),
    val maxConcurrentSideChats: Int = AgentEventConfig.DEFAULT_MAX_CONCURRENT,
    val watchdogEnabled: Boolean = true,
    val rateLimitPerMinute: Int = AgentEventConfig.DEFAULT_RATE_LIMIT,
)

private val Context.agentEventDataStore by preferencesDataStore(name = "agent_events")

@Single
class AgentEventSettings(@Provided private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    val config: Flow<AgentEventConfig> = context.agentEventDataStore.data.map { prefs ->
        val stored = prefs[Keys.config]
            ?.let { runCatching { json.decodeFromString(AgentEventConfigPayload.serializer(), it) }.getOrNull() }
            ?: AgentEventConfigPayload()
        val defaults = AgentEventConfig.defaults()
        AgentEventConfig(
            sources = defaults.sources.mapValues { (source, value) ->
                stored.sources[source.wire] ?: value
            },
            speculative = defaults.speculative.mapValues { (source, value) ->
                stored.speculative[source.wire] ?: value
            },
            maxConcurrentSideChats = stored.maxConcurrentSideChats
                .coerceIn(1, AgentEventConfig.MAX_CONCURRENT_LIMIT),
            watchdogEnabled = stored.watchdogEnabled,
            rateLimitPerMinute = stored.rateLimitPerMinute.coerceIn(5, 300),
        )
    }

    suspend fun configOnce(): AgentEventConfig = config.first()

    suspend fun setSourceEnabled(source: AgentEventSource, enabled: Boolean) = edit { payload ->
        payload.copy(sources = payload.sources + (source.wire to enabled))
    }

    suspend fun setSpeculative(source: AgentEventSource, enabled: Boolean) = edit { payload ->
        payload.copy(speculative = payload.speculative + (source.wire to enabled))
    }

    suspend fun setMaxConcurrentSideChats(value: Int) = edit { payload ->
        payload.copy(maxConcurrentSideChats = value.coerceIn(1, AgentEventConfig.MAX_CONCURRENT_LIMIT))
    }

    suspend fun setWatchdogEnabled(value: Boolean) = edit { payload ->
        payload.copy(watchdogEnabled = value)
    }

    suspend fun setRateLimit(value: Int) = edit { payload ->
        payload.copy(rateLimitPerMinute = value.coerceIn(5, 300))
    }

    private suspend fun edit(reduce: (AgentEventConfigPayload) -> AgentEventConfigPayload) {
        context.agentEventDataStore.edit { prefs ->
            val current = prefs[Keys.config]
                ?.let { runCatching { json.decodeFromString(AgentEventConfigPayload.serializer(), it) }.getOrNull() }
                ?: AgentEventConfigPayload()
            prefs[Keys.config] = json.encodeToString(AgentEventConfigPayload.serializer(), reduce(current))
        }
    }

    private object Keys {
        val config = stringPreferencesKey("agent_event_config_json")
    }
}
