package fan.x0.focus.feature.assistant

import fan.x0.focus.core.mvi.UiAction
import fan.x0.focus.core.mvi.UiEffect
import fan.x0.focus.core.mvi.UiState
import fan.x0.focus.data.assistant.WorkspaceFile

data class WorkspaceDocumentUi(
    val file: WorkspaceFile,
    val content: String,
) {
    val fileName: String get() = file.fileName
}

data class AssistantUiState(
    val loading: Boolean = true,
    val assistantName: String = "",
    val documents: List<WorkspaceDocumentUi> = emptyList(),
    val openDocument: WorkspaceDocumentUi? = null,
) : UiState

sealed interface AssistantAction : UiAction {
    data class OpenDocument(val file: WorkspaceFile) : AssistantAction
    data object CloseDocument : AssistantAction
}

sealed interface AssistantEffect : UiEffect
