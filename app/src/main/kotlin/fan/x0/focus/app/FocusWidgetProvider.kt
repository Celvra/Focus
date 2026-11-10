package fan.x0.focus.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.components.FilledButton
import androidx.glance.appwidget.components.OutlineButton
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import fan.x0.focus.AssistantEntryActivity
import fan.x0.focus.R
import fan.x0.focus.core.designsystem.theme.focusColorScheme
import fan.x0.focus.core.notify.R as NotifyR
import fan.x0.focus.data.assistant.ChatStore
import fan.x0.focus.data.assistant.ChatTurnRunner
import org.koin.mp.KoinPlatform

class FocusWidgetProvider : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FocusWidget()
}

class FocusWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = readSnapshot()
        provideContent {
            val colors = ColorProviders(
                light = focusColorScheme(context, dark = false),
                dark = focusColorScheme(context, dark = true),
            )
            GlanceTheme(colors = colors) {
                FocusWidgetContent(snapshot)
            }
        }
    }

    private suspend fun readSnapshot(): WidgetSnapshot {
        return runCatching {
            val koin = KoinPlatform.getKoin()
            val chats = koin.get<ChatStore>()
            val turns = koin.get<ChatTurnRunner>()
            val main = chats.ensureMainConversation()
            val latest = chats.messages(main.id, limit = 8)
                .lastOrNull { it.content.isNotBlank() }
                ?.content
                ?.let(::readable)
                .orEmpty()
            WidgetSnapshot(
                headline = latest,
                working = turns.isRunning(main.id),
                active = chats.activeSideChats().size,
            )
        }.getOrElse {
            WidgetSnapshot(headline = "", working = false, active = 0)
        }
    }

    private fun readable(text: String): String {
        return text
            .replace(Regex("```.*?```", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("\\[([^]]*)]\\([^)]*\\)"), "$1")
            .replace(Regex("[#>*_`~]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(240)
    }
}

private data class WidgetSnapshot(
    val headline: String,
    val working: Boolean,
    val active: Int,
)

@Composable
private fun FocusWidgetContent(snapshot: WidgetSnapshot) {
    val context = LocalContext.current
    val size = LocalSize.current
    val compact = size.height < 160.dp
    val headline = snapshot.headline.ifBlank { context.getString(R.string.widget_idle) }
    val status = when {
        snapshot.working -> context.getString(R.string.widget_status_busy)
        snapshot.active > 0 -> context.resources.getQuantityString(
            R.plurals.widget_tasks_running,
            snapshot.active,
            snapshot.active,
        )
        else -> context.getString(R.string.widget_status_idle)
    }
    val active = snapshot.working || snapshot.active > 0
    Scaffold(
        modifier = GlanceModifier.clickable(actionStartActivity<AssistantEntryActivity>()),
        titleBar = {
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = if (compact) 8.dp else 12.dp, end = 16.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(if (compact) 28.dp else 32.dp)
                        .background(GlanceTheme.colors.primaryContainer)
                        .cornerRadius(if (compact) 14.dp else 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        provider = ImageProvider(NotifyR.drawable.ic_notification_mascot),
                        contentDescription = null,
                        modifier = GlanceModifier.size(if (compact) 19.dp else 22.dp),
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer),
                    )
                }
                Text(
                    text = context.getString(R.string.widget_title),
                    modifier = GlanceModifier.padding(start = 10.dp),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = if (compact) 14.sp else 16.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.defaultWeight())
                StatusPill(text = status, active = active)
            }
        },
        horizontalPadding = 16.dp,
    ) {
        Column(GlanceModifier.fillMaxSize().padding(top = if (compact) 4.dp else 10.dp)) {
            Text(
                text = headline,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = if (compact) 17.sp else 20.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = when {
                    compact -> 1
                    size.height < 210.dp -> 2
                    else -> 3
                },
            )
            Spacer(GlanceModifier.defaultWeight())
            Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                FilledButton(
                    text = context.getString(R.string.widget_ask),
                    onClick = actionStartActivity<AssistantEntryActivity>(),
                )
                Spacer(GlanceModifier.width(8.dp))
                OutlineButton(
                    text = context.getString(R.string.widget_main),
                    contentColor = GlanceTheme.colors.primary,
                    onClick = actionStartActivity<AssistantEntryActivity>(),
                )
            }
            Spacer(GlanceModifier.size(if (compact) 6.dp else 12.dp))
        }
    }
}

@Composable
private fun StatusPill(text: String, active: Boolean) {
    val background = if (active) {
        GlanceTheme.colors.tertiaryContainer
    } else {
        GlanceTheme.colors.surface
    }
    val content = if (active) {
        GlanceTheme.colors.onTertiaryContainer
    } else {
        GlanceTheme.colors.onSurfaceVariant
    }
    val dot = if (active) {
        GlanceTheme.colors.tertiary
    } else {
        GlanceTheme.colors.outline
    }
    Row(
        modifier = GlanceModifier
            .background(background)
            .cornerRadius(12.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .size(6.dp)
                .background(dot)
                .cornerRadius(3.dp),
        ) {}
        Text(
            text = text,
            modifier = GlanceModifier.padding(start = 5.dp),
            style = TextStyle(
                color = content,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
}
