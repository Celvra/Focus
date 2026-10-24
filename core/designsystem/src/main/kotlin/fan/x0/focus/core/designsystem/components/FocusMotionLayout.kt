package fan.x0.focus.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import fan.x0.focus.core.designsystem.theme.LocalFocusMotion
import kotlinx.coroutines.delay

// First-paint fade. Delays are a product choice, not an M3 token.
@Composable
fun FocusReveal(
    modifier: Modifier = Modifier,
    index: Int = 0,
    visible: Boolean = true,
    content: @Composable () -> Unit,
) {
    val motion = LocalFocusMotion.current
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(visible, index) {
        if (!visible) {
            revealed = false
            return@LaunchedEffect
        }
        if (index > 0) delay(index * 45L)
        revealed = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = motion.effectsDefault(),
        label = "revealAlpha",
    )
    Box(modifier = modifier.graphicsLayer { this.alpha = alpha }) {
        content()
    }
}

@Composable
fun Modifier.pressScale(interactionSource: InteractionSource, pressedScale: Float = 0.96f): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val motion = LocalFocusMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = motion.spatialFast(),
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
