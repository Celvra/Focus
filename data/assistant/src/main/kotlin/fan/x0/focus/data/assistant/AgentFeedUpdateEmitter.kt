package fan.x0.focus.data.assistant

import fan.x0.focus.data.sources.FeedUpdateEmitter
import org.koin.core.annotation.Single

@Single(binds = [FeedUpdateEmitter::class])
class AgentFeedUpdateEmitter(private val bus: AgentEventBus) : FeedUpdateEmitter {
    override suspend fun feedUpdated(added: Int) {
        if (added <= 0) return
        bus.publish(
            AgentEvent.feedUpdate(
                feedId = "all",
                title = "订阅源",
                count = added,
            ),
        )
    }
}
