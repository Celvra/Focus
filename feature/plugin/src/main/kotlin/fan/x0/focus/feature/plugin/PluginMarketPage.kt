package fan.x0.focus.feature.plugin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusCardGroup
import fan.x0.focus.core.designsystem.components.FocusCardGroupIcon
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusLoadingBlock
import fan.x0.focus.core.designsystem.components.FocusOutlinedButton
import fan.x0.focus.core.designsystem.components.FocusReveal
import fan.x0.focus.data.plugin.MarketplacePlugin
import fan.x0.focus.plugin.PluginScaffold

@Composable
internal fun PluginMarketPage(
    state: PluginsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onQuery: (String) -> Unit,
    onInstall: (MarketplacePlugin) -> Unit,
) {
    PluginScaffold(
        title = stringResource(R.string.plugins_market_title),
        onBack = onBack,
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(FocusIcons.Refresh, contentDescription = stringResource(R.string.plugins_market))
            }
        },
    ) {
        item(key = "search") {
            FocusReveal {
                OutlinedTextField(
                    value = state.marketQuery,
                    onValueChange = onQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.plugins_market_search)) },
                    singleLine = true,
                    leadingIcon = { Icon(FocusIcons.Search, contentDescription = null) },
                )
            }
        }
        if (state.marketLoading) {
            item(key = "loading") { FocusLoadingBlock() }
        } else if (state.market.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.plugins_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
                )
            }
        } else {
            item(key = "list") {
                FocusReveal(index = 1) {
                    FocusCardGroup {
                        state.market.forEach { entry ->
                            item(
                                headlineContent = { Text(entry.name) },
                                supportingContent = {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("${entry.version} · ${entry.author}")
                                        if (entry.summary.isNotBlank()) {
                                            Text(
                                                entry.summary,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                },
                                leadingContent = {
                                    FocusCardGroupIcon(
                                        icon = FocusIcons.Tool,
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    )
                                },
                            )
                            item(
                                headlineContent = {
                                    val installed = state.plugins.firstOrNull { it.manifest.id == entry.id }
                                    FocusOutlinedButton(
                                        text = if (installed == null) {
                                            stringResource(R.string.plugins_install)
                                        } else {
                                            stringResource(R.string.plugins_installed)
                                        },
                                        onClick = { onInstall(entry) },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = installed == null && !state.busy,
                                        icon = FocusIcons.Add,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
