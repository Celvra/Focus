package fan.x0.focus.core.designsystem.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens
import fan.x0.focus.core.designsystem.theme.LocalFocusMotion

@Composable
fun FocusChatBubble(
    text: String,
    fromUser: Boolean,
    modifier: Modifier = Modifier,
    streaming: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = LocalFocusMotion.current
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = if (fromUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        if (fromUser) {
            UserBubble(text = text)
        } else {
            val container by animateColorAsState(
                targetValue = if (streaming) scheme.surfaceContainer else scheme.surfaceContainerLow,
                animationSpec = motion.effectsDefault(),
                label = "bubbleContainer",
            )
            Surface(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .animateContentSize(motion.spatialDefault()),
                shape = FocusShapeTokens.bubbleIncoming,
                color = container,
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Crossfade(
                        targetState = text.isBlank() && streaming,
                        animationSpec = motion.effectsDefault(),
                        label = "bubbleBody",
                    ) { waiting ->
                        if (waiting) {
                            FocusAvatar(
                                style = AvatarStyle(preset = 0, palette = 0),
                                size = 32.dp,
                                state = AvatarState.Orbit,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        } else {
                            FocusMarkdown(text = text, color = scheme.onSurface, streaming = streaming)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Surface(
        modifier = Modifier.widthIn(max = 320.dp),
        shape = FocusShapeTokens.bubbleOutgoing,
        color = MaterialTheme.colorScheme.primary,
        tonalElevation = 0.dp,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
fun FocusLabeledField(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}
