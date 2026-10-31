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
import fan.x0.focus.data.assistant.SecretStore
import fan.x0.focus.data.assistant.requestConfig
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
    private val policy: PolicySettings,
    private val agent: AgentSettings,
    private val icons: ProviderIconStore,
    private val dataUsage: DataUsageStore,
    private val contributors: ContributorsRepository,
    private val notificationSettings: NotificationSettings,
    private val notifier: MessageNotifier,
) : BaseViewModel<SettingsAction, SettingsUiState, SettingsEffect>(SettingsUiState()) {

    private var fetchJob: Job? = null
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
                    subagentProviderId = agentConfig.subagentProviderId,
                    subagentModel = agentConfig.subagentModel,
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
            is SettingsAction.ProtocolChanged -> {
                updateState {
                    copy(
                        protocol = action.value,
                        connectionOk = false,
                        connectionError = null,
                        models = emptyList(),
                    )
                }
                scheduleModelFetch()
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
            SettingsAction.OpenModelPicker -> {
                if (currentState.models.isEmpty()) {
                    val token = ++fetchToken
                    viewModelScope.launch { fetchModels(token, openPicker = true, notify = true) }
                } else {
                    updateState {
                        copy(
                            modelPickerOpen = true,
                            modelPickerTarget = ModelPickerTarget.Provider,
                            modelQuery = "",
                        )
                    }
                }
            }
            SettingsAction.CloseModelPicker -> updateState {
                copy(
                    modelPickerOpen = false,
                    modelPickerTarget = ModelPickerTarget.Provider,
                    modelQuery = "",
                )
            }
            is SettingsAction.ModelQueryChanged -> updateState { copy(modelQuery = action.value) }
            is SettingsAction.SelectModel -> {
                when (currentState.modelPickerTarget) {
                    ModelPickerTarget.Judge -> {
                        updateState { copy(modelPickerOpen = false, modelQuery = "") }
                        setJudgeModel(action.id)
                    }
                    ModelPickerTarget.Subagent -> {
                        updateState { copy(modelPickerOpen = false, modelQuery = "") }
                        setSubagentModel(action.id)
                    }
                    ModelPickerTarget.Provider -> updateState {
                        copy(model = action.id, modelPickerOpen = false, modelQuery = "", connectionOk = false)
                    }
                }
            }
            SettingsAction.FetchBalance -> fetchBalance()
            is SettingsAction.PolicyChanged -> setPolicy(action.value)
            is SettingsAction.JudgeKindChanged -> setJudgeKind(action.value)
            is SettingsAction.JudgeProviderChanged -> setJudgeProvider(action.providerId)
            SettingsAction.OpenJudgeModelPicker -> openJudgeModelPicker()
            SettingsAction.RefreshJudgeModels -> openJudgeModelPicker()
            is SettingsAction.JudgeContextChanged -> setJudgeContext(action.value)
            is SettingsAction.JudgeMaxRiskChanged -> setJudgeMaxRisk(action.value)
            is SettingsAction.JudgeTimeoutChanged -> setJudgeTimeout(action.value)
            is SettingsAction.SubagentProviderChanged -> setSubagentProvider(action.providerId)
            SettingsAction.OpenSubagentModelPicker -> openSubagentModelPicker()
            SettingsAction.RefreshSubagentModels -> openSubagentModelPicker()
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

    private fun setJudgeProvider(providerId: String) {
        val model = currentState.providers.firstOrNull { it.id == providerId }?.model.orEmpty()
        updateState { copy(judgeProviderId = providerId, judgeModel = model) }
        viewModelScope.launch { policy.setJudgeProvider(providerId, model) }
    }

    private fun setJudgeContext(value: Int) {
        updateState { copy(judgeContextWindow = value) }
        viewModelScope.launch { policy.setContextWindow(value) }
    }

    private fun setJudgeModel(value: String) {
        updateState { copy(judgeModel = value) }
        viewModelScope.launch { policy.setJudgeModel(value) }
    }

    private fun openJudgeModelPicker() {
        updateState { copy(judgeModelsLoading = true) }
        viewModelScope.launch {
            val provider = currentState.providers.firstOrNull { it.id == currentState.judgeProviderId }
            val seeded = listOfNotNull(
                provider?.model?.takeIf { it.isNotBlank() },
                currentState.judgeModel.takeIf { it.isNotBlank() },
            )
            val fetched = provider?.let { config ->
                val key = secrets.apiKey(config.id).orEmpty()
                if (key.isBlank()) {
                    null
                } else {
                    runCatching {
                        catalog.list(
                            ModelEndpoint(config.baseUrl, currentState.judgeModel.trim()),
                            config.requestConfig(key),
                            config.protocol,
                        )
                    }.getOrElse { error ->
                        if (seeded.isEmpty()) emitError(error.toAppError())
                        null
                    }
                }
            }
            val models = (seeded + fetched.orEmpty()).distinct()
            updateState {
                copy(
                    judgeModels = models,
                    judgeModelsLoading = false,
                    modelPickerOpen = models.isNotEmpty(),
                    modelPickerTarget = ModelPickerTarget.Judge,
                    modelQuery = "",
                )
            }
        }
    }

    private fun setJudgeMaxRisk(value: Int) {
        updateState { copy(judgeMaxRisk = value) }
        viewModelScope.launch { policy.setMaxRisk(value) }
    }

    private fun setJudgeTimeout(value: Int) {
        updateState { copy(judgeTimeoutSeconds = value) }
        viewModelScope.launch { policy.setTimeoutSeconds(value) }
    }

    private fun setSubagentProvider(providerId: String) {
        val model = currentState.providers.firstOrNull { it.id == providerId }?.model.orEmpty()
        updateState { copy(subagentProviderId = providerId, subagentModel = model) }
        viewModelScope.launch { agent.setSubagentProvider(providerId, model) }
    }

    private fun setSubagentModel(value: String) {
        updateState { copy(subagentModel = value) }
        viewModelScope.launch { agent.setSubagentModel(value) }
    }

    private fun openSubagentModelPicker() {
        updateState { copy(subagentModelsLoading = true) }
        viewModelScope.launch {
            val provider = currentState.providers.firstOrNull { it.id == currentState.subagentProviderId }
            val seeded = listOfNotNull(
                provider?.model?.takeIf { it.isNotBlank() },
                currentState.subagentModel.takeIf { it.isNotBlank() },
            )
            val fetched = provider?.let { config ->
                val key = secrets.apiKey(config.id).orEmpty()
                if (key.isBlank()) {
                    null
                } else {
                    runCatching {
                        catalog.list(
                            ModelEndpoint(config.baseUrl, currentState.subagentModel.trim()),
                            config.requestConfig(key),
                            config.protocol,
                        )
                    }.getOrElse { error ->
                        if (seeded.isEmpty()) emitError(error.toAppError())
                        null
                    }
                }
            }
            val models = (seeded + fetched.orEmpty()).distinct()
            updateState {
                copy(
                    subagentModels = models,
                    subagentModelsLoading = false,
                    modelPickerOpen = models.isNotEmpty(),
                    modelPickerTarget = ModelPickerTarget.Subagent,
                    modelQuery = "",
                )
            }
        }
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
                draftIcon = provider.icon,
                encodingIcon = false,
                baseUrl = provider.baseUrl,
                model = provider.model,
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
        fetchJob?.cancel()
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
                encodingIcon = false,
                requiresKey = true,
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
                val provider = ProviderConfig(
                    id = id,
                    name = name,
                    baseUrl = baseUrl,
                    model = state.model.trim(),
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
                        models = emptyList(),
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
                tester.test(ModelEndpoint(baseUrl, state.model.trim()), draftRequestConfig(key), state.protocol)
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
        val editingProvider = currentState.providers
            .firstOrNull { it.id == currentState.editingProviderId }
        if (currentState.apiKeyInput.isBlank() && currentState.requiresKey) {
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
        if (key.isBlank() && currentState.requiresKey) return
        updateState { copy(fetchingModels = true) }
        try {
            val models = catalog.list(
                ModelEndpoint(baseUrl, currentState.model.trim()),
                draftRequestConfig(key),
                currentState.protocol,
            )
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
