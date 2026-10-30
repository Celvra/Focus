package fan.x0.focus.feature.assistant

import androidx.lifecycle.viewModelScope
import fan.x0.focus.core.error.AppError
import fan.x0.focus.core.error.toAppError
import fan.x0.focus.core.mvi.BaseViewModel
import fan.x0.focus.core.mvi.UiAction
import fan.x0.focus.core.mvi.UiEffect
import fan.x0.focus.core.mvi.UiState
import fan.x0.focus.data.assistant.SecretStore
import fan.x0.focus.data.assistant.WebSearchProvider
import fan.x0.focus.data.assistant.WebSearchRepository
import fan.x0.focus.data.assistant.WebSearchResult
import fan.x0.focus.data.assistant.WebSearchSecrets
import fan.x0.focus.data.assistant.WebSearchSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

data class WebSearchProviderStatus(
    val provider: WebSearchProvider,
    val enabled: Boolean,
    val configured: Boolean,
)

data class WebSearchUiState(
    val loading: Boolean = true,
    val defaultProvider: WebSearchProvider = WebSearchProvider.DeepSeek,
    val providers: List<WebSearchProviderStatus> = emptyList(),
    val editing: WebSearchProvider? = null,
    val keyInput: String = "",
    val keySaved: Boolean = false,
    val testing: Boolean = false,
    val results: List<WebSearchResult> = emptyList(),
    val error: AppError? = null,
) : UiState

sealed interface WebSearchAction : UiAction {
    data class SetDefault(val provider: WebSearchProvider) : WebSearchAction
    data class SetEnabled(val provider: WebSearchProvider, val enabled: Boolean) : WebSearchAction
    data class Open(val provider: WebSearchProvider) : WebSearchAction
    data object Close : WebSearchAction
    data class KeyChanged(val value: String) : WebSearchAction
    data object Save : WebSearchAction
    data object Test : WebSearchAction
}

sealed interface WebSearchEffect : UiEffect {
    data object Saved : WebSearchEffect
}

@KoinViewModel
class WebSearchSettingsViewModel(
    private val settings: WebSearchSettings,
    private val secrets: SecretStore,
    private val repository: WebSearchRepository,
) : BaseViewModel<WebSearchAction, WebSearchUiState, WebSearchEffect>(WebSearchUiState()) {

    init {
        viewModelScope.launch {
            val config = settings.config.first().normalized()
            val providerStatuses = statuses(config)
            updateState {
                copy(
                    loading = false,
                    defaultProvider = config.defaultProvider,
                    providers = providerStatuses,
                )
            }
        }
    }

    override fun onAction(action: WebSearchAction) {
        when (action) {
            is WebSearchAction.SetDefault -> viewModelScope.launch {
                settings.setDefault(action.provider)
                updateState { copy(defaultProvider = action.provider) }
            }
            is WebSearchAction.SetEnabled -> viewModelScope.launch {
                settings.setEnabled(action.provider, action.enabled)
                val config = settings.configOnce().normalized()
                val providerStatuses = statuses(config)
                updateState {
                    copy(
                        defaultProvider = config.defaultProvider,
                        providers = providerStatuses,
                    )
                }
            }
            is WebSearchAction.Open -> viewModelScope.launch {
                val key = secrets.secret(WebSearchSecrets.key(action.provider)).orEmpty()
                updateState {
                    copy(
                        editing = action.provider,
                        keyInput = key,
                        keySaved = key.isNotBlank(),
                        results = emptyList(),
                        error = null,
                    )
                }
            }
            WebSearchAction.Close -> updateState {
                copy(editing = null, keyInput = "", keySaved = false, results = emptyList(), error = null)
            }
            is WebSearchAction.KeyChanged -> updateState {
                copy(keyInput = action.value, error = null)
            }
            WebSearchAction.Save -> save()
            WebSearchAction.Test -> test()
        }
    }

    private fun save() {
        val provider = currentState.editing ?: return
        val key = currentState.keyInput.trim()
        if (provider.requiresKey && key.isBlank()) {
            updateState { copy(error = AppError(fan.x0.focus.core.error.AppErrorType.Unauthorized)) }
            return
        }
        viewModelScope.launch {
            if (key.isBlank()) {
                secrets.clearSecret(WebSearchSecrets.key(provider))
            } else {
                secrets.setSecret(WebSearchSecrets.key(provider), key)
            }
            val config = settings.configOnce().normalized()
            val providerStatuses = statuses(config)
            updateState {
                copy(
                    keySaved = key.isNotBlank(),
                    providers = providerStatuses,
                    error = null,
                )
            }
            sendEffect(WebSearchEffect.Saved)
        }
    }

    private fun test() {
        val provider = currentState.editing ?: return
        if (currentState.testing) return
        val key = currentState.keyInput.trim()
        if (provider.requiresKey && key.isBlank()) {
            updateState { copy(error = AppError(fan.x0.focus.core.error.AppErrorType.Unauthorized)) }
            return
        }
        updateState { copy(testing = true, results = emptyList(), error = null) }
        viewModelScope.launch {
            try {
                if (key.isNotBlank()) {
                    secrets.setSecret(WebSearchSecrets.key(provider), key)
                }
                val results = repository.test(provider)
                val config = settings.configOnce().normalized()
                val providerStatuses = statuses(config)
                updateState {
                    copy(
                        testing = false,
                        results = results,
                        keySaved = key.isNotBlank() || keySaved,
                        providers = providerStatuses,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState { copy(testing = false, error = e.toAppError()) }
            }
        }
    }

    private suspend fun statuses(config: fan.x0.focus.data.assistant.WebSearchConfig): List<WebSearchProviderStatus> =
        WebSearchProvider.entries.map { provider ->
            val configured = if (provider.requiresKey) {
                !secrets.secret(WebSearchSecrets.key(provider)).isNullOrBlank()
            } else {
                true
            }
            WebSearchProviderStatus(
                provider = provider,
                enabled = config.enabled(provider),
                configured = configured,
            )
        }
}
