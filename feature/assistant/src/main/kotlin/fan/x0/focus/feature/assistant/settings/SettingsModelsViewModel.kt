package fan.x0.focus.feature.assistant.settings

import androidx.lifecycle.viewModelScope
import fan.x0.focus.core.mvi.BaseViewModel
import fan.x0.focus.data.assistant.AgentRole
import fan.x0.focus.data.assistant.AssistantSettings
import fan.x0.focus.data.assistant.ProviderModelCatalog
import fan.x0.focus.data.assistant.ProviderModelItem
import fan.x0.focus.data.assistant.RoleModels
import fan.x0.focus.data.assistant.ReasoningLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

data class RoleModelRowUi(
    val role: AgentRole,
    val providerId: String,
    val providerName: String,
    val model: String,
    val inherited: AgentRole?,
    val followsMain: Boolean,
    val own: Boolean,
    val toolsMissing: Boolean,
    val visionMissing: Boolean,
)

data class RoleModelPickerGroup(
    val id: String,
    val title: String,
    val icon: String,
    val models: List<RoleModelOption>,
)

data class RoleModelOption(
    val id: String,
    val name: String,
    val detail: String,
    val vision: Boolean,
    val visionKnown: Boolean,
    val tools: Boolean,
    val toolsKnown: Boolean,
    val reasoning: Boolean,
)

data class RoleModelsUiState(
    val loading: Boolean = true,
    val rows: List<RoleModelRowUi> = emptyList(),
    val pickerRole: AgentRole? = null,
    val groups: List<RoleModelPickerGroup> = emptyList(),
    val modelsLoading: Boolean = false,
    val query: String = "",
    val message: RoleModelsMessage? = null,
) : fan.x0.focus.core.mvi.UiState

data class RoleModelsMessage(val id: Long, val cleared: Boolean)

sealed interface RoleModelsAction : fan.x0.focus.core.mvi.UiAction {
    data class OpenPicker(val role: AgentRole) : RoleModelsAction
    data object ClosePicker : RoleModelsAction
    data class QueryChanged(val value: String) : RoleModelsAction
    data class Select(val providerId: String, val model: String) : RoleModelsAction
    data class Clear(val role: AgentRole) : RoleModelsAction
    data object RefreshModels : RoleModelsAction
    data object ConsumeMessage : RoleModelsAction
}

sealed interface RoleModelsEffect : fan.x0.focus.core.mvi.UiEffect {
    data class Saved(val cleared: Boolean) : RoleModelsEffect
}

@KoinViewModel
class RoleModelsViewModel(
    private val settings: AssistantSettings,
    private val roles: RoleModels,
    private val catalog: ProviderModelCatalog,
) : BaseViewModel<RoleModelsAction, RoleModelsUiState, RoleModelsEffect>(RoleModelsUiState()) {

    init {
        load()
    }

    override fun onAction(action: RoleModelsAction) {
        when (action) {
            is RoleModelsAction.OpenPicker -> openPicker(action.role)
            RoleModelsAction.ClosePicker -> updateState { copy(pickerRole = null, query = "") }
            is RoleModelsAction.QueryChanged -> updateState { copy(query = action.value) }
            is RoleModelsAction.Select -> select(action.providerId, action.model)
            is RoleModelsAction.Clear -> clear(action.role)
            RoleModelsAction.RefreshModels -> loadModels()
            RoleModelsAction.ConsumeMessage -> updateState { copy(message = null) }
        }
    }

    private fun load() {
        launchGuarded(onError = {}) {
            reloadRows()
            updateState { copy(loading = false) }
        }
    }

    private suspend fun reloadRows() {
        val config = settings.configOnce()
        val assigned = roles.configsOnce()
        val meta = modelMetaIndex()
        val rows = AgentRole.entries.map { role ->
            val own = assigned[role]
            val provider = own?.let { config.providers.firstOrNull { item -> item.id == it.providerId } }
            val inherited = if (own == null) role.fallbacks.firstOrNull { assigned[it] != null } else null
            val inheritedConfig = inherited?.let(assigned::get)
            val inheritedProvider = inheritedConfig?.let { entry ->
                config.providers.firstOrNull { it.id == entry.providerId }
            }
            val fallbackProvider = config.activeProvider
            val providerName = provider?.name
                ?: inheritedProvider?.name
                ?: fallbackProvider?.name.orEmpty()
            val model = own?.model?.takeIf { it.isNotBlank() }
                ?: inheritedConfig?.model?.takeIf { it.isNotBlank() }
                ?: provider?.model?.takeIf { it.isNotBlank() }
                ?: fallbackProvider?.model.orEmpty()
            val requestedModel = own?.model?.takeIf { it.isNotBlank() }
                ?: inheritedConfig?.model?.takeIf { it.isNotBlank() }
                ?: if (own == null && inherited == null) fallbackProvider?.model else null
            val metaProviderId = provider?.id ?: inheritedProvider?.id ?: fallbackProvider?.id
            val capability = requestedModel?.let { meta[metaProviderId to it] }
            RoleModelRowUi(
                role = role,
                providerId = provider?.id ?: inheritedProvider?.id ?: fallbackProvider?.id.orEmpty(),
                providerName = providerName,
                model = model,
                inherited = inherited,
                followsMain = own == null && inherited == null,
                own = own != null,
                toolsMissing = role.needsTools && capability?.tools == false,
                visionMissing = role.needsVision && capability?.vision == false,
            )
        }
        updateState { copy(rows = rows) }
    }

    private fun modelMetaIndex(): Map<Pair<String, String>, ModelCapability> = buildMap {
        currentState.groups.forEach { group ->
            group.models.forEach { model ->
                if (model.toolsKnown || model.visionKnown) {
                    put(
                        group.id to model.id,
                        ModelCapability(
                            tools = if (model.toolsKnown) model.tools else null,
                            vision = if (model.visionKnown) model.vision else null,
                        ),
                    )
                }
            }
        }
    }

    private data class ModelCapability(
        val tools: Boolean?,
        val vision: Boolean?,
    )

    private fun openPicker(role: AgentRole) {
        updateState { copy(pickerRole = role, query = "") }
        if (currentState.groups.isEmpty()) loadModels()
    }

    private fun loadModels() {
        if (currentState.modelsLoading) return
        updateState { copy(modelsLoading = true) }
        viewModelScope.launch {
            try {
                val config = settings.configOnce()
                val loaded = catalog.load(config)
                val groups = loaded.map { group ->
                    RoleModelPickerGroup(
                        id = group.providerId,
                        title = group.providerName,
                        icon = group.icon,
                        models = group.models.map { it.toOption() },
                    )
                }.filter { it.models.isNotEmpty() }
                updateState { copy(groups = groups, modelsLoading = false) }
                reloadRows()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                updateState { copy(modelsLoading = false) }
            }
        }
    }

    private fun select(providerId: String, model: String) {
        val role = currentState.pickerRole ?: return
        launchGuarded(onError = {}) {
            roles.set(role, providerId, model, ReasoningLevel.Auto.name)
            reloadRows()
            updateState {
                copy(
                    pickerRole = null,
                    query = "",
                    message = RoleModelsMessage(System.currentTimeMillis(), cleared = false),
                )
            }
            sendEffect(RoleModelsEffect.Saved(cleared = false))
        }
    }

    private fun clear(role: AgentRole) {
        launchGuarded(onError = {}) {
            roles.clear(role)
            reloadRows()
            updateState { copy(message = RoleModelsMessage(System.currentTimeMillis(), cleared = true)) }
            sendEffect(RoleModelsEffect.Saved(cleared = true))
        }
    }

    private fun ProviderModelItem.toOption(): RoleModelOption {
        val details = buildList {
            meta.contextWindow?.takeIf { it > 0 }?.let { add("${it / 1000}K") }
            if (meta.inputPrice != null || meta.outputPrice != null) {
                add("${meta.inputPrice ?: 0.0}/${meta.outputPrice ?: 0.0}/M")
            }
        }.joinToString(" · ")
        return RoleModelOption(
            id = id,
            name = name.ifBlank { id },
            detail = details,
            vision = meta.supportsImage == true,
            visionKnown = meta.supportsImage != null,
            tools = meta.supportsTools == true,
            toolsKnown = meta.supportsTools != null,
            reasoning = meta.supportsReasoning == true,
        )
    }
}
