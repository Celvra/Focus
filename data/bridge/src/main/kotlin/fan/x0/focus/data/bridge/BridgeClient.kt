package fan.x0.focus.data.bridge

import fan.x0.focus.core.error.NetworkException
import fan.x0.focus.core.network.NetworkExceptionMapper
import fan.x0.focus.core.network.createHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.url
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

@Single
class BridgeClient {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    private val http: HttpClient by lazy {
        createHttpClient().config {
            install(WebSockets)
        }
    }

    suspend fun health(host: String, port: Int): BridgeHealth =
        call("http://$host:$port/health", null, HttpMethod.Get)

    suspend fun pair(host: String, port: Int, code: String, deviceName: String): BridgePairResult =
        call(
            target = "http://$host:$port/pair",
            token = null,
            method = HttpMethod.Post,
            body = json.encodeToString(
                BridgePairRequest.serializer(),
                BridgePairRequest(code = code, deviceName = deviceName),
            ),
        )

    suspend fun devices(endpoint: BridgeEndpoint, token: String): List<BridgeDevice> =
        endpointCall<BridgeDevicesResult>("/devices", endpoint, token, HttpMethod.Get).devices

    suspend fun revoke(endpoint: BridgeEndpoint, token: String, deviceId: String) {
        endpointCall<BridgeOkResult>("/devices/$deviceId", endpoint, token, HttpMethod.Delete)
    }

    suspend fun workspace(endpoint: BridgeEndpoint, token: String): BridgeWorkspace =
        endpointCall("/workspace", endpoint, token, HttpMethod.Get)

    suspend fun setWorkspace(endpoint: BridgeEndpoint, token: String, roots: List<String>): BridgeWorkspace =
        endpointCall(
            path = "/workspace",
            endpoint = endpoint,
            token = token,
            method = HttpMethod.Post,
            body = json.encodeToString(BridgeWorkspaceRequest.serializer(), BridgeWorkspaceRequest(roots)),
        )

    suspend fun sessions(endpoint: BridgeEndpoint, token: String): List<BridgeSessionInfo> =
        endpointCall<BridgeSessionsResult>("/sessions", endpoint, token, HttpMethod.Get).sessions

    suspend fun openSession(
        endpoint: BridgeEndpoint,
        token: String,
        request: BridgeSessionOpenRequest,
    ): BridgeSessionInfo = endpointCall(
        path = "/sessions",
        endpoint = endpoint,
        token = token,
        method = HttpMethod.Post,
        body = json.encodeToString(BridgeSessionOpenRequest.serializer(), request),
    )

    suspend fun closeSession(endpoint: BridgeEndpoint, token: String, id: String) {
        endpointCall<BridgeOkResult>("/sessions/$id", endpoint, token, HttpMethod.Delete)
    }

    suspend fun sessionInput(
        endpoint: BridgeEndpoint,
        token: String,
        id: String,
        request: BridgeSessionWriteRequest,
    ) {
        endpointCall<BridgeOkResult>(
            "/sessions/$id/input",
            endpoint,
            token,
            HttpMethod.Post,
            json.encodeToString(BridgeSessionWriteRequest.serializer(), request),
        )
    }

    suspend fun sessionTail(endpoint: BridgeEndpoint, token: String, id: String): String =
        endpointCall<BridgeTailResult>("/sessions/$id/tail", endpoint, token, HttpMethod.Get).output

    suspend fun read(endpoint: BridgeEndpoint, token: String, request: BridgeReadRequest): BridgeReadResult =
        endpointCall(
            "/fs/read",
            endpoint,
            token,
            HttpMethod.Post,
            json.encodeToString(BridgeReadRequest.serializer(), request),
        )

    suspend fun write(endpoint: BridgeEndpoint, token: String, request: BridgeWriteRequest): BridgeOkResult =
        endpointCall(
            "/fs/write",
            endpoint,
            token,
            HttpMethod.Post,
            json.encodeToString(BridgeWriteRequest.serializer(), request),
        )

    suspend fun edit(endpoint: BridgeEndpoint, token: String, request: BridgeEditRequest): BridgeOkResult =
        endpointCall(
            "/fs/edit",
            endpoint,
            token,
            HttpMethod.Post,
            json.encodeToString(BridgeEditRequest.serializer(), request),
        )

    suspend fun list(endpoint: BridgeEndpoint, token: String, request: BridgeListRequest): BridgeListResult =
        endpointCall(
            "/fs/list",
            endpoint,
            token,
            HttpMethod.Post,
            json.encodeToString(BridgeListRequest.serializer(), request),
        )

    suspend fun glob(endpoint: BridgeEndpoint, token: String, request: BridgeGlobRequest): BridgeGlobResult =
        endpointCall(
            "/fs/glob",
            endpoint,
            token,
            HttpMethod.Post,
            json.encodeToString(BridgeGlobRequest.serializer(), request),
        )

    suspend fun grep(endpoint: BridgeEndpoint, token: String, request: BridgeGrepRequest): BridgeGrepResult =
        endpointCall(
            "/fs/grep",
            endpoint,
            token,
            HttpMethod.Post,
            json.encodeToString(BridgeGrepRequest.serializer(), request),
        )

    suspend fun mkdir(endpoint: BridgeEndpoint, token: String, path: String): BridgeOkResult =
        endpointCall(
            "/fs/mkdir",
            endpoint,
            token,
            HttpMethod.Post,
            json.encodeToString(BridgePathRequest.serializer(), BridgePathRequest(path)),
        )

    suspend fun move(endpoint: BridgeEndpoint, token: String, from: String, to: String): BridgeOkResult =
        endpointCall(
            "/fs/move",
            endpoint,
            token,
            HttpMethod.Post,
            json.encodeToString(BridgeMoveRequest.serializer(), BridgeMoveRequest(from, to)),
        )

    suspend fun remove(endpoint: BridgeEndpoint, token: String, path: String): BridgeOkResult =
        endpointCall(
            "/fs/remove",
            endpoint,
            token,
            HttpMethod.Post,
            json.encodeToString(BridgePathRequest.serializer(), BridgePathRequest(path)),
        )

    suspend fun exec(
        endpoint: BridgeEndpoint,
        token: String,
        request: BridgeExecRequest,
        onEvent: suspend (BridgeExecEvent) -> Unit,
    ) {
        val response = raw(
            target = "${endpoint.baseUrl}/exec",
            token = token,
            method = HttpMethod.Post,
            body = json.encodeToString(BridgeExecRequest.serializer(), request),
        )
        val channel = response.bodyAsChannel()
        val buffer = ByteArray(8192)
        val pending = StringBuilder()
        while (true) {
            val read = channel.readAvailable(buffer, 0, buffer.size)
            if (read <= 0) break
            pending.append(String(buffer, 0, read, Charsets.UTF_8))
            while (true) {
                val boundary = pending.indexOf("\n\n")
                if (boundary < 0) break
                val frame = pending.substring(0, boundary)
                pending.delete(0, boundary + 2)
                val payload = frame.lineSequence()
                    .firstOrNull { it.startsWith("data:") }
                    ?.removePrefix("data:")
                    ?.trim()
                    .orEmpty()
                if (payload.isEmpty()) continue
                val event = runCatching {
                    json.decodeFromString(BridgeExecEvent.serializer(), payload)
                }.getOrNull() ?: continue
                onEvent(event)
            }
        }
    }

    private suspend inline fun <reified T> endpointCall(
        path: String,
        endpoint: BridgeEndpoint,
        token: String,
        method: HttpMethod,
        body: String? = null,
    ): T = call("${endpoint.baseUrl}$path", token, method, body)

    private suspend inline fun <reified T> call(
        target: String,
        token: String?,
        method: HttpMethod,
        body: String? = null,
    ): T {
        val response = raw(target, token, method, body)
        val text = try {
            response.bodyAsText()
        } catch (throwable: Throwable) {
            throw translate(throwable)
        }
        return try {
            json.decodeFromString<T>(text)
        } catch (throwable: Throwable) {
            throw NetworkException.Serialization(throwable)
        }
    }

    private suspend fun raw(
        target: String,
        token: String?,
        method: HttpMethod,
        body: String? = null,
    ): HttpResponse {
        val response = try {
            http.request {
                url(target)
                this.method = method
                if (token != null) header(HttpHeaders.Authorization, "Bearer $token")
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }
        } catch (throwable: Throwable) {
            throw translate(throwable)
        }
        if (response.status.isSuccess()) return response
        val text = runCatching { response.bodyAsText() }.getOrNull()
        throw NetworkExceptionMapper.http(response.status.value, text)
    }

    private fun translate(throwable: Throwable): Throwable {
        if (throwable is CancellationException) return throwable
        return NetworkExceptionMapper.mapOrNull(throwable) ?: NetworkException.Unknown(throwable)
    }
}
