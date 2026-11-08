package fan.x0.focus.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.widget.RemoteViews
import fan.x0.focus.AssistantEntryActivity
import fan.x0.focus.MainActivity
import fan.x0.focus.R
import fan.x0.focus.data.assistant.ChatRole
import fan.x0.focus.data.assistant.ChatStore
import fan.x0.focus.data.assistant.MainChannelSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

class FocusWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val views = build(context)
                appWidgetIds.forEach { id ->
                    appWidgetManager.updateAppWidget(id, views)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, FocusWidgetProvider::class.java))
            if (ids.isEmpty()) return
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                val views = build(context)
                ids.forEach { id -> manager.updateAppWidget(id, views) }
            }
        }

        private suspend fun build(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_focus)
            val koin = KoinPlatform.getKoin()
            val chats = koin.get<ChatStore>()
            val main = koin.get<MainChannelSession>()
            val messages = chats.messages(ChatStore.MAIN_CONVERSATION_ID, limit = 8)
            val latest = messages
                .lastOrNull { it.role == ChatRole.Assistant && it.content.isNotBlank() }
                ?.content
                ?.lineSequence()
                ?.firstOrNull { it.isNotBlank() }
                ?.take(180)
                .orEmpty()
            val active = chats.activeSideChats().size
            val status = when {
                main.state.value.working -> context.getString(R.string.widget_working)
                active > 0 -> context.resources.getQuantityString(
                    R.plurals.widget_side_running,
                    active,
                    active,
                )
                else -> latest.ifBlank { context.getString(R.string.widget_idle) }
            }
            val night = (context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            val surface = surfaceColor(context, night)
            val onSurface = if (night) 0xFFE5E2DD.toInt() else 0xFF1A1C19.toInt()
            val accent = accentColor(context)
            views.setImageViewBitmap(R.id.widget_bg, background(surface))
            views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_title))
            views.setTextViewText(R.id.widget_body, status)
            views.setTextColor(R.id.widget_title, onSurface)
            views.setTextColor(R.id.widget_body, onSurface)
            views.setTextColor(R.id.widget_ask, accent)
            views.setTextColor(R.id.widget_main, onSurface)
            views.setInt(R.id.widget_icon, "setColorFilter", accent)
            val ask = PendingIntent.getActivity(
                context,
                1,
                Intent(context, AssistantEntryActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val mainIntent = PendingIntent.getActivity(
                context,
                2,
                Intent(context, MainActivity::class.java)
                    .setAction(Intent.ACTION_ASSIST)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_ask, ask)
            views.setOnClickPendingIntent(R.id.widget_main, mainIntent)
            return views
        }

        private fun surfaceColor(context: Context, night: Boolean): Int {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return context.getColor(
                    if (night) android.R.color.system_neutral1_900 else android.R.color.system_neutral1_50,
                )
            }
            return if (night) 0xFF1A1C19.toInt() else 0xFFFBF8F2.toInt()
        }

        private fun accentColor(context: Context): Int {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return context.getColor(android.R.color.system_accent1_500)
            }
            return 0xFF6750A4.toInt()
        }

        private fun background(color: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(720, 360, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
            canvas.drawRoundRect(RectF(0f, 0f, 720f, 360f), 48f, 48f, paint)
            return bitmap
        }
    }
}
