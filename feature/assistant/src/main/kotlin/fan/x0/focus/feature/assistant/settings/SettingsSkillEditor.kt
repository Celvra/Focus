package fan.x0.focus.feature.assistant.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusOutlinedButton
import fan.x0.focus.core.designsystem.components.FocusPrimaryButton
import fan.x0.focus.core.designsystem.components.FocusTextButton
import fan.x0.focus.core.designsystem.components.hapticTap
import fan.x0.focus.core.designsystem.components.hapticToggle
import fan.x0.focus.feature.assistant.R
import fan.x0.focus.feature.assistant.SkillDraftUi
import fan.x0.focus.feature.assistant.SkillsAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SkillEditorSheet(
    draft: SkillDraftUi,
    busy: Boolean,
    invalid: Boolean,
    onAction: (SkillsAction) -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    ModalBottomSheet(
        onDismissRequest = { onAction(SkillsAction.CloseDraft) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        when {
                            draft.readOnly -> R.string.settings_skills_read_only
                            draft.id.isBlank() -> R.string.settings_skills_new
                            else -> R.string.settings_skills_edit
                        },
                    ),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = hapticTap { onAction(SkillsAction.CloseDraft) }) {
                    Icon(
                        imageVector = FocusIcons.Close,
                        contentDescription = stringResource(R.string.settings_skills_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (draft.readOnly) {
                ReadOnlySkill(draft = draft, onAction = onAction)
            } else {
                EditableSkill(
                    draft = draft,
                    busy = busy,
                    invalid = invalid,
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
private fun ReadOnlySkill(
    draft: SkillDraftUi,
    onAction: (SkillsAction) -> Unit,
) {
    Text(
        text = draft.name,
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        text = draft.description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (draft.location.isNotBlank()) {
        Text(
            text = draft.location,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (draft.body.isNotBlank()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                text = draft.body,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(14.dp),
            )
        }
    }
    if (draft.files.isNotEmpty()) {
        Text(
            text = stringResource(
                R.string.settings_skills_files,
                draft.files.joinToString(", "),
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.settings_skills_enabled),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = draft.enabled,
            onCheckedChange = hapticToggle { onAction(SkillsAction.SetEnabled(draft.id, it)) },
        )
    }
    Text(
        text = stringResource(R.string.settings_skills_read_only_body),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EditableSkill(
    draft: SkillDraftUi,
    busy: Boolean,
    invalid: Boolean,
    onAction: (SkillsAction) -> Unit,
) {
    OutlinedTextField(
        value = draft.name,
        onValueChange = { onAction(SkillsAction.NameChanged(it)) },
        label = { Text(stringResource(R.string.settings_skills_name)) },
        singleLine = true,
        isError = invalid && draft.name.isBlank(),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft.description,
        onValueChange = { onAction(SkillsAction.DescriptionChanged(it)) },
        label = { Text(stringResource(R.string.settings_skills_description)) },
        minLines = 2,
        isError = invalid && draft.description.isBlank(),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft.body,
        onValueChange = { onAction(SkillsAction.BodyChanged(it)) },
        label = { Text(stringResource(R.string.settings_skills_body)) },
        minLines = 10,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.settings_skills_enabled),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = draft.enabled,
            onCheckedChange = hapticToggle { onAction(SkillsAction.EnabledChanged(it)) },
        )
    }
    if (invalid) {
        Text(
            text = stringResource(R.string.settings_skills_error_required),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FocusOutlinedButton(
            text = stringResource(R.string.settings_skills_close),
            onClick = { onAction(SkillsAction.CloseDraft) },
            enabled = !busy,
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
        )
        FocusPrimaryButton(
            text = stringResource(R.string.settings_skills_save),
            onClick = { onAction(SkillsAction.Save) },
            enabled = !busy,
            loading = busy,
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
        )
    }
    if (draft.id.isNotBlank()) {
        FocusTextButton(
            text = stringResource(R.string.settings_skills_delete),
            onClick = { onAction(SkillsAction.RequestDelete) },
            enabled = !busy,
        )
    }
}
