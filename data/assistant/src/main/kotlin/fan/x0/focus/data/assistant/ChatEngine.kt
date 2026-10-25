package fan.x0.focus.data.assistant

import fan.x0.focus.core.error.AppError
import fan.x0.focus.core.error.AppErrorType
import fan.x0.focus.core.error.NetworkException
import fan.x0.focus.core.error.toAppError
import fan.x0.focus.core.network.openai.ChatCompletionRequest
import fan.x0.focus.core.network.openai.ChatMessageDto
import fan.x0.focus.core.network.openai.ChatProtocol
import fan.x0.focus.core.network.openai.ChatStreamEvent
import fan.x0.focus.core.network.openai.ChatWireClient
import fan.x0.focus.core.network.openai.FunctionCallDto
import fan.x0.focus.core.network.openai.MessageContent
import fan.x0.focus.core.network.openai.ModelEndpoint
import fan.x0.focus.core.network.openai.ModelProtocolException
import fan.x0.focus.core.network.openai.StreamAccumulator
import fan.x0.focus.core.network.openai.ToolCallDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred

enum class ChatMode { Chat, Onboarding }

data class TurnOptions(
    val providerId: String = "",
    val model: String = "",
    val reasoning: ReasoningLevel = ReasoningLevel.Auto,
    val attachments: List<ChatAttachment> = emptyList(),
)

sealed interface ChatEvent {
    data class Delta(val text: String) : ChatEvent
    data class ReasoningDelta(val text: String) : ChatEvent
    data class UserMessageAppended(val turns: Int) : ChatEvent
    data class ToolStarted(val label: String) : ChatEvent
    data class ToolFinished(
        val label: String,
        val ok: Boolean,
        val summary: String,
        val tool: String? = null,
    ) : ChatEvent
    data class WorkingTextChanged(val text: String) : ChatEvent
    data class ApprovalRequested(val request: ApprovalRequest) : ChatEvent
    data class ApprovalResolved(val request: ApprovalRequest, val approved: Boolean) : ChatEvent
    data object Completed : ChatEvent
    data class Failed(val error: AppError) : ChatEvent
}

@Single
class ChatEngine(
    private val client: ChatWireClient,
    private val settings: AssistantSettings,
    private val secrets: SecretStore,
    private val chats: ChatStore,
    private val prompt: PromptBuilder,
    private val tools: ToolExecutor,
    private val approvals: ApprovalStore,
) {
    private val waivers = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    fun decide(approvalId: String, approved: Boolean) {
        waivers.computeIfAbsent(approvalId) { CompletableDeferred() }.complete(approved)
    }

    fun send(
        conversationId: String,
        text: String,
        mode: ChatMode = ChatMode.Chat,
        options: TurnOptions = TurnOptions(),
    ): Flow<ChatEvent> = flow {
        val config = if (mode == ChatMode.Chat) settings.configOnce() else null
        val provider = config?.providers?.firstOrNull { it.id == options.providerId } ?: config?.activeProvider
        val providerId = when (mode) {
            ChatMode.Onboarding -> OnboardingDefaults.PROVIDER_ID
            ChatMode.Chat -> provider?.id ?: config?.activeProviderId.orEmpty()
        }
        val baseUrl = when (mode) {
            ChatMode.Onboarding -> OnboardingDefaults.BASE_URL
            ChatMode.Chat -> provider?.baseUrl ?: config?.baseUrl.orEmpty()
        }
        val protocol = when (mode) {
            ChatMode.Onboarding -> ChatProtocol.ChatCompletions
            ChatMode.Chat -> provider?.protocol ?: ChatProtocol.ChatCompletions
        }
        val model = when (mode) {
            ChatMode.Onboarding -> OnboardingDefaults.MODEL
            ChatMode.Chat -> options.model.ifBlank { provider?.model.orEmpty() }
        }
        val endpoint = ModelEndpoint(baseUrl, model)
        if (!endpoint.isUsable) {
            emit(ChatEvent.Failed(AppError(AppErrorType.Generic, serverMessage = "Set the model connection first.")))
            return@flow
        }
        val apiKey = when (mode) {
            ChatMode.Onboarding -> OnboardingDefaults.API_KEY
            ChatMode.Chat -> secrets.apiKey(providerId)
        }
        if (apiKey.isNullOrBlank()) {
            emit(ChatEvent.Failed(AppError(AppErrorType.Unauthorized, serverMessage = "Set the API key first.")))
            return@flow
        }

        try {
            if (text.isNotBlank() || options.attachments.isNotEmpty()) {
                chats.append(
                    conversationId = conversationId,
                    role = ChatRole.User,
                    content = text,
                    attachments = options.attachments,
                )
                if (mode == ChatMode.Chat) {
                    emit(ChatEvent.UserMessageAppended(chats.userTurnCount(conversationId)))
                }
            }
            val baseSpecs = when (mode) {
                ChatMode.Chat -> tools.specs
                ChatMode.Onboarding -> ToolCatalog.onboarding()
            }
            var completesOnboarding = false
            repeat(MAX_ROUNDS) {
                val specs = if (completesOnboarding) {
                    baseSpecs.filterNot { it.name == "complete_onboarding" }
                } else {
                    baseSpecs
                }
                val history = chats.messages(conversationId)
                val system = when (mode) {
                    ChatMode.Chat -> prompt.chatPrompt(chats.conversation(conversationId)?.title.orEmpty())
                    ChatMode.Onboarding -> prompt.onboardingPrompt(
                        turn = history.count { it.role == ChatRole.User },
                    )
                }
                val messages = buildList {
                    add(ChatMessageDto(role = "system", content = system))
                    addAll(history.toDtos())
                    if (history.isEmpty()) {
                        add(ChatMessageDto(role = "user", content = "Begin the setup conversation now."))
                    }
                }
                val request = ChatCompletionRequest(
                    model = endpoint.model,
                    messages = messages,
                    stream = true,
                    tools = specs.map { it.toDto() },
                    toolChoice = "auto",
                    temperature = 0.4,
                    reasoningEffort = options.reasoning.wire,
                )
                val accumulator = StreamAccumulator()
                client.stream(endpoint, protocol, apiKey, request).collect { event ->
                    accumulator.apply(event)
                    when (event) {
                        is ChatStreamEvent.Text -> emit(ChatEvent.Delta(event.delta))
                        is ChatStreamEvent.Reasoning -> emit(ChatEvent.ReasoningDelta(event.delta))
                        else -> Unit
                    }
                }
                val message = accumulator.result()
                chats.append(
                    conversationId = conversationId,
                    role = ChatRole.Assistant,
                    content = message.text,
                    reasoning = message.reasoning,
                    toolCalls = message.toolCalls.map {
                        ToolCallRecord(it.id, it.name, ToolCatalog.redactArguments(it.name, it.arguments))
                    },
                )
                if (message.toolCalls.isEmpty()) {
                    emit(ChatEvent.Completed)
                    if (completesOnboarding) finishOnboarding(conversationId)
                    return@flow
                }

                for (call in message.toolCalls) {
                    val label = ToolLabels.of(call.name)
                    val result = tools.execute(call, conversationId)
                    if (mode == ChatMode.Onboarding && result.completesOnboarding) {
                        completesOnboarding = true
                    }
                    result.workingText?.let { emit(ChatEvent.WorkingTextChanged(it)) }
                    val approval = result.approval
                    if (approval == null) {
                        if (call.name == ToolCatalog.SET_WORKING_TEXT) {
                            chats.append(
                                conversationId = conversationId,
                                role = ChatRole.Tool,
                                content = result.content,
                                toolCallId = call.id,
                                name = call.name,
                                label = label,
                            )
                            continue
                        }
                        emit(ChatEvent.ToolStarted(label))
                        emit(ChatEvent.ToolFinished(label, result.ok, result.summary, call.name))
                        chats.append(
                            conversationId = conversationId,
                            role = ChatRole.Tool,
                            content = result.content,
                            toolCallId = call.id,
                            name = call.name,
                            label = label,
                        )
                        continue
                    }

                    emit(ChatEvent.ApprovalRequested(approval))
                    val approved = awaitDecision(approval.id)
                    emit(ChatEvent.ApprovalResolved(approval, approved))
                    val lines = mutableListOf<String>()
                    if (approved) {
                        approval.steps.forEach { step ->
                            emit(ChatEvent.ToolStarted(step.label))
                            val outcome = tools.runStep(step, conversationId)
                            emit(ChatEvent.ToolFinished(step.label, outcome.ok, outcome.summary, step.tool))
                            lines += "${step.label}: ${if (outcome.ok) "done" else "failed"}. ${outcome.summary}"
                        }
                        approvals.decide(approval.id, ApprovalStatus.Approved, lines.joinToString("\n").take(500))
                    } else {
                        lines += "The user rejected this action."
                        approvals.decide(approval.id, ApprovalStatus.Rejected, null)
                    }
                    chats.append(
                        conversationId = conversationId,
                        role = ChatRole.Tool,
                        content = lines.joinToString("\n"),
                        toolCallId = call.id,
                        name = call.name,
                        label = approval.steps.map { it.label }
                            .filter { it.isNotBlank() }
                            .takeIf { it.isNotEmpty() }
                            ?.joinToString("\n"),
                    )
                }
            }
            emit(ChatEvent.Completed)
            if (completesOnboarding) finishOnboarding(conversationId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            emit(ChatEvent.Failed(mapError(e)))
        }
    }

    private suspend fun finishOnboarding(conversationId: String) {
        withContext(NonCancellable) {
            chats.clear(conversationId)
            settings.completeOnboarding()
        }
    }

    private suspend fun awaitDecision(approvalId: String): Boolean {
        val deferred = waivers.computeIfAbsent(approvalId) { CompletableDeferred() }
        val decision = withTimeoutOrNull(APPROVAL_TIMEOUT_MS) { deferred.await() }
        waivers.remove(approvalId)
        return decision ?: false
    }

    private fun List<ChatMessage>.toDtos(): List<ChatMessageDto> = mapNotNull { message ->
        when (message.role) {
            ChatRole.User -> ChatMessageDto(
                role = "user",
                content = message.toUserContent(),
            )
            ChatRole.Assistant -> ChatMessageDto(
                role = "assistant",
                content = message.content.ifBlank { null }?.let(MessageContent::Text),
                reasoningContent = message.reasoning.ifBlank { null },
                toolCalls = message.toolCalls.map { call ->
                    ToolCallDto(
                        id = call.id,
                        function = FunctionCallDto(name = call.name, arguments = call.arguments),
                    )
                }.ifEmpty { null },
            )
            ChatRole.Tool -> ChatMessageDto(
                role = "tool",
                content = message.content.orEmpty(),
                toolCallId = message.toolCallId,
                name = message.name,
            )
            ChatRole.System -> null
        }
    }

    private fun ChatMessage.toUserContent(): MessageContent {
        val images = attachments.filter { it.kind == AttachmentKind.Image }.mapNotNull { it.dataUrl }
        val text = buildString {
            append(content)
            attachments.filter { it.kind == AttachmentKind.Text }.forEach { attach ->
                val body = attach.text ?: return@forEach
                if (body.isBlank()) return@forEach
                if (isNotEmpty()) append("\n\n")
                append("<<file: ").append(attach.name).append(">>\n")
                append(body)
                append("\n<<end file>>")
            }
        }
        return MessageContent.of(text.trim(), images)
    }

    private fun mapError(e: Throwable): AppError = when (e) {
        is NetworkException -> e.toAppError()
        is ModelProtocolException -> AppError(AppErrorType.Generic, serverMessage = e.message)
        else -> AppError(AppErrorType.Generic, serverMessage = e.message ?: e.javaClass.simpleName)
    }

    companion object {
        private const val MAX_ROUNDS = 8
        private const val APPROVAL_TIMEOUT_MS = 15 * 60 * 1000L
    }
}
