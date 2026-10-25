package fan.x0.focus.feature.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.AvatarState
import fan.x0.focus.core.designsystem.components.AvatarStyle
import fan.x0.focus.core.designsystem.components.FocusAvatar
import fan.x0.focus.core.designsystem.theme.LocalFocusMotion
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

internal val ChatMascotSize = 28.dp

// The mascot is parked in the top bar while the assistant is idle and flies to the bottom of the
// thread, just under the answering bubble, while a turn is running.
@Composable
internal fun ChatMascot(
    state: AvatarState,
    working: Boolean,
    topDock: Offset?,
    bottomDock: Offset?,
    overlayOrigin: Offset,
    modifier: Modifier = Modifier,
) {
    val start = topDock ?: bottomDock ?: return
    val motion = LocalFocusMotion.current
    val position = remember { Animatable(start - overlayOrigin, Offset.VectorConverter) }
    val top by rememberUpdatedState(topDock)
    val bottom by rememberUpdatedState(bottomDock)
    val origin by rememberUpdatedState(overlayOrigin)

    LaunchedEffect(working) {
        val docks = snapshotFlow {
            (if (working) bottom else top)?.minus(origin)
        }.filterNotNull()
        position.animateTo(docks.first(), motion.spatialSlow())
        docks.collect { position.snapTo(it) }
    }

    FocusAvatar(
        style = AvatarStyle(preset = 0, palette = 0),
        size = ChatMascotSize,
        state = state,
        modifier = modifier.offset {
            IntOffset(position.value.x.roundToInt(), position.value.y.roundToInt())
        },
    )
}
