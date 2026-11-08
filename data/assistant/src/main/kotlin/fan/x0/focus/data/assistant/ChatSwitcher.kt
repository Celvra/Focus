package fan.x0.focus.data.assistant

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.Single

sealed interface ChatCommand {
    data class Open(val conversationId: String) : ChatCommand
    data object New : ChatCommand
    data class Ask(val text: String) : ChatCommand
}

// Lets the drawer ask the single chat screen to switch conversations.
@Single
class ChatSwitcher(private val main: MainSessionStore) {
    private val _command = MutableStateFlow<ChatCommand?>(null)
    val command: StateFlow<ChatCommand?> = _command.asStateFlow()

    private val _current = MutableStateFlow<String?>(null)
    val current: StateFlow<String?> = _current.asStateFlow()

    private val _openRequest = MutableStateFlow<String?>(null)
    val openRequest: StateFlow<String?> = _openRequest.asStateFlow()

    private val _assistRequest = MutableStateFlow(false)
    val assistRequest: StateFlow<Boolean> = _assistRequest.asStateFlow()

    fun requestAssist() {
        _assistRequest.value = true
        open(main.id.value)
    }

    fun consumeAssistRequest() {
        _assistRequest.value = false
    }

    fun open(conversationId: String) {
        _current.value = conversationId
        _command.value = ChatCommand.Open(conversationId)
        _openRequest.value = conversationId
    }

    fun consumeOpenRequest() {
        _openRequest.value = null
    }

    fun newChat() {
        _command.value = ChatCommand.New
    }

    fun ask(text: String) {
        if (text.isBlank()) return
        _command.value = ChatCommand.Ask(text)
    }

    fun markLoaded(conversationId: String) {
        _current.value = conversationId
    }

    fun consume(command: ChatCommand) {
        _command.compareAndSet(command, null)
    }
}
