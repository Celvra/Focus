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
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import fan.x0.focus.core.designsystem.theme.FocusTheme
import fan.x0.focus.data.device.AutomationGate
import fan.x0.focus.data.device.DeviceAutomationState
import fan.x0.focus.data.device.DeviceRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

class AutomationOverlayService : Service() {

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
    private val runtime: DeviceRuntime by lazy { KoinPlatform.getKoin().get<DeviceRuntime>() }
    private val gate: AutomationGate by lazy { KoinPlatform.getKoin().get<AutomationGate>() }

    private val windowManager: WindowManager
        get() = requireNotNull(getSystemService(WindowManager::class.java))

    private var view: ComposeView? = null
    private var observer: Job? = null
    private var concealObserver: Job? = null
    private var finishJob: Job? = null
    private var finished by mutableStateOf(false)
    private var preview by mutableStateOf<String?>(null)
    private var leaving = false
    private var observing = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        val state = runtime.automation.value
        if (state is DeviceAutomationState.Idle) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (state is DeviceAutomationState.Finished) {
            finished = true
        }
        if (view == null) addView()
        observe()
        if (state is DeviceAutomationState.Finished) scheduleFinish()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
        owner.destroy()
        if (runtime.automation.value is DeviceAutomationState.Finished) {
            runtime.clearAutomation()
        }
        super.onDestroy()
    }

    private fun addView() {
        val compose = ComposeView(this)
        attachOwners(compose)
        compose.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        compose.setContent {
            FocusTheme {
                AutomationOverlay(finished = finished, preview = preview)
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
        val added = runCatching { windowManager.addView(compose, params) }.isSuccess
        if (!added) {
            stopSelf()
            return
        }
        compose.alpha = 0f
        compose.visibility = View.VISIBLE
        compose.animate().alpha(1f).setDuration(220L).start()
        view = compose
    }

    private fun observe() {
        if (observing) return
        observing = true
        observer = scope.launch {
            runtime.automation
                .collect { state ->
                    when (state) {
                        is DeviceAutomationState.Running -> {
                            leaving = false
                            finishJob?.cancel()
                            finished = false
                            reveal(force = true)
                        }
                        is DeviceAutomationState.Finished -> {
                            finished = true
                            scheduleFinish()
                        }
                        DeviceAutomationState.Idle -> fadeOutAndStop()
                    }
                }
        }
        scope.launch {
            runtime.displayPreview.collect { preview = it }
        }
        concealObserver = scope.launch {
            gate.concealed
                .collect { hidden ->
                    if (hidden) {
                        conceal()
                    } else {
                        reveal()
                    }
                }
        }
    }

    private fun scheduleFinish() {
        if (leaving) return
        leaving = true
        finishJob?.cancel()
        finishJob = scope.launch {
            reveal(force = true)
            delay(900L)
            fadeOutAndStop()
        }
    }

    private fun conceal() {
        val current = view ?: return
        current.animate().cancel()
        current.animate()
            .alpha(0f)
            .setDuration(140L)
            .withEndAction {
                if (gate.concealed.value) current.visibility = View.INVISIBLE
            }
            .start()
    }

    private fun reveal(force: Boolean = false) {
        val current = view ?: return
        if (leaving && !force) return
        current.animate().cancel()
        current.visibility = View.VISIBLE
        current.animate().alpha(1f).setDuration(200L).start()
    }

    private fun fadeOutAndStop() {
        val current = view
        if (current == null) {
            runtime.clearAutomation()
            stopSelf()
            return
        }
        current.animate().cancel()
        current.animate()
            .alpha(0f)
            .setDuration(220L)
            .withEndAction {
                runtime.clearAutomation()
                stopSelf()
            }
            .start()
    }

    private fun attachOwners(target: View) {
        setOwner(target, "androidx.lifecycle.ViewTreeLifecycleOwner", "androidx.lifecycle.LifecycleOwner", owner)
        setOwner(target, "androidx.lifecycle.ViewTreeViewModelStoreOwner", "androidx.lifecycle.ViewModelStoreOwner", owner)
        setOwner(target, "androidx.savedstate.ViewTreeSavedStateRegistryOwner", "androidx.savedstate.SavedStateRegistryOwner", owner)
    }

    private fun setOwner(target: View, ownerClass: String, interfaceClass: String, value: Any) {
        runCatching {
            val type = Class.forName(interfaceClass)
            val method = Class.forName(ownerClass).getMethod("set", View::class.java, type)
            method.invoke(null, target, value)
        }
    }

    companion object {
        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            runCatching {
                context.startService(Intent(context, AutomationOverlayService::class.java))
            }
        }
    }
}
