package fan.x0.focus.core.network.openai

import fan.x0.focus.core.network.NetworkExceptionMapper
import fan.x0.focus.core.network.allowLongStream
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class AnthropicChatClient(
    private val http: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = false },
) {
    suspend fun complete(
        endpoint: ModelEndpoint,
        apiKey: String,
        request: ChatCompletionRequest,
    ): ChatReply {
        val response = try {
            http.post(endpoint.messagesUrl) {
                anthropicHeaders(apiKey)
                header(HttpHeaders.Accept, "application/json")
                contentType(ContentType.Application.Json)
                setBody(payload(request, stream = false))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw NetworkExceptionMapper.mapOrNull(e) ?: e
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw NetworkExceptionMapper.http(
                response.status.value,
                body,
                response.headers[HttpHeaders.RetryAfter]?.toLongOrNull(),
            )
        }
        return decodeResponse(body)
    }

    fun stream(
        endpoint: ModelEndpoint,
        apiKey: String,
        request: ChatCompletionRequest,
    ): Flow<ChatStreamEvent> = flow {
        val response = try {
            http.post(endpoint.messagesUrl) {
                allowLongStream()
                anthropicHeaders(apiKey)
                header(HttpHeaders.Accept, "text/event-stream")
                contentType(ContentType.Application.Json)
                setBody(payload(request, stream = true))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw NetworkExceptionMapper.mapOrNull(e) ?: e
        }
        if (!response.status.isSuccess()) {
            val body = response.bodyAsText()
            throw NetworkExceptionMapper.http(
                response.status.value,
                body,
                response.headers[HttpHeaders.RetryAfter]?.toLongOrNull(),
            )
        }
        val contentType = response.headers[HttpHeaders.ContentType].orEmpty()
        if (contentType.contains("application/json")) {
            val reply = decodeResponse(response.bodyAsText())
            reply.text.takeIf { it.isNotBlank() }?.let { emit(ChatStreamEvent.Text(it)) }
            reply.toolCalls.forEachIndexed { index, call ->
                emit(ChatStreamEvent.ToolDelta(index, call.id, call.name, call.arguments))
            }
            emit(ChatStreamEvent.Finished(if (reply.toolCalls.isEmpty()) "stop" else "tool_use"))
            return@flow
        }
        val decoder = AnthropicStreamDecoder()
        val sse = SseDecoder()
        val channel = response.bodyAsChannel()
        while (true) {
            val line = channel.readUTF8Line() ?: break
            val data = sse.onLine(line) ?: continue
            val root = runCatching { json.parseToJsonElement(data) as? JsonObject }.getOrNull()
                ?: throw ModelProtocolException("invalid stream event")
            decoder.decode(root).forEach { emit(it) }
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.anthropicHeaders(apiKey: String) {
        if (apiKey.isNotBlank()) header("x-api-key", apiKey)
        header("anthropic-version", "2023-06-01")
    }

    private fun decodeResponse(body: String): ChatReply {
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            ?: throw ModelProtocolException("invalid response body")
        root.wireObject("error")?.let { error ->
            throw ModelProtocolException(error.wireString("message") ?: "request failed")
        }
        val text = buildString {
            root.wireArray("content")?.forEach { element ->
                val block = element as? JsonObject ?: return@forEach
                if (block.wireString("type") == "text") {
                    block.wireText("text")?.let { append(it) }
                }
            }
        }
        val calls = buildList {
            root.wireArray("content")?.forEach { element ->
                val block = element as? JsonObject ?: return@forEach
                if (block.wireString("type") != "tool_use") return@forEach
                val name = block.wireString("name") ?: return@forEach
                add(
                    CompletedToolCall(
                        id = block.wireString("id") ?: "call_${size}",
                        name = name,
                        arguments = block.wireObject("input")?.toString() ?: "{}",
                    ),
                )
            }
        }
        return ChatReply(text = text, model = root.wireString("model"), toolCalls = calls)
    }

    private fun payload(request: ChatCompletionRequest, stream: Boolean): String = buildJsonObject {
        put("model", request.model)
        put("max_tokens", request.maxTokens ?: DEFAULT_MAX_TOKENS)
        put("stream", stream)
        val system = request.messages
            .filter { it.role == "system" }
            .map { it.textContent() }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
        if (system.isNotBlank()) put("system", system)
        put("messages", buildMessages(request.messages))
        request.tools?.takeIf { it.isNotEmpty() }?.let { tools ->
            putJsonArray("tools") {
                tools.forEach { tool ->
                    add(
                        buildJsonObject {
                            put("name", tool.function.name)
                            put("description", tool.function.description)
                            put("input_schema", tool.function.parameters)
                        },
                    )
                }
            }
        }
        request.toolChoice?.let { choice ->
            putJsonObject("tool_choice") { put("type", choice) }
        }
        request.temperature?.let { put("temperature", it) }
    }.toString()

    private fun buildMessages(messages: List<ChatMessageDto>): JsonArray {
        val merged = mutableListOf<Pair<String, MutableList<JsonObject>>>()
        messages.filter { it.role != "system" }.forEach { message ->
            val role: String
            val blocks: List<JsonObject>
            when (message.role) {
                "assistant" -> {
                    role = "assistant"
                    blocks = buildList {
                        message.textContent().takeIf { it.isNotBlank() }?.let {
                            add(textBlock(it))
                        }
                        message.toolCalls?.forEach { call ->
                            val name = call.function.name ?: return@forEach
                            val input = runCatching {
                                json.parseToJsonElement(call.function.arguments ?: "{}") as? JsonObject
                            }.getOrNull() ?: JsonObject(emptyMap())
                            add(
                                buildJsonObject {
                                    put("type", "tool_use")
                                    put("id", call.id.orEmpty())
                                    put("name", name)
                                    put("input", input)
                                },
                            )
                        }
                    }
                }
                "tool" -> {
                    val id = message.toolCallId ?: return@forEach
                    role = "user"
                    blocks = listOf(
                        buildJsonObject {
                            put("type", "tool_result")
                            put("tool_use_id", id)
                            put("content", message.textContent())
                        },
                    )
                }
                else -> {
                    role = "user"
                    blocks = userBlocks(message.content)
                }
            }
            if (blocks.isEmpty()) return@forEach
            val last = merged.lastOrNull()
            if (last != null && last.first == role) {
                last.second.addAll(blocks)
            } else {
                merged += role to blocks.toMutableList()
            }
        }
        return JsonArray(
            merged.map { (role, blocks) ->
                buildJsonObject {
                    put("role", role)
                    put("content", JsonArray(blocks))
                }
            },
        )
    }

    private fun userBlocks(content: MessageContent?): List<JsonObject> = when (content) {
        is MessageContent.Text -> listOfNotNull(content.value.takeIf { it.isNotBlank() }?.let(::textBlock))
        is MessageContent.Parts -> content.parts.mapNotNull { part ->
            when (part.type) {
                "text" -> part.text?.takeIf { it.isNotBlank() }?.let(::textBlock)
                "image_url" -> part.imageUrl?.url?.let(::imageBlock)
                else -> null
            }
        }
        null -> emptyList()
    }

    private fun textBlock(text: String): JsonObject = buildJsonObject {
        put("type", "text")
        put("text", text)
    }

    private fun imageBlock(url: String): JsonObject? {
        if (!url.startsWith("data:")) return null
        val meta = url.removePrefix("data:").substringBefore(',')
        val data = url.substringAfter(',', "")
        val mime = meta.substringBefore(';')
        if (data.isBlank() || mime !in IMAGE_TYPES) return null
        return buildJsonObject {
            put("type", "image")
            putJsonObject("source") {
                put("type", "base64")
                put("media_type", mime)
                put("data", data)
            }
        }
    }

    companion object {
        private const val DEFAULT_MAX_TOKENS = 4096
        private val IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/gif", "image/webp")
    }
}

private class AnthropicStreamDecoder {
    private var inputTokens = 0
    private var cachedTokens = 0

    fun decode(root: JsonObject): List<ChatStreamEvent> {
        val events = mutableListOf<ChatStreamEvent>()
        when (val type = root.wireString("type")) {
            "message_start" -> {
                val usage = root.wireObject("message")?.wireObject("usage")
                inputTokens = usage?.wireInt("input_tokens") ?: 0
                cachedTokens = usage?.wireInt("cache_read_input_tokens") ?: 0
            }
            "content_block_start" -> {
                val index = root.wireInt("index") ?: 0
                val block = root.wireObject("content_block") ?: return events
                when (block.wireString("type")) {
                    "text" -> block.wireText("text")?.let {
                        events += ChatStreamEvent.Text(it)
                    }
                    "thinking" -> block.wireText("thinking")?.let {
                        events += ChatStreamEvent.Reasoning(it)
                    }
                    "tool_use" -> {
                        events += ChatStreamEvent.ToolDelta(
                            index = index,
                            id = block.wireString("id"),
                            name = block.wireString("name"),
                            arguments = block.wireObject("input")
                                ?.takeIf { it.isNotEmpty() }
                                ?.toString(),
                        )
                    }
                }
            }
            "content_block_delta" -> {
                val index = root.wireInt("index") ?: 0
                val delta = root.wireObject("delta") ?: return events
                when (delta.wireString("type")) {
                    "text_delta" -> delta.wireText("text")?.let {
                        events += ChatStreamEvent.Text(it)
                    }
                    "thinking_delta" -> delta.wireText("thinking")?.let {
                        events += ChatStreamEvent.Reasoning(it)
                    }
                    "input_json_delta" -> delta.wireText("partial_json")?.let {
                        events += ChatStreamEvent.ToolDelta(index, null, null, it)
                    }
                }
            }
            "message_delta" -> {
                val usage = root.wireObject("usage")
                val output = usage?.wireInt("output_tokens") ?: 0
                if (usage != null) {
                    events += ChatStreamEvent.Usage(
                        prompt = inputTokens,
                        completion = output,
                        total = inputTokens + output,
                        cached = cachedTokens,
                    )
                }
                val reason = root.wireObject("delta")?.wireString("stop_reason")
                events += ChatStreamEvent.Finished(reason)
            }
            "error" -> {
                val message = root.wireObject("error")?.wireString("message")
                    ?: root.wireString("message")
                    ?: "stream error"
                throw ModelProtocolException(message)
            }
        }
        return events
    }
}
