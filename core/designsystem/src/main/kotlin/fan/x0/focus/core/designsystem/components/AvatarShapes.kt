package fan.x0.focus.core.designsystem.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

internal data class Silhouette(
    val radii: FloatArray,
    val rot: Float = 0f,
    val cx: Float = 0f,
    val cy: Float = 0f,
    val sx: Float = 1f,
    val sy: Float = 1f,
)

internal val PROFILE_COS = FloatArray(PROFILE_SAMPLES) { cos(it.toFloat() / PROFILE_SAMPLES * 2f * PI.toFloat()) }
internal val PROFILE_SIN = FloatArray(PROFILE_SAMPLES) { sin(it.toFloat() / PROFILE_SAMPLES * 2f * PI.toFloat()) }

internal fun circleProfile(radius: Float): FloatArray = FloatArray(PROFILE_SAMPLES) { radius }

internal fun silhouette(profile: FloatArray, rot: Float = 0f, cx: Float = 0f, cy: Float = 0f): Silhouette =
    Silhouette(profile.copyOf(), rot, cx, cy)

internal fun blend(a: Silhouette, b: Silhouette, t: Float): Silhouette {
    val radii = FloatArray(PROFILE_SAMPLES) { i -> a.radii[i] + (b.radii[i] - a.radii[i]) * t }
    var dRot = b.rot - a.rot
    while (dRot > PI.toFloat()) dRot -= 2f * PI.toFloat()
    while (dRot < -PI.toFloat()) dRot += 2f * PI.toFloat()
    return Silhouette(
        radii = radii,
        rot = a.rot + dRot * t,
        cx = a.cx + (b.cx - a.cx) * t,
        cy = a.cy + (b.cy - a.cy) * t,
        sx = a.sx + (b.sx - a.sx) * t,
        sy = a.sy + (b.sy - a.sy) * t,
    )
}

internal fun Silhouette.toPath(scale: Float): Path {
    val cr = cos(rot)
    val sr = sin(rot)
    val points = List(PROFILE_SAMPLES) { i ->
        val r = radii[i]
        val x = r * PROFILE_COS[i]
        val y = r * PROFILE_SIN[i]
        Offset((x * cr - y * sr) * sx * scale + cx * scale, (x * sr + y * cr) * sy * scale + cy * scale)
    }
    return closedPath(points)
}

private fun closedPath(points: List<Offset>): Path = Path().apply {
    val n = points.size
    if (n < 3) return@apply
    moveTo(points[0].x, points[0].y)
    for (i in 0 until n) {
        val p0 = points[(i - 1 + n) % n]
        val p1 = points[i]
        val p2 = points[(i + 1) % n]
        val p3 = points[(i + 2) % n]
        cubicTo(
            p1.x + (p2.x - p0.x) / 6f,
            p1.y + (p2.y - p0.y) / 6f,
            p2.x - (p3.x - p1.x) / 6f,
            p2.y - (p3.y - p1.y) / 6f,
            p2.x,
            p2.y,
        )
    }
    close()
}

internal fun hullOfCircles(x1: Float, y1: Float, r1: Float, x2: Float, y2: Float, r2: Float): List<Offset> {
    val points = mutableListOf<Offset>()
    val steps = 28
    for (i in 0 until steps) {
        val a = i.toFloat() / steps * 2f * PI.toFloat()
        points += Offset(x1 + cos(a) * r1, y1 + sin(a) * r1)
        points += Offset(x2 + cos(a) * r2, y2 + sin(a) * r2)
    }
    return convexHull(points)
}

private fun convexHull(points: List<Offset>): List<Offset> {
    val sorted = points.distinct().sortedWith(compareBy<Offset> { it.x }.thenBy { it.y })
    if (sorted.size < 3) return sorted
    val lower = mutableListOf<Offset>()
    for (p in sorted) {
        while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], p) <= 0f) lower.removeAt(lower.size - 1)
        lower += p
    }
    val upper = mutableListOf<Offset>()
    for (p in sorted.asReversed()) {
        while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], p) <= 0f) upper.removeAt(upper.size - 1)
        upper += p
    }
    return (lower.dropLast(1) + upper.dropLast(1))
}

private fun cross(o: Offset, a: Offset, b: Offset): Float =
    (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)

internal fun profileFromPolygon(poly: List<Offset>, cx: Float = 0f, cy: Float = 0f): FloatArray {
    val radii = FloatArray(PROFILE_SAMPLES)
    val n = poly.size
    for (k in 0 until PROFILE_SAMPLES) {
        val dx = PROFILE_COS[k]
        val dy = PROFILE_SIN[k]
        var best = 0f
        for (i in 0 until n) {
            val a = poly[i]
            val b = poly[(i + 1) % n]
            val ex = b.x - a.x
            val ey = b.y - a.y
            val den = dx * ey - dy * ex
            if (abs(den) < 1e-9f) continue
            val px = a.x - cx
            val py = a.y - cy
            val t = (px * ey - py * ex) / den
            val u = (px * dy - py * dx) / den
            if (t > best && u in 0f..1f) best = t
        }
        radii[k] = best
    }
    return radii
}

private const val BAR_UPRIGHT_CY = -0.1875f

internal val BAR_UPRIGHT = silhouette(
    profileFromPolygon(
        hullOfCircles(0f, -0.505f, 0.132f, 0f, 0.130f, 0.075f),
        0f,
        BAR_UPRIGHT_CY,
    ),
    cy = BAR_UPRIGHT_CY,
)

internal val BAR_ITALIC = silhouette(
    profileFromPolygon(
        hullOfCircles(0f, -0.2535f, 0.1345f, 0f, 0.2535f, 0.1345f),
    ),
)

internal fun tearPath(scale: Float): Path {
    val hull = hullOfCircles(0f, 0f, 0.118f, 0f, 0.172f, 0.012f)
    return closedPath(hull.map { Offset(it.x * scale, it.y * scale) })
}
