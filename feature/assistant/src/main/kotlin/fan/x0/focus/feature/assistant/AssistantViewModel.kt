package fan.x0.focus.feature.assistant

import androidx.lifecycle.viewModelScope
import fan.x0.focus.core.error.toAppError
import fan.x0.focus.core.mvi.BaseViewModel
import fan.x0.focus.data.assistant.PromptSettings
import fan.x0.focus.data.assistant.WorkspaceStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class AssistantViewModel(
    private val workspace: WorkspaceStore,
    private val prompts: PromptSettings,
) : BaseViewModel<AssistantAction, AssistantUiState, AssistantEffect>(AssistantUiState()) {

    init {
        viewModelScope.launch {
            workspace.revision.collect { load() }
        }
        viewModelScope.launch {
            prompts.config.collect { config ->
                updateState { copy(mainPrompt = config.main, sidePrompt = config.side) }
            }
        }
    }

    private suspend fun load() {
        try {
            val documents = workspace.readAll()
            val assistantName = workspace.assistantName()
            updateState {
                copy(
                    loading = false,
                    assistantName = assistantName,
                    documents = documents.map { WorkspaceDocumentUi(it.file, it.content) },
                    openDocument = openDocument?.let { open ->
                        documents.firstOrNull { it.file == open.file }
                            ?.let { WorkspaceDocumentUi(it.file, it.content) }
                    },
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            updateState { copy(loading = false) }
            emitError(e.toAppError())
        }
    }

    override fun onAction(action: AssistantAction) {
        when (action) {
            is AssistantAction.OpenDocument -> updateState {
                copy(openDocument = documents.firstOrNull { it.file == action.file })
            }
            AssistantAction.CloseDocument -> updateState { copy(openDocument = null) }
            is AssistantAction.OpenPrompt -> updateState {
                copy(
                    promptEditor = PromptEditorUi(
                        target = action.target,
                        value = when (action.target) {
                            PromptTarget.Main -> mainPrompt
                            PromptTarget.Side -> sidePrompt
                        },
                    ),
                )
            }
            is AssistantAction.PromptChanged -> updateState {
                copy(
                    promptEditor = promptEditor?.copy(
                        value = action.value.take(PromptSettings.MAX_PROMPT_CHARS),
                    ),
                )
            }
            AssistantAction.ClosePrompt -> updateState { copy(promptEditor = null) }
            AssistantAction.SavePrompt -> savePrompt()
        }
    }

    private fun savePrompt() {
        val editor = currentState.promptEditor ?: return
        if (currentState.promptSaving) return
        updateState { copy(promptSaving = true) }
        launchGuarded(
            onError = { updateState { copy(promptSaving = false) } },
        ) {
            val value = editor.value.trim()
            when (editor.target) {
                PromptTarget.Main -> prompts.setMain(value)
                PromptTarget.Side -> prompts.setSide(value)
            }
            updateState { copy(promptSaving = false, promptEditor = null) }
            sendEffect(AssistantEffect.PromptSaved)
        }
    }
}
