package fan.x0.focus.feature.crew

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.AvatarState
import fan.x0.focus.core.designsystem.components.AvatarStyle
import fan.x0.focus.core.designsystem.components.FocusAvatar
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusOutlinedButton
import fan.x0.focus.core.designsystem.components.FocusPrimaryButton
import fan.x0.focus.core.designsystem.components.FocusStatusPill
import fan.x0.focus.core.designsystem.components.FocusTextButton
import fan.x0.focus.core.designsystem.components.FocusTonalButton
import fan.x0.focus.core.designsystem.components.StatusTone
import fan.x0.focus.core.designsystem.components.hapticSelect
import fan.x0.focus.core.designsystem.components.hapticTap
import fan.x0.focus.core.designsystem.theme.FocusRadii
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens
import fan.x0.focus.core.designsystem.theme.LocalFocusTypography
import fan.x0.focus.data.assistant.CrewAvatars

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrewGroupEditorSheet(
    draft: GroupDraft,
    saving: Boolean,
    onAction: (CrewAction) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var expandedAvatar by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(
        onDismissRequest = { onAction(CrewAction.DismissGroupEditor) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(
                    if (draft.id == null) R.string.crew_group_new else R.string.crew_group_edit,
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(
                value = draft.name,
                onValueChange = { onAction(CrewAction.GroupNameChanged(it)) },
                label = { Text(stringResource(R.string.crew_group_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.topic,
                onValueChange = { onAction(CrewAction.GroupTopicChanged(it)) },
                label = { Text(stringResource(R.string.crew_group_topic)) },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.crew_avatar),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CrewAvatarPicker(
                preset = draft.avatarPreset,
                palette = draft.avatarPalette,
                path = null,
                onPreset = { preset, palette -> onAction(CrewAction.GroupAvatarChanged(preset, palette)) },
                enabled = !saving,
            )
            if (draft.id == null) {
                Spacer(Modifier.size(4.dp))
                Text(
                    text = stringResource(R.string.crew_members),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.crew_new_group_members_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                draft.members.forEach { member ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = FocusShapeTokens.card,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CrewAvatar(
                                    preset = member.avatarPreset,
                                    palette = member.avatarPalette,
                                    path = null,
                                    size = 40.dp,
                                    modifier = Modifier.clickable {
                                        expandedAvatar = if (expandedAvatar == member.key) null else member.key
                                    },
                                )
                                OutlinedTextField(
                                    value = member.name,
                                    onValueChange = {
                                        onAction(CrewAction.DraftMemberNameChanged(member.key, it))
                                    },
                                    label = { Text(stringResource(R.string.crew_member_name)) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = hapticTap { onAction(CrewAction.RemoveDraftMember(member.key)) },
                                    enabled = draft.members.size > 1,
                                ) {
                                    Icon(
                                        FocusIcons.Delete,
                                        contentDescription = stringResource(R.string.crew_remove_member),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (expandedAvatar == member.key) {
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    repeat(CrewAvatars.PRESET_COUNT) { option ->
                                        FocusAvatar(
                                            style = AvatarStyle(option, member.avatarPalette),
                                            size = 34.dp,
                                            modifier = Modifier.clickable {
                                                onAction(
                                                    CrewAction.DraftMemberAvatarChanged(
                                                        member.key,
                                                        option,
                                                        member.avatarPalette,
                                                    ),
                                                )
                                                expandedAvatar = null
                                            },
                                        )
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = member.duty,
                                onValueChange = {
                                    onAction(CrewAction.DraftMemberDutyChanged(member.key, it))
                                },
                                label = { Text(stringResource(R.string.crew_member_duty)) },
                                minLines = 2,
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                FocusTonalButton(
                    text = stringResource(R.string.crew_add_member),
                    onClick = { onAction(CrewAction.AddDraftMember) },
                    icon = FocusIcons.PersonAdd,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    text = stringResource(R.string.crew_edit_members_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FocusPrimaryButton(
                text = stringResource(R.string.crew_save),
                onClick = { onAction(CrewAction.SaveGroup) },
                enabled = draft.name.isNotBlank() &&
                    !saving &&
                    (draft.id != null || draft.members.any { it.name.isNotBlank() }),
                loading = saving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrewMemberSheet(
    state: CrewUiState,
    onAction: (CrewAction) -> Unit,
    onPickPhoto: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val draft = state.memberDraft
    ModalBottomSheet(
        onDismissRequest = {
            if (draft != null) onAction(CrewAction.DismissMemberEditor) else onAction(CrewAction.CloseMembers)
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        if (draft == null) {
            MemberList(state = state, onAction = onAction)
        } else {
            MemberEditor(
                draft = draft,
                saving = state.saving,
                onAction = onAction,
                onPickPhoto = onPickPhoto,
            )
        }
    }
}

@Composable
private fun MemberList(state: CrewUiState, onAction: (CrewAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.crew_members),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = hapticTap { onAction(CrewAction.NewMember) }) {
                Icon(
                    FocusIcons.PersonAdd,
                    contentDescription = stringResource(R.string.crew_add_member),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.members.isEmpty()) {
            Text(
                text = stringResource(R.string.crew_no_members),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = state.members,
                    key = { it.id },
                    contentType = { "member" },
                ) { member ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = FocusShapeTokens.card,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        onClick = hapticTap { onAction(CrewAction.EditMember(member.id)) },
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CrewAvatar(
                                preset = member.avatarPreset,
                                palette = member.avatarPalette,
                                path = member.avatarPath,
                                size = 42.dp,
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = member.name,
                                        style = MaterialTheme.typography.titleSmallEmphasized,
                                    )
                                    if (state.members.firstOrNull()?.id == member.id) {
                                        FocusStatusPill(
                                            text = stringResource(R.string.crew_lead),
                                            tone = StatusTone.Active,
                                        )
                                    }
                                }
                                if (member.duty.isNotBlank()) {
                                    Text(
                                        text = member.duty,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Switch(
                                checked = member.active,
                                onCheckedChange = { checked ->
                                    onAction(CrewAction.SetMemberActive(member.id, checked))
                                },
                                enabled = !(member.active && state.members.size == 1),
                                modifier = Modifier.semantics {
                                    contentDescription = member.name
                                },
                            )
                        }
                    }
                }
                if (state.removedMembers.isNotEmpty()) {
                    item(key = "removed-header", contentType = "header") {
                        Text(
                            text = stringResource(R.string.crew_removed_members),
                            style = MaterialTheme.typography.titleSmallEmphasized,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                        )
                    }
                    items(
                        items = state.removedMembers,
                        key = { "removed-${it.id}" },
                        contentType = { "removed" },
                    ) { member ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = FocusShapeTokens.card,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                CrewAvatar(
                                    preset = member.avatarPreset,
                                    palette = member.avatarPalette,
                                    path = member.avatarPath,
                                    size = 36.dp,
                                    state = AvatarState.Sleep,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = member.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (member.duty.isNotBlank()) {
                                        Text(
                                            text = member.duty,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                FocusTextButton(
                                    text = stringResource(R.string.crew_restore_member),
                                    onClick = {
                                        onAction(CrewAction.SetMemberActive(member.id, true))
                                    },
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
private fun MemberEditor(
    draft: MemberDraft,
    saving: Boolean,
    onAction: (CrewAction) -> Unit,
    onPickPhoto: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(
                if (draft.id == null) R.string.crew_member_new else R.string.crew_member_edit,
            ),
            style = MaterialTheme.typography.titleLarge,
        )
        OutlinedTextField(
            value = draft.name,
            onValueChange = { onAction(CrewAction.MemberNameChanged(it)) },
            label = { Text(stringResource(R.string.crew_member_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.duty,
            onValueChange = { onAction(CrewAction.MemberDutyChanged(it)) },
            label = { Text(stringResource(R.string.crew_member_duty)) },
            minLines = 3,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.crew_avatar),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CrewAvatarPicker(
            preset = draft.avatarPreset,
            palette = draft.avatarPalette,
            path = draft.avatarPath,
            onPreset = { preset, palette -> onAction(CrewAction.MemberAvatarChanged(preset, palette)) },
            onPickPhoto = onPickPhoto,
            onClearPhoto = { onAction(CrewAction.MemberPhotoCleared) },
            enabled = !saving,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusPrimaryButton(
                text = stringResource(R.string.crew_save),
                onClick = { onAction(CrewAction.SaveMember) },
                enabled = draft.name.isNotBlank() && !saving,
                loading = saving,
                modifier = Modifier.weight(1f),
            )
            FocusOutlinedButton(
                text = stringResource(R.string.crew_cancel),
                onClick = { onAction(CrewAction.DismissMemberEditor) },
                enabled = !saving,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun CrewDeleteGroupDialog(
    group: CrewGroupUi,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.crew_delete_group_title)) },
        text = { Text(stringResource(R.string.crew_delete_group_body, group.name)) },
        confirmButton = {
            TextButton(onClick = hapticTap(onConfirm)) {
                Text(stringResource(R.string.crew_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = hapticTap(onDismiss)) {
                Text(stringResource(R.string.crew_cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrewMessageMenuSheet(
    menu: CrewMessageMenuUi,
    onAction: (CrewAction) -> Unit,
    onCopy: (String) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { onAction(CrewAction.CloseMessageMenu) },
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = menu.author.ifBlank { stringResource(R.string.crew_you) },
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Text(
                text = menu.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            if (menu.messageId != null) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val counts = menu.reactions.groupingBy { it }.eachCount()
                    CrewReactionChoices.forEach { emoji ->
                        val selected = counts[emoji] != null
                        FilterChip(
                            selected = selected,
                            onClick = hapticSelect {
                                onAction(CrewAction.React(menu.messageId, emoji))
                            },
                            label = {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(emoji, style = MaterialTheme.typography.labelLarge)
                                    if (selected) {
                                        Text(
                                            text = counts[emoji].toString(),
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                }
                            },
                            shape = RoundedCornerShape(FocusRadii.extraLarge),
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                CrewMenuAction(
                    icon = FocusIcons.Reply,
                    label = stringResource(R.string.crew_reply),
                    onClick = { onAction(CrewAction.ReplyTo(menu.key)) },
                )
                CrewMenuAction(
                    icon = FocusIcons.Edit,
                    label = stringResource(R.string.crew_edit_message),
                    enabled = menu.canEdit,
                    onClick = { onAction(CrewAction.StartEdit(menu.key)) },
                )
            }
            CrewMenuAction(
                icon = FocusIcons.Copy,
                label = stringResource(R.string.crew_copy),
                enabled = menu.text.isNotBlank(),
                onClick = { onCopy(menu.text) },
            )
            if (menu.messageId != null) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                CrewMenuAction(
                    icon = FocusIcons.Delete,
                    label = stringResource(R.string.crew_delete_message),
                    enabled = menu.canDelete,
                    destructive = true,
                    onClick = { onAction(CrewAction.AskDeleteMessage(menu.key)) },
                )
            }
        }
    }
}

@Composable
private fun CrewMenuAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    val content = when {
        !enabled -> scheme.onSurface.copy(alpha = 0.38f)
        destructive -> scheme.error
        else -> scheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = hapticTap(onClick))
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = content,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrewMessageEditSheet(
    edit: CrewEditUi,
    saving: Boolean,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.crew_edit_title),
                style = LocalFocusTypography.current.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            OutlinedTextField(
                value = edit.draft,
                onValueChange = onDraftChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.crew_edit_hint)) },
                minLines = 3,
                maxLines = 10,
                shape = MaterialTheme.shapes.largeIncreased,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FocusTextButton(
                    text = stringResource(R.string.crew_cancel),
                    onClick = onDismiss,
                    enabled = !saving,
                    modifier = Modifier.weight(1f),
                )
                FocusPrimaryButton(
                    text = stringResource(R.string.crew_edit_save),
                    onClick = onSave,
                    enabled = edit.draft.isNotBlank() && edit.draft.trim() != edit.original.trim(),
                    loading = saving,
                    modifier = Modifier.weight(1.8f),
                )
            }
        }
    }
}

@Composable
fun CrewMessageDeleteDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = FocusIcons.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = {
            Text(
                text = stringResource(R.string.crew_delete_message_title),
                style = LocalFocusTypography.current.titleLarge,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.crew_delete_message_body),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(
                onClick = hapticTap(onConfirm),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(stringResource(R.string.crew_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = hapticTap(onDismiss)) {
                Text(stringResource(R.string.crew_cancel))
            }
        },
    )
}
