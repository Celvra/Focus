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
import fan.x0.focus.core.network.openai.CompletedMessage
import fan.x0.focus.core.network.openai.CompletedToolCall
import fan.x0.focus.core.network.openai.FunctionCallDto
import fan.x0.focus.core.network.openai.MessageContent
import fan.x0.focus.core.network.openai.ModelEndpoint
import fan.x0.focus.core.network.openai.ModelProtocolException
import fan.x0.focus.core.network.openai.StreamAccumulator
import fan.x0.focus.core.network.openai.ToolCallDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap

enum class ChatMode { Chat, Onboarding }

data class TurnOptions(
    val providerId: String = "",
    val model: String = "",
    val reasoning: ReasoningLevel = ReasoningLevel.Auto,
    val attachments: List<ChatAttachment> = emptyList(),
    val agentMode: AgentMode = AgentMode.Build,
    val replyToId: Long? = null,
)

sealed interface ChatEvent {
    data class Delta(val text: String) : ChatEvent
    data class ReasoningDelta(val text: String) : ChatEvent
    data class UserMessageAppended(val turns: Int) : ChatEvent
    data class ToolStarted(
        val label: String,
        val id: String = "",
        val tool: String = "",
        val arguments: String = "",
    ) : ChatEvent
    data class ToolFinished(
        val label: String,
        val ok: Boolean,
        val summary: String,
        val tool: String? = null,
        val id: String = "",
        val output: String? = null,
    ) : ChatEvent
    data class WorkingTextChanged(val text: String) : ChatEvent
    data class ApprovalRequested(val request: ApprovalRequest) : ChatEvent
    data class ApprovalResolved(val request: ApprovalRequest, val approved: Boolean) : ChatEvent
    data class AskRequested(val request: AskRequest) : ChatEvent
    data class AskResolved(val request: AskRequest, val reply: AskReply) : ChatEvent
    data class PlanProposed(val plan: ProposedPlan, val request: ApprovalRequest) : ChatEvent
    data class PlanResolved(val plan: ProposedPlan, val approved: Boolean) : ChatEvent
    data class TodoUpdated(val todos: List<ConversationTodo>) : ChatEvent
    data class StatsUpdated(val stats: MessageStats) : ChatEvent
    data class RetryScheduled(val attempt: Int, val max: Int, val reason: String) : ChatEvent
    data object RetryReset : ChatEvent
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
    private val agent: AgentSettings,
    private val subAgents: SubAgentRunner,
) {
    private val waivers = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val askReplies = ConcurrentHashMap<String, CompletableDeferred<AskReply>>()

    fun decide(approvalId: String, approved: Boolean) {
        waivers.computeIfAbsent(approvalId) { CompletableDeferred() }.complete(approved)
    }

    fun reply(askId: String, answers: List<AskAnswer>, skipped: Boolean) {
        askReplies.computeIfAbsent(askId) { CompletableDeferred() }
            .complete(AskReply(id = askId, answers = answers, skipped = skipped))
    }

    fun send(
        conversationId: String,
        text: String,
        mode: ChatMode = ChatMode.Chat,
        options: TurnOptions = TurnOptions(),
        appendUser: Boolean = true,
    ): Flow<ChatEvent> = flow {
        val config = settings.configOnce()
        val savedActive = config.activeProvider
        val hasUsableSaved = savedActive != null &&
            savedActive.baseUrl.isNotBlank() &&
            savedActive.model.isNotBlank() &&
            (!savedActive.requiresKey || !secrets.apiKey(savedActive.id).isNullOrBlank())
        val useSaved = mode == ChatMode.Chat || hasUsableSaved
        val provider = config.providers.firstOrNull { it.id == options.providerId } ?: savedActive
        val providerId = when {
            !useSaved -> OnboardingDefaults.PROVIDER_ID
            else -> provider?.id ?: config.activeProviderId.orEmpty()
        }
        val baseUrl = when {
            !useSaved -> OnboardingDefaults.BASE_URL
            else -> provider?.baseUrl ?: config.baseUrl.orEmpty()
        }
        val protocol = when {
            !useSaved -> ChatProtocol.ChatCompletions
            else -> provider?.protocol ?: ChatProtocol.ChatCompletions
        }
        val model = when {
            !useSaved -> OnboardingDefaults.MODEL
            else -> options.model.ifBlank { provider?.model.orEmpty() }
        }
        val endpoint = ModelEndpoint(baseUrl, model)
        if (!endpoint.isUsable) {
            emit(ChatEvent.Failed(AppError(AppErrorType.Generic, serverMessage = "Set the model connection first.")))
            return@flow
        }
        val apiKey = when {
            !useSaved -> OnboardingDefaults.API_KEY
            else -> secrets.apiKey(providerId).orEmpty()
        }
        if (apiKey.isBlank() && (provider?.requiresKey != false || !useSaved)) {
            emit(ChatEvent.Failed(AppError(AppErrorType.Unauthorized, serverMessage = "Set the API key first.")))
            return@flow
        }

        val startedAt = System.currentTimeMillis()
        var agentMode = options.agentMode
        var totalStats = MessageStats(
            providerId = providerId,
            providerName = provider?.name ?: providerId,
            model = model,
        )
        var stepCount = 0
        var completesOnboarding = false
        val agentConfig = agent.configOnce()
        val retry = agentConfig.retry

        try {
            if (appendUser && (text.isNotBlank() || options.attachments.isNotEmpty())) {
                chats.append(
                    conversationId = conversationId,
                    role = ChatRole.User,
                    content = text,
                    attachments = options.attachments,
                    replyToId = options.replyToId,
                )
                if (mode == ChatMode.Chat) {
                    emit(ChatEvent.UserMessageAppended(chats.userTurnCount(conversationId)))
                }
            }
            val maxRounds = agentConfig.roundLimit
            repeat(maxRounds) { roundIndex ->
                val round = roundIndex + 1
                val specs = when (mode) {
                    ChatMode.Chat -> tools.specsFor(agentMode)
                    ChatMode.Onboarding -> ToolCatalog.onboarding()
                }.let { list ->
                    if (completesOnboarding) list.filterNot { it.name == "complete_onboarding" } else list
                }
                val history = chats.messages(conversationId)
                val system = when (mode) {
                    ChatMode.Chat -> prompt.chatPrompt(
                        title = chats.conversation(conversationId)?.title.orEmpty(),
                        mode = agentMode,
                    )
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
                val roundResult = streamRound(
                    endpoint = endpoint,
                    protocol = protocol,
                    apiKey = apiKey,
                    request = request,
                    retry = retry,
                    round = round,
                    step = stepCount,
                    totalStats = totalStats,
                    startedAt = startedAt,
                    emitStats = { stats -> emit(ChatEvent.StatsUpdated(stats)) },
                    emitRetry = { attempt, max, reason -> emit(ChatEvent.RetryScheduled(attempt, max, reason)) },
                    emitReset = { emit(ChatEvent.RetryReset) },
                )
                val message = roundResult.message
                totalStats = roundResult.stats.copy(
                    round = round,
                    step = stepCount,
                    durationMs = System.currentTimeMillis() - startedAt,
                )
                emit(ChatEvent.StatsUpdated(totalStats))
                chats.append(
                    conversationId = conversationId,
                    role = ChatRole.Assistant,
                    content = message.text,
                    reasoning = message.reasoning,
                    toolCalls = message.toolCalls.map {
                        ToolCallRecord(it.id, it.name, ToolCatalog.redactArguments(it.name, it.arguments))
                    },
                    stats = totalStats,
                )
                if (message.toolCalls.isEmpty()) {
                    emit(ChatEvent.Completed)
                    if (completesOnboarding) finishOnboarding(conversationId)
                    return@flow
                }

                message.toolCalls.forEach { call ->
                    emit(
                        ChatEvent.ToolStarted(
                            label = ToolLabels.of(call.name),
                            id = call.id,
                            tool = call.name,
                            arguments = ToolCatalog.redactArguments(call.name, call.arguments).take(500),
                        ),
                    )
                }
                val results = executeTools(message.toolCalls, conversationId, agentMode)
                for ((call, result) in message.toolCalls.zip(results)) {
                    stepCount++
                    totalStats = totalStats.copy(step = stepCount, durationMs = System.currentTimeMillis() - startedAt)
                    emit(ChatEvent.StatsUpdated(totalStats))
                    val label = ToolLabels.of(call.name)
                    result.workingText?.let { emit(ChatEvent.WorkingTextChanged(it)) }
                    result.todos?.let { emit(ChatEvent.TodoUpdated(it)) }
                    if (mode == ChatMode.Onboarding && result.completesOnboarding) {
                        completesOnboarding = true
                    }
                    val ask = result.ask
                    if (ask != null) {
                        emit(ChatEvent.AskRequested(ask))
                        val reply = awaitReply(ask.id)
                        emit(ChatEvent.AskResolved(ask, reply))
                        chats.append(
                            conversationId = conversationId,
                            role = ChatRole.Tool,
                            content = ask.answerContent(reply),
                            toolCallId = call.id,
                            name = call.name,
                            label = ask.questionLabels(),
                        )
                        continue
                    }
                    val plan = result.plan
                    if (plan != null && agentMode == AgentMode.Plan) {
                        val request = result.approval ?: approvals.create(plan.title, plan.summary, emptyList())
                        chats.setPlan(conversationId, plan, PlanStatus.Pending, request.id)
                        emit(ChatEvent.PlanProposed(plan, request))
                        val approved = awaitDecision(request.id)
                        chats.setPlan(
                            conversationId,
                            plan,
                            if (approved) PlanStatus.Approved else PlanStatus.Rejected,
                            request.id,
                        )
                        emit(ChatEvent.PlanResolved(plan, approved))
                        if (approved) {
                            agentMode = AgentMode.Build
                            chats.setMode(conversationId, AgentMode.Build)
                        }
                        chats.append(
                            conversationId = conversationId,
                            role = ChatRole.Tool,
                            content = if (approved) {
                                "The user approved the plan. Continue in build mode and execute it."
                            } else {
                                "The user wants changes. Stay in plan mode and revise the plan."
                            },
                            toolCallId = call.id,
                            name = call.name,
                            label = plan.title,
                        )
                        emit(ChatEvent.ToolFinished(label, true, plan.title, call.name, call.id))
                        continue
                    }
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
                        emit(ChatEvent.ToolFinished(label, result.ok, result.summary, call.name, call.id, result.content))
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
                            emit(ChatEvent.ToolStarted(step.label, tool = step.tool, arguments = step.arguments.take(500)))
                            val outcome = tools.runStep(step, conversationId, agentMode)
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
                    emit(ChatEvent.ToolFinished(label, approved, if (approved) "Approved" else "Rejected", call.name, call.id))
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

    private suspend fun executeTools(
        calls: List<CompletedToolCall>,
        conversationId: String,
        mode: AgentMode,
    ): List<ToolResult> = coroutineScope {
        val semaphore = Semaphore(TOOL_CONCURRENCY)
        val readCalls = calls.filter { call ->
            ToolCatalog.find(call.name)?.kind == ToolKind.Read
        }
        val deferred = readCalls.associate { call ->
            call.id to async {
                semaphore.withPermit {
                    if (call.name == "spawn_agent") runSubAgent(call) else tools.execute(call, conversationId, mode)
                }
            }
        }
        calls.map { call ->
            if (call.id in deferred) {
                deferred.getValue(call.id).await()
            } else {
                if (call.name == "spawn_agent") runSubAgent(call) else tools.execute(call, conversationId, mode)
            }
        }
    }

    private suspend fun runSubAgent(call: CompletedToolCall): ToolResult {
        val args = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(call.arguments)
                as kotlinx.serialization.json.JsonObject
        }.getOrNull() ?: return ToolResult(false, "Error: invalid spawn_agent arguments", "Invalid arguments")
        val task = (args["task"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
        val tools = (args["tools"] as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            .orEmpty()
        val model = (args["model"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
        val rounds = (args["max_rounds"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() ?: 6
        val result = subAgents.run(
            SubAgentRequest(task = task, tools = tools, model = model, maxRounds = rounds),
        )
        val content = buildString {
            appendLine(result.summary)
            if (result.files.isNotEmpty()) {
                appendLine()
                appendLine("Files:")
                result.files.forEach { appendLine("- $it") }
            }
            if (result.steps.isNotEmpty()) {
                appendLine()
                appendLine("Steps:")
                result.steps.forEach { step ->
                    appendLine("- ${step.label}: ${if (step.ok) "done" else "failed"} (${step.detail})")
                }
            }
            result.error?.let {
                appendLine()
                appendLine("Error: $it")
            }
        }.trim()
        return ToolResult(
            ok = result.error == null,
            content = content,
            summary = if (result.error == null) "Subagent finished" else "Subagent failed: ${result.error}",
        )
    }

    private suspend fun streamRound(
        endpoint: ModelEndpoint,
        protocol: ChatProtocol,
        apiKey: String,
        request: ChatCompletionRequest,
        retry: RetryConfig,
        round: Int,
        step: Int,
        totalStats: MessageStats,
        startedAt: Long,
        emitStats: suspend (MessageStats) -> Unit,
        emitRetry: suspend (Int, Int, String) -> Unit,
        emitReset: suspend () -> Unit,
    ): RoundResult {
        var attempt = 0
        while (true) {
            var latest = totalStats.copy(round = round, step = step)
            val accumulator = StreamAccumulator()
            var visible = false
            var roundStats = MessageStats(
                providerId = totalStats.providerId,
                providerName = totalStats.providerName,
                model = totalStats.model,
                round = round,
                step = step,
            )
            try {
                client.stream(endpoint, protocol, apiKey, request).collect { event ->
                    if (event is ChatStreamEvent.Text && event.delta.isNotBlank()) visible = true
                    accumulator.apply(event)
                    when (event) {
                        is ChatStreamEvent.Text -> Unit
                        is ChatStreamEvent.Reasoning -> Unit
                        is ChatStreamEvent.Usage -> {
                            roundStats = roundStats.copy(
                                inputTokens = event.prompt,
                                outputTokens = event.completion,
                                cachedTokens = event.cached,
                                totalTokens = event.total,
                                durationMs = System.currentTimeMillis() - startedAt,
                            )
                            latest = totalStats.merge(roundStats).copy(
                                round = round,
                                step = step,
                                durationMs = System.currentTimeMillis() - startedAt,
                            )
                            emitStats(latest)
                        }
                        else -> Unit
                    }
                }
                val message = accumulator.result()
                if (message.text.isBlank() && message.toolCalls.isEmpty() && message.reasoning.isBlank()) {
                    throw ModelProtocolException("stream ended before a reply")
                }
                return RoundResult(message, latest)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (visible || attempt >= retry.maxRetries || !isRetryable(e)) throw e
                attempt++
                emitReset()
                emitRetry(attempt, retry.maxRetries, retryReason(e))
                delay(retry.delayFor(attempt, retryAfter(e)))
            }
        }
    }

    private fun isRetryable(e: Throwable): Boolean = RetryPolicy.isRetryable(e)

    private fun retryAfter(e: Throwable): Long? = RetryPolicy.retryAfter(e)

    private fun retryReason(e: Throwable): String = RetryPolicy.reason(e)

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

    private suspend fun awaitReply(askId: String): AskReply {
        val deferred = askReplies.computeIfAbsent(askId) { CompletableDeferred() }
        val reply = withTimeoutOrNull(APPROVAL_TIMEOUT_MS) { deferred.await() }
        askReplies.remove(askId)
        return reply ?: AskReply(id = askId, skipped = true)
    }

    private suspend fun List<ChatMessage>.toDtos(): List<ChatMessageDto> {
        val quotes = chats.messagesByIds(mapNotNull { it.replyToId }.toSet())
        return mapNotNull { message ->
            when (message.role) {
                ChatRole.User -> ChatMessageDto(
                    role = "user",
                    content = message.toUserContent(quotes[message.replyToId]),
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
                ChatRole.System -> ChatMessageDto(
                    role = "user",
                    content = "[app notice] ${message.content}",
                )
            }
        }
    }

    private fun ChatMessage.toUserContent(quoted: ChatMessage?): MessageContent {
        val images = attachments.filter { it.kind == AttachmentKind.Image }.mapNotNull { it.dataUrl }
        val text = buildString {
            if (quoted != null) {
                val author = if (quoted.role == ChatRole.User) "the user" else "you"
                append("Replying to ").append(author).append(": ")
                append(quoted.content.replace(Regex("\\s+"), " ").trim().take(600))
                append("\n\n")
            }
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
        private const val APPROVAL_TIMEOUT_MS = 15 * 60 * 1000L
        private const val TOOL_CONCURRENCY = 4
    }
}

private data class RoundResult(
    val message: CompletedMessage,
    val stats: MessageStats,
)
