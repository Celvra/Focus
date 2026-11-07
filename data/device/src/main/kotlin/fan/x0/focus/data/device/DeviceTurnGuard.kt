package fan.x0.focus.data.device

import fan.x0.focus.data.assistant.AssistantTurnGuard
import org.koin.core.annotation.Single

@Single
class DeviceTurnGuard(private val runtime: DeviceRuntime) : AssistantTurnGuard {
    override fun turnFinished(conversationId: String) {
        if (conversationId.isBlank()) return
        val session = runtime.session.value ?: return
        if (session.remote || session.conversationId != conversationId) return
        if (runtime.backgroundList().any { it.running }) return
        runtime.stopSession()
    }
}
