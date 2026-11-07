package fan.x0.focus.data.device

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import fan.x0.focus.data.assistant.SecretStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.security.MessageDigest
import java.util.UUID

private val Context.deviceDataStore by preferencesDataStore(name = "device_control")

@Single
class DeviceStore(
    @Provided private val context: Context,
    private val secrets: SecretStore,
) {
    private object Keys {
        val localEnabled = booleanPreferencesKey("local_enabled")
        val remoteEnabled = booleanPreferencesKey("remote_enabled")
        val role = stringPreferencesKey("role")
        val deviceId = stringPreferencesKey("device_id")
        val deviceName = stringPreferencesKey("device_name")
        val endpoints = stringPreferencesKey("endpoints_json")
        val controllers = stringPreferencesKey("controllers_json")
        val controllerTokens = stringPreferencesKey("controller_tokens_json")
        val triggers = stringPreferencesKey("triggers_json")
        val activeEndpoint = stringPreferencesKey("active_endpoint")
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val endpointSerializer = ListSerializer(DeviceEndpoint.serializer())
    private val controllerSerializer = ListSerializer(DeviceController.serializer())
    private val triggerSerializer = ListSerializer(DeviceTrigger.serializer())

    val localEnabled: Flow<Boolean> = context.deviceDataStore.data.map { it[Keys.localEnabled] ?: false }
    val remoteEnabled: Flow<Boolean> = context.deviceDataStore.data.map { it[Keys.remoteEnabled] ?: false }
    val role: Flow<DeviceRole> = context.deviceDataStore.data.map { prefs ->
        if (prefs[Keys.role] == DeviceRole.Controlled.name) DeviceRole.Controlled else DeviceRole.Full
    }
    val endpoints: Flow<List<DeviceEndpoint>> = context.deviceDataStore.data.map { readEndpoints(it) }
    val controllers: Flow<List<DeviceController>> = context.deviceDataStore.data.map { readControllers(it) }
    val activeEndpointId: Flow<String?> = context.deviceDataStore.data.map { it[Keys.activeEndpoint] }
    val triggers: Flow<List<DeviceTrigger>> = context.deviceDataStore.data.map { readTriggers(it) }

    suspend fun localEnabledOnce(): Boolean = localEnabled.first()
    suspend fun remoteEnabledOnce(): Boolean = remoteEnabled.first()
    suspend fun roleOnce(): DeviceRole = role.first()
    suspend fun endpointsOnce(): List<DeviceEndpoint> = endpoints.first()
    suspend fun controllersOnce(): List<DeviceController> = controllers.first()
    suspend fun triggersOnce(): List<DeviceTrigger> = triggers.first()

    suspend fun setLocalEnabled(value: Boolean) {
        context.deviceDataStore.edit { it[Keys.localEnabled] = value }
    }

    suspend fun setRemoteEnabled(value: Boolean) {
        context.deviceDataStore.edit { it[Keys.remoteEnabled] = value }
    }

    suspend fun addTrigger(
        name: String,
        packageFilter: String,
        textFilter: String,
        typeFilter: String,
        action: String,
        conversationId: String,
        command: String,
        cooldownSec: Int,
    ): DeviceTrigger {
        val trigger = DeviceTrigger(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "Trigger" }.take(60),
            packageFilter = packageFilter.trim(),
            textFilter = textFilter.trim(),
            typeFilter = typeFilter.trim(),
            action = action,
            conversationId = conversationId,
            command = command,
            cooldownSec = cooldownSec.coerceIn(1, 86_400),
            createdAt = System.currentTimeMillis(),
        )
        context.deviceDataStore.edit { prefs ->
            val next = readTriggers(prefs) + trigger
            prefs[Keys.triggers] = json.encodeToString(triggerSerializer, next)
        }
        return trigger
    }

    suspend fun removeTrigger(id: String) {
        context.deviceDataStore.edit { prefs ->
            val next = readTriggers(prefs).filterNot { it.id == id }
            prefs[Keys.triggers] = json.encodeToString(triggerSerializer, next)
        }
    }

    suspend fun setTriggerEnabled(id: String, enabled: Boolean) {
        context.deviceDataStore.edit { prefs ->
            val next = readTriggers(prefs).map { if (it.id == id) it.copy(enabled = enabled) else it }
            prefs[Keys.triggers] = json.encodeToString(triggerSerializer, next)
        }
    }

    suspend fun touchTrigger(id: String) {
        context.deviceDataStore.edit { prefs ->
            val next = readTriggers(prefs).map {
                if (it.id == id) it.copy(lastFiredAt = System.currentTimeMillis()) else it
            }
            prefs[Keys.triggers] = json.encodeToString(triggerSerializer, next)
        }
    }

    suspend fun setRole(value: DeviceRole) {
        context.deviceDataStore.edit { it[Keys.role] = value.name }
    }

    suspend fun deviceId(): String {
        val existing = context.deviceDataStore.data.first()[Keys.deviceId]
        if (!existing.isNullOrBlank()) return existing
        val next = UUID.randomUUID().toString()
        context.deviceDataStore.edit { it[Keys.deviceId] = next }
        return next
    }

    suspend fun deviceName(): String {
        val existing = context.deviceDataStore.data.first()[Keys.deviceName]
        if (!existing.isNullOrBlank()) return existing
        val next = Build.MODEL.orEmpty().ifBlank { "Android" }
        context.deviceDataStore.edit { it[Keys.deviceName] = next }
        return next
    }

    suspend fun setDeviceName(value: String) {
        val name = value.trim().take(60)
        if (name.isBlank()) return
        context.deviceDataStore.edit { it[Keys.deviceName] = name }
    }

    suspend fun activeEndpoint(): DeviceEndpoint? {
        val list = endpointsOnce()
        val id = activeEndpointId.first()
        return list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }

    suspend fun saveEndpoint(endpoint: DeviceEndpoint, token: String) {
        context.deviceDataStore.edit { prefs ->
            val current = readEndpoints(prefs)
            val next = current.filterNot { it.id == endpoint.id } + endpoint
            prefs[Keys.endpoints] = json.encodeToString(endpointSerializer, next)
            prefs[Keys.activeEndpoint] = endpoint.id
        }
        secrets.setSecret(tokenKey(endpoint.id), token)
    }

    suspend fun selectEndpoint(id: String) {
        context.deviceDataStore.edit { it[Keys.activeEndpoint] = id }
    }

    suspend fun removeEndpoint(id: String) {
        context.deviceDataStore.edit { prefs ->
            val next = readEndpoints(prefs).filterNot { it.id == id }
            prefs[Keys.endpoints] = json.encodeToString(endpointSerializer, next)
            if (prefs[Keys.activeEndpoint] == id) {
                val fallback = next.firstOrNull()?.id
                if (fallback == null) prefs.remove(Keys.activeEndpoint) else prefs[Keys.activeEndpoint] = fallback
            }
        }
        secrets.clearSecret(tokenKey(id))
    }

    suspend fun token(endpointId: String): String? =
        secrets.secret(tokenKey(endpointId))?.takeIf { it.isNotBlank() }

    suspend fun addController(name: String, token: String): DeviceController {
        val controller = DeviceController(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "Focus" }.take(60),
            createdAt = System.currentTimeMillis(),
            lastSeen = System.currentTimeMillis(),
        )
        context.deviceDataStore.edit { prefs ->
            val next = readControllers(prefs) + controller
            val tokens = readControllerTokens(prefs) + (controller.id to hash(token))
            prefs[Keys.controllers] = json.encodeToString(controllerSerializer, next)
            prefs[Keys.controllerTokens] = json.encodeToString(tokens)
        }
        return controller
    }

    suspend fun verifyController(token: String): DeviceController? {
        if (token.isBlank()) return null
        val digest = hash(token)
        val prefs = context.deviceDataStore.data.first()
        val tokens = readControllerTokens(prefs)
        val match = readControllers(prefs).firstOrNull { controller ->
            tokens[controller.id] == digest
        } ?: return null
        val now = System.currentTimeMillis()
        if (now - match.lastSeen > 60_000) touchController(match.id)
        return match.copy(lastSeen = now)
    }

    suspend fun revokeController(id: String) {
        context.deviceDataStore.edit { prefs ->
            val next = readControllers(prefs).filterNot { it.id == id }
            val tokens = readControllerTokens(prefs) - id
            prefs[Keys.controllers] = json.encodeToString(controllerSerializer, next)
            prefs[Keys.controllerTokens] = json.encodeToString(tokens)
        }
    }

    suspend fun touchController(id: String) {
        context.deviceDataStore.edit { prefs ->
            val next = readControllers(prefs).map {
                if (it.id == id) it.copy(lastSeen = System.currentTimeMillis()) else it
            }
            prefs[Keys.controllers] = json.encodeToString(controllerSerializer, next)
        }
    }

    private fun readEndpoints(prefs: androidx.datastore.preferences.core.Preferences): List<DeviceEndpoint> =
        prefs[Keys.endpoints]?.let {
            runCatching { json.decodeFromString(endpointSerializer, it) }.getOrNull()
        }.orEmpty()

    private fun readControllers(prefs: androidx.datastore.preferences.core.Preferences): List<DeviceController> =
        prefs[Keys.controllers]?.let {
            runCatching { json.decodeFromString(controllerSerializer, it) }.getOrNull()
        }.orEmpty()

    private fun readControllerTokens(prefs: androidx.datastore.preferences.core.Preferences): Map<String, String> =
        prefs[Keys.controllerTokens]?.let {
            runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull()
        }.orEmpty()

    private fun readTriggers(prefs: androidx.datastore.preferences.core.Preferences): List<DeviceTrigger> =
        prefs[Keys.triggers]?.let {
            runCatching { json.decodeFromString(triggerSerializer, it) }.getOrNull()
        }.orEmpty()

    private fun tokenKey(id: String): String = "device:token:$id"

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
