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
    data class Retry(val attempt: Int, val max: Int, val reason: String) : CrewSpeechEvent
    data class ToolStarted(val id: String, val label: String, val arguments: String = "") : CrewSpeechEvent
    data class ToolFinished(
        val id: String,
        val label: String,
        val ok: Boolean,
        val summary: String,
        val arguments: String = "",
        val detail: String = "",
    ) : CrewSpeechEvent
    data class ApprovalRequested(val request: ApprovalRequest) : CrewSpeechEvent
    data class ApprovalResolved(val request: ApprovalRequest, val approved: Boolean) : CrewSpeechEvent
    data class ApprovalFinished(val id: String) : CrewSpeechEvent
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
) {
    private val waivers = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    private val specs: List<ToolSpec> = ToolCatalog.chat()
        .filterNot { it.name in EXCLUDED }
        .filterNot { it.name in CrewToolCatalog.assistantNames }
        .filterNot { it.name in CrewToolCatalog.agentNames }
        .plus(CrewToolCatalog.agentSpecs)

    private val specNames: Set<String> = specs.mapTo(mutableSetOf()) { it.name }

    fun decide(approvalId: String, approved: Boolean) {
        waivers.computeIfAbsent(approvalId) { CompletableDeferred() }.complete(approved)
    }

    fun speak(
        group: AgentGroup,
        member: CrewMember,
        members: List<CrewMember>,
    ): Flow<CrewSpeechEvent> = flow {
        val config = settings.configOnce()
        val provider = config.activeProvider
        if (provider == null) {
            emit(CrewSpeechEvent.Completed(CrewSpeech(error = "No model provider is configured.")))
            return@flow
        }
        val apiKey = secrets.apiKey(provider.id).orEmpty()
        if (provider.requiresKey && apiKey.isBlank()) {
            emit(CrewSpeechEvent.Completed(CrewSpeech(error = "No API key for ${provider.name}.")))
            return@flow
        }
        val endpoint = ModelEndpoint(provider.baseUrl, provider.model)
        if (!endpoint.isUsable) {
            emit(CrewSpeechEvent.Completed(CrewSpeech(error = "The model connection is not usable.")))
            return@flow
        }
        val requestConfig = provider.requestConfig(apiKey)
        val retry = agent.configOnce().retry
        val history = store.messages(group.id, CONTEXT_MESSAGES)
        val quotes = mutableMapOf<Long, CrewMessage>()
        history.mapNotNullTo(mutableSetOf()) { it.replyToId }.forEach { id ->
            store.message(id)?.let { quotes[id] = it }
        }
        val conversation = mutableListOf<ChatMessageDto>()
        conversation += ChatMessageDto(role = "system", content = prompt.agentPrompt(group, member, members))
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
                if (round > 0) {
                    val fresh = store.messagesAfter(group.id, lastSeenId)
                    if (fresh.isNotEmpty()) {
                        lastSeenId = fresh.last().id
                        fresh.forEach { message ->
                            val text = prompt.transcriptMessage(message, member.id, quotes[message.replyToId])
                            if (text.isNotBlank()) {
                                conversation += ChatMessageDto(role = "user", content = text)
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
                val result = streamRound(
                    endpoint = endpoint,
                    protocol = provider.protocol,
                    config = requestConfig,
                    messages = conversation,
                    retry = retry,
                    onDelta = { delta ->
                        roundText.append(delta)
                        activeRoundText = roundText.toString()
                        emit(CrewSpeechEvent.Delta(delta))
                    },
                    onRetry = { attempt, max, reason ->
                        emit(CrewSpeechEvent.Retry(attempt, max, reason))
                    },
                )
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
                            label = ToolLabels.of(call.name),
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
                            label = ToolLabels.of(call.name),
                            ok = outcome.result.ok,
                            summary = outcome.result.summary,
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
                val spec = ToolCatalog.find(call.name)
                if (spec == null || call.name !in specNames) {
                    return ToolOutcome(
                        result = ToolResult(false, "Error: tool ${call.name} is not allowed", "Tool not allowed"),
                    )
                }
                val result = tools.execute(call, conversationId = group.id, mode = AgentMode.Build)
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
                val outcome = tools.runStep(step, conversationId = group.id, mode = AgentMode.Build)
                if (!outcome.ok) ok = false
                lines += "${step.label}: ${if (outcome.ok) "done" else "failed"}. ${outcome.summary}"
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
            if (text.isBlank()) return@forEach
            val role = if (message.authorType == CrewAuthorType.Agent && message.authorId == selfId) {
                "assistant"
            } else {
                "user"
            }
            result += ChatMessageDto(role = role, content = text.take(MESSAGE_LIMIT))
        }
        if (result.firstOrNull()?.role == "assistant") {
            result.add(0, ChatMessageDto(role = "user", content = "The group transcript starts here."))
        }
        return result
    }

    private suspend fun streamRound(
        endpoint: ModelEndpoint,
        protocol: ChatProtocol,
        config: ProviderRequestConfig,
        messages: List<ChatMessageDto>,
        retry: RetryConfig,
        onDelta: suspend (String) -> Unit,
        onRetry: suspend (Int, Int, String) -> Unit,
    ): CompletedMessage {
        var attempt = 0
        while (true) {
            var visible = false
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
                    ),
                ).collect { event ->
                    if (event is ChatStreamEvent.Text && event.delta.isNotBlank()) visible = true
                    accumulator.apply(event)
                    if (event is ChatStreamEvent.Text && event.delta.isNotEmpty()) {
                        onDelta(event.delta)
                    }
                }
                val result = accumulator.result()
                if (result.text.isBlank() && result.toolCalls.isEmpty() && result.reasoning.isBlank()) {
                    throw ModelProtocolException("the model returned an empty reply")
                }
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
            "ask",
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
