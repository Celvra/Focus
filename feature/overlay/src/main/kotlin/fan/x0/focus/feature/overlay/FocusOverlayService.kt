package fan.x0.focus.feature.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Base64
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import fan.x0.focus.core.designsystem.theme.FocusTheme
import fan.x0.focus.core.notify.R as NotifyR
import fan.x0.focus.data.device.DeviceAutomationState
import fan.x0.focus.data.device.DeviceImage
import fan.x0.focus.data.device.DeviceRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform

class FocusOverlayService : Service() {

    private class OverlayOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val controller = SavedStateRegistryController.create(this)
        override val viewModelStore = ViewModelStore()
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

        init {
            controller.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            if (registry.currentState == Lifecycle.State.DESTROYED) return
            registry.currentState = Lifecycle.State.DESTROYED
            viewModelStore.clear()
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val owner = OverlayOwner()

    private val windowManager: WindowManager
        get() = requireNotNull(getSystemService(WindowManager::class.java))
    private val deviceRuntime: DeviceRuntime by lazy { KoinPlatform.getKoin().get<DeviceRuntime>() }
    private val viewModel: OverlayViewModel by lazy {
        ViewModelProvider(
            owner,
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return KoinPlatform.getKoin().get<OverlayViewModel>() as T
                }
            },
        )[OverlayViewModel::class.java]
    }

    private val selectionActive = MutableStateFlow(false)
    private val scene = MutableStateFlow(OverlayScene.Bar)
    private var windowJob: Job? = null
    private var resultVisible = false
    private var glowView: ComposeView? = null
    private var selectionView: ComposeView? = null
    private var selectionParams: WindowManager.LayoutParams? = null
    private var resultView: ComposeView? = null
    private var resultParams: WindowManager.LayoutParams? = null
    private var panelView: ComposeView? = null
    private var automationHidden = false
    private var interactionVisible = false
    private var capturing = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(OverlayLocale.wrap(newBase))
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground()
        scope.launch {
            deviceRuntime.automation
                .collect { state ->
                    when (state) {
                        is DeviceAutomationState.Running -> hideForAutomation()
                        is DeviceAutomationState.Finished -> {
                            if (state.remote) {
                                if (automationHidden) animatedDismiss()
                            } else {
                                restoreAfterAutomation()
                            }
                        }
                        DeviceAutomationState.Idle -> Unit
                    }
                }
        }
        scope.launch {
            viewModel.state.collect { state ->
                val needsInput = state.ask != null || state.approval != null || state.trust != null
                if (needsInput) {
                    showInteractionIfNeeded()
                } else if (interactionVisible) {
                    hideInteraction()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            FocusOverlay.ACTION_HIDE -> animatedDismiss()
            FocusOverlay.ACTION_SHOW -> show()
            else -> if (panelView == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        removeViews()
        owner.destroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun show(interaction: Boolean = false) {
        if (panelView != null) return
        if (!interaction && deviceRuntime.automation.value is DeviceAutomationState.Running) {
            dismiss()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.overlay_permission_needed, Toast.LENGTH_LONG).show()
            dismiss()
            return
        }
        val manager = windowManager
        addGlowView(manager)
        addSelectionView(manager)
        addResultView(manager)
        addPanelView(manager)
        if (panelView == null) return
        startWindowObserver()
        if (!interaction && !viewModel.state.value.hasResult) setSelectionActive(true)
    }

    private fun addGlowView(manager: WindowManager) {
        val view = ComposeView(this)
        attachOwners(view)
        view.setContent {
            val current by scene.collectAsState()
            FocusTheme {
                OverlayGlow(current)
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                fitInsetsTypes = 0
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        runCatching { manager.addView(view, params) }
        glowView = view
    }

    private fun addSelectionView(manager: WindowManager) {
        val view = ComposeView(this)
        attachOwners(view)
        view.setContent {
            FocusTheme {
                OverlaySelection(
                    onSelected = ::captureSelection,
                    onClose = ::animatedDismiss,
                )
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                fitInsetsTypes = 0
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        runCatching { manager.addView(view, params) }
        view.visibility = View.GONE
        selectionView = view
        selectionParams = params
    }

    private fun setSelectionActive(active: Boolean) {
        if (selectionActive.value == active) return
        selectionActive.value = active
        val view = selectionView ?: return
        val params = selectionParams ?: return
        if (active) {
            setResultActive(false)
            val keyboard = getSystemService(InputMethodManager::class.java)
            panelView?.let {
                keyboard?.hideSoftInputFromWindow(it.windowToken, 0)
                it.clearFocus()
            }
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            runCatching { windowManager.updateViewLayout(view, params) }
            view.alpha = 1f
            view.visibility = View.VISIBLE
        } else {
            view.visibility = View.GONE
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            runCatching { windowManager.updateViewLayout(view, params) }
        }
    }

    private fun setResultActive(active: Boolean) {
        if (resultVisible == active) return
        resultVisible = active
        val view = resultView ?: return
        val params = resultParams ?: return
        if (active) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            runCatching { windowManager.updateViewLayout(view, params) }
            view.alpha = 0f
            view.visibility = View.VISIBLE
            view.animate().alpha(1f).setDuration(180L).start()
        } else {
            view.visibility = View.GONE
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            runCatching { windowManager.updateViewLayout(view, params) }
        }
    }

    private fun addResultView(manager: WindowManager) {
        val view = ComposeView(this)
        attachOwners(view)
        view.setContent {
            val state by viewModel.state.collectAsState()
            OverlayResultContent(
                state = state,
                onAction = viewModel::onAction,
                onClose = ::animatedDismiss,
            )
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                fitInsetsTypes = 0
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        runCatching { manager.addView(view, params) }
        view.visibility = View.GONE
        resultView = view
        resultParams = params
    }

    private fun startWindowObserver() {
        if (windowJob != null) return
        windowJob = scope.launch {
            combine(viewModel.state, selectionActive) { state, selecting ->
                val result = !selecting && state.hasResult
                val effect = when {
                    selecting -> OverlayScene.Selection
                    result -> OverlayScene.Result
                    else -> OverlayScene.Bar
                }
                OverlayWindowState(
                    result = result,
                    selecting = selecting,
                    effect = effect,
                    sending = state.sending,
                )
            }
                .distinctUntilChanged()
                .collect { window ->
                    if (window.selecting && window.sending) {
                        setSelectionActive(false)
                        return@collect
                    }
                    scene.value = window.effect
                    setResultActive(window.result)
                    if (window.selecting) setSelectionActive(true)
                }
        }
    }

    private data class OverlayWindowState(
        val result: Boolean,
        val selecting: Boolean,
        val effect: OverlayScene,
        val sending: Boolean,
    )

    private fun addPanelView(manager: WindowManager) {
        val view = ComposeView(this)
        attachOwners(view)
        view.setContent {
            val state by viewModel.state.collectAsState()
            OverlayBarContent(
                state = state,
                onAction = viewModel::onAction,
                onCaptureScreen = ::captureScreen,
                onClose = ::animatedDismiss,
            )
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                if (selectionActive.value || resultVisible) {
                    false
                } else {
                    animatedDismiss()
                    true
                }
            } else {
                false
            }
        }
        val added = runCatching { manager.addView(view, params) }.isSuccess
        if (!added) {
            Toast.makeText(this, R.string.overlay_start_failed, Toast.LENGTH_SHORT).show()
            dismiss()
            return
        }
        panelView = view
        view.requestFocus()
    }

    private suspend fun captureFullScreen(): String? {
        val keyboard = getSystemService(InputMethodManager::class.java)
        panelView?.let { view ->
            keyboard?.hideSoftInputFromWindow(view.windowToken, 0)
            view.clearFocus()
        }
        fadeOutOverlay()
        return try {
            deviceRuntime.screenshot()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        } finally {
            fadeInOverlay()
        }
    }

    private suspend fun fadeOutOverlay() {
        val views = overlayViews().filter { it.visibility == View.VISIBLE }
        views.forEach { view ->
            view.animate().cancel()
            view.animate().alpha(0f).setDuration(140L).start()
        }
        if (views.isNotEmpty()) delay(150L)
        setViewsVisible(false)
    }

    private fun fadeInOverlay() {
        setViewsVisible(true)
        overlayViews()
            .filter { it.visibility == View.VISIBLE }
            .forEach { view ->
                view.animate().cancel()
                view.alpha = 0f
                view.animate().alpha(1f).setDuration(180L).start()
            }
    }

    private suspend fun hideForAutomation() {
        if (panelView == null || automationHidden) return
        interactionVisible = false
        automationHidden = true
        if (selectionActive.value) setSelectionActive(false)
        val keyboard = getSystemService(InputMethodManager::class.java)
        panelView?.let {
            keyboard?.hideSoftInputFromWindow(it.windowToken, 0)
            it.clearFocus()
        }
        fadeOutOverlay()
        removeViews()
        windowJob?.cancel()
        windowJob = null
        if (!automationHidden) {
            restoreOverlay()
        } else {
            showInteractionIfNeeded()
        }
    }

    private fun showInteractionIfNeeded() {
        if (!automationHidden || panelView != null) return
        val state = viewModel.state.value
        val needsInput = state.ask != null || state.approval != null || state.trust != null
        if (!needsInput) return
        interactionVisible = true
        show(interaction = true)
    }

    private suspend fun hideInteraction() {
        if (!interactionVisible) return
        interactionVisible = false
        if (deviceRuntime.automation.value !is DeviceAutomationState.Running) return
        if (panelView == null) return
        val keyboard = getSystemService(InputMethodManager::class.java)
        panelView?.let {
            keyboard?.hideSoftInputFromWindow(it.windowToken, 0)
            it.clearFocus()
        }
        fadeOutOverlay()
        removeViews()
        windowJob?.cancel()
        windowJob = null
        if (automationHidden) {
            showInteractionIfNeeded()
        } else {
            restoreOverlay()
        }
    }

    private fun restoreAfterAutomation() {
        if (!automationHidden) return
        if (deviceRuntime.automation.value is DeviceAutomationState.Running) return
        automationHidden = false
        interactionVisible = false
        restoreOverlay()
    }

    private fun restoreOverlay() {
        if (panelView != null) return
        if (deviceRuntime.automation.value is DeviceAutomationState.Running) {
            automationHidden = true
            return
        }
        selectionActive.value = false
        resultVisible = false
        scene.value = OverlayScene.Bar
        show()
    }

    private fun overlayViews(): List<View> = listOfNotNull(
        glowView,
        panelView,
        selectionView,
        resultView,
    )

    private fun captureScreen() {
        if (capturing) return
        if (selectionActive.value) setSelectionActive(false)
        capturing = true
        viewModel.onAction(OverlayAction.CaptureRequested)
        scope.launch {
            val shot = captureFullScreen()
            if (shot.isNullOrBlank()) {
                viewModel.onAction(OverlayAction.ScreenshotFailed)
            } else {
                viewModel.onAction(OverlayAction.ScreenshotCaptured(shot))
            }
            capturing = false
        }
    }

    private fun captureSelection(selection: androidx.compose.ui.geometry.Rect) {
        val rect = Rect(
            selection.left.toInt(),
            selection.top.toInt(),
            selection.right.toInt(),
            selection.bottom.toInt(),
        )
        if (capturing || rect.width() <= 0 || rect.height() <= 0) return
        capturing = true
        viewModel.onAction(OverlayAction.CaptureRequested)
        scope.launch {
            val full = captureFullScreen()
            val cropped = if (full.isNullOrBlank()) {
                null
            } else {
                withContext(Dispatchers.Default) { cropSelection(full, rect) }
            }
            if (cropped.isNullOrBlank()) {
                viewModel.onAction(OverlayAction.ScreenshotFailed)
            } else {
                viewModel.onAction(OverlayAction.ScreenshotCaptured(cropped, selection = true))
            }
            capturing = false
        }
    }

    private fun cropSelection(base64: String, rect: Rect): String? {
        val screenWidth = selectionView?.width?.takeIf { it > 0 }
            ?: resources.displayMetrics.widthPixels
        val screenHeight = selectionView?.height?.takeIf { it > 0 }
            ?: resources.displayMetrics.heightPixels
        if (screenWidth <= 0 || screenHeight <= 0) return null
        val bytes = runCatching { Base64.decode(base64, Base64.NO_WRAP) }.getOrNull() ?: return null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val scaleX = bitmap.width.toFloat() / screenWidth.toFloat()
        val scaleY = bitmap.height.toFloat() / screenHeight.toFloat()
        val left = (rect.left * scaleX).toInt().coerceIn(0, bitmap.width - 1)
        val top = (rect.top * scaleY).toInt().coerceIn(0, bitmap.height - 1)
        val right = (rect.right * scaleX).toInt().coerceIn(left + 1, bitmap.width)
        val bottom = (rect.bottom * scaleY).toInt().coerceIn(top + 1, bitmap.height)
        val cropped = runCatching {
            Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        }.getOrNull()
        val encoded = cropped?.let { DeviceImage.encode(it) }
        cropped?.recycle()
        bitmap.recycle()
        return encoded
    }

    private fun setViewsVisible(visible: Boolean) {
        val value = if (visible) View.VISIBLE else View.INVISIBLE
        glowView?.visibility = value
        panelView?.visibility = value
        selectionView?.visibility = when {
            !visible -> View.INVISIBLE
            selectionActive.value -> View.VISIBLE
            else -> View.GONE
        }
        resultView?.visibility = when {
            !visible -> View.INVISIBLE
            resultVisible && !selectionActive.value -> View.VISIBLE
            else -> View.GONE
        }
    }

    private fun animatedDismiss() {
        val panel = panelView
        if (panel == null) {
            dismiss()
            return
        }
        val keyboard = getSystemService(InputMethodManager::class.java)
        keyboard?.hideSoftInputFromWindow(panel.windowToken, 0)
        glowView?.animate()?.alpha(0f)?.setDuration(180L)?.start()
        selectionView?.animate()?.alpha(0f)?.setDuration(180L)?.start()
        resultView?.animate()?.alpha(0f)?.setDuration(180L)?.start()
        panel.animate()
            .alpha(0f)
            .scaleX(0.96f)
            .scaleY(0.96f)
            .setDuration(180L)
            .start()
        panel.postDelayed({ dismiss() }, 200L)
    }

    private fun dismiss() {
        removeViews()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun removeViews() {
        val manager = windowManager
        panelView?.let { view -> runCatching { manager.removeView(view) } }
        resultView?.let { view -> runCatching { manager.removeView(view) } }
        selectionView?.let { view -> runCatching { manager.removeView(view) } }
        glowView?.let { view -> runCatching { manager.removeView(view) } }
        panelView = null
        resultView = null
        resultParams = null
        selectionView = null
        selectionParams = null
        glowView = null
    }

    private fun attachOwners(view: View) {
        setOwner(view, "androidx.lifecycle.ViewTreeLifecycleOwner", "androidx.lifecycle.LifecycleOwner", owner)
        setOwner(view, "androidx.lifecycle.ViewTreeViewModelStoreOwner", "androidx.lifecycle.ViewModelStoreOwner", owner)
        setOwner(view, "androidx.savedstate.ViewTreeSavedStateRegistryOwner", "androidx.savedstate.SavedStateRegistryOwner", owner)
    }

    private fun setOwner(view: View, ownerClass: String, interfaceClass: String, value: Any) {
        runCatching {
            val type = Class.forName(interfaceClass)
            val method = Class.forName(ownerClass).getMethod("set", View::class.java, type)
            method.invoke(null, view, value)
        }
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun startForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val microphone = if (
                ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                0
            }
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or microphone
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
        }
    }

    private fun buildNotification(): Notification {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val close = PendingIntent.getService(
            this,
            1,
            Intent(this, FocusOverlayService::class.java).setAction(FocusOverlay.ACTION_HIDE),
            flags,
        )
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 2, it, flags)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(NotifyR.drawable.ic_notification_mascot)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_body))
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, getString(R.string.overlay_notification_close), close)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "focus_overlay"
        private const val NOTIFICATION_ID = 4102
    }
}
