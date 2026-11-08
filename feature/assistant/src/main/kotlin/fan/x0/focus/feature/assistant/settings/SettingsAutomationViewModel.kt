package fan.x0.focus.feature.assistant.settings

import androidx.lifecycle.viewModelScope
import fan.x0.focus.core.mvi.BaseViewModel
import fan.x0.focus.data.assistant.AgentEventConfig
import fan.x0.focus.data.assistant.AgentEventSettings
import fan.x0.focus.data.assistant.AgentEventSource
import fan.x0.focus.data.assistant.AgentTrigger
import fan.x0.focus.data.assistant.AgentTriggerKind
import fan.x0.focus.data.assistant.AgentTriggerScheduler
import fan.x0.focus.data.assistant.AgentTriggerStore
import fan.x0.focus.data.assistant.AgentTriggerTarget
import fan.x0.focus.data.assistant.AgentWebhookServer
import fan.x0.focus.data.assistant.CronExpression
import fan.x0.focus.data.assistant.DataUsage
import fan.x0.focus.data.assistant.DataUsageStore
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import java.time.ZoneId

data class AgentSourceRowUi(
    val source: AgentEventSource,
    val enabled: Boolean,
)

data class TriggerDraftUi(
    val id: String? = null,
    val name: String = "",
    val kind: AgentTriggerKind = AgentTriggerKind.Cron,
    val prompt: String = "",
    val schedule: String = "0 9 * * *",
    val timezone: String = ZoneId.systemDefault().id,
    val target: AgentTriggerTarget = AgentTriggerTarget.Side,
)

data class AutomationUiState(
    val loading: Boolean = true,
    val sources: List<AgentSourceRowUi> = emptyList(),
    val maxConcurrent: Int = AgentEventConfig.DEFAULT_MAX_CONCURRENT,
    val watchdog: Boolean = true,
    val rateLimit: Int = AgentEventConfig.DEFAULT_RATE_LIMIT,
    val postToToday: Boolean = false,
    val usage: DataUsage = DataUsage(),
    val triggers: List<AgentTrigger> = emptyList(),
    val webhookBase: String = "",
    val triggerDraft: TriggerDraftUi? = null,
    val triggerSaving: Boolean = false,
    val triggerError: String? = null,
) : fan.x0.focus.core.mvi.UiState

sealed interface AutomationAction : fan.x0.focus.core.mvi.UiAction {
    data class ToggleSource(val source: AgentEventSource, val enabled: Boolean) : AutomationAction
    data class SetConcurrent(val value: Int) : AutomationAction
    data class ToggleWatchdog(val enabled: Boolean) : AutomationAction
    data class SetRateLimit(val value: Int) : AutomationAction
    data class ToggleToday(val enabled: Boolean) : AutomationAction
    data object NewTrigger : AutomationAction
    data class EditTrigger(val id: String) : AutomationAction
    data object DismissTrigger : AutomationAction
    data class TriggerDraftChanged(val draft: TriggerDraftUi) : AutomationAction
    data object SaveTrigger : AutomationAction
    data class DeleteTrigger(val id: String) : AutomationAction
    data class ToggleTrigger(val id: String, val enabled: Boolean) : AutomationAction
}

sealed interface AutomationEffect : fan.x0.focus.core.mvi.UiEffect {
    data object Saved : AutomationEffect
    data object TriggerDeleted : AutomationEffect
}

@KoinViewModel
class AutomationViewModel(
    private val settings: AgentEventSettings,
    private val dataUsage: DataUsageStore,
    private val triggerStore: AgentTriggerStore,
    private val triggerScheduler: AgentTriggerScheduler,
    private val webhook: AgentWebhookServer,
) : BaseViewModel<AutomationAction, AutomationUiState, AutomationEffect>(AutomationUiState()) {

    init {
        viewModelScope.launch {
            runCatching { dataUsage.snapshot() }.getOrNull()?.let { usage ->
                updateState { copy(usage = usage) }
            }
        }
        viewModelScope.launch {
            settings.config.collect { config ->
                updateState {
                    copy(
                        loading = false,
                        sources = AgentEventSource.entries.filter { it.configurable }.map { source ->
                            AgentSourceRowUi(
                                source = source,
                                enabled = config.enabled(source),
                            )
                        },
                        maxConcurrent = config.maxConcurrentSideChats,
                        watchdog = config.watchdogEnabled,
                        rateLimit = config.rateLimitPerMinute,
                        postToToday = config.postResultsToToday,
                    )
                }
            }
        }
        viewModelScope.launch {
            triggerStore.changes.collect { reloadTriggers() }
        }
        viewModelScope.launch {
            reloadTriggers()
            updateState { copy(webhookBase = webhook.baseUrl()) }
        }
    }

    override fun onAction(action: AutomationAction) {
        when (action) {
            is AutomationAction.ToggleSource -> persist {
                settings.setSourceEnabled(action.source, action.enabled)
            }
            is AutomationAction.SetConcurrent -> persist {
                settings.setMaxConcurrentSideChats(action.value)
            }
            is AutomationAction.ToggleWatchdog -> persist {
                settings.setWatchdogEnabled(action.enabled)
            }
            is AutomationAction.SetRateLimit -> persist {
                settings.setRateLimit(action.value)
            }
            is AutomationAction.ToggleToday -> persist {
                settings.setPostResultsToToday(action.enabled)
            }
            AutomationAction.NewTrigger -> updateState {
                copy(triggerDraft = TriggerDraftUi(), triggerError = null)
            }
            is AutomationAction.EditTrigger -> {
                val trigger = currentState.triggers.firstOrNull { it.id == action.id } ?: return
                updateState {
                    copy(
                        triggerDraft = TriggerDraftUi(
                            id = trigger.id,
                            name = trigger.name,
                            kind = trigger.kind,
                            prompt = trigger.prompt,
                            schedule = trigger.schedule,
                            timezone = trigger.timezone,
                            target = trigger.target,
                        ),
                        triggerError = null,
                    )
                }
            }
            AutomationAction.DismissTrigger -> updateState {
                copy(triggerDraft = null, triggerError = null, triggerSaving = false)
            }
            is AutomationAction.TriggerDraftChanged -> updateState {
                copy(triggerDraft = action.draft, triggerError = null)
            }
            AutomationAction.SaveTrigger -> saveTrigger()
            is AutomationAction.DeleteTrigger -> deleteTrigger(action.id)
            is AutomationAction.ToggleTrigger -> toggleTrigger(action.id, action.enabled)
        }
    }

    private fun saveTrigger() {
        val draft = currentState.triggerDraft ?: return
        if (currentState.triggerSaving) return
        val name = draft.name.trim()
        val prompt = draft.prompt.trim()
        val error = when {
            name.isBlank() -> "Name is required"
            prompt.isBlank() -> "Prompt is required"
            draft.kind == AgentTriggerKind.Cron && CronExpression.validate(draft.schedule) != null ->
                CronExpression.validate(draft.schedule)
            else -> null
        }
        if (error != null) {
            updateState { copy(triggerError = error) }
            return
        }
        updateState { copy(triggerSaving = true, triggerError = null) }
        launchGuarded(
            onError = { error ->
                updateState {
                    copy(
                        triggerSaving = false,
                        triggerError = error.serverMessage ?: error.serverTitle,
                    )
                }
            },
        ) {
            val trigger = if (draft.id == null) {
                triggerStore.create(
                    name = name,
                    kind = draft.kind,
                    prompt = prompt,
                    schedule = draft.schedule,
                    timezone = draft.timezone,
                    target = draft.target,
                )
            } else {
                triggerStore.update(
                    id = draft.id,
                    name = name,
                    prompt = prompt,
                    schedule = draft.schedule,
                    timezone = draft.timezone,
                    target = draft.target,
                )
            }
            if (trigger != null) {
                triggerScheduler.sync(trigger)
            }
            updateState { copy(triggerDraft = null, triggerSaving = false) }
            sendEffect(AutomationEffect.Saved)
        }
    }

    private fun deleteTrigger(id: String) {
        launchGuarded(onError = {}) {
            triggerStore.delete(id)
            triggerScheduler.cancel(id)
            updateState { copy(triggerDraft = null) }
            sendEffect(AutomationEffect.TriggerDeleted)
        }
    }

    private fun toggleTrigger(id: String, enabled: Boolean) {
        launchGuarded(onError = {}) {
            val trigger = triggerStore.setEnabled(id, enabled) ?: return@launchGuarded
            if (enabled) triggerScheduler.sync(trigger) else triggerScheduler.cancel(trigger.id)
        }
    }

    private suspend fun reloadTriggers() {
        val triggers = triggerStore.list()
        updateState { copy(triggers = triggers) }
    }

    private fun persist(block: suspend () -> Unit) {
        launchGuarded(onError = {}) {
            block()
            sendEffect(AutomationEffect.Saved)
        }
    }
}
