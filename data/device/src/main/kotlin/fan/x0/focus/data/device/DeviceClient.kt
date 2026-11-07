package fan.x0.focus.data.device

import fan.x0.focus.core.error.NetworkException
import fan.x0.focus.core.network.NetworkExceptionMapper
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.koin.core.annotation.Single

@Single
class DeviceClient(private val client: HttpClient) {
    private val wsClient by lazy {
        client.config {
            install(WebSockets)
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    suspend fun health(host: String, port: Int): DeviceHealth = call {
        val response = client.request("http://$host:$port/health") {
            method = HttpMethod.Get
            timeout {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 5_000
                socketTimeoutMillis = 15_000
            }
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw NetworkException.Http(response.status.value, null, body.take(500))
        }
        json.decodeFromString(DeviceHealth.serializer(), body)
    }

    suspend fun pair(
        host: String,
        port: Int,
        code: String,
        name: String,
    ): DevicePairResult = call {
        val response = client.request("http://$host:$port/pair") {
            method = HttpMethod.Post
            contentType(ContentType.Application.Json)
            timeout {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 5_000
                socketTimeoutMillis = 20_000
            }
            setBody(
                json.encodeToString(
                    DevicePairRequest.serializer(),
                    DevicePairRequest(code = code, name = name),
                ),
            )
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw NetworkException.Http(response.status.value, null, errorText(body, response.status.value))
        }
        json.decodeFromString(DevicePairResult.serializer(), body)
    }

    suspend fun rpc(
        endpoint: DeviceEndpoint,
        token: String,
        op: String,
        args: JsonObject = JsonObject(emptyMap()),
    ): DeviceRpcResponse = call {
        val response = client.request("http://${endpoint.host}:${endpoint.port}/rpc") {
            method = HttpMethod.Post
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            timeout {
                requestTimeoutMillis = 600_000
                socketTimeoutMillis = 600_000
            }
            setBody(
                json.encodeToString(
                    DeviceRpcRequest.serializer(),
                    DeviceRpcRequest(op = op, args = args),
                ),
            )
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            DeviceRpcResponse(ok = false, error = errorText(body, response.status.value))
        } else {
            runCatching {
                json.decodeFromString(DeviceRpcResponse.serializer(), body)
            }.getOrElse {
                DeviceRpcResponse(ok = false, error = it.message.orEmpty())
            }
        }
    }

    suspend fun screenStream(
        endpoint: DeviceEndpoint,
        token: String,
        fps: Int = 5,
        onFrame: suspend (DeviceStreamFrame) -> Unit,
    ) {
        wsClient.webSocket(
            urlString = "ws://${endpoint.host}:${endpoint.port}/stream?token=$token&fps=${fps.coerceIn(1, 10)}",
        ) {
            for (frame in incoming) {
                when (frame) {
                    is Frame.Binary -> {
                        val image = android.util.Base64.encodeToString(
                            frame.readBytes(),
                            android.util.Base64.NO_WRAP,
                        )
                        onFrame(DeviceStreamFrame("frame", image = image))
                    }
                    is Frame.Text -> {
                        val value = runCatching {
                            json.decodeFromString(DeviceStreamFrame.serializer(), frame.readText())
                        }.getOrNull() ?: continue
                        onFrame(value)
                    }
                    else -> Unit
                }
            }
        }
    }

    private suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        throw NetworkExceptionMapper.mapOrNull(e) ?: NetworkException.Unknown(e)
    }

    private fun errorText(body: String, status: Int): String {
        val parsed = runCatching {
            json.decodeFromString(DeviceRpcResponse.serializer(), body)
        }.getOrNull()
        return parsed?.error?.takeIf { it.isNotBlank() } ?: "HTTP $status"
    }
}
