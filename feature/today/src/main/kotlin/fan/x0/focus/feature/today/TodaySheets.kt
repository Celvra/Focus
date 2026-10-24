package fan.x0.focus.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusCategoryIcons
import fan.x0.focus.core.designsystem.components.FocusIconBadge
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusPrimaryButton
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens
import fan.x0.focus.core.designsystem.theme.LocalFocusTypography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReasonSheet(update: UpdateUi, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                FocusIconBadge(
                    icon = FocusCategoryIcons.resolve(update.icon),
                    size = 44.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    if (update.category.isNotBlank()) {
                        Text(
                            text = update.category,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = update.headline,
                        style = LocalFocusTypography.current.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Text(
                text = stringResource(R.string.today_update_reason_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = update.reason,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeaSheet(idea: IdeaUi, onAction: (TodayAction) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = { onAction(TodayAction.CloseIdea) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = idea.title,
                style = LocalFocusTypography.current.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (idea.detail.isNotBlank()) {
                Text(
                    text = idea.detail,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (idea.steps.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.today_idea_includes),
                        modifier = Modifier.weight(1f),
                        style = LocalFocusTypography.current.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Icon(
                        imageVector = FocusIcons.Checklist,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
                idea.steps.forEach { step ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = step.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (step.detail.isNotBlank()) {
                                Text(
                                    text = step.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Icon(
                            imageVector = FocusIcons.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .size(20.dp),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalIconButton(
                    onClick = { onAction(TodayAction.IdeaMenu(idea.id)) },
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f),
                ) {
                    Icon(
                        imageVector = FocusIcons.MoreHoriz,
                        contentDescription = stringResource(R.string.today_idea_more),
                    )
                }
                FocusPrimaryButton(
                    text = stringResource(R.string.today_idea_start),
                    onClick = { onAction(TodayAction.StartIdea(idea.id)) },
                    icon = FocusIcons.Assistant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeaMenuDialog(
    idea: IdeaUi,
    onDismiss: () -> Unit,
    onMoreLike: () -> Unit,
    onNotInterested: () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = FocusShapeTokens.cardLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = idea.title,
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                DialogOption(
                    icon = FocusIcons.Assistant,
                    text = stringResource(R.string.today_idea_more_like),
                    onClick = onMoreLike,
                )
                DialogOption(
                    icon = FocusIcons.VisibilityOff,
                    text = stringResource(R.string.today_idea_not_interested),
                    onClick = onNotInterested,
                )
            }
        }
    }
}

@Composable
private fun DialogOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalEditorSheet(draft: GoalDraft, saving: Boolean, onAction: (TodayAction) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = { onAction(TodayAction.CancelGoal) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(
                    if (draft.id == null) R.string.today_goal_new_title else R.string.today_goal_edit_title,
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            if (draft.category.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FocusIconBadge(
                        icon = FocusCategoryIcons.resolve(draft.icon),
                        size = 28.dp,
                    )
                    Text(
                        text = draft.category,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            OutlinedTextField(
                value = draft.title,
                onValueChange = { onAction(TodayAction.GoalTitle(it)) },
                label = { Text(stringResource(R.string.today_goal_field_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.detail,
                onValueChange = { onAction(TodayAction.GoalDetail(it)) },
                label = { Text(stringResource(R.string.today_goal_field_detail)) },
                minLines = 3,
                maxLines = 6,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            FocusPrimaryButton(
                text = stringResource(R.string.today_goal_save),
                onClick = { onAction(TodayAction.SaveGoal) },
                enabled = draft.title.isNotBlank() && !saving,
                loading = saving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun DeleteGoalDialog(goal: GoalUi, onAction: (TodayAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(TodayAction.CancelDeleteGoal) },
        title = { Text(stringResource(R.string.today_goal_delete_title)) },
        text = { Text(stringResource(R.string.today_goal_delete_body, goal.title)) },
        confirmButton = {
            TextButton(onClick = { onAction(TodayAction.ConfirmDeleteGoal) }) {
                Text(stringResource(R.string.today_goal_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(TodayAction.CancelDeleteGoal) }) {
                Text(stringResource(R.string.today_cancel))
            }
        },
    )
}
