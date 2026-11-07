package fan.x0.focus.feature.device

import fan.x0.focus.core.mvi.UiAction
import fan.x0.focus.core.mvi.UiEffect
import fan.x0.focus.core.mvi.UiState
import fan.x0.focus.data.device.DeviceController
import fan.x0.focus.data.device.DeviceEndpoint
import fan.x0.focus.data.device.DeviceTrigger

enum class DeviceTab { Local, Controlled, Devices, Triggers }

data class DeviceUiState(
    val loading: Boolean = true,
    val tab: DeviceTab = DeviceTab.Local,
    val localEnabled: Boolean = false,
    val accessibility: Boolean = false,
    val root: Boolean = false,
    val shizuku: Boolean = false,
    val displayRunning: Boolean = false,
    val displayStarting: Boolean = false,
    val displayId: Int = 0,
    val displayWidth: Int = 0,
    val displayHeight: Int = 0,
    val displayDensity: Int = 0,
    val displayBackend: String = "",
    val displayTrusted: Boolean = false,
    val displayError: String = "",
    val displayPreview: String? = null,
    val displayPreviewing: Boolean = false,
    val session: Boolean = false,
    val sessionOwner: String = "",
    val preview: String? = null,
    val previewing: Boolean = false,
    val live: Boolean = false,
    val liveTarget: String? = null,
    val remoteEnabled: Boolean = false,
    val serverRunning: Boolean = false,
    val address: String = "",
    val port: Int = 7790,
    val pairingCode: String = "",
    val controllers: List<DeviceController> = emptyList(),
    val endpoints: List<DeviceEndpoint> = emptyList(),
    val activeEndpointId: String = "",
    val pairOpen: Boolean = false,
    val host: String = "",
    val portText: String = "7790",
    val code: String = "",
    val pairing: Boolean = false,
    val triggers: List<DeviceTrigger> = emptyList(),
    val triggerOpen: Boolean = false,
    val triggerName: String = "",
    val triggerPackage: String = "",
    val triggerText: String = "",
    val triggerType: String = "",
    val triggerAction: String = "shell",
    val triggerCommand: String = "",
    val triggerCooldown: String = "30",
) : UiState

sealed interface DeviceAction : UiAction {
    data class TabChanged(val tab: DeviceTab) : DeviceAction
    data object Refresh : DeviceAction
    data class LocalEnabledChanged(val value: Boolean) : DeviceAction
    data class RemoteEnabledChanged(val value: Boolean) : DeviceAction
    data object OpenAccessibility : DeviceAction
    data object Preview : DeviceAction
    data class ToggleLive(val id: String? = null) : DeviceAction
    data object StartLocal : DeviceAction
    data object StopSession : DeviceAction
    data object StartDisplay : DeviceAction
    data object StopDisplay : DeviceAction
    data object ToggleDisplayPreview : DeviceAction
    data object NewPairingCode : DeviceAction
    data object UseFullMode : DeviceAction
    data object RequestShizuku : DeviceAction
    data class RevokeController(val id: String) : DeviceAction
    data object OpenPair : DeviceAction
    data object ClosePair : DeviceAction
    data class HostChanged(val value: String) : DeviceAction
    data class PortChanged(val value: String) : DeviceAction
    data class CodeChanged(val value: String) : DeviceAction
    data object Pair : DeviceAction
    data class SelectDevice(val id: String) : DeviceAction
    data class ForgetDevice(val id: String) : DeviceAction
    data class StartRemote(val id: String) : DeviceAction
    data class StopRemote(val id: String) : DeviceAction
    data object OpenTrigger : DeviceAction
    data object CloseTrigger : DeviceAction
    data class TriggerNameChanged(val value: String) : DeviceAction
    data class TriggerPackageChanged(val value: String) : DeviceAction
    data class TriggerTextChanged(val value: String) : DeviceAction
    data class TriggerTypeChanged(val value: String) : DeviceAction
    data class TriggerActionChanged(val value: String) : DeviceAction
    data class TriggerCommandChanged(val value: String) : DeviceAction
    data class TriggerCooldownChanged(val value: String) : DeviceAction
    data object SaveTrigger : DeviceAction
    data class ToggleTrigger(val id: String, val enabled: Boolean) : DeviceAction
    data class DeleteTrigger(val id: String) : DeviceAction
}

sealed interface DeviceEffect : UiEffect {
    data class Message(val res: Int, val arg: String? = null) : DeviceEffect
    data object OpenAccessibility : DeviceEffect
}
