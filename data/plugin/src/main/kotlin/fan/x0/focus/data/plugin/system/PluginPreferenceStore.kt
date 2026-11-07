package fan.x0.focus.data.plugin.system

import android.content.Context

object PluginPreferenceStore {
    private const val NAME = "plugin_system"

    fun set(context: Context, key: String, value: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putString(key, value).apply()
    }

    fun get(context: Context, key: String): String? =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString(key, null)

    fun removeWidget(context: Context, widgetId: Int) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .remove(widgetKey(widgetId))
            .apply()
    }

    fun widgetKey(widgetId: Int): String = "widget_$widgetId"
}
