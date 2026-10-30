package fan.x0.focus.feature.assistant

import androidx.lifecycle.viewModelScope
import fan.x0.focus.core.error.toAppError
import fan.x0.focus.core.mvi.BaseViewModel
import fan.x0.focus.core.mvi.UiAction
import fan.x0.focus.core.mvi.UiEffect
import fan.x0.focus.core.mvi.UiState
import fan.x0.focus.data.bridge.BridgeEndpoint
import fan.x0.focus.data.bridge.BridgeHealth
import fan.x0.focus.data.bridge.BridgeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

data class BridgeSettingsUiState(
    val loading: Boolean = true,
    val devices: List<BridgeEndpoint> = emptyList(),
    val activeId: String = "",
    val host: String = "",
    val port: String = "7788",
    val code: String = "",
    val pairing: Boolean = false,
    val roots: List<String> = emptyList(),
    val rootDraft: String = "",
    val savingRoots: Boolean = false,
    val health: BridgeHealth? = null,
    val testing: Boolean = false,
    val deleteTarget: BridgeEndpoint? = null,
) : UiState {
    val active: BridgeEndpoint? get() = devices.firstOrNull { it.id == activeId } ?: devices.firstOrNull()
}

sealed interface BridgeSettingsAction : UiAction {
    data class HostChanged(val value: String) : BridgeSettingsAction
    data class PortChanged(val value: String) : BridgeSettingsAction
    data class CodeChanged(val value: String) : BridgeSettingsAction
    data object Pair : BridgeSettingsAction
    data class Select(val id: String) : BridgeSettingsAction
    data class AskForget(val id: String) : BridgeSettingsAction
    data object ConfirmForget : BridgeSettingsAction
    data object CancelForget : BridgeSettingsAction
    data class RootDraftChanged(val value: String) : BridgeSettingsAction
    data object AddRoot : BridgeSettingsAction
    data class RemoveRoot(val path: String) : BridgeSettingsAction
    data object SaveRoots : BridgeSettingsAction
    data object TestHealth : BridgeSettingsAction
}

sealed interface BridgeSettingsEffect : UiEffect {
    data object Paired : BridgeSettingsEffect
    data object Saved : BridgeSettingsEffect
    data object Deleted : BridgeSettingsEffect
}

@KoinViewModel
class BridgeSettingsViewModel(
    private val bridge: BridgeRepository,
) : BaseViewModel<BridgeSettingsAction, BridgeSettingsUiState, BridgeSettingsEffect>(BridgeSettingsUiState()) {

    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            val host = bridge.lastHost().orEmpty()
            val port = bridge.lastPort()
            updateState {
                copy(
                    host = if (this.host.isBlank()) host else this.host,
                    port = if (port != null && this.port == "7788") port.toString() else this.port,
                )
            }
        }
        viewModelScope.launch {
            bridge.endpoints.collect { endpoints ->
                val active = currentState.activeId.takeIf { id ->
                    endpoints.any { it.id == id }
                } ?: endpoints.firstOrNull()?.id.orEmpty()
                updateState {
                    copy(
                        loading = false,
                        devices = endpoints,
                        activeId = active,
                    )
                }
            }
        }
        viewModelScope.launch {
            bridge.activeEndpointId.collect { id ->
                if (!id.isNullOrBlank()) updateState { copy(activeId = id) }
            }
        }
        refresh()
    }

    override fun onAction(action: BridgeSettingsAction) {
        when (action) {
            is BridgeSettingsAction.HostChanged -> updateState { copy(host = action.value, health = null) }
            is BridgeSettingsAction.PortChanged -> updateState { copy(port = action.value, health = null) }
            is BridgeSettingsAction.CodeChanged -> updateState { copy(code = action.value) }
            BridgeSettingsAction.Pair -> pair()
            is BridgeSettingsAction.Select -> select(action.id)
            is BridgeSettingsAction.AskForget -> {
                val target = currentState.devices.firstOrNull { it.id == action.id } ?: return
                updateState { copy(deleteTarget = target) }
            }
            BridgeSettingsAction.ConfirmForget -> forget()
            BridgeSettingsAction.CancelForget -> updateState { copy(deleteTarget = null) }
            is BridgeSettingsAction.RootDraftChanged -> updateState { copy(rootDraft = action.value) }
            BridgeSettingsAction.AddRoot -> addRoot()
            is BridgeSettingsAction.RemoveRoot -> removeRoot(action.path)
            BridgeSettingsAction.SaveRoots -> saveRoots()
            BridgeSettingsAction.TestHealth -> testHealth()
        }
    }

    private fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = launchGuarded(
            onError = { error -> updateState { copy(loading = false) }; emitError(error) },
        ) {
            val endpoints = bridge.endpointsOnce()
            val active = bridge.activeEndpoint()
            val roots = active?.let { runCatching { bridge.workspace(it) }.getOrNull() }.orEmpty()
            updateState {
                copy(
                    loading = false,
                    devices = endpoints,
                    activeId = active?.id.orEmpty(),
                    roots = roots,
                )
            }
        }
    }

    private fun pair() {
        val state = currentState
        val host = state.host.trim()
        val port = state.port.trim().toIntOrNull()
        val code = state.code.trim()
        if (host.isBlank() || port == null || code.isBlank() || state.pairing) return
        updateState { copy(pairing = true) }
        launchGuarded(
            onError = {
                updateState { copy(pairing = false) }
                emitError(it)
            },
        ) {
            val endpoint = bridge.pair(host, port, code, "focus")
            updateState {
                copy(
                    pairing = false,
                    code = "",
                    activeId = endpoint.id,
                    roots = endpoint.workspaceRoots,
                )
            }
            sendEffect(BridgeSettingsEffect.Paired)
            refresh()
        }
    }

    private fun select(id: String) {
        val endpoint = currentState.devices.firstOrNull { it.id == id } ?: return
        launchGuarded(onError = ::emitError) {
            bridge.select(endpoint)
            val roots = runCatching { bridge.workspace(endpoint) }.getOrDefault(emptyList())
            updateState { copy(activeId = id, roots = roots, health = null) }
        }
    }

    private fun forget() {
        val target = currentState.deleteTarget ?: return
        updateState { copy(deleteTarget = null) }
        launchGuarded(onError = ::emitError) {
            bridge.forget(target)
            sendEffect(BridgeSettingsEffect.Deleted)
            refresh()
        }
    }

    private fun addRoot() {
        val path = currentState.rootDraft.trim()
        if (path.isBlank() || currentState.roots.contains(path)) return
        updateState { copy(roots = roots + path, rootDraft = "") }
        saveRoots()
    }

    private fun removeRoot(path: String) {
        updateState { copy(roots = roots.filterNot { it == path }) }
        saveRoots()
    }

    private fun saveRoots() {
        val endpoint = currentState.active ?: return
        if (currentState.savingRoots) return
        updateState { copy(savingRoots = true) }
        launchGuarded(
            onError = {
                updateState { copy(savingRoots = false) }
                emitError(it)
            },
        ) {
            val roots = bridge.setWorkspace(endpoint, currentState.roots)
            updateState { copy(savingRoots = false, roots = roots) }
            sendEffect(BridgeSettingsEffect.Saved)
        }
    }

    private fun testHealth() {
        val host = currentState.host.trim()
        val port = currentState.port.trim().toIntOrNull() ?: return
        if (currentState.testing) return
        updateState { copy(testing = true, health = null) }
        launchGuarded(
            onError = {
                updateState { copy(testing = false) }
                emitError(it)
            },
        ) {
            val health = bridge.health(host, port)
            updateState { copy(testing = false, health = health) }
        }
    }
}
