package fan.x0.focus.feature.today

import android.text.format.DateUtils
import androidx.lifecycle.viewModelScope
import fan.x0.focus.core.error.toAppError
import fan.x0.focus.core.mvi.BaseViewModel
import fan.x0.focus.data.assistant.ChatSwitcher
import fan.x0.focus.data.assistant.Goal
import fan.x0.focus.data.assistant.GoalStatus
import fan.x0.focus.data.assistant.GoalStore
import fan.x0.focus.data.assistant.Idea
import fan.x0.focus.data.assistant.LocaleController
import fan.x0.focus.data.assistant.TodayStore
import fan.x0.focus.data.assistant.Update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class TodayViewModel(
    private val today: TodayStore,
    private val goalStore: GoalStore,
    private val chat: ChatSwitcher,
    private val locale: LocaleController,
) : BaseViewModel<TodayAction, TodayUiState, TodayEffect>(TodayUiState()) {

    init {
        viewModelScope.launch { reload() }
    }

    override fun onAction(action: TodayAction) {
        when (action) {
            is TodayAction.SectionChanged -> updateState { copy(section = action.section) }
            TodayAction.Refresh -> viewModelScope.launch { reload() }
            is TodayAction.Feedback -> setFeedback(action.id, action.value)
            is TodayAction.OpenReason -> updateState {
                copy(reason = updates.firstOrNull { it.id == action.id })
            }
            TodayAction.CloseReason -> updateState { copy(reason = null) }
            is TodayAction.Discuss -> discuss(action.id)
            TodayAction.AskForUpdates -> askForUpdates()
            TodayAction.AskForIdeas -> askForIdeas()
            is TodayAction.OpenIdea -> updateState {
                copy(openIdea = ideas.firstOrNull { it.id == action.id })
            }
            TodayAction.CloseIdea -> updateState { copy(openIdea = null) }
            is TodayAction.IdeaMenu -> updateState {
                copy(ideaMenu = ideas.firstOrNull { it.id == action.id })
            }
            TodayAction.CloseIdeaMenu -> updateState { copy(ideaMenu = null) }
            is TodayAction.DismissIdea -> dismissIdea(action.id)
            is TodayAction.MoreLikeIdea -> moreLikeIdea(action.id)
            is TodayAction.StartIdea -> startIdea(action.id)
            is TodayAction.GoalMenu -> updateState { copy(goalMenu = action.id) }
            TodayAction.CloseGoalMenu -> updateState { copy(goalMenu = null) }
            TodayAction.NewGoal -> updateState { copy(goalDraft = GoalDraft()) }
            is TodayAction.NewGoalInCategory -> updateState {
                copy(goalDraft = GoalDraft(category = action.category, icon = action.icon))
            }
            is TodayAction.EditGoal -> editGoal(action.id)
            TodayAction.CancelGoal -> updateState { copy(goalDraft = null) }
            is TodayAction.GoalTitle -> updateState {
                copy(goalDraft = goalDraft?.copy(title = action.value))
            }
            is TodayAction.GoalDetail -> updateState {
                copy(goalDraft = goalDraft?.copy(detail = action.value))
            }
            TodayAction.SaveGoal -> saveGoal()
            is TodayAction.ToggleGoal -> toggleGoal(action.id)
            is TodayAction.AskDeleteGoal -> updateState {
                copy(deleteGoal = goals.firstOrNull { it.id == action.id })
            }
            TodayAction.CancelDeleteGoal -> updateState { copy(deleteGoal = null) }
            TodayAction.ConfirmDeleteGoal -> deleteGoal()
        }
    }

    private fun setFeedback(id: String, value: Int) {
        val current = currentState.updates.firstOrNull { it.id == id } ?: return
        val next = if (current.feedback == value) 0 else value
        updateState {
            copy(updates = updates.map { if (it.id == id) it.copy(feedback = next) else it })
        }
        viewModelScope.launch {
            try {
                today.setFeedback(id, next)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emitError(e.toAppError())
                reload()
            }
        }
    }

    private fun discuss(id: String) {
        val update = currentState.updates.firstOrNull { it.id == id } ?: return
        ask(
            zh = "我想聊聊这条动态：${update.headline}。${update.body}",
            en = "I want to talk about this card: ${update.headline}. ${update.body}",
        )
    }

    private fun askForUpdates() {
        ask(
            zh = "根据我的订阅和日程，帮我整理今天的动态，一条一条发出来。",
            en = "Build today's feed from my feeds and calendar, one card at a time.",
        )
    }

    private fun askForIdeas() {
        ask(
            zh = "给我几个今天就能动手的点子，每个说清楚包含什么。",
            en = "Give me a few ideas I can start on today, with what each one includes.",
        )
    }

    private fun moreLikeIdea(id: String) {
        val idea = currentState.ideas.firstOrNull { it.id == id } ?: return
        updateState { copy(ideaMenu = null) }
        ask(
            zh = "再多给我几个和「${idea.title}」相似的点子。",
            en = "Give me a few more ideas similar to \"${idea.title}\".",
        )
    }

    private fun startIdea(id: String) {
        val idea = currentState.ideas.firstOrNull { it.id == id } ?: return
        updateState { copy(openIdea = null) }
        ask(
            zh = "就按这个点子开始：${idea.title}。${idea.detail} 先告诉我第一步做什么。",
            en = "Let's start on this idea: ${idea.title}. ${idea.detail} Tell me the first step.",
        )
    }

    private fun dismissIdea(id: String) {
        updateState {
            copy(
                ideas = ideas.filterNot { it.id == id },
                ideaMenu = null,
                openIdea = null,
            )
        }
        viewModelScope.launch {
            try {
                today.dismissIdea(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emitError(e.toAppError())
                reload()
            }
        }
    }

    private fun editGoal(id: String) {
        val goal = currentState.goals.firstOrNull { it.id == id } ?: return
        updateState {
            copy(
                goalMenu = null,
                goalDraft = GoalDraft(
                    id = goal.id,
                    title = goal.title,
                    detail = goal.detail.orEmpty(),
                    category = goal.category,
                    icon = goal.icon,
                ),
            )
        }
    }

    private fun saveGoal() {
        val draft = currentState.goalDraft ?: return
        val title = draft.title.trim()
        if (title.isBlank() || currentState.saving) return
        updateState { copy(saving = true) }
        viewModelScope.launch {
            try {
                if (draft.id == null) {
                    goalStore.add(
                        title = title,
                        detail = draft.detail.trim().ifBlank { null },
                        dueAt = null,
                        category = draft.category,
                        icon = draft.icon,
                    )
                } else {
                    goalStore.update(id = draft.id, title = title, detail = draft.detail.trim())
                }
                updateState { copy(saving = false, goalDraft = null) }
                reload()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState { copy(saving = false) }
                emitError(e.toAppError())
            }
        }
    }

    private fun toggleGoal(id: String) {
        val goal = currentState.goals.firstOrNull { it.id == id } ?: return
        val status = if (goal.done) GoalStatus.Active else GoalStatus.Done
        updateState {
            copy(goals = goals.map { if (it.id == id) it.copy(done = !it.done) else it })
        }
        viewModelScope.launch {
            try {
                goalStore.update(id = id, status = status, progress = if (status == GoalStatus.Done) 100 else 0)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emitError(e.toAppError())
                reload()
            }
        }
    }

    private fun deleteGoal() {
        val target = currentState.deleteGoal ?: return
        updateState {
            copy(deleteGoal = null, goals = goals.filterNot { it.id == target.id })
        }
        viewModelScope.launch {
            try {
                goalStore.delete(target.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emitError(e.toAppError())
                reload()
            }
        }
    }

    private fun ask(zh: String, en: String) {
        viewModelScope.launch {
            val tag = locale.current() ?: java.util.Locale.getDefault().language
            chat.ask(if (tag.startsWith("zh")) zh else en)
            sendEffect(TodayEffect.OpenChat)
        }
    }

    private suspend fun reload() {
        val updates = today.updates()
        val ideas = today.ideas()
        val goalList = goalStore.list()
        updateState {
            copy(
                loading = false,
                updates = updates.map { it.toUi() },
                ideas = ideas.map { it.toUi() },
                goals = goalList.map { it.toUi() },
            )
        }
    }

    private fun Update.toUi(): UpdateUi = UpdateUi(
        id = id,
        headline = headline,
        body = body,
        reason = reason,
        category = category,
        icon = icon,
        feedback = feedback,
        time = relative(createdAt),
    )

    private fun Idea.toUi(): IdeaUi = IdeaUi(
        id = id,
        title = title,
        detail = detail,
        steps = steps.map { IdeaStepUi(it.title, it.detail) },
        category = category,
        icon = icon,
    )

    private fun Goal.toUi(): GoalUi = GoalUi(
        id = id,
        title = title,
        detail = detail,
        category = category,
        icon = icon,
        done = status == GoalStatus.Done,
    )

    private fun relative(millis: Long): String = DateUtils.getRelativeTimeSpanString(
        millis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
}
