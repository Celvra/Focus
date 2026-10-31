package fan.x0.focus.feature.chat

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

@Composable
internal fun FullScreenChargingRipple(
    active: Boolean,
    color: Color,
) {
    val progress = remember { Animatable(0f) }
    val running = remember { booleanArrayOf(false) }
    var visible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(active) {
        if (!active || running[0]) return@LaunchedEffect
        running[0] = true
        visible = true
        scope.launch {
            try {
                progress.snapTo(0f)
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = RIPPLE_DURATION_MS, easing = LinearEasing),
                )
            } finally {
                running[0] = false
                visible = false
            }
        }
    }
    if (!visible) return
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        FullScreenRippleLayer(progress = progress, color = color)
    }
}

@Composable
private fun FullScreenRippleLayer(
    progress: Animatable<Float, AnimationVector1D>,
    color: Color,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val ripple = remember { runCatching { AospRippleShader() }.getOrNull() } ?: return
    val colorInt = color.copy(alpha = RIPPLE_ALPHA / 255f).toArgb()
    val view = LocalView.current
    LaunchedEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@LaunchedEffect
        window.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
        )
        window.setGravity(Gravity.TOP or Gravity.START)
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        )
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(0f)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val raw = progress.value
                if (raw <= 0f || raw >= 1f) return@drawBehind
                ripple.draw(
                    canvas = drawContext.canvas.nativeCanvas,
                    width = size.width,
                    height = size.height,
                    center = Offset(size.width - 26.dp.toPx(), size.height),
                    rawProgress = raw,
                    time = raw * RIPPLE_DURATION_MS,
                    pixelDensity = density,
                    color = colorInt,
                )
            },
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AospRippleShader {
    private val shader = RuntimeShader(CIRCLE_SHADER)
    private val paint = Paint().apply {
        isAntiAlias = true
        this.shader = this@AospRippleShader.shader
    }

    fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        center: Offset,
        rawProgress: Float,
        time: Float,
        pixelDensity: Float,
        color: Int,
    ) {
        val eased = STANDARD.transform(rawProgress)
        val size = max(width, height) * 2f * eased
        shader.setFloatUniform("in_center", center.x, center.y)
        shader.setFloatUniform("in_size", size, size)
        shader.setFloatUniform("in_time", time)
        shader.setFloatUniform("in_distort_radial", 75f * rawProgress * (1f - rawProgress))
        shader.setFloatUniform("in_distort_xy", 75f * (1f - rawProgress))
        shader.setFloatUniform(
            "in_fadeSparkle",
            fade(rawProgress, 0f, 0.1f, 0.4f, 1f),
        )
        shader.setFloatUniform(
            "in_fadeFill",
            fade(rawProgress, 0f, 0f, 0f, 0.6f),
        )
        shader.setFloatUniform(
            "in_fadeRing",
            fade(rawProgress, 0f, 0.1f, 0.3f, 1f),
        )
        shader.setFloatUniform("in_blur", 1.25f + (0.5f - 1.25f) * eased)
        shader.setFloatUniform("in_pixelDensity", pixelDensity)
        shader.setColorUniform("in_color", color)
        shader.setFloatUniform("in_sparkle_strength", 0.3f)
        canvas.drawRect(0f, 0f, width, height, paint)
    }

    private fun fade(
        progress: Float,
        fadeInStart: Float,
        fadeInEnd: Float,
        fadeOutStart: Float,
        fadeOutEnd: Float,
    ): Float {
        val fadeIn = subProgress(fadeInStart, fadeInEnd, progress)
        val fadeOut = 1f - subProgress(fadeOutStart, fadeOutEnd, progress)
        return min(fadeIn, fadeOut)
    }

    private fun subProgress(start: Float, end: Float, progress: Float): Float {
        if (start == end) {
            return if (progress > start) 1f else 0f
        }
        val low = min(start, end)
        val high = max(start, end)
        val value = progress.coerceIn(low, high)
        return (value - start) / (end - start)
    }

    private companion object {
        val STANDARD = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    }
}

private const val RIPPLE_DURATION_MS = 1750
private const val RIPPLE_ALPHA = 115f

private const val CIRCLE_SHADER = """
uniform vec2 in_center;
uniform vec2 in_size;
uniform float in_time;
uniform float in_distort_radial;
uniform float in_distort_xy;
uniform float in_fadeSparkle;
uniform float in_fadeFill;
uniform float in_fadeRing;
uniform float in_blur;
uniform float in_pixelDensity;
layout(color) uniform vec4 in_color;
uniform float in_sparkle_strength;

float triangleNoise(vec2 n) {
    n = fract(n * vec2(5.3987, 5.4421));
    n += dot(n.yx, n.xy + vec2(21.5351, 14.3137));
    float xy = n.x * n.y;
    return fract(xy * 95.4307) + fract(xy * 75.04961) - 1.0;
}

const float PI = 3.1415926535897932384626;

float sparkles(vec2 uv, float t) {
    float n = triangleNoise(uv);
    float s = 0.0;
    for (float i = 0; i < 4; i += 1) {
        float l = i * 0.01;
        float h = l + 0.1;
        float o = smoothstep(n - l, h, n);
        o *= abs(sin(PI * o * (t + 0.55 * i)));
        s += o;
    }
    return s;
}

vec2 distort(vec2 p, float time, float distort_amount_radial,
    float distort_amount_xy) {
    float angle = atan(p.y, p.x);
    return p + vec2(sin(angle * 8 + time * 0.003 + 1.641),
                cos(angle * 5 + 2.14 + time * 0.00412)) * distort_amount_radial
             + vec2(sin(p.x * 0.01 + time * 0.00215 + 0.8123),
                cos(p.y * 0.01 + time * 0.005931)) * distort_amount_xy;
}

float soften(float d, float blur) {
    float blurHalf = blur * 0.5;
    return smoothstep(-blurHalf, blurHalf, d);
}

float subtract(float outer, float inner) {
    return max(outer, -inner);
}

float sdCircle(vec2 p, float r) {
    return (length(p) - r) / r;
}

float circleRing(vec2 p, float radius) {
    float thicknessHalf = radius * 0.25;
    float outerCircle = sdCircle(p, radius + thicknessHalf);
    float innerCircle = sdCircle(p, radius);
    return subtract(outerCircle, innerCircle);
}

vec4 main(vec2 p) {
    vec2 p_distorted = distort(p, in_time, in_distort_radial, in_distort_xy);
    float radius = in_size.x * 0.5;
    float sparkleRing = soften(circleRing(p_distorted - in_center, radius), in_blur);
    float inside = soften(sdCircle(p_distorted - in_center, radius * 1.25), in_blur);
    float sparkle = sparkles(p - mod(p, in_pixelDensity * 0.8), in_time * 0.00175)
        * (1.0 - sparkleRing) * in_fadeSparkle;

    float rippleInsideAlpha = (1.0 - inside) * in_fadeFill;
    float rippleRingAlpha = (1.0 - sparkleRing) * in_fadeRing;
    float rippleAlpha = max(rippleInsideAlpha, rippleRingAlpha) * in_color.a;
    vec4 ripple = vec4(in_color.rgb, 1.0) * rippleAlpha;
    return mix(ripple, vec4(sparkle), sparkle * in_sparkle_strength);
}
"""
