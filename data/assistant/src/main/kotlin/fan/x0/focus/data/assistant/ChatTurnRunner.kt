package fan.x0.focus.data.assistant

import fan.x0.focus.core.error.AppError
import fan.x0.focus.core.error.toAppError
import fan.x0.focus.core.notify.MessageNotifier
import fan.x0.focus.core.notify.NotifyActionKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Single
class ChatTurnRunner(
    private val engine: ChatEngine,
    private val chats: ChatStore,
    private val titles: ChatTitleGenerator,
    private val keepAlive: ChatTurnKeepAlive,
    private val background: BackgroundCommandMonitor,
    private val notifier: MessageNotifier,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val titleJobs = ConcurrentHashMap<String, Job>()
    private val nextTurnId = AtomicLong()

    private val _states = MutableStateFlow<Map<String, ChatTurnState>>(emptyMap())
    val states: StateFlow<Map<String, ChatTurnState>> = _states.asStateFlow()

    private val _results = MutableStateFlow<Map<String, ChatTurnResult>>(emptyMap())
    val results: StateFlow<Map<String, ChatTurnResult>> = _results.asStateFlow()

    private val _titleUpdates = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val titleUpdates: SharedFlow<String> = _titleUpdates.asSharedFlow()

    init {
        scope.launch {
            background.completed.collect { completion ->
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
    ) {
        launch(conversationId, manualCompact = false, options = options) {
            engine.send(conversationId, text, ChatMode.Chat, options, appendUser)
        }
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
        flow: () -> Flow<ChatEvent>,
    ) {
        if (conversationId.isBlank() || isRunning(conversationId)) return
        val turnId = nextTurnId.incrementAndGet()
        _results.update { it - conversationId }
        val state = ChatTurnState(
            conversationId = conversationId,
            turnId = turnId,
            running = true,
            compacting = manualCompact,
        )
        _states.update { it + (conversationId to state) }
        keepAlive.start()
        var compactionFailed = false
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                flow().collect { event ->
                    when (event) {
                        is ChatEvent.Delta -> updateEntries(conversationId, turnId) { entries ->
                            entries.appendAnswer(event.text, reasoning = false, now = System.currentTimeMillis())
                        }
                        is ChatEvent.ReasoningDelta -> updateEntries(conversationId, turnId) { entries ->
                            entries.appendAnswer(event.text, reasoning = true, now = System.currentTimeMillis())
                        }
                        is ChatEvent.ReasoningTimed -> updateEntries(conversationId, turnId) { entries ->
                            entries.finishReasoning(event.durationMs)
                        }
                        is ChatEvent.UserMessageAppended -> scheduleTitle(conversationId, event.turns, options)
                        is ChatEvent.WorkingTextChanged -> updateState(conversationId, turnId) { state ->
                            state.copy(workingText = event.text)
                        }
                        is ChatEvent.ToolStarted -> updateEntries(conversationId, turnId) { entries ->
                            entries.startStep(
                                id = event.id,
                                label = event.label,
                                tool = event.tool,
                                arguments = event.arguments,
                                startedAt = System.currentTimeMillis(),
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
                        is ChatEvent.TodoUpdated -> updateState(conversationId, turnId) { state ->
                            state.copy(todos = event.todos)
                        }
                        is ChatEvent.StatsUpdated -> updateState(conversationId, turnId) { state ->
                            state.copy(stats = event.stats)
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
                            finish(conversationId, turnId, null, compactionFailed)
                            if (finished != null && !manualCompact) {
                                pushReply(finished, null, failed = false)
                            }
                        }
                        is ChatEvent.Failed -> {
                            persistPartial(conversationId, turnId)
                            val finished = _states.value[conversationId]
                            finish(conversationId, turnId, event.error, compactionFailed)
                            if (finished != null && !manualCompact) {
                                pushReply(finished, event.error, failed = true)
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    persistPartial(conversationId, turnId)
                    finish(conversationId, turnId, null, compactionFailed)
                }
                throw e
            } catch (e: Throwable) {
                persistPartial(conversationId, turnId)
                finish(conversationId, turnId, e.toAppError(), compactionFailed)
            } finally {
                jobs.remove(conversationId)
            }
        }
        jobs[conversationId] = job
        job.start()
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

    fun consumeResult(conversationId: String, turnId: Long): Boolean {
        val previous = _results.getAndUpdate { results ->
            if (results[conversationId]?.turnId == turnId) results - conversationId else results
        }
        return previous[conversationId]?.turnId == turnId
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

    private fun finish(conversationId: String, turnId: Long, error: AppError?, compactionFailed: Boolean) {
        val state = _states.value[conversationId] ?: return
        if (state.turnId != turnId) return
        _states.update { it - conversationId }
        _results.update {
            it + (conversationId to ChatTurnResult(conversationId, turnId, error, compactionFailed))
        }
    }

    private fun pushReply(state: ChatTurnState, error: AppError?, failed: Boolean) {
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
