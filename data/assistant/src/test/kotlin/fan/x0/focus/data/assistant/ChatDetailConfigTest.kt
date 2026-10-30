package fan.x0.focus.data.assistant

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatDetailConfigTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun orderAndEnabledRoundTrip() {
        val config = ChatDetailConfig(
            order = listOf(
                ChatStatField.Duration,
                ChatStatField.Model,
                ChatStatField.Provider,
                ChatStatField.Input,
                ChatStatField.Output,
                ChatStatField.Total,
                ChatStatField.Cached,
                ChatStatField.Round,
                ChatStatField.Step,
            ),
            enabled = setOf(ChatStatField.Provider, ChatStatField.Duration),
        )
        val decoded = json.decodeFromString(
            ChatDetailConfig.serializer(),
            json.encodeToString(ChatDetailConfig.serializer(), config),
        )
        assertEquals(config.order, decoded.order)
        assertEquals(config.enabled, decoded.enabled)
    }

    @Test
    fun normalizationRestoresMissingFields() {
        val config = ChatDetailConfig(
            order = listOf(ChatStatField.Model),
            enabled = setOf(ChatStatField.Model),
        ).normalized()
        assertEquals(ChatDetailConfig.DEFAULT_ORDER.size, config.order.size)
        assertEquals(ChatStatField.Model, config.order.first())
    }

    @Test
    fun statsMergeKeepsProviderAndAddsUsage() {
        val total = MessageStats(inputTokens = 10, outputTokens = 2, totalTokens = 12)
            .merge(
                MessageStats(
                    inputTokens = 20,
                    outputTokens = 3,
                    totalTokens = 23,
                    cachedTokens = 8,
                    providerName = "DeepSeek",
                    model = "deepseek-chat",
                    round = 2,
                    step = 3,
                ),
            )
        assertEquals(30, total.inputTokens)
        assertEquals(5, total.outputTokens)
        assertEquals(8, total.cachedTokens)
        assertEquals(35, total.totalTokens)
        assertEquals("DeepSeek", total.providerName)
        assertEquals(2, total.round)
    }
}
