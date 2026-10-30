package fan.x0.focus.feature.assistant

import androidx.lifecycle.viewModelScope
import fan.x0.focus.core.mvi.BaseViewModel
import fan.x0.focus.core.mvi.UiAction
import fan.x0.focus.core.mvi.UiEffect
import fan.x0.focus.core.mvi.UiState
import fan.x0.focus.data.assistant.ChatDetailConfig
import fan.x0.focus.data.assistant.ChatDetailSettings
import fan.x0.focus.data.assistant.ChatStatField
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

data class ChatDetailUiState(
    val loading: Boolean = true,
    val config: ChatDetailConfig = ChatDetailConfig(),
) : UiState

sealed interface ChatDetailAction : UiAction {
    data class Toggle(val field: ChatStatField, val enabled: Boolean) : ChatDetailAction
    data class Move(val field: ChatStatField, val delta: Int) : ChatDetailAction
    data object Reset : ChatDetailAction
}

sealed interface ChatDetailEffect : UiEffect {
    data object Saved : ChatDetailEffect
}

@KoinViewModel
class ChatDetailSettingsViewModel(
    private val settings: ChatDetailSettings,
) : BaseViewModel<ChatDetailAction, ChatDetailUiState, ChatDetailEffect>(ChatDetailUiState()) {

    init {
        viewModelScope.launch {
            val config = settings.config.first().normalized()
            updateState { copy(loading = false, config = config) }
        }
    }

    override fun onAction(action: ChatDetailAction) {
        when (action) {
            is ChatDetailAction.Toggle -> {
                viewModelScope.launch {
                    settings.setEnabled(action.field, action.enabled)
                    val config = settings.configOnce()
                    updateState { copy(config = config) }
                    sendEffect(ChatDetailEffect.Saved)
                }
            }
            is ChatDetailAction.Move -> {
                viewModelScope.launch {
                    settings.move(action.field, action.delta)
                    val config = settings.configOnce()
                    updateState { copy(config = config) }
                    sendEffect(ChatDetailEffect.Saved)
                }
            }
            ChatDetailAction.Reset -> {
                viewModelScope.launch {
                    settings.reset()
                    val config = settings.configOnce()
                    updateState { copy(config = config) }
                    sendEffect(ChatDetailEffect.Saved)
                }
            }
        }
    }
}
