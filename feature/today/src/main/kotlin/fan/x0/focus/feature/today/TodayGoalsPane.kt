package fan.x0.focus.feature.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusCategoryIcons
import fan.x0.focus.core.designsystem.components.FocusDivider
import fan.x0.focus.core.designsystem.components.FocusIconBadge
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.hapticTap
import fan.x0.focus.core.designsystem.components.hapticToggle
import fan.x0.focus.core.designsystem.theme.LocalFocusTypography

@Composable
fun GoalsPane(
    state: TodayUiState,
    onAction: (TodayAction) -> Unit,
    contentPadding: PaddingValues,
    listState: LazyListState,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item(key = "track-head") {
            SectionTitle(
                text = stringResource(R.string.today_goals_track),
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
        }
        if (state.goals.isEmpty()) {
            item(key = "track-empty") {
                Text(
                    text = stringResource(R.string.today_goals_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        } else {
            itemsIndexed(state.goals, key = { _, goal -> goal.id }) { index, goal ->
                GoalRow(
                    goal = goal,
                    menuOpen = state.goalMenu == goal.id,
                    onAction = onAction,
                )
                if (index < state.goals.lastIndex) {
                    FocusDivider()
                }
            }
        }
        item(key = "create-head") {
            Column {
                FocusDivider(modifier = Modifier.padding(top = 24.dp))
                SectionTitle(
                    text = stringResource(R.string.today_goals_create),
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                )
            }
        }
        items(state.categories, key = { "category-${it.name}" }) { category ->
            CategoryRow(
                category = category,
                onClick = { onAction(TodayAction.NewGoalInCategory(category.name, category.icon)) },
            )
        }
        item(key = "new-goal") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = hapticTap { onAction(TodayAction.NewGoal) })
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(
                    imageVector = FocusIcons.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.today_goals_new),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = LocalFocusTypography.current.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun GoalRow(
    goal: GoalUi,
    menuOpen: Boolean,
    onAction: (TodayAction) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(
            checked = goal.done,
            onCheckedChange = hapticToggle { onAction(TodayAction.ToggleGoal(goal.id)) },
            modifier = Modifier.padding(top = 2.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp, top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = goal.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (goal.done) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textDecoration = if (goal.done) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = goal.detail
            if (!detail.isNullOrBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box {
            IconButton(onClick = hapticTap { onAction(TodayAction.GoalMenu(goal.id)) }) {
                Icon(
                    imageVector = FocusIcons.More,
                    contentDescription = stringResource(R.string.today_goal_more),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { onAction(TodayAction.CloseGoalMenu) },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.today_goal_edit)) },
                    onClick = hapticTap { onAction(TodayAction.EditGoal(goal.id)) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.today_goal_delete)) },
                    onClick = hapticTap { onAction(TodayAction.AskDeleteGoal(goal.id)) },
                )
            }
        }
    }
}

@Composable
private fun CategoryRow(category: CategoryOptionUi, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = hapticTap(onClick))
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FocusIconBadge(
            icon = FocusCategoryIcons.resolve(category.icon),
            size = 36.dp,
        )
        Text(
            text = category.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = FocusIcons.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}
