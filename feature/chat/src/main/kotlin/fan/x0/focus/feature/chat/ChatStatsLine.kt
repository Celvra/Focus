package fan.x0.focus.feature.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.theme.LocalFocusMotion
import fan.x0.focus.data.assistant.ChatDetailConfig
import fan.x0.focus.data.assistant.ChatStatField
import fan.x0.focus.data.assistant.MessageStats
import java.util.Locale

@Composable
internal fun ChatStatsLine(
    stats: MessageStats,
    config: ChatDetailConfig,
    modifier: Modifier = Modifier,
) {
    val motion = LocalFocusMotion.current
    val input by animateIntAsState(
        targetValue = stats.inputTokens,
        animationSpec = motion.effectsDefault(),
        label = "statsInput",
    )
    val output by animateIntAsState(
        targetValue = stats.outputTokens,
        animationSpec = motion.effectsDefault(),
        label = "statsOutput",
    )
    val total by animateIntAsState(
        targetValue = stats.totalTokens,
        animationSpec = motion.effectsDefault(),
        label = "statsTotal",
    )
    val cached by animateIntAsState(
        targetValue = stats.cachedTokens,
        animationSpec = motion.effectsDefault(),
        label = "statsCached",
    )
    val round by animateIntAsState(
        targetValue = stats.round,
        animationSpec = motion.effectsDefault(),
        label = "statsRound",
    )
    val step by animateIntAsState(
        targetValue = stats.step,
        animationSpec = motion.effectsDefault(),
        label = "statsStep",
    )
    val duration by animateIntAsState(
        targetValue = stats.durationMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
        animationSpec = tween(durationMillis = 900, easing = LinearEasing),
        label = "statsDuration",
    )
    val parts = config.order
        .filter { it in config.enabled }
        .mapNotNull { field ->
            when (field) {
                ChatStatField.Provider -> stats.providerName
                    .ifBlank { stats.providerId }
                    .takeIf { it.isNotBlank() }
                    ?.let { "${stringResource(R.string.chat_stats_provider)} $it" }

                ChatStatField.Model -> stats.model
                    .takeIf { it.isNotBlank() }
                    ?.let { "${stringResource(R.string.chat_stats_model)} $it" }

                ChatStatField.Input -> stats.inputTokens
                    .takeIf { it > 0 }
                    ?.let { tokenText(input, stats.inputTokens)?.let { text ->
                        stringResource(R.string.chat_stats_input) + " " + text
                    } }

                ChatStatField.Output -> stats.outputTokens
                    .takeIf { it > 0 }
                    ?.let { tokenText(output, stats.outputTokens)?.let { text ->
                        stringResource(R.string.chat_stats_output) + " " + text
                    } }

                ChatStatField.Total -> stats.totalTokens
                    .takeIf { it > 0 }
                    ?.let { tokenText(total, stats.totalTokens)?.let { text ->
                        stringResource(R.string.chat_stats_total) + " " + text
                    } }

                ChatStatField.Cached -> stats.cachedTokens
                    .takeIf { it > 0 }
                    ?.let { tokenText(cached, stats.cachedTokens)?.let { text ->
                        stringResource(R.string.chat_stats_cached) + " " + text
                    } }

                ChatStatField.Round -> stats.round
                    .takeIf { it > 0 }
                    ?.let { stringResource(R.string.chat_stats_round_value, round.coerceAtLeast(1)) }

                ChatStatField.Step -> stats.step
                    .takeIf { it > 0 }
                    ?.let { stringResource(R.string.chat_stats_step_value, step.coerceAtLeast(1)) }

                ChatStatField.Duration -> stats.durationMs
                    .takeIf { it > 0 }
                    ?.let { durationText(duration.toLong(), stats.durationMs) }
            }
        }
    if (parts.isEmpty()) return
    Text(
        text = parts.joinToString(" · "),
        modifier = modifier.padding(start = 6.dp, top = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

internal fun MessageStats.hasStats(config: ChatDetailConfig): Boolean =
    config.order.any { field ->
        field in config.enabled && when (field) {
            ChatStatField.Provider -> providerName.isNotBlank() || providerId.isNotBlank()
            ChatStatField.Model -> model.isNotBlank()
            ChatStatField.Input -> inputTokens > 0
            ChatStatField.Output -> outputTokens > 0
            ChatStatField.Total -> totalTokens > 0
            ChatStatField.Cached -> cachedTokens > 0
            ChatStatField.Round -> round > 0
            ChatStatField.Step -> step > 0
            ChatStatField.Duration -> durationMs > 0
        }
    }

@Composable
private fun tokenText(tokens: Int, target: Int): String? {
    val shown = if (tokens > 0) tokens else target
    if (shown <= 0) return null
    val thousands = shown / 1000.0
    val value = if (thousands >= 100) {
        String.format(Locale.US, "%.0f", thousands)
    } else {
        String.format(Locale.US, "%.1f", thousands)
    }
    return stringResource(R.string.chat_stats_tokens, value)
}

@Composable
private fun durationText(durationMs: Long, target: Long): String? {
    val shown = if (durationMs > 0) durationMs else target
    if (shown <= 0) return null
    val seconds = shown / 1000.0
    val value = if (seconds >= 10) {
        String.format(Locale.US, "%.0f", seconds)
    } else {
        String.format(Locale.US, "%.1f", seconds)
    }
    return stringResource(R.string.chat_stats_seconds, value)
}
