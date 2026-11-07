package fan.x0.focus.data.assistant

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single

@Single
class AgentEventDispatcher(
    private val bus: AgentEventBus,
    private val events: AgentEventStore,
    private val threads: ThreadRegistry,
    private val router: RouterSession,
    private val sideChats: SideChatManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            bus.events.collect { event -> dispatch(event) }
        }
    }

    suspend fun dispatch(event: AgentEvent) {
        if (event.kind == AgentEventKind.UserMessage) {
            router.submit(event)
            return
        }
        val route = event.route
        if (route is AgentEventRoute.Side) {
            sideChats.deliver(event)
            return
        }
        if (route == AgentEventRoute.Router) {
            router.submit(event)
            return
        }
        val subscribed = if (event.threadKey.isNotBlank()) {
            threads.subscribers(event.threadKey)
        } else {
            emptyList()
        }
        if (subscribed.isEmpty()) {
            router.submit(event)
            return
        }
        var delivered = false
        subscribed.forEach { subscription ->
            if (sideChats.deliver(event.copy(target = "side:${subscription.conversationId}"))) {
                delivered = true
            }
        }
        if (!delivered) router.submit(event)
    }

    suspend fun resume() {
        bus.resumePending()
        events.pending(target = AgentEventRoute.ROUTER_TARGET, limit = 50).forEach { router.submit(it) }
    }
}
