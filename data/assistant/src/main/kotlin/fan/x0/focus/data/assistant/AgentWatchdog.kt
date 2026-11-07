package fan.x0.focus.data.assistant

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap

@Single
class AgentWatchdog(
    private val chats: ChatStore,
    private val turns: ChatTurnRunner,
    private val journal: TurnJournalStore,
    private val sideChats: SideChatManager,
    private val router: RouterSession,
    private val bus: AgentEventBus,
    private val main: MainChannelSession,
    private val settings: AgentEventSettings,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val progress = ConcurrentHashMap<String, Progress>()
    private val alarmed = ConcurrentHashMap.newKeySet<String>()

    fun start() {
        scope.launch {
            while (true) {
                delay(SCAN_MS)
                runCatching { scan() }
            }
        }
    }
    private suspend fun scan() {
        if (!settings.configOnce().watchdogEnabled) return
        val now = System.currentTimeMillis()
        scanTurns(now)
        scanJournal(now)
        scanSideChats(now)
    }

    private suspend fun scanTurns(now: Long) {
        turns.states.value.forEach { (conversationId, turn) ->
            if (!turn.running) {
                progress.remove(conversationId)
                return@forEach
            }
            val answerLength = turn.entries
                .filterIsInstance<ChatTurnEntry.Answer>()
                .lastOrNull()
                ?.text
                ?.length
                ?: 0
            val snapshot = Progress(turn.stats.totalTokens.toLong(), answerLength, now)
            val previous = progress.put(conversationId, snapshot)
            val lastTouch = journal.latest(conversationId)?.updatedAt ?: 0
            val waiting = turn.entries.any { entry ->
                when (entry) {
                    is ChatTurnEntry.Approval -> entry.card.phase == ChatTurnApprovalPhase.Waiting
                    is ChatTurnEntry.Plan -> entry.card.phase == ChatTurnApprovalPhase.Waiting
                    is ChatTurnEntry.Ask -> entry.card.phase == ChatTurnAskPhase.Waiting
                    is ChatTurnEntry.Trust -> entry.card.phase == ChatTurnTrustPhase.Waiting
                    else -> false
                }
            }
            if (!waiting && previous != null &&
                previous.tokens == snapshot.tokens &&
                previous.answer == snapshot.answer &&
                now - maxOf(previous.at, lastTouch) > NO_PROGRESS_MS
            ) {
                alarm(conversationId, "no_progress", "长时间没有输出，已停止这次运行。")
                stop(conversationId)
                return@forEach
            }
            val retry = turn.retry
            if (retry != null && retry.attempt >= retry.max && retry.max > 0) {
                alarm(conversationId, "retry_exhausted", "重试次数已用完。")
                stop(conversationId)
            }
        }
    }

    private suspend fun scanJournal(now: Long) {
        journal.running().forEach { entry ->
            val conversation = chats.conversation(entry.conversationId) ?: return@forEach
            val tooLong = now - entry.startedAt > TURN_TIMEOUT_MS
            val repeated = entry.repeatedCalls >= REPEAT_LIMIT
            if (!tooLong && !repeated) return@forEach
            val code = if (repeated) "repeated_tools" else "timeout"
            val text = if (repeated) {
                "重复调用同一个工具，已停止。"
            } else {
                "运行超过 30 分钟，已停止。"
            }
            if (conversation.kind == ConversationKind.Router) {
                alarm(conversation.id, code, "Router $text")
                router.stopCurrent()
            } else {
                alarm(conversation.id, code, text)
                stop(entry.conversationId)
                chats.setTaskStatus(entry.conversationId, TaskStatus.Interrupted)
            }
            journal.finish(entry.conversationId, entry.turnId, "interrupted", text)
        }
    }

    private suspend fun scanSideChats(now: Long) {
        val sides = chats.sideChats()
        sides.forEach { conversation ->
            when {
                conversation.taskStatus == TaskStatus.Queued &&
                    now - conversation.updatedAt > QUEUE_STARVATION_MS -> {
                    alarm(conversation.id, "starved", "排队太久，已尝试重新启动。")
                    sideChats.wake()
                }
                conversation.taskStatus == TaskStatus.Running &&
                    !turns.isRunning(conversation.id) -> {
                    alarm(conversation.id, "zombie", "任务没有在运行，已标记为中断。")
                    chats.setTaskStatus(conversation.id, TaskStatus.Interrupted)
                }
                conversation.taskStatus == TaskStatus.Waiting &&
                    now - conversation.updatedAt > WAITING_TIMEOUT_MS -> {
                    alarm(conversation.id, "waiting", "等待审批超过 30 分钟。")
                }
            }
        }
    }

    private suspend fun alarm(conversationId: String, code: String, text: String) {
        val key = "$conversationId:$code"
        if (!alarmed.add(key)) return
        val conversation = chats.conversation(conversationId)
        val title = conversation?.title?.ifBlank { "旁聊" } ?: "旁聊"
        main.appendWatchdog("$title：$text")
        bus.publish(
            AgentEvent.watchdogAlarm(
                conversationId = conversationId,
                code = code,
                text = text,
                dedupeKey = "watchdog:$conversationId:$code:${System.currentTimeMillis() / 600_000}",
            ),
        )
    }

    private suspend fun stop(conversationId: String) {
        val conversation = chats.conversation(conversationId)
        if (conversation?.kind == ConversationKind.Router) {
            router.stopCurrent()
        } else {
            if (conversation?.kind == ConversationKind.Side) {
                chats.setTaskStatus(conversationId, TaskStatus.Interrupted)
            }
            turns.stop(conversationId)
        }
    }

    private data class Progress(
        val tokens: Long,
        val answer: Int,
        val at: Long,
    )

    private companion object {
        const val SCAN_MS = 20_000L
        const val NO_PROGRESS_MS = 15L * 60 * 1000
        const val TURN_TIMEOUT_MS = 30L * 60 * 1000
        const val QUEUE_STARVATION_MS = 10L * 60 * 1000
        const val WAITING_TIMEOUT_MS = 30L * 60 * 1000
        const val REPEAT_LIMIT = 5
    }
}
