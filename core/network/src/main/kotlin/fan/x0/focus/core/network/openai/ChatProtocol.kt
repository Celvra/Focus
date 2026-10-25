package fan.x0.focus.core.network.openai

enum class ChatProtocol {
    ChatCompletions,
    Responses,
    AnthropicMessages,
}

data class ChatReply(
    val text: String,
    val model: String?,
    val toolCalls: List<CompletedToolCall> = emptyList(),
)
