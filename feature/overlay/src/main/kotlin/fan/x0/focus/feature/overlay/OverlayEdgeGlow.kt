package fan.x0.focus.feature.overlay

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.SweepGradient
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class OverlayScene { Bar, Selection, Result }

@Composable
internal fun OverlayGlow(scene: OverlayScene) {
    var appeared by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 240),
        label = "overlayGlowAlpha",
    )
    LaunchedEffect(Unit) { appeared = true }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha },
    ) {
        GlowScrim()
        Crossfade(
            targetState = scene,
            animationSpec = tween(durationMillis = 260),
            label = "overlayScene",
        ) { current ->
            when (current) {
                OverlayScene.Bar -> BarGlow()
                OverlayScene.Selection -> SelectionGlow()
                OverlayScene.Result -> ResultGlow()
            }
        }
    }
}

@Composable
private fun GlowScrim() {
    val scheme = MaterialTheme.colorScheme
    val shade = remember(scheme) { lerp(scheme.scrim, scheme.primary, 0.14f) }
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                0.05f to shade.copy(alpha = 0.02f),
                0.5f to shade.copy(alpha = 0.18f),
                1f to shade.copy(alpha = 0.5f),
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    scheme.primary.copy(alpha = 0.26f),
                    scheme.tertiary.copy(alpha = 0.1f),
                    Color.Transparent,
                ),
                center = Offset(size.width / 2f, size.height),
                radius = size.height * 0.55f,
            ),
        )
    }
}

private data class GlowSparkle(
    val x: Float,
    val y: Float,
    val size: Float,
    val phase: Float,
    val speed: Float,
    val color: Int,
)

@Composable
private fun BarGlow() {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "barGlow")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "barGlowTime",
    )
    val colors = remember(scheme) {
        listOf(scheme.primary, scheme.tertiary, scheme.secondary, scheme.inversePrimary)
    }
    val sparkles = remember {
        val random = Random(17)
        List(120) {
            GlowSparkle(
                x = random.nextFloat(),
                y = random.nextFloat(),
                size = 0.8f + random.nextFloat() * 2.4f,
                phase = random.nextFloat() * (2f * PI.toFloat()),
                speed = 0.5f + random.nextFloat() * 0.9f,
                color = random.nextInt(colors.size),
            )
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val tau = 2f * PI.toFloat()
        sparkles.forEach { sparkle ->
            val wave = (sin(time * tau * sparkle.speed + sparkle.phase) + 1f) / 2f
            val twinkle = 0.25f + 0.75f * wave
            val depth = 0.35f + sparkle.y * 0.65f
            val alpha = twinkle * depth * 0.7f
            if (alpha <= 0.02f) return@forEach
            val center = Offset(
                x = sparkle.x * size.width + sin(time * tau + sparkle.phase) * 5.dp.toPx(),
                y = sparkle.y * size.height + cos(time * tau + sparkle.phase) * 5.dp.toPx(),
            )
            val radius = sparkle.size.dp.toPx()
            val color = colors[sparkle.color].copy(alpha = alpha)
            if (radius < 1.2.dp.toPx()) {
                drawCircle(color = color, radius = radius * 0.55f, center = center)
            } else {
                drawLine(
                    color = color,
                    start = Offset(center.x - radius, center.y),
                    end = Offset(center.x + radius, center.y),
                    strokeWidth = radius * 0.5f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(center.x, center.y - radius),
                    end = Offset(center.x, center.y + radius),
                    strokeWidth = radius * 0.5f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun SelectionGlow() {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "selectionGlow")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "selectionGlowAngle",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "selectionGlowPulse",
    )
    val colors = remember(scheme) {
        listOf(
            scheme.primary,
            scheme.tertiary,
            scheme.secondary,
            scheme.inversePrimary,
            scheme.primary,
        ).map { it.toArgb() }.toIntArray()
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val inset = 1.5.dp.toPx()
        val radius = 30.dp.toPx()
        drawSweepRings(
            colors = colors,
            angle = angle,
            pulse = pulse,
            layers = listOf(
                2.5.dp.toPx() to 0.98f,
                7.dp.toPx() to 0.38f,
                18.dp.toPx() to 0.14f,
                34.dp.toPx() to 0.05f,
            ),
            inset = inset,
            radius = radius,
        )
        drawRoundRect(
            color = scheme.surface.copy(alpha = 0.55f),
            topLeft = Offset(3.5.dp.toPx(), 3.5.dp.toPx()),
            size = Size(
                (size.width - 7.dp.toPx()).coerceAtLeast(0f),
                (size.height - 7.dp.toPx()).coerceAtLeast(0f),
            ),
            cornerRadius = CornerRadius(radius - 2.dp.toPx()),
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}

@Composable
private fun ResultGlow() {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "resultGlow")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "resultGlowAngle",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "resultGlowPulse",
    )
    val colors = remember(scheme) {
        listOf(
            scheme.primary,
            scheme.tertiary,
            scheme.secondary,
            scheme.inversePrimary,
            scheme.primary,
        ).map { it.toArgb() }.toIntArray()
    }
    val aura = remember(scheme) {
        listOf(
            scheme.tertiary,
            scheme.primary,
            scheme.inversePrimary,
            scheme.secondary,
            scheme.tertiary,
        ).map { it.toArgb() }.toIntArray()
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val inset = 1.5.dp.toPx()
        val radius = 32.dp.toPx()
        drawSweepRings(
            colors = colors,
            angle = angle,
            pulse = pulse,
            layers = listOf(
                110.dp.toPx() to 0.035f,
                84.dp.toPx() to 0.05f,
                60.dp.toPx() to 0.07f,
                36.dp.toPx() to 0.1f,
                16.dp.toPx() to 0.16f,
                5.dp.toPx() to 0.28f,
            ),
            inset = inset,
            radius = radius,
        )
        drawSweepRings(
            colors = aura,
            angle = -angle * 0.72f + 120f,
            pulse = pulse,
            layers = listOf(
                72.dp.toPx() to 0.03f,
                40.dp.toPx() to 0.05f,
                12.dp.toPx() to 0.08f,
            ),
            inset = inset,
            radius = radius,
        )
    }
}

internal fun DrawScope.drawSweepRings(
    colors: IntArray,
    angle: Float,
    pulse: Float,
    layers: List<Pair<Float, Float>>,
    inset: Float,
    radius: Float,
) {
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    val positions = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
    val shader = SweepGradient(centerX, centerY, colors, positions).apply {
        setLocalMatrix(Matrix().apply { postRotate(angle, centerX, centerY) })
    }
    val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.shader = shader
    }
    layers.forEach { (width, layerAlpha) ->
        paint.strokeWidth = width
        paint.alpha = (layerAlpha * pulse * 255).toInt().coerceIn(0, 255)
        drawContext.canvas.nativeCanvas.drawRoundRect(
            inset,
            inset,
            size.width - inset,
            size.height - inset,
            radius,
            radius,
            paint,
        )
    }
}
