package fan.x0.focus.feature.assistant

import android.net.Uri
import fan.x0.focus.core.error.AppError
import fan.x0.focus.core.mvi.UiAction
import fan.x0.focus.core.mvi.UiEffect
import fan.x0.focus.core.mvi.UiState
import fan.x0.focus.core.network.ProviderAuthType
import fan.x0.focus.core.network.ProviderHeader
import fan.x0.focus.core.network.openai.ChatProtocol
import fan.x0.focus.core.network.openai.ProviderBalance
import fan.x0.focus.data.assistant.AgentDefaults
import fan.x0.focus.data.assistant.CommandPolicy
import fan.x0.focus.data.assistant.Contributor
import fan.x0.focus.data.assistant.DataUsage
import fan.x0.focus.data.assistant.JudgeKind
import fan.x0.focus.data.assistant.ProviderConfig

data class SettingsUiState(
    val loading: Boolean = true,
    val providers: List<ProviderConfig> = emptyList(),
    val activeProviderId: String = "",
    val refreshHours: Int = 6,
    val language: String? = null,
    val resetConfirm: Boolean = false,
    val dataUsage: DataUsage = DataUsage(),
    val dataUsageLoading: Boolean = false,
    val contributors: List<Contributor> = emptyList(),
    val contributorAvatars: Map<String, ByteArray> = emptyMap(),
    val contributorsLoading: Boolean = false,
    val contributorAvatarsLoading: Boolean = false,
    val contributorsError: Boolean = false,
    val contributorsExpanded: Boolean = false,
    val pendingClear: DataClearTarget? = null,
    val editorOpen: Boolean = false,
    val editingProviderId: String? = null,
    val isNewProvider: Boolean = false,
    val draftName: String = "",
    val draftIcon: String = "",
    val encodingIcon: Boolean = false,
    val baseUrl: String = "",
    val model: String = "",
    val protocol: ChatProtocol = ChatProtocol.ChatCompletions,
    val apiKeyInput: String = "",
    val apiKeySaved: Boolean = false,
    val requiresKey: Boolean = true,
    val userAgent: String = "",
    val headers: List<ProviderHeader> = emptyList(),
    val authType: ProviderAuthType = ProviderAuthType.Default,
    val authName: String = "",
    val authUsername: String = "",
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
    val policy: CommandPolicy = CommandPolicy.Manual,
    val judgeKind: JudgeKind = JudgeKind.Jev,
    val judgeProviderId: String = "",
    val judgeModel: String = "",
    val judgeModels: List<String> = emptyList(),
    val judgeModelsLoading: Boolean = false,
    val judgeContextWindow: Int = 2,
    val judgeMaxRisk: Int = 3,
    val judgeTimeoutSeconds: Int = 8,
    val subagentProviderId: String = "",
    val subagentModel: String = "",
    val subagentModels: List<String> = emptyList(),
    val subagentModelsLoading: Boolean = false,
    val maxRounds: Int = AgentDefaults.MAX_ROUNDS,
    val unlimitedRounds: Boolean = false,
    val maxRetries: Int = 4,
    val modelPickerTarget: ModelPickerTarget = ModelPickerTarget.Provider,
) : UiState {
    val activeProvider: ProviderConfig? get() = providers.firstOrNull { it.id == activeProviderId }
    val canDelete: Boolean get() = !isNewProvider
    val balanceSupported: Boolean get() = editingProviderId != null
}

enum class ModelPickerTarget { Provider, Judge, Subagent }

enum class DataClearTarget { Conversations, FileIndex }

sealed interface SettingsAction : UiAction {
    data class RefreshHoursChanged(val hours: Int) : SettingsAction
    data class LanguageChanged(val tag: String?) : SettingsAction
    data object RefreshData : SettingsAction
    data object ToggleContributors : SettingsAction
    data class AskClearData(val target: DataClearTarget) : SettingsAction
    data object CancelClearData : SettingsAction
    data object ConfirmClearData : SettingsAction
    data object AskReset : SettingsAction
    data object CancelReset : SettingsAction
    data object ConfirmReset : SettingsAction
    data class SelectProvider(val id: String) : SettingsAction
    data class OpenProvider(val id: String) : SettingsAction
    data object AddProvider : SettingsAction
    data object CloseEditor : SettingsAction
    data class NameChanged(val value: String) : SettingsAction
    data class IconChanged(val value: String) : SettingsAction
    data class CustomIconPicked(val uri: Uri) : SettingsAction
    data class BaseUrlChanged(val value: String) : SettingsAction
    data class ModelChanged(val value: String) : SettingsAction
    data class ProtocolChanged(val value: ChatProtocol) : SettingsAction
    data class ApiKeyChanged(val value: String) : SettingsAction
    data class RequiresKeyChanged(val value: Boolean) : SettingsAction
    data class UserAgentChanged(val value: String) : SettingsAction
    data class AuthTypeChanged(val value: ProviderAuthType) : SettingsAction
    data class AuthNameChanged(val value: String) : SettingsAction
    data class AuthUsernameChanged(val value: String) : SettingsAction
    data object AddHeader : SettingsAction
    data class RemoveHeader(val index: Int) : SettingsAction
    data class HeaderNameChanged(val index: Int, val value: String) : SettingsAction
    data class HeaderValueChanged(val index: Int, val value: String) : SettingsAction
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
    data class PolicyChanged(val value: CommandPolicy) : SettingsAction
    data class JudgeKindChanged(val value: JudgeKind) : SettingsAction
    data class JudgeProviderChanged(val providerId: String) : SettingsAction
    data object OpenJudgeModelPicker : SettingsAction
    data object RefreshJudgeModels : SettingsAction
    data class JudgeContextChanged(val value: Int) : SettingsAction
    data class JudgeMaxRiskChanged(val value: Int) : SettingsAction
    data class JudgeTimeoutChanged(val value: Int) : SettingsAction
    data class SubagentProviderChanged(val providerId: String) : SettingsAction
    data object OpenSubagentModelPicker : SettingsAction
    data object RefreshSubagentModels : SettingsAction
    data class MaxRoundsChanged(val value: Int) : SettingsAction
    data class MaxRetriesChanged(val value: Int) : SettingsAction
    data class UnlimitedRoundsChanged(val value: Boolean) : SettingsAction
}

sealed interface SettingsEffect : UiEffect {
    data object ConnectionSaved : SettingsEffect
    data object ConnectionOk : SettingsEffect
    data object ProviderDeleted : SettingsEffect
    data object DataCleared : SettingsEffect
    data object Reset : SettingsEffect
}
