package fan.x0.focus.feature.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusLoadingBlob
import fan.x0.focus.core.designsystem.theme.LocalFocusTypography

@Composable
internal fun AutomationOverlay(finished: Boolean, showMarquee: Boolean = true) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (showMarquee) AutomationMarquee()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(bottom = 56.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AutomationPill(finished = finished)
        }
    }
}

@Composable
private fun AutomationPill(finished: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val typography = LocalFocusTypography.current
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(scheme.surfaceContainerHigh.copy(alpha = 0.94f))
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.5f), shape)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AnimatedContent(
            targetState = finished,
            transitionSpec = {
                fadeIn(tween(durationMillis = 180)) togetherWith fadeOut(tween(durationMillis = 120))
            },
            label = "automationPill",
        ) { done ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (done) {
                    Icon(
                        imageVector = FocusIcons.Check,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    FocusLoadingBlob(
                        size = 18.dp,
                        color = scheme.primary,
                    )
                }
                Text(
                    text = stringResource(
                        if (done) {
                            R.string.overlay_automation_finished
                        } else {
                            R.string.overlay_automation_running
                        },
                    ),
                    style = typography.labelLarge,
                    color = scheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun AutomationMarquee() {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "automationMarquee")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "automationMarqueeAngle",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.68f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "automationMarqueePulse",
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
        val radius = 30.dp.toPx()
        drawSweepRings(
            colors = colors,
            angle = angle,
            pulse = pulse,
            layers = listOf(
                3.dp.toPx() to 0.95f,
                9.dp.toPx() to 0.32f,
                22.dp.toPx() to 0.12f,
                40.dp.toPx() to 0.05f,
            ),
            inset = inset,
            radius = radius,
        )
        drawSweepRings(
            colors = aura,
            angle = -angle * 0.72f + 120f,
            pulse = pulse,
            layers = listOf(
                8.dp.toPx() to 0.12f,
                26.dp.toPx() to 0.05f,
            ),
            inset = inset,
            radius = radius,
        )
    }
}
