package fan.x0.focus.core.designsystem.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

internal data class AvatarShape(
    val exponent: Float,
    val lobes: Int = 0,
    val amplitude: Float = 0f,
    val rotation: Float = 0f,
)

internal val shapePresets = listOf(
    AvatarShape(2f),
    AvatarShape(3.6f),
    AvatarShape(6f),
    AvatarShape(1.25f),
    AvatarShape(2f, lobes = 5, amplitude = 0.14f),
    AvatarShape(2.2f, lobes = 6, amplitude = 0.2f),
    AvatarShape(2.4f, lobes = 2, amplitude = 0.1f, rotation = 90f),
    AvatarShape(4f, lobes = 6, amplitude = 0.07f),
)

internal data class HeadGaze(val yaw: Float, val pitch: Float, val roll: Float)

internal data class EyeCfg(
    val width: Float,
    val height: Float,
    val tilt: Float = 0f,
    val open: Float = 1f,
)

internal const val EYE_SPLIT = 15.46f
internal const val EYE_WIDTH = 0.186f
internal const val EYE_HEIGHT = 0.412f
internal const val REST_YAW = 28.49f
internal const val REST_PITCH = 28.62f
internal const val REST_ROLL = -13f
internal val REST_GAZE = HeadGaze(REST_YAW, REST_PITCH, REST_ROLL)
internal val BASE_EYE = EyeCfg(EYE_WIDTH, EYE_HEIGHT)

internal data class Vec3(val x: Float, val y: Float, val z: Float)

internal data class EyePose(
    val x: Float,
    val y: Float,
    val a: Float,
    val b: Float,
    val c: Float,
    val d: Float,
    val depth: Float,
)

internal fun buildShape(
    radius: Float,
    exponent: Float,
    lobes: Float,
    amplitude: Float,
    rotationDeg: Float,
): Path {
    val path = Path()
    val steps = 180
    val power = exponent.coerceIn(1f, 12f)
    val start = rotationDeg * PI.toFloat() / 180f
    // Keep the squircle corners and lobes inside the canvas.
    val norm = 2f.pow(1f / power - 0.5f)
    val fit = 1f + abs(amplitude)
    for (i in 0..steps) {
        val angle = start + (i.toFloat() / steps) * 2f * PI.toFloat()
        val c = abs(cos(angle))
        val s = abs(sin(angle))
        val base = (c.pow(power) + s.pow(power)).pow(-1f / power) * norm
        val wave = if (lobes > 0f) 1f + amplitude * cos(lobes * angle) else 1f
        val r = radius * base * wave / fit
        val x = r * cos(angle)
        val y = r * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

// Eyes sit on a sphere, so the far eye narrows and both lean with the gaze.
internal fun eyePoses(yaw: Float, pitch: Float, roll: Float, scale: Float, split: Float): List<EyePose> {
    var forward = Vec3(0f, 0f, 1f)
    var right = Vec3(1f, 0f, 0f)
    var down = Vec3(0f, 1f, 0f)

    spin(forward, right, yaw.toRadians()).let { (f, r) ->
        forward = f
        right = r
    }
    spin(down, forward, pitch.toRadians()).let { (d, f) ->
        down = d
        forward = f
    }
    spin(right, down, roll.toRadians()).let { (r, d) ->
        right = r
        down = d
    }

    return listOf(-1f, 1f).map { side ->
        val (eyeForward, eyeRight) = spin(forward, right, (split * side).toRadians())
        EyePose(
            x = eyeForward.x * scale,
            y = eyeForward.y * scale,
            a = eyeRight.x,
            b = eyeRight.y,
            c = down.x,
            d = down.y,
            depth = eyeForward.z,
        )
    }
}

internal fun DrawScope.drawEyes(
    poses: List<EyePose>,
    inner: EyeCfg,
    outer: EyeCfg,
    ballScale: Float,
    color: Color,
    alpha: Float,
    blink: Float,
    lidOverride: Float,
) {
    if (alpha <= 0.01f) return
    poses.forEachIndexed { index, pose ->
        if (pose.depth > 0.05f) {
            val cfg = if (index == 0) inner else outer
            val lid = (cfg.open * blink * lidOverride).coerceIn(0.02f, 1f)
            drawPath(buildEye(pose, cfg, ballScale, lid), color, alpha = alpha)
        }
    }
}

private fun buildEye(pose: EyePose, cfg: EyeCfg, ballScale: Float, lid: Float): Path {
    val width = cfg.width * ballScale
    val height = cfg.height * ballScale
    val points = capsulePoints(width, height)
    val tilt = cfg.tilt.toRadians()
    val cosTilt = cos(tilt)
    val sinTilt = sin(tilt)
    return Path().apply {
        points.forEachIndexed { index, point ->
            val lx = point.x * cosTilt - point.y * sinTilt
            val ly = point.x * sinTilt + point.y * cosTilt
            val sx = pose.a * lx + pose.c * ly + pose.x
            val sy = pose.b * lx + pose.d * ly + pose.y
            val squashed = pose.y + (sy - pose.y) * lid
            if (index == 0) moveTo(sx, squashed) else lineTo(sx, squashed)
        }
        close()
    }
}

private fun capsulePoints(width: Float, height: Float): List<Offset> {
    val hw = width / 2f
    val hh = height / 2f
    val radius = min(hw, hh)
    val points = mutableListOf<Offset>()
    val steps = 10
    if (height >= width) {
        val straight = hh - radius
        for (i in 0..steps) {
            val a = PI.toFloat() + i.toFloat() / steps * PI.toFloat()
            points += Offset(cos(a) * radius, -straight + sin(a) * radius)
        }
        for (i in 0..steps) {
            val a = i.toFloat() / steps * PI.toFloat()
            points += Offset(cos(a) * radius, straight + sin(a) * radius)
        }
    } else {
        val straight = hw - radius
        for (i in 0..steps) {
            val a = -PI.toFloat() / 2f + i.toFloat() / steps * PI.toFloat()
            points += Offset(straight + cos(a) * radius, sin(a) * radius)
        }
        for (i in 0..steps) {
            val a = PI.toFloat() / 2f + i.toFloat() / steps * PI.toFloat()
            points += Offset(-straight + cos(a) * radius, sin(a) * radius)
        }
    }
    return points
}

private fun spin(u: Vec3, v: Vec3, angle: Float): Pair<Vec3, Vec3> {
    val c = cos(angle)
    val s = sin(angle)
    return Vec3(
        u.x * c + v.x * s,
        u.y * c + v.y * s,
        u.z * c + v.z * s,
    ) to Vec3(
        v.x * c - u.x * s,
        v.y * c - u.y * s,
        v.z * c - u.z * s,
    )
}

internal fun Float.toRadians(): Float = this * PI.toFloat() / 180f
