package fan.x0.focus.data.device.display

import android.content.Context
import android.util.Base64
import fan.x0.focus.data.device.DeviceShell
import fan.x0.focus.data.device.system.DeviceDisplayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import rikka.shizuku.Shizuku

@Single
class VirtualDisplayController(
    @Provided private val context: Context,
    private val shell: DeviceShell,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val _state = MutableStateFlow(VirtualDisplayState())
    val state: StateFlow<VirtualDisplayState> = _state.asStateFlow()
    private val _activeDisplayId = MutableStateFlow(0)
    val activeDisplayId: StateFlow<Int> = _activeDisplayId.asStateFlow()
    private val _preview = MutableStateFlow<String?>(null)
    val preview: StateFlow<String?> = _preview.asStateFlow()

    private var backend: DisplayBackend? = null
    private var previewJob: Job? = null

    val running: Boolean get() = _state.value.running

    suspend fun start(spec: VirtualDisplaySpec): VirtualDisplayState = mutex.withLock {
        if (_state.value.running) return@withLock _state.value
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            _state.value = VirtualDisplayState(error = "Virtual screen requires Android 10 or later")
            return@withLock _state.value
        }
        _state.value = _state.value.copy(starting = true, error = "")
        val errors = mutableListOf<String>()
        for (candidate in candidates(spec.backend)) {
            try {
                val result = candidate.start(spec)
                backend = candidate
                _state.value = VirtualDisplayState(
                    running = true,
                    starting = false,
                    displayId = result.displayId,
                    width = result.width,
                    height = result.height,
                    density = result.density,
                    backend = candidate.kind,
                    trusted = result.trusted,
                    frameReady = true,
                )
                _activeDisplayId.value = result.displayId
                setPreviewActive(true)
                DeviceDisplayService.start(context)
                return@withLock _state.value
            } catch (error: Throwable) {
                errors += "${candidate.kind.name.lowercase()}: ${error.message ?: error.javaClass.simpleName}"
                candidate.release()
            }
        }
        _state.value = VirtualDisplayState(
            starting = false,
            error = errors.ifEmpty {
                listOf("Virtual screen needs Shizuku or root. Start Shizuku and grant access, or use a rooted device.")
            }.joinToString("; "),
        )
        _state.value
    }

    suspend fun refresh() {
        val current = backend ?: return
        val status = runCatching { current.status() }.getOrNull()
        if (status == null || !status.running) {
            stop()
        }
    }

    suspend fun stop() = mutex.withLock {
        previewJob?.cancel()
        previewJob = null
        _preview.value = null
        runCatching { backend?.stop() }
        backend?.release()
        backend = null
        _activeDisplayId.value = 0
        _state.value = VirtualDisplayState()
        DeviceDisplayService.stop(context)
    }

    private fun setPreviewActive(active: Boolean) {
        if (!active) {
            previewJob?.cancel()
            previewJob = null
            _preview.value = null
            return
        }
        if (previewJob != null) return
        previewJob = scope.launch {
            while (isActive) {
                val bytes = runCatching { backend?.capture(quality = 60, maxWidth = 480) }.getOrNull()
                if (bytes != null) {
                    _preview.value = Base64.encodeToString(bytes, Base64.NO_WRAP)
                }
                delay(200)
            }
        }
    }

    suspend fun capture(quality: Int = 80, maxWidth: Int = 0): ByteArray? =
        backend?.capture(quality, maxWidth)

    suspend fun captureBase64(quality: Int = 80, maxWidth: Int = 0): String? {
        val bytes = capture(quality, maxWidth) ?: return null
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    suspend fun tap(x: Int, y: Int, longPress: Boolean = false): Boolean {
        val id = _activeDisplayId.value
        if (id <= 0) return false
        return backend?.tap(id, x, y, if (longPress) 700 else 60) == true
    }

    suspend fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Int = 300): Boolean {
        val id = _activeDisplayId.value
        if (id <= 0) return false
        return backend?.swipe(id, startX, startY, endX, endY, durationMs) == true
    }

    suspend fun key(name: String): Boolean {
        val id = _activeDisplayId.value
        if (id <= 0) return false
        val code = when (name.lowercase()) {
            "back" -> android.view.KeyEvent.KEYCODE_BACK
            "enter" -> android.view.KeyEvent.KEYCODE_ENTER
            "delete" -> android.view.KeyEvent.KEYCODE_DEL
            "escape" -> android.view.KeyEvent.KEYCODE_ESCAPE
            "tab" -> android.view.KeyEvent.KEYCODE_TAB
            "space" -> android.view.KeyEvent.KEYCODE_SPACE
            else -> return false
        }
        return backend?.key(id, code) == true
    }

    suspend fun text(text: String): Boolean {
        val id = _activeDisplayId.value
        if (id <= 0) return false
        return backend?.text(id, text) == true
    }

    suspend fun launch(packageName: String, activity: String): Boolean {
        val id = _activeDisplayId.value
        if (id <= 0) return false
        return backend?.launch(id, packageName, activity) == true
    }

    fun release() {
        previewJob?.cancel()
        previewJob = null
        runCatching { backend?.release() }
        backend = null
        _activeDisplayId.value = 0
        _preview.value = null
        _state.value = VirtualDisplayState()
        DeviceDisplayService.stop(context)
    }

    private suspend fun candidates(requested: String): List<DisplayBackend> {
        val status = runCatching { shell.refresh() }.getOrDefault(shell.status())
        val shizuku = ShizukuDisplayBackend(context)
        val root = RootDisplayBackend(context, shell)
        val shizukuAvailable = shizuku.available()
        val rootAvailable = status.root
        val shizukuRoot = runCatching { Shizuku.getUid() == 0 }.getOrDefault(false)
        val all = when (requested.lowercase()) {
            "shizuku" -> listOf(shizuku)
            "root" -> listOf(root)
            else -> when {
                shizukuAvailable && shizukuRoot -> listOf(shizuku, root)
                rootAvailable -> listOf(root, shizuku)
                else -> listOf(shizuku, root)
            }
        }
        return all.filter { candidate ->
            when (candidate.kind) {
                DisplayBackendKind.Shizuku -> shizukuAvailable
                DisplayBackendKind.Root -> rootAvailable
                DisplayBackendKind.None -> false
            }
        }
    }
}
