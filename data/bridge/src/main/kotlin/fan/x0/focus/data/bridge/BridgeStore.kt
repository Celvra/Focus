package fan.x0.focus.data.bridge

import android.content.Context
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

@kotlinx.serialization.Serializable
data class BridgeEndpoint(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val os: String = "",
    val arch: String = "",
    val version: String = "",
    val workspaceRoots: List<String> = emptyList(),
) {
    val baseUrl: String get() = "http://$host:$port"
    val wsUrl: String get() = "ws://$host:$port"
}

private val Context.bridgeDataStore by preferencesDataStore(name = "bridge_settings")

@Single
class BridgeStore(@Provided private val context: Context) {
    private object Keys {
        val endpoints = stringPreferencesKey("endpoints_json")
        val activeId = stringPreferencesKey("active_endpoint_id")
        val tokens = stringPreferencesKey("tokens_json")
        val lastHost = stringPreferencesKey("last_host")
        val lastPort = stringPreferencesKey("last_port")
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val endpointSerializer = ListSerializer(BridgeEndpoint.serializer())

    val endpoints: Flow<List<BridgeEndpoint>> = context.bridgeDataStore.data.map { prefs ->
        prefs[Keys.endpoints]?.let { raw ->
            runCatching { json.decodeFromString(endpointSerializer, raw) }.getOrNull()
        }.orEmpty()
    }

    val activeEndpointId: Flow<String?> = context.bridgeDataStore.data.map { prefs ->
        prefs[Keys.activeId]?.takeIf { it.isNotBlank() }
    }

    suspend fun endpointsOnce(): List<BridgeEndpoint> = endpoints.first()

    suspend fun activeEndpoint(): BridgeEndpoint? {
        val list = endpointsOnce()
        val id = activeEndpointId.first()
        return list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }

    suspend fun token(endpointId: String): String? {
        val prefs = context.bridgeDataStore.data.first()
        val raw = prefs[Keys.tokens] ?: return null
        val map = runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrNull() ?: return null
        return map[endpointId]?.takeIf { it.isNotBlank() }
    }

    suspend fun saveEndpoint(endpoint: BridgeEndpoint, token: String?) {
        context.bridgeDataStore.edit { prefs ->
            val current = readEndpoints(prefs)
            val next = current.filterNot { it.id == endpoint.id } + endpoint
            prefs[Keys.endpoints] = json.encodeToString(endpointSerializer, next)
            prefs[Keys.activeId] = endpoint.id
            prefs[Keys.lastHost] = endpoint.host
            prefs[Keys.lastPort] = endpoint.port.toString()
            if (token != null) {
                val map = readTokens(prefs).toMutableMap()
                map[endpoint.id] = token
                prefs[Keys.tokens] = json.encodeToString(map)
            }
        }
    }

    suspend fun setActive(endpointId: String) {
        context.bridgeDataStore.edit { prefs -> prefs[Keys.activeId] = endpointId }
    }

    suspend fun setWorkspaceRoots(endpointId: String, roots: List<String>) {
        context.bridgeDataStore.edit { prefs ->
            val current = readEndpoints(prefs)
            val next = current.map { if (it.id == endpointId) it.copy(workspaceRoots = roots) else it }
            prefs[Keys.endpoints] = json.encodeToString(endpointSerializer, next)
        }
    }

    suspend fun remove(endpointId: String) {
        context.bridgeDataStore.edit { prefs ->
            val next = readEndpoints(prefs).filterNot { it.id == endpointId }
            prefs[Keys.endpoints] = json.encodeToString(endpointSerializer, next)
            val map = readTokens(prefs).toMutableMap()
            map.remove(endpointId)
            prefs[Keys.tokens] = json.encodeToString(map)
            if (prefs[Keys.activeId] == endpointId) {
                val fallback = next.firstOrNull()?.id
                if (fallback == null) {
                    prefs.remove(Keys.activeId)
                } else {
                    prefs[Keys.activeId] = fallback
                }
            }
        }
    }

    suspend fun lastHost(): String? = context.bridgeDataStore.data.first()[Keys.lastHost]
    suspend fun lastPort(): Int? = context.bridgeDataStore.data.first()[Keys.lastPort]?.toIntOrNull()

    private fun readEndpoints(prefs: androidx.datastore.preferences.core.Preferences): List<BridgeEndpoint> =
        prefs[Keys.endpoints]?.let { raw ->
            runCatching { json.decodeFromString(endpointSerializer, raw) }.getOrNull()
        }.orEmpty()

    private fun readTokens(prefs: androidx.datastore.preferences.core.Preferences): Map<String, String> =
        prefs[Keys.tokens]?.let { raw ->
            runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrNull()
        }.orEmpty()
}
