package fan.x0.focus.data.assistant

import fan.x0.focus.core.notify.AppPresence
import fan.x0.focus.core.notify.MessageNotifier
import fan.x0.focus.core.notify.NotifyActionKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single

data class SideChatSummary(
    val id: String,
    val title: String,
    val status: TaskStatus,
    val note: String,
    val latest: String,
    val updatedAt: Long,
    val resultSeen: Boolean,
)

data class SideChatCounts(
    val active: Int = 0,
    val unread: Int = 0,
)

@Single
class SideChatManager(
    private val chats: ChatStore,
    private val turns: ChatTurnRunner,
    private val bus: AgentEventBus,
    private val events: AgentEventSettings,
    private val models: ModelResolver,
    private val notifier: MessageNotifier,
    private val presence: AppPresence,
    private val today: TodayStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val queue = ArrayDeque<String>()
    private val pendingTasks = mutableMapOf<String, String>()
    private val pendingAttachments = mutableMapOf<String, List<ChatAttachment>>()
    private val running = mutableSetOf<String>()
    private val notified = mutableSetOf<String>()
    private val activeTurns = mutableMapOf<String, Long>()

    private val _counts = MutableStateFlow(SideChatCounts())
    val counts: StateFlow<SideChatCounts> = _counts.asStateFlow()

    init {
        scope.launch {
            turns.states.collect { states ->
                states.forEach { (conversationId, turn) ->
                    val conversation = chats.conversation(conversationId) ?: return@forEach
                    if (!conversation.isSideChat) return@forEach
                    if (turn.running) activeTurns[conversationId] = turn.turnId
                    val waiting = waitingKey(turn)
                    if (waiting != null) {
                        if (conversation.taskStatus != TaskStatus.Waiting) {
                            chats.setTaskStatus(conversationId, TaskStatus.Waiting)
                        }
                        if (notified.add("$conversationId:${turn.turnId}:$waiting")) {
                            appendSideApproval(conversationId, conversation.title, waiting)
                        }
                    } else if (turn.running && conversation.taskStatus != TaskStatus.Running) {
                        chats.setTaskStatus(conversationId, TaskStatus.Running)
                    }
                }
            }
        }
        scope.launch {
            turns.results.collect { results ->
                results.forEach { (conversationId, result) ->
                    if (activeTurns[conversationId] != result.turnId) return@forEach
                    val conversation = chats.conversation(conversationId) ?: return@forEach
                    if (!conversation.isSideChat) return@forEach
                    finish(conversation, result)
                }
            }
        }
        scope.launch {
            chats.changes.collect { updateCounts() }
        }
        scope.launch {
            updateCounts()
        }
    }

    suspend fun create(
        title: String,
        task: String,
        note: String = "",
        origin: ConversationOrigin = ConversationOrigin.Agent,
        parentId: String = "",
        providerId: String = "",
        model: String = "",
        reasoning: ReasoningLevel = ReasoningLevel.Auto,
        attachments: List<ChatAttachment> = emptyList(),
    ): Conversation {
        val resolved = if (providerId.isNotBlank() && model.isNotBlank()) {
            models.resolveManual(providerId, model, reasoning)
        } else {
            models.resolve(AgentRole.Main)
        }
        val main = chats.ensureMainConversation()
        val conversation = chats.createSideChat(
            title = title.trim().take(40).ifBlank { "旁聊" },
            parentId = parentId.ifBlank { main.id },
            note = buildString {
                append(task)
                if (note.isNotBlank()) append("\n\nNote: ").append(note)
            }.take(2_000),
            providerId = resolved?.providerId.orEmpty(),
            model = resolved?.model.orEmpty(),
            reasoning = (resolved?.reasoning ?: reasoning).name,
            origin = origin,
        )
        enqueue(conversation.id, task, attachments)
        updateCounts()
        return conversation
    }

    suspend fun createEmpty(): Conversation {
        val resolved = models.resolve(AgentRole.Main)
        val main = chats.ensureMainConversation()
        val conversation = chats.createSideChat(
            title = "新旁聊",
            parentId = main.id,
            note = "",
            providerId = resolved?.providerId.orEmpty(),
            model = resolved?.model.orEmpty(),
            reasoning = (resolved?.reasoning ?: ReasoningLevel.Auto).name,
            origin = ConversationOrigin.User,
        )
        chats.setTaskStatus(conversation.id, TaskStatus.None)
        updateCounts()
        return conversation
    }

    suspend fun resume(id: String): Boolean {
        val conversation = chats.conversation(id) ?: return false
        if (!conversation.isSideChat) return false
        if (conversation.taskStatus.active || turns.isRunning(id)) return false
        chats.append(
            conversationId = id,
            role = ChatRole.System,
            content = "继续上次的任务。先检查已经完成了什么，不要重复已经完成的工作。",
            label = "agent_resume",
        )
        chats.setTaskStatus(id, TaskStatus.Queued)
        enqueue(
            id,
            "继续上次的任务。先检查已经完成了什么，不要重复已经完成的工作。如果任务已经完成，直接说明结果。",
        )
        return true
    }

    suspend fun stop(id: String, reason: String = "") {
        val conversation = chats.conversation(id) ?: return
        if (conversation.kind != ConversationKind.Side) return
        mutex.withLock {
            queue.remove(id)
            pendingTasks.remove(id)
        }
        turns.stop(id)
        chats.setTaskStatus(id, TaskStatus.Interrupted)
        chats.append(
            conversationId = id,
            role = ChatRole.System,
            content = reason.ifBlank { "已停止。" },
            label = "agent_stop",
        )
        release(id)
    }

    suspend fun retry(id: String): Boolean = resume(id)

    suspend fun markSeen(id: String) {
        val conversation = chats.conversation(id) ?: return
        if (conversation.resultSeen) return
        chats.setResultSeen(id, true)
        updateCounts()
    }

    suspend fun list(limit: Int = 40): List<SideChatSummary> {
        val items = chats.conversations()
            .filter { it.id != ChatStore.ONBOARDING_CONVERSATION_ID }
            .take(limit)
        return items.map { conversation ->
            SideChatSummary(
                id = conversation.id,
                title = conversation.title.ifBlank { "旁聊" },
                status = conversation.taskStatus,
                note = conversation.taskNote,
                latest = latestSummary(conversation.id),
                updatedAt = conversation.updatedAt,
                resultSeen = conversation.resultSeen,
            )
        }.sortedWith(compareBy<SideChatSummary> { it.status.sideChatRank() }.thenByDescending { it.updatedAt })
    }

    suspend fun recover() {
        val active = chats.sideChats().filter {
            it.isSideChat && (it.taskStatus.active || turns.isRunning(it.id))
        }
        active.forEach { conversation ->
            if (conversation.taskStatus == TaskStatus.Queued) {
                enqueue(
                    conversation.id,
                    conversation.taskNote.ifBlank { "继续上次的任务。" },
                )
            } else {
                chats.setTaskStatus(conversation.id, TaskStatus.Interrupted)
                chats.append(
                    conversationId = conversation.id,
                    role = ChatRole.System,
                    content = "进程中断，任务已标记为中断。可以重试。",
                    label = "agent_interrupted",
                )
                publishResult(conversation, "进程中断，任务已标记为中断。", failed = true, turnId = 0)
            }
        }
        updateCounts()
    }

    fun isActive(id: String): Boolean = turns.isRunning(id)

    suspend fun wake() {
        pump()
    }

    suspend fun deliver(conversationId: String, text: String) {
        val conversation = chats.conversation(conversationId) ?: return
        if (!conversation.isSideChat) return
        if (conversation.archived) return
        if (turns.isRunning(conversationId)) {
            chats.append(
                conversationId = conversationId,
                role = ChatRole.System,
                content = text,
                label = "agent_event",
            )
            return
        }
        chats.setTaskStatus(conversationId, TaskStatus.Queued)
        enqueue(
            conversationId,
            buildString {
                append("新事件：\n")
                append(text.take(4_000))
                append("\n\n根据这个事件继续当前任务。需要告诉用户时直接说明；没有值得汇报的内容就安静结束。")
            },
        )
    }

    private suspend fun enqueue(id: String, task: String, attachments: List<ChatAttachment> = emptyList()) {
        mutex.withLock {
            if (id in running || id in queue) return@withLock
            queue.addLast(id)
            pendingTasks[id] = task
            if (attachments.isNotEmpty()) pendingAttachments[id] = attachments
        }
        pump()
    }

    private suspend fun pump() {
        mutex.withLock {
            val max = events.configOnce().maxConcurrentSideChats
            while (running.size < max && queue.isNotEmpty()) {
                val id = queue.removeFirst()
                if (startNow(id)) {
                    running.add(id)
                }
            }
        }
    }

    private suspend fun startNow(id: String): Boolean {
        val conversation = chats.conversation(id) ?: return false
        if (conversation.archived) return false
        val task = pendingTasks.remove(id)
            ?: conversation.taskNote.ifBlank { "继续上次的任务。" }
        val attachments = pendingAttachments.remove(id).orEmpty()
        chats.setTaskStatus(id, TaskStatus.Running)
        val handle = turns.start(
            conversationId = id,
            text = task,
            options = TurnOptions(
                providerId = conversation.providerId,
                model = conversation.model,
                reasoning = ReasoningLevel.fromName(conversation.reasoning.takeIf { it.isNotBlank() }),
                attachments = attachments,
                agentMode = AgentMode.Build,
                autoTitle = true,
            ),
        )
        if (handle == null) {
            chats.setTaskStatus(id, TaskStatus.Queued)
            return false
        }
        activeTurns[id] = handle.turnId
        return true
    }

    private suspend fun finish(conversation: Conversation, result: ChatTurnResult) {
        val expected = activeTurns[conversation.id]
        if (expected != null && expected != result.turnId) return
        activeTurns.remove(conversation.id)
        if (conversation.taskStatus == TaskStatus.Interrupted) {
            release(conversation.id)
            return
        }
        val status = when {
            result.error != null -> TaskStatus.Failed
            result.answer.isBlank() -> TaskStatus.NoResult
            else -> TaskStatus.Done
        }
        val summary = result.error?.let { it.serverMessage ?: it.serverTitle ?: it.type.name }
            ?: result.answer.trim()
        chats.setTaskStatus(conversation.id, status)
        chats.setResultSeen(conversation.id, presence.visibleConversationId.value == conversation.id)
        release(conversation.id)
        if (summary.isNotBlank() && events.configOnce().postResultsToToday) {
            runCatching {
                today.postUpdate(
                    headline = conversation.title.ifBlank { "旁聊" },
                    body = summary,
                    reason = if (status == TaskStatus.Failed) "旁聊失败" else "旁聊完成",
                    category = "旁聊",
                    icon = if (status == TaskStatus.Failed) "warning" else "task",
                    articleId = null,
                )
            }
        }
        publishResult(conversation, summary, status == TaskStatus.Failed, result.turnId)
        updateCounts()
    }

    private suspend fun publishResult(
        conversation: Conversation,
        summary: String,
        failed: Boolean,
        turnId: Long,
    ) {
        val main = chats.ensureMainConversation()
        if (main.id == conversation.id) return
        if (summary.isBlank() && !failed) return
        bus.publish(
            AgentEvent.sideChatResult(
                mainId = main.id,
                sideChatId = conversation.id,
                title = conversation.title.ifBlank { "旁聊" },
                summary = summary.take(4_000),
                failed = failed,
                turnId = turnId,
            ),
        )
    }

    private suspend fun appendSideApproval(sideChatId: String, title: String, note: String) {
        val main = chats.ensureMainConversation()
        chats.append(
            conversationId = main.id,
            role = ChatRole.System,
            content = "旁聊 $title 等待审批",
            label = "side_approval",
        )
        notifier.notifyAction(sideChatId, title.ifBlank { "旁聊" }, NotifyActionKind.Approval)
    }

    private suspend fun release(id: String) {
        mutex.withLock {
            running.remove(id)
            notified.removeAll { it.startsWith("$id:") }
        }
        pump()
    }

    private suspend fun latestSummary(id: String): String {
        return chats.messages(id, limit = 12)
            .lastOrNull { it.role == ChatRole.Assistant && it.content.isNotBlank() }
            ?.content
            ?.trim()
            .orEmpty()
    }

    private suspend fun updateCounts() {
        val sides = chats.sideChats().filter { it.isSideChat }
        val active = sides.count { it.taskStatus.active }
        val unseen = sides.count { !it.resultSeen && it.taskStatus.settledSide }
        _counts.update { SideChatCounts(active = active, unread = unseen) }
    }

    private val TaskStatus.settledSide: Boolean
        get() = this == TaskStatus.Done || this == TaskStatus.Failed || this == TaskStatus.NoResult ||
            this == TaskStatus.Interrupted

    private fun waitingKey(turn: ChatTurnState): String? {
        turn.entries.forEach { entry ->
            when (entry) {
                is ChatTurnEntry.Approval ->
                    if (entry.card.phase == ChatTurnApprovalPhase.Waiting) return "审批 ${entry.card.title}"
                is ChatTurnEntry.Plan ->
                    if (entry.card.phase == ChatTurnApprovalPhase.Waiting) return "计划 ${entry.card.title}"
                is ChatTurnEntry.Ask ->
                    if (entry.card.phase == ChatTurnAskPhase.Waiting) return "提问 ${entry.card.intro}"
                is ChatTurnEntry.Trust ->
                    if (entry.card.phase == ChatTurnTrustPhase.Waiting) return "工作区授权 ${entry.card.label}"
                else -> Unit
            }
        }
        return null
    }
}
