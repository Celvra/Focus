package fan.x0.focus.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import kotlin.math.exp

@Composable
internal fun FocusAmbientBackdrop(activity: FocusAmbientActivity) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val palette = remember(scheme, dark) { ambientPalette(scheme, dark) }
    val progress = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(activity) {
        var last = withFrameNanos { it }
        var accumulated = 0L
        var speed = IDLE_SPEED
        while (true) {
            val now = withFrameNanos { it }
            val delta = (now - last).coerceIn(0L, MAX_FRAME_NANOS)
            last = now
            accumulated += delta
            if (accumulated < FRAME_INTERVAL_NANOS) continue
            val seconds = accumulated / NANOS_PER_SECOND
            accumulated = 0L
            val target = if (activity.working) WORKING_SPEED else IDLE_SPEED
            speed += (target - speed) * (1f - exp(-seconds / SPEED_RESPONSE_SECONDS))
            progress.floatValue = (progress.floatValue + seconds * speed / LOOP_SECONDS) % 1f
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawAmbientFlow(palette, progress.floatValue)
    }
}

private const val NANOS_PER_SECOND = 1_000_000_000f
private const val LOOP_SECONDS = 64f
private const val IDLE_SPEED = 1f
private const val WORKING_SPEED = 2.4f
private const val SPEED_RESPONSE_SECONDS = 1.8f
private const val FRAME_INTERVAL_NANOS = 33_333_333L
private const val MAX_FRAME_NANOS = 120_000_000L
