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
import java.util.UUID

enum class McpTransport {
    StreamableHttp,
    Sse,
    Stdio,
    ;

    val wire: String
        get() = when (this) {
            StreamableHttp -> "http"
            Sse -> "sse"
            Stdio -> "stdio"
        }

    companion object {
        fun fromWire(value: String): McpTransport = when (value.lowercase()) {
            "sse" -> Sse
            "stdio" -> Stdio
            else -> StreamableHttp
        }
    }
}

@Serializable
data class McpHeader(
    val name: String,
    val value: String = "",
    val secret: Boolean = false,
)

@Serializable
data class McpServerConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val transport: McpTransport = McpTransport.StreamableHttp,
    val url: String = "",
    val command: String = "",
    val args: List<String> = emptyList(),
    val env: List<McpHeader> = emptyList(),
    val headers: List<McpHeader> = emptyList(),
    val enabled: Boolean = true,
) {
    val endpoint: String get() = url.trim().trimEnd('/')
}

@Serializable
data class McpConfig(
    val servers: List<McpServerConfig> = emptyList(),
)

private val Context.mcpDataStore by preferencesDataStore(name = "mcp_settings")

@Single
class McpSettings(@Provided private val context: Context) {
    private val key = stringPreferencesKey("config_json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val config: Flow<McpConfig> = context.mcpDataStore.data.map { prefs ->
        prefs[key]?.let { raw ->
            runCatching { json.decodeFromString(McpConfig.serializer(), raw) }.getOrNull()
        } ?: McpConfig()
    }

    suspend fun configOnce(): McpConfig = config.first()

    suspend fun upsert(server: McpServerConfig) {
        context.mcpDataStore.edit { prefs ->
            val current = read(prefs[key])
            val index = current.servers.indexOfFirst { it.id == server.id }
            val next = if (index >= 0) {
                current.servers.toMutableList().also { it[index] = server }
            } else {
                current.servers + server
            }
            prefs[key] = json.encodeToString(McpConfig.serializer(), McpConfig(next))
        }
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        context.mcpDataStore.edit { prefs ->
            val current = read(prefs[key])
            prefs[key] = json.encodeToString(
                McpConfig.serializer(),
                McpConfig(current.servers.map { if (it.id == id) it.copy(enabled = enabled) else it }),
            )
        }
    }

    suspend fun remove(id: String) {
        context.mcpDataStore.edit { prefs ->
            val current = read(prefs[key])
            prefs[key] = json.encodeToString(
                McpConfig.serializer(),
                McpConfig(current.servers.filterNot { it.id == id }),
            )
        }
    }

    private fun read(raw: String?): McpConfig = raw
        ?.let { runCatching { json.decodeFromString(McpConfig.serializer(), it) }.getOrNull() }
        ?: McpConfig()
}

object McpSecrets {
    fun key(serverId: String, name: String): String = "mcp_${serverId}_$name"
}
