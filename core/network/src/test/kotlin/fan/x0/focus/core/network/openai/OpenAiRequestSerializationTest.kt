package fan.x0.focus.core.network.openai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiRequestSerializationTest {

    // Same config as OpenAiChatClient: encodeDefaults is off, so @EncodeDefault must keep type.
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    @Test
    fun `tool definition keeps function type`() {
        val request = ChatCompletionRequest(
            model = "deepseek-chat",
            messages = listOf(ChatMessageDto(role = "user", content = "hi")),
            stream = true,
            tools = listOf(
                ToolDto(
                    function = ToolFunctionDto(
                        name = "ping",
                        description = "test",
                        parameters = buildJsonObject {
                            put("type", "object")
                            put("required", buildJsonArray { add("value") })
                        },
                    ),
                ),
            ),
            toolChoice = "auto",
        )

        val body = json.encodeToString(ChatCompletionRequest.serializer(), request)

        assertTrue(body, body.contains("\"type\":\"function\""))
    }

    @Test
    fun `assistant tool call keeps function type`() {
        val request = ChatCompletionRequest(
            model = "deepseek-chat",
            messages = listOf(
                ChatMessageDto(
                    role = "assistant",
                    toolCalls = listOf(
                        ToolCallDto(
                            id = "call_1",
                            function = FunctionCallDto(name = "ping", arguments = "{}"),
                        ),
                    ),
                ),
            ),
            stream = true,
        )

        val body = json.encodeToString(ChatCompletionRequest.serializer(), request)

        assertTrue(body, body.contains("\"type\":\"function\""))
    }
}
