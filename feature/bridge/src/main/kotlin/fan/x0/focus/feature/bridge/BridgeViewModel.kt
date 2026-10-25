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
    private val connections = mutableMapOf<String, DeviceConnection>()
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
            is BridgeAction.TerminalInputChanged -> setTerminalInput(action.value)
            BridgeAction.SendTerminalInput -> {
                val raw = activeConnection()?.input.orEmpty()
                setTerminalInput("")
                sendInput("$raw\r")
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
            if (active != null) {
                val serverRoots = runCatching { bridge.workspace(active) }.getOrNull()
                if (serverRoots != null) {
                    updateState { copy(roots = serverRoots) }
                }
            }
            syncActive()
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
        val endpoint = endpointFor(id) ?: return
        launchGuarded(onError = ::emitError) {
            bridge.select(endpoint)
            val roots = runCatching { bridge.workspace(endpoint) }.getOrDefault(emptyList())
            updateState {
                copy(
                    devices = devices.map { it.copy(active = it.id == id) },
                    roots = roots,
                )
            }
            syncActive()
        }
    }

    private fun forget() {
        val target = currentState.deleteTarget ?: return
        updateState { copy(deleteTarget = null) }
        val endpoint = endpointFor(target.id) ?: return
        launchGuarded(onError = ::emitError) {
            bridge.forget(endpoint)
            dropConnection(target.id)
            endpoints = bridge.endpointsOnce()
            updateState {
                copy(
                    loading = false,
                    devices = endpoints.map { it.toUi(null) },
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
        val deviceId = activeDeviceId() ?: return
        val endpoint = endpointFor(deviceId) ?: return
        if (currentState.sessionsLoading) return
        updateState { copy(sessionsLoading = true) }
        launchGuarded(
            onError = {
                updateState { copy(sessionsLoading = false) }
                emitError(it)
            },
        ) {
            val list = bridge.sessions(endpoint)
            val connection = connection(deviceId)
            connection.sessions = list.map {
                BridgeSessionUi(
                    id = it.id,
                    name = it.name.ifBlank { it.shell },
                    shell = it.shell,
                    cols = it.cols,
                    rows = it.rows,
                )
            }
            updateState { copy(sessionsLoading = false) }
            syncActive()
        }
    }

    private fun newSession() {
        val deviceId = activeDeviceId() ?: return
        val endpoint = endpointFor(deviceId) ?: return
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
            val connection = connection(deviceId)
            connection.sessions = connection.sessions + session
            updateState { copy(tab = BridgeTab.Terminal) }
            openSession(session.id)
        }
    }

    private fun openSession(id: String) {
        val deviceId = activeDeviceId() ?: return
        val endpoint = endpointFor(deviceId) ?: return
        val connection = connection(deviceId)
        val session = connection.sessions.firstOrNull { it.id == id } ?: return
        updateState { copy(tab = BridgeTab.Terminal) }
        if (connection.opened?.id == id && connection.terminal != null) {
            syncActive()
            return
        }
        if (connection.attachJob?.isActive == true) return
        detachTerminal(connection)
        connection.opened = session
        connection.output = ""
        connection.input = ""
        connection.connecting = true
        syncActive()
        connection.attachJob = launchGuarded(onError = ::emitError) {
            val token = bridge.token(endpoint)
            if (token == null) {
                connection.connecting = false
                connection.opened = null
                syncActive()
                return@launchGuarded
            }
            val terminal = bridge.attach(endpoint, token, id, session.cols, session.rows)
            connection.terminal = terminal
            connection.job = viewModelScope.launch {
                terminal.output().collect { event -> onTerminalEvent(deviceId, event) }
            }
            syncActive()
        }
    }

    private fun onTerminalEvent(deviceId: String, event: BridgeTerminalEvent) {
        val connection = connection(deviceId)
        when (event) {
            is BridgeTerminalEvent.Output -> appendOutput(connection, deviceId, event.text)
            BridgeTerminalEvent.Closed -> {
                connection.connecting = false
                connection.terminal = null
                publish(deviceId)
            }
            is BridgeTerminalEvent.Failed -> {
                connection.connecting = false
                connection.terminal = null
                publish(deviceId)
                emitError(event.error.toAppError())
            }
        }
    }

    private fun appendOutput(connection: DeviceConnection, deviceId: String, text: String) {
        if (text.isEmpty()) return
        val merged = connection.output + text
        connection.output = if (merged.length > MAX_TERMINAL_CHARS) {
            merged.substring(merged.length - MAX_TERMINAL_CHARS)
        } else {
            merged
        }
        connection.connecting = false
        publish(deviceId)
    }

    private fun setTerminalInput(value: String) {
        val deviceId = activeDeviceId() ?: return
        connection(deviceId).input = value
        syncActive()
    }

    private fun sendInput(raw: String) {
        val terminal = activeConnection()?.terminal ?: return
        if (raw.isEmpty()) return
        viewModelScope.launch {
            runCatching { terminal.send(raw) }
        }
    }

    private fun sendKey(key: TerminalKey) {
        val terminal = activeConnection()?.terminal ?: return
        val sequence = when (key) {
            TerminalKey.Escape -> "\u001b"
            TerminalKey.Tab -> "\t"
            TerminalKey.Enter -> "\r"
            TerminalKey.ControlC -> "\u0003"
            TerminalKey.ControlD -> "\u0004"
        }
        viewModelScope.launch {
            runCatching { terminal.send(sequence) }
        }
    }

    private fun closeSession(id: String) {
        val deviceId = activeDeviceId() ?: return
        val endpoint = endpointFor(deviceId) ?: return
        launchGuarded(onError = ::emitError) {
            bridge.closeSession(endpoint, id)
            val connection = connection(deviceId)
            if (connection.opened?.id == id) {
                detachTerminal(connection)
                connection.opened = null
                connection.output = ""
                connection.input = ""
                connection.connecting = false
            }
            connection.sessions = connection.sessions.filterNot { it.id == id }
            syncActive()
        }
    }

    private fun closeTerminal() {
        val deviceId = activeDeviceId() ?: return
        val connection = connection(deviceId)
        detachTerminal(connection)
        connection.opened = null
        connection.output = ""
        connection.input = ""
        connection.connecting = false
        syncActive()
    }

    private fun detachTerminal(connection: DeviceConnection) {
        connection.attachJob?.cancel()
        connection.attachJob = null
        connection.job?.cancel()
        connection.job = null
        connection.terminal?.close()
        connection.terminal = null
    }

    private fun dropConnection(deviceId: String) {
        connections.remove(deviceId)?.let { detachTerminal(it) }
    }

    private fun publish(deviceId: String) {
        if (deviceId == activeDeviceId()) {
            syncActive()
        } else {
            updateState { copy(liveDevices = liveDeviceIds()) }
        }
    }

    private fun syncActive() {
        val connection = activeConnection()
        updateState {
            copy(
                sessions = connection?.sessions.orEmpty(),
                opened = connection?.opened,
                terminalOutput = connection?.output.orEmpty(),
                terminalInput = connection?.input.orEmpty(),
                connecting = connection?.connecting ?: false,
                liveDevices = liveDeviceIds(),
            )
        }
    }

    private fun liveDeviceIds(): Set<String> =
        connections.filterValues { it.terminal != null }.keys.toSet()

    private fun connection(deviceId: String): DeviceConnection =
        connections.getOrPut(deviceId) { DeviceConnection() }

    private fun activeConnection(): DeviceConnection? =
        activeDeviceId()?.let { connection(it) }

    private fun endpointFor(id: String?): BridgeEndpoint? =
        endpoints.firstOrNull { it.id == id }

    private fun activeDeviceId(): String? = currentState.devices.firstOrNull { it.active }?.id

    private fun activeEndpoint(): BridgeEndpoint? = endpointFor(activeDeviceId())

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

private class DeviceConnection {
    var sessions: List<BridgeSessionUi> = emptyList()
    var opened: BridgeSessionUi? = null
    var output: String = ""
    var input: String = ""
    var connecting: Boolean = false
    var terminal: BridgeTerminal? = null
    var job: Job? = null
    var attachJob: Job? = null
}
