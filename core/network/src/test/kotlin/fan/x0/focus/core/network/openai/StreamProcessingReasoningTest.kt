package fan.x0.focus.core.network.openai

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamProcessingReasoningTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `accumulates reasoning and answer independently`() {
        val accumulator = StreamAccumulator()
        accumulator.apply(ChatStreamEvent.Reasoning("plan "))
        accumulator.apply(ChatStreamEvent.Reasoning("first"))
        accumulator.apply(ChatStreamEvent.Text("answer"))

        val result = accumulator.result()
        assertEquals("plan first", result.reasoning)
        assertEquals("answer", result.text)
    }

    @Test
    fun `decodes both reasoning fields from streamed deltas`() {
        val reasoningContent = json.decodeFromString(
            ChatCompletionResponse.serializer(),
            """{"choices":[{"delta":{"reasoning_content":"deepseek"}}]}""",
        ).choices.single().delta?.reasoningContent
        val reasoning = json.decodeFromString(
            ChatCompletionResponse.serializer(),
            """{"choices":[{"delta":{"reasoning":"openrouter"}}]}""",
        ).choices.single().delta?.reasoning

        assertEquals("deepseek", reasoningContent)
        assertEquals("openrouter", reasoning)
    }

    @Test
    fun `decodes reasoning fields from regular completion messages`() {
        val response = json.decodeFromString(
            ChatCompletionResponse.serializer(),
            """{"choices":[{"message":{"role":"assistant","reasoning":"thinking","content":"done"}}]}""",
        )

        assertEquals("thinking", response.choices.single().message?.reasoning)
        assertEquals("done", response.choices.single().message?.content)
    }
}
