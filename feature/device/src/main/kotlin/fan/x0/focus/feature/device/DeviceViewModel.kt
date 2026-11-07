package fan.x0.focus.feature.device

import androidx.lifecycle.viewModelScope
import fan.x0.focus.core.error.toAppError
import fan.x0.focus.core.mvi.BaseViewModel
import fan.x0.focus.data.device.DeviceRepository
import fan.x0.focus.data.device.DeviceRole
import fan.x0.focus.data.device.DeviceRuntime
import fan.x0.focus.data.device.DeviceServerController
import fan.x0.focus.data.device.display.VirtualDisplaySpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class DeviceViewModel(
    private val repository: DeviceRepository,
    private val runtime: DeviceRuntime,
    private val server: DeviceServerController,
) : BaseViewModel<DeviceAction, DeviceUiState, DeviceEffect>(DeviceUiState()) {

    private var liveJob: Job? = null

    init {
        repository.localEnabled.onEach { value ->
            updateState { copy(localEnabled = value) }
        }.launchIn(viewModelScope)
        repository.remoteEnabled.onEach { value ->
            updateState { copy(remoteEnabled = value) }
        }.launchIn(viewModelScope)
        repository.controllers.onEach { value ->
            updateState { copy(controllers = value) }
        }.launchIn(viewModelScope)
        repository.endpoints.onEach { value ->
            updateState { copy(endpoints = value) }
        }.launchIn(viewModelScope)
        repository.activeEndpointId.onEach { value ->
            updateState { copy(activeEndpointId = value.orEmpty()) }
        }.launchIn(viewModelScope)
        repository.triggers.onEach { value ->
            updateState { copy(triggers = value) }
        }.launchIn(viewModelScope)
        server.running.onEach { value ->
            updateState { copy(serverRunning = value) }
        }.launchIn(viewModelScope)
        server.pairingCode.onEach { value ->
            updateState { copy(pairingCode = value.orEmpty()) }
        }.launchIn(viewModelScope)
        runtime.session.onEach { session ->
            updateState { copy(session = session != null, sessionOwner = session?.owner.orEmpty()) }
        }.launchIn(viewModelScope)
        runtime.displayState.onEach { state ->
            updateState {
                copy(
                    displayRunning = state.running,
                    displayStarting = state.starting,
                    displayId = state.displayId,
                    displayWidth = state.width,
                    displayHeight = state.height,
                    displayDensity = state.density,
                    displayBackend = state.backend.name.lowercase(),
                    displayTrusted = state.trusted,
                    displayError = state.error,
                )
            }
        }.launchIn(viewModelScope)
        runtime.displayPreview.onEach { image ->
            if (currentState.displayPreviewing) {
                updateState { copy(displayPreview = image) }
            }
        }.launchIn(viewModelScope)
        refresh()
    }

    override fun onAction(action: DeviceAction) {
        when (action) {
            is DeviceAction.TabChanged -> {
                stopLive()
                updateState { copy(tab = action.tab, preview = null) }
            }
            DeviceAction.Refresh -> refresh(force = true)
            is DeviceAction.LocalEnabledChanged -> setLocalEnabled(action.value)
            is DeviceAction.RemoteEnabledChanged -> setRemoteEnabled(action.value)
            DeviceAction.OpenAccessibility -> sendEffect(DeviceEffect.OpenAccessibility)
            DeviceAction.Preview -> preview()
            is DeviceAction.ToggleLive -> toggleLive(action.id)
            DeviceAction.StartLocal -> startLocal()
            DeviceAction.StopSession -> stopSession()
            DeviceAction.StartDisplay -> startDisplay()
            DeviceAction.StopDisplay -> stopDisplay()
            DeviceAction.ToggleDisplayPreview -> toggleDisplayPreview()
            DeviceAction.NewPairingCode -> {
                val code = server.newPairingCode()
                updateState { copy(pairingCode = code) }
            }
            DeviceAction.UseFullMode -> launchGuarded(onError = ::emitError) {
                stopLive()
                repository.setRole(DeviceRole.Full)
                sendEffect(DeviceEffect.Message(R.string.device_msg_full_mode))
            }
            DeviceAction.RequestShizuku -> {
                if (runtime.requestShizukuPermission()) {
                    sendEffect(DeviceEffect.Message(R.string.device_msg_shizuku_requested))
                } else {
                    sendEffect(DeviceEffect.Message(R.string.device_msg_shizuku_missing))
                }
            }
            is DeviceAction.RevokeController -> revoke(action.id)
            DeviceAction.OpenPair -> updateState { copy(pairOpen = true, code = "") }
            DeviceAction.ClosePair -> updateState { copy(pairOpen = false, code = "") }
            is DeviceAction.HostChanged -> updateState { copy(host = action.value) }
            is DeviceAction.PortChanged -> updateState { copy(portText = action.value) }
            is DeviceAction.CodeChanged -> updateState { copy(code = action.value) }
            DeviceAction.Pair -> pair()
            is DeviceAction.SelectDevice -> select(action.id)
            is DeviceAction.ForgetDevice -> forget(action.id)
            is DeviceAction.StartRemote -> startRemote(action.id)
            is DeviceAction.StopRemote -> stopRemote(action.id)
            DeviceAction.OpenTrigger -> updateState {
                copy(
                    triggerOpen = true,
                    triggerName = "",
                    triggerPackage = "",
                    triggerText = "",
                    triggerType = "",
                    triggerAction = "shell",
                    triggerCommand = "",
                    triggerCooldown = "30",
                )
            }
            DeviceAction.CloseTrigger -> updateState { copy(triggerOpen = false) }
            is DeviceAction.TriggerNameChanged -> updateState { copy(triggerName = action.value) }
            is DeviceAction.TriggerPackageChanged -> updateState { copy(triggerPackage = action.value) }
            is DeviceAction.TriggerTextChanged -> updateState { copy(triggerText = action.value) }
            is DeviceAction.TriggerTypeChanged -> updateState { copy(triggerType = action.value) }
            is DeviceAction.TriggerActionChanged -> updateState { copy(triggerAction = action.value) }
            is DeviceAction.TriggerCommandChanged -> updateState { copy(triggerCommand = action.value) }
            is DeviceAction.TriggerCooldownChanged -> updateState { copy(triggerCooldown = action.value) }
            DeviceAction.SaveTrigger -> saveTrigger()
            is DeviceAction.ToggleTrigger -> toggleTrigger(action.id, action.enabled)
            is DeviceAction.DeleteTrigger -> deleteTrigger(action.id)
        }
    }

    private fun refresh(force: Boolean = false) {
        launchGuarded(
            onError = { emitError(it) },
            onComplete = { updateState { copy(loading = false) } },
        ) {
            if (force) runtime.refresh()
            val health = runtime.health()
            val remoteEnabled = repository.remoteEnabledOnce()
            if (remoteEnabled && !server.running.value) {
                server.start()
                if (server.pairingCode.value.isNullOrBlank()) server.newPairingCode()
            }
            val endpoints = repository.endpointsOnce()
            val controllers = repository.controllersOnce()
            val active = repository.activeEndpoint()
            val session = runtime.session.value
            updateState {
                copy(
                    loading = false,
                    accessibility = health.accessibility,
                    root = health.root,
                    shizuku = health.shizuku,
                    session = session != null,
                    sessionOwner = session?.owner.orEmpty(),
                    controllers = controllers,
                    endpoints = endpoints,
                    activeEndpointId = active?.id.orEmpty(),
                    address = server.address(),
                    port = server.port(),
                    serverRunning = server.running.value,
                    pairingCode = server.pairingCode.value.orEmpty(),
                )
            }
        }
    }

    private fun setLocalEnabled(value: Boolean) {
        launchGuarded(onError = ::emitError) {
            repository.setLocalEnabled(value)
            if (!value) {
                stopLive()
                runtime.stopSessionWithDisplay()
                updateState {
                    copy(
                        localEnabled = false,
                        session = false,
                        sessionOwner = "",
                        displayRunning = false,
                        displayId = 0,
                        displayPreview = null,
                        displayPreviewing = false,
                    )
                }
            } else {
                updateState { copy(localEnabled = true) }
            }
        }
    }

    private fun setRemoteEnabled(value: Boolean) {
        launchGuarded(onError = ::emitError) {
            val result = runCatching {
                repository.setRemoteEnabled(value)
                if (value) {
                    server.start()
                    server.newPairingCode()
                } else {
                    server.stop()
                }
            }
            if (result.isFailure) {
                sendEffect(DeviceEffect.Message(R.string.device_msg_remote_failed, result.exceptionOrNull()?.message))
                return@launchGuarded
            }
            if (value) {
                updateState {
                    copy(remoteEnabled = true, serverRunning = true, pairingCode = server.pairingCode.value.orEmpty())
                }
            } else {
                updateState { copy(remoteEnabled = false, serverRunning = false, pairingCode = "") }
            }
        }
    }

    private fun preview() {
        if (currentState.previewing) return
        updateState { copy(previewing = true) }
        launchGuarded(
            onError = {
                updateState { copy(previewing = false) }
                sendEffect(DeviceEffect.Message(R.string.device_msg_preview_failed, it.serverMessage ?: it.type.name))
            },
            onComplete = { updateState { copy(previewing = false) } },
        ) {
            val image = runtime.screenshot()
            if (image == null) {
                sendEffect(DeviceEffect.Message(R.string.device_msg_preview_failed))
            } else {
                updateState { copy(preview = image) }
            }
        }
    }

    private fun toggleLive(id: String?) {
        if (currentState.live && currentState.liveTarget == id) {
            stopLive()
            return
        }
        liveJob?.cancel()
        updateState { copy(live = true, liveTarget = id) }
        liveJob = viewModelScope.launch {
            val endpoint = id?.let { endpointId ->
                repository.endpointsOnce().firstOrNull { it.id == endpointId }
            }
            if (id != null && endpoint == null) {
                sendEffect(DeviceEffect.Message(R.string.device_msg_preview_failed))
                stopLive()
                return@launch
            }
            if (endpoint != null) {
                try {
                    repository.screenStream(endpoint, fps = 5) { frame ->
                        if (frame.type == "frame" && frame.image.isNotBlank()) {
                            updateState { copy(preview = frame.image) }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    sendEffect(DeviceEffect.Message(R.string.device_msg_remote_failed, e.message))
                    stopLive()
                }
                stopLive()
                return@launch
            }
            while (isActive) {
                val image = runCatching { runtime.screenshot() }.getOrNull()
                if (image != null) updateState { copy(preview = image) }
                delay(500)
            }
        }
    }

    private fun stopLive() {
        liveJob?.cancel()
        liveJob = null
        if (currentState.live || currentState.liveTarget != null) {
            updateState { copy(live = false, liveTarget = null) }
        }
    }

    override fun onCleared() {
        liveJob?.cancel()
        super.onCleared()
    }

    private fun startLocal() {
        if (!currentState.localEnabled) {
            sendEffect(DeviceEffect.Message(R.string.device_msg_turn_on))
            return
        }
        launchGuarded(onError = ::emitError) {
            val health = runtime.health()
            if (!health.accessibility && !health.root && !health.shizuku) {
                sendEffect(DeviceEffect.Message(R.string.device_msg_enable_first))
                return@launchGuarded
            }
            val session = runtime.startSession("Focus")
            updateState { copy(session = true, sessionOwner = session.owner) }
            sendEffect(DeviceEffect.Message(R.string.device_msg_control_started))
        }
    }

    private fun stopSession() {
        stopLive()
        launchGuarded(onError = ::emitError) {
            runtime.stopSessionWithDisplay()
            updateState {
                copy(
                    session = false,
                    sessionOwner = "",
                    displayRunning = false,
                    displayId = 0,
                    displayPreview = null,
                    displayPreviewing = false,
                )
            }
            sendEffect(DeviceEffect.Message(R.string.device_msg_control_stopped))
        }
    }

    private fun startDisplay() {
        if (currentState.displayRunning || currentState.displayStarting) return
        if (!currentState.localEnabled) {
            sendEffect(DeviceEffect.Message(R.string.device_msg_turn_on))
            return
        }
        launchGuarded(onError = ::emitError) {
            if (runtime.session.value == null) {
                val session = runtime.startSession("Focus")
                updateState { copy(session = true, sessionOwner = session.owner) }
            }
            val state = runtime.startVirtualDisplay(VirtualDisplaySpec())
            if (!state.running) {
                sendEffect(DeviceEffect.Message(R.string.device_msg_display_failed, state.error))
                return@launchGuarded
            }
            updateState {
                copy(
                    displayRunning = true,
                    displayId = state.displayId,
                    displayWidth = state.width,
                    displayHeight = state.height,
                    displayDensity = state.density,
                    displayBackend = state.backend.name.lowercase(),
                    displayTrusted = state.trusted,
                    displayError = "",
                    displayPreviewing = true,
                )
            }
            sendEffect(DeviceEffect.Message(R.string.device_msg_display_started, state.displayId.toString()))
        }
    }

    private fun stopDisplay() {
        launchGuarded(onError = ::emitError) {
            runtime.stopVirtualDisplay()
            updateState {
                copy(
                    displayRunning = false,
                    displayId = 0,
                    displayPreview = null,
                    displayPreviewing = false,
                    displayError = "",
                )
            }
        }
    }

    private fun toggleDisplayPreview() {
        val next = !currentState.displayPreviewing
        updateState { copy(displayPreviewing = next, displayPreview = if (next) displayPreview else null) }
    }

    private fun revoke(id: String) {
        launchGuarded(onError = ::emitError) {
            repository.revokeController(id)
            val controllers = repository.controllersOnce()
            updateState { copy(controllers = controllers) }
        }
    }

    private fun pair() {
        val state = currentState
        val host = state.host.trim()
        val port = state.portText.trim().toIntOrNull() ?: return
        val code = state.code.trim()
        if (host.isBlank() || code.isBlank() || state.pairing) return
        updateState { copy(pairing = true) }
        launchGuarded(
            onError = {
                updateState { copy(pairing = false) }
                sendEffect(DeviceEffect.Message(R.string.device_msg_pair_failed, it.message()))
            },
        ) {
            val result = runCatching { repository.pair(host, port, code, "Focus") }
            val paired = result.getOrNull()
            if (paired == null) {
                updateState { copy(pairing = false) }
                sendEffect(DeviceEffect.Message(R.string.device_msg_pair_failed, result.exceptionOrNull()?.message))
                return@launchGuarded
            }
            val endpoints = repository.endpointsOnce()
            updateState {
                copy(
                    pairing = false,
                    pairOpen = false,
                    code = "",
                    endpoints = endpoints,
                    activeEndpointId = paired.id,
                )
            }
            sendEffect(DeviceEffect.Message(R.string.device_msg_paired, paired.name))
        }
    }

    private fun select(id: String) {
        launchGuarded(onError = ::emitError) {
            repository.select(id)
            updateState { copy(activeEndpointId = id) }
        }
    }

    private fun forget(id: String) {
        launchGuarded(onError = ::emitError) {
            val endpoint = repository.endpointsOnce().firstOrNull { it.id == id } ?: return@launchGuarded
            repository.forget(endpoint)
            updateState {
                val remaining = endpoints.filterNot { it.id == id }
                copy(endpoints = remaining, activeEndpointId = remaining.firstOrNull()?.id.orEmpty())
            }
        }
    }

    private fun startRemote(id: String) {
        launchGuarded(onError = ::emitError) {
            val endpoint = repository.endpointsOnce().firstOrNull { it.id == id } ?: return@launchGuarded
            val response = repository.rpc(endpoint, "start", buildJsonObject {
                put("owner", "Focus")
            })
            if (response.ok) {
                repository.select(id)
                updateState { copy(activeEndpointId = id) }
                sendEffect(DeviceEffect.Message(R.string.device_msg_remote_started))
            } else {
                sendEffect(DeviceEffect.Message(R.string.device_msg_remote_failed, response.error))
            }
        }
    }

    private fun saveTrigger() {
        val state = currentState
        val name = state.triggerName.trim()
        val action = state.triggerAction
        val command = state.triggerCommand.trim()
        if (name.isBlank()) {
            sendEffect(DeviceEffect.Message(R.string.device_msg_trigger_name))
            return
        }
        if (action != "notify" && command.isBlank()) {
            sendEffect(DeviceEffect.Message(R.string.device_msg_trigger_command))
            return
        }
        launchGuarded(onError = ::emitError) {
            runtime.addTrigger(
                name = name,
                packageFilter = state.triggerPackage.trim(),
                textFilter = state.triggerText.trim(),
                typeFilter = state.triggerType.trim(),
                action = action,
                conversationId = "",
                command = command,
                cooldownSec = state.triggerCooldown.toIntOrNull() ?: 30,
            )
            updateState { copy(triggerOpen = false) }
            sendEffect(DeviceEffect.Message(R.string.device_msg_trigger_saved))
        }
    }

    private fun toggleTrigger(id: String, enabled: Boolean) {
        launchGuarded(onError = ::emitError) {
            runtime.setTriggerEnabled(id, enabled)
        }
    }

    private fun deleteTrigger(id: String) {
        launchGuarded(onError = ::emitError) {
            runtime.removeTrigger(id)
        }
    }

    private fun stopRemote(id: String) {
        if (currentState.liveTarget == id) stopLive()
        launchGuarded(onError = ::emitError) {
            val endpoint = repository.endpointsOnce().firstOrNull { it.id == id } ?: return@launchGuarded
            val response = repository.rpc(endpoint, "stop")
            sendEffect(DeviceEffect.Message(if (response.ok) R.string.device_msg_remote_stopped else R.string.device_msg_remote_failed, if (response.ok) null else response.error))
        }
    }

    private fun fan.x0.focus.core.error.AppError.message(): String =
        serverMessage ?: serverTitle ?: type.name
}
