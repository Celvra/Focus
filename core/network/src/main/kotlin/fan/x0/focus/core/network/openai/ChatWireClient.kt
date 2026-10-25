package fan.x0.focus.core.network.openai

import kotlinx.coroutines.flow.Flow

class ChatWireClient(
    private val chat: OpenAiChatClient,
    private val responses: OpenAiResponsesClient,
    private val anthropic: AnthropicChatClient,
) {
    fun stream(
        endpoint: ModelEndpoint,
        protocol: ChatProtocol,
        apiKey: String,
        request: ChatCompletionRequest,
    ): Flow<ChatStreamEvent> = when (protocol) {
        ChatProtocol.ChatCompletions -> chat.stream(endpoint, apiKey, request)
        ChatProtocol.Responses -> responses.stream(endpoint, apiKey, request)
        ChatProtocol.AnthropicMessages -> anthropic.stream(endpoint, apiKey, request)
    }

    suspend fun complete(
        endpoint: ModelEndpoint,
        protocol: ChatProtocol,
        apiKey: String,
        request: ChatCompletionRequest,
    ): ChatReply = when (protocol) {
        ChatProtocol.ChatCompletions -> chat.complete(endpoint, apiKey, request).toReply()
        ChatProtocol.Responses -> responses.complete(endpoint, apiKey, request)
        ChatProtocol.AnthropicMessages -> anthropic.complete(endpoint, apiKey, request)
    }
}

private fun ChatCompletionResponse.toReply(): ChatReply {
    val message = choices.firstOrNull()?.message
    val calls = message?.toolCalls?.mapIndexedNotNull { index, call ->
        val name = call.function?.name ?: return@mapIndexedNotNull null
        CompletedToolCall(
            id = call.id ?: "call_$index",
            name = name,
            arguments = call.function?.arguments ?: "{}",
        )
    }.orEmpty()
    return ChatReply(
        text = message?.content.orEmpty(),
        model = model,
        toolCalls = calls,
    )
}
