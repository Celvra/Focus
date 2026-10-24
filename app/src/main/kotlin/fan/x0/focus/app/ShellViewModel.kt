package fan.x0.focus.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fan.x0.focus.data.assistant.ChatStore
import fan.x0.focus.data.assistant.ChatSwitcher
import fan.x0.focus.data.assistant.Conversation
import fan.x0.focus.data.assistant.ProfileStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

data class ShellState(
    val loading: Boolean = true,
    val name: String? = null,
    val conversations: List<Conversation> = emptyList(),
    val archived: List<Conversation> = emptyList(),
) {
    val recent: List<Conversation> get() = conversations.take(5)
}

@KoinViewModel
class ShellViewModel(
    private val chats: ChatStore,
    private val profile: ProfileStore,
    private val switcher: ChatSwitcher,
) : ViewModel() {

    private val _state = MutableStateFlow(ShellState())
    val state: StateFlow<ShellState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    fun openConversation(id: String) {
        switcher.open(id)
    }

    fun openArchivedConversation(id: String) {
        switcher.open(id)
        viewModelScope.launch {
            chats.restore(id)
            load()
        }
    }

    fun archiveConversation(id: String) {
        viewModelScope.launch {
            chats.archive(id)
            if (switcher.current.value == id) switcher.newChat()
            load()
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            chats.deleteConversation(id)
            load()
        }
    }

    fun newChat() {
        switcher.newChat()
    }

    private suspend fun load() {
        val person = profile.read()
        val conversations = chats.conversations().filter { it.id != ONBOARDING_CHAT }
        val archived = chats.conversations(archived = true).filter { it.id != ONBOARDING_CHAT }
        _state.value = ShellState(
            loading = false,
            name = person.name,
            conversations = conversations,
            archived = archived,
        )
    }

    companion object {
        private const val ONBOARDING_CHAT = "onboarding"
    }
}
