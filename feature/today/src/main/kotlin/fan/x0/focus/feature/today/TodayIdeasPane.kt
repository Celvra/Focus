package fan.x0.focus.feature.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusCategoryIcons
import fan.x0.focus.core.designsystem.components.FocusDivider
import fan.x0.focus.core.designsystem.components.FocusEmptyState
import fan.x0.focus.core.designsystem.components.FocusIconBadge
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.theme.LocalFocusTypography

@Composable
fun IdeasPane(
    state: TodayUiState,
    onAction: (TodayAction) -> Unit,
    contentPadding: PaddingValues,
    listState: LazyListState,
) {
    val groups = state.ideas.groupBy { it.category }.entries.toList()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        if (state.ideas.isEmpty()) {
            item(key = "empty") {
                FocusEmptyState(
                    icon = FocusIcons.Idea,
                    title = stringResource(R.string.today_ideas_empty_title),
                    body = stringResource(R.string.today_ideas_empty_body),
                    actionLabel = stringResource(R.string.today_ideas_generate),
                    onAction = { onAction(TodayAction.AskForIdeas) },
                )
            }
        } else {
            groups.forEach { (category, ideas) ->
                item(key = "head-$category") {
                    Row(
                        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FocusIconBadge(
                            icon = FocusCategoryIcons.resolve(ideas.first().icon),
                            size = 32.dp,
                        )
                        Text(
                            text = category.ifBlank { stringResource(R.string.today_ideas_default_section) },
                            style = LocalFocusTypography.current.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                itemsIndexed(ideas, key = { _, idea -> idea.id }) { index, idea ->
                    IdeaRow(
                        idea = idea,
                        onClick = { onAction(TodayAction.OpenIdea(idea.id)) },
                    )
                    if (index < ideas.lastIndex) {
                        FocusDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun IdeaRow(idea: IdeaUi, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FocusIconBadge(
            icon = FocusCategoryIcons.resolve(idea.icon),
            size = 44.dp,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = idea.title,
                style = LocalFocusTypography.current.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (idea.detail.isNotBlank()) {
                Text(
                    text = idea.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
