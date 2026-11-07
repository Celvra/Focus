package fan.x0.focus.data.device

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import org.koin.core.annotation.Single

@Single
class DeviceRepository(
    private val client: DeviceClient,
    private val store: DeviceStore,
) {
    val endpoints: Flow<List<DeviceEndpoint>> = store.endpoints
    val controllers: Flow<List<DeviceController>> = store.controllers
    val activeEndpointId: Flow<String?> = store.activeEndpointId
    val localEnabled: Flow<Boolean> = store.localEnabled
    val remoteEnabled: Flow<Boolean> = store.remoteEnabled
    val role: Flow<DeviceRole> = store.role
    val triggers: Flow<List<DeviceTrigger>> = store.triggers

    suspend fun endpointsOnce(): List<DeviceEndpoint> = store.endpointsOnce()
    suspend fun controllersOnce(): List<DeviceController> = store.controllersOnce()
    suspend fun activeEndpoint(): DeviceEndpoint? = store.activeEndpoint()
    suspend fun localEnabledOnce(): Boolean = store.localEnabledOnce()
    suspend fun remoteEnabledOnce(): Boolean = store.remoteEnabledOnce()

    suspend fun setLocalEnabled(value: Boolean) = store.setLocalEnabled(value)
    suspend fun setRemoteEnabled(value: Boolean) = store.setRemoteEnabled(value)
    suspend fun setRole(value: DeviceRole) = store.setRole(value)
    suspend fun select(id: String) = store.selectEndpoint(id)
    suspend fun setDeviceName(value: String) = store.setDeviceName(value)
    suspend fun deviceName(): String = store.deviceName()

    suspend fun pair(host: String, port: Int, code: String, name: String): DeviceEndpoint {
        val health = client.health(host, port)
        val result = client.pair(host, port, code, name)
        val endpoint = DeviceEndpoint(
            id = result.id,
            name = result.name.ifBlank { health.name.ifBlank { host } },
            host = host,
            port = port,
            model = result.model.ifBlank { health.model },
            version = result.version.ifBlank { health.version },
            android = result.android.ifBlank { health.android },
            lastSeen = System.currentTimeMillis(),
        )
        store.saveEndpoint(endpoint, result.token)
        return endpoint
    }

    suspend fun forget(endpoint: DeviceEndpoint) = store.removeEndpoint(endpoint.id)

    suspend fun revokeController(id: String) = store.revokeController(id)

    suspend fun token(endpoint: DeviceEndpoint): String? = store.token(endpoint.id)

    suspend fun rpc(
        endpoint: DeviceEndpoint,
        op: String,
        args: JsonObject = JsonObject(emptyMap()),
    ): DeviceRpcResponse {
        val token = store.token(endpoint.id) ?: return DeviceRpcResponse(ok = false, error = "Device is not paired")
        return client.rpc(endpoint, token, op, args)
    }

    suspend fun health(endpoint: DeviceEndpoint): DeviceHealth? = runCatching {
        client.health(endpoint.host, endpoint.port)
    }.getOrNull()

    suspend fun screenStream(
        endpoint: DeviceEndpoint,
        fps: Int = 2,
        onFrame: suspend (DeviceStreamFrame) -> Unit,
    ) {
        val token = store.token(endpoint.id) ?: error("Device is not paired")
        client.screenStream(endpoint, token, fps, onFrame)
    }
}
