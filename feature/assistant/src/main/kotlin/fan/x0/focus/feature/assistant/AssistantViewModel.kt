package fan.x0.focus.feature.assistant

import androidx.lifecycle.viewModelScope
import fan.x0.focus.core.error.toAppError
import fan.x0.focus.core.mvi.BaseViewModel
import fan.x0.focus.data.assistant.MemoryStore
import fan.x0.focus.data.assistant.ProfileStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class AssistantViewModel(
    private val profile: ProfileStore,
    private val memories: MemoryStore,
) : BaseViewModel<AssistantAction, AssistantUiState, AssistantEffect>(AssistantUiState()) {

    init {
        viewModelScope.launch {
            profile.revision.collect { loadProfile() }
        }
        viewModelScope.launch {
            memories.revision.collect { loadMemories() }
        }
    }

    private suspend fun loadProfile() {
        try {
            val person = profile.read()
            updateState {
                copy(
                    loading = false,
                    name = person.name.orEmpty(),
                    tone = person.tone.orEmpty(),
                    focus = person.focus.orEmpty(),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            emitError(e.toAppError())
        }
    }

    private suspend fun loadMemories() {
        try {
            val saved = memories.list()
            updateState {
                copy(
                    loading = false,
                    memories = saved.map { MemoryUi(it.id, it.content) },
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            emitError(e.toAppError())
        }
    }

    override fun onAction(action: AssistantAction) {
        when (action) {
            is AssistantAction.AskDeleteMemory -> updateState {
                copy(deleteMemory = memories.firstOrNull { it.id == action.id })
            }
            AssistantAction.ConfirmDeleteMemory -> confirmDeleteMemory()
            AssistantAction.CancelDeleteMemory -> updateState { copy(deleteMemory = null) }
        }
    }

    private fun confirmDeleteMemory() {
        val target = currentState.deleteMemory ?: return
        updateState {
            copy(deleteMemory = null, memories = memories.filterNot { it.id == target.id })
        }
        viewModelScope.launch {
            try {
                memories.delete(target.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emitError(e.toAppError())
            }
        }
    }
}
