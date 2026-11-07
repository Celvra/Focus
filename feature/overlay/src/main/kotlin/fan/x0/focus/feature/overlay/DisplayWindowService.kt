package fan.x0.focus.feature.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ComposeView
import fan.x0.focus.core.designsystem.theme.FocusTheme
import fan.x0.focus.data.device.AutomationGate
import fan.x0.focus.data.device.DeviceRuntime
import fan.x0.focus.data.device.display.VirtualDisplayState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

class DisplayWindowService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val owner = OverlayWindowOwner()
    private val runtime: DeviceRuntime by lazy { KoinPlatform.getKoin().get<DeviceRuntime>() }
    private val gate: AutomationGate by lazy { KoinPlatform.getKoin().get<AutomationGate>() }

    private val windowManager: WindowManager
        get() = requireNotNull(getSystemService(WindowManager::class.java))

    private var view: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    private var observing = false
    private var state by mutableStateOf(VirtualDisplayState())
    private var mode by mutableStateOf(DisplayWindowMode.Normal)
    private val preview = mutableStateOf<String?>(null)
    private var windowWidthDp = DISPLAY_WINDOW_WIDTH_DP
    private var normalX = 0
    private var normalY = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!runtime.displayState.value.running) {
            stopSelf()
            return START_NOT_STICKY
        }
        state = runtime.displayState.value
        if (view == null) addView()
        observe()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
        params = null
        owner.destroy()
        super.onDestroy()
    }

    private fun addView() {
        val compose = ComposeView(this)
        compose.attachOverlayOwners(owner)
        compose.setContent {
            FocusTheme {
                DisplayWindow(
                    mode = mode,
                    state = state,
                    preview = preview,
                    onDrag = ::moveWindow,
                    onDragEnd = ::finishDrag,
                    onResize = ::resizeWindow,
                    onTap = { x, y -> scope.launch { runtime.virtualTap(x, y) } },
                    onLongPress = { x, y -> scope.launch { runtime.virtualTap(x, y, longPress = true) } },
                    onSwipe = { startX, startY, endX, endY ->
                        scope.launch { runtime.virtualSwipe(startX, startY, endX, endY) }
                    },
                    onHover = ::enterHover,
                    onMinimize = ::enterMinimized,
                    onRestore = ::restoreNormal,
                    onClose = ::closeDisplay,
                )
            }
        }
        val metrics = resources.displayMetrics
        val width = dp(windowWidthDp)
        val layout = WindowManager.LayoutParams(
            width,
            dp(normalHeightDp()),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = metrics.widthPixels - width - dp(12)
            y = dp(96)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                fitInsetsTypes = 0
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        normalX = layout.x
        normalY = layout.y
        val added = runCatching { windowManager.addView(compose, layout) }.isSuccess
        if (!added) {
            stopSelf()
            return
        }
        compose.alpha = 0f
        compose.visibility = View.VISIBLE
        compose.animate().alpha(1f).setDuration(180L).start()
        view = compose
        params = layout
    }

    private fun observe() {
        if (observing) return
        observing = true
        scope.launch {
            runtime.displayState.collect { next ->
                state = next
                if (!next.running) {
                    stopSelf()
                } else if (mode == DisplayWindowMode.Normal) {
                    applyCurrentMode()
                }
            }
        }
        scope.launch {
            runtime.displayPreview.collect { preview.value = it }
        }
        scope.launch {
            gate.concealed.collect { concealed ->
                view?.animate()
                    ?.alpha(if (concealed) 0f else 1f)
                    ?.setDuration(if (concealed) 140L else 200L)
                    ?.start()
            }
        }
    }

    private fun moveWindow(delta: Offset) {
        if (mode == DisplayWindowMode.Minimized) return
        val current = params ?: return
        val target = view ?: return
        val metrics = resources.displayMetrics
        current.x += delta.x.toInt()
        current.y += delta.y.toInt()
        current.x = current.x.coerceIn(0, (metrics.widthPixels - current.width).coerceAtLeast(0))
        current.y = current.y.coerceIn(0, (metrics.heightPixels - current.height).coerceAtLeast(0))
        if (mode == DisplayWindowMode.Normal) {
            normalX = current.x
            normalY = current.y
        }
        runCatching { windowManager.updateViewLayout(target, current) }
    }

    private fun finishDrag() {
        val current = params ?: return
        val target = view ?: return
        val metrics = resources.displayMetrics
        if (mode == DisplayWindowMode.Hover) {
            snapToEdge(current, metrics)
        }
        current.x = current.x.coerceIn(0, (metrics.widthPixels - current.width).coerceAtLeast(0))
        current.y = current.y.coerceIn(0, (metrics.heightPixels - current.height).coerceAtLeast(0))
        if (mode == DisplayWindowMode.Normal) {
            normalX = current.x
            normalY = current.y
        }
        runCatching { windowManager.updateViewLayout(target, current) }
    }

    private fun resizeWindow(delta: Offset) {
        if (mode != DisplayWindowMode.Normal) return
        val density = resources.displayMetrics.density
        val deltaDp = if (abs(delta.x) >= abs(delta.y)) {
            delta.x / density
        } else {
            delta.y / density
        }
        val current = params ?: return
        normalX = current.x
        normalY = current.y
        val state = runtime.displayState.value
        val ratio = if (state.width > 0 && state.height > 0) {
            state.width.toFloat() / state.height
        } else {
            9f / 16f
        }
        val maxByHeight = (
            (resources.displayMetrics.heightPixels / density - DISPLAY_WINDOW_CHROME_DP) * ratio +
                DISPLAY_WINDOW_PADDING_DP * 2
            ).toInt().coerceAtLeast(180)
        windowWidthDp = (windowWidthDp + deltaDp).roundToInt().coerceIn(180, min(360, maxByHeight))
        applyCurrentMode()
    }

    private fun enterHover() {
        if (mode == DisplayWindowMode.Normal) rememberNormal()
        mode = DisplayWindowMode.Hover
        applyCurrentMode()
    }

    private fun enterMinimized() {
        if (mode == DisplayWindowMode.Normal) rememberNormal()
        mode = DisplayWindowMode.Minimized
        applyCurrentMode()
    }

    private fun restoreNormal() {
        if (mode == DisplayWindowMode.Hover) {
            params?.let {
                normalX = it.x
                normalY = it.y
            }
        }
        mode = DisplayWindowMode.Normal
        applyCurrentMode()
    }

    private fun closeDisplay() {
        scope.launch {
            runtime.stopSessionWithDisplay()
            stopSelf()
        }
    }

    private fun rememberNormal() {
        val current = params ?: return
        normalX = current.x
        normalY = current.y
    }

    private fun applyCurrentMode() {
        val current = params ?: return
        val target = view ?: return
        val metrics = resources.displayMetrics
        when (mode) {
            DisplayWindowMode.Normal -> {
                current.width = dp(windowWidthDp)
                current.height = dp(normalHeightDp())
                current.x = normalX
                current.y = normalY
            }
            DisplayWindowMode.Hover -> {
                current.width = dp(DISPLAY_WINDOW_HOVER_DP)
                current.height = dp(DISPLAY_WINDOW_HOVER_DP)
                snapToEdge(current, metrics)
            }
            DisplayWindowMode.Minimized -> {
                current.width = dp(DISPLAY_WINDOW_BUBBLE_DP)
                current.height = dp(DISPLAY_WINDOW_BUBBLE_DP)
                snapToEdge(current, metrics)
            }
        }
        current.x = current.x.coerceIn(0, (metrics.widthPixels - current.width).coerceAtLeast(0))
        current.y = current.y.coerceIn(0, (metrics.heightPixels - current.height).coerceAtLeast(0))
        if (mode == DisplayWindowMode.Normal) {
            normalX = current.x
            normalY = current.y
        }
        runCatching { windowManager.updateViewLayout(target, current) }
    }

    private fun normalHeightDp(): Int {
        val state = runtime.displayState.value
        val ratio = if (state.width > 0 && state.height > 0) {
            state.width.toFloat() / state.height
        } else {
            9f / 16f
        }
        val innerWidth = windowWidthDp - DISPLAY_WINDOW_PADDING_DP * 2
        val density = resources.displayMetrics.density
        val maxImage = (resources.displayMetrics.heightPixels / density - DISPLAY_WINDOW_CHROME_DP)
            .coerceAtLeast(120f)
        val imageHeight = (innerWidth / ratio).coerceAtMost(maxImage)
        return (DISPLAY_WINDOW_CHROME_DP + imageHeight).toInt()
    }

    private fun snapToEdge(current: WindowManager.LayoutParams, metrics: android.util.DisplayMetrics) {
        val center = current.x + current.width / 2
        current.x = if (center < metrics.widthPixels / 2) {
            dp(12)
        } else {
            metrics.widthPixels - current.width - dp(12)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            runCatching {
                context.startService(Intent(context, DisplayWindowService::class.java))
            }
        }
    }
}
