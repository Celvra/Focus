package fan.x0.focus.core.designsystem.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

internal data class ArcSeed(
    val a: Float,
    val k: Float,
    val tilt: Float,
    val speed: Float,
    val phase: Float,
    val sweep: Float,
    val hue: Float,
    val hueSpan: Float,
    val width: Float,
    val cx: Float,
    val cy: Float,
)

internal data class ArcRender(
    val front: Path,
    val back: Path,
    val brush: Brush,
    val width: Float,
)

internal data class ParticleSeed(val birth: Float, val angle: Float, val rho: Float)

internal enum class DotKind { Circle, Tear }

internal data class DotSpec(
    val x: Float,
    val y: Float,
    val radius: Float,
    val opacity: Float,
    val depth: Float = 1f,
    val kind: DotKind = DotKind.Circle,
    val rotation: Float = 0f,
)

internal const val NOTIF_ANGLE = -42f
internal const val NOTIF_DIST = 1.003f
internal const val NOTIF_R = 0.15f
internal const val NOTIF_POP = 1.14f
internal const val NOTIF_MARGIN = 0.054f
internal val NOTIF_BLUE = Color(0xFF2496E8)

internal fun arcRender(seed: ArcSeed, t: Float, scale: Float): ArcRender {
    val spin = seed.phase + t * seed.speed * 2f * PI.toFloat()
    val cu = cos(seed.tilt)
    val su = sin(seed.tilt)
    val kz = sqrt((1f - seed.k * seed.k).coerceAtLeast(0f))
    val span = seed.sweep * 2f * PI.toFloat()

    val front = Path()
    val back = Path()
    var prev: Boolean? = null
    val steps = 64
    for (i in 0..steps) {
        val th = spin + i.toFloat() / steps * span
        val ct = cos(th)
        val st = sin(th)
        val x = seed.a * (ct * cu + st * -su * seed.k) + seed.cx
        val y = seed.a * (ct * su + st * cu * seed.k) + seed.cy
        val z = seed.a * st * kz
        val behind = z < 0f
        val sx = x * scale
        val sy = y * scale
        val path = if (behind) back else front
        if (behind != prev) path.moveTo(sx, sy) else path.lineTo(sx, sy)
        prev = behind
    }

    val gx = cos(seed.tilt) * seed.a * scale
    val gy = sin(seed.tilt) * seed.a * scale
    val brush = Brush.linearGradient(
        colors = listOf(
            wheel(seed.hue),
            wheel(seed.hue + seed.hueSpan * 0.5f),
            wheel(seed.hue + seed.hueSpan),
        ),
        start = Offset(seed.cx * scale - gx, seed.cy * scale - gy),
        end = Offset(seed.cx * scale + gx, seed.cy * scale + gy),
    )
    return ArcRender(front, back, brush, seed.width * scale)
}

internal fun DrawScope.drawArc(arc: ArcRender, opacity: Float, front: Boolean) {
    if (opacity <= 0.01f) return
    drawPath(
        path = if (front) arc.front else arc.back,
        brush = arc.brush,
        alpha = opacity,
        style = Stroke(width = arc.width, cap = StrokeCap.Round),
    )
}

internal fun wheel(hue: Float, s: Float = 0.55f, l: Float = 0.62f): Color {
    val h = ((hue % 360f) + 360f) % 360f
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r, g, b) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m)
}

internal fun particles(t: Float, scale: Float): List<DotSpec> {
    val out = mutableListOf<DotSpec>()
    for (seed in PARTICLE_SEEDS) {
        val u = t - seed.birth
        if (u < 0f || u > 0.62f) continue
        val rho = seed.rho * 0.75f.pow(u * 10f)
        val a = seed.angle + u * 100f * PI.toFloat() / 180f
        out += DotSpec(
            x = cos(a) * rho * scale,
            y = sin(a) * rho * scale,
            radius = (0.04f + 0.028f * (u / 0.55f).coerceIn(0f, 1f)) * scale,
            opacity = (u / 0.06f).coerceIn(0f, 1f) * ((0.62f - u) / 0.08f).coerceIn(0f, 1f),
            depth = (1f - rho / 0.8f).coerceIn(0f, 1f),
        )
    }
    return out
}
