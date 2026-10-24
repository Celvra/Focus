package fan.x0.focus.feature.assistant

import androidx.lifecycle.viewModelScope
import fan.x0.focus.core.error.AppError
import fan.x0.focus.core.error.AppErrorType
import fan.x0.focus.core.error.toAppError
import fan.x0.focus.core.mvi.BaseViewModel
import fan.x0.focus.core.network.openai.ConnectionStage
import fan.x0.focus.core.network.openai.ConnectionTestException
import fan.x0.focus.core.network.openai.ModelConnectionTester
import fan.x0.focus.core.network.openai.ModelEndpoint
import fan.x0.focus.core.network.openai.OpenAiModelCatalog
import fan.x0.focus.core.network.openai.ProviderBalanceClient
import fan.x0.focus.data.assistant.AssistantSettings
import fan.x0.focus.data.assistant.LocaleController
import fan.x0.focus.data.assistant.ProviderConfig
import fan.x0.focus.data.assistant.SecretStore
import fan.x0.focus.data.sources.FeedRefreshScheduler
import fan.x0.focus.data.sources.SourcePreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import java.util.UUID

@KoinViewModel
class SettingsViewModel(
    private val settings: AssistantSettings,
    private val secrets: SecretStore,
    private val sources: SourcePreferences,
    private val scheduler: FeedRefreshScheduler,
    private val tester: ModelConnectionTester,
    private val catalog: OpenAiModelCatalog,
    private val balanceClient: ProviderBalanceClient,
    private val locale: LocaleController,
) : BaseViewModel<SettingsAction, SettingsUiState, SettingsEffect>(SettingsUiState()) {

    private var fetchJob: Job? = null
    private var fetchToken = 0
    private var balanceToken = 0

    init {
        viewModelScope.launch {
            val config = settings.configOnce()
            val source = sources.settings.first()
            updateState {
                copy(
                    loading = false,
                    providers = config.providers,
                    activeProviderId = config.activeProviderId,
                    refreshHours = source.refreshIntervalHours,
                    language = locale.current(),
                )
            }
            loadBalances(config.providers)
        }
    }

    private suspend fun loadBalances(providers: List<ProviderConfig>) {
        providers.filter { balanceClient.supports(it.id) }.forEach { provider ->
            val key = secrets.apiKey(provider.id)
            if (key.isNullOrBlank()) return@forEach
            try {
                val balance = balanceClient.fetch(provider.id, provider.baseUrl, key)
                updateState { copy(providerBalances = providerBalances + (provider.id to balance)) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                Unit
            }
        }
    }

    override fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.RefreshHoursChanged -> setRefreshHours(action.hours)
            is SettingsAction.LanguageChanged -> {
                locale.setLanguage(action.tag)
                updateState { copy(language = action.tag) }
            }
            SettingsAction.AskReset -> updateState { copy(resetConfirm = true) }
            SettingsAction.CancelReset -> updateState { copy(resetConfirm = false) }
            SettingsAction.ConfirmReset -> resetSetup()
            is SettingsAction.SelectProvider -> selectProvider(action.id)
            is SettingsAction.OpenProvider -> openProvider(action.id)
            SettingsAction.AddProvider -> addProvider()
            SettingsAction.CloseEditor -> closeEditor()
            is SettingsAction.NameChanged -> updateState { copy(draftName = action.value) }
            is SettingsAction.BaseUrlChanged -> {
                updateState {
                    copy(
                        baseUrl = action.value,
                        connectionOk = false,
                        connectionError = null,
                        models = emptyList(),
                    )
                }
                scheduleModelFetch()
            }
            is SettingsAction.ModelChanged -> updateState {
                copy(model = action.value, connectionOk = false, connectionError = null)
            }
            is SettingsAction.ApiKeyChanged -> {
                updateState {
                    copy(
                        apiKeyInput = action.value,
                        connectionOk = false,
                        connectionError = null,
                        models = emptyList(),
                    )
                }
                scheduleModelFetch()
            }
            SettingsAction.ToggleApiKeyVisibility -> updateState {
                copy(apiKeyVisible = !apiKeyVisible)
            }
            is SettingsAction.MakeActiveChanged -> updateState { copy(draftMakeActive = action.value) }
            SettingsAction.SaveProvider -> saveProvider()
            SettingsAction.TestProvider -> testProvider()
            SettingsAction.AskDeleteProvider -> updateState { copy(deleteConfirm = true) }
            SettingsAction.CancelDeleteProvider -> updateState { copy(deleteConfirm = false) }
            SettingsAction.DeleteProvider -> deleteProvider()
            SettingsAction.FetchModels -> {
                val token = ++fetchToken
                viewModelScope.launch { fetchModels(token, notify = true) }
            }
            SettingsAction.OpenModelPicker -> {
                if (currentState.models.isEmpty()) {
                    val token = ++fetchToken
                    viewModelScope.launch { fetchModels(token, openPicker = true, notify = true) }
                } else {
                    updateState { copy(modelPickerOpen = true, modelQuery = "") }
                }
            }
            SettingsAction.CloseModelPicker -> updateState { copy(modelPickerOpen = false, modelQuery = "") }
            is SettingsAction.ModelQueryChanged -> updateState { copy(modelQuery = action.value) }
            is SettingsAction.SelectModel -> updateState {
                copy(model = action.id, modelPickerOpen = false, modelQuery = "", connectionOk = false)
            }
            SettingsAction.FetchBalance -> fetchBalance()
        }
    }

    private fun fetchBalance() {
        val state = currentState
        val id = state.editingProviderId ?: return
        if (!state.balanceSupported) return
        val baseUrl = state.baseUrl.trim().trimEnd('/')
        viewModelScope.launch {
            val key = state.apiKeyInput.trim().ifBlank { secrets.apiKey(id).orEmpty() }
            if (key.isBlank()) {
                updateState {
                    copy(balanceError = AppError(AppErrorType.Unauthorized, serverMessage = "No API key"))
                }
                return@launch
            }
            refreshBalance(id, baseUrl, key, notify = true)
        }
    }

    private suspend fun refreshBalance(
        providerId: String,
        baseUrl: String,
        key: String,
        notify: Boolean,
    ) {
        if (key.isBlank()) return
        val token = ++balanceToken
        updateState { copy(fetchingBalance = true, balanceError = null) }
        try {
            val result = balanceClient.fetch(providerId, baseUrl, key)
            if (token != balanceToken) return
            updateState {
                copy(
                    fetchingBalance = false,
                    balance = result,
                    providerBalances = providerBalances + (providerId to result),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (token != balanceToken) return
            updateState {
                copy(
                    fetchingBalance = false,
                    balanceError = if (notify) e.toAppError() else null,
                )
            }
        }
    }

    private fun selectProvider(id: String) {
        viewModelScope.launch {
            settings.setActiveProvider(id)
            updateState { copy(activeProviderId = id) }
        }
    }

    private fun openProvider(id: String) {
        val provider = currentState.providers.firstOrNull { it.id == id } ?: return
        fetchJob?.cancel()
        ++fetchToken
        ++balanceToken
        updateState {
            copy(
                editorOpen = true,
                editingProviderId = provider.id,
                isNewProvider = false,
                draftName = provider.name,
                baseUrl = provider.baseUrl,
                model = provider.model,
                apiKeyInput = "",
                apiKeySaved = false,
                apiKeyVisible = false,
                draftMakeActive = provider.id == activeProviderId,
                connectionError = null,
                connectionOk = false,
                models = emptyList(),
                modelPickerOpen = false,
                modelQuery = "",
                deleteConfirm = false,
                balance = providerBalances[provider.id],
                fetchingBalance = false,
                balanceError = null,
            )
        }
        viewModelScope.launch {
            val saved = secrets.apiKey(provider.id)
            if (currentState.editingProviderId != provider.id) return@launch
            updateState {
                copy(
                    apiKeyInput = saved.orEmpty(),
                    apiKeySaved = !saved.isNullOrBlank(),
                )
            }
            if (!saved.isNullOrBlank()) {
                val token = ++fetchToken
                fetchModels(token)
                refreshBalance(provider.id, provider.baseUrl, saved, notify = false)
            }
        }
    }

    private fun addProvider() {
        fetchJob?.cancel()
        ++fetchToken
        ++balanceToken
        updateState {
            copy(
                editorOpen = true,
                editingProviderId = null,
                isNewProvider = true,
                draftName = "",
                baseUrl = "",
                model = "",
                apiKeyInput = "",
                apiKeySaved = false,
                apiKeyVisible = false,
                draftMakeActive = true,
                connectionError = null,
                connectionOk = false,
                models = emptyList(),
                modelPickerOpen = false,
                modelQuery = "",
                deleteConfirm = false,
                balance = null,
                fetchingBalance = false,
                balanceError = null,
            )
        }
    }

    private fun closeEditor() {
        fetchJob?.cancel()
        ++fetchToken
        ++balanceToken
        updateState {
            copy(
                editorOpen = false,
                editingProviderId = null,
                isNewProvider = false,
                apiKeyInput = "",
                apiKeyVisible = false,
                connectionError = null,
                connectionOk = false,
                models = emptyList(),
                modelPickerOpen = false,
                modelQuery = "",
                deleteConfirm = false,
                balance = null,
                fetchingBalance = false,
                balanceError = null,
            )
        }
    }

    private fun saveProvider() {
        val state = currentState
        if (state.savingConnection) return
        val name = state.draftName.trim().ifBlank { "Provider" }
        val baseUrl = state.baseUrl.trim().trimEnd('/')
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            updateState {
                copy(connectionError = AppError(AppErrorType.Generic, serverMessage = "Enter a valid base URL"))
            }
            return
        }
        updateState { copy(savingConnection = true, connectionError = null) }
        viewModelScope.launch {
            try {
                val id = state.editingProviderId ?: UUID.randomUUID().toString()
                val provider = ProviderConfig(
                    id = id,
                    name = name,
                    baseUrl = baseUrl,
                    model = state.model.trim(),
                )
                settings.updateProvider(provider, makeActive = state.draftMakeActive)
                val key = state.apiKeyInput.trim()
                if (key.isNotBlank()) secrets.setApiKey(id, key)
                val config = settings.configOnce()
                updateState {
                    copy(
                        savingConnection = false,
                        editorOpen = false,
                        editingProviderId = null,
                        isNewProvider = false,
                        apiKeyInput = "",
                        apiKeyVisible = false,
                        providers = config.providers,
                        activeProviderId = config.activeProviderId,
                        models = emptyList(),
                        modelPickerOpen = false,
                        modelQuery = "",
                    )
                }
                val savedKey = key.ifBlank { secrets.apiKey(id).orEmpty() }
                if (savedKey.isNotBlank()) {
                    refreshBalance(id, provider.baseUrl, savedKey, notify = false)
                }
                sendEffect(SettingsEffect.ConnectionSaved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState { copy(savingConnection = false) }
                emitError(e.toAppError())
            }
        }
    }

    private fun testProvider() {
        val state = currentState
        if (state.testing) return
        val baseUrl = state.baseUrl.trim().trimEnd('/')
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://") || state.model.isBlank()) {
            updateState {
                copy(connectionError = AppError(AppErrorType.Generic, serverMessage = "Fill the endpoint and model"))
            }
            return
        }
        updateState { copy(testing = true, connectionError = null, connectionOk = false) }
        viewModelScope.launch {
            try {
                val key = state.apiKeyInput.trim().ifBlank {
                    state.editingProviderId?.let { secrets.apiKey(it) }.orEmpty()
                }
                if (key.isBlank()) {
                    updateState {
                        copy(
                            testing = false,
                            connectionError = AppError(AppErrorType.Unauthorized, serverMessage = "No API key"),
                        )
                    }
                    return@launch
                }
                tester.test(ModelEndpoint(baseUrl, state.model.trim()), key)
                updateState { copy(testing = false, connectionOk = true) }
                val id = state.editingProviderId
                if (id != null) {
                    refreshBalance(id, baseUrl, key, notify = false)
                }
                sendEffect(SettingsEffect.ConnectionOk)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ConnectionTestException) {
                val message = when (e.stage) {
                    ConnectionStage.Streaming -> "The model did not stream a reply"
                    ConnectionStage.ToolCalling -> "The model did not call a tool"
                }
                updateState {
                    copy(
                        testing = false,
                        connectionError = AppError(
                            AppErrorType.Generic,
                            serverMessage = listOfNotNull(message, e.detail).joinToString(": "),
                        ),
                    )
                }
            } catch (e: Throwable) {
                updateState { copy(testing = false, connectionError = e.toAppError()) }
            }
        }
    }

    private fun deleteProvider() {
        val state = currentState
        val id = state.editingProviderId ?: return
        if (!state.canDelete || state.savingConnection) return
        ++balanceToken
        viewModelScope.launch {
            try {
                settings.removeProvider(id)
                secrets.clearApiKey(id)
                val config = settings.configOnce()
                updateState {
                    copy(
                        providers = config.providers,
                        activeProviderId = config.activeProviderId,
                        editorOpen = false,
                        editingProviderId = null,
                        isNewProvider = false,
                        deleteConfirm = false,
                        apiKeyInput = "",
                        apiKeyVisible = false,
                        models = emptyList(),
                        modelPickerOpen = false,
                        modelQuery = "",
                        providerBalances = providerBalances - id,
                        balance = null,
                        fetchingBalance = false,
                        balanceError = null,
                    )
                }
                sendEffect(SettingsEffect.ProviderDeleted)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emitError(e.toAppError())
            }
        }
    }

    private fun scheduleModelFetch() {
        fetchJob?.cancel()
        val token = ++fetchToken
        val url = currentState.baseUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            updateState { copy(fetchingModels = false) }
            return
        }
        if (currentState.apiKeyInput.isBlank() && currentState.editingProviderId == null) {
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
        val baseUrl = currentState.baseUrl.trim().trimEnd('/')
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) return
        val key = currentState.apiKeyInput.trim().ifBlank {
            currentState.editingProviderId?.let { secrets.apiKey(it) }.orEmpty()
        }
        if (key.isBlank()) return
        updateState { copy(fetchingModels = true) }
        try {
            val models = catalog.list(ModelEndpoint(baseUrl, currentState.model.trim()), key)
            if (token != fetchToken) return
            updateState {
                copy(
                    fetchingModels = false,
                    models = models,
                    model = if (model.isBlank() && models.size == 1) models.first() else model,
                    modelPickerOpen = if (openPicker) models.isNotEmpty() else modelPickerOpen,
                    modelQuery = if (openPicker) "" else modelQuery,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (token != fetchToken) return
            updateState { copy(fetchingModels = false) }
            if (notify) emitError(e.toAppError())
        }
    }

    private fun setRefreshHours(hours: Int) {
        val clamped = hours.coerceIn(1, 24)
        updateState { copy(refreshHours = clamped) }
        viewModelScope.launch {
            try {
                sources.setRefreshIntervalHours(clamped)
                scheduler.schedule(clamped)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emitError(e.toAppError())
            }
        }
    }

    private fun resetSetup() {
        viewModelScope.launch {
            try {
                settings.resetOnboarding()
                updateState { copy(resetConfirm = false) }
                sendEffect(SettingsEffect.Reset)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emitError(e.toAppError())
            }
        }
    }
}
