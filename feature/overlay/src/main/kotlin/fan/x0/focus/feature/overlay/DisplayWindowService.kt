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
    private val preview = mutableStateOf<String?>(null)

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
                    state = state,
                    preview = preview,
                    onDrag = ::moveWindow,
                    onClose = ::closeDisplay,
                    onTap = { x, y -> scope.launch { runtime.virtualTap(x, y) } },
                    onLongPress = { x, y -> scope.launch { runtime.virtualTap(x, y, longPress = true) } },
                    onSwipe = { startX, startY, endX, endY ->
                        scope.launch { runtime.virtualSwipe(startX, startY, endX, endY) }
                    },
                )
            }
        }
        val layout = WindowManager.LayoutParams(
            dp(DISPLAY_WINDOW_WIDTH_DP),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = resources.displayMetrics.widthPixels - dp(DISPLAY_WINDOW_WIDTH_DP) - dp(12)
            y = dp(96)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                fitInsetsTypes = 0
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
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
                if (!next.running) stopSelf()
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
        val current = params ?: return
        val target = view ?: return
        val metrics = resources.displayMetrics
        val width = target.width.takeIf { it > 0 } ?: dp(DISPLAY_WINDOW_WIDTH_DP)
        val height = target.height.takeIf { it > 0 } ?: dp(460)
        current.x = (current.x + delta.x.toInt()).coerceIn(0, (metrics.widthPixels - width).coerceAtLeast(0))
        current.y = (current.y + delta.y.toInt()).coerceIn(0, (metrics.heightPixels - height).coerceAtLeast(0))
        runCatching { windowManager.updateViewLayout(target, current) }
    }

    private fun closeDisplay() {
        scope.launch {
            runtime.stopSessionWithDisplay()
            stopSelf()
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
