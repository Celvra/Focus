package fan.x0.focus.core.network.openai

import fan.x0.focus.core.network.NetworkExceptionMapper
import fan.x0.focus.core.error.NetworkException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

enum class ConnectionStage { Streaming, ToolCalling }

class ConnectionTestException(
    val stage: ConnectionStage,
    val detail: String?,
    cause: Throwable? = null,
) : Exception(detail, cause)

data class ConnectionReport(
    val latencyMs: Long,
    val echoedModel: String?,
)

class ModelConnectionTester(private val client: ChatWireClient) {
    suspend fun test(
        endpoint: ModelEndpoint,
        apiKey: String,
        protocol: ChatProtocol = ChatProtocol.ChatCompletions,
    ): ConnectionReport {
        val started = System.currentTimeMillis()
        val textEvents = try {
            client.stream(
                endpoint = endpoint,
                protocol = protocol,
                apiKey = apiKey,
                request = ChatCompletionRequest(
                    model = endpoint.model,
                    messages = listOf(
                        ChatMessageDto(
                            role = "user",
                            content = "Reply with the single word: ready",
                        ),
                    ),
                    stream = true,
                    temperature = 0.0,
                ),
            ).toList()
        } catch (e: CancellationException) {
            throw e
        } catch (e: NetworkException) {
            throw ConnectionTestException(ConnectionStage.Streaming, describe(e), e)
        } catch (e: Exception) {
            throw ConnectionTestException(ConnectionStage.Streaming, e.message, e)
        }
        val text = textEvents.filterIsInstance<ChatStreamEvent.Text>().joinToString("") { it.delta }
        if (text.isBlank()) {
            throw ConnectionTestException(ConnectionStage.Streaming, "empty stream")
        }

        val toolResponse = try {
            client.complete(
                endpoint = endpoint,
                protocol = protocol,
                apiKey = apiKey,
                request = ChatCompletionRequest(
                    model = endpoint.model,
                    messages = listOf(
                        ChatMessageDto(
                            role = "user",
                            content = "Call the ping function with value 1. Do not reply with text.",
                        ),
                    ),
                    tools = listOf(ToolDto(function = pingTool())),
                    toolChoice = "auto",
                    temperature = 0.0,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: NetworkException.Http) {
            throw ConnectionTestException(ConnectionStage.ToolCalling, describe(e), e)
        } catch (e: Exception) {
            val mapped = NetworkExceptionMapper.mapOrNull(e)
            val detail = if (mapped != null) describe(mapped) else e.message
            throw ConnectionTestException(ConnectionStage.ToolCalling, detail, e)
        }
        if (toolResponse.toolCalls.isEmpty()) {
            throw ConnectionTestException(ConnectionStage.ToolCalling, "no tool call returned")
        }
        return ConnectionReport(
            latencyMs = System.currentTimeMillis() - started,
            echoedModel = toolResponse.model,
        )
    }

    private fun describe(error: NetworkException): String = when (error) {
        is NetworkException.Http -> {
            val server = listOfNotNull(error.error?.title, error.error?.message).joinToString(": ")
            val body = error.bodySnippet?.takeIf { server.isBlank() && !it.startsWith("<") }
            listOf("HTTP ${error.statusCode}", server.takeIf { it.isNotBlank() }, body)
                .filterNotNull()
                .joinToString(" · ")
        }
        is NetworkException.Timeout -> "Request timed out"
        is NetworkException.Connection -> "Could not reach the endpoint"
        is NetworkException.SslHandshake -> "TLS handshake failed"
        is NetworkException.Serialization -> "Invalid response body"
        is NetworkException.Unknown -> "Network request failed"
    }

    private fun pingTool(): ToolFunctionDto = ToolFunctionDto(
        name = "ping",
        description = "Test tool. Echoes the given value.",
        parameters = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("value") {
                    put("type", "string")
                    put("description", "Any value")
                }
            }
            put("required", kotlinx.serialization.json.buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("value")) })
            put("additionalProperties", false)
        },
    )
}

@Suppress("unused")
private val emptySchema = JsonObject(emptyMap())
