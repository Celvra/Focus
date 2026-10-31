package fan.x0.focus.feature.assistant.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusCardGroup
import fan.x0.focus.core.designsystem.components.FocusCardGroupIcon
import fan.x0.focus.core.designsystem.components.FocusCardGroupScope
import fan.x0.focus.core.designsystem.components.FocusChip
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusReveal
import fan.x0.focus.feature.assistant.R
import fan.x0.focus.feature.assistant.SettingsAction
import fan.x0.focus.feature.assistant.SettingsUiState

@Composable
internal fun SettingsToolsPage(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onOpen: (SettingsDestination) -> Unit,
    onBack: () -> Unit,
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_page_tools),
        onBack = onBack,
    ) {
        item(key = "intro") {
            FocusReveal {
                Text(
                    text = stringResource(R.string.settings_page_tools_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        item(key = "tools") {
            FocusReveal(index = 1) {
                val searchTitle = stringResource(R.string.settings_tools_search_title)
                val searchBody = stringResource(R.string.settings_tools_search_body)
                val mcpTitle = stringResource(R.string.settings_tools_mcp_title)
                val mcpBody = stringResource(R.string.settings_tools_mcp_body)
                val indexTitle = stringResource(R.string.settings_tools_index_title)
                val indexBody = stringResource(R.string.settings_tools_index_body)
                FocusCardGroup {
                    toolItem(
                        title = searchTitle,
                        body = searchBody,
                        icon = FocusIcons.Search,
                        onClick = { onOpen(SettingsDestination.WebSearch) },
                    )
                    toolItem(
                        title = mcpTitle,
                        body = mcpBody,
                        icon = FocusIcons.Tool,
                        onClick = { onOpen(SettingsDestination.Mcp) },
                    )
                    toolItem(
                        title = indexTitle,
                        body = indexBody,
                        icon = FocusIcons.Folder,
                        onClick = { onOpen(SettingsDestination.WorkspaceIndex) },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.settings_tools_bridge_title)) },
                        supportingContent = { Text(stringResource(R.string.settings_tools_bridge_body)) },
                        leadingContent = {
                            FocusCardGroupIcon(
                                icon = FocusIcons.Terminal,
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        },
                    )
                }
            }
        }
        item(key = "feeds") {
            FocusReveal(index = 2) {
                FeedsGroup(state = state, onAction = onAction)
            }
        }
    }
}

@Composable
private fun FeedsGroup(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    FocusCardGroup(title = stringResource(R.string.settings_tools_feeds_title)) {
        formItem(
            headlineContent = { Text(stringResource(R.string.settings_feeds_item)) },
            supportingContent = { Text(stringResource(R.string.settings_feeds_body)) },
            leadingContent = {
                FocusCardGroupIcon(
                    icon = FocusIcons.Feed,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            },
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(1, 3, 6, 12, 24).forEach { hours ->
                    FocusChip(
                        label = stringResource(R.string.assistant_hours, hours),
                        selected = state.refreshHours == hours,
                        onClick = { onAction(SettingsAction.RefreshHoursChanged(hours)) },
                    )
                }
            }
        }
    }
}

private fun FocusCardGroupScope.toolItem(
    title: String,
    body: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    item(
        headlineContent = { Text(title) },
        supportingContent = { Text(body) },
        leadingContent = {
            FocusCardGroupIcon(icon = icon)
        },
        trailingContent = {
            Icon(
                imageVector = FocusIcons.Chevron,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        onClick = onClick,
    )
}
