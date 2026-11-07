package fan.x0.focus.feature.overlay

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat

object FocusOverlay {
    const val ACTION_SHOW = "fan.x0.focus.action.SHOW_OVERLAY"
    const val ACTION_HIDE = "fan.x0.focus.action.HIDE_OVERLAY"

    private const val SHORTCUT_ID = "ask_focus_pinned"

    fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun openPermission(context: Context): Boolean {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(intent) }.isSuccess) return true
        val fallback = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        if (context !is Activity) fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(fallback) }.isSuccess
    }

    fun show(context: Context): Boolean {
        if (!canDraw(context)) return false
        val intent = Intent(context, FocusOverlayService::class.java).setAction(ACTION_SHOW)
        return runCatching { ContextCompat.startForegroundService(context, intent) }.isSuccess
    }

    fun hide(context: Context) {
        val intent = Intent(context, FocusOverlayService::class.java).setAction(ACTION_HIDE)
        runCatching { context.startService(intent) }
    }

    fun pinShortcut(context: Context): Boolean {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return false
        if (!manager.isRequestPinShortcutSupported) return false
        val intent = Intent(context, OverlayShortcutActivity::class.java).setAction(Intent.ACTION_VIEW)
        val shortcut = ShortcutInfo.Builder(context, SHORTCUT_ID)
            .setShortLabel(context.getString(R.string.overlay_shortcut_short))
            .setLongLabel(context.getString(R.string.overlay_shortcut_long))
            .setIcon(Icon.createWithResource(context, R.drawable.ic_overlay_shortcut))
            .setIntent(intent)
            .build()
        return runCatching { manager.requestPinShortcut(shortcut, null) }.getOrDefault(false)
    }
}
