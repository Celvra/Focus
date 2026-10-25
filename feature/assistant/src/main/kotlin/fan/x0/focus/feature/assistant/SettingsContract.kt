package fan.x0.focus.feature.assistant

import fan.x0.focus.core.error.AppError
import fan.x0.focus.core.mvi.UiAction
import fan.x0.focus.core.mvi.UiEffect
import fan.x0.focus.core.mvi.UiState
import fan.x0.focus.core.network.openai.ChatProtocol
import fan.x0.focus.core.network.openai.ProviderBalance
import fan.x0.focus.data.assistant.ProviderConfig

data class SettingsUiState(
    val loading: Boolean = true,
    val providers: List<ProviderConfig> = emptyList(),
    val activeProviderId: String = "",
    val refreshHours: Int = 6,
    val language: String? = null,
    val resetConfirm: Boolean = false,
    val editorOpen: Boolean = false,
    val editingProviderId: String? = null,
    val isNewProvider: Boolean = false,
    val draftName: String = "",
    val baseUrl: String = "",
    val model: String = "",
    val protocol: ChatProtocol = ChatProtocol.ChatCompletions,
    val apiKeyInput: String = "",
    val apiKeySaved: Boolean = false,
    val apiKeyVisible: Boolean = false,
    val draftMakeActive: Boolean = true,
    val savingConnection: Boolean = false,
    val testing: Boolean = false,
    val connectionError: AppError? = null,
    val connectionOk: Boolean = false,
    val models: List<String> = emptyList(),
    val fetchingModels: Boolean = false,
    val modelPickerOpen: Boolean = false,
    val modelQuery: String = "",
    val deleteConfirm: Boolean = false,
    val providerBalances: Map<String, ProviderBalance> = emptyMap(),
    val balance: ProviderBalance? = null,
    val fetchingBalance: Boolean = false,
    val balanceError: AppError? = null,
) : UiState {
    val activeProvider: ProviderConfig? get() = providers.firstOrNull { it.id == activeProviderId }
    val canDelete: Boolean get() = providers.size > 1 && !isNewProvider
    val balanceSupported: Boolean get() = editingProviderId != null
}

sealed interface SettingsAction : UiAction {
    data class RefreshHoursChanged(val hours: Int) : SettingsAction
    data class LanguageChanged(val tag: String?) : SettingsAction
    data object AskReset : SettingsAction
    data object CancelReset : SettingsAction
    data object ConfirmReset : SettingsAction
    data class SelectProvider(val id: String) : SettingsAction
    data class OpenProvider(val id: String) : SettingsAction
    data object AddProvider : SettingsAction
    data object CloseEditor : SettingsAction
    data class NameChanged(val value: String) : SettingsAction
    data class BaseUrlChanged(val value: String) : SettingsAction
    data class ModelChanged(val value: String) : SettingsAction
    data class ProtocolChanged(val value: ChatProtocol) : SettingsAction
    data class ApiKeyChanged(val value: String) : SettingsAction
    data object ToggleApiKeyVisibility : SettingsAction
    data class MakeActiveChanged(val value: Boolean) : SettingsAction
    data object SaveProvider : SettingsAction
    data object TestProvider : SettingsAction
    data object AskDeleteProvider : SettingsAction
    data object CancelDeleteProvider : SettingsAction
    data object DeleteProvider : SettingsAction
    data object FetchModels : SettingsAction
    data object OpenModelPicker : SettingsAction
    data object CloseModelPicker : SettingsAction
    data class ModelQueryChanged(val value: String) : SettingsAction
    data class SelectModel(val id: String) : SettingsAction
    data object FetchBalance : SettingsAction
}

sealed interface SettingsEffect : UiEffect {
    data object ConnectionSaved : SettingsEffect
    data object ConnectionOk : SettingsEffect
    data object ProviderDeleted : SettingsEffect
    data object Reset : SettingsEffect
}
