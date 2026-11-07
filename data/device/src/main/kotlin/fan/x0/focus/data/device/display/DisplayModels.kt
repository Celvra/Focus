package fan.x0.focus.data.device.display

import kotlinx.serialization.Serializable

@Serializable
enum class DisplayBackendKind { None, Shizuku, Root }

@Serializable
data class VirtualDisplaySpec(
    val width: Int = 720,
    val height: Int = 1280,
    val density: Int = 320,
    val backend: String = "auto",
)

data class DisplayStartResult(
    val displayId: Int,
    val width: Int,
    val height: Int,
    val density: Int,
    val trusted: Boolean,
    val backend: DisplayBackendKind,
)

data class DisplayStatus(
    val running: Boolean = false,
    val displayId: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val density: Int = 0,
    val trusted: Boolean = false,
    val frameReady: Boolean = false,
    val error: String = "",
)

@Serializable
data class VirtualDisplayState(
    val running: Boolean = false,
    val starting: Boolean = false,
    val displayId: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val density: Int = 0,
    val backend: DisplayBackendKind = DisplayBackendKind.None,
    val trusted: Boolean = false,
    val frameReady: Boolean = false,
    val error: String = "",
)

interface DisplayBackend {
    val kind: DisplayBackendKind

    suspend fun start(spec: VirtualDisplaySpec): DisplayStartResult

    suspend fun stop()

    suspend fun status(): DisplayStatus

    suspend fun capture(quality: Int = 80, maxWidth: Int = 0): ByteArray?

    suspend fun tap(displayId: Int, x: Int, y: Int, durationMs: Int): Boolean

    suspend fun swipe(
        displayId: Int,
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMs: Int,
    ): Boolean

    suspend fun key(displayId: Int, keyCode: Int): Boolean

    suspend fun text(displayId: Int, text: String): Boolean

    suspend fun launch(displayId: Int, packageName: String, activity: String): Boolean

    fun release()
}
