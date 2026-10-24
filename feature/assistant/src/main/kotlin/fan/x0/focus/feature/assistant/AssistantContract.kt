package fan.x0.focus.feature.assistant

import fan.x0.focus.core.error.AppError
import fan.x0.focus.core.mvi.UiAction
import fan.x0.focus.core.mvi.UiEffect
import fan.x0.focus.core.mvi.UiState

data class MemoryUi(
    val id: String,
    val content: String,
)

data class AssistantUiState(
    val loading: Boolean = true,
    val name: String = "",
    val tone: String = "",
    val focus: String = "",
    val memories: List<MemoryUi> = emptyList(),
    val deleteMemory: MemoryUi? = null,
) : UiState

sealed interface AssistantAction : UiAction {
    data class AskDeleteMemory(val id: String) : AssistantAction
    data object ConfirmDeleteMemory : AssistantAction
    data object CancelDeleteMemory : AssistantAction
}

sealed interface AssistantEffect : UiEffect
