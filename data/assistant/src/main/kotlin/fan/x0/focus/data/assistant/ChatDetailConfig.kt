package fan.x0.focus.data.assistant

import kotlinx.serialization.Serializable

@Serializable
enum class ChatStatField {
    Provider,
    Model,
    Input,
    Output,
    Total,
    Cached,
    Round,
    Step,
    Duration,
}

@Serializable
data class ChatDetailConfig(
    val version: Int = 1,
    val order: List<ChatStatField> = DEFAULT_ORDER,
    val enabled: Set<ChatStatField> = DEFAULT_ENABLED,
) {
    fun normalized(): ChatDetailConfig {
        val known = order.filter { it in DEFAULT_ORDER }.distinct()
        val missing = DEFAULT_ORDER.filterNot { it in known }
        return copy(order = known + missing, enabled = enabled.intersect(DEFAULT_ORDER.toSet()))
    }

    companion object {
        val DEFAULT_ORDER = listOf(
            ChatStatField.Provider,
            ChatStatField.Model,
            ChatStatField.Input,
            ChatStatField.Output,
            ChatStatField.Total,
            ChatStatField.Cached,
            ChatStatField.Round,
            ChatStatField.Step,
            ChatStatField.Duration,
        )
        val DEFAULT_ENABLED = setOf(
            ChatStatField.Provider,
            ChatStatField.Model,
            ChatStatField.Input,
            ChatStatField.Output,
            ChatStatField.Total,
            ChatStatField.Cached,
            ChatStatField.Round,
            ChatStatField.Step,
            ChatStatField.Duration,
        )
    }
}
