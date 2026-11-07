package fan.x0.focus.feature.assistant

import androidx.lifecycle.viewModelScope
import fan.x0.focus.core.mvi.BaseViewModel
import fan.x0.focus.core.mvi.UiAction
import fan.x0.focus.core.mvi.UiEffect
import fan.x0.focus.core.mvi.UiState
import fan.x0.focus.data.assistant.SkillCandidate
import fan.x0.focus.data.assistant.SkillDraft
import fan.x0.focus.data.assistant.SkillEntry
import fan.x0.focus.data.assistant.SkillRepository
import fan.x0.focus.data.assistant.SkillSource
import fan.x0.focus.data.assistant.SkillTransfer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import java.util.Locale

data class SkillDraftUi(
    val id: String = "",
    val slug: String = "",
    val name: String = "",
    val description: String = "",
    val body: String = "",
    val enabled: Boolean = true,
    val source: SkillSource = SkillSource.App,
    val location: String = "",
    val readOnly: Boolean = false,
    val files: List<String> = emptyList(),
)

data class SkillsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val skills: List<SkillEntry> = emptyList(),
    val draft: SkillDraftUi? = null,
    val draftError: Boolean = false,
    val deleteConfirm: Boolean = false,
    val busy: Boolean = false,
    val fetching: Boolean = false,
    val fetchProgress: Float? = null,
    val linkOpen: Boolean = false,
    val link: String = "",
    val importOffers: List<SkillCandidate> = emptyList(),
    val importSelected: Set<String> = emptySet(),
) : UiState

sealed interface SkillsAction : UiAction {
    data object Refresh : SkillsAction
    data object New : SkillsAction
    data class Edit(val id: String) : SkillsAction
    data object CloseDraft : SkillsAction
    data class NameChanged(val value: String) : SkillsAction
    data class DescriptionChanged(val value: String) : SkillsAction
    data class BodyChanged(val value: String) : SkillsAction
    data class EnabledChanged(val value: Boolean) : SkillsAction
    data class SetEnabled(val id: String, val enabled: Boolean) : SkillsAction
    data object Save : SkillsAction
    data object RequestDelete : SkillsAction
    data object ConfirmDelete : SkillsAction
    data object CancelDelete : SkillsAction
    data object OpenLink : SkillsAction
    data object CloseLink : SkillsAction
    data class LinkChanged(val value: String) : SkillsAction
    data object ImportLink : SkillsAction
    data class ImportFile(val uri: String) : SkillsAction
    data class ToggleImport(val key: String) : SkillsAction
    data object SelectAllImports : SkillsAction
    data object ClearImports : SkillsAction
    data object CancelImport : SkillsAction
    data object ConfirmImport : SkillsAction
    data object Export : SkillsAction
}

sealed interface SkillsEffect : UiEffect {
    data object Saved : SkillsEffect
    data object Deleted : SkillsEffect
    data class Imported(val count: Int) : SkillsEffect
    data object NothingFound : SkillsEffect
    data class ExportReady(val path: String) : SkillsEffect
    data class Failed(val message: String) : SkillsEffect
}

@KoinViewModel
class SkillsSettingsViewModel(
    private val repository: SkillRepository,
    private val transfer: SkillTransfer,
) : BaseViewModel<SkillsAction, SkillsUiState, SkillsEffect>(SkillsUiState()) {

    private var loadJob: Job? = null

    init {
        load(force = false)
    }

    override fun onAction(action: SkillsAction) {
        when (action) {
            SkillsAction.Refresh -> load(force = true)
            SkillsAction.New -> updateState {
                copy(
                    draft = SkillDraftUi(),
                    draftError = false,
                )
            }
            is SkillsAction.Edit -> edit(action.id)
            SkillsAction.CloseDraft -> updateState { copy(draft = null, draftError = false, deleteConfirm = false) }
            is SkillsAction.NameChanged -> updateDraft { it.copy(name = action.value) }
            is SkillsAction.DescriptionChanged -> updateDraft { it.copy(description = action.value) }
            is SkillsAction.BodyChanged -> updateDraft { it.copy(body = action.value) }
            is SkillsAction.EnabledChanged -> updateDraft { it.copy(enabled = action.value) }
            is SkillsAction.SetEnabled -> setEnabled(action.id, action.enabled)
            SkillsAction.Save -> save()
            SkillsAction.RequestDelete -> updateState { copy(deleteConfirm = true) }
            SkillsAction.ConfirmDelete -> delete()
            SkillsAction.CancelDelete -> updateState { copy(deleteConfirm = false) }
            SkillsAction.OpenLink -> updateState { copy(linkOpen = true, link = "") }
            SkillsAction.CloseLink -> updateState { copy(linkOpen = false, link = "") }
            is SkillsAction.LinkChanged -> updateState { copy(link = action.value) }
            SkillsAction.ImportLink -> importLink()
            is SkillsAction.ImportFile -> importFile(action.uri)
            is SkillsAction.ToggleImport -> toggleImport(action.key)
            SkillsAction.SelectAllImports -> updateState {
                copy(importSelected = importOffers.mapTo(mutableSetOf()) { it.key })
            }
            SkillsAction.ClearImports -> updateState { copy(importSelected = emptySet()) }
            SkillsAction.CancelImport -> if (!currentState.busy) {
                updateState { copy(importOffers = emptyList(), importSelected = emptySet()) }
            }
            SkillsAction.ConfirmImport -> confirmImport()
            SkillsAction.Export -> export()
        }
    }

    private fun load(force: Boolean) {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            val first = currentState.skills.isEmpty()
            updateState { copy(loading = first, refreshing = !first) }
            try {
                val skills = repository.entries(force)
                updateState { copy(loading = false, refreshing = false, skills = skills) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState { copy(loading = false, refreshing = false) }
                sendEffect(SkillsEffect.Failed(e.message ?: e.javaClass.simpleName))
            }
        }
    }

    private fun edit(id: String) {
        val entry = currentState.skills.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            try {
                val content = repository.content(entry)
                if (content == null) {
                    sendEffect(SkillsEffect.Failed("Skill content is unavailable"))
                    return@launch
                }
                updateState {
                    copy(
                        draft = SkillDraftUi(
                            id = entry.id,
                            slug = entry.slug,
                            name = entry.name,
                            description = entry.description,
                            body = content.body,
                            enabled = entry.enabled,
                            source = entry.source,
                            location = entry.location,
                            readOnly = entry.source != SkillSource.App,
                            files = content.files,
                        ),
                        draftError = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                sendEffect(SkillsEffect.Failed(e.message ?: e.javaClass.simpleName))
            }
        }
    }

    private fun save() {
        if (currentState.busy) return
        val draft = currentState.draft ?: return
        if (draft.readOnly) return
        if (draft.name.isBlank() || draft.description.isBlank()) {
            updateState { copy(draftError = true) }
            return
        }
        viewModelScope.launch {
            updateState { copy(busy = true, draftError = false) }
            try {
                repository.save(
                    SkillDraft(
                        slug = draft.slug,
                        name = draft.name,
                        description = draft.description,
                        body = draft.body,
                        enabled = draft.enabled,
                    ),
                )
                val skills = repository.entries()
                updateState { copy(busy = false, skills = skills, draft = null) }
                sendEffect(SkillsEffect.Saved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState { copy(busy = false) }
                sendEffect(SkillsEffect.Failed(e.message ?: e.javaClass.simpleName))
            }
        }
    }

    private fun delete() {
        if (currentState.busy) return
        val draft = currentState.draft ?: return
        if (draft.readOnly) return
        val entry = currentState.skills.firstOrNull { it.id == draft.id } ?: return
        viewModelScope.launch {
            updateState { copy(busy = true) }
            try {
                repository.delete(entry)
                val skills = repository.entries()
                updateState { copy(busy = false, skills = skills, draft = null, deleteConfirm = false) }
                sendEffect(SkillsEffect.Deleted)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState { copy(busy = false, deleteConfirm = false) }
                sendEffect(SkillsEffect.Failed(e.message ?: e.javaClass.simpleName))
            }
        }
    }

    private fun setEnabled(id: String, enabled: Boolean) {
        val entry = currentState.skills.firstOrNull { it.id == id } ?: return
        updateState {
            copy(
                skills = skills.map { if (it.id == id) it.copy(enabled = enabled) else it },
                draft = draft?.let { if (it.id == id) it.copy(enabled = enabled) else it },
            )
        }
        viewModelScope.launch {
            try {
                repository.setEnabled(entry, enabled)
                val skills = repository.entries()
                updateState { copy(skills = skills) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState {
                    copy(
                        skills = skills.map {
                            if (it.id == id) it.copy(enabled = entry.enabled) else it
                        },
                        draft = draft?.let {
                            if (it.id == id) it.copy(enabled = entry.enabled) else it
                        },
                    )
                }
                sendEffect(SkillsEffect.Failed(e.message ?: e.javaClass.simpleName))
            }
        }
    }

    private fun importLink() {
        if (currentState.busy) return
        val link = currentState.link.trim()
        if (link.isBlank()) return
        viewModelScope.launch {
            updateState { copy(busy = true, fetching = true, fetchProgress = null) }
            try {
                val candidates = transfer.previewUrl(link) { progress ->
                    updateState { copy(fetchProgress = progress) }
                }
                showOffers(candidates, closeLink = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState { copy(busy = false, fetching = false, fetchProgress = null) }
                sendEffect(SkillsEffect.Failed(e.message ?: e.javaClass.simpleName))
            }
        }
    }

    private fun importFile(uri: String) {
        if (currentState.busy) return
        viewModelScope.launch {
            updateState { copy(busy = true) }
            try {
                showOffers(transfer.previewFile(uri), closeLink = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState { copy(busy = false) }
                sendEffect(SkillsEffect.Failed(e.message ?: e.javaClass.simpleName))
            }
        }
    }

    private fun showOffers(candidates: List<SkillCandidate>, closeLink: Boolean) {
        if (candidates.isEmpty()) {
            updateState {
                copy(
                    busy = false,
                    fetching = false,
                    fetchProgress = null,
                    linkOpen = if (closeLink) false else linkOpen,
                    link = if (closeLink) "" else link,
                )
            }
            sendEffect(SkillsEffect.NothingFound)
            return
        }
        val installed = currentState.skills
            .filter { it.source == SkillSource.App }
            .mapTo(mutableSetOf()) { it.name.lowercase(Locale.ROOT) }
        val offers = candidates.map {
            it.copy(installed = it.name.lowercase(Locale.ROOT) in installed)
        }
        updateState {
            copy(
                busy = false,
                fetching = false,
                fetchProgress = null,
                importOffers = offers,
                importSelected = offers.filterNot { it.installed }
                    .mapTo(mutableSetOf()) { it.key },
                linkOpen = if (closeLink) false else linkOpen,
                link = if (closeLink) "" else link,
            )
        }
    }

    private fun toggleImport(key: String) {
        updateState {
            val selected = if (key in importSelected) importSelected - key else importSelected + key
            copy(importSelected = selected)
        }
    }

    private fun confirmImport() {
        if (currentState.busy) return
        val chosen = currentState.importOffers.filter { it.key in currentState.importSelected }
        if (chosen.isEmpty()) return
        viewModelScope.launch {
            updateState { copy(busy = true) }
            try {
                val report = transfer.install(chosen)
                val skills = repository.entries()
                updateState {
                    copy(
                        busy = false,
                        skills = skills,
                        importOffers = emptyList(),
                        importSelected = emptySet(),
                    )
                }
                sendEffect(SkillsEffect.Imported(report.imported.size))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState { copy(busy = false) }
                sendEffect(SkillsEffect.Failed(e.message ?: e.javaClass.simpleName))
            }
        }
    }

    private fun export() {
        if (currentState.busy) return
        viewModelScope.launch {
            updateState { copy(busy = true) }
            try {
                val file = transfer.exportZip()
                updateState { copy(busy = false) }
                sendEffect(SkillsEffect.ExportReady(file.absolutePath))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState { copy(busy = false) }
                sendEffect(SkillsEffect.Failed(e.message ?: e.javaClass.simpleName))
            }
        }
    }

    private fun updateDraft(reduce: (SkillDraftUi) -> SkillDraftUi) {
        val draft = currentState.draft ?: return
        updateState { copy(draft = reduce(draft), draftError = false) }
    }
}
