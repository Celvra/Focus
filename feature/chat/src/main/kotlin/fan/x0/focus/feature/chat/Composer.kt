package fan.x0.focus.feature.chat

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import fan.x0.focus.core.designsystem.components.BrandIcons
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusSearchPickerSheet
import fan.x0.focus.core.designsystem.components.pressScale
import fan.x0.focus.core.designsystem.theme.LocalFocusMotion
import fan.x0.focus.data.assistant.AttachmentKind

@Composable
internal fun Composer(
    state: ChatUiState,
    hazeState: HazeState,
    onAction: (ChatAction) -> Unit,
    onPickFiles: () -> Unit,
    onPickPhotos: () -> Unit,
    onTakePhoto: () -> Unit,
    onHeightChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val motion = LocalFocusMotion.current
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.extraLarge
    val buttonInteraction = remember { MutableInteractionSource() }
    val fieldInteraction = remember { MutableInteractionSource() }
    val focused by fieldInteraction.collectIsFocusedAsState()
    var attachSheet by remember { mutableStateOf(false) }
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
    val active = state.canSend || state.sending
    val buttonContainer by animateColorAsState(
        targetValue = when {
            state.sending && !state.canSend -> scheme.errorContainer
            active -> scheme.primary
            else -> scheme.surfaceContainerHighest
        },
        animationSpec = motion.effectsFast(),
        label = "sendContainer",
    )
    val buttonContent by animateColorAsState(
        targetValue = when {
            state.sending && !state.canSend -> scheme.onErrorContainer
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
            if (state.attachments.isNotEmpty()) {
                AttachmentRow(
                    attachments = state.attachments,
                    onRemove = { onAction(ChatAction.RemoveAttachment(it)) },
                )
            }
            TextField(
                value = state.input,
                onValueChange = { onAction(ChatAction.InputChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    val title = state.title.trim()
                    Text(
                        if (title.isBlank()) {
                            stringResource(R.string.chat_input_hint)
                        } else {
                            stringResource(R.string.chat_input_hint_titled, title)
                        },
                    )
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
                keyboardActions = KeyboardActions(onSend = { onAction(ChatAction.Send) }),
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
                    ComposerPill(
                        icon = FocusIcons.Provider,
                        contentDescription = state.providerName.ifBlank {
                            stringResource(R.string.chat_picker_provider_title)
                        },
                        compact = true,
                        onClick = { onAction(ChatAction.OpenPicker(ComposerPicker.Provider)) },
                    )
                    ComposerPill(
                        label = state.model.ifBlank { stringResource(R.string.chat_picker_model_empty) },
                        icon = FocusIcons.Assistant,
                        compact = true,
                        onClick = { onAction(ChatAction.OpenPicker(ComposerPicker.Model)) },
                    )
                    ComposerPill(
                        label = stringResource(state.reasoningLevel.labelRes()),
                        icon = FocusIcons.Idea,
                        selected = state.reasoningLevel.isEnabled,
                        compact = true,
                        onClick = { onAction(ChatAction.OpenPicker(ComposerPicker.Reasoning)) },
                    )
                }
                ComposerPill(
                    icon = FocusIcons.Add,
                    contentDescription = stringResource(R.string.chat_attach),
                    onClick = { attachSheet = true },
                )
                Surface(
                    modifier = Modifier
                        .size(36.dp)
                        .pressScale(buttonInteraction),
                    shape = CircleShape,
                    color = buttonContainer,
                    contentColor = buttonContent,
                    enabled = active,
                    onClick = {
                        if (state.sending) onAction(ChatAction.Stop) else onAction(ChatAction.Send)
                    },
                    interactionSource = buttonInteraction,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AnimatedContent(
                            targetState = state.sending,
                            transitionSpec = {
                                (fadeIn(motion.effectsFast()) + scaleIn(motion.spatialDefault(), initialScale = 0.8f)) togetherWith
                                    (fadeOut(motion.effectsFast()) + scaleOut(motion.spatialDefault(), targetScale = 0.8f))
                            },
                            label = "sendIcon",
                        ) { isSending ->
                            Icon(
                                imageVector = if (isSending) FocusIcons.Stop else FocusIcons.Send,
                                contentDescription = stringResource(
                                    if (isSending) R.string.chat_stop else R.string.chat_send,
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

    when (state.picker) {
        ComposerPicker.Provider -> ProviderPickerSheet(
            providers = state.providers,
            selected = state.providerId,
            onSelect = { onAction(ChatAction.SelectProvider(it)) },
            onDismiss = { onAction(ChatAction.OpenPicker(null)) },
        )
        ComposerPicker.Model -> {
            val labels = modelMetaLabels()
            val details = remember(state.models, labels) {
                state.models.mapNotNull { option ->
                    option.meta.toPickerDetail(labels)?.let { option.id to it }
                }.toMap()
            }
            FocusSearchPickerSheet(
                title = stringResource(R.string.chat_picker_model_title),
                searchHint = stringResource(R.string.chat_picker_model_search),
                emptyText = stringResource(
                    if (state.modelsLoading) {
                        R.string.chat_picker_model_loading
                    } else {
                        R.string.chat_picker_model_empty
                    },
                ),
                items = remember(state.models) { state.models.map { it.id } },
                query = state.modelQuery,
                onQueryChange = { onAction(ChatAction.ModelQueryChanged(it)) },
                onSelect = { onAction(ChatAction.SelectModel(it)) },
                onDismiss = { onAction(ChatAction.OpenPicker(null)) },
                selected = state.model,
                onRefresh = { onAction(ChatAction.RefreshModels) },
                itemIcon = { BrandIcons.forModel(it) },
                details = details,
            )
        }
        ComposerPicker.Reasoning -> ReasoningPickerSheet(
            level = state.reasoningLevel,
            onSelect = { onAction(ChatAction.SelectReasoning(it)) },
            onDismiss = { onAction(ChatAction.OpenPicker(null)) },
        )
        null -> Unit
    }

    if (attachSheet) {
        AttachmentSheet(
            onTakePhoto = {
                attachSheet = false
                onTakePhoto()
            },
            onPickPhotos = {
                attachSheet = false
                onPickPhotos()
            },
            onPickFiles = {
                attachSheet = false
                onPickFiles()
            },
            onDismiss = { attachSheet = false },
        )
    }
}

@Composable
private fun ComposerPill(
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
        onClick = onClick,
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

@Composable
private fun AttachmentRow(
    attachments: List<AttachmentUi>,
    onRemove: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        attachments.forEach { attachment ->
            AttachmentChip(attachment = attachment, onRemove = { onRemove(attachment.id) })
        }
    }
}

@Composable
internal fun AttachmentChip(
    attachment: AttachmentUi,
    onRemove: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = scheme.surfaceContainer,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.height(44.dp).padding(start = 5.dp, end = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                val bitmap = rememberAttachmentBitmap(attachment)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)),
                    )
                } else {
                    Icon(
                        imageVector = FocusIcons.Folder,
                        contentDescription = null,
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                text = attachment.name,
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp),
            )
            Surface(
                onClick = onRemove,
                modifier = Modifier.size(26.dp),
                shape = CircleShape,
                color = Color.Transparent,
                contentColor = scheme.onSurfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = FocusIcons.Close,
                        contentDescription = stringResource(R.string.chat_attach_remove),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun UserAttachmentStrip(attachments: List<AttachmentUi>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
    ) {
        attachments.forEach { attachment ->
            val bitmap = rememberAttachmentBitmap(attachment)
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = attachment.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = FocusIcons.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = attachment.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 140.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun rememberAttachmentBitmap(attachment: AttachmentUi): ImageBitmap? {
    if (attachment.kind != AttachmentKind.Image) return null
    return remember(attachment.id, attachment.base64) {
        val raw = attachment.base64 ?: return@remember null
        val bytes = runCatching { Base64.decode(raw, Base64.DEFAULT) }.getOrNull() ?: return@remember null
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }
}