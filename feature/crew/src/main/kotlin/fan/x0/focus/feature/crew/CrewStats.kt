package fan.x0.focus.feature.crew

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fan.x0.focus.core.designsystem.components.FocusStatsLine
import fan.x0.focus.core.designsystem.theme.LocalFocusMotion
import fan.x0.focus.data.assistant.ChatDetailConfig
import fan.x0.focus.data.assistant.ChatStatField
import fan.x0.focus.data.assistant.MessageStats
import java.util.Locale

@Composable
internal fun CrewStatsLine(
    stats: MessageStats,
    config: ChatDetailConfig,
    modifier: Modifier = Modifier,
) {
    val motion = LocalFocusMotion.current
    val input by animateIntAsState(stats.inputTokens, motion.effectsDefault(), label = "crewStatsInput")
    val output by animateIntAsState(stats.outputTokens, motion.effectsDefault(), label = "crewStatsOutput")
    val total by animateIntAsState(stats.totalTokens, motion.effectsDefault(), label = "crewStatsTotal")
    val cached by animateIntAsState(stats.cachedTokens, motion.effectsDefault(), label = "crewStatsCached")
    val round by animateIntAsState(stats.round, motion.effectsDefault(), label = "crewStatsRound")
    val step by animateIntAsState(stats.step, motion.effectsDefault(), label = "crewStatsStep")
    val duration by animateIntAsState(
        targetValue = stats.durationMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
        animationSpec = motion.effectsDefault(),
        label = "crewStatsDuration",
    )
    val parts = config.order
        .filter { it in config.enabled }
        .mapNotNull { field ->
            when (field) {
                ChatStatField.Provider -> stats.providerName
                    .ifBlank { stats.providerId }
                    .takeIf { it.isNotBlank() }
                    ?.let { "${stringResource(R.string.crew_stats_provider)} $it" }

                ChatStatField.Model -> stats.model
                    .takeIf { it.isNotBlank() }
                    ?.let { "${stringResource(R.string.crew_stats_model)} $it" }

                ChatStatField.Input -> stats.inputTokens
                    .takeIf { it > 0 }
                    ?.let { tokenText(input)?.let { text ->
                        "${stringResource(R.string.crew_stats_input)} $text"
                    } }

                ChatStatField.Output -> stats.outputTokens
                    .takeIf { it > 0 }
                    ?.let { tokenText(output)?.let { text ->
                        "${stringResource(R.string.crew_stats_output)} $text"
                    } }

                ChatStatField.Total -> stats.totalTokens
                    .takeIf { it > 0 }
                    ?.let { tokenText(total)?.let { text ->
                        "${stringResource(R.string.crew_stats_total)} $text"
                    } }

                ChatStatField.Cached -> stats.cachedTokens
                    .takeIf { it > 0 }
                    ?.let { tokenText(cached)?.let { text ->
                        "${stringResource(R.string.crew_stats_cached)} $text"
                    } }

                ChatStatField.Round -> stats.round
                    .takeIf { it > 0 }
                    ?.let { stringResource(R.string.crew_stats_round_value, round.coerceAtLeast(1)) }

                ChatStatField.Step -> stats.step
                    .takeIf { it > 0 }
                    ?.let { stringResource(R.string.crew_stats_step_value, step.coerceAtLeast(1)) }

                ChatStatField.Duration -> stats.durationMs
                    .takeIf { it > 0 }
                    ?.let { durationText(duration, stats.durationMs) }
            }
        }
    FocusStatsLine(parts = parts, modifier = modifier)
}

@Composable
private fun tokenText(tokens: Int): String? {
    if (tokens <= 0) return null
    val thousands = tokens / 1000.0
    val value = if (thousands >= 100) {
        String.format(Locale.US, "%.0f", thousands)
    } else {
        String.format(Locale.US, "%.1f", thousands)
    }
    return stringResource(R.string.crew_stats_tokens, value)
}

@Composable
private fun durationText(durationMs: Int, targetMs: Long): String? {
    val shown = if (durationMs > 0) durationMs.toLong() else targetMs
    if (shown <= 0) return null
    val seconds = shown / 1000.0
    val value = if (seconds >= 10) {
        String.format(Locale.US, "%.0f", seconds)
    } else {
        String.format(Locale.US, "%.1f", seconds)
    }
    return stringResource(R.string.crew_stats_seconds, value)
}
