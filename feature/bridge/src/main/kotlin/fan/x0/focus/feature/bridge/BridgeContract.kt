package fan.x0.focus.feature.bridge

import fan.x0.focus.core.mvi.UiAction
import fan.x0.focus.core.mvi.UiEffect
import fan.x0.focus.core.mvi.UiState

enum class BridgeTab { Devices, Terminal }

data class BridgeDeviceUi(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val os: String,
    val arch: String,
    val version: String,
    val roots: List<String>,
    val active: Boolean,
)

data class BridgeDiscoveredUi(
    val host: String,
    val port: Int,
    val name: String,
    val os: String,
    val arch: String,
)

data class BridgeSessionUi(
    val id: String,
    val name: String,
    val shell: String,
    val cols: Int,
    val rows: Int,
)

data class BridgeUiState(
    val loading: Boolean = true,
    val tab: BridgeTab = BridgeTab.Devices,
    val devices: List<BridgeDeviceUi> = emptyList(),
    val scanning: Boolean = false,
    val discovered: List<BridgeDiscoveredUi> = emptyList(),
    val addOpen: Boolean = false,
    val host: String = "",
    val port: String = "7788",
    val code: String = "",
    val pairing: Boolean = false,
    val roots: List<String> = emptyList(),
    val rootDraft: String = "",
    val savingRoots: Boolean = false,
    val sessions: List<BridgeSessionUi> = emptyList(),
    val sessionsLoading: Boolean = false,
    val opened: BridgeSessionUi? = null,
    val terminalOutput: String = "",
    val terminalInput: String = "",
    val connecting: Boolean = false,
    val deleteTarget: BridgeDeviceUi? = null,
) : UiState

sealed interface BridgeAction : UiAction {
    data class TabChanged(val tab: BridgeTab) : BridgeAction
    data object Refresh : BridgeAction
    data object Scan : BridgeAction
    data class UseDiscovered(val host: String, val port: Int) : BridgeAction
    data object OpenAdd : BridgeAction
    data object DismissAdd : BridgeAction
    data class HostChanged(val value: String) : BridgeAction
    data class PortChanged(val value: String) : BridgeAction
    data class CodeChanged(val value: String) : BridgeAction
    data object SubmitPair : BridgeAction
    data class SelectDevice(val id: String) : BridgeAction
    data class AskForgetDevice(val id: String) : BridgeAction
    data object ConfirmForget : BridgeAction
    data object CancelForget : BridgeAction
    data class RootDraftChanged(val value: String) : BridgeAction
    data object AddRoot : BridgeAction
    data class RemoveRoot(val path: String) : BridgeAction
    data object SaveRoots : BridgeAction
    data object LoadSessions : BridgeAction
    data object NewSession : BridgeAction
    data class OpenSession(val id: String) : BridgeAction
    data class CloseSession(val id: String) : BridgeAction
    data object CloseTerminal : BridgeAction
    data class TerminalInputChanged(val value: String) : BridgeAction
    data object SendTerminalInput : BridgeAction
    data class SendTerminalKey(val key: TerminalKey) : BridgeAction
}

enum class TerminalKey { Escape, Tab, ControlC, ControlD }

sealed interface BridgeEffect : UiEffect
