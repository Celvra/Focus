package fan.x0.focus.core.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.theme.FocusRadii
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens
import fan.x0.focus.core.designsystem.theme.LocalFocusMotion
import kotlinx.coroutines.flow.first

@Composable
fun FocusChatBubble(
    text: String,
    fromUser: Boolean,
    modifier: Modifier = Modifier,
    streaming: Boolean = false,
    highlighted: Boolean = false,
    selectable: Boolean = false,
    reactions: List<String> = emptyList(),
    onLongPress: (() -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
    menu: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = if (fromUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box {
            if (fromUser) {
                UserBubble(
                    text = text,
                    highlighted = highlighted,
                    selectable = selectable,
                    reactions = reactions,
                    onLongPress = onLongPress,
                    header = header,
                )
            } else {
                AssistantBubble(
                    text = text,
                    streaming = streaming,
                    highlighted = highlighted,
                    selectable = selectable,
                    onLongPress = onLongPress,
                    header = header,
                )
            }
            menu?.invoke()
        }
    }
}

@Composable
private fun UserBubble(
    text: String,
    highlighted: Boolean,
    selectable: Boolean,
    reactions: List<String>,
    onLongPress: (() -> Unit)?,
    header: (@Composable () -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = LocalFocusMotion.current
    val shape = FocusShapeTokens.bubbleOutgoing
    val container by animateColorAsState(
        targetValue = if (highlighted) scheme.tertiaryContainer else scheme.primary,
        animationSpec = motion.effectsDefault(),
        label = "userBubbleContainer",
    )
    val content by animateColorAsState(
        targetValue = if (highlighted) scheme.onTertiaryContainer else scheme.onPrimary,
        animationSpec = motion.effectsDefault(),
        label = "userBubbleContent",
    )
    Surface(
        modifier = Modifier
            .widthIn(max = 320.dp)
            .animateContentSize(motion.spatialDefault())
            .bubbleGestures(
                shape = shape,
                contentColor = content,
                selectable = selectable,
                onLongPress = onLongPress,
            ),
        shape = shape,
        color = container,
        contentColor = content,
        border = if (selectable) BorderStroke(1.dp, content) else null,
        tonalElevation = 0.dp,
    ) {
        Column {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                header?.let {
                    it()
                    Spacer(Modifier.height(6.dp))
                }
                if (selectable) {
                    SelectionContainer {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                } else {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            BubbleReactions(
                reactions = reactions,
                fromUser = true,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(start = 6.dp, end = 6.dp, bottom = 6.dp),
            )
        }
    }
}

@Composable
private fun AssistantBubble(
    text: String,
    streaming: Boolean,
    highlighted: Boolean,
    selectable: Boolean,
    onLongPress: (() -> Unit)?,
    header: (@Composable () -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = LocalFocusMotion.current
    val shape = FocusShapeTokens.bubbleIncoming
    val container by animateColorAsState(
        targetValue = when {
            highlighted -> scheme.secondaryContainer
            streaming -> scheme.surfaceContainer
            else -> scheme.surfaceContainerLow
        },
        animationSpec = motion.effectsDefault(),
        label = "bubbleContainer",
    )
    Surface(
        modifier = Modifier
            .widthIn(max = 320.dp)
            .animateContentSize(motion.spatialDefault())
            .bubbleGestures(
                shape = shape,
                contentColor = scheme.onSurface,
                selectable = selectable,
                onLongPress = onLongPress,
            ),
        shape = shape,
        color = container,
        contentColor = scheme.onSurface,
        border = if (selectable) BorderStroke(1.dp, scheme.primary) else null,
    ) {
        Column {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                header?.let {
                    it()
                    Spacer(Modifier.height(6.dp))
                }
                Crossfade(
                    targetState = text.isBlank() && streaming,
                    animationSpec = motion.effectsDefault(),
                    label = "bubbleBody",
                ) { waiting ->
                    when {
                        waiting -> FocusAvatar(
                            style = AvatarStyle(preset = 0, palette = 0),
                            size = 32.dp,
                            state = AvatarState.Orbit,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                        selectable -> SelectionContainer {
                            FocusMarkdown(text = text, color = scheme.onSurface, streaming = streaming)
                        }
                        else -> FocusMarkdown(text = text, color = scheme.onSurface, streaming = streaming)
                    }
                }
            }
        }
    }
}

@Composable
private fun BubbleReactions(
    reactions: List<String>,
    fromUser: Boolean,
    modifier: Modifier = Modifier,
) {
    var retained by remember { mutableStateOf(reactions) }
    var initialized by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { initialized = true }
    LaunchedEffect(reactions) {
        retained = (retained + reactions).distinct()
    }
    if (retained.isEmpty()) return
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            retained.forEach { reaction ->
                key(reaction) {
                    BubbleReaction(
                        reaction = reaction,
                        count = reactions.count { it == reaction }.coerceAtLeast(1),
                        visible = reaction in reactions,
                        animateEnter = initialized,
                        fromUser = fromUser,
                        onExited = { retained = retained - reaction },
                    )
                }
            }
        }
    }
}

@Composable
private fun BubbleReaction(
    reaction: String,
    count: Int,
    visible: Boolean,
    animateEnter: Boolean,
    fromUser: Boolean,
    onExited: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalFocusMotion.current
    val scheme = MaterialTheme.colorScheme
    val state = remember { MutableTransitionState(if (animateEnter) false else visible) }
    SideEffect { state.targetState = visible }
    LaunchedEffect(Unit) {
        snapshotFlow { !state.currentState && !state.targetState }.first { it }
        onExited()
    }
    val origin = TransformOrigin(if (fromUser) 1f else 0f, 1f)
    AnimatedVisibility(
        visibleState = state,
        modifier = modifier,
        enter = fadeIn(motion.effectsFast()) +
            scaleIn(
                animationSpec = motion.spatialDefault(),
                initialScale = 0.6f,
                transformOrigin = origin,
            ),
        exit = fadeOut(motion.effectsFast()) +
            scaleOut(
                animationSpec = motion.spatialFast(),
                targetScale = 0.6f,
                transformOrigin = origin,
            ),
    ) {
        FilterChip(
            selected = true,
            onClick = {},
            label = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = reaction,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                }
            },
            modifier = Modifier.clearAndSetSemantics { contentDescription = "$reaction, $count" },
            shape = RoundedCornerShape(FocusRadii.extraLarge),
            colors = if (fromUser) {
                FilterChipDefaults.filterChipColors(
                    selectedContainerColor = scheme.onPrimary.copy(alpha = 0.16f),
                    selectedLabelColor = scheme.onPrimary,
                )
            } else {
                FilterChipDefaults.filterChipColors()
            },
        )
    }
}

@Composable
private fun Modifier.bubbleGestures(
    shape: Shape,
    contentColor: Color,
    selectable: Boolean,
    onLongPress: (() -> Unit)?,
): Modifier {
    if (selectable || onLongPress == null) return this
    val interaction = remember { MutableInteractionSource() }
    return this
        .clip(shape)
        .combinedClickable(
            interactionSource = interaction,
            indication = ripple(color = contentColor),
            role = Role.Button,
            onClick = {},
            onLongClick = hapticLongPress(onLongPress),
        )
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
