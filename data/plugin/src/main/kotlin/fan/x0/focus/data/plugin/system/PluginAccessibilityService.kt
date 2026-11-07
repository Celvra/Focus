package fan.x0.focus.data.plugin.system

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import fan.x0.focus.data.plugin.PluginRegistry
import org.koin.mp.KoinPlatform

class PluginAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        current = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val registry = runCatching { KoinPlatform.getKoin().get<PluginRegistry>() }.getOrNull() ?: return
        registry.accessibilityHandlers().forEach { (plugin, handler) ->
            runCatching { handler.onAccessibilityEvent(event, this) }
                .onFailure { registry.log().write(plugin.manifest.id, "W", "Accessibility event failed", it) }
        }
    }

    override fun onInterrupt() {
        val registry = runCatching { KoinPlatform.getKoin().get<PluginRegistry>() }.getOrNull() ?: return
        registry.accessibilityHandlers().forEach { (plugin, handler) ->
            runCatching { handler.onInterrupt(this) }
                .onFailure { registry.log().write(plugin.manifest.id, "W", "Accessibility interrupt failed", it) }
        }
    }

    override fun onDestroy() {
        current = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var current: PluginAccessibilityService? = null
            private set
    }
}
