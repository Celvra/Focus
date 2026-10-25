package fan.x0.focus.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.theme.LocalFocusMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun FocusBubbleEnter(
    play: Boolean,
    fromUser: Boolean,
    onPlayed: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val motion = LocalFocusMotion.current
    val density = LocalDensity.current
    val alpha = remember { Animatable(if (play) 0f else 1f) }
    val progress = remember { Animatable(if (play) 0f else 1f) }
    var settled by remember { mutableStateOf(!play) }
    LaunchedEffect(Unit) {
        if (play) {
            onPlayed()
            coroutineScope {
                launch { alpha.animateTo(1f, motion.effectsDefault()) }
                launch { progress.animateTo(1f, motion.spatialDefault()) }
            }
            settled = true
        }
    }
    val startScale = if (fromUser) 0.88f else 0.94f
    val startLift = with(density) { (if (fromUser) 18.dp else 12.dp).toPx() }
    val origin = if (fromUser) TransformOrigin(1f, 1f) else TransformOrigin(0f, 1f)
    Box(
        modifier = if (settled) {
            Modifier
        } else {
            Modifier.graphicsLayer {
                this.alpha = alpha.value
                scaleX = startScale + (1f - startScale) * progress.value
                scaleY = scaleX
                translationY = startLift * (1f - progress.value)
                transformOrigin = origin
            }
        },
    ) {
        content()
    }
}
