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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.AvatarStyle
import fan.x0.focus.core.designsystem.components.FocusAvatar
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusOutlinedButton
import fan.x0.focus.core.designsystem.components.FocusPrimaryButton
import fan.x0.focus.core.designsystem.components.FocusStatusPill
import fan.x0.focus.core.designsystem.components.FocusTextButton
import fan.x0.focus.core.designsystem.components.FocusTonalButton
import fan.x0.focus.core.designsystem.components.StatusTone
import fan.x0.focus.core.designsystem.components.hapticTap
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens
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
                            IconButton(onClick = hapticTap { onAction(CrewAction.AskRemoveMember(member.id)) }) {
                                Icon(
                                    FocusIcons.Delete,
                                    contentDescription = stringResource(R.string.crew_remove_member),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
fun CrewRemoveMemberDialog(
    member: CrewMemberUi,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.crew_remove_member_title)) },
        text = { Text(stringResource(R.string.crew_remove_member_body, member.name)) },
        confirmButton = {
            TextButton(onClick = hapticTap(onConfirm)) {
                Text(stringResource(R.string.crew_remove_member))
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { onAction(CrewAction.CloseMessageMenu) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = menu.author.ifBlank { stringResource(R.string.crew_you) },
                style = MaterialTheme.typography.titleSmallEmphasized,
            )
            Text(
                text = menu.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (menu.messageId != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("👍", "❤️", "😂", "🎉", "👀").forEach { emoji ->
                        Surface(
                            shape = CircleShape,
                            color = if (emoji in menu.reactions) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            onClick = hapticTap {
                                onAction(CrewAction.React(menu.messageId, emoji))
                            },
                        ) {
                            Text(
                                text = emoji,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                FocusTextButton(
                    text = stringResource(R.string.crew_reply),
                    onClick = { onAction(CrewAction.ReplyTo(menu.key)) },
                )
            }
            FocusTextButton(
                text = stringResource(R.string.crew_copy),
                onClick = { onCopy(menu.text) },
            )
        }
    }
}
