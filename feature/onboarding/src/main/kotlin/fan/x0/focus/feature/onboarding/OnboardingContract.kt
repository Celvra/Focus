package fan.x0.focus.feature.onboarding

import fan.x0.focus.core.mvi.UiAction
import fan.x0.focus.core.mvi.UiEffect
import fan.x0.focus.core.mvi.UiState

enum class OnboardingPhase { Connect, Chat }

enum class ConnectFailureKind { Streaming, Tools, Models, Generic }

data class ConnectFailure(val kind: ConnectFailureKind, val detail: String? = null)

data class OnboardingMessage(val id: Long, val fromUser: Boolean, val text: String)

data class OnboardingUiState(
    val loading: Boolean = true,
    val phase: OnboardingPhase = OnboardingPhase.Connect,
    val baseUrl: String = "",
    val model: String = "",
    val apiKey: String = "",
    val apiKeySaved: Boolean = false,
    val apiKeyVisible: Boolean = false,
    val testing: Boolean = false,
    val failure: ConnectFailure? = null,
    val messages: List<OnboardingMessage> = emptyList(),
    val streamingText: String = "",
    val sending: Boolean = false,
    val input: String = "",
    val avatarSeed: Int = 0,
    val models: List<String> = emptyList(),
    val fetchingModels: Boolean = false,
    val modelPickerOpen: Boolean = false,
    val modelQuery: String = "",
) : UiState

sealed interface OnboardingAction : UiAction {
    data class BaseUrlChanged(val value: String) : OnboardingAction
    data class ModelChanged(val value: String) : OnboardingAction
    data class ApiKeyChanged(val value: String) : OnboardingAction
    data object ToggleApiKeyVisibility : OnboardingAction
    data object Connect : OnboardingAction
    data class InputChanged(val value: String) : OnboardingAction
    data object Send : OnboardingAction
    data object Skip : OnboardingAction
    data object FetchModels : OnboardingAction
    data object OpenModelPicker : OnboardingAction
    data object CloseModelPicker : OnboardingAction
    data class ModelQueryChanged(val value: String) : OnboardingAction
    data class SelectModel(val id: String) : OnboardingAction
}

sealed interface OnboardingEffect : UiEffect