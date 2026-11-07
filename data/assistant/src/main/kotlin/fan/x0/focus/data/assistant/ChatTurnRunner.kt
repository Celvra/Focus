package fan.x0.focus.data.assistant

import fan.x0.focus.core.error.AppError
import fan.x0.focus.core.error.toAppError
import fan.x0.focus.core.notify.MessageNotifier
import fan.x0.focus.core.notify.NotifyActionKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.put
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class ChatAction(
    val conversationId: String,
    val kind: NotifyActionKind,
)

class ChatTurnHandle(
    val conversationId: String,
    val turnId: Long,
    private val completion: CompletableDeferred<ChatTurnResult?>,
) {
    suspend fun await(): ChatTurnResult? = completion.await()
}

@Single
class ChatTurnRunner(
    private val engine: ChatEngine,
    private val chats: ChatStore,
    private val titles: ChatTitleGenerator,
    private val keepAlive: ChatTurnKeepAlive,
    private val background: BackgroundCommandMonitor,
    private val notifier: MessageNotifier,
    private val events: AssistantEventSink,
    private val triggerBus: AssistantTriggerBus,
    private val turnGuard: AssistantTurnGuard,
    private val journal: TurnJournalStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val titleJobs = ConcurrentHashMap<String, Job>()
    private val handles = ConcurrentHashMap<String, CompletableDeferred<ChatTurnResult?>>()
    private val progressChannels = ConcurrentHashMap<String, Channel<ChatTurnProgress>>()
    private val nextTurnId = AtomicLong()

    private val _states = MutableStateFlow<Map<String, ChatTurnState>>(emptyMap())
    val states: StateFlow<Map<String, ChatTurnState>> = _states.asStateFlow()

    private val _results = MutableStateFlow<Map<String, ChatTurnResult>>(emptyMap())
    val results: StateFlow<Map<String, ChatTurnResult>> = _results.asStateFlow()

    private val _titleUpdates = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val titleUpdates: SharedFlow<String> = _titleUpdates.asSharedFlow()

    private val _actions = MutableSharedFlow<ChatAction>(extraBufferCapacity = 16)
    val actions: SharedFlow<ChatAction> = _actions.asSharedFlow()

    init {
        scope.launch {
            triggerBus.events.collect { event ->
                if (chats.conversation(event.conversationId) == null) return@collect
                chats.append(
                    conversationId = event.conversationId,
                    role = ChatRole.System,
                    content = "Trigger ${event.label}: ${event.prompt}",
                    label = "trigger:${event.label}",
                )
                if (!isRunning(event.conversationId)) {
                    start(
                        conversationId = event.conversationId,
                        text = "",
                        options = TurnOptions(),
                        appendUser = false,
                    )
                }
            }
        }
        scope.launch {
            background.completed.collect { completion ->
                if (chats.conversation(completion.conversationId) == null) return@collect
                if (!isRunning(completion.conversationId)) {
                    start(
                        conversationId = completion.conversationId,
                        text = "",
                        options = TurnOptions(),
                        appendUser = false,
                    )
                }
            }
        }
    }

    fun state(conversationId: String): ChatTurnState =
        _states.value[conversationId] ?: ChatTurnState(conversationId = conversationId)

    fun result(conversationId: String): ChatTurnResult? = _results.value[conversationId]

    fun isRunning(conversationId: String): Boolean = jobs[conversationId]?.isActive == true

    fun start(
        conversationId: String,
        text: String,
        options: TurnOptions,
        appendUser: Boolean = true,
        progress: Channel<ChatTurnProgress>? = null,
    ): ChatTurnHandle? = launch(
        conversationId = conversationId,
        manualCompact = false,
        options = options,
        userText = text,
        progress = progress,
    ) {
        engine.send(conversationId, text, ChatMode.Chat, options, appendUser)
    }

    suspend fun startAndAwait(
        conversationId: String,
        text: String,
        options: TurnOptions,
        appendUser: Boolean = true,
        timeoutMs: Long = 20 * 60_000,
        progress: Channel<ChatTurnProgress>? = null,
    ): ChatTurnResult? = withTimeoutOrNull(timeoutMs) {
        while (true) {
            val handle = start(conversationId, text, options, appendUser, progress)
            if (handle != null) return@withTimeoutOrNull handle.await()
            states.first { it[conversationId]?.running != true }
            delay(50)
        }
        null
    }

    fun compact(conversationId: String, options: TurnOptions) {
        launch(conversationId, manualCompact = true, options = options) {
            engine.compact(conversationId, options)
        }
    }

    private fun launch(
        conversationId: String,
        manualCompact: Boolean,
        options: TurnOptions,
        userText: String = "",
        progress: Channel<ChatTurnProgress>? = null,
        flow: () -> Flow<ChatEvent>,
    ): ChatTurnHandle? {
        if (conversationId.isBlank() || isRunning(conversationId)) return null
        val turnId = nextTurnId.incrementAndGet()
        _results.update { it - conversationId }
        val state = ChatTurnState(
            conversationId = conversationId,
            turnId = turnId,
            running = true,
            userText = userText,
            compacting = manualCompact,
        )
        _states.update { it + (conversationId to state) }
        if (progress != null) {
            progressChannels.put(conversationId, progress)?.close()
        }
        keepAlive.start()
        events.emit(
            "chat.turn.start",
            conversationId,
            kotlinx.serialization.json.buildJsonObject {
                put("turn", turnId)
                put("text", userText.take(2000))
            },
        )
        val completion = CompletableDeferred<ChatTurnResult?>()
        handles[conversationId] = completion
        var compactionFailed = false
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                journal.begin(conversationId, turnId, userText)
            } catch (_: Throwable) {
            }
            try {
                flow().collect { event ->
                    when (event) {
                        is ChatEvent.Delta -> updateEntries(conversationId, turnId) { entries ->
                            entries.appendAnswer(event.text, reasoning = false, now = System.currentTimeMillis())
                        }
                        is ChatEvent.ReasoningDelta -> {
                            updateEntries(conversationId, turnId) { entries ->
                                entries.appendAnswer(event.text, reasoning = true, now = System.currentTimeMillis())
                            }
                            emitProgress(
                                conversationId,
                                ChatTurnProgress.Reasoning(event.text, progressStats(conversationId)),
                            )
                        }
                        is ChatEvent.ReasoningTimed -> {
                            updateEntries(conversationId, turnId) { entries ->
                                entries.finishReasoning(event.durationMs)
                            }
                            emitProgress(conversationId, ChatTurnProgress.ReasoningEnded)
                        }
                        is ChatEvent.UserMessageAppended -> scheduleTitle(conversationId, event.turns, options)
                        is ChatEvent.WorkingTextChanged -> updateState(conversationId, turnId) { state ->
                            state.copy(workingText = event.text)
                        }
                        is ChatEvent.ToolStarted -> {
                            try {
                                journal.toolStarted(conversationId, turnId, event.tool, event.arguments)
                            } catch (_: Throwable) {
                            }
                            updateEntries(conversationId, turnId) { entries ->
                                entries.startStep(
                                    id = event.id,
                                    label = event.label,
                                    tool = event.tool,
                                    arguments = event.arguments,
                                    startedAt = System.currentTimeMillis(),
                                )
                            }
                            emitProgress(
                                conversationId,
                                ChatTurnProgress.ToolStarted(
                                    id = event.id,
                                    label = event.label,
                                    tool = event.tool,
                                    arguments = event.arguments,
                                    stats = progressStats(conversationId),
                                ),
                            )
                        }
                        is ChatEvent.ToolFinished -> {
                            updateEntries(conversationId, turnId) { entries ->
                                entries.finishStep(
                                    id = event.id,
                                    label = event.label,
                                    ok = event.ok,
                                    summary = event.summary,
                                    output = event.output,
                                    durationMs = event.durationMs,
                                    diffs = event.diffs,
                                    media = event.media,
                                )
                            }
                            if (event.tool == RENAME_TOOL) _titleUpdates.tryEmit(conversationId)
                        }
                        is ChatEvent.ApprovalRequested -> {
                            updateEntries(conversationId, turnId) { entries ->
                                entries + ChatTurnEntry.Approval(event.request.toTurnApproval())
                            }
                            pushAction(conversationId, NotifyActionKind.Approval)
                        }
                        is ChatEvent.ApprovalResolved -> {
                            updateEntries(conversationId, turnId) { entries ->
                                entries.resolveApproval(
                                    id = event.request.id,
                                    phase = if (event.approved) {
                                        ChatTurnApprovalPhase.Running
                                    } else {
                                        ChatTurnApprovalPhase.Declined
                                    },
                                )
                            }
                            notifier.cancel(conversationId)
                        }
                        is ChatEvent.PlanProposed -> {
                            updateState(conversationId, turnId) { state ->
                                state.copy(plan = event.plan, planRequestId = event.request.id)
                            }
                            updateEntries(conversationId, turnId) { entries ->
                                entries + ChatTurnEntry.Plan(event.plan, event.request.toTurnApproval())
                            }
                            pushAction(conversationId, NotifyActionKind.Plan)
                        }
                        is ChatEvent.PlanResolved -> {
                            val requestId = _states.value[conversationId]?.planRequestId.orEmpty()
                            updateEntries(conversationId, turnId) { entries ->
                                entries.resolvePlan(
                                    id = requestId,
                                    phase = if (event.approved) ChatTurnApprovalPhase.Finished else ChatTurnApprovalPhase.Declined,
                                )
                            }
                            notifier.cancel(conversationId)
                        }
                        is ChatEvent.AskRequested -> {
                            updateEntries(conversationId, turnId) { entries ->
                                entries + ChatTurnEntry.Ask(event.request.toTurnAsk())
                            }
                            pushAction(conversationId, NotifyActionKind.Question)
                        }
                        is ChatEvent.AskResolved -> {
                            updateEntries(conversationId, turnId) { entries ->
                                entries.resolveAsk(event.request.id, event.reply)
                            }
                            notifier.cancel(conversationId)
                        }
                        is ChatEvent.TrustRequested -> {
                            updateEntries(conversationId, turnId) { entries ->
                                entries + ChatTurnEntry.Trust(event.request.toTurnTrust())
                            }
                            pushAction(conversationId, NotifyActionKind.Workspace)
                        }
                        is ChatEvent.TrustResolved -> {
                            updateEntries(conversationId, turnId) { entries ->
                                entries.resolveTrust(
                                    id = event.request.id,
                                    phase = if (event.trusted) {
                                        ChatTurnTrustPhase.Trusted
                                    } else {
                                        ChatTurnTrustPhase.Declined
                                    },
                                )
                            }
                            notifier.cancel(conversationId)
                        }
                        is ChatEvent.TodoUpdated -> updateState(conversationId, turnId) { state ->
                            state.copy(todos = event.todos)
                        }
                        is ChatEvent.StatsUpdated -> {
                            updateState(conversationId, turnId) { state ->
                                state.copy(stats = event.stats)
                            }
                            emitProgress(conversationId, ChatTurnProgress.Stats(event.stats))
                        }
                        is ChatEvent.CompactionStarted -> {
                            updateState(conversationId, turnId) { state ->
                                state.copy(compacting = true)
                            }
                            updateEntries(conversationId, turnId) { entries ->
                                entries + ChatTurnEntry.Compaction(summary = "", streaming = true)
                            }
                        }
                        is ChatEvent.CompactionDelta -> updateEntries(conversationId, turnId) { entries ->
                            val last = entries.lastOrNull() as? ChatTurnEntry.Compaction
                                ?: return@updateEntries entries
                            entries.dropLast(1) + last.copy(summary = last.summary + event.text)
                        }
                        is ChatEvent.CompactionFinished -> {
                            updateState(conversationId, turnId) { state ->
                                state.copy(compacting = false)
                            }
                            updateEntries(conversationId, turnId) { entries ->
                                val last = entries.lastOrNull() as? ChatTurnEntry.Compaction
                                when {
                                    !event.ok -> entries.filterNot { it is ChatTurnEntry.Compaction }
                                    last == null -> entries
                                    else -> entries.dropLast(1) + last.copy(
                                        streaming = false,
                                        tokensBefore = event.tokensBefore,
                                        tokensAfter = event.tokensAfter,
                                    )
                                }
                            }
                            if (manualCompact && !event.ok) compactionFailed = true
                        }
                        is ChatEvent.RetryScheduled -> updateState(conversationId, turnId) { state ->
                            state.copy(retry = RetryState(event.attempt, event.max, event.reason))
                        }
                        ChatEvent.RetryReset -> {
                            updateEntries(conversationId, turnId) { entries -> entries.resetAnswer() }
                            updateState(conversationId, turnId) { state -> state.copy(retry = null) }
                        }
                        ChatEvent.Completed -> {
                            updateState(conversationId, turnId) { state -> state.copy(retry = null) }
                            val finished = _states.value[conversationId]
                            finish(conversationId, turnId, null, compactionFailed, status = "done")
                            if (finished != null && !manualCompact) {
                                pushReply(finished, null, failed = false, silent = options.silentReply)
                            }
                        }
                        is ChatEvent.Failed -> {
                            persistPartial(conversationId, turnId)
                            val finished = _states.value[conversationId]
                            finish(conversationId, turnId, event.error, compactionFailed, status = "failed")
                            if (finished != null && !manualCompact) {
                                pushReply(finished, event.error, failed = true, silent = options.silentReply)
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    persistPartial(conversationId, turnId)
                    finish(conversationId, turnId, null, compactionFailed, status = "interrupted")
                }
                throw e
            } catch (e: Throwable) {
                persistPartial(conversationId, turnId)
                finish(conversationId, turnId, e.toAppError(), compactionFailed, status = "failed")
            } finally {
                val self = coroutineContext[Job]
                if (self != null) jobs.remove(conversationId, self)
                if (progress != null && progressChannels.remove(conversationId, progress)) {
                    progress.close()
                }
                if (handles.remove(conversationId, completion)) {
                    completion.complete(_results.value[conversationId])
                }
                runCatching { turnGuard.turnFinished(conversationId) }
            }
        }
        jobs[conversationId] = job
        job.start()
        return ChatTurnHandle(conversationId, turnId, completion)
    }

    fun stop(conversationId: String) {
        jobs[conversationId]?.cancel()
    }

    fun decide(approvalId: String, approved: Boolean) {
        engine.decide(approvalId, approved)
    }

    fun answer(askId: String, answers: List<AskAnswer>, skipped: Boolean) {
        engine.reply(askId, answers, skipped)
    }

    fun decideTrust(id: String, trusted: Boolean) {
        engine.decideTrust(id, trusted)
    }

    fun consumeResult(conversationId: String, turnId: Long): Boolean {
        val previous = _results.getAndUpdate { results ->
            if (results[conversationId]?.turnId == turnId) results - conversationId else results
        }
        return previous[conversationId]?.turnId == turnId
    }

    private fun emitProgress(conversationId: String, progress: ChatTurnProgress) {
        progressChannels[conversationId]?.trySend(progress)
    }

    private fun progressStats(conversationId: String): MessageStats {
        val state = _states.value[conversationId] ?: return MessageStats()
        val answers = state.entries.count { it is ChatTurnEntry.Answer }
        val steps = state.entries.sumOf { entry ->
            when (entry) {
                is ChatTurnEntry.Trace -> entry.steps.count { it.status != ChatTurnStepStatus.Pending }
                else -> 0
            }
        }
        return state.stats.copy(
            round = maxOf(state.stats.round, answers),
            step = maxOf(state.stats.step, steps),
        )
    }

    private fun updateEntries(
        conversationId: String,
        turnId: Long,
        reduce: (List<ChatTurnEntry>) -> List<ChatTurnEntry>,
    ) {
        _states.update { states ->
            val state = states[conversationId] ?: return@update states
            if (state.turnId != turnId) return@update states
            states + (conversationId to state.copy(entries = reduce(state.entries)))
        }
    }

    private fun updateState(
        conversationId: String,
        turnId: Long,
        reduce: (ChatTurnState) -> ChatTurnState,
    ) {
        _states.update { states ->
            val state = states[conversationId] ?: return@update states
            if (state.turnId != turnId) return@update states
            states + (conversationId to reduce(state))
        }
    }

    private suspend fun finish(
        conversationId: String,
        turnId: Long,
        error: AppError?,
        compactionFailed: Boolean,
        status: String,
    ) {
        try {
            journal.finish(conversationId, turnId, status)
        } catch (_: Throwable) {
        }
        val state = _states.value[conversationId] ?: return
        if (state.turnId != turnId) return
        val answer = state.entries
            .filterIsInstance<ChatTurnEntry.Answer>()
            .lastOrNull()
            ?.text
            ?.trim()
            .orEmpty()
        val media = state.entries
            .filterIsInstance<ChatTurnEntry.Trace>()
            .flatMap { it.steps }
            .flatMap { it.media }
            .distinctBy { it.id }
        _states.update { it - conversationId }
        val result = ChatTurnResult(conversationId, turnId, error, compactionFailed, answer, media)
        _results.update { it + (conversationId to result) }
        handles.remove(conversationId)?.complete(result)
        events.emit(
            "chat.turn.end",
            conversationId,
            kotlinx.serialization.json.buildJsonObject {
                put("turn", turnId)
                put("answer", answer.take(4000))
                put("failed", error != null)
            },
        )
    }

    private fun pushReply(
        state: ChatTurnState,
        error: AppError?,
        failed: Boolean,
        silent: Boolean,
    ) {
        if (silent) return
        scope.launch {
            val errorText = error?.let { it.serverMessage ?: it.serverTitle ?: it.type.name }.orEmpty()
            val answer = state.entries
                .filterIsInstance<ChatTurnEntry.Answer>()
                .lastOrNull()
                ?.text
                ?.trim()
                .orEmpty()
            notifier.notifyReply(
                conversationId = state.conversationId,
                title = titleOf(state.conversationId),
                preview = errorText.ifBlank { answer },
                failed = failed,
            )
        }
    }

    private fun pushAction(conversationId: String, kind: NotifyActionKind) {
        _actions.tryEmit(ChatAction(conversationId, kind))
        scope.launch {
            notifier.notifyAction(conversationId, titleOf(conversationId), kind)
        }
    }

    private suspend fun titleOf(conversationId: String): String = try {
        chats.conversation(conversationId)?.title.orEmpty()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        ""
    }

    private suspend fun scheduleTitle(conversationId: String, turns: Int, options: TurnOptions) {
        if (!options.autoTitle) return
        val due = turns == 1 || turns % TITLE_REFRESH_TURNS == 0
        val blank = chats.conversation(conversationId)?.title.isNullOrBlank()
        if (!due && !blank) return
        if (titleJobs[conversationId]?.isActive == true) return
        titleJobs[conversationId] = scope.launch {
            try {
                titles.generate(conversationId, options.providerId, options.model)
                _titleUpdates.tryEmit(conversationId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
            }
        }
    }

    private suspend fun persistPartial(conversationId: String, turnId: Long) {
        val state = _states.value[conversationId] ?: return
        if (state.turnId != turnId) return
        val answer = state.entries.filterIsInstance<ChatTurnEntry.Answer>().lastOrNull() ?: return
        val text = answer.text.trim()
        val reasoning = answer.reasoning.trim()
        if (text.isBlank() && reasoning.isBlank()) return
        val last = chats.messages(conversationId, PARTIAL_SCAN)
            .lastOrNull { it.role == ChatRole.Assistant }
        if (last != null && last.content.trim() == text && last.reasoning.trim() == reasoning) return
        chats.append(
            conversationId = conversationId,
            role = ChatRole.Assistant,
            content = text,
            reasoning = reasoning,
            stats = state.stats,
            elapsedMs = answer.reasoningMs,
        )
    }

    private fun List<ChatTurnEntry>.resolveApproval(
        id: String,
        phase: ChatTurnApprovalPhase,
    ): List<ChatTurnEntry> = map { entry ->
        if (entry is ChatTurnEntry.Approval && entry.card.id == id) {
            entry.copy(card = entry.card.copy(phase = phase))
        } else {
            entry
        }
    }

    private fun List<ChatTurnEntry>.resolvePlan(
        id: String,
        phase: ChatTurnApprovalPhase,
    ): List<ChatTurnEntry> = map { entry ->
        if (entry is ChatTurnEntry.Plan && entry.card.id == id) {
            entry.copy(card = entry.card.copy(phase = phase))
        } else {
            entry
        }
    }

    private fun List<ChatTurnEntry>.resolveAsk(id: String, reply: AskReply): List<ChatTurnEntry> = map { entry ->
        if (entry is ChatTurnEntry.Ask && entry.card.id == id) {
            entry.copy(
                card = entry.card.copy(
                    phase = if (reply.skipped) ChatTurnAskPhase.Skipped else ChatTurnAskPhase.Answered,
                    answers = reply.answers,
                ),
            )
        } else {
            entry
        }
    }

    private fun List<ChatTurnEntry>.resolveTrust(
        id: String,
        phase: ChatTurnTrustPhase,
    ): List<ChatTurnEntry> = map { entry ->
        if (entry is ChatTurnEntry.Trust && entry.card.id == id) {
            entry.copy(card = entry.card.copy(phase = phase))
        } else {
            entry
        }
    }

    private fun WorkspaceTrustRequest.toTurnTrust(): ChatTurnTrust = ChatTurnTrust(
        id = id,
        label = target.label,
        kind = target.kind,
        files = instructionFiles,
        phase = ChatTurnTrustPhase.Waiting,
    )

    private fun AskRequest.toTurnAsk(): ChatTurnAsk = ChatTurnAsk(
        id = id,
        intro = intro,
        questions = questions,
        phase = ChatTurnAskPhase.Waiting,
    )

    private fun ApprovalRequest.toTurnApproval(): ChatTurnApproval = ChatTurnApproval(
        id = id,
        title = title,
        summary = summary,
        steps = steps.map {
            ChatTurnStep(
                label = it.label,
                status = ChatTurnStepStatus.Pending,
                tool = it.tool,
                arguments = it.arguments,
            )
        },
        phase = ChatTurnApprovalPhase.Waiting,
    )

    companion object {
        private const val TITLE_REFRESH_TURNS = 10
        private const val PARTIAL_SCAN = 20
        private const val RENAME_TOOL = "rename_conversation"
    }
}
