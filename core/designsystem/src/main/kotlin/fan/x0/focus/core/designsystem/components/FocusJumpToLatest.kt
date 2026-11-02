package fan.x0.focus.core.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.R
import fan.x0.focus.core.designsystem.theme.LocalFocusMotion

@Composable
fun FocusJumpToLatest(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalFocusMotion.current
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(motion.effectsDefault()) +
            scaleIn(motion.spatialFast(), initialScale = 0.7f) +
            slideInVertically(motion.spatialFast()) { height -> height / 4 },
        exit = fadeOut(motion.effectsFast()) +
            scaleOut(motion.spatialFast(), targetScale = 0.7f),
    ) {
        SmallFloatingActionButton(
            onClick = hapticTap(onClick),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Icon(
                imageVector = FocusIcons.ArrowDown,
                contentDescription = stringResource(R.string.common_jump_latest),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
