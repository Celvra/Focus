package fan.x0.focus.data.device

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
enum class DeviceRole { Full, Controlled }

@Serializable
data class DeviceEndpoint(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val model: String = "",
    val version: String = "",
    val android: String = "",
    val lastSeen: Long = 0,
)

@Serializable
data class DeviceController(
    val id: String,
    val name: String,
    val createdAt: Long = 0,
    val lastSeen: Long = 0,
)

@Serializable
data class DeviceBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val centerX: Int get() = left + width / 2
    val centerY: Int get() = top + height / 2
    val valid: Boolean get() = width > 0 && height > 0
}

@Serializable
data class DeviceNode(
    val index: Int,
    val text: String = "",
    val description: String = "",
    val hint: String = "",
    val className: String = "",
    val packageName: String = "",
    val viewId: String = "",
    val bounds: DeviceBounds,
    val clickable: Boolean = false,
    val editable: Boolean = false,
    val scrollable: Boolean = false,
    val password: Boolean = false,
    val focused: Boolean = false,
    val checked: Boolean = false,
    val selected: Boolean = false,
) {
    val label: String
        get() = text.ifBlank { description }.ifBlank { hint }.ifBlank { className.substringAfterLast('.') }
}

@Serializable
data class DeviceSnapshot(
    val deviceId: String = "",
    val deviceName: String = "",
    val packageName: String = "",
    val windowTitle: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val keyguard: Boolean = false,
    val screenOn: Boolean = true,
    val keyboard: Boolean = false,
    val nodes: List<DeviceNode> = emptyList(),
    val text: String = "",
)

@Serializable
data class DeviceHealth(
    val id: String = "",
    val name: String = "",
    val model: String = "",
    val version: String = "",
    val android: String = "",
    val accessibility: Boolean = false,
    val root: Boolean = false,
    val shizuku: Boolean = false,
    val remoteEnabled: Boolean = false,
    val session: Boolean = false,
    val sessionOwner: String = "",
    val width: Int = 0,
    val height: Int = 0,
)

@Serializable
data class DevicePairRequest(
    val code: String,
    val name: String,
)

@Serializable
data class DevicePairResult(
    val id: String,
    val token: String,
    val name: String = "",
    val model: String = "",
    val version: String = "",
    val android: String = "",
)

@Serializable
data class DeviceStreamFrame(
    val type: String,
    val image: String = "",
    val error: String = "",
)

@Serializable
data class DeviceRpcRequest(
    val op: String,
    val args: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class DeviceRpcResponse(
    val ok: Boolean,
    val error: String = "",
    val data: JsonObject = JsonObject(emptyMap()),
)

data class DeviceSession(
    val id: String,
    val owner: String,
    val startedAt: Long,
    val remote: Boolean = false,
    val conversationId: String = "",
)

sealed interface DeviceAutomationState {
    data object Idle : DeviceAutomationState

    data class Running(
        val owner: String,
        val remote: Boolean,
        val startedAt: Long,
    ) : DeviceAutomationState

    data class Finished(
        val owner: String,
        val remote: Boolean,
    ) : DeviceAutomationState
}

data class DeviceApp(
    val label: String,
    val packageName: String,
    val activity: String = "",
)

@Serializable
data class DeviceShellSessionInfo(
    val id: String,
    val shell: String,
    val root: Boolean,
    val startedAt: Long,
    val alive: Boolean,
)

@Serializable
data class DeviceBgJobInfo(
    val id: String,
    val command: String,
    val pid: Long,
    val startedAt: Long,
    val endedAt: Long = 0,
    val exitCode: Int = -1,
    val running: Boolean = true,
    val timedOut: Boolean = false,
)

@Serializable
data class DeviceTrigger(
    val id: String,
    val name: String,
    val packageFilter: String = "",
    val textFilter: String = "",
    val typeFilter: String = "",
    val action: String = "shell",
    val conversationId: String = "",
    val command: String,
    val enabled: Boolean = true,
    val cooldownSec: Int = 30,
    val createdAt: Long = 0,
    val lastFiredAt: Long = 0,
)

@Serializable
data class DeviceEventInfo(
    val seq: Long,
    val at: Long,
    val type: String,
    val packageName: String = "",
    val className: String = "",
    val text: String = "",
)

data class DeviceShellResult(
    val ok: Boolean,
    val output: String = "",
    val error: String = "",
    val exitCode: Int = -1,
    val root: Boolean = false,
)

enum class DeviceShellMode { None, Root, Shizuku, Accessibility }

data class DeviceShellStatus(
    val mode: DeviceShellMode = DeviceShellMode.None,
    val root: Boolean = false,
    val shizuku: Boolean = false,
    val detail: String = "",
)

data class DeviceStep(
    val ok: Boolean,
    val text: String,
    val screenshot: String? = null,
    val changed: Boolean = false,
    val error: String = "",
)
