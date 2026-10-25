package fan.x0.focus.data.bridge

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BridgeHealth(
    val name: String = "",
    val os: String = "",
    val version: String = "",
    val sessions: Int = 0,
    @SerialName("default_shell") val defaultShell: String = "",
    val home: String = "",
    val arch: String = "",
)

@Serializable
data class BridgePairRequest(
    val code: String,
    @SerialName("device_name") val deviceName: String,
)

@Serializable
data class BridgePairResult(
    @SerialName("device_id") val deviceId: String,
    val token: String,
)

@Serializable
data class BridgeDevice(
    val id: String,
    val name: String,
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("last_seen") val lastSeen: Long = 0,
    @SerialName("workspace_roots") val workspaceRoots: List<String> = emptyList(),
)

@Serializable
data class BridgeDevicesResult(
    val devices: List<BridgeDevice> = emptyList(),
)

@Serializable
data class BridgeSessionInfo(
    val id: String,
    val name: String = "",
    val shell: String = "",
    val cols: Int = 80,
    val rows: Int = 24,
    @SerialName("created_at") val createdAt: Long = 0,
    val closed: Boolean = false,
)

@Serializable
data class BridgeSessionsResult(
    val sessions: List<BridgeSessionInfo> = emptyList(),
)

@Serializable
data class BridgeSessionOpenRequest(
    val shell: String? = null,
    val cwd: String? = null,
    val name: String? = null,
    val cols: Int = 100,
    val rows: Int = 30,
)

@Serializable
data class BridgeSessionInput(
    val type: String,
    val data: String? = null,
    val cols: Int? = null,
    val rows: Int? = null,
    val signal: String? = null,
)

@Serializable
data class BridgeSessionWriteRequest(
    val data: String? = null,
    val cols: Int? = null,
    val rows: Int? = null,
    val signal: String? = null,
)

@Serializable
data class BridgeExecRequest(
    val command: String? = null,
    val argv: List<String>? = null,
    val cwd: String? = null,
    val env: Map<String, String>? = null,
    val stdin: String? = null,
    @SerialName("timeout_sec") val timeoutSec: Long? = null,
    @SerialName("session_id") val sessionId: String? = null,
)

@Serializable
data class BridgeExecEvent(
    val type: String = "",
    val data: String? = null,
    val message: String? = null,
    val pid: Long = 0,
    val command: String? = null,
    val code: Int = 0,
    @SerialName("timed_out") val timedOut: Boolean = false,
)

@Serializable
data class BridgeWorkspace(
    val roots: List<String> = emptyList(),
)

@Serializable
data class BridgeWorkspaceRequest(
    val roots: List<String> = emptyList(),
)

@Serializable
data class BridgeReadRequest(
    val path: String,
    val offset: Int? = null,
    val limit: Int? = null,
)

@Serializable
data class BridgeReadResult(
    val lines: String = "",
    @SerialName("total_lines") val totalLines: Long = 0,
    @SerialName("start_line") val startLine: Int = 1,
    val truncated: Boolean = false,
    val size: Long = 0,
)

@Serializable
data class BridgeWriteRequest(
    val path: String,
    val content: String,
)

@Serializable
data class BridgeEditRequest(
    val path: String,
    val old: String,
    val new: String,
    @SerialName("replace_all") val replaceAll: Boolean = false,
)

@Serializable
data class BridgeListRequest(
    val path: String,
    val depth: Int? = null,
)

@Serializable
data class BridgeListEntry(
    val path: String = "",
    val name: String = "",
    @SerialName("is_dir") val isDir: Boolean = false,
    val size: Long = 0,
)

@Serializable
data class BridgeListResult(
    val entries: List<BridgeListEntry> = emptyList(),
)

@Serializable
data class BridgeGlobRequest(
    val pattern: String,
    val root: String? = null,
)

@Serializable
data class BridgeGlobResult(
    val matches: List<String> = emptyList(),
)

@Serializable
data class BridgeGrepRequest(
    val pattern: String,
    val root: String? = null,
    val include: String? = null,
    val literal: Boolean = false,
)

@Serializable
data class BridgeGrepMatch(
    val path: String = "",
    val line: Int = 0,
    val text: String = "",
)

@Serializable
data class BridgeGrepResult(
    val matches: List<BridgeGrepMatch> = emptyList(),
)

@Serializable
data class BridgePathRequest(
    val path: String,
)

@Serializable
data class BridgeMoveRequest(
    val from: String,
    val to: String,
)

@Serializable
data class BridgeOkResult(
    val written: Boolean = false,
    val created: Boolean = false,
    val moved: Boolean = false,
    val removed: Boolean = false,
    val closed: Boolean = false,
    val replacements: Int = 0,
    val bytes: Long = 0,
    val path: String = "",
)

@Serializable
data class BridgeTailResult(
    val output: String = "",
)
