package fan.x0.focus.core.notify

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.Single

@Single
class AppPresence {
    private val _foreground = MutableStateFlow(false)
    val foreground: StateFlow<Boolean> = _foreground.asStateFlow()

    private val _visibleConversationId = MutableStateFlow<String?>(null)
    val visibleConversationId: StateFlow<String?> = _visibleConversationId.asStateFlow()

    fun setForeground(value: Boolean) {
        _foreground.value = value
    }

    fun setVisibleConversation(conversationId: String?) {
        _visibleConversationId.value = conversationId
    }

    fun shouldNotify(conversationId: String): Boolean =
        !_foreground.value || _visibleConversationId.value != conversationId

    fun shouldNotifyBackground(): Boolean = !_foreground.value
}
