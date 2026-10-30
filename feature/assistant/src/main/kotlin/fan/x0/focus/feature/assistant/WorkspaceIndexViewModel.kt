package fan.x0.focus.feature.assistant

import androidx.lifecycle.viewModelScope
import fan.x0.focus.core.error.AppError
import fan.x0.focus.core.error.toAppError
import fan.x0.focus.core.mvi.BaseViewModel
import fan.x0.focus.core.mvi.UiAction
import fan.x0.focus.core.mvi.UiEffect
import fan.x0.focus.core.mvi.UiState
import fan.x0.focus.data.assistant.WorkspaceIndexStatus
import fan.x0.focus.data.assistant.WorkspaceIndexer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

data class WorkspaceIndexUiState(
    val loading: Boolean = true,
    val building: Boolean = false,
    val status: WorkspaceIndexStatus? = null,
    val message: String? = null,
    val error: AppError? = null,
) : UiState

sealed interface WorkspaceIndexAction : UiAction {
    data object Refresh : WorkspaceIndexAction
    data object Build : WorkspaceIndexAction
    data object Clear : WorkspaceIndexAction
}

sealed interface WorkspaceIndexEffect : UiEffect

@KoinViewModel
class WorkspaceIndexViewModel(
    private val indexer: WorkspaceIndexer,
) : BaseViewModel<WorkspaceIndexAction, WorkspaceIndexUiState, WorkspaceIndexEffect>(WorkspaceIndexUiState()) {

    init {
        refresh()
    }

    override fun onAction(action: WorkspaceIndexAction) {
        when (action) {
            WorkspaceIndexAction.Refresh -> refresh()
            WorkspaceIndexAction.Build -> build()
            WorkspaceIndexAction.Clear -> clear()
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            try {
                val status = indexer.status()
                updateState { copy(loading = false, status = status, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState { copy(loading = false, status = null, error = e.toAppError()) }
            }
        }
    }

    private fun build() {
        if (currentState.building) return
        viewModelScope.launch {
            updateState { copy(building = true, error = null, message = null) }
            try {
                val result = indexer.index()
                val status = indexer.status()
                updateState {
                    copy(
                        building = false,
                        status = status,
                        message = "${result.indexed} files indexed",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState { copy(building = false, error = e.toAppError()) }
            }
        }
    }

    private fun clear() {
        viewModelScope.launch {
            try {
                indexer.clear()
                updateState { copy(status = null, message = null) }
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState { copy(error = e.toAppError()) }
            }
        }
    }
}
