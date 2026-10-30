package fan.x0.focus.data.assistant

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.koin.core.annotation.Single
import java.util.concurrent.atomic.AtomicLong

data class McpServerInfo(
    val name: String,
    val version: String,
    val protocolVersion: String,
)

data class McpTool(
    val name: String,
    val title: String,
    val description: String,
    val inputSchema: JsonObject,
)

data class McpResource(
    val uri: String,
    val name: String,
    val description: String,
    val mimeType: String,
)

data class McpContent(
    val type: String,
    val text: String,
    val uri: String = "",
    val mimeType: String = "",
)

data class McpCallResult(
    val content: List<McpContent>,
    val isError: Boolean,
    val structured: String?,
) {
    fun readable(): String = buildString {
        if (isError) append("Error from MCP server\n")
        content.forEach { item ->
            when (item.type) {
                "text" -> append(item.text).append('\n')
                "resource" -> append("[resource ").append(item.uri).append("]\n").append(item.text).append('\n')
                "image" -> append("[image ").append(item.mimeType).append("]\n")
                else -> append(item.text).append('\n')
            }
        }
        structured?.takeIf { it.isNotBlank() }?.let { append("\nstructured:\n").append(it) }
    }.trim()
}

class McpException(message: String) : Exception(message)

@Single
class McpClient(
    private val http: HttpClient,
    private val secrets: SecretStore,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val nextId = AtomicLong(1)

    suspend fun connect(server: McpServerConfig): McpSession {
        if (!server.enabled) throw McpException("${server.name} is disabled")
        return when (server.transport) {
            McpTransport.StreamableHttp -> {
                try {
                    streamableSession(server)
                } catch (e: McpException) {
                    val message = e.message.orEmpty()
                    if (message.contains("HTTP 404") || message.contains("HTTP 405")) {
                        legacySession(server)
                    } else {
                        throw e
                    }
                }
            }
            McpTransport.Sse -> legacySession(server)
            McpTransport.Stdio -> throw McpException("stdio MCP servers need the bridge and are not available in this build")
        }
    }

    private suspend fun streamableSession(server: McpServerConfig): McpSession {
        if (server.endpoint.isBlank()) throw McpException("${server.name} has no URL")
        val session = McpSession(
            server = server,
            http = http,
            json = json,
            extraHeaders = headers(server),
            nextId = nextId,
            legacy = false,
        )
        session.initialize()
        return session
    }

    private suspend fun legacySession(server: McpServerConfig): McpSession {
        if (server.endpoint.isBlank()) throw McpException("${server.name} has no URL")
        val session = McpSession(
            server = server,
            http = http,
            json = json,
            extraHeaders = headers(server),
            nextId = nextId,
            legacy = true,
        )
        session.initialize()
        return session
    }

    private suspend fun headers(server: McpServerConfig): Map<String, String> {
        val resolved = server.headers.mapNotNull { header ->
            val name = header.name.trim()
            if (name.isBlank()) return@mapNotNull null
            val value = if (header.secret) {
                secrets.secret(McpSecrets.key(server.id, name)) ?: header.value
            } else {
                header.value
            }
            name to value
        }.toMap()
        return resolved
    }
}

class McpSession internal constructor(
    private val server: McpServerConfig,
    private val http: HttpClient,
    private val json: Json,
    private val extraHeaders: Map<String, String>,
    private val nextId: AtomicLong,
    private val legacy: Boolean,
) {
    private var sessionId: String? = null
    private var protocolVersion: String = DEFAULT_PROTOCOL
    private var legacyEndpoint: String? = null
    private var serverInfo: McpServerInfo = McpServerInfo(server.name, "", DEFAULT_PROTOCOL)

    fun info(): McpServerInfo = serverInfo

    suspend fun initialize(): McpServerInfo {
        if (legacy) {
            openLegacyEndpoint()
        }
        val result = call(
            method = "initialize",
            params = buildJsonObject {
                put("protocolVersion", DEFAULT_PROTOCOL)
                putJsonObject("capabilities") {}
                putJsonObject("clientInfo") {
                    put("name", "Focus")
                    put("version", "1.0")
                }
            },
            captureSession = true,
            retryOn404 = false,
        ) ?: throw McpException("${server.name} did not return an initialize result")
        val obj = result as? JsonObject ?: throw McpException("${server.name} returned an invalid initialize result")
        protocolVersion = (obj["protocolVersion"] as? JsonPrimitive)?.content ?: DEFAULT_PROTOCOL
        val info = obj["serverInfo"] as? JsonObject
        serverInfo = McpServerInfo(
            name = (info?.get("name") as? JsonPrimitive)?.content ?: server.name,
            version = (info?.get("version") as? JsonPrimitive)?.content.orEmpty(),
            protocolVersion = protocolVersion,
        )
        notify("notifications/initialized")
        return serverInfo
    }

    suspend fun listTools(): List<McpTool> {
        val tools = mutableListOf<McpTool>()
        var cursor: String? = null
        repeat(MAX_PAGES) {
            val params = buildJsonObject {
                cursor?.let { put("cursor", it) }
            }
            val result = call("tools/list", params) as? JsonObject ?: return tools
            val array = result["tools"] as? JsonArray ?: return tools
            array.forEach { element ->
                val obj = element as? JsonObject ?: return@forEach
                val name = (obj["name"] as? JsonPrimitive)?.content ?: return@forEach
                tools += McpTool(
                    name = name,
                    title = (obj["title"] as? JsonPrimitive)?.content.orEmpty(),
                    description = (obj["description"] as? JsonPrimitive)?.content.orEmpty(),
                    inputSchema = obj["inputSchema"] as? JsonObject ?: JsonObject(emptyMap()),
                )
            }
            cursor = (result["nextCursor"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
            if (cursor == null) return tools
        }
        return tools
    }

    suspend fun listResources(): List<McpResource> {
        val resources = mutableListOf<McpResource>()
        var cursor: String? = null
        repeat(MAX_PAGES) {
            val params = buildJsonObject {
                cursor?.let { put("cursor", it) }
            }
            val result = call("resources/list", params) as? JsonObject ?: return resources
            val array = result["resources"] as? JsonArray ?: return resources
            array.forEach { element ->
                val obj = element as? JsonObject ?: return@forEach
                val uri = (obj["uri"] as? JsonPrimitive)?.content ?: return@forEach
                resources += McpResource(
                    uri = uri,
                    name = (obj["name"] as? JsonPrimitive)?.content.orEmpty(),
                    description = (obj["description"] as? JsonPrimitive)?.content.orEmpty(),
                    mimeType = (obj["mimeType"] as? JsonPrimitive)?.content.orEmpty(),
                )
            }
            cursor = (result["nextCursor"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
            if (cursor == null) return resources
        }
        return resources
    }

    suspend fun callTool(name: String, arguments: JsonObject): McpCallResult {
        val result = call(
            method = "tools/call",
            params = buildJsonObject {
                put("name", name)
                put("arguments", arguments)
            },
        ) as? JsonObject ?: throw McpException("${server.name} returned no tool result")
        val content = (result["content"] as? JsonArray).orEmpty().mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val type = (obj["type"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            when (type) {
                "text" -> McpContent(type, (obj["text"] as? JsonPrimitive)?.content.orEmpty())
                "resource" -> {
                    val resource = obj["resource"] as? JsonObject
                    McpContent(
                        type = "resource",
                        text = (resource?.get("text") as? JsonPrimitive)?.content.orEmpty(),
                        uri = (resource?.get("uri") as? JsonPrimitive)?.content.orEmpty(),
                        mimeType = (resource?.get("mimeType") as? JsonPrimitive)?.content.orEmpty(),
                    )
                }
                "image", "audio" -> McpContent(
                    type = "image",
                    text = "",
                    mimeType = (obj["mimeType"] as? JsonPrimitive)?.content.orEmpty(),
                )
                else -> McpContent(type, obj.toString())
            }
        }
        val structured = result["structuredContent"]?.let { element ->
            if (element is JsonPrimitive) element.content else element.toString()
        }
        return McpCallResult(
            content = content,
            isError = (result["isError"] as? JsonPrimitive)?.content?.toBoolean() ?: false,
            structured = structured,
        )
    }

    suspend fun readResource(uri: String): String {
        val result = call(
            method = "resources/read",
            params = buildJsonObject { put("uri", uri) },
        ) as? JsonObject ?: throw McpException("${server.name} returned no resource")
        val contents = result["contents"] as? JsonArray ?: return ""
        return contents.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            (obj["text"] as? JsonPrimitive)?.content
                ?: (obj["blob"] as? JsonPrimitive)?.content?.let { "[binary ${it.length} bytes]" }
        }.joinToString("\n")
    }

    private suspend fun call(
        method: String,
        params: JsonObject?,
        captureSession: Boolean = false,
        retryOn404: Boolean = true,
    ): JsonElement? {
        val response = postRpc(
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", nextId.getAndIncrement())
                put("method", method)
                params?.let { put("params", it) }
            },
            captureSession = captureSession,
        )
        if (response.status.value == 404 && retryOn404 && !legacy) {
            sessionId = null
            initialize()
            return call(method, params, captureSession = false, retryOn404 = false)
        }
        val body = withTimeout(30_000) { response.bodyAsText() }
        if (!response.status.isSuccess()) {
            throw McpException("${server.name} HTTP ${response.status.value}: ${body.take(200)}")
        }
        val message = readMessage(response, body)
        val error = message["error"] as? JsonObject
        if (error != null) {
            val code = (error["code"] as? JsonPrimitive)?.content.orEmpty()
            val text = (error["message"] as? JsonPrimitive)?.content.orEmpty()
            throw McpException("${server.name} error $code: $text")
        }
        return message["result"]
    }

    private suspend fun notify(method: String) {
        val response = postRpc(
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("method", method)
            },
        )
        if (response.status.value in setOf(200, 202, 204)) return
        if (response.status.value == 404 && !legacy) {
            sessionId = null
            initialize()
            return
        }
        throw McpException("${server.name} rejected $method with HTTP ${response.status.value}")
    }

    private suspend fun postRpc(payload: JsonObject, captureSession: Boolean = false): HttpResponse {
        val target = legacyEndpoint ?: server.endpoint
        val response = http.request(target) {
            method = HttpMethod.Post
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Accept, "application/json, text/event-stream")
            extraHeaders.forEach { (name, value) -> header(name, value) }
            sessionId?.let { header("Mcp-Session-Id", it) }
            if (!legacy) header("MCP-Protocol-Version", protocolVersion)
            setBody(payload.toString())
        }
        if (captureSession) {
            response.headers["Mcp-Session-Id"]?.takeIf { it.isNotBlank() }?.let { sessionId = it }
        }
        return response
    }

    private suspend fun readMessage(response: HttpResponse, body: String): JsonObject {
        val contentType = response.headers[HttpHeaders.ContentType].orEmpty()
        if (contentType.contains("text/event-stream")) return readSseMessage(body)
        return runCatching { json.parseToJsonElement(body).jsonObject }
            .getOrElse { throw McpException("${server.name} returned invalid JSON") }
    }

    private fun readSseMessage(body: String): JsonObject {
        val frames = parseSse(body)
        val message = frames.asReversed().firstNotNullOfOrNull { frame ->
            if (frame.data.isBlank()) return@firstNotNullOfOrNull null
            val obj = runCatching { json.parseToJsonElement(frame.data).jsonObject }.getOrNull()
                ?: return@firstNotNullOfOrNull null
            if (obj.containsKey("result") || obj.containsKey("error")) obj else null
        } ?: throw McpException("${server.name} returned an empty SSE stream")
        return message
    }

    private suspend fun openLegacyEndpoint() {
        val response = http.request(server.endpoint) {
            method = HttpMethod.Get
            header(HttpHeaders.Accept, "text/event-stream")
            extraHeaders.forEach { (name, value) -> header(name, value) }
        }
        if (!response.status.isSuccess()) {
            throw McpException("${server.name} SSE HTTP ${response.status.value}")
        }
        val channel = response.bodyAsChannel()
        val data = StringBuilder()
        val event = StringBuilder()
        withTimeout(15_000) {
            while (true) {
                val line = channel.readUTF8Line() ?: break
                when {
                    line.isEmpty() -> {
                        if (event.toString() == "endpoint" && data.isNotBlank()) {
                            legacyEndpoint = resolveEndpoint(data.toString().trim())
                            return@withTimeout
                        }
                        event.clear()
                        data.clear()
                    }
                    line.startsWith("event:") -> event.append(line.removePrefix("event:").trim())
                    line.startsWith("data:") -> data.append(line.removePrefix("data:").trim())
                }
            }
        }
        if (legacyEndpoint == null) throw McpException("${server.name} did not send an endpoint event")
    }

    private fun resolveEndpoint(raw: String): String {
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
        val base = java.net.URI(server.endpoint)
        return base.resolve(raw).toString()
    }

    private fun parseSse(body: String): List<SseFrame> {
        val frames = mutableListOf<SseFrame>()
        var event = ""
        var id = ""
        val data = StringBuilder()
        body.lineSequence().forEach { line ->
            when {
                line.isEmpty() -> {
                    if (data.isNotEmpty()) {
                        frames += SseFrame(event.ifBlank { "message" }, id, data.toString().trimEnd())
                    }
                    event = ""
                    id = ""
                    data.clear()
                }
                line.startsWith(":") -> Unit
                line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                line.startsWith("id:") -> id = line.removePrefix("id:").trim()
                line.startsWith("data:") -> {
                    if (data.isNotEmpty()) data.append('\n')
                    data.append(line.removePrefix("data:").removePrefix(" "))
                }
            }
        }
        if (data.isNotEmpty()) frames += SseFrame(event.ifBlank { "message" }, id, data.toString().trimEnd())
        return frames
    }

    private data class SseFrame(val event: String, val id: String, val data: String)

    companion object {
        const val DEFAULT_PROTOCOL = "2025-06-18"
        private const val MAX_PAGES = 50
    }
}

@Single
class McpManager(
    private val client: McpClient,
    private val settings: McpSettings,
) {
    suspend fun servers(): List<McpServerConfig> = settings.configOnce().servers

    suspend fun test(server: McpServerConfig): McpTestReport {
        val session = client.connect(server)
        val tools = session.listTools()
        val resources = runCatching { session.listResources() }.getOrDefault(emptyList())
        return McpTestReport(session.info(), tools, resources)
    }

    suspend fun listTools(server: McpServerConfig): List<McpTool> =
        client.connect(server).listTools()

    suspend fun callTool(server: McpServerConfig, name: String, arguments: JsonObject): McpCallResult =
        client.connect(server).callTool(name, arguments)

    suspend fun listResources(server: McpServerConfig): List<McpResource> =
        client.connect(server).listResources()

    suspend fun readResource(server: McpServerConfig, uri: String): String =
        client.connect(server).readResource(uri)

    suspend fun enabledServers(): List<McpServerConfig> =
        settings.configOnce().servers.filter { it.enabled }
}

data class McpTestReport(
    val info: McpServerInfo,
    val tools: List<McpTool>,
    val resources: List<McpResource>,
)
