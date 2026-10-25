package fan.x0.focus.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

@Composable
internal fun FocusAmbientBackdrop(activity: FocusAmbientActivity) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val palette = remember(scheme, dark) { ambientPalette(scheme, dark) }
    val phase = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(phase, activity) {
        var last = withFrameNanos { it }
        var accumulated = 0L
        var speed = IDLE_SPEED
        while (true) {
            val now = withFrameNanos { it }
            val delta = (now - last).coerceIn(0L, MAX_FRAME_NANOS)
            last = now
            accumulated += delta
            val seconds = accumulated / NANOS_PER_SECOND
            val target = if (activity.working) WORKING_SPEED else IDLE_SPEED
            speed += (target - speed) * (1f - exp(-seconds / SPEED_RESPONSE_SECONDS))
            if (accumulated < FRAME_INTERVAL_NANOS) continue
            accumulated = 0L
            phase.floatValue = (phase.floatValue + seconds * speed * FULL_TURN / CYCLE_SECONDS) % FULL_TURN
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(color = palette.base)
        val time = phase.floatValue
        val shortest = size.minDimension
        AmbientBlobs.forEach { blob ->
            val angle = time * blob.orbit + blob.phase
            val alpha = palette.alphas[blob.colorIndex] * (0.94f + 0.06f * sin(angle))
            val center = Offset(
                x = size.width * (blob.x + sin(angle) * blob.driftX),
                y = size.height * (blob.y + cos(angle * 0.72f + blob.phase) * blob.driftY),
            )
            val radius = shortest * blob.radius * (1f + sin(angle * 0.58f + 1.7f) * blob.breath)
            val color = palette.colors[blob.colorIndex]
            withTransform({
                translate(center.x, center.y)
                rotate(blob.spin * time, pivot = Offset.Zero)
                scale(blob.stretchX, blob.stretchY, pivot = Offset.Zero)
            }) {
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to color.copy(alpha = alpha),
                        0.45f to color.copy(alpha = alpha * 0.72f),
                        1f to Color.Transparent,
                        center = Offset.Zero,
                        radius = radius,
                    ),
                    radius = radius,
                    center = Offset.Zero,
                )
            }
        }
    }
}

private class AmbientPalette(
    val base: Color,
    val colors: List<Color>,
    val alphas: List<Float>,
)

private fun ambientPalette(scheme: ColorScheme, dark: Boolean): AmbientPalette {
    val colors = listOf(
        scheme.primaryContainer,
        scheme.tertiaryContainer,
        scheme.secondaryContainer,
        scheme.primary,
        scheme.tertiary,
    )
    val alphas = if (dark) {
        listOf(0.55f, 0.5f, 0.45f, 0.3f, 0.26f)
    } else {
        listOf(0.65f, 0.55f, 0.5f, 0.32f, 0.28f)
    }
    return AmbientPalette(scheme.background, colors, alphas)
}

private class AmbientBlob(
    val colorIndex: Int,
    val x: Float,
    val y: Float,
    val radius: Float,
    val driftX: Float,
    val driftY: Float,
    val orbit: Float,
    val phase: Float,
    val breath: Float,
    val spin: Float,
    val stretchX: Float,
    val stretchY: Float,
)

private val AmbientBlobs = listOf(
    AmbientBlob(0, 0.08f, -0.05f, 0.95f, 0.14f, 0.1f, 0.85f, 0f, 0.08f, 7f, 1.18f, 0.86f),
    AmbientBlob(1, 0.95f, 0.92f, 0.9f, 0.12f, 0.13f, 0.7f, 2.4f, 0.07f, -5f, 0.88f, 1.22f),
    AmbientBlob(2, 0.5f, 0.48f, 1f, 0.16f, 0.11f, 0.55f, 4.1f, 0.09f, 9f, 1.1f, 0.92f),
    AmbientBlob(3, 0.88f, 0.12f, 0.62f, 0.18f, 0.14f, 1.25f, 1.3f, 0.1f, -8f, 1.25f, 0.8f),
    AmbientBlob(4, 0.1f, 0.84f, 0.56f, 0.15f, 0.16f, 1.05f, 3.5f, 0.09f, 6f, 0.85f, 1.25f),
)

private const val NANOS_PER_SECOND = 1_000_000_000f
private const val CYCLE_SECONDS = 34f
private const val IDLE_SPEED = 0.22f
private const val WORKING_SPEED = 1f
private const val SPEED_RESPONSE_SECONDS = 1.8f
private const val FRAME_INTERVAL_NANOS = 33_333_333L
private const val MAX_FRAME_NANOS = 120_000_000L
private const val FULL_TURN = 2f * PI.toFloat()
