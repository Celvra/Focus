package fan.x0.focus.data.plugin

import fan.x0.focus.plugin.PluginManifest
import fan.x0.focus.plugin.PluginPageRef

enum class PluginRuntimeState { Disabled, Loading, Loaded, Failed }

data class PluginStatus(
    val manifest: PluginManifest,
    val enabled: Boolean,
    val source: String,
    val capabilities: Set<String>,
    val state: PluginRuntimeState,
    val error: String = "",
    val tools: List<String> = emptyList(),
    val pages: List<PluginPageRef> = emptyList(),
    val skills: List<String> = emptyList(),
)

object PluginCapability {
    const val OVERLAY = "overlay"
    const val FOREGROUND = "foreground"
    const val ACCESSIBILITY = "accessibility"
    const val NOTIFICATION_LISTENER = "notification_listener"
    const val TILE = "tile"
    const val WIDGET = "widget"
    const val BOOT = "boot"
    const val SCREEN = "screen"
    val all = setOf(OVERLAY, FOREGROUND, ACCESSIBILITY, NOTIFICATION_LISTENER, TILE, WIDGET, BOOT, SCREEN)
}
