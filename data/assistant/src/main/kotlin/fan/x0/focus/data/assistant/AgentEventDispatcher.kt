package fan.x0.focus.data.assistant

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single

@Single
class AgentEventDispatcher(
    private val bus: AgentEventBus,
    private val events: AgentEventStore,
    private val chats: ChatStore,
    private val turns: ChatTurnRunner,
    private val sideChats: SideChatManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            bus.events.collect { event -> dispatch(event) }
        }
        scope.launch {
            while (true) {
                delay(RETRY_SCAN_MS)
                runCatching {
                    events.pending(limit = 50).forEach { dispatch(it) }
                }
            }
        }
    }

    suspend fun dispatch(event: AgentEvent) {
        try {
            when (event.kind) {
                AgentEventKind.SideChatResult -> injectSideResult(event)
                AgentEventKind.Scheduled,
                AgentEventKind.Webhook,
                -> when (event.triggerTarget()) {
                    AgentTriggerTarget.Main -> runInMain(event)
                    AgentTriggerTarget.Side -> startSideChat(event)
                }
                AgentEventKind.BackgroundCommand,
                AgentEventKind.DeviceTrigger,
                -> deliverToConversation(event)
                AgentEventKind.Reminder,
                AgentEventKind.FeedUpdate,
                -> appendMainNotice(event)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            val retry = event.attempts + 1 < MAX_ATTEMPTS
            events.fail(
                event.id,
                e.message ?: e.javaClass.simpleName,
                retryAt = if (retry) System.currentTimeMillis() + RETRY_DELAY_MS else null,
            )
        }
    }

    private suspend fun injectSideResult(event: AgentEvent) {
        val main = chats.ensureMainConversation()
        if (event.sideChatId() == main.id) {
            events.complete(event.id)
            return
        }
        if (turns.isRunning(main.id)) return
        if (!events.claim(event.id)) return
        val label = "side_result:${event.sideChatId()}"
        val existing = chats.messages(main.id, limit = 20).any { it.label == label }
        if (!existing) {
            chats.append(
                conversationId = main.id,
                role = ChatRole.System,
                content = buildString {
                    append("旁聊《")
                    append(event.title().ifBlank { "旁聊" })
                    append(if (event.failed()) "》失败：\n" else "》完成：\n")
                    append(event.summary().take(4_000))
                    append("\n\n这是一条后台结果。判断是否需要告诉用户，或者继续处理。")
                    append("需要细节就调用 read_side_chat，不需要打扰用户就调用 stay_silent。")
                },
                label = label,
            )
        }
        startMainTurn(autoTitle = false, silent = true, event = event)
    }

    private suspend fun runInMain(event: AgentEvent) {
        val main = chats.ensureMainConversation()
        if (turns.isRunning(main.id)) return
        if (!events.claim(event.id)) return
        val label = "trigger:${event.kind.wire}"
        if (chats.messages(main.id, limit = 10).none { it.label == label && it.content == event.instruction() }) {
            chats.append(
                conversationId = main.id,
                role = ChatRole.System,
                content = event.instruction(),
                label = label,
            )
        }
        startMainTurn(autoTitle = false, silent = false, event = event)
    }

    private suspend fun startMainTurn(autoTitle: Boolean, silent: Boolean, event: AgentEvent) {
        val main = chats.ensureMainConversation()
        val handle = turns.start(
            conversationId = main.id,
            text = "",
            options = TurnOptions(
                autoTitle = autoTitle,
                silentReply = silent,
            ),
            appendUser = false,
        )
        if (handle == null) {
            events.fail(
                event.id,
                "main is busy",
                retryAt = System.currentTimeMillis() + RETRY_SCAN_MS,
            )
            return
        }
        events.complete(event.id)
    }

    private suspend fun startSideChat(event: AgentEvent) {
        if (!events.claim(event.id)) return
        sideChats.create(
            title = event.title().ifBlank { "自动任务" },
            task = event.instruction(),
            note = "",
            origin = ConversationOrigin.System,
        )
        events.complete(event.id)
    }

    private suspend fun deliverToConversation(event: AgentEvent) {
        val main = chats.ensureMainConversation()
        val id = event.conversationId().ifBlank {
            if (event.target == AgentTriggerTarget.Main.wire) main.id else event.target
        }
        val conversation = chats.conversation(id)
        if (conversation == null || conversation.archived) {
            events.complete(event.id)
            return
        }
        if (conversation.kind == ConversationKind.Side) {
            if (!events.claim(event.id)) return
            sideChats.deliver(conversation.id, event.instruction())
            events.complete(event.id)
            return
        }
        if (turns.isRunning(conversation.id)) return
        if (!events.claim(event.id)) return
        chats.append(
            conversationId = conversation.id,
            role = ChatRole.System,
            content = event.instruction(),
            label = "event:${event.kind.wire}",
        )
        val handle = turns.start(
            conversationId = conversation.id,
            text = "",
            options = TurnOptions(autoTitle = false, silentReply = false),
            appendUser = false,
        )
        if (handle == null) {
            events.fail(event.id, "main is busy", retryAt = System.currentTimeMillis() + RETRY_SCAN_MS)
        } else {
            events.complete(event.id)
        }
    }

    private suspend fun appendMainNotice(event: AgentEvent) {
        if (!events.claim(event.id)) return
        val main = chats.ensureMainConversation()
        chats.append(
            conversationId = main.id,
            role = ChatRole.System,
            content = event.instruction(),
            label = "event:${event.kind.wire}",
        )
        events.complete(event.id)
    }

    suspend fun resume() {
        bus.resumePending()
        events.pending(limit = 50).forEach { dispatch(it) }
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 20_000L
        const val RETRY_SCAN_MS = 15_000L
    }
}
