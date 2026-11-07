package fan.x0.focus.data.assistant

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.koin.core.annotation.Single

data class AssistantTriggerEvent(
    val conversationId: String,
    val label: String,
    val prompt: String,
)

@Single
class AssistantTriggerBus {
    private val _events = MutableSharedFlow<AssistantTriggerEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<AssistantTriggerEvent> = _events.asSharedFlow()

    fun emit(event: AssistantTriggerEvent) {
        _events.tryEmit(event)
    }
}
