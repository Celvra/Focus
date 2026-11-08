package fan.x0.focus.feature.assistant.settings

import androidx.lifecycle.viewModelScope
import fan.x0.focus.core.mvi.BaseViewModel
import fan.x0.focus.data.assistant.PromptSettings
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

data class PromptsUiState(
    val main: String = "",
    val side: String = "",
    val saving: Boolean = false,
) : fan.x0.focus.core.mvi.UiState

sealed interface PromptsAction : fan.x0.focus.core.mvi.UiAction {
    data class MainChanged(val value: String) : PromptsAction
    data class SideChanged(val value: String) : PromptsAction
    data object Save : PromptsAction
}

sealed interface PromptsEffect : fan.x0.focus.core.mvi.UiEffect {
    data object Saved : PromptsEffect
}

@KoinViewModel
class PromptsViewModel(private val settings: PromptSettings) :
    BaseViewModel<PromptsAction, PromptsUiState, PromptsEffect>(PromptsUiState()) {

    init {
        viewModelScope.launch {
            val config = settings.configOnce()
            updateState { copy(main = config.main, side = config.side) }
        }
    }

    override fun onAction(action: PromptsAction) {
        when (action) {
            is PromptsAction.MainChanged -> updateState {
                copy(main = action.value.take(PromptSettings.MAX_PROMPT_CHARS))
            }
            is PromptsAction.SideChanged -> updateState {
                copy(side = action.value.take(PromptSettings.MAX_PROMPT_CHARS))
            }
            PromptsAction.Save -> save()
        }
    }

    private fun save() {
        if (currentState.saving) return
        updateState { copy(saving = true) }
        launchGuarded(
            onError = { updateState { copy(saving = false) } },
        ) {
            settings.setMain(currentState.main.trim())
            settings.setSide(currentState.side.trim())
            updateState { copy(saving = false) }
            sendEffect(PromptsEffect.Saved)
        }
    }
}
