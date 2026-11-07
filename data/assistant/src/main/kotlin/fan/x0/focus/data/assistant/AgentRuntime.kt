package fan.x0.focus.data.assistant

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single

@Single
class AgentRuntime(
    private val chats: ChatStore,
    private val router: RouterSession,
    private val sideChats: SideChatManager,
    private val dispatcher: AgentEventDispatcher,
    private val bus: AgentEventBus,
    private val main: MainChannelSession,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun start() {
        main.ensureConversation()
        chats.ensureRouterConversation()
        router.prepare()
        sideChats.recover()
        dispatcher.resume()
        bus.cleanup()
        scope.launch {
            while (true) {
                delay(CLEANUP_INTERVAL_MS)
                bus.cleanup()
            }
        }
    }

    private companion object {
        const val CLEANUP_INTERVAL_MS = 6L * 60 * 60 * 1000
    }
}
