package fan.x0.focus.data.bridge

import fan.x0.focus.core.error.NetworkException
import fan.x0.focus.core.network.NetworkExceptionMapper
import fan.x0.focus.core.network.createHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.parameter
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
import io.ktor.utils.io.readUTF8Line
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
        readSse(response) { payload ->
            val event = runCatching {
                json.decodeFromString(BridgeExecEvent.serializer(), payload)
            }.getOrNull() ?: return@readSse
            onEvent(event)
        }
    }

    suspend fun bgStart(
        endpoint: BridgeEndpoint,
        token: String,
        request: BridgeBgStartRequest,
    ): BridgeBgJob = endpointCall(
        path = "/bg/start",
        endpoint = endpoint,
        token = token,
        method = HttpMethod.Post,
        body = json.encodeToString(BridgeBgStartRequest.serializer(), request),
    )

    suspend fun bgList(endpoint: BridgeEndpoint, token: String): List<BridgeBgJob> =
        endpointCall<BridgeBgListResult>("/bg/list", endpoint, token, HttpMethod.Get).jobs

    suspend fun bgLogs(
        endpoint: BridgeEndpoint,
        token: String,
        id: String,
        after: Long,
    ): BridgeBgLogsResult = endpointCall(
        path = "/bg/logs?id=${encode(id)}&after=$after&follow=false",
        endpoint = endpoint,
        token = token,
        method = HttpMethod.Get,
    )

    suspend fun bgFollow(
        endpoint: BridgeEndpoint,
        token: String,
        id: String,
        after: Long,
        onFrame: suspend (BridgeBgLogsResult) -> Unit,
    ) {
        val response = raw(
            target = "${endpoint.baseUrl}/bg/logs?id=${encode(id)}&after=$after&follow=true",
            token = token,
            method = HttpMethod.Get,
        )
        readSse(response) { payload ->
            val frame = runCatching {
                json.decodeFromString(BridgeBgLogsResult.serializer(), payload)
            }.getOrNull() ?: return@readSse
            onFrame(frame)
        }
    }

    suspend fun bgKill(endpoint: BridgeEndpoint, token: String, id: String): BridgeOkResult =
        endpointCall(
            path = "/bg/kill",
            endpoint = endpoint,
            token = token,
            method = HttpMethod.Post,
            body = json.encodeToString(BridgeBgIdRequest.serializer(), BridgeBgIdRequest(id)),
        )

    suspend fun bgClear(endpoint: BridgeEndpoint, token: String, id: String?): BridgeOkResult =
        endpointCall(
            path = "/bg/clear",
            endpoint = endpoint,
            token = token,
            method = HttpMethod.Post,
            body = id?.let {
                json.encodeToString(BridgeBgIdRequest.serializer(), BridgeBgIdRequest(it))
            } ?: "null",
        )

    private suspend fun readSse(response: HttpResponse, onData: suspend (String) -> Unit) {
        val channel = response.bodyAsChannel()
        val data = StringBuilder()
        while (true) {
            val line = channel.readUTF8Line() ?: break
            when {
                line.isEmpty() -> {
                    if (data.isNotEmpty()) {
                        onData(data.toString())
                        data.clear()
                    }
                }
                line.startsWith(":") -> Unit
                line.startsWith("data:") -> {
                    if (data.isNotEmpty()) data.append('\n')
                    data.append(line.removePrefix("data:").removePrefix(" "))
                }
            }
        }
        if (data.isNotEmpty()) onData(data.toString())
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

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
