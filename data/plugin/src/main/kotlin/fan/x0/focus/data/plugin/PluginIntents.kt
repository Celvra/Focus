package fan.x0.focus.data.plugin

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

object PluginIntents {
    const val EXTRA_PLUGIN_ID = "focus.plugin.id"
    const val EXTRA_PLUGIN_PAGE = "focus.plugin.page"
    const val EXTRA_PLUGIN_ARGS = "focus.plugin.args"

    fun launch(
        context: Context,
        pluginId: String,
        pageId: String,
        args: String? = null,
    ) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(Intent.ACTION_MAIN).setPackage(context.packageName)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.putExtra(EXTRA_PLUGIN_ID, pluginId)
        intent.putExtra(EXTRA_PLUGIN_PAGE, pageId)
        if (args != null) intent.putExtra(EXTRA_PLUGIN_ARGS, args)
        runCatching { context.startActivity(intent) }
    }

    fun openSettings(context: Context, action: String, data: Uri? = null) {
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (data != null) intent.data = data
        runCatching { context.startActivity(intent) }
    }

    fun overlaySettings(context: Context) {
        openSettings(
            context,
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
    }

    fun accessibilitySettings(context: Context) {
        openSettings(context, Settings.ACTION_ACCESSIBILITY_SETTINGS)
    }

    fun notificationListenerSettings(context: Context) {
        openSettings(context, "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
    }

    fun appSettings(context: Context) {
        openSettings(
            context,
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        )
    }

    fun canDrawOverlays(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
}
