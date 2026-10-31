package fan.x0.focus.core.designsystem.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import fan.x0.focus.core.designsystem.R
import fan.x0.focus.core.designsystem.theme.LocalFocusMotion

@Composable
fun FocusComposer(
    input: String,
    onInputChange: (String) -> Unit,
    placeholder: String,
    canSend: Boolean,
    sending: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    onHeightChange: (Int) -> Unit = {},
    onAdd: (() -> Unit)? = null,
    addContentDescription: String? = null,
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    attachments: @Composable ColumnScope.() -> Unit = {},
    pills: @Composable RowScope.() -> Unit = {},
) {
    val motion = LocalFocusMotion.current
    val haptics = LocalFocusHaptics.current
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.extraLarge
    val buttonInteraction = remember { MutableInteractionSource() }
    val fieldInteraction = remember { MutableInteractionSource() }
    val focused by fieldInteraction.collectIsFocusedAsState()
    val container by animateColorAsState(
        targetValue = if (focused) scheme.surfaceContainer else scheme.surfaceContainerLow,
        animationSpec = motion.effectsDefault(),
        label = "composerContainer",
    )
    val scrim by animateColorAsState(
        targetValue = container.copy(alpha = 0.72f),
        animationSpec = motion.effectsDefault(),
        label = "composerScrim",
    )
    val blurStyle = remember {
        HazeBlurStyle {
            blurRadius(20.dp)
            noiseFactor(0f)
        }
    }
    val active = canSend || sending
    val buttonContainer by animateColorAsState(
        targetValue = when {
            sending && !canSend -> scheme.errorContainer
            active -> scheme.primary
            else -> scheme.surfaceContainerHighest
        },
        animationSpec = motion.effectsFast(),
        label = "sendContainer",
    )
    val buttonContent by animateColorAsState(
        targetValue = when {
            sending && !canSend -> scheme.onErrorContainer
            active -> scheme.onPrimary
            else -> scheme.onSurfaceVariant
        },
        animationSpec = motion.effectsFast(),
        label = "sendContent",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .onSizeChanged { onHeightChange(it.height) }
            .clip(shape)
            .hazeBlur(
                input = HazeInput.Sources(hazeState),
                style = blurStyle,
            ),
        shape = shape,
        color = scrim,
    ) {
        Column(modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 6.dp)) {
            attachments()
            TextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent(onKeyEvent),
                placeholder = {
                    Text(placeholder)
                },
                maxLines = 5,
                interactionSource = fieldInteraction,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (canSend) {
                            haptics.tap()
                            onSend()
                        }
                    },
                ),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    pills()
                }
                if (onAdd != null) {
                    FocusComposerPill(
                        icon = FocusIcons.Add,
                        contentDescription = addContentDescription,
                        onClick = onAdd,
                    )
                }
                Surface(
                    modifier = Modifier
                        .size(36.dp)
                        .pressScale(buttonInteraction),
                    shape = CircleShape,
                    color = buttonContainer,
                    contentColor = buttonContent,
                    enabled = active,
                    onClick = hapticTap {
                        if (sending) onStop() else onSend()
                    },
                    interactionSource = buttonInteraction,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AnimatedContent(
                            targetState = sending,
                            transitionSpec = {
                                (fadeIn(motion.effectsFast()) + scaleIn(motion.spatialDefault(), initialScale = 0.8f)) togetherWith
                                    (fadeOut(motion.effectsFast()) + scaleOut(motion.spatialDefault(), targetScale = 0.8f))
                            },
                            label = "sendIcon",
                        ) { isSending ->
                            Icon(
                                imageVector = if (isSending) FocusIcons.Stop else FocusIcons.Send,
                                contentDescription = stringResource(
                                    if (isSending) R.string.common_stop else R.string.common_send,
                                ),
                                tint = buttonContent,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FocusComposerPill(
    onClick: () -> Unit,
    icon: ImageVector? = null,
    label: String? = null,
    contentDescription: String? = null,
    selected: Boolean = false,
    compact: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val circleSize = if (compact) 26.dp else 28.dp
    val circleIcon = if (compact) 13.dp else 14.dp
    Surface(
        modifier = if (label == null) Modifier.size(circleSize) else Modifier.heightIn(min = 26.dp),
        shape = CircleShape,
        color = if (selected) scheme.secondaryContainer else scheme.surfaceContainerHigh,
        contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
        onClick = hapticTap(onClick),
        interactionSource = interaction,
    ) {
        if (label == null) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon ?: FocusIcons.Assistant,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(circleIcon),
                )
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 108.dp),
                )
            }
        }
    }
}
