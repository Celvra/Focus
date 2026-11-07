package fan.x0.focus.data.assistant

import fan.x0.focus.core.notify.AppPresence
import fan.x0.focus.core.notify.MessageNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single

data class SideChatSummary(
    val id: String,
    val title: String,
    val status: TaskStatus,
    val profile: WorkerProfile,
    val note: String,
    val latest: String,
    val updatedAt: Long,
    val resultSeen: Boolean,
)

@Single
class SideChatManager(
    private val chats: ChatStore,
    private val turns: ChatTurnRunner,
    private val bus: AgentEventBus,
    private val threads: ThreadRegistry,
    private val events: AgentEventSettings,
    private val models: ModelResolver,
    private val main: MainChannelSession,
    private val notifier: MessageNotifier,
    private val presence: AppPresence,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val queue = ArrayDeque<String>()
    private val pendingTasks = mutableMapOf<String, String>()
    private val running = mutableSetOf<String>()
    private val notified = mutableSetOf<String>()
    private val activeTurns = mutableMapOf<String, Long>()

    init {
        scope.launch {
            turns.states.collect { states ->
                states.forEach { (conversationId, turn) ->
                    val conversation = chats.conversation(conversationId) ?: return@forEach
                    if (conversation.kind != ConversationKind.Side) return@forEach
                    if (turn.running) activeTurns[conversationId] = turn.turnId
                    val waiting = waitingKey(turn)
                    if (waiting != null) {
                        if (conversation.taskStatus != TaskStatus.Waiting) {
                            chats.setTaskStatus(conversationId, TaskStatus.Waiting)
                        }
                        if (notified.add("$conversationId:${turn.turnId}:$waiting")) {
                            main.appendSideApproval(conversationId, conversation.title, waiting)
                            bus.publish(
                                AgentEvent.sideChatApproval(
                                    sideChatId = conversationId,
                                    title = conversation.title,
                                    note = waiting,
                                ),
                            )
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
                    val conversation = chats.conversation(conversationId) ?: return@forEach
                    if (conversation.kind != ConversationKind.Side) return@forEach
                    finish(conversation, result)
                }
            }
        }
        scope.launch {
            chats.changes.collect { updateCounts() }
        }
    }

    suspend fun create(
        title: String,
        task: String,
        profile: WorkerProfile,
        note: String = "",
        threadKey: String = "",
        providerId: String = "",
        model: String = "",
        reasoning: ReasoningLevel = ReasoningLevel.Auto,
    ): Conversation {
        val role = when (profile) {
            WorkerProfile.Light -> AgentRole.WorkerLight
            WorkerProfile.Standard -> AgentRole.WorkerStandard
            WorkerProfile.Heavy -> AgentRole.WorkerHeavy
        }
        val resolved = if (providerId.isNotBlank() && model.isNotBlank()) {
            models.resolveManual(providerId, model, reasoning)
        } else {
            models.resolve(role)
        }
        val conversation = chats.createSideChat(
            title = title.trim().take(40).ifBlank { "旁聊" },
            parentId = ChatStore.MAIN_CONVERSATION_ID,
            profile = profile,
            note = buildString {
                append(task)
                if (note.isNotBlank()) append("\n\nNote: ").append(note)
            }.take(2_000),
            providerId = resolved?.providerId.orEmpty(),
            model = resolved?.model.orEmpty(),
            reasoning = (resolved?.reasoning ?: reasoning).name,
        )
        if (threadKey.isNotBlank()) {
            threads.subscribe(threadKey, conversation.id, "origin")
        }
        threads.subscribe("side:${conversation.id}", conversation.id, "self")
        enqueue(conversation.id, task)
        updateCounts()
        return conversation
    }

    suspend fun createEmpty(profile: WorkerProfile = WorkerProfile.Standard): Conversation {
        val role = when (profile) {
            WorkerProfile.Light -> AgentRole.WorkerLight
            WorkerProfile.Standard -> AgentRole.WorkerStandard
            WorkerProfile.Heavy -> AgentRole.WorkerHeavy
        }
        val resolved = models.resolve(role)
        val conversation = chats.createSideChat(
            title = "新旁聊",
            parentId = ChatStore.MAIN_CONVERSATION_ID,
            profile = profile,
            note = "",
            providerId = resolved?.providerId.orEmpty(),
            model = resolved?.model.orEmpty(),
            reasoning = (resolved?.reasoning ?: ReasoningLevel.Auto).name,
            origin = ConversationOrigin.User,
        )
        threads.subscribe("side:${conversation.id}", conversation.id, "self")
        chats.setTaskStatus(conversation.id, TaskStatus.None)
        updateCounts()
        return conversation
    }

    suspend fun resume(id: String): Boolean {
        val conversation = chats.conversation(id) ?: return false
        if (conversation.kind != ConversationKind.Side) return false
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
        release(id)
        chats.append(
            conversationId = id,
            role = ChatRole.System,
            content = reason.ifBlank { "已停止。" },
            label = "agent_stop",
        )
        bus.publish(
            AgentEvent.sideChatResult(
                sideChatId = id,
                title = conversation.title,
                summary = reason.ifBlank { "已停止。" },
                failed = true,
            ),
        )
    }

    suspend fun retry(id: String): Boolean = resume(id)

    suspend fun showResult(id: String, message: String = "") {
        val conversation = chats.conversation(id) ?: return
        if (conversation.taskStatus == TaskStatus.Failed) return
        val summary = message.ifBlank { latestSummary(id) }
        main.appendSideResult(
            SideResultCard(
                sideChatId = id,
                title = conversation.title.ifBlank { "旁聊" },
                summary = summary,
                failed = conversation.taskStatus == TaskStatus.Failed ||
                    conversation.taskStatus == TaskStatus.Interrupted,
                createdAt = System.currentTimeMillis(),
            ),
        )
        updateCounts()
    }

    suspend fun markSeen(id: String) {
        val conversation = chats.conversation(id) ?: return
        if (conversation.resultSeen) return
        chats.setResultSeen(id, true)
        updateCounts()
    }

    suspend fun list(limit: Int = 40): List<SideChatSummary> {
        val items = chats.sideChats().take(limit)
        return items.map { conversation ->
            SideChatSummary(
                id = conversation.id,
                title = conversation.title.ifBlank { "旁聊" },
                status = conversation.taskStatus,
                profile = conversation.workerProfile,
                note = conversation.taskNote,
                latest = latestSummary(conversation.id),
                updatedAt = conversation.updatedAt,
                resultSeen = conversation.resultSeen,
            )
        }.sortedWith(compareBy<SideChatSummary> { statusRank(it.status) }.thenByDescending { it.updatedAt })
    }

    private fun statusRank(status: TaskStatus): Int = when (status) {
        TaskStatus.Waiting -> 0
        TaskStatus.Running -> 1
        TaskStatus.Failed, TaskStatus.Interrupted -> 2
        TaskStatus.Queued -> 3
        TaskStatus.Done -> 4
        TaskStatus.NoResult, TaskStatus.None -> 5
    }

    suspend fun recover() {
        val active = chats.sideChats().filter { it.taskStatus.active || turns.isRunning(it.id) }
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
                main.appendSideResult(
                    SideResultCard(
                        sideChatId = conversation.id,
                        title = conversation.title.ifBlank { "旁聊" },
                        summary = "进程中断，任务已标记为中断。",
                        failed = true,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                bus.publish(
                    AgentEvent.sideChatResult(
                        sideChatId = conversation.id,
                        title = conversation.title,
                        summary = "进程中断，任务已标记为中断。",
                        failed = true,
                    ),
                )
            }
        }
        updateCounts()
    }

    fun isActive(id: String): Boolean = turns.isRunning(id)

    suspend fun wake() {
        pump()
    }

    suspend fun deliver(event: AgentEvent): Boolean {
        val id = (event.route as? AgentEventRoute.Side)?.conversationId ?: return false
        if (chats.conversation(id)?.kind != ConversationKind.Side) return false
        val text = eventPrompt(event)
        if (turns.isRunning(id)) {
            chats.append(
                conversationId = id,
                role = ChatRole.System,
                content = text,
                label = "agent_event",
            )
            return true
        }
        chats.setTaskStatus(id, TaskStatus.Queued)
        enqueue(
            id,
            buildString {
                append("新事件：\n")
                append(text.take(4_000))
                append("\n\n根据这个事件继续当前任务。需要告诉用户时直接说明；没有值得汇报的内容就安静结束。")
            },
        )
        return true
    }

    private fun eventPrompt(event: AgentEvent): String = when (event.kind) {
        AgentEventKind.BackgroundCommand ->
            "后台命令完成：${event.text()}\n${event.payloadObject()["logs"].stringValue().take(2_000)}"
        AgentEventKind.Reminder -> "提醒：${event.text()}"
        AgentEventKind.DeviceTrigger -> "设备触发器：${event.text()}"
        AgentEventKind.FeedUpdate ->
            "订阅源更新：${event.payloadObject()["title"].stringValue()} " +
                "新增 ${event.payloadObject()["count"].stringValue()} 篇"
        AgentEventKind.WatchdogAlarm -> "看门狗：${event.text()}"
        else -> event.text().ifBlank { event.note() }
    }

    private suspend fun enqueue(id: String, task: String) {
        mutex.withLock {
            if (id in running || id in queue) return@withLock
            queue.addLast(id)
            pendingTasks[id] = task
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
        val task = pendingTasks.remove(id)
            ?: conversation.taskNote.ifBlank { "继续上次的任务。" }
        chats.setTaskStatus(id, TaskStatus.Running)
        val handle = turns.start(
            conversationId = id,
            text = task,
            options = TurnOptions(
                providerId = conversation.providerId,
                model = conversation.model,
                reasoning = ReasoningLevel.fromName(conversation.reasoning.takeIf { it.isNotBlank() }),
                agentMode = AgentMode.Build,
                autoTitle = true,
            ),
        )
        if (handle == null) {
            chats.setTaskStatus(id, TaskStatus.Queued)
            return false
        }
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
            ?: result.answer.trim().take(600)
        chats.setTaskStatus(conversation.id, status)
        chats.setResultSeen(conversation.id, presence.visibleConversationId.value == conversation.id)
        release(conversation.id)
        if (status == TaskStatus.Failed) {
            main.appendSideResult(
                SideResultCard(
                    sideChatId = conversation.id,
                    title = conversation.title.ifBlank { "旁聊" },
                    summary = summary.ifBlank { "旁聊失败" },
                    failed = true,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
        bus.publish(
            AgentEvent.sideChatResult(
                sideChatId = conversation.id,
                title = conversation.title,
                summary = summary.ifBlank { if (status == TaskStatus.Done) "完成" else "" },
                failed = status == TaskStatus.Failed,
            ),
        )
        notifier.notifyReply(
            conversationId = conversation.id,
            title = conversation.title.ifBlank { "旁聊" },
            preview = summary.ifBlank { if (status == TaskStatus.Done) "旁聊完成" else "旁聊结束" },
            failed = status == TaskStatus.Failed,
        )
        updateCounts()
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
            ?.take(600)
            .orEmpty()
    }

    private suspend fun updateCounts() {
        val sides = chats.sideChats()
        val active = sides.count { it.taskStatus.active }
        val unseen = sides.count { !it.resultSeen && it.taskStatus.settledSide }
        main.setActiveSideChats(active)
        main.setUnreadResults(unseen)
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
