package fan.x0.focus.data.plugin.system

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import fan.x0.focus.data.plugin.PluginIntents
import fan.x0.focus.data.plugin.PluginRegistry
import fan.x0.focus.data.plugin.R
import fan.x0.focus.plugin.PluginWidgetContent
import org.koin.mp.KoinPlatform

class PluginWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> update(context, appWidgetManager, id) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> PluginPreferenceStore.removeWidget(context, id) }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            appWidgetIds(context).forEach { id -> update(context, manager, id) }
        }

        fun updateWidget(context: Context, widgetId: Int, content: PluginWidgetContent) {
            val manager = AppWidgetManager.getInstance(context)
            val pluginId = runCatching {
                KoinPlatform.getKoin().get<PluginRegistry>().widgetHandler()?.first?.manifest?.id
            }.getOrNull().orEmpty()
            val views = views(context, pluginId, content)
            manager.updateAppWidget(widgetId, views)
        }

        private fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val registry = runCatching { KoinPlatform.getKoin().get<PluginRegistry>() }.getOrNull() ?: return
            val handler = registry.widgetHandler() ?: return
            val content = runCatching { handler.second.content(widgetId) }.getOrNull() ?: return
            manager.updateAppWidget(widgetId, views(context, handler.first.manifest.id, content))
        }

        private fun views(context: Context, pluginId: String, content: PluginWidgetContent): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.plugin_widget)
            views.setTextViewText(R.id.plugin_widget_title, content.title)
            views.setTextViewText(R.id.plugin_widget_body, content.body)
            content.icon?.let { views.setImageViewBitmap(R.id.plugin_widget_icon, it) }
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent(Intent.ACTION_MAIN).setPackage(context.packageName)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (content.pageId != null) {
                intent.putExtra(PluginIntents.EXTRA_PLUGIN_ID, pluginId)
                intent.putExtra(PluginIntents.EXTRA_PLUGIN_PAGE, content.pageId)
                content.args?.let { intent.putExtra(PluginIntents.EXTRA_PLUGIN_ARGS, it) }
            }
            val pending = PendingIntent.getActivity(
                context,
                content.title.hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.plugin_widget_root, pending)
            return views
        }

        private fun appWidgetIds(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(
                android.content.ComponentName(context, PluginWidgetProvider::class.java),
            )
    }
}
