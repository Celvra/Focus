package fan.x0.focus.data.plugin

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import fan.x0.focus.data.plugin.system.PluginAccessibilityService
import fan.x0.focus.data.plugin.system.PluginBootReceiver
import fan.x0.focus.data.plugin.system.PluginNotificationListenerService
import fan.x0.focus.data.plugin.system.PluginTileService
import fan.x0.focus.data.plugin.system.PluginWidgetProvider

object PluginComponents {
    fun sync(context: Context, capabilities: Set<String>) {
        enable(context, PluginAccessibilityService::class.java, PluginCapability.ACCESSIBILITY in capabilities)
        enable(context, PluginNotificationListenerService::class.java, PluginCapability.NOTIFICATION_LISTENER in capabilities)
        enable(context, PluginTileService::class.java, PluginCapability.TILE in capabilities)
        enable(context, PluginWidgetProvider::class.java, PluginCapability.WIDGET in capabilities)
        enable(context, PluginBootReceiver::class.java, PluginCapability.BOOT in capabilities)
    }

    fun disable(context: Context, pluginId: String) {
        val registry = runCatching {
            org.koin.mp.KoinPlatform.getKoin().get<PluginRegistry>()
        }.getOrNull() ?: return
        val all = registry.statuses.value.values
            .filter { it.enabled && it.manifest.id != pluginId }
            .flatMap { it.capabilities }
            .toSet()
        sync(context, all)
    }

    private fun enable(context: Context, type: Class<*>, enabled: Boolean) {
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        runCatching {
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, type),
                state,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
