package fan.x0.focus.data.assistant

import kotlinx.serialization.Serializable

@Serializable
data class MessageStats(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val cachedTokens: Int = 0,
    val totalTokens: Int = 0,
    val providerId: String = "",
    val providerName: String = "",
    val model: String = "",
    val round: Int = 0,
    val step: Int = 0,
    val durationMs: Long = 0,
) {
    val hasUsage: Boolean
        get() = inputTokens > 0 || outputTokens > 0 || cachedTokens > 0 || totalTokens > 0

    fun merge(other: MessageStats): MessageStats = MessageStats(
        inputTokens = inputTokens + other.inputTokens,
        outputTokens = outputTokens + other.outputTokens,
        cachedTokens = cachedTokens + other.cachedTokens,
        totalTokens = totalTokens + other.totalTokens,
        providerId = other.providerId.ifBlank { providerId },
        providerName = other.providerName.ifBlank { providerName },
        model = other.model.ifBlank { model },
        round = maxOf(round, other.round),
        step = maxOf(step, other.step),
        durationMs = durationMs + other.durationMs,
    )
}
