package fan.x0.focus.core.designsystem.components

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal class AmbientPalette(
    val base: Color,
    val colors: List<Color>,
    val alphas: FloatArray,
)

internal fun ambientPalette(scheme: ColorScheme, dark: Boolean): AmbientPalette {
    val colors = if (dark) {
        listOf(
            scheme.inversePrimary,
            scheme.primary,
            scheme.tertiary,
            scheme.tertiaryContainer,
            scheme.secondary,
        )
    } else {
        listOf(
            scheme.primaryContainer,
            scheme.tertiaryContainer,
            scheme.secondaryContainer,
            scheme.primary,
            scheme.tertiary,
        )
    }
    val alphas = if (dark) {
        floatArrayOf(0.63f, 0.4f, 0.37f, 0.52f, 0.29f)
    } else {
        floatArrayOf(0.71f, 0.57f, 0.63f, 0.18f, 0.16f)
    }
    return AmbientPalette(scheme.background, colors, alphas)
}

internal fun DrawScope.drawAmbientFlow(palette: AmbientPalette, progress: Float) {
    drawRect(color = palette.base)
    val angle = progress * FULL_TURN
    val shortest = size.minDimension
    val driftX = sin(angle + 0.4f) * GLOBAL_DRIFT_X
    val driftY = sin(angle * 2f + 1.2f) * GLOBAL_DRIFT_Y
    val zoom = 1f + sin(angle) * GLOBAL_BREATH
    AmbientLobes.forEach { lobe ->
        val color = palette.colors[lobe.color]
        val alpha = palette.alphas[lobe.color] * lobe.strength
        val phase = lobe.phase * FULL_TURN
        val center = Offset(
            x = size.width * (
                lobe.x +
                    driftX +
                    lobe.driftX * sin(angle * lobe.cycle + phase) +
                    lobe.driftX * SECONDARY_DRIFT * sin(angle * (lobe.cycle + 1) + phase * 1.6f)
            ),
            y = size.height * (
                lobe.y +
                    driftY +
                    lobe.driftY * cos(angle * (lobe.cycle + 1) + phase * 0.8f) +
                    lobe.driftY * SECONDARY_DRIFT * cos(angle * lobe.cycle + phase * 1.9f)
            ),
        )
        val radius = shortest * lobe.radius * zoom *
            (1f + lobe.breath * sin(angle * (lobe.cycle + 2) + phase))
        val rotation = lobe.tilt + lobe.wobble * sin(angle * (lobe.cycle + 1) + phase * 1.3f)
        val stretch = sin(angle * (lobe.cycle + 1) + phase * 1.1f) * STRETCH_BREATH
        withTransform({
            translate(center.x, center.y)
            rotate(rotation, pivot = Offset.Zero)
            scale(lobe.stretchX * (1f + stretch), lobe.stretchY * (1f - stretch), pivot = Offset.Zero)
        }) {
            drawCircle(
                brush = radialBrush(color, alpha, radius),
                radius = radius,
                center = Offset.Zero,
            )
        }
    }
    drawRect(
        brush = Brush.radialGradient(
            0.7f to palette.base.copy(alpha = 0f),
            1f to palette.base.copy(alpha = EDGE_ALPHA),
            center = size.center,
            radius = size.maxDimension * 0.62f,
        ),
    )
}

private fun radialBrush(color: Color, alpha: Float, radius: Float): Brush = Brush.radialGradient(
    colorStops = arrayOf(
        0f to color.copy(alpha = alpha),
        0.22f to color.copy(alpha = alpha * 0.9f),
        0.45f to color.copy(alpha = alpha * 0.68f),
        0.68f to color.copy(alpha = alpha * 0.4f),
        0.86f to color.copy(alpha = alpha * 0.16f),
        1f to color.copy(alpha = 0f),
    ),
    center = Offset.Zero,
    radius = radius,
)

private class AmbientLobe(
    val color: Int,
    val x: Float,
    val y: Float,
    val radius: Float,
    val driftX: Float,
    val driftY: Float,
    val cycle: Int,
    val phase: Float,
    val breath: Float,
    val stretchX: Float,
    val stretchY: Float,
    val tilt: Float,
    val wobble: Float,
    val strength: Float,
)

private val AmbientLobes = listOf(
    AmbientLobe(0, 0.02f, 0.02f, 1.04f, 0.085f, 0.07f, 1, 0.08f, 0.06f, 1.34f, 0.82f, -16f, 9f, 1f),
    AmbientLobe(1, 0.98f, 0.16f, 0.92f, 0.075f, 0.09f, 2, 0.41f, 0.07f, 0.82f, 1.26f, 22f, 11f, 0.95f),
    AmbientLobe(2, 0.42f, 0.55f, 1.21f, 0.06f, 0.05f, 1, 0.63f, 0.05f, 1.16f, 0.9f, 6f, 12f, 0.52f),
    AmbientLobe(3, 0.88f, 0.76f, 0.78f, 0.1f, 0.07f, 2, 0.27f, 0.08f, 1.22f, 0.86f, -28f, 10f, 1f),
    AmbientLobe(4, 0.08f, 0.62f, 0.73f, 0.08f, 0.1f, 1, 0.79f, 0.07f, 0.88f, 1.2f, 30f, 12f, 0.9f),
    AmbientLobe(2, 0.55f, 1.02f, 1.09f, 0.07f, 0.05f, 2, 0.52f, 0.05f, 1.1f, 0.94f, 14f, 8f, 0.48f),
    AmbientLobe(1, 0.24f, 0.34f, 0.49f, 0.075f, 0.06f, 2, 0.18f, 0.09f, 1.42f, 0.72f, -32f, 14f, 0.38f),
    AmbientLobe(2, 0.74f, 0.66f, 0.44f, 0.07f, 0.07f, 1, 0.58f, 0.1f, 1.5f, 0.7f, 26f, 13f, 0.34f),
)

private const val SECONDARY_DRIFT = 0.35f
private const val STRETCH_BREATH = 0.06f
private const val GLOBAL_DRIFT_X = 0.012f
private const val GLOBAL_DRIFT_Y = 0.01f
private const val GLOBAL_BREATH = 0.025f
private const val EDGE_ALPHA = 0.3f
private const val FULL_TURN = 2f * PI.toFloat()
