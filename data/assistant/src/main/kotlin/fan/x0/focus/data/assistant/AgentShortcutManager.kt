package fan.x0.focus.data.assistant

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

@Single
class AgentShortcutManager(@Provided private val context: Context) {

    fun pin(target: String, label: String): Boolean {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return false
        if (!manager.isRequestPinShortcutSupported) return false
        val normalized = target.trim().lowercase().ifBlank { "overlay" }
        val id = "focus_agent_$normalized"
        val intent = when (normalized) {
            "main" -> Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(context.packageName, "fan.x0.focus.MainActivity")
            else -> Intent(Intent.ACTION_VIEW)
                .setClassName(
                    context.packageName,
                    "fan.x0.focus.feature.overlay.OverlayShortcutActivity",
                )
        }
        val iconId = context.resources.getIdentifier("ic_shortcut", "mipmap", context.packageName)
            .takeIf { it != 0 }
            ?: context.applicationInfo.icon
        val info = ShortcutInfo.Builder(context, id)
            .setShortLabel(label.trim().take(30).ifBlank { "Focus" })
            .setLongLabel(label.trim().take(60).ifBlank { "Focus" })
            .setIcon(Icon.createWithResource(context, iconId))
            .setIntent(intent)
            .build()
        return runCatching {
            manager.pushDynamicShortcut(info)
            manager.requestPinShortcut(info, null)
        }.getOrDefault(false)
    }
}
