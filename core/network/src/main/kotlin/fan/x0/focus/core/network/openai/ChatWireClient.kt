package fan.x0.focus.core.network.openai

import fan.x0.focus.core.network.ProviderRequestConfig
import kotlinx.coroutines.flow.Flow

class ChatWireClient(
    private val chat: OpenAiChatClient,
    private val responses: OpenAiResponsesClient,
    private val anthropic: AnthropicChatClient,
) {
    fun stream(
        endpoint: ModelEndpoint,
        protocol: ChatProtocol,
        config: ProviderRequestConfig,
        request: ChatCompletionRequest,
    ): Flow<ChatStreamEvent> = when (protocol) {
        ChatProtocol.ChatCompletions -> chat.stream(endpoint, config, request)
        ChatProtocol.Responses -> responses.stream(endpoint, config, request)
        ChatProtocol.AnthropicMessages -> anthropic.stream(endpoint, config, request)
    }

    suspend fun complete(
        endpoint: ModelEndpoint,
        protocol: ChatProtocol,
        config: ProviderRequestConfig,
        request: ChatCompletionRequest,
    ): ChatReply = when (protocol) {
        ChatProtocol.ChatCompletions -> chat.complete(endpoint, config, request).toReply()
        ChatProtocol.Responses -> responses.complete(endpoint, config, request)
        ChatProtocol.AnthropicMessages -> anthropic.complete(endpoint, config, request)
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
