package fan.x0.focus.data.assistant

import fan.x0.focus.data.bridge.BridgeBgJob
import fan.x0.focus.data.bridge.BridgeEndpoint
import fan.x0.focus.data.bridge.BridgeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap

data class BackgroundCompletion(
    val conversationId: String,
    val jobId: String,
    val summary: String,
    val logs: String,
)

data class BackgroundJobSnapshot(
    val done: Boolean,
    val summary: String,
    val logs: String = "",
)

@Single
class BackgroundCommandMonitor(
    private val chats: ChatStore,
    private val bridge: BridgeRepository,
    private val bus: AgentEventBus,
    private val async: AsyncToolRegistry,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()

    private val _completed = MutableSharedFlow<BackgroundCompletion>(extraBufferCapacity = 8)
    val completed: SharedFlow<BackgroundCompletion> = _completed.asSharedFlow()

    fun register(
        conversationId: String,
        jobId: String,
        poll: suspend () -> BackgroundJobSnapshot,
    ) {
        if (conversationId.isBlank() || jobs.containsKey(jobId)) return
        val job = scope.launch {
            var snapshot = BackgroundJobSnapshot(done = false, summary = jobId)
            while (!snapshot.done) {
                snapshot = try {
                    poll()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    BackgroundJobSnapshot(done = true, summary = "Background job failed: ${e.message}")
                }
                if (!snapshot.done) delay(POLL_MS)
            }
            val tail = snapshot.logs.takeLast(LOG_TAIL_CHARS)
            val content = if (tail.isBlank()) snapshot.summary else "${snapshot.summary}\n\nLogs:\n$tail"
            if (chats.conversation(conversationId)?.kind == ConversationKind.Chat) {
                chats.append(
                    conversationId = conversationId,
                    role = ChatRole.System,
                    content = content,
                    label = "bg:$jobId",
                )
            }
            _completed.tryEmit(
                BackgroundCompletion(
                    conversationId = conversationId,
                    jobId = jobId,
                    summary = snapshot.summary,
                    logs = tail,
                ),
            )
            bus.publish(AgentEvent.backgroundCommand(conversationId, jobId, snapshot.summary, tail))
            jobs.remove(jobId)
        }
        jobs[jobId] = job
        async.track(conversationId, jobId, "background", jobId, job)
    }

    fun register(conversationId: String, endpoint: BridgeEndpoint, jobId: String) {
        if (conversationId.isBlank() || jobs.containsKey(jobId)) return
        val job = scope.launch {
            var after = 0L
            var done = false
            var job: BridgeBgJob? = null
            val logs = StringBuilder()
            while (!done) {
                runCatching {
                    bridge.backgroundLogs(endpoint, jobId, after, follow = false) { frame ->
                        frame.lines.forEach { line ->
                            if (logs.length < MAX_LOG_CHARS) {
                                logs.append(line.data).append('\n')
                            }
                        }
                        after = frame.lines.lastOrNull()?.seq ?: after
                        done = frame.done
                        job = frame.job
                    }
                }
                if (!done) delay(POLL_MS)
            }
            val info = job
            val summary = buildString {
                append("Background command finished on ")
                append(endpoint.name)
                append(": ")
                append(info?.command ?: jobId)
                append(". Status: ")
                append(info?.status ?: "unknown")
                if (info != null) {
                    append(", exit code ")
                    append(info.exitCode)
                    if (info.timedOut) append(", timed out")
                }
                append('.')
            }
            val tail = logs.toString().takeLast(LOG_TAIL_CHARS)
            val content = if (tail.isBlank()) summary else "$summary\n\nLogs:\n$tail"
            if (chats.conversation(conversationId)?.kind == ConversationKind.Chat) {
                chats.append(
                    conversationId = conversationId,
                    role = ChatRole.System,
                    content = content,
                    label = "bg:$jobId",
                )
            }
            _completed.tryEmit(
                BackgroundCompletion(
                    conversationId = conversationId,
                    jobId = jobId,
                    summary = summary,
                    logs = tail,
                ),
            )
            bus.publish(AgentEvent.backgroundCommand(conversationId, jobId, summary, tail))
            jobs.remove(jobId)
        }
        jobs[jobId] = job
        async.track(conversationId, jobId, "bridge_background", jobId, job)
    }

    companion object {
        private const val POLL_MS = 1200L
        private const val MAX_LOG_CHARS = 20_000
        private const val LOG_TAIL_CHARS = 4_000
    }
}
