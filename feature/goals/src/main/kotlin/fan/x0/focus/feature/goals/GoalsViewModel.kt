package fan.x0.focus.feature.goals

import androidx.lifecycle.viewModelScope
import fan.x0.focus.core.error.toAppError
import fan.x0.focus.core.mvi.BaseViewModel
import fan.x0.focus.data.assistant.Goal
import fan.x0.focus.data.assistant.GoalStatus
import fan.x0.focus.data.assistant.GoalStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@KoinViewModel
class GoalsViewModel(
    private val store: GoalStore,
) : BaseViewModel<GoalsAction, GoalsUiState, GoalsEffect>(GoalsUiState()) {

    private val dateFormatter = DateTimeFormatter.ofPattern("MM-dd")
    private val zone: ZoneId = ZoneId.systemDefault()

    init {
        viewModelScope.launch { reload() }
    }

    override fun onAction(action: GoalsAction) {
        when (action) {
            is GoalsAction.FilterChanged -> updateState { copy(filter = action.filter) }
            GoalsAction.Add -> updateState { copy(draft = GoalDraft()) }
            is GoalsAction.Edit -> {
                val goal = currentState.goals.firstOrNull { it.id == action.id } ?: return
                updateState {
                    copy(draft = GoalDraft(id = goal.id, title = goal.title, detail = goal.detail.orEmpty()))
                }
            }
            GoalsAction.DismissEditor -> updateState { copy(draft = null) }
            is GoalsAction.TitleChanged -> updateState {
                copy(draft = draft?.copy(title = action.value))
            }
            is GoalsAction.DetailChanged -> updateState {
                copy(draft = draft?.copy(detail = action.value))
            }
            GoalsAction.Save -> save()
            is GoalsAction.ProgressChanged -> updateProgress(action.id, action.progress)
            is GoalsAction.ToggleDone -> toggleDone(action.id)
            is GoalsAction.Delete -> updateState {
                copy(deleteTarget = goals.firstOrNull { it.id == action.id })
            }
            GoalsAction.ConfirmDelete -> confirmDelete()
            GoalsAction.CancelDelete -> updateState { copy(deleteTarget = null) }
        }
    }

    private fun save() {
        val draft = currentState.draft ?: return
        val title = draft.title.trim()
        if (title.isBlank() || currentState.saving) return
        updateState { copy(saving = true) }
        viewModelScope.launch {
            try {
                if (draft.id == null) {
                    store.add(title = title, detail = draft.detail.trim().ifBlank { null }, dueAt = draft.dueAt)
                } else {
                    store.update(id = draft.id, title = title, detail = draft.detail.trim())
                }
                updateState { copy(saving = false, draft = null) }
                reload()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState { copy(saving = false) }
                emitError(e.toAppError())
            }
        }
    }

    private fun updateProgress(id: String, progress: Int) {
        val clamped = progress.coerceIn(0, 100)
        updateState {
            copy(goals = goals.map { if (it.id == id) it.copy(progress = clamped) else it })
        }
        viewModelScope.launch {
            try {
                store.update(id = id, progress = clamped)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emitError(e.toAppError())
            }
        }
    }

    private fun toggleDone(id: String) {
        val goal = currentState.goals.firstOrNull { it.id == id } ?: return
        val status = if (goal.status == GoalStatus.Done) GoalStatus.Active else GoalStatus.Done
        val progress = if (status == GoalStatus.Done) 100 else goal.progress
        updateState {
            copy(goals = goals.map { if (it.id == id) it.copy(status = status, progress = progress) else it })
        }
        viewModelScope.launch {
            try {
                store.update(id = id, status = status, progress = progress)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emitError(e.toAppError())
            }
        }
    }

    private fun confirmDelete() {
        val target = currentState.deleteTarget ?: return
        updateState {
            copy(deleteTarget = null, goals = goals.filterNot { it.id == target.id })
        }
        viewModelScope.launch {
            try {
                store.delete(target.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emitError(e.toAppError())
                reload()
            }
        }
    }

    private suspend fun reload() {
        val goals = store.list()
        updateState { copy(loading = false, goals = goals.map { it.toUi() }) }
    }

    private fun Goal.toUi(): GoalUi = GoalUi(
        id = id,
        title = title,
        detail = detail,
        status = status,
        progress = progress,
        dueLabel = dueAt?.let { dateFormatter.format(Instant.ofEpochMilli(it).atZone(zone)) },
        note = note,
    )
}
