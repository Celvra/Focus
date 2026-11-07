package fan.x0.focus.data.assistant

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap

@Single
class AgentEventBus(
    private val store: AgentEventStore,
    private val settings: AgentEventSettings,
) {
    private val _events = MutableSharedFlow<AgentEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<AgentEvent> = _events.asSharedFlow()

    private val windows = ConcurrentHashMap<AgentEventSource, ArrayDeque<Long>>()

    suspend fun publish(event: AgentEvent): AgentEvent? {
        val config = settings.configOnce()
        if (!config.enabled(event.source)) {
            return store.insert(
                event.copy(status = AgentEventStatus.Filtered, error = "source off: ${event.source.wire}"),
            )
        }
        if (rateLimited(event.source, config.rateLimitPerMinute)) {
            return store.insert(
                event.copy(status = AgentEventStatus.Filtered, error = "rate limited: ${event.source.wire}"),
            )
        }
        val stored = store.insert(event) ?: return null
        if (stored.status == AgentEventStatus.Pending) _events.emit(stored)
        return stored
    }

    suspend fun resumePending() {
        store.recoverRunning()
        store.pending(limit = 500).forEach { event -> _events.emit(event) }
    }

    suspend fun cleanup() {
        store.cleanup()
    }

    private fun rateLimited(source: AgentEventSource, limit: Int): Boolean = synchronized(windows) {
        val now = System.currentTimeMillis()
        val window = windows.getOrPut(source) { ArrayDeque() }
        while (window.isNotEmpty() && now - window.first() > 60_000) window.removeFirst()
        if (window.size >= limit) return@synchronized true
        window.addLast(now)
        false
    }
}
