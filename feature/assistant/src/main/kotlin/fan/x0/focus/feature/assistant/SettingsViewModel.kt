package fan.x0.focus.feature.assistant

import android.net.Uri
import androidx.lifecycle.viewModelScope
import fan.x0.focus.core.error.AppError
import fan.x0.focus.core.error.AppErrorType
import fan.x0.focus.core.error.toAppError
import fan.x0.focus.core.mvi.BaseViewModel
import fan.x0.focus.core.network.ProviderAuth
import fan.x0.focus.core.network.ProviderAuthType
import fan.x0.focus.core.network.ProviderHeader
import fan.x0.focus.core.network.ProviderRequestConfig
import fan.x0.focus.core.network.openai.ChatProtocol
import fan.x0.focus.core.network.openai.ConnectionStage
import fan.x0.focus.core.network.openai.ConnectionTestException
import fan.x0.focus.core.network.openai.ModelConnectionTester
import fan.x0.focus.core.network.openai.ModelEndpoint
import fan.x0.focus.core.network.openai.OpenAiModelCatalog
import fan.x0.focus.core.network.openai.ProviderBalanceClient
import fan.x0.focus.core.notify.MessageNotifier
import fan.x0.focus.core.notify.NotificationSettings
import fan.x0.focus.data.assistant.AgentSettings
import fan.x0.focus.data.assistant.AssistantSettings
import fan.x0.focus.data.assistant.CommandPolicy
import fan.x0.focus.data.assistant.Contributor
import fan.x0.focus.data.assistant.ContributorsRepository
import fan.x0.focus.data.assistant.DataUsageStore
import fan.x0.focus.data.assistant.JudgeKind
import fan.x0.focus.data.assistant.LocaleController
import fan.x0.focus.data.assistant.PolicySettings
import fan.x0.focus.data.assistant.ProviderConfig
import fan.x0.focus.data.assistant.ProviderIconStore
import fan.x0.focus.data.assistant.ProviderModel
import fan.x0.focus.data.assistant.ProviderModelCatalog
import fan.x0.focus.data.assistant.SecretStore
import fan.x0.focus.data.assistant.UpdateController
import fan.x0.focus.data.assistant.modelOptions
import fan.x0.focus.data.assistant.requestConfig
import fan.x0.focus.data.sources.FeedRefreshScheduler
import fan.x0.focus.data.sources.SourcePreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    private val providerModels: ProviderModelCatalog,
    private val balanceClient: ProviderBalanceClient,
    private val locale: LocaleController,
    private val policy: PolicySettings,
    private val agent: AgentSettings,
    private val icons: ProviderIconStore,
    private val dataUsage: DataUsageStore,
    private val contributors: ContributorsRepository,
    private val notificationSettings: NotificationSettings,
    private val notifier: MessageNotifier,
    private val updates: UpdateController,
) : BaseViewModel<SettingsAction, SettingsUiState, SettingsEffect>(SettingsUiState()) {

    private var modelPickerJob: Job? = null
    private var avatarsJob: Job? = null
    private var fetchToken = 0
    private var balanceToken = 0

    init {
        viewModelScope.launch {
            val config = settings.configOnce()
            val source = sources.settings.first()
            val policyConfig = policy.configOnce()
            val agentConfig = agent.configOnce()
            val notificationsEnabled = notificationSettings.enabledOnce()
            updateState {
                copy(
                    loading = false,
                    providers = config.providers,
                    activeProviderId = config.activeProviderId,
                    refreshHours = source.refreshIntervalHours,
                    notificationsEnabled = notificationsEnabled,
                    language = locale.current(),
                    policy = policyConfig.policy,
                    judgeKind = policyConfig.judgeKind,
                    judgeProviderId = policyConfig.judgeProviderId,
                    judgeModel = policyConfig.judgeModel,
                    judgeContextWindow = policyConfig.contextWindow,
                    judgeMaxRisk = policyConfig.maxRisk,
                    judgeTimeoutSeconds = policyConfig.timeoutSeconds,
                    maxRounds = agentConfig.maxRounds,
                    unlimitedRounds = agentConfig.unlimited,
                    maxRetries = agentConfig.retry.maxRetries,
                )
            }
            loadBalances(config.providers)
        }
        viewModelScope.launch {
            settings.config.collect { config ->
                updateState {
                    copy(
                        providers = config.providers,
                        activeProviderId = config.activeProviderId,
                    )
                }
            }
        }
        viewModelScope.launch {
            updates.state.collect { update ->
                updateState {
                    copy(
                        updateChecking = update.checking,
                        updateChecked = update.checked,
                        updateAvailable = update.updateAvailable,
                        updateVersion = update.release?.tagName.orEmpty(),
                        updateError = update.error != null,
                    )
                }
            }
        }
    }

    private suspend fun loadBalances(providers: List<ProviderConfig>) {
        providers.filter { balanceClient.supports(it.id) }.forEach { provider ->
            val key = secrets.apiKey(provider.id)
            if (key.isNullOrBlank()) return@forEach
            try {
                val balance = balanceClient.fetch(provider.id, provider.baseUrl, provider.requestConfig(key), provider.protocol)
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
            is SettingsAction.NotificationsEnabledChanged -> setNotificationsEnabled(action.value)
            is SettingsAction.LanguageChanged -> {
                locale.setLanguage(action.tag)
                updateState { copy(language = action.tag) }
            }
            SettingsAction.RefreshData -> refreshData()
            SettingsAction.CheckUpdate -> updates.check(userInitiated = true)
            SettingsAction.ToggleContributors -> toggleContributors()
            is SettingsAction.AskClearData -> updateState { copy(pendingClear = action.target) }
            SettingsAction.CancelClearData -> updateState { copy(pendingClear = null) }
            SettingsAction.ConfirmClearData -> confirmClearData()
            SettingsAction.AskReset -> updateState { copy(resetConfirm = true) }
            SettingsAction.CancelReset -> updateState { copy(resetConfirm = false) }
            SettingsAction.ConfirmReset -> resetSetup()
            is SettingsAction.SelectProvider -> selectProvider(action.id)
            is SettingsAction.OpenProvider -> openProvider(action.id)
            SettingsAction.AddProvider -> addProvider()
            SettingsAction.CloseEditor -> closeEditor()
            is SettingsAction.NameChanged -> updateState { copy(draftName = action.value) }
            is SettingsAction.IconChanged -> updateState { copy(draftIcon = action.value) }
            is SettingsAction.CustomIconPicked -> importIcon(action.uri)
            is SettingsAction.BaseUrlChanged -> updateState {
                copy(
                    baseUrl = action.value,
                    connectionOk = false,
                    connectionError = null,
                    availableModels = emptyList(),
                )
            }
            is SettingsAction.ProtocolChanged -> updateState {
                copy(
                    protocol = action.value,
                    connectionOk = false,
                    connectionError = null,
                    availableModels = emptyList(),
                )
            }
            is SettingsAction.ApiKeyChanged -> updateState {
                copy(
                    apiKeyInput = action.value,
                    connectionOk = false,
                    connectionError = null,
                    availableModels = emptyList(),
                )
            }
            SettingsAction.ToggleApiKeyVisibility -> updateState {
                copy(apiKeyVisible = !apiKeyVisible)
            }
            is SettingsAction.RequiresKeyChanged -> updateState {
                copy(requiresKey = action.value, connectionOk = false, connectionError = null)
            }
            is SettingsAction.UserAgentChanged -> updateState {
                copy(userAgent = action.value, connectionOk = false, connectionError = null)
            }
            is SettingsAction.AuthTypeChanged -> updateState {
                copy(
                    authType = action.value,
                    authName = if (
                        action.value == ProviderAuthType.ApiKeyHeader ||
                        action.value == ProviderAuthType.Query
                    ) {
                        authName
                    } else {
                        ""
                    },
                    authUsername = if (action.value == ProviderAuthType.Basic) authUsername else "",
                    connectionOk = false,
                    connectionError = null,
                )
            }
            is SettingsAction.AuthNameChanged -> updateState {
                copy(authName = action.value, connectionOk = false, connectionError = null)
            }
            is SettingsAction.AuthUsernameChanged -> updateState {
                copy(authUsername = action.value, connectionOk = false, connectionError = null)
            }
            SettingsAction.AddHeader -> updateState {
                copy(headers = headers + ProviderHeader())
            }
            is SettingsAction.RemoveHeader -> updateState {
                copy(headers = headers.filterIndexed { index, _ -> index != action.index })
            }
            is SettingsAction.HeaderNameChanged -> updateHeader(action.index) {
                it.copy(name = action.value)
            }
            is SettingsAction.HeaderValueChanged -> updateHeader(action.index) {
                it.copy(value = action.value)
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
            is SettingsAction.AddProviderModel -> addProviderModel(action.id, action.name)
            is SettingsAction.UpdateProviderModel -> updateProviderModel(
                action.originalId,
                action.id,
                action.name,
            )
            is SettingsAction.RemoveProviderModel -> removeProviderModel(action.id)
            is SettingsAction.SetDefaultProviderModel -> updateState {
                copy(model = action.id, connectionOk = false, connectionError = null)
            }
            is SettingsAction.ToggleProviderModel -> toggleProviderModel(action.id)
            is SettingsAction.AddProviderModels -> addProviderModels(action.ids)
            SettingsAction.OpenActiveModelPicker -> openActiveModelPicker()
            SettingsAction.RefreshActiveModels -> openActiveModelPicker()
            is SettingsAction.SelectActiveModel -> selectActiveModel(action.providerId, action.model)
            SettingsAction.CloseModelPicker -> updateState {
                copy(
                    modelPickerOpen = false,
                    modelPickerTarget = ModelPickerTarget.Active,
                    modelQuery = "",
                )
            }
            is SettingsAction.ModelQueryChanged -> updateState { copy(modelQuery = action.value) }
            is SettingsAction.SelectModel -> {
                when (currentState.modelPickerTarget) {
                    ModelPickerTarget.Judge -> setJudgeSelection(action.providerId, action.id)
                    ModelPickerTarget.Active -> Unit
                }
            }
            SettingsAction.FetchBalance -> fetchBalance()
            is SettingsAction.PolicyChanged -> setPolicy(action.value)
            is SettingsAction.JudgeKindChanged -> setJudgeKind(action.value)
            SettingsAction.OpenJudgeModelPicker -> openJudgeModelPicker()
            SettingsAction.RefreshJudgeModels -> openJudgeModelPicker()
            is SettingsAction.JudgeContextChanged -> setJudgeContext(action.value)
            is SettingsAction.JudgeMaxRiskChanged -> setJudgeMaxRisk(action.value)
            is SettingsAction.JudgeTimeoutChanged -> setJudgeTimeout(action.value)
            is SettingsAction.MaxRoundsChanged -> setMaxRounds(action.value)
            is SettingsAction.MaxRetriesChanged -> setMaxRetries(action.value)
            is SettingsAction.UnlimitedRoundsChanged -> setUnlimitedRounds(action.value)
        }
    }

    private fun setPolicy(value: CommandPolicy) {
        updateState { copy(policy = value) }
        viewModelScope.launch { policy.setPolicy(value) }
    }

    private fun setJudgeKind(value: JudgeKind) {
        updateState { copy(judgeKind = value) }
        viewModelScope.launch { policy.setJudgeKind(value) }
    }

    private fun setJudgeSelection(providerId: String, model: String) {
        updateState {
            copy(
                modelPickerOpen = false,
                modelQuery = "",
                judgeProviderId = providerId,
                judgeModel = model,
            )
        }
        viewModelScope.launch { policy.setJudgeProvider(providerId, model) }
    }

    private fun setJudgeContext(value: Int) {
        updateState { copy(judgeContextWindow = value) }
        viewModelScope.launch { policy.setContextWindow(value) }
    }

    private fun openJudgeModelPicker() {
        openModelPicker(ModelPickerTarget.Judge)
    }

    private fun setJudgeMaxRisk(value: Int) {
        updateState { copy(judgeMaxRisk = value) }
        viewModelScope.launch { policy.setMaxRisk(value) }
    }

    private fun setJudgeTimeout(value: Int) {
        updateState { copy(judgeTimeoutSeconds = value) }
        viewModelScope.launch { policy.setTimeoutSeconds(value) }
    }

    private fun setMaxRounds(value: Int) {
        updateState { copy(maxRounds = value) }
        viewModelScope.launch { agent.setMaxRounds(value) }
    }

    private fun setMaxRetries(value: Int) {
        updateState { copy(maxRetries = value) }
        viewModelScope.launch { agent.setMaxRetries(value) }
    }

    private fun setUnlimitedRounds(value: Boolean) {
        updateState { copy(unlimitedRounds = value) }
        viewModelScope.launch { agent.setUnlimited(value) }
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
            refreshBalance(id, baseUrl, draftRequestConfig(key), state.protocol, notify = true)
        }
    }

    private suspend fun refreshBalance(
        providerId: String,
        baseUrl: String,
        requestConfig: ProviderRequestConfig,
        protocol: ChatProtocol,
        notify: Boolean,
    ) {
        if (requestConfig.apiKey.isBlank()) return
        val token = ++balanceToken
        updateState { copy(fetchingBalance = true, balanceError = null) }
        try {
            val result = balanceClient.fetch(providerId, baseUrl, requestConfig, protocol)
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

    private fun openActiveModelPicker() {
        openModelPicker(ModelPickerTarget.Active)
    }

    private fun openModelPicker(target: ModelPickerTarget) {
        modelPickerJob?.cancel()
        updateState {
            copy(
                modelPickerOpen = true,
                modelPickerTarget = target,
                modelQuery = "",
                providerModelsLoading = true,
            )
        }
        modelPickerJob = viewModelScope.launch {
            val groups = providerModels.load(settings.configOnce())
            updateState {
                copy(
                    providerModelGroups = groups,
                    providerModelsLoading = false,
                )
            }
        }
    }

    private fun selectActiveModel(providerId: String, model: String) {
        viewModelScope.launch {
            val provider = settings.configOnce().providers
                .firstOrNull { it.id == providerId }
                ?: return@launch
            settings.updateProvider(provider.copy(model = model), makeActive = true)
            updateState {
                copy(
                    providers = providers.map {
                        if (it.id == providerId) it.copy(model = model) else it
                    },
                    activeProviderId = providerId,
                    modelPickerOpen = false,
                    modelQuery = "",
                )
            }
        }
    }

    private fun openProvider(id: String) {
        val provider = currentState.providers.firstOrNull { it.id == id } ?: return
        ++fetchToken
        ++balanceToken
        updateState {
            copy(
                editorOpen = true,
                editingProviderId = provider.id,
                isNewProvider = false,
                draftName = provider.name,
                draftIcon = provider.icon,
                encodingIcon = false,
                baseUrl = provider.baseUrl,
                model = provider.model.ifBlank {
                    provider.modelOptions.firstOrNull()?.id.orEmpty()
                },
                draftModels = provider.modelOptions,
                draftModelsChanged = false,
                protocol = provider.protocol,
                apiKeyInput = "",
                apiKeySaved = false,
                apiKeyVisible = false,
                requiresKey = provider.requiresKey,
                userAgent = provider.userAgent,
                headers = provider.headers,
                authType = provider.auth.type,
                authName = provider.auth.name,
                authUsername = provider.auth.username,
                draftMakeActive = provider.id == activeProviderId,
                connectionError = null,
                connectionOk = false,
                savingConnection = false,
                testing = false,
                availableModels = emptyList(),
                fetchingModels = false,
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
                refreshBalance(
                    provider.id,
                    provider.baseUrl,
                    provider.requestConfig(saved),
                    provider.protocol,
                    notify = false,
                )
            }
        }
    }

    private fun addProvider() {
        ++fetchToken
        ++balanceToken
        updateState {
            copy(
                editorOpen = true,
                editingProviderId = null,
                isNewProvider = true,
                draftName = "",
                draftIcon = "",
                encodingIcon = false,
                baseUrl = "",
                model = "",
                draftModels = emptyList(),
                draftModelsChanged = false,
                protocol = ChatProtocol.ChatCompletions,
                apiKeyInput = "",
                apiKeySaved = false,
                apiKeyVisible = false,
                requiresKey = true,
                userAgent = "",
                headers = emptyList(),
                authType = ProviderAuthType.Default,
                authName = "",
                authUsername = "",
                draftMakeActive = true,
                connectionError = null,
                connectionOk = false,
                savingConnection = false,
                testing = false,
                availableModels = emptyList(),
                fetchingModels = false,
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
        ++fetchToken
        ++balanceToken
        updateState {
            copy(
                editorOpen = false,
                editingProviderId = null,
                isNewProvider = false,
                apiKeyInput = "",
                apiKeyVisible = false,
                encodingIcon = false,
                requiresKey = true,
                connectionError = null,
                connectionOk = false,
                draftModels = emptyList(),
                draftModelsChanged = false,
                availableModels = emptyList(),
                fetchingModels = false,
                modelPickerOpen = false,
                modelQuery = "",
                deleteConfirm = false,
                balance = null,
                fetchingBalance = false,
                balanceError = null,
            )
        }
    }

    private fun importIcon(uri: Uri) {
        if (currentState.encodingIcon) return
        updateState { copy(encodingIcon = true) }
        viewModelScope.launch {
            try {
                val encoded = icons.encode(uri)
                if (encoded == null) {
                    updateState { copy(encodingIcon = false) }
                    emitError(AppError(AppErrorType.Generic, serverMessage = "Could not read the image"))
                } else {
                    updateState { copy(encodingIcon = false, draftIcon = encoded) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState { copy(encodingIcon = false) }
                emitError(e.toAppError())
            }
        }
    }

    private fun updateHeader(index: Int, reduce: (ProviderHeader) -> ProviderHeader) {
        val state = currentState
        if (index !in state.headers.indices) return
        val headers = state.headers.toMutableList()
        headers[index] = reduce(headers[index])
        updateState { copy(headers = headers, connectionOk = false, connectionError = null) }
    }

    private fun addProviderModel(id: String, name: String) {
        val modelId = id.trim()
        if (modelId.isBlank()) return
        if (currentState.draftModels.any { it.id == modelId }) {
            emitError(AppError(AppErrorType.Generic, serverMessage = "Model already exists"))
            return
        }
        updateState {
            copy(
                draftModels = draftModels + ProviderModel(modelId, name.trim()),
                draftModelsChanged = true,
                model = model.ifBlank { modelId },
                connectionOk = false,
                connectionError = null,
            )
        }
    }

    private fun updateProviderModel(originalId: String, id: String, name: String) {
        val modelId = id.trim()
        if (modelId.isBlank()) return
        val state = currentState
        val index = state.draftModels.indexOfFirst { it.id == originalId }
        if (index < 0) return
        if (modelId != originalId && state.draftModels.any { it.id == modelId }) {
            emitError(AppError(AppErrorType.Generic, serverMessage = "Model already exists"))
            return
        }
        updateState {
            val models = draftModels.toMutableList()
            models[index] = ProviderModel(modelId, name.trim())
            copy(
                draftModels = models,
                draftModelsChanged = true,
                model = if (model == originalId) modelId else model,
                connectionOk = false,
                connectionError = null,
            )
        }
    }

    private fun removeProviderModel(id: String) {
        updateState {
            val models = draftModels.filterNot { it.id == id }
            copy(
                draftModels = models,
                draftModelsChanged = true,
                model = if (model == id) models.firstOrNull()?.id.orEmpty() else model,
                connectionOk = false,
                connectionError = null,
            )
        }
    }

    private fun toggleProviderModel(id: String) {
        if (id.isBlank()) return
        updateState {
            if (draftModels.any { it.id == id }) {
                val models = draftModels.filterNot { it.id == id }
                copy(
                    draftModels = models,
                    draftModelsChanged = true,
                    model = if (model == id) models.firstOrNull()?.id.orEmpty() else model,
                )
            } else {
                copy(
                    draftModels = draftModels + ProviderModel(id),
                    draftModelsChanged = true,
                    model = model.ifBlank { id },
                )
            }
        }
    }

    private fun addProviderModels(ids: List<String>) {
        updateState {
            val models = draftModels.toMutableList()
            ids.filter { it.isNotBlank() }.forEach { id ->
                if (models.none { it.id == id }) models += ProviderModel(id)
            }
            copy(
                draftModels = models,
                draftModelsChanged = true,
                model = model.ifBlank { models.firstOrNull()?.id.orEmpty() },
            )
        }
    }

    private fun draftRequestConfig(apiKey: String): ProviderRequestConfig = ProviderRequestConfig(
        apiKey = apiKey,
        auth = ProviderAuth(
            type = currentState.authType,
            name = currentState.authName.trim(),
            username = currentState.authUsername.trim(),
        ),
        userAgent = currentState.userAgent.trim(),
        headers = currentState.headers.filter { it.name.isNotBlank() },
    )

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
                val existing = state.providers.firstOrNull { it.id == id }
                val defaultModel = state.model.trim()
                val models = if (state.draftModelsChanged) {
                    state.draftModels
                        .map { ProviderModel(it.id.trim(), it.name.trim()) }
                        .filter { it.id.isNotBlank() }
                        .distinctBy { it.id }
                        .toMutableList()
                        .also { list ->
                            if (defaultModel.isNotBlank() && list.none { it.id == defaultModel }) {
                                list.add(0, ProviderModel(defaultModel))
                            }
                        }
                } else {
                    existing?.models.orEmpty()
                }
                val provider = ProviderConfig(
                    id = id,
                    name = name,
                    baseUrl = baseUrl,
                    model = defaultModel.ifBlank { models.firstOrNull()?.id.orEmpty() },
                    models = models,
                    modelsConfigured = state.draftModelsChanged || existing?.modelsConfigured == true,
                    protocol = state.protocol,
                    requiresKey = state.requiresKey,
                    userAgent = state.userAgent.trim(),
                    headers = state.headers.filter { it.name.isNotBlank() },
                    auth = ProviderAuth(
                        type = state.authType,
                        name = state.authName.trim(),
                        username = state.authUsername.trim(),
                    ),
                    icon = state.draftIcon,
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
                        encodingIcon = false,
                        providers = config.providers,
                        activeProviderId = config.activeProviderId,
                        draftModels = emptyList(),
                        draftModelsChanged = false,
                        availableModels = emptyList(),
                        fetchingModels = false,
                        modelPickerOpen = false,
                        modelQuery = "",
                    )
                }
                val savedKey = key.ifBlank { secrets.apiKey(id).orEmpty() }
                if (savedKey.isNotBlank()) {
                    refreshBalance(
                        id,
                        provider.baseUrl,
                        provider.requestConfig(savedKey),
                        provider.protocol,
                        notify = false,
                    )
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
        val model = state.model.ifBlank { state.draftModels.firstOrNull()?.id.orEmpty() }
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://") || model.isBlank()) {
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
                val requiresKey = state.requiresKey
                if (key.isBlank() && requiresKey) {
                    updateState {
                        copy(
                            testing = false,
                            connectionError = AppError(AppErrorType.Unauthorized, serverMessage = "No API key"),
                        )
                    }
                    return@launch
                }
                tester.test(ModelEndpoint(baseUrl, model), draftRequestConfig(key), state.protocol)
                updateState { copy(testing = false, connectionOk = true) }
                val id = state.editingProviderId
                if (id != null) {
                    refreshBalance(
                        id,
                        baseUrl,
                        draftRequestConfig(key),
                        state.protocol,
                        notify = false,
                    )
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
                        draftModels = emptyList(),
                        draftModelsChanged = false,
                        availableModels = emptyList(),
                        fetchingModels = false,
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

    private suspend fun fetchModels(token: Int, notify: Boolean = false) {
        if (token != fetchToken) return
        val state = currentState
        val baseUrl = state.baseUrl.trim().trimEnd('/')
        val key = state.apiKeyInput.trim().ifBlank {
            state.editingProviderId?.let { secrets.apiKey(it) }.orEmpty()
        }
        val validUrl = baseUrl.startsWith("http://") || baseUrl.startsWith("https://")
        if (!validUrl || (key.isBlank() && state.requiresKey)) {
            updateState { copy(fetchingModels = false) }
            if (notify) {
                emitError(
                    AppError(AppErrorType.Generic, serverMessage = "Fill the endpoint and API key"),
                )
            }
            return
        }
        updateState { copy(fetchingModels = true) }
        try {
            val models = catalog.list(
                ModelEndpoint(baseUrl, state.model.trim()),
                draftRequestConfig(key),
                state.protocol,
            )
            if (token != fetchToken) return
            updateState {
                copy(
                    fetchingModels = false,
                    availableModels = models,
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

    private fun refreshData() {
        loadDataUsage()
        loadContributors()
    }

    private fun loadDataUsage() {
        viewModelScope.launch {
            updateState { copy(dataUsageLoading = true) }
            try {
                val usage = dataUsage.snapshot()
                updateState { copy(dataUsage = usage, dataUsageLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState { copy(dataUsageLoading = false) }
                emitError(e.toAppError())
            }
        }
    }

    private fun loadContributors() {
        viewModelScope.launch {
            updateState { copy(contributorsLoading = true, contributorsError = false) }
            try {
                val list = contributors.list()
                avatarsJob?.cancel()
                updateState {
                    copy(
                        contributors = list,
                        contributorsLoading = false,
                        contributorAvatars = emptyMap(),
                        contributorAvatarsLoading = false,
                    )
                }
                if (currentState.contributorsExpanded) loadContributorAvatars(list)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                updateState { copy(contributorsLoading = false, contributorsError = true) }
            }
        }
    }

    private fun toggleContributors() {
        val expanded = !currentState.contributorsExpanded
        updateState { copy(contributorsExpanded = expanded) }
        if (!expanded) return
        val list = currentState.contributors
        when {
            list.isEmpty() && !currentState.contributorsLoading -> loadContributors()
            list.isNotEmpty() && currentState.contributorAvatars.isEmpty() -> loadContributorAvatars(list)
        }
    }

    private fun loadContributorAvatars(list: List<Contributor>) {
        if (list.isEmpty()) return
        avatarsJob?.cancel()
        avatarsJob = viewModelScope.launch {
            updateState { copy(contributorAvatarsLoading = true) }
            try {
                val avatars = contributors.avatars(list)
                updateState { copy(contributorAvatars = avatars, contributorAvatarsLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                updateState { copy(contributorAvatarsLoading = false) }
            }
        }
    }

    private fun confirmClearData() {
        val target = currentState.pendingClear ?: return
        viewModelScope.launch {
            try {
                when (target) {
                    DataClearTarget.Conversations -> dataUsage.clearConversations()
                    DataClearTarget.FileIndex -> dataUsage.clearFileIndex()
                }
                updateState { copy(pendingClear = null) }
                sendEffect(SettingsEffect.DataCleared)
                loadDataUsage()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState { copy(pendingClear = null) }
                emitError(e.toAppError())
            }
        }
    }

    private fun setNotificationsEnabled(value: Boolean) {
        updateState { copy(notificationsEnabled = value) }
        viewModelScope.launch {
            try {
                notificationSettings.setEnabled(value)
                if (!value) notifier.cancelAll()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emitError(e.toAppError())
            }
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
