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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import fan.x0.focus.core.designsystem.R
import fan.x0.focus.core.designsystem.theme.LocalFocusMotion

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    highlightTokens: List<String> = emptyList(),
    voice: FocusVoiceUi? = null,
    onVoiceStart: (() -> Unit)? = null,
    onVoiceStop: () -> Unit = {},
    onVoiceCancel: () -> Unit = {},
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
    val voiceActive = voice?.active == true
    val voiceBusy = voice?.busy == true
    val mic = !active && !voiceActive && !voiceBusy && onVoiceStart != null
    val buttonEnabled = active || voiceActive || voiceBusy || onVoiceStart != null
    var fieldValue by remember { mutableStateOf(TextFieldValue(input)) }
    LaunchedEffect(input) {
        if (fieldValue.text != input) {
            fieldValue = TextFieldValue(input, selection = TextRange(input.length))
        }
    }
    val highlight = remember(highlightTokens, scheme.primary) {
        VisualTransformation { text ->
            val ranges = tokenRanges(text.text, highlightTokens)
            if (ranges.isEmpty()) {
                TransformedText(text, OffsetMapping.Identity)
            } else {
                val styled = buildAnnotatedString {
                    append(text)
                    ranges.forEach { range ->
                        addStyle(SpanStyle(color = scheme.primary), range.first, range.last + 1)
                    }
                }
                TransformedText(styled, OffsetMapping.Identity)
            }
        }
    }
    val buttonContainer by animateColorAsState(
        targetValue = when {
            sending && !canSend -> scheme.errorContainer
            voiceActive -> scheme.error
            voiceBusy -> scheme.secondaryContainer
            active -> scheme.primary
            mic -> scheme.primary
            else -> scheme.surfaceContainerHighest
        },
        animationSpec = motion.effectsFast(),
        label = "sendContainer",
    )
    val buttonContent by animateColorAsState(
        targetValue = when {
            sending && !canSend -> scheme.onErrorContainer
            voiceActive -> scheme.onError
            voiceBusy -> scheme.onSecondaryContainer
            active -> scheme.onPrimary
            mic -> scheme.onPrimary
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
            if (voice != null) {
                FocusVoiceHud(
                    voice = voice,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            attachments()
            TextField(
                value = fieldValue,
                onValueChange = { value ->
                    val next = atomicDelete(fieldValue, value, highlightTokens)
                    fieldValue = next
                    onInputChange(next.text)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent(onKeyEvent),
                placeholder = {
                    Text(placeholder)
                },
                maxLines = 5,
                visualTransformation = highlight,
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
                val buttonMode = when {
                    sending -> ComposerButton.Stop
                    voiceBusy -> ComposerButton.Busy
                    voiceActive -> ComposerButton.Stop
                    canSend -> ComposerButton.Send
                    mic -> ComposerButton.Mic
                    else -> ComposerButton.Send
                }
                Surface(
                    modifier = Modifier
                        .size(36.dp)
                        .pressScale(buttonInteraction),
                    shape = CircleShape,
                    color = buttonContainer,
                    contentColor = buttonContent,
                    enabled = buttonEnabled,
                    onClick = hapticTap {
                        when {
                            sending -> onStop()
                            voiceBusy -> onVoiceCancel()
                            voiceActive -> onVoiceStop()
                            canSend -> onSend()
                            onVoiceStart != null -> onVoiceStart()
                        }
                    },
                    interactionSource = buttonInteraction,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AnimatedContent(
                            targetState = buttonMode,
                            transitionSpec = {
                                (fadeIn(motion.effectsFast()) + scaleIn(motion.spatialDefault(), initialScale = 0.8f)) togetherWith
                                    (fadeOut(motion.effectsFast()) + scaleOut(motion.spatialDefault(), targetScale = 0.8f))
                            },
                            label = "sendIcon",
                        ) { mode ->
                            when (mode) {
                                ComposerButton.Busy -> LoadingIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = buttonContent,
                                )
                                ComposerButton.Mic -> Icon(
                                    imageVector = FocusIcons.Mic,
                                    contentDescription = stringResource(R.string.common_voice_start),
                                    tint = buttonContent,
                                    modifier = Modifier.size(16.dp),
                                )
                                ComposerButton.Stop -> Icon(
                                    imageVector = FocusIcons.Stop,
                                    contentDescription = stringResource(
                                        if (voiceActive) R.string.common_voice_stop else R.string.common_stop,
                                    ),
                                    tint = buttonContent,
                                    modifier = Modifier.size(16.dp),
                                )
                                ComposerButton.Send -> Icon(
                                    imageVector = FocusIcons.Send,
                                    contentDescription = stringResource(R.string.common_send),
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

private enum class ComposerButton { Send, Stop, Mic, Busy }

private fun tokenRanges(text: String, tokens: List<String>): List<IntRange> {
    if (text.isEmpty() || tokens.isEmpty()) return emptyList()
    val ranges = mutableListOf<IntRange>()
    tokens.asSequence()
        .filter { it.length > 1 }
        .distinct()
        .sortedByDescending { it.length }
        .forEach { token ->
            var from = 0
            while (from <= text.length - token.length) {
                val index = text.indexOf(token, from, ignoreCase = true)
                if (index < 0) break
                val end = index + token.length
                val before = text.getOrNull(index - 1)
                val after = text.getOrNull(end)
                val bounded = !blocksBefore(before) &&
                    !blocksAfter(after) &&
                    !(token.startsWith("/") && before == '/') &&
                    ranges.none { it.first < end && index <= it.last }
                if (bounded) {
                    ranges += index until end
                    from = end
                } else {
                    from = index + 1
                }
            }
        }
    return ranges.sortedBy { it.first }
}

private fun blocksBefore(char: Char?): Boolean =
    char != null && (char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9')

private fun blocksAfter(char: Char?): Boolean =
    char != null && (char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char == '_')

private fun atomicDelete(
    old: TextFieldValue,
    next: TextFieldValue,
    tokens: List<String>,
): TextFieldValue {
    if (next.text.length >= old.text.length || tokens.isEmpty()) return next
    val oldText = old.text
    val newText = next.text
    val shared = minOf(oldText.length, newText.length)
    var prefix = 0
    while (prefix < shared && oldText[prefix] == newText[prefix]) prefix++
    var suffix = 0
    while (suffix < shared - prefix && oldText[oldText.length - 1 - suffix] == newText[newText.length - 1 - suffix]) suffix++
    val from = prefix
    val to = oldText.length - suffix
    if (oldText.removeRange(from, to) != newText) return next
    val ranges = tokenRanges(oldText, tokens)
    var hit = ranges.filter { it.first < to && it.last + 1 > from }
    if (hit.isEmpty() && to == from + 1 && oldText[from] == ' ') {
        val trailing = ranges.firstOrNull { it.last + 1 == from }
        if (trailing != null) hit = listOf(trailing)
    }
    if (hit.isEmpty()) return next
    val tokenStart = hit.minOf { it.first }
    val tokenEnd = hit.maxOf { it.last + 1 }
    var start = minOf(from, tokenStart)
    var end = maxOf(to, tokenEnd)
    val before = oldText.getOrNull(start - 1)
    val after = oldText.getOrNull(end)
    if (end <= tokenEnd) {
        if (after == ' ' && (before == null || !before.isLetterOrDigit())) {
            end++
        } else if (before == ' ') {
            start--
        }
    }
    val text = oldText.removeRange(start, end)
    return TextFieldValue(
        text = text,
        selection = TextRange(start.coerceIn(0, text.length)),
    )
}
