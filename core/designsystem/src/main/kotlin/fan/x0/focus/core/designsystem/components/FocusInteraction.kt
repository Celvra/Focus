package fan.x0.focus.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import fan.x0.focus.core.designsystem.theme.LocalFocusMotion

// Quiet hover and press tint for rows inside cards. Replaces the ripple state layer.
@Composable
fun Modifier.stateLayer(
    interactionSource: InteractionSource,
    shape: Shape,
    color: Color = LocalContentColor.current,
): Modifier {
    val motion = LocalFocusMotion.current
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val alpha by animateFloatAsState(
        targetValue = when {
            pressed -> 0.10f
            hovered -> 0.05f
            focused -> 0.08f
            else -> 0f
        },
        animationSpec = motion.effectsFast(),
        label = "stateLayer",
    )
    return background(color.copy(alpha = alpha), shape)
}
