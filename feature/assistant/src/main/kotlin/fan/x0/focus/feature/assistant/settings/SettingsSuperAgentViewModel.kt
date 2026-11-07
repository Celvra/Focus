package fan.x0.focus.feature.assistant.settings

import androidx.lifecycle.viewModelScope
import fan.x0.focus.core.mvi.BaseViewModel
import fan.x0.focus.data.assistant.AgentEventConfig
import fan.x0.focus.data.assistant.AgentEventSettings
import fan.x0.focus.data.assistant.AgentEventSource
import fan.x0.focus.data.assistant.DataUsage
import fan.x0.focus.data.assistant.DataUsageStore
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

data class AgentSourceRowUi(
    val source: AgentEventSource,
    val enabled: Boolean,
    val speculative: Boolean,
)

data class SuperAgentUiState(
    val loading: Boolean = true,
    val sources: List<AgentSourceRowUi> = emptyList(),
    val maxConcurrent: Int = AgentEventConfig.DEFAULT_MAX_CONCURRENT,
    val watchdog: Boolean = true,
    val rateLimit: Int = AgentEventConfig.DEFAULT_RATE_LIMIT,
    val postToToday: Boolean = false,
    val usage: DataUsage = DataUsage(),
) : fan.x0.focus.core.mvi.UiState

sealed interface SuperAgentAction : fan.x0.focus.core.mvi.UiAction {
    data class ToggleSource(val source: AgentEventSource, val enabled: Boolean) : SuperAgentAction
    data class ToggleSpeculative(val source: AgentEventSource, val enabled: Boolean) : SuperAgentAction
    data class SetConcurrent(val value: Int) : SuperAgentAction
    data class ToggleWatchdog(val enabled: Boolean) : SuperAgentAction
    data class SetRateLimit(val value: Int) : SuperAgentAction
    data class ToggleToday(val enabled: Boolean) : SuperAgentAction
}

sealed interface SuperAgentEffect : fan.x0.focus.core.mvi.UiEffect {
    data object Saved : SuperAgentEffect
}

@KoinViewModel
class SuperAgentViewModel(
    private val settings: AgentEventSettings,
    private val dataUsage: DataUsageStore,
) : BaseViewModel<SuperAgentAction, SuperAgentUiState, SuperAgentEffect>(SuperAgentUiState()) {

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
                        sources = AgentEventSource.entries.map { source ->
                            AgentSourceRowUi(
                                source = source,
                                enabled = config.enabled(source),
                                speculative = config.speculative(source),
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
    }

    override fun onAction(action: SuperAgentAction) {
        when (action) {
            is SuperAgentAction.ToggleSource -> persist {
                settings.setSourceEnabled(action.source, action.enabled)
            }
            is SuperAgentAction.ToggleSpeculative -> persist {
                settings.setSpeculative(action.source, action.enabled)
            }
            is SuperAgentAction.SetConcurrent -> persist {
                settings.setMaxConcurrentSideChats(action.value)
            }
            is SuperAgentAction.ToggleWatchdog -> persist {
                settings.setWatchdogEnabled(action.enabled)
            }
            is SuperAgentAction.SetRateLimit -> persist {
                settings.setRateLimit(action.value)
            }
            is SuperAgentAction.ToggleToday -> persist {
                settings.setPostResultsToToday(action.enabled)
            }
        }
    }

    private fun persist(block: suspend () -> Unit) {
        launchGuarded(onError = {}) {
            block()
            sendEffect(SuperAgentEffect.Saved)
        }
    }
}
