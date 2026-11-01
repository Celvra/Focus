package fan.x0.focus.feature.crew

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusEmptyState
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusLargeTopBar
import fan.x0.focus.core.designsystem.components.FocusLoadingBlock
import fan.x0.focus.core.designsystem.components.FocusStatusPill
import fan.x0.focus.core.designsystem.components.StatusTone
import fan.x0.focus.core.designsystem.components.hapticTap
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrewGroupListScreen(
    state: CrewUiState,
    onAction: (CrewAction) -> Unit,
    snackbar: SnackbarHostState,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            FocusLargeTopBar(
                title = stringResource(R.string.crew_title),
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = hapticTap { onAction(CrewAction.NewGroup) }) {
                        Icon(
                            FocusIcons.Add,
                            contentDescription = stringResource(R.string.crew_new_group),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = inner.calculateTopPadding() + 4.dp,
                bottom = inner.calculateBottomPadding() + 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                state.loading -> item(key = "loading", contentType = "loading") {
                    FocusLoadingBlock()
                }
                state.groups.isEmpty() -> item(key = "empty", contentType = "empty") {
                    FocusEmptyState(
                        icon = FocusIcons.Groups,
                        title = stringResource(R.string.crew_empty_title),
                        body = stringResource(R.string.crew_empty_body),
                        actionLabel = stringResource(R.string.crew_new_group),
                        onAction = { onAction(CrewAction.NewGroup) },
                    )
                }
                else -> items(
                    items = state.groups,
                    key = { it.id },
                    contentType = { "group" },
                ) { group ->
                    androidx.compose.foundation.layout.Box(modifier = Modifier.animateItem()) {
                        CrewGroupCard(
                            group = group,
                            onClick = { onAction(CrewAction.OpenGroup(group.id)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CrewGroupCard(group: CrewGroupUi, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = FocusShapeTokens.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = hapticTap(onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CrewAvatar(
                preset = group.avatarPreset,
                palette = group.avatarPalette,
                path = null,
                size = 56.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (group.running) {
                        FocusStatusPill(
                            text = stringResource(R.string.crew_running),
                            tone = StatusTone.Active,
                        )
                    }
                }
                if (group.topic.isNotBlank()) {
                    Text(
                        text = group.topic,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = when {
                        group.running -> stringResource(R.string.crew_working_line)
                        group.preview.isBlank() -> stringResource(R.string.crew_no_messages)
                        group.previewAuthor.isBlank() -> "${stringResource(R.string.crew_you)}: ${group.preview}"
                        else -> "${group.previewAuthor}: ${group.preview}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        group.members.take(3).forEach { member ->
                            CrewAvatar(
                                preset = member.avatarPreset,
                                palette = member.avatarPalette,
                                path = member.avatarPath,
                                size = 20.dp,
                            )
                        }
                    }
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = pluralStringResource(
                            R.plurals.crew_member_count,
                            group.memberCount,
                            group.memberCount,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (group.updatedAt > 0) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = DateUtils.getRelativeTimeSpanString(
                                group.updatedAt,
                                System.currentTimeMillis(),
                                DateUtils.MINUTE_IN_MILLIS,
                                DateUtils.FORMAT_ABBREV_RELATIVE,
                            ).toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Icon(
                imageVector = FocusIcons.Chevron,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
