package fan.x0.focus.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import fan.x0.focus.core.designsystem.theme.LocalFocusMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal class FocusAmbientActivity {
    private var count = 0
    internal var working by mutableStateOf(false)
        private set

    internal fun acquire() {
        count += 1
        working = true
    }

    internal fun release() {
        count -= 1
        working = count > 0
    }
}

internal val LocalFocusAmbientActivity = staticCompositionLocalOf { FocusAmbientActivity() }

// Reports assistant work so the ambient background flows while it runs.
@Composable
fun FocusAmbientWorking(active: Boolean) {
    val activity = LocalFocusAmbientActivity.current
    DisposableEffect(activity, active) {
        if (active) activity.acquire()
        onDispose { if (active) activity.release() }
    }
}

@Composable
fun FocusAmbientBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val activity = remember { FocusAmbientActivity() }
    CompositionLocalProvider(LocalFocusAmbientActivity provides activity) {
        Box(modifier = modifier.fillMaxSize()) {
            FocusAmbientCanvas(activity)
            content()
        }
    }
}

@Composable
private fun FocusAmbientCanvas(activity: FocusAmbientActivity) {
    val working = activity.working
    val primary = MaterialTheme.colorScheme.primaryContainer
    val tertiary = MaterialTheme.colorScheme.tertiaryContainer
    val motion = LocalFocusMotion.current
    val flow = animateFloatAsState(
        targetValue = if (working) 1f else 0f,
        animationSpec = motion.effectsSlow(),
        label = "ambientFlow",
    )
    val phase = remember { Animatable(0f) }
    LaunchedEffect(working) {
        if (working) {
            while (true) {
                phase.animateTo(1f, tween(DRIFT_MS, easing = LinearEasing))
                phase.snapTo(0f)
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val amplitude = flow.value
        val angle = phase.value * 2f * PI.toFloat()
        val min = size.minDimension
        val drift = min * DRIFT_FRACTION * amplitude

        val primaryCenter = Offset(
            size.width * 0.88f + sin(angle) * drift,
            size.height * 0.04f + cos(angle) * drift * 0.7f,
        )
        val primaryRadius = min * 0.95f * (1f + sin(angle * 2f) * BREATH_FRACTION * amplitude)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primary.copy(alpha = 0.22f), Color.Transparent),
                center = primaryCenter,
                radius = primaryRadius,
            ),
            radius = primaryRadius,
            center = primaryCenter,
        )

        val tertiaryCenter = Offset(
            size.width * 0.06f - sin(angle) * drift * 0.8f,
            size.height * 0.92f - cos(angle) * drift * 0.6f,
        )
        val tertiaryRadius = min * 0.8f * (1f - sin(angle * 2f) * BREATH_FRACTION * amplitude)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(tertiary.copy(alpha = 0.16f), Color.Transparent),
                center = tertiaryCenter,
                radius = tertiaryRadius,
            ),
            radius = tertiaryRadius,
            center = tertiaryCenter,
        )
    }
}

private const val DRIFT_MS = 14_000
private const val DRIFT_FRACTION = 0.05f
private const val BREATH_FRACTION = 0.06f
