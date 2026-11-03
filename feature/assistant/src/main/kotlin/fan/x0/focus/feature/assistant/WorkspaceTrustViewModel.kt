package fan.x0.focus.feature.assistant

import androidx.lifecycle.viewModelScope
import fan.x0.focus.core.error.AppError
import fan.x0.focus.core.error.toAppError
import fan.x0.focus.core.mvi.BaseViewModel
import fan.x0.focus.core.mvi.UiAction
import fan.x0.focus.core.mvi.UiEffect
import fan.x0.focus.core.mvi.UiState
import fan.x0.focus.data.assistant.WorkspaceTrustEntry
import fan.x0.focus.data.assistant.WorkspaceTrustService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

data class WorkspaceTrustUiState(
    val loading: Boolean = true,
    val entries: List<WorkspaceTrustEntry> = emptyList(),
    val error: AppError? = null,
) : UiState

sealed interface WorkspaceTrustAction : UiAction {
    data object Refresh : WorkspaceTrustAction
    data class SetTrusted(val key: String, val trusted: Boolean) : WorkspaceTrustAction
}

sealed interface WorkspaceTrustEffect : UiEffect

@KoinViewModel
class WorkspaceTrustViewModel(
    private val service: WorkspaceTrustService,
) : BaseViewModel<WorkspaceTrustAction, WorkspaceTrustUiState, WorkspaceTrustEffect>(WorkspaceTrustUiState()) {

    init {
        refresh()
    }

    override fun onAction(action: WorkspaceTrustAction) {
        when (action) {
            WorkspaceTrustAction.Refresh -> refresh()
            is WorkspaceTrustAction.SetTrusted -> setTrusted(action.key, action.trusted)
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            try {
                val entries = service.entries()
                updateState { copy(loading = false, entries = entries, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState { copy(loading = false, error = e.toAppError()) }
            }
        }
    }

    private fun setTrusted(key: String, trusted: Boolean) {
        viewModelScope.launch {
            try {
                service.decide(key, trusted)
                val entries = service.entries()
                updateState { copy(entries = entries, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState { copy(error = e.toAppError()) }
            }
        }
    }
}
