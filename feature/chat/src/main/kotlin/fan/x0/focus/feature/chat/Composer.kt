package fan.x0.focus.feature.chat

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import fan.x0.focus.core.designsystem.components.BrandIcons
import fan.x0.focus.core.designsystem.components.FocusComposer
import fan.x0.focus.core.designsystem.components.FocusComposerPill
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusImageLightbox
import fan.x0.focus.core.designsystem.components.FocusSearchPickerSheet
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
    var attachSheet by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { onHeightChange(it.height) },
    ) {
        FocusComposer(
            input = state.input,
            onInputChange = { onAction(ChatAction.InputChanged(it)) },
            placeholder = state.inputPlaceholder(),
            canSend = state.canSend,
            sending = state.sending,
            onSend = { onAction(ChatAction.Send) },
            onStop = { onAction(ChatAction.Stop) },
            hazeState = hazeState,
            modifier = Modifier.fillMaxWidth(),
            onHeightChange = {},
            onAdd = { attachSheet = true },
            addContentDescription = stringResource(R.string.chat_attach),
            onKeyEvent = { event ->
                if (!state.slash.visible || event.type != KeyEventType.KeyDown) {
                    false
                } else {
                    when (event.key) {
                        Key.DirectionUp -> {
                            onAction(ChatAction.SlashMove(-1))
                            true
                        }
                        Key.DirectionDown -> {
                            onAction(ChatAction.SlashMove(1))
                            true
                        }
                        Key.Enter -> {
                            onAction(ChatAction.SlashSelect)
                            true
                        }
                        Key.NumPadEnter -> {
                            onAction(ChatAction.SlashSelect)
                            true
                        }
                        Key.Escape -> {
                            onAction(ChatAction.SlashClose)
                            true
                        }
                        else -> false
                    }
                }
            },
            attachments = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.reply?.let { reply ->
                        ReplyPreview(
                            reply = reply,
                            onCancel = { onAction(ChatAction.CancelReply) },
                        )
                    }
                    if (state.attachments.isNotEmpty()) {
                        AttachmentRow(
                            attachments = state.attachments,
                            onRemove = { onAction(ChatAction.RemoveAttachment(it)) },
                        )
                    }
                }
            },
            pills = {
                FocusComposerPill(
                    icon = FocusIcons.Provider,
                    contentDescription = state.providerName.ifBlank {
                        stringResource(R.string.chat_picker_provider_title)
                    },
                    compact = true,
                    onClick = { onAction(ChatAction.OpenPicker(ComposerPicker.Provider)) },
                )
                FocusComposerPill(
                    label = state.model.ifBlank { stringResource(R.string.chat_picker_model_empty) },
                    icon = FocusIcons.Assistant,
                    compact = true,
                    onClick = { onAction(ChatAction.OpenPicker(ComposerPicker.Model)) },
                )
                FocusComposerPill(
                    label = stringResource(state.reasoningLevel.labelRes()),
                    icon = FocusIcons.Idea,
                    selected = state.reasoningLevel.isEnabled,
                    compact = true,
                    onClick = { onAction(ChatAction.OpenPicker(ComposerPicker.Reasoning)) },
                )
            },
        )
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
            model = state.model,
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
private fun ChatUiState.inputPlaceholder(): String {
    val title = title.trim()
    return if (title.isBlank()) {
        stringResource(R.string.chat_input_hint)
    } else {
        stringResource(R.string.chat_input_hint_titled, title)
    }
}

@Composable
private fun ReplyPreview(
    reply: ReplyUi,
    onCancel: () -> Unit,
) {
    val author = reply.author.ifBlank { stringResource(R.string.chat_quote_you) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = FocusIcons.Reply,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.chat_reply_title, author),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = reply.preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Surface(
                onClick = onCancel,
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = FocusIcons.Close,
                        contentDescription = stringResource(R.string.chat_reply_cancel),
                        modifier = Modifier.size(16.dp),
                    )
                }
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
    var preview by remember { mutableStateOf<AttachmentUi?>(null) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
    ) {
        attachments.forEach { attachment ->
            val bitmap = rememberAttachmentBitmap(attachment)
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = stringResource(R.string.chat_attachment_preview, attachment.name),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(role = Role.Button) { preview = attachment },
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
    preview?.let { attachment ->
        val bitmap = rememberAttachmentBitmap(attachment)
        if (bitmap != null) {
            FocusImageLightbox(
                image = bitmap,
                title = attachment.name,
                onDismiss = { preview = null },
            )
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
