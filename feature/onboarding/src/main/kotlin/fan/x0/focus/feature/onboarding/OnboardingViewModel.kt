package fan.x0.focus.feature.onboarding

import androidx.lifecycle.viewModelScope
import fan.x0.focus.core.designsystem.components.FocusStepStatus
import fan.x0.focus.core.designsystem.components.FocusTraceStep
import fan.x0.focus.core.error.AppError
import fan.x0.focus.core.error.AppErrorType
import fan.x0.focus.core.error.toAppError
import fan.x0.focus.core.mvi.BaseViewModel
import fan.x0.focus.core.network.openai.ChatProtocol
import fan.x0.focus.core.network.openai.ConnectionTestException
import fan.x0.focus.core.network.openai.ModelConnectionTester
import fan.x0.focus.core.network.openai.ModelEndpoint
import fan.x0.focus.core.network.openai.OpenAiModelCatalog
import fan.x0.focus.data.assistant.AssistantSettings
import fan.x0.focus.data.assistant.ChatEngine
import fan.x0.focus.data.assistant.ChatEvent
import fan.x0.focus.data.assistant.ChatMode
import fan.x0.focus.data.assistant.ChatStore
import fan.x0.focus.data.assistant.ProviderCatalog
import fan.x0.focus.data.assistant.ProviderConfig
import fan.x0.focus.data.assistant.SecretStore
import fan.x0.focus.data.assistant.WorkspaceStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import java.util.UUID

@KoinViewModel
class OnboardingViewModel(
    private val settings: AssistantSettings,
    private val secrets: SecretStore,
    private val tester: ModelConnectionTester,
    private val catalog: OpenAiModelCatalog,
    private val engine: ChatEngine,
    private val chats: ChatStore,
    private val workspace: WorkspaceStore,
) : BaseViewModel<OnboardingAction, OnboardingUiState, OnboardingEffect>(OnboardingUiState()) {

    private var turnJob: Job? = null
    private var fetchJob: Job? = null
    private var fetchToken = 0
    private var nextId = 0L
    private var finishing = false

    init {
        viewModelScope.launch {
            chats.clear(ONBOARDING_CONVERSATION)
            updateState { copy(loading = false, restored = false) }
            send("")
        }
        viewModelScope.launch {
            workspace.revision.collect {
                val name = workspace.assistantName()
                updateState { copy(assistantName = name) }
            }
        }
        viewModelScope.launch {
            settings.config.collect { config ->
                val provider = config.activeProvider
                val ready = provider != null &&
                    provider.model.isNotBlank() &&
                    !secrets.apiKey(provider.id).isNullOrBlank()
                updateState { copy(providerReady = ready) }
            }
        }
    }

    override fun onAction(action: OnboardingAction) {
        when (action) {
            is OnboardingAction.InputChanged -> updateState { copy(input = action.value) }
            OnboardingAction.Send -> send(currentState.input)
            OnboardingAction.Stop -> stop()
            OnboardingAction.Finish -> finishSetup()
            OnboardingAction.OpenProviderSheet -> openProviderSheet()
            OnboardingAction.CloseProviderSheet -> closeProviderSheet()
            is OnboardingAction.ApplyPreset -> applyPreset(action.id)
            is OnboardingAction.ProviderNameChanged -> updateState {
                copy(providerName = action.value, providerFailure = null)
            }
            is OnboardingAction.BaseUrlChanged -> {
                updateState {
                    copy(
                        baseUrl = action.value,
                        providerFailure = null,
                        connectionOk = false,
                        models = emptyList(),
                    )
                }
                scheduleModelFetch()
            }
            is OnboardingAction.ModelChanged -> updateState {
                copy(model = action.value, providerFailure = null, connectionOk = false)
            }
            is OnboardingAction.ProtocolChanged -> {
                updateState {
                    copy(
                        protocol = action.value,
                        providerFailure = null,
                        connectionOk = false,
                        models = emptyList(),
                    )
                }
                scheduleModelFetch()
            }
            is OnboardingAction.ApiKeyChanged -> {
                updateState {
                    copy(
                        apiKey = action.value,
                        providerFailure = null,
                        connectionOk = false,
                        models = emptyList(),
                    )
                }
                scheduleModelFetch()
            }
            OnboardingAction.ToggleApiKeyVisibility -> updateState {
                copy(apiKeyVisible = !apiKeyVisible)
            }
            OnboardingAction.TestProvider -> testProvider()
            OnboardingAction.SaveProvider -> saveProvider()
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
            OnboardingAction.CloseModelPicker -> updateState {
                copy(modelPickerOpen = false, modelQuery = "")
            }
            is OnboardingAction.ModelQueryChanged -> updateState { copy(modelQuery = action.value) }
            is OnboardingAction.SelectModel -> updateState {
                copy(
                    model = action.id,
                    modelPickerOpen = false,
                    modelQuery = "",
                    providerFailure = null,
                    connectionOk = false,
                )
            }
        }
    }

    private fun send(text: String) {
        val state = currentState
        val value = text.trim()
        if (state.loading || state.sending) return
        if (value.isBlank() && state.messages.isNotEmpty()) return
        turnJob?.cancel()
        val message = if (value.isBlank()) null else OnboardingMessage(nextId++, true, value)
        updateState {
            copy(
                messages = if (message == null) messages else messages + message,
                lastMessageId = message?.id ?: lastMessageId,
                streamingText = "",
                reasoning = "",
                steps = emptyList(),
                sending = true,
                input = "",
            )
        }
        turnJob = viewModelScope.launch {
            try {
                engine.send(ONBOARDING_CONVERSATION, value, ChatMode.Onboarding).collect { event ->
                    when (event) {
                        is ChatEvent.Delta -> updateState { copy(streamingText = streamingText + event.text) }
                        is ChatEvent.ReasoningDelta -> updateState { copy(reasoning = reasoning + event.text) }
                        is ChatEvent.ToolStarted -> updateState { copy(steps = steps.upsert(event.label, FocusStepStatus.Running, null)) }
                        is ChatEvent.ToolFinished -> updateState {
                            copy(
                                steps = steps.upsert(
                                    event.label,
                                    if (event.ok) FocusStepStatus.Done else FocusStepStatus.Failed,
                                    event.summary.take(120).ifBlank { null },
                                ),
                            )
                        }
                        is ChatEvent.Completed -> finishTurn()
                        is ChatEvent.Failed -> {
                            finishTurn()
                            emitError(event.error)
                        }
                        else -> Unit
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                finishTurn()
                emitError(e.toAppError())
            }
        }
    }

    private fun stop() {
        if (!currentState.sending) return
        turnJob?.cancel()
        turnJob = null
        finishTurn()
    }

    private fun finishTurn() {
        updateState {
            val answer = streamingText.trim()
            copy(
                messages = if (answer.isBlank() && reasoning.isBlank() && steps.isEmpty()) {
                    messages
                } else {
                    messages + OnboardingMessage(
                        id = nextId++,
                        fromUser = false,
                        text = answer,
                        reasoning = reasoning.trim(),
                        steps = steps,
                    )
                },
                lastMessageId = if (answer.isBlank() && reasoning.isBlank() && steps.isEmpty()) lastMessageId else nextId - 1,
                streamingText = "",
                reasoning = "",
                steps = emptyList(),
                sending = false,
            )
        }
    }

    private fun List<FocusTraceStep>.upsert(
        label: String,
        status: FocusStepStatus,
        summary: String?,
    ): List<FocusTraceStep> {
        val index = indexOfLast { it.label == label }
        val step = FocusTraceStep(label, status, summary)
        return when {
            index < 0 -> this + step
            else -> toMutableList().also { it[index] = step }
        }
    }

    private fun finishSetup() {
        if (finishing) return
        finishing = true
        turnJob?.cancel()
        turnJob = null
        viewModelScope.launch {
            chats.clear(ONBOARDING_CONVERSATION)
            settings.completeOnboarding()
        }
    }

    private fun openProviderSheet() {
        updateState {
            copy(providerSheetOpen = true, providerFailure = null, connectionOk = false)
        }
        if (currentState.baseUrl.isNotBlank() || currentState.model.isNotBlank()) return
        viewModelScope.launch {
            val config = settings.configOnce()
            val provider = config.activeProvider ?: return@launch
            if (provider.model.isBlank()) return@launch
            val key = secrets.apiKey(provider.id).orEmpty()
            updateState {
                if (baseUrl.isNotBlank() || model.isNotBlank()) {
                    this
                } else {
                    copy(
                        providerName = provider.name,
                        baseUrl = provider.baseUrl,
                        model = provider.model,
                        protocol = provider.protocol,
                        apiKey = key,
                    )
                }
            }
        }
    }

    private fun closeProviderSheet() {
        fetchJob?.cancel()
        ++fetchToken
        updateState {
            copy(
                providerSheetOpen = false,
                providerFailure = null,
                connectionOk = false,
                modelPickerOpen = false,
                modelQuery = "",
            )
        }
    }

    private fun applyPreset(id: String) {
        val preset = ProviderCatalog.presets.firstOrNull { it.id == id } ?: return
        updateState {
            copy(
                providerName = preset.name,
                baseUrl = preset.baseUrl,
                protocol = preset.protocol,
                providerFailure = null,
                connectionOk = false,
                models = emptyList(),
            )
        }
        scheduleModelFetch()
    }

    private fun testProvider() {
        val state = currentState
        if (state.testing || state.saving) return
        val baseUrl = validBaseUrl(state.baseUrl)
        if (baseUrl == null) {
            updateState { copy(providerFailure = ProviderFailure.Validation(ProviderFailure.Field.BaseUrl)) }
            return
        }
        val model = state.model.trim()
        if (model.isBlank()) {
            updateState { copy(providerFailure = ProviderFailure.Validation(ProviderFailure.Field.Model)) }
            return
        }
        val key = state.apiKey.trim()
        if (key.isBlank()) {
            updateState { copy(providerFailure = ProviderFailure.Validation(ProviderFailure.Field.ApiKey)) }
            return
        }
        updateState { copy(testing = true, providerFailure = null, connectionOk = false) }
        viewModelScope.launch {
            try {
                tester.test(ModelEndpoint(baseUrl, model), key, state.protocol)
                updateState { copy(testing = false, connectionOk = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ConnectionTestException) {
                updateState {
                    copy(
                        testing = false,
                        providerFailure = ProviderFailure.Network(
                            AppError(AppErrorType.Generic, serverMessage = e.detail),
                        ),
                    )
                }
            } catch (e: Throwable) {
                updateState {
                    copy(testing = false, providerFailure = ProviderFailure.Network(e.toAppError()))
                }
            }
        }
    }

    private fun saveProvider() {
        val state = currentState
        if (state.saving || state.testing) return
        val baseUrl = validBaseUrl(state.baseUrl)
        if (baseUrl == null) {
            updateState { copy(providerFailure = ProviderFailure.Validation(ProviderFailure.Field.BaseUrl)) }
            return
        }
        val model = state.model.trim()
        if (model.isBlank()) {
            updateState { copy(providerFailure = ProviderFailure.Validation(ProviderFailure.Field.Model)) }
            return
        }
        val key = state.apiKey.trim()
        if (key.isBlank()) {
            updateState { copy(providerFailure = ProviderFailure.Validation(ProviderFailure.Field.ApiKey)) }
            return
        }
        val name = state.providerName.trim().ifBlank { providerName(baseUrl) }
        updateState { copy(saving = true, providerFailure = null, connectionOk = false) }
        viewModelScope.launch {
            try {
                val config = settings.configOnce()
                val existing = config.providers.firstOrNull {
                    it.name.equals(name, ignoreCase = true) || it.baseUrl == baseUrl
                }
                val id = existing?.id ?: UUID.randomUUID().toString()
                settings.updateProvider(
                    ProviderConfig(
                        id = id,
                        name = name,
                        baseUrl = baseUrl,
                        model = model,
                        protocol = state.protocol,
                    ),
                    makeActive = true,
                )
                secrets.setApiKey(id, key)
                updateState {
                    copy(
                        saving = false,
                        providerReady = true,
                        providerSheetOpen = false,
                        providerFailure = null,
                        connectionOk = false,
                        apiKey = "",
                        models = emptyList(),
                        modelPickerOpen = false,
                        modelQuery = "",
                    )
                }
                sendEffect(OnboardingEffect.ProviderSaved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState {
                    copy(saving = false, providerFailure = ProviderFailure.Network(e.toAppError()))
                }
            }
        }
    }

    private fun scheduleModelFetch() {
        fetchJob?.cancel()
        val token = ++fetchToken
        val state = currentState
        if (validBaseUrl(state.baseUrl) == null || state.apiKey.trim().isBlank()) {
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
        val state = currentState
        val baseUrl = validBaseUrl(state.baseUrl) ?: return
        val key = state.apiKey.trim()
        if (key.isBlank()) return
        updateState { copy(fetchingModels = true) }
        try {
            val models = catalog.list(ModelEndpoint(baseUrl, state.model.trim()), key, state.protocol)
            if (token != fetchToken) return
            updateState {
                copy(
                    fetchingModels = false,
                    models = models,
                    model = if (model.isBlank() && models.size == 1) models.first() else model,
                    modelPickerOpen = if (openPicker) models.isNotEmpty() else modelPickerOpen,
                    modelQuery = if (openPicker) "" else modelQuery,
                    providerFailure = if (providerFailure is ProviderFailure.Network) null else providerFailure,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (token != fetchToken) return
            updateState {
                copy(
                    fetchingModels = false,
                    providerFailure = if (notify) {
                        ProviderFailure.Network(e.toAppError())
                    } else {
                        providerFailure
                    },
                )
            }
        }
    }

    private fun validBaseUrl(value: String): String? {
        val url = value.trim().trimEnd('/')
        return url.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    private fun providerName(baseUrl: String): String =
        runCatching { java.net.URI(baseUrl).host }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: baseUrl

    companion object {
        private const val ONBOARDING_CONVERSATION = "onboarding"
    }
}
