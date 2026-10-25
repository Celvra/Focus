package fan.x0.focus.core.network.openai

import fan.x0.focus.core.network.NetworkExceptionMapper
import io.ktor.client.HttpClient
import fan.x0.focus.core.network.allowLongStream
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

data class ModelEndpoint(val baseUrl: String, val model: String) {
    val rootUrl: String
        get() {
            var value = baseUrl.trim().trimEnd('/')
            listOf("/chat/completions", "/responses", "/messages").forEach { suffix ->
                if (value.endsWith(suffix)) value = value.removeSuffix(suffix).trimEnd('/')
            }
            return value
        }

    val chatUrl: String get() = "$rootUrl/chat/completions"

    val responsesUrl: String get() = "$rootUrl/responses"

    val messagesUrl: String get() = "$rootUrl/messages"

    val isUsable: Boolean
        get() = model.isNotBlank() && (baseUrl.startsWith("https://") || baseUrl.startsWith("http://"))
}

class ModelProtocolException(message: String) : Exception(message)

class OpenAiChatClient(
    private val http: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = false },
) {
    suspend fun complete(
        endpoint: ModelEndpoint,
        apiKey: String,
        request: ChatCompletionRequest,
    ): ChatCompletionResponse {
        val response = try {
            http.post(endpoint.chatUrl) {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                header(HttpHeaders.Accept, "application/json")
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(ChatCompletionRequest.serializer(), request))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw NetworkExceptionMapper.mapOrNull(e) ?: e
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw NetworkExceptionMapper.http(response.status.value, body)
        }
        return runCatching { json.decodeFromString(ChatCompletionResponse.serializer(), body) }
            .getOrElse { throw ModelProtocolException(it.message ?: "invalid response body") }
    }

    fun stream(
        endpoint: ModelEndpoint,
        apiKey: String,
        request: ChatCompletionRequest,
    ): Flow<ChatStreamEvent> = flow {
        val response = try {
            http.post(endpoint.chatUrl) {
                allowLongStream()
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                header(HttpHeaders.Accept, "text/event-stream")
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(ChatCompletionRequest.serializer(), request))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw NetworkExceptionMapper.mapOrNull(e) ?: e
        }
        if (!response.status.isSuccess()) {
            val body = response.bodyAsText()
            throw NetworkExceptionMapper.http(response.status.value, body)
        }
        val contentType = response.headers[HttpHeaders.ContentType].orEmpty()
        if (contentType.contains("application/json")) {
            val body = response.bodyAsText()
            val chunk = runCatching { json.decodeFromString(ChatCompletionResponse.serializer(), body) }
                .getOrElse { throw ModelProtocolException(it.message ?: "invalid response body") }
            chunk.choices.forEach { choice ->
                choice.message?.let { message ->
                    (message.reasoningContent?.takeIf { it.isNotBlank() }
                        ?: message.reasoning?.takeIf { it.isNotBlank() })
                        ?.let { emit(ChatStreamEvent.Reasoning(it)) }
                    message.content?.takeIf { it.isNotBlank() }?.let { emit(ChatStreamEvent.Text(it)) }
                }
                choice.message?.toolCalls?.forEachIndexed { index, call ->
                    emit(
                        ChatStreamEvent.ToolDelta(
                            index = index,
                            id = call.id,
                            name = call.function?.name,
                            arguments = call.function?.arguments,
                        ),
                    )
                }
                emit(ChatStreamEvent.Finished(choice.finishReason))
            }
            return@flow
        }
        val channel = response.bodyAsChannel()
        val decoder = SseDecoder()
        while (true) {
            val line = channel.readUTF8Line() ?: break
            val payload = decoder.onLine(line) ?: continue
            if (payload == "[DONE]") break
            for (event in decodeChunk(payload)) emit(event)
        }
    }

    private fun decodeChunk(payload: String): List<ChatStreamEvent> {
        val chunk = runCatching { json.decodeFromString(ChatCompletionResponse.serializer(), payload) }
            .getOrElse {
                val error = NetworkExceptionMapper.decodeError(payload)
                if (error != null) {
                    throw ModelProtocolException(error.message ?: error.title ?: "stream error")
                }
                throw ModelProtocolException(it.message ?: "invalid stream chunk")
            }
        val events = mutableListOf<ChatStreamEvent>()
        chunk.usage?.let {
            events += ChatStreamEvent.Usage(it.promptTokens, it.completionTokens, it.totalTokens)
        }
        chunk.choices.forEach { choice: ChoiceDto ->
            choice.delta?.let { delta ->
                (delta.reasoningContent?.takeIf { it.isNotBlank() } ?: delta.reasoning?.takeIf { it.isNotBlank() })
                    ?.let { events += ChatStreamEvent.Reasoning(it) }
                delta.content?.let { events += ChatStreamEvent.Text(it) }
            }
            choice.delta?.toolCalls?.forEach { call: ToolCallDeltaDto ->
                events += ChatStreamEvent.ToolDelta(
                    index = call.index,
                    id = call.id,
                    name = call.function?.name,
                    arguments = call.function?.arguments,
                )
            }
            choice.finishReason?.let { events += ChatStreamEvent.Finished(it) }
        }
        return events
    }
}
