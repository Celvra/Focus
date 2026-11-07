package fan.x0.focus.data.plugin

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import fan.x0.focus.plugin.LocalPluginContext
import fan.x0.focus.plugin.PluginOverlayHost
import org.koin.mp.KoinPlatform
import java.util.concurrent.ConcurrentHashMap

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
        registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}

private data class OverlayEntry(
    val view: ComposeView,
    val owner: OverlayOwner,
)

class PluginOverlayManager(private val context: Context) : PluginOverlayHost {
    private val manager = context.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val views = ConcurrentHashMap<String, OverlayEntry>()

    override fun canDraw(): Boolean = PluginIntents.canDrawOverlays(context)

    override fun requestPermission() {
        PluginIntents.overlaySettings(context)
    }

    override fun show(pluginId: String, content: @Composable () -> Unit): Boolean {
        if (!canDraw()) return false
        if (views.containsKey(pluginId)) {
            hide(pluginId)
        }
        val registry = runCatching { KoinPlatform.getKoin().get<PluginRegistry>() }.getOrNull() ?: return false
        val plugin = registry.loadedPlugin(pluginId) ?: return false
        val pluginContext = plugin.context
        main.post {
            val view = ComposeView(context)
            val owner = OverlayOwner()
            setOwner(view, "androidx.lifecycle.ViewTreeLifecycleOwner", "androidx.lifecycle.LifecycleOwner", owner)
            setOwner(view, "androidx.lifecycle.ViewTreeViewModelStoreOwner", "androidx.lifecycle.ViewModelStoreOwner", owner)
            setOwner(view, "androidx.savedstate.ViewTreeSavedStateRegistryOwner", "androidx.savedstate.SavedStateRegistryOwner", owner)
            view.setContent {
                CompositionLocalProvider(LocalPluginContext provides pluginContext) {
                    FocusTheme {
                        content()
                    }
                }
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 24
                y = 220
            }
            runCatching { manager?.addView(view, params) }
            views[pluginId] = OverlayEntry(view, owner)
        }
        return true
    }

    override fun hide(pluginId: String) {
        main.post {
            val entry = views.remove(pluginId) ?: return@post
            runCatching { manager?.removeView(entry.view) }
            entry.owner.destroy()
        }
    }

    override fun isShowing(pluginId: String): Boolean = views.containsKey(pluginId)

    private fun setOwner(view: android.view.View, ownerClass: String, interfaceClass: String, owner: Any) {
        runCatching {
            val type = Class.forName(interfaceClass)
            val method = Class.forName(ownerClass).getMethod("set", android.view.View::class.java, type)
            method.invoke(null, view, owner)
        }
    }
}
