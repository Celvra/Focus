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

enum class AgentRole(
    val key: String,
    val fallbacks: List<AgentRole> = emptyList(),
) {
    Router("router"),
    WorkerLight("worker_light"),
    WorkerStandard("worker_standard"),
    WorkerHeavy("worker_heavy", listOf(WorkerStandard)),
    Subagent("subagent", listOf(WorkerLight)),
    Compactor("compactor", listOf(WorkerLight)),
    Watchdog("watchdog", listOf(WorkerLight)),
    Judge("judge", listOf(Router)),
    Title("title", listOf(WorkerLight)),
    Suggestion("suggestion", listOf(WorkerLight)),
    ;

    val needsTools: Boolean
        get() = this in setOf(Router, WorkerLight, WorkerStandard, WorkerHeavy, Subagent, Watchdog)

    val needsVision: Boolean
        get() = this in setOf(Router, WorkerLight, WorkerStandard, WorkerHeavy, Subagent)

    companion object {
        fun fromKey(value: String?): AgentRole? = entries.firstOrNull { it.key == value }
    }
}

@Serializable
data class RoleModelConfig(
    val providerId: String = "",
    val model: String = "",
    val reasoning: String = "",
) {
    val configured: Boolean get() = providerId.isNotBlank() && model.isNotBlank()
}

private val Context.roleModelDataStore by preferencesDataStore(name = "role_models")

@Single
class RoleModels(@Provided private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val serializer = ListSerializer(RoleModelEntry.serializer())

    val configs: Flow<Map<AgentRole, RoleModelConfig>> = context.roleModelDataStore.data.map { prefs ->
        val stored = prefs[Keys.configs]
            ?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
            .orEmpty()
        stored.mapNotNull { entry ->
            val role = AgentRole.fromKey(entry.role) ?: return@mapNotNull null
            role to RoleModelConfig(entry.providerId, entry.model, entry.reasoning)
        }.toMap()
    }

    suspend fun configsOnce(): Map<AgentRole, RoleModelConfig> = configs.first()

    suspend fun set(role: AgentRole, providerId: String, model: String, reasoning: String = "") {
        context.roleModelDataStore.edit { prefs ->
            val current = read(prefs).filterNot { it.role == role.key }
            val next = current + RoleModelEntry(role.key, providerId, model, reasoning)
            prefs[Keys.configs] = json.encodeToString(serializer, next)
        }
    }

    suspend fun clear(role: AgentRole) {
        context.roleModelDataStore.edit { prefs ->
            val next = read(prefs).filterNot { it.role == role.key }
            prefs[Keys.configs] = json.encodeToString(serializer, next)
        }
    }

    private fun read(prefs: androidx.datastore.preferences.core.Preferences): List<RoleModelEntry> =
        prefs[Keys.configs]
            ?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
            .orEmpty()

    private object Keys {
        val configs = stringPreferencesKey("role_models_json")
    }
}

@Serializable
private data class RoleModelEntry(
    val role: String,
    val providerId: String = "",
    val model: String = "",
    val reasoning: String = "",
)
