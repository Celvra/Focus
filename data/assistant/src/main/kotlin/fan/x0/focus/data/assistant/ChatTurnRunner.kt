package fan.x0.focus.data.assistant

import fan.x0.focus.core.error.AppError
import fan.x0.focus.core.error.toAppError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
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

    fun state(conversationId: String): ChatTurnState =
        _states.value[conversationId] ?: ChatTurnState(conversationId = conversationId)

    fun result(conversationId: String): ChatTurnResult? = _results.value[conversationId]

    fun isRunning(conversationId: String): Boolean = jobs[conversationId]?.isActive == true

    fun start(conversationId: String, text: String, options: TurnOptions) {
        if (conversationId.isBlank() || isRunning(conversationId)) return
        val turnId = nextTurnId.incrementAndGet()
        _results.update { it - conversationId }
        _states.update {
            it + (conversationId to ChatTurnState(conversationId = conversationId, turnId = turnId, running = true))
        }
        keepAlive.start()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                engine.send(conversationId, text, ChatMode.Chat, options).collect { event ->
                    when (event) {
                        is ChatEvent.Delta -> updateEntries(conversationId, turnId) { entries ->
                            entries.appendAnswer(event.text, reasoning = false)
                        }
                        is ChatEvent.ReasoningDelta -> updateEntries(conversationId, turnId) { entries ->
                            entries.appendAnswer(event.text, reasoning = true)
                        }
                        is ChatEvent.UserMessageAppended -> scheduleTitle(conversationId, event.turns, options)
                        is ChatEvent.WorkingTextChanged -> updateState(conversationId, turnId) { state ->
                            state.copy(workingText = event.text)
                        }
                        is ChatEvent.ToolStarted -> updateEntries(conversationId, turnId) { entries ->
                            entries.startStep(event.label)
                        }
                        is ChatEvent.ToolFinished -> {
                            updateEntries(conversationId, turnId) { entries ->
                                entries.finishStep(event.label, event.ok, event.summary)
                            }
                            if (event.tool == RENAME_TOOL) _titleUpdates.tryEmit(conversationId)
                        }
                        is ChatEvent.ApprovalRequested -> updateEntries(conversationId, turnId) { entries ->
                            entries + ChatTurnEntry.Approval(event.request.toTurnApproval())
                        }
                        is ChatEvent.ApprovalResolved -> updateEntries(conversationId, turnId) { entries ->
                            entries.resolveApproval(
                                id = event.request.id,
                                phase = if (event.approved) {
                                    ChatTurnApprovalPhase.Running
                                } else {
                                    ChatTurnApprovalPhase.Declined
                                },
                            )
                        }
                        ChatEvent.Completed -> finish(conversationId, turnId, null)
                        is ChatEvent.Failed -> {
                            persistPartial(conversationId, turnId)
                            finish(conversationId, turnId, event.error)
                        }
                    }
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    persistPartial(conversationId, turnId)
                    finish(conversationId, turnId, null)
                }
                throw e
            } catch (e: Throwable) {
                persistPartial(conversationId, turnId)
                finish(conversationId, turnId, e.toAppError())
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

    private fun finish(conversationId: String, turnId: Long, error: AppError?) {
        val state = _states.value[conversationId] ?: return
        if (state.turnId != turnId) return
        _states.update { it - conversationId }
        _results.update { it + (conversationId to ChatTurnResult(conversationId, turnId, error)) }
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
        )
    }

    private fun List<ChatTurnEntry>.appendAnswer(text: String, reasoning: Boolean): List<ChatTurnEntry> {
        val last = lastOrNull()
        if (last is ChatTurnEntry.Answer) {
            val updated = if (reasoning) {
                last.copy(reasoning = last.reasoning + text)
            } else {
                last.copy(text = last.text + text)
            }
            return dropLast(1) + updated
        }
        return this + if (reasoning) ChatTurnEntry.Answer("", text) else ChatTurnEntry.Answer(text, "")
    }

    private fun List<ChatTurnEntry>.startStep(label: String): List<ChatTurnEntry> {
        val last = lastOrNull()
        return when (last) {
            is ChatTurnEntry.Trace -> dropLast(1) + last.copy(
                steps = last.steps.upsert(label, ChatTurnStepStatus.Running, null),
            )
            is ChatTurnEntry.Approval -> dropLast(1) + last.copy(
                card = last.card.copy(
                    steps = last.card.steps.upsert(label, ChatTurnStepStatus.Running, null),
                ),
            )
            else -> this + ChatTurnEntry.Trace(
                listOf(ChatTurnStep(label, ChatTurnStepStatus.Running)),
            )
        }
    }

    private fun List<ChatTurnEntry>.finishStep(
        label: String,
        ok: Boolean,
        summary: String,
    ): List<ChatTurnEntry> {
        val status = if (ok) ChatTurnStepStatus.Done else ChatTurnStepStatus.Failed
        val last = lastOrNull()
        return when (last) {
            is ChatTurnEntry.Trace -> dropLast(1) + last.copy(
                steps = last.steps.upsert(label, status, summary),
            )
            is ChatTurnEntry.Approval -> dropLast(1) + last.copy(
                card = last.card.copy(steps = last.card.steps.upsert(label, status, summary)),
            )
            else -> this
        }
    }

    private fun List<ChatTurnStep>.upsert(
        label: String,
        status: ChatTurnStepStatus,
        summary: String?,
    ): List<ChatTurnStep> {
        val index = indexOfLast {
            it.label == label && (it.status == ChatTurnStepStatus.Pending || it.status == ChatTurnStepStatus.Running)
        }
        if (index < 0) {
            return if (status == ChatTurnStepStatus.Running) this + ChatTurnStep(label, status, summary) else this
        }
        return toMutableList().also {
            it[index] = it[index].copy(status = status, summary = summary ?: it[index].summary)
        }
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

    private fun ApprovalRequest.toTurnApproval(): ChatTurnApproval = ChatTurnApproval(
        id = id,
        title = title,
        summary = summary,
        steps = steps.map { ChatTurnStep(it.label, ChatTurnStepStatus.Pending) },
        phase = ChatTurnApprovalPhase.Waiting,
    )

    companion object {
        private const val TITLE_REFRESH_TURNS = 10
        private const val PARTIAL_SCAN = 20
        private const val RENAME_TOOL = "rename_conversation"
    }
}
