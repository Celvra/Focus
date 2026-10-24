package fan.x0.focus.feature.goals

import fan.x0.focus.core.mvi.UiAction
import fan.x0.focus.core.mvi.UiEffect
import fan.x0.focus.core.mvi.UiState
import fan.x0.focus.data.assistant.GoalStatus

enum class GoalFilter { Active, Done, All }

data class GoalUi(
    val id: String,
    val title: String,
    val detail: String?,
    val status: GoalStatus,
    val progress: Int,
    val dueLabel: String?,
    val note: String?,
)

data class GoalDraft(
    val id: String? = null,
    val title: String = "",
    val detail: String = "",
    val dueAt: Long? = null,
)

data class GoalsUiState(
    val loading: Boolean = true,
    val goals: List<GoalUi> = emptyList(),
    val filter: GoalFilter = GoalFilter.Active,
    val draft: GoalDraft? = null,
    val saving: Boolean = false,
    val deleteTarget: GoalUi? = null,
) : UiState {
    val visible: List<GoalUi>
        get() = goals.filter {
            when (filter) {
                GoalFilter.Active -> it.status == GoalStatus.Active
                GoalFilter.Done -> it.status == GoalStatus.Done
                GoalFilter.All -> true
            }
        }

    val activeCount: Int get() = goals.count { it.status == GoalStatus.Active }
    val doneCount: Int get() = goals.count { it.status == GoalStatus.Done }
}

sealed interface GoalsAction : UiAction {
    data class FilterChanged(val filter: GoalFilter) : GoalsAction
    data object Add : GoalsAction
    data class Edit(val id: String) : GoalsAction
    data object DismissEditor : GoalsAction
    data class TitleChanged(val value: String) : GoalsAction
    data class DetailChanged(val value: String) : GoalsAction
    data object Save : GoalsAction
    data class ProgressChanged(val id: String, val progress: Int) : GoalsAction
    data class ToggleDone(val id: String) : GoalsAction
    data class Delete(val id: String) : GoalsAction
    data object ConfirmDelete : GoalsAction
    data object CancelDelete : GoalsAction
}

sealed interface GoalsEffect : UiEffect
