package fan.x0.focus.feature.onboarding

import androidx.lifecycle.viewModelScope
import fan.x0.focus.core.mvi.BaseViewModel
import fan.x0.focus.core.network.openai.ConnectionStage
import fan.x0.focus.core.network.openai.ConnectionTestException
import fan.x0.focus.core.network.openai.ModelConnectionTester
import fan.x0.focus.core.network.openai.ModelEndpoint
import fan.x0.focus.core.network.openai.OpenAiModelCatalog
import fan.x0.focus.data.assistant.AssistantSettings
import fan.x0.focus.data.assistant.ChatEngine
import fan.x0.focus.data.assistant.ChatEvent
import fan.x0.focus.data.assistant.ChatMode
import fan.x0.focus.data.assistant.ChatStore
import fan.x0.focus.core.error.toAppError
import fan.x0.focus.data.assistant.SecretStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class OnboardingViewModel(
    private val settings: AssistantSettings,
    private val secrets: SecretStore,
    private val tester: ModelConnectionTester,
    private val catalog: OpenAiModelCatalog,
    private val engine: ChatEngine,
    private val chats: ChatStore,
) : BaseViewModel<OnboardingAction, OnboardingUiState, OnboardingEffect>(OnboardingUiState()) {

    private var turnJob: Job? = null
    private var fetchJob: Job? = null
    private var fetchToken = 0
    private var activeProviderId = "default"
    private var nextId = 0L
    private var finishing = false

    init {
        viewModelScope.launch {
            chats.clear(ONBOARDING_CONVERSATION)
            val config = settings.configOnce()
            activeProviderId = config.activeProviderId
            val savedKey = secrets.apiKey(activeProviderId)
            updateState {
                copy(
                    loading = false,
                    baseUrl = config.baseUrl,
                    model = config.model,
                    apiKey = savedKey.orEmpty(),
                    apiKeySaved = !savedKey.isNullOrBlank(),
                )
            }
        }
    }

    override fun onAction(action: OnboardingAction) {
        when (action) {
            is OnboardingAction.BaseUrlChanged -> {
                updateState { copy(baseUrl = action.value, failure = null, models = emptyList()) }
                scheduleModelFetch()
            }
            is OnboardingAction.ModelChanged -> updateState { copy(model = action.value, failure = null) }
            is OnboardingAction.ApiKeyChanged -> {
                updateState { copy(apiKey = action.value, failure = null, models = emptyList()) }
                scheduleModelFetch()
            }
            OnboardingAction.Connect -> connect()
            is OnboardingAction.InputChanged -> updateState { copy(input = action.value) }
            OnboardingAction.Send -> send(currentState.input)
            OnboardingAction.ToggleApiKeyVisibility -> updateState {
                copy(apiKeyVisible = !apiKeyVisible)
            }
            OnboardingAction.Skip -> finishSetup(cancelTurn = true)
            OnboardingAction.FetchModels -> {
                val token = ++fetchToken
                viewModelScope.launch { fetchModels(token, notify = true) }
            }
            OnboardingAction.OpenModelPicker -> {
                if (currentState.models.isEmpty()) {
                    val token = ++fetchToken
                    viewModelScope.launch { fetchModels(token, openPicker = true, notify = true) }
                } else {
                    updateState { copy(modelPickerOpen = true, modelQuery = "") }
                }
            }
            OnboardingAction.CloseModelPicker -> updateState { copy(modelPickerOpen = false, modelQuery = "") }
            is OnboardingAction.ModelQueryChanged -> updateState { copy(modelQuery = action.value) }
            is OnboardingAction.SelectModel -> updateState {
                copy(model = action.id, modelPickerOpen = false, modelQuery = "", failure = null)
            }
        }
    }

    private fun scheduleModelFetch() {
        fetchJob?.cancel()
        val token = ++fetchToken
        val url = currentState.baseUrl.trim()
        val hasKey = currentState.apiKey.trim().isNotBlank() || currentState.apiKeySaved
        if (!hasKey || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            updateState { copy(fetchingModels = false) }
            return
        }
        fetchJob = viewModelScope.launch {
            delay(1200)
            fetchModels(token)
        }
    }

    private suspend fun fetchModels(token: Int, openPicker: Boolean = false, notify: Boolean = false) {
        if (token != fetchToken) return
        val key = currentState.apiKey.trim().ifBlank { secrets.apiKey(activeProviderId).orEmpty() }
        if (key.isBlank()) return
        val endpoint = ModelEndpoint(currentState.baseUrl.trim().trimEnd('/'), currentState.model.trim())
        if (!endpoint.baseUrl.startsWith("http://") && !endpoint.baseUrl.startsWith("https://")) return
        updateState { copy(fetchingModels = true) }
        try {
            val models = catalog.list(endpoint, key)
            if (token != fetchToken) return
            updateState {
                copy(
                    fetchingModels = false,
                    models = models,
                    model = if (model.isBlank() && models.size == 1) models.first() else model,
                    modelPickerOpen = if (openPicker) models.isNotEmpty() else modelPickerOpen,
                    modelQuery = if (openPicker) "" else modelQuery,
                    failure = if (failure?.kind == ConnectFailureKind.Models) null else failure,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (token != fetchToken) return
            val error = e.toAppError()
            updateState {
                copy(
                    fetchingModels = false,
                    failure = if (notify) {
                        ConnectFailure(
                            ConnectFailureKind.Models,
                            error.serverMessage ?: error.serverTitle,
                        )
                    } else {
                        failure
                    },
                )
            }
        }
    }

    private fun connect() {
        val state = currentState
        if (state.testing) return
        val baseUrl = state.baseUrl.trim().trimEnd('/')
        val typedKey = state.apiKey.trim()
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            updateState { copy(failure = ConnectFailure(ConnectFailureKind.Generic, "Missing connection details")) }
            return
        }
        if (typedKey.isBlank() && !state.apiKeySaved) {
            updateState { copy(failure = ConnectFailure(ConnectFailureKind.Generic, "Missing connection details")) }
            return
        }
        if (state.model.isBlank()) {
            if (state.models.isNotEmpty()) {
                updateState { copy(modelPickerOpen = true) }
            } else {
                val token = ++fetchToken
                viewModelScope.launch { fetchModels(token, openPicker = true, notify = true) }
            }
            return
        }
        val endpoint = ModelEndpoint(baseUrl, state.model.trim())
        updateState { copy(testing = true, failure = null) }
        viewModelScope.launch {
            try {
                val key = typedKey.ifBlank { secrets.apiKey(activeProviderId).orEmpty() }
                tester.test(endpoint, key)
                if (typedKey.isNotBlank()) secrets.setApiKey(activeProviderId, typedKey)
                settings.setModel(endpoint.baseUrl, endpoint.model)
                updateState { copy(testing = false, phase = OnboardingPhase.Chat) }
                send("")
            } catch (e: CancellationException) {
                throw e
            } catch (e: ConnectionTestException) {
                updateState {
                    copy(
                        testing = false,
                        failure = ConnectFailure(
                            kind = if (e.stage == ConnectionStage.Streaming) {
                                ConnectFailureKind.Streaming
                            } else {
                                ConnectFailureKind.Tools
                            },
                            detail = e.detail,
                        ),
                    )
                }
            } catch (e: Throwable) {
                updateState { copy(testing = false, failure = ConnectFailure(ConnectFailureKind.Generic, e.message)) }
            }
        }
    }

    private fun send(text: String) {
        if (currentState.sending) return
        if (text.isBlank() && currentState.messages.isNotEmpty()) return
        turnJob?.cancel()
        val userMessage = OnboardingMessage(nextId++, fromUser = true, text = text)
        updateState {
            copy(
                messages = if (text.isBlank()) messages else messages + userMessage,
                streamingText = "",
                sending = true,
                input = "",
                avatarSeed = avatarSeed + 1,
                failure = null,
            )
        }
        turnJob = viewModelScope.launch {
            try {
                engine.send(ONBOARDING_CONVERSATION, text, ChatMode.Onboarding).collect { event ->
                    when (event) {
                        is ChatEvent.Delta -> updateState { copy(streamingText = streamingText + event.text) }
                        is ChatEvent.Completed -> finishTurn()
                        is ChatEvent.Failed -> {
                            updateState {
                                copy(
                                    sending = false,
                                    failure = ConnectFailure(ConnectFailureKind.Generic, event.error.serverMessage),
                                )
                            }
                            finishIfExhausted()
                        }
                        else -> Unit
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState {
                    copy(sending = false, failure = ConnectFailure(ConnectFailureKind.Generic, e.message))
                }
                finishIfExhausted()
            }
        }
    }

    private fun finishTurn() {
        updateState {
            val answer = streamingText.trim()
            copy(
                messages = if (answer.isBlank()) {
                    messages
                } else {
                    messages + OnboardingMessage(nextId++, fromUser = false, text = answer)
                },
                streamingText = "",
                sending = false,
                avatarSeed = avatarSeed + 1,
            )
        }
        finishIfExhausted()
    }

    private fun finishIfExhausted() {
        val userTurns = currentState.messages.count { it.fromUser }
        if (userTurns >= MAX_USER_TURNS) finishSetup()
    }

    private fun finishSetup(cancelTurn: Boolean = false) {
        if (finishing) return
        finishing = true
        if (cancelTurn) turnJob?.cancel()
        viewModelScope.launch { settings.completeOnboarding() }
    }

    companion object {
        private const val ONBOARDING_CONVERSATION = "onboarding"
        private const val MAX_USER_TURNS = 5
    }
}
