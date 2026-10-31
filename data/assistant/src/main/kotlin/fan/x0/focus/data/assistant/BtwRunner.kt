package fan.x0.focus.data.assistant

import fan.x0.focus.core.error.AppError
import fan.x0.focus.core.error.AppErrorType
import fan.x0.focus.core.error.toAppError
import fan.x0.focus.core.network.ProviderRequestConfig
import fan.x0.focus.core.network.openai.ChatCompletionRequest
import fan.x0.focus.core.network.openai.ChatMessageDto
import fan.x0.focus.core.network.openai.ChatProtocol
import fan.x0.focus.core.network.openai.ChatStreamEvent
import fan.x0.focus.core.network.openai.ChatWireClient
import fan.x0.focus.core.network.openai.ModelEndpoint
import fan.x0.focus.core.network.openai.ModelProtocolException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.koin.core.annotation.Single

@Single
class BtwRunner(
    private val client: ChatWireClient,
    private val settings: AssistantSettings,
    private val secrets: SecretStore,
    private val chats: ChatStore,
    private val prompt: PromptBuilder,
) {
    fun ask(
        conversationId: String,
        question: String,
        options: TurnOptions,
    ): Flow<ChatEvent> = flow {
        val config = settings.configOnce()
        val provider = config.providers.firstOrNull { it.id == options.providerId }
            ?: config.activeProvider
        val endpoint = ModelEndpoint(
            baseUrl = provider?.baseUrl.orEmpty(),
            model = options.model.ifBlank { provider?.model.orEmpty() },
        )
        if (!endpoint.isUsable) {
            emit(
                ChatEvent.Failed(
                    AppError(AppErrorType.Generic, serverMessage = "Set the model connection first."),
                ),
            )
            return@flow
        }
        val apiKey = provider?.let { secrets.apiKey(it.id) }.orEmpty()
        if (provider?.requiresKey != false && apiKey.isBlank()) {
            emit(
                ChatEvent.Failed(
                    AppError(AppErrorType.Unauthorized, serverMessage = "Set the API key first."),
                ),
            )
            return@flow
        }
        val requestConfig = provider?.requestConfig(apiKey) ?: ProviderRequestConfig(apiKey = apiKey)
        val protocol = provider?.protocol ?: ChatProtocol.ChatCompletions
        val history = chats.messages(conversationId, limit = CONTEXT_MESSAGES)
        val messages = buildList {
            add(ChatMessageDto(role = "system", content = prompt.btwPrompt()))
            addAll(history.toBtwMessages())
            add(ChatMessageDto(role = "user", content = question))
        }
        val request = ChatCompletionRequest(
            model = endpoint.model,
            messages = messages,
            stream = true,
            temperature = 0.4,
            reasoningEffort = options.reasoning.wire,
        )
        var answered = false
        try {
            client.stream(endpoint, protocol, requestConfig, request).collect { event ->
                when (event) {
                    is ChatStreamEvent.Text -> {
                        if (event.delta.isNotBlank()) {
                            answered = true
                            emit(ChatEvent.Delta(event.delta))
                        }
                    }
                    is ChatStreamEvent.Reasoning -> Unit
                    else -> Unit
                }
            }
            if (!answered) throw ModelProtocolException("stream ended before a reply")
            emit(ChatEvent.Completed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            emit(ChatEvent.Failed(e.toAppError()))
        }
    }

    private fun List<ChatMessage>.toBtwMessages(): List<ChatMessageDto> = mapNotNull { message ->
        val content = message.content.trim()
        if (content.isBlank()) return@mapNotNull null
        when (message.role) {
            ChatRole.User -> ChatMessageDto(role = "user", content = content)
            ChatRole.Assistant -> ChatMessageDto(role = "assistant", content = content)
            ChatRole.System -> ChatMessageDto(role = "user", content = "[app notice] $content")
            ChatRole.Tool -> null
        }
    }

    private companion object {
        const val CONTEXT_MESSAGES = 30
    }
}
