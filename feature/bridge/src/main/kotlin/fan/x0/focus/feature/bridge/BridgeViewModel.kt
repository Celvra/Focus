package fan.x0.focus.feature.bridge

import androidx.lifecycle.viewModelScope
import fan.x0.focus.core.error.toAppError
import fan.x0.focus.core.mvi.BaseViewModel
import fan.x0.focus.data.bridge.BridgeEndpoint
import fan.x0.focus.data.bridge.BridgeRepository
import fan.x0.focus.data.bridge.BridgeTerminal
import fan.x0.focus.data.bridge.BridgeTerminalEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class BridgeViewModel(
    private val bridge: BridgeRepository,
) : BaseViewModel<BridgeAction, BridgeUiState, BridgeEffect>(BridgeUiState()) {

    private var refreshJob: Job? = null
    private var terminal: BridgeTerminal? = null
    private var terminalJob: Job? = null
    private var endpoints: List<BridgeEndpoint> = emptyList()

    init {
        refresh()
        launchGuarded(onError = {}) {
            val host = bridge.lastHost()
            val port = bridge.lastPort()
            updateState {
                copy(
                    host = host.orEmpty(),
                    port = (port ?: 7788).toString(),
                )
            }
        }
    }

    override fun onAction(action: BridgeAction) {
        when (action) {
            is BridgeAction.TabChanged -> updateState { copy(tab = action.tab) }
            BridgeAction.Refresh -> refresh()
            BridgeAction.Scan -> scan()
            is BridgeAction.UseDiscovered -> updateState {
                copy(host = action.host, port = action.port.toString(), addOpen = true, code = "")
            }
            BridgeAction.OpenAdd -> updateState { copy(addOpen = true, code = "") }
            BridgeAction.DismissAdd -> updateState { copy(addOpen = false, code = "") }
            is BridgeAction.HostChanged -> updateState { copy(host = action.value) }
            is BridgeAction.PortChanged -> updateState { copy(port = action.value) }
            is BridgeAction.CodeChanged -> updateState { copy(code = action.value) }
            BridgeAction.SubmitPair -> pair()
            is BridgeAction.SelectDevice -> select(action.id)
            is BridgeAction.AskForgetDevice -> {
                val target = currentState.devices.firstOrNull { it.id == action.id } ?: return
                updateState { copy(deleteTarget = target) }
            }
            BridgeAction.ConfirmForget -> forget()
            BridgeAction.CancelForget -> updateState { copy(deleteTarget = null) }
            is BridgeAction.RootDraftChanged -> updateState { copy(rootDraft = action.value) }
            BridgeAction.AddRoot -> addRoot()
            is BridgeAction.RemoveRoot -> removeRoot(action.path)
            BridgeAction.SaveRoots -> {
                val endpoint = activeEndpoint() ?: return
                saveRoots(endpoint)
            }
            BridgeAction.LoadSessions -> loadSessions()
            BridgeAction.NewSession -> newSession()
            is BridgeAction.OpenSession -> openSession(action.id)
            is BridgeAction.CloseSession -> closeSession(action.id)
            BridgeAction.CloseTerminal -> closeTerminal()
            is BridgeAction.TerminalInputChanged -> updateState { copy(terminalInput = action.value) }
            BridgeAction.SendTerminalInput -> {
                val raw = currentState.terminalInput
                updateState { copy(terminalInput = "") }
                sendInput(raw)
            }
            is BridgeAction.SendTerminalKey -> sendKey(action.key)
        }
    }

    private fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = launchGuarded(onError = {}) {
            val list = bridge.endpointsOnce()
            endpoints = list
            val active = bridge.activeEndpoint()
            updateState {
                copy(
                    loading = false,
                    devices = list.map { it.toUi(active?.id) },
                    roots = active?.workspaceRoots.orEmpty(),
                )
            }
            val activeEndpoint = list.firstOrNull { it.id == active?.id }
            if (activeEndpoint != null) {
                val serverRoots = runCatching { bridge.workspace(activeEndpoint) }.getOrNull()
                if (serverRoots != null) {
                    updateState { copy(roots = serverRoots) }
                }
            }
        }
    }

    private fun scan() {
        if (currentState.scanning) return
        updateState { copy(scanning = true) }
        launchGuarded(
            onError = { error ->
                updateState { copy(scanning = false) }
                emitError(error)
            },
            onComplete = { updateState { copy(scanning = false) } },
        ) {
            val found = bridge.scanLan()
            updateState {
                copy(
                    discovered = found.map {
                        BridgeDiscoveredUi(
                            host = it.host,
                            port = it.port,
                            name = it.name,
                            os = it.os,
                            arch = it.arch,
                        )
                    },
                )
            }
        }
    }

    private fun pair() {
        val state = currentState
        val host = state.host.trim()
        val port = state.port.trim().toIntOrNull()
        val code = state.code.trim()
        if (host.isEmpty() || port == null || code.isEmpty() || state.pairing) {
            return
        }
        updateState { copy(pairing = true) }
        launchGuarded(
            onError = {
                updateState { copy(pairing = false) }
                emitError(it)
            },
        ) {
            val endpoint = bridge.pair(host, port, code, "focus")
            endpoints = bridge.endpointsOnce()
            updateState {
                copy(
                    pairing = false,
                    addOpen = false,
                    code = "",
                    host = host,
                    port = port.toString(),
                    loading = false,
                    roots = endpoint.workspaceRoots,
                )
            }
            refresh()
        }
    }

    private fun select(id: String) {
        val endpoint = bridgeEndpointFor(id) ?: return
        closeTerminal()
        launchGuarded(onError = ::emitError) {
            bridge.select(endpoint)
            val roots = runCatching { bridge.workspace(endpoint) }.getOrDefault(emptyList())
            updateState {
                copy(
                    devices = devices.map { it.copy(active = it.id == id) },
                    roots = roots,
                    sessions = emptyList(),
                )
            }
        }
    }

    private fun forget() {
        val target = currentState.deleteTarget ?: return
        updateState { copy(deleteTarget = null) }
        val endpoint = bridgeEndpointFor(target.id) ?: return
        launchGuarded(onError = ::emitError) {
            bridge.forget(endpoint)
            closeTerminal()
            endpoints = bridge.endpointsOnce()
            updateState {
                copy(
                    loading = false,
                    devices = endpoints.map { it.toUi(null) },
                    sessions = emptyList(),
                    roots = emptyList(),
                )
            }
            refresh()
        }
    }

    private fun addRoot() {
        val path = currentState.rootDraft.trim()
        if (path.isEmpty()) return
        if (currentState.roots.contains(path)) {
            updateState { copy(rootDraft = "") }
            return
        }
        updateState { copy(roots = roots + path, rootDraft = "") }
        activeEndpoint()?.let { saveRoots(it) }
    }

    private fun removeRoot(path: String) {
        val endpoint = activeEndpoint() ?: return
        updateState { copy(roots = roots.filterNot { it == path }) }
        saveRoots(endpoint)
    }

    private fun saveRoots(endpoint: BridgeEndpoint) {
        if (currentState.savingRoots) return
        updateState { copy(savingRoots = true) }
        launchGuarded(
            onError = {
                updateState { copy(savingRoots = false) }
                emitError(it)
            },
        ) {
            val roots = bridge.setWorkspace(endpoint, currentState.roots)
            updateState {
                copy(
                    savingRoots = false,
                    roots = roots,
                    devices = devices.map {
                        if (it.id == endpoint.id) it.copy(roots = roots) else it
                    },
                )
            }
        }
    }

    private fun loadSessions() {
        val endpoint = activeEndpoint() ?: return
        if (currentState.sessionsLoading) return
        updateState { copy(sessionsLoading = true) }
        launchGuarded(
            onError = {
                updateState { copy(sessionsLoading = false) }
                emitError(it)
            },
        ) {
            val list = bridge.sessions(endpoint)
            updateState {
                copy(
                    sessionsLoading = false,
                    sessions = list.map {
                        BridgeSessionUi(
                            id = it.id,
                            name = it.name.ifBlank { it.shell },
                            shell = it.shell,
                            cols = it.cols,
                            rows = it.rows,
                        )
                    },
                )
            }
        }
    }

    private fun newSession() {
        val endpoint = activeEndpoint() ?: return
        launchGuarded(onError = ::emitError) {
            val cwd = currentState.roots.firstOrNull()
            val info = bridge.openSession(endpoint, cwd = cwd, cols = 100, rows = 30)
            val session = BridgeSessionUi(
                id = info.id,
                name = info.name.ifBlank { info.shell },
                shell = info.shell,
                cols = info.cols,
                rows = info.rows,
            )
            updateState {
                copy(
                    sessions = sessions + session,
                    tab = BridgeTab.Terminal,
                )
            }
            openSession(session.id)
        }
    }

    private fun openSession(id: String) {
        val endpoint = activeEndpoint() ?: return
        val session = currentState.sessions.firstOrNull { it.id == id } ?: return
        closeTerminal()
        updateState {
            copy(
                opened = session,
                terminalOutput = "",
                terminalInput = "",
                connecting = true,
                tab = BridgeTab.Terminal,
            )
        }
        launchGuarded(onError = ::emitError) {
            val token = bridge.token(endpoint)
            if (token == null) {
                updateState { copy(connecting = false, opened = null) }
                return@launchGuarded
            }
            val terminal = bridge.attach(endpoint, token, id, session.cols, session.rows)
            this.terminal = terminal
            terminalJob = viewModelScope.launch {
                terminal.output().collect { event ->
                    when (event) {
                        is BridgeTerminalEvent.Output -> appendOutput(event.text)
                        BridgeTerminalEvent.Closed -> updateState { copy(connecting = false) }
                        is BridgeTerminalEvent.Failed -> {
                            updateState { copy(connecting = false) }
                            emitError(event.error.toAppError())
                        }
                    }
                }
            }
        }
    }

    private fun appendOutput(text: String) {
        if (text.isEmpty()) return
        val merged = currentState.terminalOutput + text
        val trimmed = if (merged.length > MAX_TERMINAL_CHARS) {
            merged.substring(merged.length - MAX_TERMINAL_CHARS)
        } else {
            merged
        }
        updateState { copy(terminalOutput = trimmed, connecting = false) }
    }

    private fun sendInput(raw: String) {
        val terminal = terminal ?: return
        if (raw.isEmpty()) return
        viewModelScope.launch {
            runCatching { terminal.send(raw) }
        }
    }

    private fun sendKey(key: TerminalKey) {
        val terminal = terminal ?: return
        val sequence = when (key) {
            TerminalKey.Escape -> "\u001b"
            TerminalKey.Tab -> "\t"
            TerminalKey.ControlC -> "\u0003"
            TerminalKey.ControlD -> "\u0004"
        }
        viewModelScope.launch {
            runCatching { terminal.send(sequence) }
        }
    }

    private fun closeSession(id: String) {
        val endpoint = activeEndpoint() ?: return
        launchGuarded(onError = ::emitError) {
            bridge.closeSession(endpoint, id)
            if (currentState.opened?.id == id) {
                closeTerminal()
            }
            updateState { copy(sessions = sessions.filterNot { it.id == id }) }
        }
    }

    private fun closeTerminal() {
        terminal?.close()
        terminal = null
        terminalJob?.cancel()
        terminalJob = null
        updateState { copy(opened = null, terminalOutput = "", terminalInput = "") }
    }

    private fun bridgeEndpointFor(id: String?): BridgeEndpoint? =
        endpoints.firstOrNull { it.id == id }

    private fun activeEndpoint(): BridgeEndpoint? {
        val activeId = currentState.devices.firstOrNull { it.active }?.id ?: return null
        return bridgeEndpointFor(activeId)
    }

    private fun BridgeEndpoint.toUi(activeId: String?): BridgeDeviceUi = BridgeDeviceUi(
        id = id,
        name = name,
        host = host,
        port = port,
        os = os,
        arch = arch,
        version = version,
        roots = workspaceRoots,
        active = id == activeId,
    )

    private companion object {
        const val MAX_TERMINAL_CHARS = 120_000
    }
}
