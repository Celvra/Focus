package fan.x0.focus.data.assistant

import fan.x0.focus.core.error.AppError
import fan.x0.focus.core.error.AppErrorType
import fan.x0.focus.core.error.NetworkException
import fan.x0.focus.core.error.toAppError
import fan.x0.focus.core.network.ProviderRequestConfig
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
    data class ReasoningTimed(val durationMs: Long) : ChatEvent
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
        val durationMs: Long = 0,
        val diffs: List<FileDiff> = emptyList(),
        val media: List<ChatAttachment> = emptyList(),
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
    data class CompactionStarted(val automatic: Boolean) : ChatEvent
    data class CompactionDelta(val text: String) : ChatEvent
    data class CompactionFinished(
        val automatic: Boolean,
        val ok: Boolean,
        val tokensBefore: Int = 0,
        val tokensAfter: Int = 0,
    ) : ChatEvent
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
    private val compactor: ChatCompactor,
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
        val requestConfig = provider?.requestConfig(apiKey) ?: ProviderRequestConfig(apiKey = apiKey)

        val startedAt = System.currentTimeMillis()
        var agentMode = options.agentMode
        var totalStats = MessageStats(
            providerId = providerId,
            providerName = provider?.name ?: providerId,
            model = model,
        )
        var stepCount = 0
        var completesOnboarding = false
        var pendingMedia: List<ChatAttachment> = emptyList()
        var compactionTried = false
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
                if (!compactionTried && mode == ChatMode.Chat &&
                    compactor.shouldCompact(conversationId, providerId, model)
                ) {
                    compactionTried = true
                    emit(ChatEvent.CompactionStarted(automatic = true))
                    val compacted = compactor.compact(conversationId, providerId, model) { delta ->
                        emit(ChatEvent.CompactionDelta(delta))
                    }
                    emit(
                        ChatEvent.CompactionFinished(
                            automatic = true,
                            ok = compacted != null,
                            tokensBefore = compacted?.tokensBefore ?: 0,
                            tokensAfter = compacted?.tokensAfter ?: 0,
                        ),
                    )
                }
                val specs = when (mode) {
                    ChatMode.Chat -> tools.specsFor(agentMode)
                    ChatMode.Onboarding -> ToolCatalog.onboarding()
                }.let { list ->
                    if (completesOnboarding) list.filterNot { it.name == "complete_onboarding" } else list
                }
                val context = chats.context(conversationId)
                val system = when (mode) {
                    ChatMode.Chat -> prompt.chatPrompt(
                        title = chats.conversation(conversationId)?.title.orEmpty(),
                        mode = agentMode,
                    )
                    ChatMode.Onboarding -> prompt.onboardingPrompt(
                        turn = context.messages.count { it.role == ChatRole.User },
                    )
                }.withSummary(context.summary)
                val messages = buildList {
                    add(ChatMessageDto(role = "system", content = system))
                    addAll(context.messages.toDtos())
                    val media = pendingMedia.mapNotNull { it.dataUrl }
                    pendingMedia = emptyList()
                    if (media.isNotEmpty()) {
                        add(
                            ChatMessageDto(
                                role = "user",
                                content = MessageContent.of("Images returned by the tool calls above.", media),
                            ),
                        )
                    }
                    if (context.messages.isEmpty() && context.summary.isBlank()) {
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
                    config = requestConfig,
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
                if (roundResult.reasoningMs > 0) {
                    emit(ChatEvent.ReasoningTimed(roundResult.reasoningMs))
                }
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
                    elapsedMs = roundResult.reasoningMs,
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
                val results = executeTools(message.toolCalls, conversationId, agentMode, providerId, model)
                pendingMedia = results.flatMap { it.media }
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
                            elapsedMs = result.durationMs,
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
                            elapsedMs = result.durationMs,
                        )
                        emit(
                            ChatEvent.ToolFinished(
                                label = label,
                                ok = true,
                                summary = plan.title,
                                tool = call.name,
                                id = call.id,
                                durationMs = result.durationMs,
                            ),
                        )
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
                                elapsedMs = result.durationMs,
                            )
                            continue
                        }
                        emit(
                            ChatEvent.ToolFinished(
                                label = label,
                                ok = result.ok,
                                summary = result.summary,
                                tool = call.name,
                                id = call.id,
                                output = result.content,
                                durationMs = result.durationMs,
                                diffs = result.diffs,
                                media = result.previews,
                            ),
                        )
                        chats.append(
                            conversationId = conversationId,
                            role = ChatRole.Tool,
                            content = result.content,
                            toolCallId = call.id,
                            name = call.name,
                            attachments = result.previews,
                            label = label,
                            elapsedMs = result.durationMs,
                            diffs = result.diffs.takeIf { it.isNotEmpty() }?.let { listOf(it) }.orEmpty(),
                        )
                        continue
                    }

                    emit(ChatEvent.ApprovalRequested(approval))
                    val approved = awaitDecision(approval.id)
                    emit(ChatEvent.ApprovalResolved(approval, approved))
                    val lines = mutableListOf<String>()
                    val stepDiffs = mutableListOf<List<FileDiff>>()
                    if (approved) {
                        approval.steps.forEach { step ->
                            emit(ChatEvent.ToolStarted(step.label, tool = step.tool, arguments = step.arguments.take(500)))
                            val stepStartedAt = System.currentTimeMillis()
                            val outcome = tools.runStep(step, conversationId, agentMode)
                            stepDiffs += outcome.diffs
                            emit(
                                ChatEvent.ToolFinished(
                                    label = step.label,
                                    ok = outcome.ok,
                                    summary = outcome.summary,
                                    tool = step.tool,
                                    durationMs = System.currentTimeMillis() - stepStartedAt,
                                    diffs = outcome.diffs,
                                ),
                            )
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
                        elapsedMs = result.durationMs,
                        diffs = stepDiffs.takeIf { list -> list.any { it.isNotEmpty() } }.orEmpty(),
                    )
                    emit(
                        ChatEvent.ToolFinished(
                            label = label,
                            ok = approved,
                            summary = if (approved) "Approved" else "Rejected",
                            tool = call.name,
                            id = call.id,
                            durationMs = result.durationMs,
                        ),
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

    fun compact(
        conversationId: String,
        options: TurnOptions = TurnOptions(),
    ): Flow<ChatEvent> = flow {
        emit(ChatEvent.CompactionStarted(automatic = false))
        val result = try {
            compactor.compact(conversationId, options.providerId, options.model) { delta ->
                emit(ChatEvent.CompactionDelta(delta))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        }
        emit(
            ChatEvent.CompactionFinished(
                automatic = false,
                ok = result != null,
                tokensBefore = result?.tokensBefore ?: 0,
                tokensAfter = result?.tokensAfter ?: 0,
            ),
        )
        emit(ChatEvent.Completed)
    }

    private suspend fun executeTools(
        calls: List<CompletedToolCall>,
        conversationId: String,
        mode: AgentMode,
        providerId: String,
        model: String,
    ): List<ToolResult> = coroutineScope {
        val semaphore = Semaphore(TOOL_CONCURRENCY)
        val readCalls = calls.filter { call ->
            ToolCatalog.find(call.name)?.kind == ToolKind.Read
        }
        val deferred = readCalls.associate { call ->
            call.id to async {
                semaphore.withPermit {
                    executeTool(call, conversationId, mode, providerId, model)
                }
            }
        }
        calls.map { call ->
            if (call.id in deferred) {
                deferred.getValue(call.id).await()
            } else {
                executeTool(call, conversationId, mode, providerId, model)
            }
        }
    }

    private suspend fun executeTool(
        call: CompletedToolCall,
        conversationId: String,
        mode: AgentMode,
        providerId: String,
        model: String,
    ): ToolResult {
        val startedAt = System.currentTimeMillis()
        val result = if (call.name == "spawn_agent") {
            runSubAgent(call, providerId, model)
        } else {
            tools.execute(call, conversationId, mode)
        }
        return result.copy(durationMs = System.currentTimeMillis() - startedAt)
    }

    private suspend fun runSubAgent(
        call: CompletedToolCall,
        providerId: String,
        model: String,
    ): ToolResult {
        val args = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(call.arguments)
                as kotlinx.serialization.json.JsonObject
        }.getOrNull() ?: return ToolResult(false, "Error: invalid spawn_agent arguments", "Invalid arguments")
        val task = (args["task"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
        val tools = (args["tools"] as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            .orEmpty()
        val overrideModel = (args["model"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
        val rounds = (args["max_rounds"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() ?: 6
        val result = subAgents.run(
            SubAgentRequest(
                task = task,
                tools = tools,
                model = overrideModel,
                parentProviderId = providerId,
                parentModel = model,
                maxRounds = rounds,
                runId = call.id,
            ),
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
        config: ProviderRequestConfig,
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
            var reasoningStartedAt = 0L
            var reasoningEndedAt = 0L
            var roundStats = MessageStats(
                providerId = totalStats.providerId,
                providerName = totalStats.providerName,
                model = totalStats.model,
                round = round,
                step = step,
            )
            try {
                client.stream(endpoint, protocol, config, request).collect { event ->
                    if (event is ChatStreamEvent.Text && event.delta.isNotBlank()) visible = true
                    accumulator.apply(event)
                    when (event) {
                        is ChatStreamEvent.Text -> {
                            if (reasoningStartedAt > 0 && reasoningEndedAt == 0L) {
                                reasoningEndedAt = System.currentTimeMillis()
                            }
                        }
                        is ChatStreamEvent.Reasoning -> {
                            if (reasoningStartedAt == 0L) {
                                reasoningStartedAt = System.currentTimeMillis()
                            }
                        }
                        is ChatStreamEvent.Usage -> {
                            roundStats = roundStats.copy(
                                inputTokens = event.prompt,
                                outputTokens = event.completion,
                                cachedTokens = event.cached,
                                totalTokens = event.total,
                                durationMs = System.currentTimeMillis() - startedAt,
                                lastContextTokens = event.prompt,
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
                val reasoningMs = when {
                    reasoningStartedAt == 0L -> 0L
                    reasoningEndedAt > 0 -> reasoningEndedAt - reasoningStartedAt
                    else -> System.currentTimeMillis() - reasoningStartedAt
                }
                return RoundResult(message, latest, reasoningMs)
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

    private fun String.withSummary(summary: String): String {
        if (summary.isBlank()) return this
        return buildString {
            append(this@withSummary)
            appendLine()
            appendLine("Summary of the earlier conversation:")
            append(summary)
        }
    }

    private suspend fun List<ChatMessage>.toDtos(): List<ChatMessageDto> {
        val quotes = chats.messagesByIds(mapNotNull { it.replyToId }.toSet())
        return withoutDanglingToolCalls().mapNotNull { message ->
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

    private fun List<ChatMessage>.withoutDanglingToolCalls(): List<ChatMessage> {
        val consumed = BooleanArray(size)
        val kept = mutableListOf<ChatMessage>()
        for (index in indices) {
            if (consumed[index]) continue
            val message = this[index]
            if (message.role == ChatRole.Tool) continue
            if (message.role != ChatRole.Assistant || message.toolCalls.isEmpty()) {
                kept += message
                continue
            }
            val ids = message.toolCalls.mapTo(mutableSetOf()) { it.id }
            val answered = mutableSetOf<String>()
            val results = mutableListOf<ChatMessage>()
            for (next in index + 1 until size) {
                val candidate = this[next]
                if (candidate.role == ChatRole.Assistant) break
                val callId = candidate.toolCallId
                if (candidate.role == ChatRole.Tool && callId != null && callId in ids && answered.add(callId)) {
                    consumed[next] = true
                    results += candidate
                }
            }
            if (answered.containsAll(ids)) {
                kept += message
                kept += results
            }
        }
        return kept
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
    val reasoningMs: Long = 0,
)
