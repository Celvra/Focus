package fan.x0.focus.data.assistant

import fan.x0.focus.core.network.ProviderRequestConfig
import fan.x0.focus.core.network.openai.ChatCompletionRequest
import fan.x0.focus.core.network.openai.ChatMessageDto
import fan.x0.focus.core.network.openai.ChatProtocol
import fan.x0.focus.core.network.openai.ChatStreamEvent
import fan.x0.focus.core.network.openai.ChatWireClient
import fan.x0.focus.core.network.openai.CompletedMessage
import fan.x0.focus.core.network.openai.FunctionCallDto
import fan.x0.focus.core.network.openai.MessageContent
import fan.x0.focus.core.network.openai.ModelEndpoint
import fan.x0.focus.core.network.openai.ModelProtocolException
import fan.x0.focus.core.network.openai.StreamAccumulator
import fan.x0.focus.core.network.openai.ToolCallDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap

sealed interface CrewSpeechEvent {
    data class Delta(val text: String) : CrewSpeechEvent
    data class TextReset(val text: String) : CrewSpeechEvent
    data class Activity(val text: String) : CrewSpeechEvent
    data class ReasoningDelta(val text: String) : CrewSpeechEvent
    data class ReasoningTimed(val durationMs: Long) : CrewSpeechEvent
    data class StatsUpdated(val stats: MessageStats) : CrewSpeechEvent
    data class Retry(val attempt: Int, val max: Int, val reason: String) : CrewSpeechEvent
    data class ToolStarted(
        val id: String,
        val label: String,
        val tool: String = "",
        val arguments: String = "",
    ) : CrewSpeechEvent
    data class ToolFinished(
        val id: String,
        val label: String,
        val ok: Boolean,
        val summary: String,
        val tool: String = "",
        val arguments: String = "",
        val detail: String = "",
    ) : CrewSpeechEvent
    data class ApprovalRequested(val request: ApprovalRequest) : CrewSpeechEvent
    data class ApprovalResolved(val request: ApprovalRequest, val approved: Boolean) : CrewSpeechEvent
    data class ApprovalFinished(val id: String) : CrewSpeechEvent
    data class AskRequested(val request: AskRequest) : CrewSpeechEvent
    data class AskResolved(val request: AskRequest, val reply: AskReply) : CrewSpeechEvent
    data class TrustRequested(val request: WorkspaceTrustRequest) : CrewSpeechEvent
    data class TrustResolved(val request: WorkspaceTrustRequest, val trusted: Boolean) : CrewSpeechEvent
    data class TodoUpdated(val todos: List<ConversationTodo>) : CrewSpeechEvent
    data class ReactionAdded(val messageId: Long, val emoji: String) : CrewSpeechEvent
    data class Handoff(val memberId: String) : CrewSpeechEvent
    data class Completed(val speech: CrewSpeech) : CrewSpeechEvent
}

data class CrewSpeech(
    val text: String = "",
    val error: String? = null,
    val handoffMemberId: String? = null,
    val finish: Boolean = false,
)

@Single
class CrewEngine(
    private val client: ChatWireClient,
    private val settings: AssistantSettings,
    private val secrets: SecretStore,
    private val store: CrewStore,
    private val prompt: CrewPrompt,
    private val tools: ToolExecutor,
    private val approvals: ApprovalStore,
    private val agent: AgentSettings,
    private val workspaceTrust: WorkspaceTrustService,
    private val skills: SkillRepository,
) {
    private val waivers = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val askReplies = ConcurrentHashMap<String, CompletableDeferred<AskReply>>()
    private val trustDecisions = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    private val specs: List<ToolSpec>
        get() = tools.allSpecs()
            .filterNot { it.name in EXCLUDED }
            .filterNot { it.name in CrewToolCatalog.assistantNames }
            .filterNot { it.name in CrewToolCatalog.agentNames }
            .plus(CrewToolCatalog.agentSpecs)

    private val specNames: Set<String> get() = specs.mapTo(mutableSetOf()) { it.name }

    fun decide(approvalId: String, approved: Boolean) {
        waivers.computeIfAbsent(approvalId) { CompletableDeferred() }.complete(approved)
    }

    fun decideTrust(id: String, trusted: Boolean) {
        trustDecisions.computeIfAbsent(id) { CompletableDeferred() }.complete(trusted)
    }

    fun answerAsk(askId: String, answers: List<AskAnswer>, skipped: Boolean = false) {
        askReplies.computeIfAbsent(askId) { CompletableDeferred() }
            .complete(AskReply(id = askId, answers = answers, skipped = skipped))
    }

    fun speak(
        group: AgentGroup,
        member: CrewMember,
        members: List<CrewMember>,
    ): Flow<CrewSpeechEvent> = flow {
        val config = settings.configOnce()
        val provider = config.providers.firstOrNull { it.id == member.providerId } ?: config.activeProvider
        if (provider == null) {
            emit(CrewSpeechEvent.Completed(CrewSpeech(error = "No model provider is configured.")))
            return@flow
        }
        val apiKey = secrets.apiKey(provider.id).orEmpty()
        if (provider.requiresKey && apiKey.isBlank()) {
            emit(CrewSpeechEvent.Completed(CrewSpeech(error = "No API key for ${provider.name}.")))
            return@flow
        }
        val model = member.model.trim().ifBlank { provider.model }
        val endpoint = ModelEndpoint(provider.baseUrl, model)
        if (!endpoint.isUsable) {
            emit(CrewSpeechEvent.Completed(CrewSpeech(error = "The model connection is not usable.")))
            return@flow
        }
        val requestConfig = provider.requestConfig(apiKey)
        val reasoning = ReasoningLevel.fromName(member.reasoning)
        val retry = agent.configOnce().retry
        var totalStats = MessageStats(
            providerId = provider.id,
            providerName = provider.name,
            model = model,
        )
        val history = store.messages(group.id, CONTEXT_MESSAGES)
        val quotes = mutableMapOf<Long, CrewMessage>()
        history.mapNotNullTo(mutableSetOf()) { it.replyToId }.forEach { id ->
            store.message(id)?.let { quotes[id] = it }
        }
        val conversation = mutableListOf<ChatMessageDto>()
        var workspaceInstructions = workspaceTrust.promptSection()
        val lastUserText = history.asReversed()
            .firstOrNull { it.authorType == CrewAuthorType.User }
            ?.content
            .orEmpty()
        val skillNames = MentionText.mentionedNames(
            lastUserText,
            skills.entries().filter { it.enabled }.map { it.name },
        )
        val referencedSkills = skills.mentionSection(skillNames)
        var skillInstructions = combineSkills(skills.promptSection(), referencedSkills)
        conversation += ChatMessageDto(
            role = "system",
            content = prompt.agentPrompt(group, member, members, workspaceInstructions, skillInstructions),
        )
        conversation += buildTranscript(history, quotes, member.id)
        conversation += ChatMessageDto(role = "user", content = prompt.turnPrompt(member))
        var lastSeenId = history.lastOrNull()?.id ?: 0
        val answer = StringBuilder()
        var activeRoundText = ""
        var roundAppended = true
        var handoff: String? = null
        var finish = false
        var hasCrewAction = false
        var error: String? = null
        val pendingMedia = mutableListOf<ChatAttachment>()
        val emitEvent: suspend (CrewSpeechEvent) -> Unit = { emit(it) }

        try {
            for (round in 0 until MAX_ROUNDS) {
                workspaceInstructions = workspaceTrust.promptSection()
                skillInstructions = combineSkills(skills.promptSection(), referencedSkills)
                conversation[0] = ChatMessageDto(
                    role = "system",
                    content = prompt.agentPrompt(group, member, members, workspaceInstructions, skillInstructions),
                )
                if (round > 0) {
                    val fresh = store.messagesAfter(group.id, lastSeenId)
                    if (fresh.isNotEmpty()) {
                        lastSeenId = fresh.last().id
                        fresh.forEach { message ->
                            val text = prompt.transcriptMessage(message, member.id, quotes[message.replyToId])
                            if (hasContent(message, text)) {
                                conversation += ChatMessageDto(
                                    role = "user",
                                    content = messageContent(message, text.take(MESSAGE_LIMIT)),
                                )
                            }
                        }
                    }
                    if (pendingMedia.isNotEmpty()) {
                        val images = pendingMedia.mapNotNull { it.dataUrl }
                        pendingMedia.clear()
                        if (images.isNotEmpty()) {
                            conversation += ChatMessageDto(
                                role = "user",
                                content = MessageContent.of("Images returned by the tool calls above.", images),
                            )
                        }
                    }
                }
                val roundText = StringBuilder()
                roundAppended = false
                activeRoundText = ""
                val roundBase = totalStats
                var roundLatest = roundBase
                val result = streamRound(
                    endpoint = endpoint,
                    protocol = provider.protocol,
                    config = requestConfig,
                    messages = conversation,
                    retry = retry,
                    reasoningEffort = reasoning.wire,
                    stats = roundBase,
                    round = round,
                    onStats = { roundStats ->
                        roundLatest = roundBase.merge(roundStats)
                        emit(
                            CrewSpeechEvent.StatsUpdated(
                                roundLatest.copy(round = round, step = round),
                            ),
                        )
                    },
                    onDelta = { delta ->
                        roundText.append(delta)
                        activeRoundText = roundText.toString()
                        emit(CrewSpeechEvent.Delta(delta))
                    },
                    onReasoning = { delta -> emit(CrewSpeechEvent.ReasoningDelta(delta)) },
                    onReasoningTimed = { durationMs ->
                        emit(CrewSpeechEvent.ReasoningTimed(durationMs))
                    },
                    onRetry = { attempt, max, reason ->
                        emit(CrewSpeechEvent.Retry(attempt, max, reason))
                    },
                )
                totalStats = roundLatest
                val finalText = result.text
                val streamed = roundText.toString()
                val separator = if (answer.isNotEmpty() && finalText.isNotBlank()) "\n\n" else ""
                if (finalText != streamed) {
                    if (finalText.startsWith(streamed)) {
                        val suffix = finalText.substring(streamed.length)
                        if (suffix.isNotEmpty()) emit(CrewSpeechEvent.Delta(suffix))
                        activeRoundText = finalText
                    } else {
                        val full = answer.toString() + separator + finalText
                        emit(CrewSpeechEvent.TextReset(full))
                        activeRoundText = finalText
                    }
                }
                if (finalText.isNotBlank()) {
                    answer.append(separator).append(finalText)
                    activeRoundText = finalText
                    roundAppended = true
                }
                if (result.toolCalls.isEmpty()) break
                conversation += ChatMessageDto(
                    role = "assistant",
                    content = finalText.ifBlank { null }?.let(MessageContent::Text),
                    toolCalls = result.toolCalls.map { call ->
                        ToolCallDto(
                            id = call.id,
                            function = FunctionCallDto(name = call.name, arguments = call.arguments),
                        )
                    },
                )
                val results = result.toolCalls.map { call ->
                    if (call.name in CrewToolCatalog.agentNames) hasCrewAction = true
                    emit(
                        CrewSpeechEvent.ToolStarted(
                            id = call.id,
                            label = tools.label(call.name),
                            tool = call.name,
                            arguments = call.arguments.take(ARGUMENT_LIMIT),
                        ),
                    )
                    val outcome = executeTool(call, group, member, members, emitEvent)
                    if (outcome.handoff != null) handoff = outcome.handoff
                    if (outcome.finish) finish = true
                    if (outcome.result.workingText != null) {
                        emit(CrewSpeechEvent.Activity(outcome.result.workingText))
                    }
                    pendingMedia += outcome.result.media
                    emit(
                        CrewSpeechEvent.ToolFinished(
                            id = call.id,
                            label = tools.label(call.name),
                            ok = outcome.result.ok,
                            summary = outcome.result.summary,
                            tool = call.name,
                            arguments = call.arguments.take(ARGUMENT_LIMIT),
                            detail = outcome.result.content.take(DETAIL_LIMIT),
                        ),
                    )
                    call to outcome
                }
                results.forEach { (call, outcome) ->
                    conversation += ChatMessageDto(
                        role = "tool",
                        content = outcome.result.content.take(RESULT_LIMIT),
                        toolCallId = call.id,
                        name = call.name,
                    )
                }
                if (finish) break
            }
            val mention = handoff?.let { id -> members.firstOrNull { it.id == id } }
            val initialText = answer.toString().trim()
            if (mention != null && !initialText.contains("@${mention.name}", ignoreCase = true)) {
                val suffix = "\n\n@${mention.name}"
                emit(CrewSpeechEvent.Delta(suffix))
                answer.append(suffix)
            }
            val finalText = answer.toString().trim()
            if (finalText.isBlank() && !finish && !hasCrewAction) {
                error = "The member returned an empty reply."
            }
            emit(
                CrewSpeechEvent.Completed(
                    CrewSpeech(
                        text = finalText,
                        error = error,
                        handoffMemberId = handoff,
                        finish = finish,
                    ),
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (!roundAppended && activeRoundText.isNotBlank()) {
                answer.append(activeRoundText)
            }
            emit(
                CrewSpeechEvent.Completed(
                    CrewSpeech(
                        text = answer.toString().trim(),
                        error = e.message ?: e.javaClass.simpleName,
                        handoffMemberId = handoff,
                        finish = finish,
                    ),
                ),
            )
        }
    }

    private suspend fun executeTool(
        call: fan.x0.focus.core.network.openai.CompletedToolCall,
        group: AgentGroup,
        member: CrewMember,
        members: List<CrewMember>,
        emitEvent: suspend (CrewSpeechEvent) -> Unit,
    ): ToolOutcome {
        return when (call.name) {
            CrewToolCatalog.REACT -> react(call, group, emitEvent)
            CrewToolCatalog.MENTION -> mention(call, member, members, emitEvent)
            CrewToolCatalog.FINISH -> ToolOutcome(
                result = ToolResult(true, "The discussion will end after this reply.", "Discussion finished"),
                finish = true,
            )
            else -> {
                val spec = tools.find(call.name)
                if (spec == null || call.name !in specNames) {
                    return ToolOutcome(
                        result = ToolResult(false, "Error: tool ${call.name} is not allowed", "Tool not allowed"),
                    )
                }
                var result = tools.execute(call, conversationId = group.id, mode = AgentMode.Build)
                val trust = result.trust
                if (trust != null) {
                    if (workspaceTrust.state(trust.target) == WorkspaceTrustState.Unknown) {
                        emitEvent(CrewSpeechEvent.TrustRequested(trust))
                        val trusted = awaitTrust(trust.id)
                        emitEvent(CrewSpeechEvent.TrustResolved(trust, trusted))
                        workspaceTrust.decide(trust, trusted)
                    }
                    result = tools.execute(call, conversationId = group.id, mode = AgentMode.Build)
                }
                if (result.todos != null) {
                    emitEvent(CrewSpeechEvent.TodoUpdated(result.todos))
                }
                val request = result.ask
                if (request != null) {
                    emitEvent(CrewSpeechEvent.AskRequested(request))
                    val reply = awaitReply(request.id)
                    emitEvent(CrewSpeechEvent.AskResolved(request, reply))
                    return ToolOutcome(
                        result = result.copy(
                            content = request.answerContent(reply),
                            summary = request.questionLabels(),
                        ),
                    )
                }
                handleApproval(group, result, emitEvent)
            }
        }
    }

    private suspend fun handleApproval(
        group: AgentGroup,
        initial: ToolResult,
        emitEvent: suspend (CrewSpeechEvent) -> Unit,
    ): ToolOutcome {
        val approval = initial.approval ?: return ToolOutcome(result = initial)
        emitEvent(CrewSpeechEvent.ApprovalRequested(approval))
        val approved = awaitDecision(approval.id)
        emitEvent(CrewSpeechEvent.ApprovalResolved(approval, approved))
        val lines = mutableListOf<String>()
        var ok = approved
        if (approved) {
            approval.steps.forEach { step ->
                var outcome = tools.runStep(step, conversationId = group.id, mode = AgentMode.Build)
                outcome.trust?.let { trust ->
                    if (workspaceTrust.state(trust.target) == WorkspaceTrustState.Unknown) {
                        emitEvent(CrewSpeechEvent.TrustRequested(trust))
                        val trusted = awaitTrust(trust.id)
                        emitEvent(CrewSpeechEvent.TrustResolved(trust, trusted))
                        workspaceTrust.decide(trust, trusted)
                    }
                    outcome = tools.runStep(step, conversationId = group.id, mode = AgentMode.Build)
                }
                if (!outcome.ok) ok = false
                lines += outcome.describe()
            }
            approvals.decide(approval.id, ApprovalStatus.Approved, lines.joinToString("\n").take(500))
        } else {
            lines += "The user rejected this action."
            approvals.decide(approval.id, ApprovalStatus.Rejected, null)
        }
        if (approved) emitEvent(CrewSpeechEvent.ApprovalFinished(approval.id))
        return ToolOutcome(
            result = ToolResult(
                ok = ok,
                content = lines.joinToString("\n"),
                summary = if (approved) "Approved" else "Rejected",
            ),
        )
    }

    private suspend fun react(
        call: fan.x0.focus.core.network.openai.CompletedToolCall,
        group: AgentGroup,
        emitEvent: suspend (CrewSpeechEvent) -> Unit,
    ): ToolOutcome {
        val args = parseArguments(call.arguments)
            ?: return ToolOutcome(result = ToolResult(false, "Error: invalid reaction arguments", "Invalid arguments"))
        val reaction = args.text("reaction")?.trim().orEmpty()
        if (reaction.isBlank() || reaction.length > 16) {
            return ToolOutcome(result = ToolResult(false, "Error: reaction must be one emoji", "Invalid reaction"))
        }
        val id = args.number("message_id")
        val match = args.text("match")
        val target = when {
            id != null -> store.message(id)?.takeIf { it.groupId == group.id }
            match != null -> store.messages(group.id, 200)
                .lastOrNull {
                    it.authorType == CrewAuthorType.User && it.content.contains(match, ignoreCase = true)
                }
            else -> store.messages(group.id, 200).lastOrNull { it.authorType == CrewAuthorType.User }
        } ?: return ToolOutcome(result = ToolResult(false, "Error: no message to react to", "No target"))
        store.addReaction(target.id, reaction)
        emitEvent(CrewSpeechEvent.ReactionAdded(target.id, reaction))
        return ToolOutcome(
            result = ToolResult(
                ok = true,
                content = "Added $reaction to ${target.authorName}'s message.",
                summary = "Reacted $reaction",
            ),
        )
    }

    private suspend fun mention(
        call: fan.x0.focus.core.network.openai.CompletedToolCall,
        member: CrewMember,
        members: List<CrewMember>,
        emitEvent: suspend (CrewSpeechEvent) -> Unit,
    ): ToolOutcome {
        val args = parseArguments(call.arguments)
            ?: return ToolOutcome(result = ToolResult(false, "Error: invalid mention arguments", "Invalid arguments"))
        val reference = args.text("agent")?.removePrefix("@")?.trim().orEmpty()
        val target = members.firstOrNull {
            it.id != member.id && (it.id == reference || it.name.equals(reference, ignoreCase = true))
        } ?: return ToolOutcome(result = ToolResult(false, "Error: member not found", "Member not found"))
        emitEvent(CrewSpeechEvent.Handoff(target.id))
        return ToolOutcome(
            result = ToolResult(
                ok = true,
                content = "Asked ${target.name} to speak next.",
                summary = "Called ${target.name}",
            ),
            handoff = target.id,
        )
    }

    private suspend fun buildTranscript(
        messages: List<CrewMessage>,
        quotes: Map<Long, CrewMessage>,
        selfId: String,
    ): List<ChatMessageDto> {
        val result = mutableListOf<ChatMessageDto>()
        messages.forEach { message ->
            val text = prompt.transcriptMessage(message, selfId, quotes[message.replyToId])
            if (!hasContent(message, text)) return@forEach
            val role = if (message.authorType == CrewAuthorType.Agent && message.authorId == selfId) {
                "assistant"
            } else {
                "user"
            }
            val content: MessageContent = if (role == "assistant") {
                MessageContent.Text(text.take(MESSAGE_LIMIT))
            } else {
                messageContent(message, text.take(MESSAGE_LIMIT))
            }
            result += ChatMessageDto(role = role, content = content)
        }
        if (result.firstOrNull()?.role == "assistant") {
            result.add(0, ChatMessageDto(role = "user", content = "The group transcript starts here."))
        }
        return result
    }

    private fun combineSkills(base: String, referenced: String): String =
        listOf(base, referenced).filter { it.isNotBlank() }.joinToString("\n\n")

    private fun hasContent(message: CrewMessage, text: String): Boolean =
        text.isNotBlank() || message.attachments.any { it.kind == AttachmentKind.Image && it.dataUrl != null }

    private fun messageContent(message: CrewMessage, text: String): MessageContent {
        val images = message.attachments
            .filter { it.kind == AttachmentKind.Image }
            .mapNotNull { it.dataUrl }
        return if (images.isEmpty()) MessageContent.Text(text) else MessageContent.of(text, images)
    }

    private suspend fun streamRound(
        endpoint: ModelEndpoint,
        protocol: ChatProtocol,
        config: ProviderRequestConfig,
        messages: List<ChatMessageDto>,
        retry: RetryConfig,
        reasoningEffort: String?,
        stats: MessageStats,
        round: Int,
        onStats: suspend (MessageStats) -> Unit,
        onDelta: suspend (String) -> Unit,
        onReasoning: suspend (String) -> Unit,
        onReasoningTimed: suspend (Long) -> Unit,
        onRetry: suspend (Int, Int, String) -> Unit,
    ): CompletedMessage {
        var attempt = 0
        while (true) {
            var visible = false
            var reasoningStartedAt = 0L
            var reasoningEndedAt = 0L
            val startedAt = System.currentTimeMillis()
            var roundStats = MessageStats(
                providerId = stats.providerId,
                providerName = stats.providerName,
                model = stats.model,
                round = round,
                step = round,
            )
            val accumulator = StreamAccumulator()
            try {
                client.stream(
                    endpoint,
                    protocol,
                    config,
                    ChatCompletionRequest(
                        model = endpoint.model,
                        messages = messages,
                        stream = true,
                        tools = specs.map { it.toDto() },
                        toolChoice = "auto",
                        temperature = 0.5,
                        reasoningEffort = reasoningEffort,
                    ),
                ).collect { event ->
                    if (event is ChatStreamEvent.Text && event.delta.isNotBlank()) visible = true
                    accumulator.apply(event)
                    when (event) {
                        is ChatStreamEvent.Text -> {
                            if (event.delta.isEmpty()) return@collect
                            if (reasoningStartedAt > 0 && reasoningEndedAt == 0L) {
                                reasoningEndedAt = System.currentTimeMillis()
                            }
                            onDelta(event.delta)
                        }
                        is ChatStreamEvent.Reasoning -> {
                            if (event.delta.isEmpty()) return@collect
                            if (reasoningStartedAt == 0L) reasoningStartedAt = System.currentTimeMillis()
                            onReasoning(event.delta)
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
                            onStats(roundStats)
                        }
                        else -> Unit
                    }
                }
                val result = accumulator.result()
                if (result.text.isBlank() && result.toolCalls.isEmpty() && result.reasoning.isBlank()) {
                    throw ModelProtocolException("the model returned an empty reply")
                }
                val reasoningMs = when {
                    reasoningStartedAt == 0L -> 0L
                    reasoningEndedAt > 0 -> reasoningEndedAt - reasoningStartedAt
                    else -> System.currentTimeMillis() - reasoningStartedAt
                }
                if (reasoningMs > 0) onReasoningTimed(reasoningMs)
                return result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (visible || attempt >= retry.maxRetries || !RetryPolicy.isRetryable(e)) throw e
                attempt++
                onRetry(attempt, retry.maxRetries, RetryPolicy.reason(e))
                delay(retry.delayFor(attempt, RetryPolicy.retryAfter(e)))
            }
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

    private suspend fun awaitTrust(id: String): Boolean {
        val deferred = trustDecisions.computeIfAbsent(id) { CompletableDeferred() }
        val decision = withTimeoutOrNull(APPROVAL_TIMEOUT_MS) { deferred.await() }
        trustDecisions.remove(id)
        return decision ?: false
    }

    private fun parseArguments(raw: String): JsonObject? =
        runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull()

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

    private fun JsonObject.number(key: String): Long? =
        (this[key] as? JsonPrimitive)?.content?.toLongOrNull()

    private data class ToolOutcome(
        val result: ToolResult,
        val handoff: String? = null,
        val finish: Boolean = false,
    )

    companion object {
        private const val MAX_ROUNDS = 6
        private const val CONTEXT_MESSAGES = 60
        private const val MESSAGE_LIMIT = 4_000
        private const val RESULT_LIMIT = 12_000
        private const val ARGUMENT_LIMIT = 2_000
        private const val DETAIL_LIMIT = 6_000
        private const val APPROVAL_TIMEOUT_MS = 15 * 60 * 1000L
        private val EXCLUDED = setOf(
            "create_plan",
            "propose_plan",
            "react_to_message",
            "rename_conversation",
            "spawn_agent",
            "switch_model",
            "save_provider",
            "test_provider",
            "list_models",
            "list_providers",
            "set_assistant_name",
            "complete_onboarding",
        )
    }
}
