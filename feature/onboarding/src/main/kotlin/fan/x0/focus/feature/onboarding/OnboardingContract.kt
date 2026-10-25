package fan.x0.focus.feature.onboarding

import fan.x0.focus.core.error.AppError
import fan.x0.focus.core.mvi.UiAction
import fan.x0.focus.core.mvi.UiEffect
import fan.x0.focus.core.mvi.UiState
import fan.x0.focus.core.network.openai.ChatProtocol

data class OnboardingMessage(val id: Long, val fromUser: Boolean, val text: String)

sealed interface ProviderFailure {
    enum class Field { BaseUrl, Model, ApiKey }

    data class Validation(val field: Field) : ProviderFailure

    data class Network(val error: AppError) : ProviderFailure
}

data class OnboardingUiState(
    val loading: Boolean = true,
    val assistantName: String = "",
    val messages: List<OnboardingMessage> = emptyList(),
    val streamingText: String = "",
    val sending: Boolean = false,
    val input: String = "",
    val providerReady: Boolean = false,
    val providerSheetOpen: Boolean = false,
    val providerName: String = "",
    val baseUrl: String = "",
    val model: String = "",
    val protocol: ChatProtocol = ChatProtocol.ChatCompletions,
    val apiKey: String = "",
    val apiKeyVisible: Boolean = false,
    val testing: Boolean = false,
    val saving: Boolean = false,
    val providerFailure: ProviderFailure? = null,
    val connectionOk: Boolean = false,
    val models: List<String> = emptyList(),
    val fetchingModels: Boolean = false,
    val modelPickerOpen: Boolean = false,
    val modelQuery: String = "",
) : UiState {
    val canSend: Boolean get() = !loading && input.isNotBlank() && !sending
    val canTestProvider: Boolean get() = !testing && !saving && baseUrl.isNotBlank() && model.isNotBlank()
    val canSaveProvider: Boolean get() = !saving && !testing && baseUrl.isNotBlank() && model.isNotBlank()
}

sealed interface OnboardingAction : UiAction {
    data class InputChanged(val value: String) : OnboardingAction
    data object Send : OnboardingAction
    data object Stop : OnboardingAction
    data object Finish : OnboardingAction
    data object OpenProviderSheet : OnboardingAction
    data object CloseProviderSheet : OnboardingAction
    data class ApplyPreset(val id: String) : OnboardingAction
    data class ProviderNameChanged(val value: String) : OnboardingAction
    data class BaseUrlChanged(val value: String) : OnboardingAction
    data class ModelChanged(val value: String) : OnboardingAction
    data class ProtocolChanged(val value: ChatProtocol) : OnboardingAction
    data class ApiKeyChanged(val value: String) : OnboardingAction
    data object ToggleApiKeyVisibility : OnboardingAction
    data object TestProvider : OnboardingAction
    data object SaveProvider : OnboardingAction
    data object FetchModels : OnboardingAction
    data object OpenModelPicker : OnboardingAction
    data object CloseModelPicker : OnboardingAction
    data class ModelQueryChanged(val value: String) : OnboardingAction
    data class SelectModel(val id: String) : OnboardingAction
}

sealed interface OnboardingEffect : UiEffect {
    data object ProviderSaved : OnboardingEffect
}
