package fan.x0.focus.data.assistant

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Single
class RouterSession(
    private val engine: ChatEngine,
    private val chats: ChatStore,
    private val models: ModelResolver,
    private val events: AgentEventStore,
    private val eventSettings: AgentEventSettings,
    private val tools: RouterToolHandler,
    private val sideChats: SideChatManager,
    private val main: MainChannelSession,
    private val memory: RouterMemoryStore,
    private val workspace: WorkspaceStore,
    private val journal: TurnJournalStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<AgentEvent>(Channel.UNLIMITED)
    private val queuedIds = mutableSetOf<Long>()

    @Volatile
    private var currentJob: Job? = null

    init {
        scope.launch {
            for (first in queue) {
                val batch = mutableListOf(first)
                while (batch.size < MAX_BATCH) {
                    val next = queue.tryReceive().getOrNull() ?: break
                    batch += next
                }
                process(batch)
            }
        }
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(RETRY_SCAN_MS)
                events.pending(target = AgentEventRoute.ROUTER_TARGET, limit = 20).forEach { submit(it) }
            }
        }
    }

    fun submit(event: AgentEvent) {
        val id = event.id
        val added = synchronized(queuedIds) {
            if (id == 0L) true else queuedIds.add(id)
        }
        if (added) queue.trySend(event)
    }

    suspend fun prepare() {
        chats.ensureMainConversation()
        chats.ensureRouterConversation()
        models.resolve(AgentRole.Router)?.let { resolved ->
            main.setRouterModel("${resolved.provider.name} · ${resolved.model}")
        }
    }

    fun stopCurrent() {
        currentJob?.cancel()
    }

    private suspend fun process(batch: List<AgentEvent>) {
        val claimed = batch.filter { events.claim(it.id) }
        synchronized(queuedIds) { batch.forEach { queuedIds.remove(it.id) } }
        if (claimed.isEmpty()) return
        main.setWorking(true, "Router 正在处理")
        var journalStatus = "failed"
        try {
            val resolved = models.resolve(AgentRole.Router)
            if (resolved == null) {
                failAll(claimed, "没有可用的 Router 模型")
                return
            }
            val router = chats.ensureRouterConversation()
            val prompt = buildPrompt(claimed)
            val flags = RouterTurnFlags()
            val eventText = claimed.joinToString("\n\n") { describe(it) }
            chats.append(
                conversationId = router.id,
                role = ChatRole.User,
                content = eventText,
                label = "agent_event",
            )
            val turnId = System.currentTimeMillis()
            runCatching { journal.begin(router.id, turnId, eventText.take(500)) }
            val job = scope.async {
                val outcome = TurnOutcome()
                engine.send(
                    conversationId = router.id,
                    text = eventText,
                    mode = ChatMode.Chat,
                    options = TurnOptions(
                        providerId = resolved.providerId,
                        model = resolved.model,
                        reasoning = resolved.reasoning,
                        agentMode = AgentMode.Build,
                        promptOverride = prompt,
                        allowedTools = RouterToolCatalog.names,
                        toolSpecs = RouterToolCatalog.specs,
                        toolHandler = { call -> tools.handle(call, flags) },
                        autoTitle = false,
                        silentReply = true,
                    ),
                    appendUser = false,
                ).collect { event ->
                    when (event) {
                        is ChatEvent.Delta -> outcome.answer += event.text
                        is ChatEvent.Failed -> outcome.error = event.error.serverMessage
                            ?: event.error.serverTitle
                            ?: event.error.type.name
                        else -> Unit
                    }
                }
                outcome
            }
            currentJob = job
            val outcome = try {
                job.await()
            } catch (e: CancellationException) {
                failAll(claimed, "Router 已停止")
                journalStatus = "interrupted"
                return
            } finally {
                currentJob = null
            }
            if (outcome.error != null) {
                failAll(claimed, outcome.error.orEmpty())
                return
            }
            if (!flags.silent) {
                val text = outcome.answer.trim().ifBlank {
                    if (flags.started > 0) "已开始旁聊，完成后会通知你。" else ""
                }
                if (text.isNotBlank()) main.appendAssistant(text)
            }
            claimed.forEach { events.complete(it.id) }
            journalStatus = "done"
        } catch (e: Throwable) {
            failAll(claimed, e.message ?: e.javaClass.simpleName)
        } finally {
            main.setWorking(false)
            val routerId = ChatStore.ROUTER_CONVERSATION_ID
            journal.latest(routerId)?.let { entry ->
                if (entry.status == "running") {
                    runCatching { journal.finish(routerId, entry.turnId, journalStatus) }
                }
            }
        }
    }

    private suspend fun failAll(batch: List<AgentEvent>, error: String) {
        batch.forEach { event ->
            val retry = event.attempts + 1 < MAX_ATTEMPTS
            events.fail(
                event.id,
                error,
                retryAt = if (retry) System.currentTimeMillis() + RETRY_DELAY_MS * (event.attempts + 1) else null,
            )
        }
        if (batch.any { it.attempts + 1 >= MAX_ATTEMPTS }) {
            main.appendNotice("Router 处理失败：${error.take(200)}")
        }
    }

    private suspend fun buildPrompt(batch: List<AgentEvent>): String {
        val config = eventSettings.configOnce()
        val name = workspace.assistantName()
        val userName = workspace.userName().orEmpty()
        val mainMessages = chats.messages(ChatStore.MAIN_CONVERSATION_ID, limit = 12)
        val sides = sideChats.list(limit = 12)
        val recent = events.recent(limit = 12)
        val note = memory.read()
        val builder = StringBuilder()
        builder.appendLine("You are $name, the router of a personal agent system on the user's Android phone.")
        builder.appendLine("The main conversation is your only human channel. Side chats do the work. You never use shell, files, devices, or apps directly.")
        builder.appendLine("Rules:")
        builder.appendLine("- Reply directly only for simple conversation, questions you can answer without tools, or decisions about routing.")
        builder.appendLine("- For any work that needs tools, files, devices, apps, long steps, or parallel effort, call start_side_chat with a complete, self-contained task.")
        builder.appendLine("- Start one side chat per independent task. Several can run in parallel. Do not start a side chat for simple chat.")
        builder.appendLine("- When you start work, tell the user in one short line what is running. Do not claim the work is done.")
        builder.appendLine("- Side chat results arrive as events. If the user can use or act on a result, call show_side_chat_result. Otherwise call stay_silent. Never repeat a full side chat trajectory in the main conversation.")
        builder.appendLine("- Failures already show a card in the main conversation. Add at most one short sentence when it helps.")
        builder.appendLine("- If you need a decision or a missing detail, call ask with one concrete question. Do not guess.")
        builder.appendLine("- Before slow work call set_working_text with a short line in the user's language.")
        builder.appendLine("- Never expose tool names, ids, JSON, or internal status codes to the user.")
        builder.appendLine("- Use recall or read_side_chat when a result needs detail. Do not ask the user to read a side chat for you.")
        builder.appendLine("- Memory is yours. Use read_memory and write_memory for durable routing preferences and standing rules.")
        builder.appendLine("- Change role models only when the user explicitly asks. Use list_role_models first.")
        builder.appendLine("- Pass a thread_key to start_side_chat when the event carries one and the work continues that thread.")
        builder.appendLine("- User messages always go through you. Context events may go straight to a subscribed side chat.")
        builder.appendLine("- Stay silent when nothing needs the user. Quiet is the default.")
        builder.appendLine("- ${language()}")
        if (userName.isNotBlank()) builder.appendLine("- The user's name is $userName.")
        builder.appendLine("- Current time: ${time()}.")
        builder.appendLine()
        builder.appendLine("Sources:")
        AgentEventSource.entries.forEach { source ->
            val speculative = if (config.speculative(source)) "speculative" else "normal"
            builder.appendLine("- ${source.wire}: ${if (config.enabled(source)) "on" else "off"}, $speculative")
        }
        builder.appendLine()
        builder.appendLine("Recent main conversation:")
        if (mainMessages.isEmpty()) {
            builder.appendLine("(empty)")
        } else {
            mainMessages.forEach { message ->
                val who = when (message.role) {
                    ChatRole.User -> "User"
                    ChatRole.Assistant -> "Assistant"
                    ChatRole.System -> "App"
                    ChatRole.Tool -> "Tool"
                }
                val body = message.content.trim().replace('\n', ' ').take(500)
                if (body.isNotBlank()) builder.appendLine("$who: $body")
            }
        }
        builder.appendLine()
        builder.appendLine("Side chats:")
        if (sides.isEmpty()) {
            builder.appendLine("(none)")
        } else {
            sides.forEach { side ->
                builder.appendLine(
                    "- ${side.id} | ${side.status.wire} | ${side.profile.wire} | ${side.title} | " +
                        side.latest.replace('\n', ' ').take(200),
                )
            }
        }
        builder.appendLine()
        builder.appendLine("Recent events:")
        if (recent.isEmpty()) {
            builder.appendLine("(none)")
        } else {
            recent.forEach { event ->
                builder.appendLine("- ${event.kind.wire} / ${event.source.wire}: ${describe(event).replace('\n', ' ').take(300)}")
            }
        }
        builder.appendLine()
        builder.appendLine("Router memory:")
        builder.appendLine(note.trim().ifBlank { "(empty)" })
        builder.appendLine()
        builder.appendLine("Events in this turn:")
        batch.forEach { event ->
            builder.appendLine("--- ${event.kind.wire} / ${event.source.wire} / thread ${event.threadKey.ifBlank { "-" }}")
            builder.appendLine(describe(event))
        }
        return builder.toString()
    }

    private fun describe(event: AgentEvent): String = when (event.kind) {
        AgentEventKind.UserMessage -> "User: ${event.text()}"
        AgentEventKind.SideChatResult -> "Side chat ${event.sideChatId()} (${event.payloadObject()["title"].stringValue()}) " +
            if (event.failed()) "failed: ${event.summary()}" else "finished: ${event.summary()}"
        AgentEventKind.SideChatApproval -> "Side chat ${event.sideChatId()} " +
            "(${event.payloadObject()["title"].stringValue()}) waits for approval: ${event.note()}"
        AgentEventKind.BackgroundCommand -> "Background command finished: ${event.text()}\n${event.payloadObject()["logs"].stringValue().take(1_200)}"
        AgentEventKind.Reminder -> "Reminder: ${event.text()}"
        AgentEventKind.DeviceTrigger -> "Device trigger ${event.payloadObject()["label"].stringValue()}: ${event.text()}"
        AgentEventKind.FeedUpdate -> "Feed update: ${event.text()}"
        AgentEventKind.WatchdogAlarm -> "Watchdog: ${event.text()}"
    }

    private suspend fun language(): String {
        val tag = workspace.userLanguage() ?: Locale.getDefault().language
        return when {
            tag.startsWith("zh") -> "Reply in Simplified Chinese."
            tag.startsWith("en") -> "Reply in English."
            else -> "Reply in the user's language."
        }
    }

    private fun time(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

    private companion object {
        const val MAX_BATCH = 8
        const val MAX_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 20_000L
        const val RETRY_SCAN_MS = 15_000L
    }
}

private class TurnOutcome(
    var answer: String = "",
    var error: String? = null,
)
