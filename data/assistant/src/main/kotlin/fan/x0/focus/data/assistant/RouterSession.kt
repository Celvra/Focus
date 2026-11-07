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

@Single
class RouterSession(
    private val engine: ChatEngine,
    private val chats: ChatStore,
    private val models: ModelResolver,
    private val events: AgentEventStore,
    private val tools: RouterToolHandler,
    private val main: MainChannelSession,
    private val journal: TurnJournalStore,
    private val assembler: ContextAssembler,
    private val keepAlive: ChatTurnKeepAlive,
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
        keepAlive.start()
        var journalStatus = "failed"
        try {
            val resolved = models.resolve(AgentRole.Router)
            if (resolved == null) {
                failAll(claimed, "没有可用的 Router 模型")
                return
            }
            val router = chats.ensureRouterConversation()
            val prompt = assembler.build(claimed)
            val flags = RouterTurnFlags()
            val eventText = claimed.joinToString("\n\n") { assembler.describe(it) }
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
                failAll(claimed, "Router 已停止", allowRetry = false)
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

    private suspend fun failAll(batch: List<AgentEvent>, error: String, allowRetry: Boolean = true) {
        batch.forEach { event ->
            val retry = allowRetry && event.attempts + 1 < MAX_ATTEMPTS
            events.fail(
                event.id,
                error,
                retryAt = if (retry) System.currentTimeMillis() + RETRY_DELAY_MS * (event.attempts + 1) else null,
            )
        }
        if (allowRetry && batch.any { it.attempts + 1 >= MAX_ATTEMPTS }) {
            main.appendNotice("Router 处理失败：${error.take(200)}")
        }
    }

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
