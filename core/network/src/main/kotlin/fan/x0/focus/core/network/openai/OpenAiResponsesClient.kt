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
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class OpenAiResponsesClient(
    private val http: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = false },
) {
    suspend fun complete(
        endpoint: ModelEndpoint,
        apiKey: String,
        request: ChatCompletionRequest,
    ): ChatReply {
        val response = try {
            http.post(endpoint.responsesUrl) {
                if (apiKey.isNotBlank()) header(HttpHeaders.Authorization, "Bearer $apiKey")
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
            http.post(endpoint.responsesUrl) {
                allowLongStream()
                if (apiKey.isNotBlank()) header(HttpHeaders.Authorization, "Bearer $apiKey")
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
            emit(ChatStreamEvent.Finished(if (reply.toolCalls.isEmpty()) "stop" else "tool_calls"))
            return@flow
        }
        val decoder = ResponseStreamDecoder()
        val sse = SseDecoder()
        val channel = response.bodyAsChannel()
        while (true) {
            val line = channel.readUTF8Line() ?: break
            val data = sse.onLine(line) ?: continue
            if (data == "[DONE]") break
            decoder.decode(payloadObject(data)).forEach { emit(it) }
        }
    }

    private fun payloadObject(payload: String): JsonObject =
        runCatching { json.parseToJsonElement(payload) as? JsonObject }.getOrNull()
            ?: throw ModelProtocolException("invalid stream event")

    private fun decodeResponse(body: String): ChatReply {
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            ?: throw ModelProtocolException("invalid response body")
        root.wireObject("error")?.let { error ->
            throw ModelProtocolException(error.wireString("message") ?: "request failed")
        }
        val text = buildString {
            root.wireArray("output")?.forEach { element ->
                val item = element as? JsonObject ?: return@forEach
                if (item.wireString("type") != "message") return@forEach
                item.wireArray("content")?.forEach { part ->
                    val block = part as? JsonObject ?: return@forEach
                    if (block.wireString("type") == "output_text") {
                        block.wireText("text")?.let { append(it) }
                    }
                }
            }
        }
        val calls = buildList {
            root.wireArray("output")?.forEach { element ->
                val item = element as? JsonObject ?: return@forEach
                if (item.wireString("type") != "function_call") return@forEach
                val name = item.wireString("name") ?: return@forEach
                add(
                    CompletedToolCall(
                        id = item.wireString("call_id") ?: item.wireString("id") ?: "call_${size}",
                        name = name,
                        arguments = item.wireString("arguments") ?: "{}",
                    ),
                )
            }
        }
        return ChatReply(text = text, model = root.wireString("model"), toolCalls = calls)
    }

    private fun payload(request: ChatCompletionRequest, stream: Boolean): String = buildJsonObject {
        put("model", request.model)
        val instructions = request.messages
            .filter { it.role == "system" }
            .map { it.textContent() }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
        if (instructions.isNotBlank()) put("instructions", instructions)
        putJsonArray("input") {
            request.messages.filter { it.role != "system" }.forEach { addMessage(it) }
        }
        put("stream", stream)
        request.tools?.takeIf { it.isNotEmpty() }?.let { tools ->
            putJsonArray("tools") {
                tools.forEach { tool ->
                    add(
                        buildJsonObject {
                            put("type", "function")
                            put("name", tool.function.name)
                            put("description", tool.function.description)
                            put("parameters", tool.function.parameters)
                        },
                    )
                }
            }
        }
        request.toolChoice?.let { put("tool_choice", it) }
        val effort = request.reasoningEffort
            ?.takeIf { it != "none" }
            ?.let { if (it == "xhigh" || it == "max") "high" else it }
        if (effort != null) {
            putJsonObject("reasoning") {
                put("effort", effort)
                put("summary", "auto")
            }
        } else {
            request.temperature?.let { put("temperature", it) }
        }
        request.maxTokens?.let { put("max_output_tokens", it) }
    }.toString()

    private fun JsonArrayBuilder.addMessage(message: ChatMessageDto) {
        when (message.role) {
            "assistant" -> {
                val text = message.textContent()
                if (text.isNotBlank()) {
                    add(
                        buildJsonObject {
                            put("role", "assistant")
                            put("content", text)
                        },
                    )
                }
                message.toolCalls?.forEach { call ->
                    val name = call.function.name ?: return@forEach
                    add(
                        buildJsonObject {
                            put("type", "function_call")
                            put("call_id", call.id.orEmpty())
                            put("name", name)
                            put("arguments", call.function.arguments ?: "{}")
                        },
                    )
                }
            }
            "tool" -> {
                val callId = message.toolCallId ?: return
                add(
                    buildJsonObject {
                        put("type", "function_call_output")
                        put("call_id", callId)
                        put("output", message.textContent())
                    },
                )
            }
            else -> addUserMessage(message)
        }
    }

    private fun JsonArrayBuilder.addUserMessage(message: ChatMessageDto) {
        when (val content = message.content) {
            is MessageContent.Text -> {
                if (content.value.isNotBlank()) {
                    add(
                        buildJsonObject {
                            put("role", message.role)
                            put("content", content.value)
                        },
                    )
                }
            }
            is MessageContent.Parts -> {
                val parts = content.parts.mapNotNull { part ->
                    when (part.type) {
                        "text" -> part.text?.takeIf { it.isNotBlank() }?.let { text ->
                            buildJsonObject {
                                put("type", "input_text")
                                put("text", text)
                            }
                        }
                        "image_url" -> part.imageUrl?.url?.let { url ->
                            buildJsonObject {
                                put("type", "input_image")
                                put("image_url", url)
                                part.imageUrl.detail?.let { put("detail", it) }
                            }
                        }
                        else -> null
                    }
                }
                if (parts.isNotEmpty()) {
                    add(
                        buildJsonObject {
                            put("role", message.role)
                            put("content", kotlinx.serialization.json.JsonArray(parts))
                        },
                    )
                }
            }
            null -> Unit
        }
    }
}

private class ResponseStreamDecoder {
    private val indices = mutableMapOf<String, Int>()
    private val argumentDeltas = mutableSetOf<String>()
    private var nextIndex = 0
    private var hasToolCalls = false
    private var inputTokens = 0

    fun decode(root: JsonObject): List<ChatStreamEvent> {
        val events = mutableListOf<ChatStreamEvent>()
        when (val type = root.wireString("type")) {
            "response.output_text.delta", "response.refusal.delta" -> {
                root.wireText("delta")?.let { events += ChatStreamEvent.Text(it) }
            }
            "response.reasoning_summary_text.delta", "response.reasoning_text.delta" -> {
                root.wireText("delta")?.let { events += ChatStreamEvent.Reasoning(it) }
            }
            "response.output_item.added" -> {
                val item = root.wireObject("item") ?: return events
                if (item.wireString("type") == "function_call") {
                    val key = itemKey(item, root)
                    val index = indexFor(key)
                    hasToolCalls = true
                    events += ChatStreamEvent.ToolDelta(
                        index = index,
                        id = item.wireString("call_id") ?: item.wireString("id"),
                        name = item.wireString("name"),
                        arguments = item.wireString("arguments"),
                    )
                }
            }
            "response.function_call_arguments.delta" -> {
                val key = root.wireString("item_id") ?: return events
                val index = indexFor(key)
                argumentDeltas += key
                hasToolCalls = true
                root.wireText("delta")?.let {
                    events += ChatStreamEvent.ToolDelta(index, null, null, it)
                }
            }
            "response.output_item.done" -> {
                val item = root.wireObject("item") ?: return events
                if (item.wireString("type") != "function_call") return events
                val key = itemKey(item, root)
                val index = indexFor(key)
                hasToolCalls = true
                if (key !in argumentDeltas) {
                    events += ChatStreamEvent.ToolDelta(
                        index = index,
                        id = item.wireString("call_id") ?: item.wireString("id"),
                        name = item.wireString("name"),
                        arguments = item.wireString("arguments"),
                    )
                }
            }
            "response.completed", "response.incomplete", "response.failed" -> {
                val response = root.wireObject("response")
                val usage = response?.wireObject("usage")
                inputTokens = usage?.wireInt("input_tokens") ?: inputTokens
                val output = usage?.wireInt("output_tokens") ?: 0
                if (usage != null) {
                    events += ChatStreamEvent.Usage(
                        prompt = inputTokens,
                        completion = output,
                        total = usage.wireInt("total_tokens") ?: (inputTokens + output),
                        cached = usage.wireObject("input_tokens_details")?.wireInt("cached_tokens") ?: 0,
                    )
                }
                if (type == "response.failed") {
                    val message = response?.wireObject("error")?.wireString("message")
                        ?: root.wireObject("error")?.wireString("message")
                        ?: "response failed"
                    throw ModelProtocolException(message)
                }
                val reason = when {
                    hasToolCalls -> "tool_calls"
                    type == "response.incomplete" -> "incomplete"
                    else -> "stop"
                }
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

    private fun itemKey(item: JsonObject, root: JsonObject): String =
        item.wireString("id")
            ?: item.wireString("call_id")
            ?: root.wireString("item_id")
            ?: "item_${indices.size}"

    private fun indexFor(key: String): Int = indices.getOrPut(key) { nextIndex++ }
}
