package fan.x0.focus.data.assistant

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.Single

data class CrewLaunchRequest(
    val groupId: String,
    val focusMemberIds: Set<String> = emptySet(),
)

@Single
class CrewLaunchBus {
    private val _requests = MutableSharedFlow<CrewLaunchRequest>(extraBufferCapacity = 8)
    val requests: SharedFlow<CrewLaunchRequest> = _requests.asSharedFlow()

    fun request(groupId: String, focusMemberIds: Set<String> = emptySet()) {
        if (groupId.isNotBlank()) _requests.tryEmit(CrewLaunchRequest(groupId, focusMemberIds))
    }
}

@Single
class CrewSwitcher {
    private val _openRequest = MutableStateFlow<String?>(null)
    val openRequest: StateFlow<String?> = _openRequest.asStateFlow()

    fun open(groupId: String) {
        _openRequest.value = groupId
    }

    fun consume() {
        _openRequest.value = null
    }
}
