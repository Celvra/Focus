package fan.x0.focus.core.designsystem.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fan.x0.focus.core.designsystem.R
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
internal fun FocusDurationText(
    durationMs: Long,
    startedAt: Long,
    running: Boolean,
    modifier: Modifier = Modifier,
) {
    val shown = when {
        running && durationMs <= 0 && startedAt > 0 -> liveElapsed(startedAt)
        durationMs > 0 -> durationMs
        else -> return
    }
    Text(
        text = durationLabel(shown),
        style = MaterialTheme.typography.labelSmall,
        color = LocalContentColor.current.copy(alpha = 0.6f),
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
