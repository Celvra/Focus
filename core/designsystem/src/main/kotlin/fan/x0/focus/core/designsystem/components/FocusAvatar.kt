package fan.x0.focus.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import fan.x0.focus.core.designsystem.theme.LocalFocusMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

enum class AvatarState {
    Idle,

    @Deprecated("Use Sleep")
    Thinking,
    Wink,
    Wide,
    Alert,
    Notify,
    Exclaim,
    Sleep,
    Egg,
    Hexagon,
    Play,
    Orbit,
    Burst,
    Comet,
}

data class AvatarStyle(val preset: Int, val palette: Int)

private data class NotifSpec(val x: Float, val y: Float, val radius: Float, val notch: Float)

private data class ArcInstance(val seed: ArcSeed, val opacity: Float)

private data class AvatarPose(
    val gaze: HeadGaze = REST_GAZE,
    val split: Float = EYE_SPLIT,
    val inner: EyeCfg = BASE_EYE,
    val outer: EyeCfg = BASE_EYE,
    val eyeAlpha: Float = 1f,
    val frame: Float = 0.96f,
    val body: Silhouette? = null,
    val thinking: Boolean = false,
    val dots: List<DotSpec> = emptyList(),
    val arcs: List<ArcInstance> = emptyList(),
    val notif: NotifSpec? = null,
    val dotsBehind: Boolean = false,
)

private data class ShapeStyle(
    val exponent: Float,
    val lobes: Float,
    val amplitude: Float,
    val rotation: Float,
    val face: Float,
)

@Suppress("DEPRECATION")
private fun poseFor(state: AvatarState, t: Float): AvatarPose = when (state) {
    AvatarState.Idle -> AvatarPose(
        gaze = HeadGaze(
            yaw = REST_YAW + loopNoise(t, 11.3f, 0.4f) * 5.5f + loopNoise(t, 3.7f, 2.1f) * 1.6f,
            pitch = REST_PITCH + loopNoise(t, 9.1f, 1.3f) * 4.2f + loopNoise(t, 4.3f, 0.7f) * 1.3f,
            roll = REST_ROLL + loopNoise(t, 13.7f, 3.2f) * 2.2f,
        ),
    )
    AvatarState.Thinking -> AvatarPose(
        thinking = true,
        eyeAlpha = 0f,
    )
    AvatarState.Wink -> AvatarPose(
        gaze = HeadGaze(-5.37f, 4.55f, 6.7f),
        split = 16.25f,
        inner = EyeCfg(0.236f, 0.464f),
        outer = EyeCfg(0.447f, 0.089f),
    )
    AvatarState.Wide -> AvatarPose(
        gaze = HeadGaze(6.92f, -21.96f, 11.6f),
        split = 18.43f,
        inner = EyeCfg(0.356f, 0.875f),
        outer = EyeCfg(0.356f, 0.875f),
    )
    AvatarState.Alert -> {
        val travel = easeInOutCubic(clamp01(t / 1.5f)) * 0.82f - 0.087f
        val back = if (t > 1.6f) clamp01((t - 1.6f) / 0.4f) else 0f
        val x = travel * (1f - back) + 0.1f * back
        val buzz = sin(t * 2.5f * 2f * PI.toFloat()) * 0.005f
        val tilt = 17.7f
        val tiltRad = tilt.toRadians()
        AvatarPose(
            eyeAlpha = 0f,
            body = BAR_ITALIC.copy(rot = tiltRad, cx = x, cy = -0.325f - buzz),
            dots = listOf(
                DotSpec(
                    x = x - sin(tiltRad) * 0.58f,
                    y = -0.325f + cos(tiltRad) * 0.58f + buzz * 2.8f,
                    radius = 0.118f,
                    opacity = 1f,
                    kind = DotKind.Tear,
                    rotation = tilt,
                ),
            ),
        )
    }
    AvatarState.Notify -> {
        val p = clamp01(t / 0.45f)
        val pop = 1f + (NOTIF_POP - 1f) * sin(p * PI.toFloat()) * (1f - p * 0.35f)
        val angle = NOTIF_ANGLE.toRadians()
        val radius = NOTIF_R * pop
        AvatarPose(
            gaze = HeadGaze(-21.94f, -5.82f, -12.2f),
            split = 18.89f,
            inner = EyeCfg(0.505f, 0.498f),
            outer = EyeCfg(0.505f, 0.498f),
            notif = NotifSpec(
                x = cos(angle) * NOTIF_DIST,
                y = sin(angle) * NOTIF_DIST,
                radius = radius,
                notch = radius + NOTIF_MARGIN,
            ),
        )
    }
    AvatarState.Exclaim -> AvatarPose(
        eyeAlpha = 0f,
        body = BAR_UPRIGHT,
        dots = listOf(DotSpec(-0.012f, 0.526f, 0.113f, 1f)),
    )
    AvatarState.Sleep -> AvatarPose(
        eyeAlpha = 0f,
        body = Silhouette(
            circleProfile(0.1585f),
            cy = 0.11f + sin(t * 2f * PI.toFloat() / 0.6f) * 0.19f,
        ),
    )
    AvatarState.Egg -> AvatarPose(
        gaze = HeadGaze(19.97f, 26.01f, -17.1f),
        split = 11.07f,
        inner = EyeCfg(0.164f, 0.385f),
        outer = EyeCfg(0.164f, 0.385f),
        frame = 0.92f,
        body = Silhouette(EGG_PROFILE),
    )
    AvatarState.Hexagon -> AvatarPose(
        gaze = HeadGaze(23.11f, 24.42f, -13.3f),
        split = 13.37f,
        inner = EyeCfg(0.177f, 0.411f),
        outer = EyeCfg(0.177f, 0.411f),
        frame = 0.94f,
        body = Silhouette(HEXAGON_PROFILE),
    )
    AvatarState.Play -> {
        val fade = clamp01(t / 0.35f) * clamp01((2.2f - t) / 0.5f)
        AvatarPose(
            gaze = HeadGaze(12f, -8f, -6f),
            split = 15f,
            inner = EyeCfg(0.18f, 0.34f),
            outer = EyeCfg(0.18f, 0.34f),
            frame = 0.58f,
            body = Silhouette(TRIANGLE_PROFILE, cy = 0.213f),
            arcs = SWOOSH_SEEDS.map { ArcInstance(it.copy(cx = 0.45f - t * 0.42f), fade) },
        )
    }
    AvatarState.Orbit -> {
        val ramp = easeInOutCubic(clamp01(t / 0.35f))
        val rot = -2f * PI.toFloat() * 1.25f * t * ramp
        val back = easeInOutCubic(clamp01((t - 1.6f) / 0.9f))
        val triangle = Silhouette(
            TRIANGLE_PROFILE,
            rot = rot,
            cx = -0.213f * sin(rot),
            cy = 0.213f * cos(rot),
        )
        val ball = Silhouette(circleProfile(1f), rot = rot)
        val fade = clamp01(t / 0.8f) * clamp01((3.6f - t) / 0.9f)
        AvatarPose(
            gaze = HeadGaze(
                yaw = REST_YAW + sin(t * 6.5f) * 65f * (1f - back),
                pitch = -4f + back * 32f,
                roll = -13f,
            ),
            frame = 0.58f,
            body = blend(triangle, ball, back),
            arcs = RING_SEEDS.mapIndexed { index, seed ->
                ArcInstance(seed, fade * clamp01((t - index * 0.13f) / 0.3f))
            },
            inner = EyeCfg(0.18f, 0.34f + back * 0.07f),
            outer = EyeCfg(0.18f, 0.34f + back * 0.07f),
        )
    }
    AvatarState.Burst -> {
        val collapse = 1f - 0.834f * easeOutQuint(clamp01(t / 0.7f))
        val regrow = easeOutQuint(clamp01((t - 1.7f) / 0.7f))
        val scale = collapse + (1f - collapse) * regrow
        AvatarPose(
            eyeAlpha = clamp01((t - 1.85f) / 0.4f),
            frame = 0.72f,
            body = Silhouette(circleProfile(scale)),
            dots = particles(t, 1f),
            dotsBehind = true,
        )
    }
    AvatarState.Comet -> {
        val collapse = 1f - (1f - COMET_DOT) * easeOutQuint(clamp01(t / 0.55f))
        val regrow = easeOutQuint(clamp01((t - 1.85f) / 0.6f))
        val scale = collapse + (1f - collapse) * regrow
        val fade = clamp01((t - 0.15f) / 0.25f) * clamp01((1.95f - t) / 0.3f)
        AvatarPose(
            eyeAlpha = clamp01((t - 2f) / 0.35f),
            frame = 0.62f,
            body = Silhouette(
                circleProfile(scale),
                cy = sin(clamp01(t / 1.7f) * PI.toFloat()) * 0.035f,
            ),
            arcs = COMET_SEEDS.map { ArcInstance(it, fade) },
        )
    }
}

@Composable
fun FocusAvatar(
    style: AvatarStyle,
    size: Dp,
    modifier: Modifier = Modifier,
    state: AvatarState = AvatarState.Idle,
) {
    val safePreset = shapePresets[style.preset.mod(shapePresets.size)]
    val motion = LocalFocusMotion.current
    val scheme = MaterialTheme.colorScheme
    val pairs = listOf(
        scheme.onBackground to scheme.background,
        scheme.primaryContainer to scheme.onPrimaryContainer,
        scheme.secondaryContainer to scheme.onSecondaryContainer,
        scheme.tertiaryContainer to scheme.onTertiaryContainer,
        scheme.primary to scheme.onPrimary,
        scheme.tertiary to scheme.onTertiary,
    )
    val pair = pairs[style.palette.mod(pairs.size)]

    val exponent = animateFloatAsState(safePreset.exponent, motion.spatialDefault(), label = "avatarExponent")
    val lobes = animateFloatAsState(safePreset.lobes.toFloat(), motion.spatialDefault(), label = "avatarLobes")
    val amplitude = animateFloatAsState(safePreset.amplitude, motion.spatialDefault(), label = "avatarAmplitude")
    val rotation = animateFloatAsState(safePreset.rotation, motion.spatialDefault(), label = "avatarRotation")
    val faceScale = animateFloatAsState(
        if (safePreset.lobes > 0) 0.88f else 1f,
        motion.spatialDefault(),
        label = "avatarFaceScale",
    )
    val container = animateColorAsState(pair.first, motion.effectsDefault<Color>(), label = "avatarFill")
    val content = animateColorAsState(pair.second, motion.effectsDefault<Color>(), label = "avatarInk")

    var shownState by remember { mutableStateOf(state) }
    var previousState by remember { mutableStateOf<AvatarState?>(null) }
    var previousTime by remember { mutableStateOf(0f) }
    val clock = remember { Animatable(0f) }
    val morph = remember { Animatable(1f) }

    LaunchedEffect(state) {
        if (state == shownState) return@LaunchedEffect
        previousState = shownState
        previousTime = clock.value * durationOf(shownState)
        shownState = state
        morph.snapTo(0f)
        morph.animateTo(1f, tween(durationMillis = 260, easing = FastOutSlowInEasing))
    }

    LaunchedEffect(shownState) {
        val duration = durationOf(shownState)
        while (true) {
            clock.snapTo(0f)
            clock.animateTo(1f, tween(durationMillis = (duration * 1000f).toInt(), easing = LinearEasing))
        }
    }

    Canvas(modifier = modifier.size(size)) {
        val radius = this.size.minDimension / 2f
        val shapeStyle = ShapeStyle(
            exponent = exponent.value,
            lobes = lobes.value,
            amplitude = amplitude.value,
            rotation = rotation.value,
            face = faceScale.value,
        )
        val lidOverride = 1f - 0.9f * sin(morph.value * PI.toFloat())
        val shownTime = clock.value * durationOf(shownState)
        translate(left = radius, top = radius) {
            previousState?.let { previous ->
                if (morph.value < 1f) {
                    drawState(
                        state = previous,
                        t = previousTime,
                        radius = radius,
                        alpha = 1f - morph.value,
                        lidOverride = lidOverride,
                        shapeStyle = shapeStyle,
                        container = container.value,
                        content = content.value,
                    )
                }
            }
            drawState(
                state = shownState,
                t = shownTime,
                radius = radius,
                alpha = morph.value,
                lidOverride = lidOverride,
                shapeStyle = shapeStyle,
                container = container.value,
                content = content.value,
            )
        }
    }
}

private fun DrawScope.drawState(
    state: AvatarState,
    t: Float,
    radius: Float,
    alpha: Float,
    lidOverride: Float,
    shapeStyle: ShapeStyle,
    container: Color,
    content: Color,
) {
    if (alpha <= 0.01f) return
    val pose = poseFor(state, t)
    val ball = radius * pose.frame
    val face = if (pose.body == null && !pose.thinking) shapeStyle.face else 1f
    val eyeBall = ball * face

    if (pose.dotsBehind) drawDots(pose.dots, ball, container, alpha)
    pose.arcs.forEach { drawArc(arcRender(it.seed, t, ball), it.opacity * alpha, front = false) }
    drawBody(pose, shapeStyle, radius, ball, container, alpha, t)
    if (!pose.dotsBehind) drawDots(pose.dots, ball, container, alpha)
    pose.notif?.let { notif ->
        drawCircle(
            color = NOTIF_BLUE,
            radius = notif.radius * ball,
            center = Offset(notif.x * ball, notif.y * ball),
            alpha = alpha,
        )
    }
    pose.arcs.forEach { drawArc(arcRender(it.seed, t, ball), it.opacity * alpha, front = true) }

    val blink = if (state == AvatarState.Idle) blinkLid(t) else 1f
    drawEyes(
        poses = eyePoses(pose.gaze.yaw, pose.gaze.pitch, pose.gaze.roll, eyeBall, pose.split),
        inner = pose.inner,
        outer = pose.outer,
        ballScale = eyeBall,
        color = content,
        alpha = pose.eyeAlpha * alpha,
        blink = blink,
        lidOverride = lidOverride,
    )
}

private fun DrawScope.drawBody(
    pose: AvatarPose,
    shapeStyle: ShapeStyle,
    radius: Float,
    ball: Float,
    container: Color,
    alpha: Float,
    t: Float,
) {
    if (pose.thinking) {
        for (index in DOT_X.indices) {
            val pulse = dotPulse(t, index)
            val radiusFactor = DOT_R * (1f + (DOT_PEAK - 1f) * pulse)
            drawCircle(
                color = container,
                radius = radiusFactor * ball,
                center = Offset(DOT_X[index] * ball, 0f),
                alpha = if (index == 1) alpha else alpha * (0.55f + 0.45f * pulse),
            )
        }
        return
    }

    val body = if (pose.body != null) {
        pose.body.toPath(ball)
    } else {
        buildShape(
            radius = radius * 0.96f,
            exponent = shapeStyle.exponent,
            lobes = shapeStyle.lobes,
            amplitude = shapeStyle.amplitude,
            rotationDeg = shapeStyle.rotation,
        )
    }
    val notif = pose.notif
    if (notif == null) {
        drawPath(body, container, alpha = alpha)
        return
    }
    val notchRadius = notif.notch * ball
    val notch = Path().apply {
        addOval(
            Rect(
                left = notif.x * ball - notchRadius,
                top = notif.y * ball - notchRadius,
                right = notif.x * ball + notchRadius,
                bottom = notif.y * ball + notchRadius,
            ),
        )
    }
    val notched = Path().apply { op(body, notch, PathOperation.Difference) }
    drawPath(notched, container, alpha = alpha)
}

private fun DrawScope.drawDots(dots: List<DotSpec>, ball: Float, container: Color, alpha: Float) {
    dots.forEach { dot ->
        val dotAlpha = dot.opacity * alpha * (0.3f + 0.7f * dot.depth)
        if (dotAlpha <= 0.01f) return@forEach
        when (dot.kind) {
            DotKind.Circle -> drawCircle(
                color = container,
                radius = dot.radius * ball,
                center = Offset(dot.x * ball, dot.y * ball),
                alpha = dotAlpha,
            )
            DotKind.Tear -> withTransform({
                translate(left = dot.x * ball, top = dot.y * ball)
                rotate(degrees = dot.rotation, pivot = Offset.Zero)
            }) {
                drawPath(tearPath(ball), container, alpha = dotAlpha)
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun durationOf(state: AvatarState): Float = when (state) {
    AvatarState.Idle -> 60f
    AvatarState.Thinking -> 2.6f
    AvatarState.Wink -> 1.6f
    AvatarState.Wide -> 1.8f
    AvatarState.Alert -> 2.4f
    AvatarState.Notify -> 2.2f
    AvatarState.Exclaim -> 2f
    AvatarState.Sleep -> 2.4f
    AvatarState.Egg -> 1.8f
    AvatarState.Hexagon -> 1.6f
    AvatarState.Play -> 2f
    AvatarState.Orbit -> 3.4f
    AvatarState.Burst -> 2.6f
    AvatarState.Comet -> 2.4f
}

private fun dotPulse(seconds: Float, index: Int): Float {
    val p = (((seconds - index * 0.5f) / 1.5f) % 1f + 1f) % 1f
    return if (p < 0.5f) 0.5f - 0.5f * cos(p * 4f * PI.toFloat()) else 0f
}

private fun blinkLid(t: Float): Float {
    for (start in BLINK_STARTS) {
        val k = (t - start) / 0.18f
        if (k in 0f..1f) {
            val lid = if (k < 0.45f) 1f - k / 0.45f else (k - 0.45f) / 0.55f
            return 0.06f + 0.94f * lid
        }
    }
    return 1f
}

// Fixed blink schedule; sparse enough to read as alive, dense enough to feel regular.
private val BLINK_STARTS: FloatArray = buildList {
    var t = 1.4f
    while (t < 60f) {
        add(t)
        t += 2.3f
        if ((t.toInt() % 7) == 0) {
            add(t)
            t += 0.24f
        }
    }
}.toFloatArray()

private fun loopNoise(t: Float, period: Float, seed: Float): Float {
    val p = t / period * 2f * PI.toFloat()
    return 0.55f * sin(p + seed) +
        0.3f * sin(2f * p + seed * 1.7f + 1.1f) +
        0.15f * sin(3f * p + seed * 2.3f + 2.4f)
}

private fun clamp01(v: Float): Float = if (v < 0f) 0f else if (v > 1f) 1f else v

private fun easeOutQuint(t: Float): Float = 1f - (1f - t).pow(5)

private fun easeInOutCubic(t: Float): Float = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f
