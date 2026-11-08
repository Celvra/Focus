package fan.x0.focus.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fan.x0.focus.data.assistant.ChatStore
import fan.x0.focus.data.assistant.ChatSwitcher
import fan.x0.focus.data.assistant.Conversation
import fan.x0.focus.data.assistant.SideChatManager
import fan.x0.focus.data.assistant.WorkspaceStore
import fan.x0.focus.data.assistant.sideChatRank
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

data class ShellState(
    val loading: Boolean = true,
    val name: String? = null,
    val main: Conversation? = null,
    val conversations: List<Conversation> = emptyList(),
    val sides: List<Conversation> = emptyList(),
    val archived: List<Conversation> = emptyList(),
) {
    val recent: List<Conversation> get() = sides.take(6)
}

@KoinViewModel
class ShellViewModel(
    private val chats: ChatStore,
    private val workspace: WorkspaceStore,
    private val switcher: ChatSwitcher,
    private val sideChats: SideChatManager,
) : ViewModel() {

    private val _state = MutableStateFlow(ShellState())
    val state: StateFlow<ShellState> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            chats.changes.collect { load() }
        }
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
            if (switcher.current.value == id) switcher.open(chats.ensureMainConversation().id)
            load()
        }
    }

    fun promoteConversation(id: String) {
        viewModelScope.launch {
            if (chats.promoteToMain(id)) {
                switcher.open(id)
                load()
            }
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

    fun newSideChat() {
        viewModelScope.launch {
            val conversation = sideChats.createEmpty()
            switcher.open(conversation.id)
            load()
        }
    }

    fun openMain() {
        viewModelScope.launch {
            switcher.open(chats.ensureMainConversation().id)
        }
    }

    private suspend fun load() {
        val name = workspace.userName()
        val main = chats.ensureMainConversation()
        val conversations = chats.conversations()
            .filter { it.id != ONBOARDING_CHAT && !it.archived }
        val sides = chats.sideChats()
            .filter { it.id != ONBOARDING_CHAT && !it.archived && it.title.isNotBlank() }
            .sortedWith(compareBy({ it.taskStatus.sideChatRank() }, { -it.updatedAt }))
        val archived = chats.conversations(archived = true)
            .filter { it.id != ONBOARDING_CHAT && it.archived }
        _state.value = ShellState(
            loading = false,
            name = name,
            main = main,
            conversations = conversations,
            sides = sides,
            archived = archived,
        )
    }

    companion object {
        private const val ONBOARDING_CHAT = "onboarding"
    }
}
