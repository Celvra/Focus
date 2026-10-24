package fan.x0.focus.feature.today

import fan.x0.focus.core.mvi.UiAction
import fan.x0.focus.core.mvi.UiEffect
import fan.x0.focus.core.mvi.UiState

enum class TodaySection {
    Updates,
    Ideas,
    Goals,
}

data class UpdateUi(
    val id: String,
    val headline: String,
    val body: String,
    val reason: String,
    val category: String,
    val icon: String,
    val feedback: Int,
    val time: String,
)

data class IdeaStepUi(
    val title: String,
    val detail: String,
)

data class IdeaUi(
    val id: String,
    val title: String,
    val detail: String,
    val steps: List<IdeaStepUi>,
    val category: String,
    val icon: String,
)

data class GoalUi(
    val id: String,
    val title: String,
    val detail: String?,
    val category: String,
    val icon: String,
    val done: Boolean,
)

data class GoalDraft(
    val id: String? = null,
    val title: String = "",
    val detail: String = "",
    val category: String = "",
    val icon: String = "",
)

data class CategoryOptionUi(
    val name: String,
    val icon: String,
)

data class TodayUiState(
    val loading: Boolean = true,
    val section: TodaySection = TodaySection.Updates,
    val updates: List<UpdateUi> = emptyList(),
    val ideas: List<IdeaUi> = emptyList(),
    val goals: List<GoalUi> = emptyList(),
    val reason: UpdateUi? = null,
    val openIdea: IdeaUi? = null,
    val ideaMenu: IdeaUi? = null,
    val goalDraft: GoalDraft? = null,
    val goalMenu: String? = null,
    val deleteGoal: GoalUi? = null,
    val saving: Boolean = false,
) : UiState {
    val categories: List<CategoryOptionUi>
        get() {
            val seen = LinkedHashMap<String, CategoryOptionUi>()
            goals.forEach { goal ->
                if (goal.category.isNotBlank() && !seen.containsKey(goal.category)) {
                    seen[goal.category] = CategoryOptionUi(goal.category, goal.icon)
                }
            }
            ideas.forEach { idea ->
                if (idea.category.isNotBlank() && !seen.containsKey(idea.category)) {
                    seen[idea.category] = CategoryOptionUi(idea.category, idea.icon)
                }
            }
            return seen.values.toList()
        }
}

sealed interface TodayAction : UiAction {
    data class SectionChanged(val section: TodaySection) : TodayAction
    data object Refresh : TodayAction
    data class Feedback(val id: String, val value: Int) : TodayAction
    data class OpenReason(val id: String) : TodayAction
    data object CloseReason : TodayAction
    data class Discuss(val id: String) : TodayAction
    data object AskForUpdates : TodayAction
    data object AskForIdeas : TodayAction
    data class OpenIdea(val id: String) : TodayAction
    data object CloseIdea : TodayAction
    data class IdeaMenu(val id: String) : TodayAction
    data object CloseIdeaMenu : TodayAction
    data class DismissIdea(val id: String) : TodayAction
    data class MoreLikeIdea(val id: String) : TodayAction
    data class StartIdea(val id: String) : TodayAction
    data class GoalMenu(val id: String) : TodayAction
    data object CloseGoalMenu : TodayAction
    data object NewGoal : TodayAction
    data class NewGoalInCategory(val category: String, val icon: String) : TodayAction
    data class EditGoal(val id: String) : TodayAction
    data object CancelGoal : TodayAction
    data class GoalTitle(val value: String) : TodayAction
    data class GoalDetail(val value: String) : TodayAction
    data object SaveGoal : TodayAction
    data class ToggleGoal(val id: String) : TodayAction
    data class AskDeleteGoal(val id: String) : TodayAction
    data object CancelDeleteGoal : TodayAction
    data object ConfirmDeleteGoal : TodayAction
}

sealed interface TodayEffect : UiEffect {
    data object OpenChat : TodayEffect
}
