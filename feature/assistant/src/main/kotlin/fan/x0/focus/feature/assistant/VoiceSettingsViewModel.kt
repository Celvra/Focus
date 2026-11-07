package fan.x0.focus.feature.assistant

import androidx.lifecycle.viewModelScope
import fan.x0.focus.core.mvi.BaseViewModel
import fan.x0.focus.core.mvi.UiAction
import fan.x0.focus.core.mvi.UiEffect
import fan.x0.focus.core.mvi.UiState
import fan.x0.focus.data.assistant.AssistantSettings
import fan.x0.focus.data.voice.LocalModelState
import fan.x0.focus.data.voice.LocalVoiceModelRepository
import fan.x0.focus.data.voice.VoiceConfig
import fan.x0.focus.data.voice.VoiceEngine
import fan.x0.focus.data.voice.VoiceInputController
import fan.x0.focus.data.voice.VoiceSettingsStore
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

data class VoiceProviderOption(
    val id: String,
    val name: String,
)

data class VoiceSettingsUiState(
    val loading: Boolean = true,
    val config: VoiceConfig = VoiceConfig(),
    val providers: List<VoiceProviderOption> = emptyList(),
    val model: LocalModelState = LocalModelState.Missing,
) : UiState

sealed interface VoiceSettingsAction : UiAction {
    data class SetEngine(val engine: VoiceEngine) : VoiceSettingsAction
    data class SetCloudProvider(val providerId: String) : VoiceSettingsAction
    data class SetCloudModel(val model: String) : VoiceSettingsAction
    data object DownloadModel : VoiceSettingsAction
    data object DeleteModel : VoiceSettingsAction
    data object RefreshModel : VoiceSettingsAction
}

sealed interface VoiceSettingsEffect : UiEffect

@KoinViewModel
class VoiceSettingsViewModel(
    private val settings: VoiceSettingsStore,
    private val assistant: AssistantSettings,
    private val models: LocalVoiceModelRepository,
    private val voice: VoiceInputController,
) : BaseViewModel<VoiceSettingsAction, VoiceSettingsUiState, VoiceSettingsEffect>(
    VoiceSettingsUiState(),
) {
    init {
        viewModelScope.launch {
            settings.config.collect { config ->
                updateState { copy(config = config, loading = false) }
            }
        }
        viewModelScope.launch {
            assistant.config.collect { config ->
                updateState {
                    copy(
                        providers = config.providers.map { provider ->
                            VoiceProviderOption(
                                id = provider.id,
                                name = provider.name.ifBlank { provider.id },
                            )
                        },
                    )
                }
            }
        }
        viewModelScope.launch {
            models.state.collect { state ->
                updateState { copy(model = state) }
            }
        }
    }

    override fun onAction(action: VoiceSettingsAction) {
        when (action) {
            is VoiceSettingsAction.SetEngine -> viewModelScope.launch {
                settings.setEngine(action.engine)
            }
            is VoiceSettingsAction.SetCloudProvider -> viewModelScope.launch {
                settings.setCloudProvider(action.providerId)
            }
            is VoiceSettingsAction.SetCloudModel -> viewModelScope.launch {
                settings.setCloudModel(action.model)
            }
            VoiceSettingsAction.DownloadModel -> models.download()
            VoiceSettingsAction.DeleteModel -> {
                models.delete()
                voice.releaseLocalModel()
            }
            VoiceSettingsAction.RefreshModel -> models.refresh()
        }
    }
}
