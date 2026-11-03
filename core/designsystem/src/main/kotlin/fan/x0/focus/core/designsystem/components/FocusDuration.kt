package fan.x0.focus.core.designsystem.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import fan.x0.focus.core.designsystem.R
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
internal fun focusDurationLabel(
    durationMs: Long,
    startedAt: Long,
    running: Boolean,
): String? {
    val shown = when {
        running && durationMs <= 0 && startedAt > 0 -> liveElapsed(startedAt)
        durationMs > 0 -> durationMs
        else -> return null
    }
    return durationLabel(shown)
}

@Composable
internal fun FocusDurationText(
    durationMs: Long,
    startedAt: Long,
    running: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val label = focusDurationLabel(durationMs, startedAt, running) ?: return
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color.copy(alpha = 0.6f),
        modifier = modifier,
    )
}

@Composable
private fun liveElapsed(startedAt: Long): Long {
    var now by remember(startedAt) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(200)
        }
    }
    return (now - startedAt).coerceAtLeast(0)
}

@Composable
private fun durationLabel(durationMs: Long): String {
    val seconds = durationMs / 1000.0
    val value = if (seconds >= 10) {
        String.format(Locale.US, "%.0f", seconds)
    } else {
        String.format(Locale.US, "%.1f", seconds)
    }
    return stringResource(R.string.common_seconds, value)
}
