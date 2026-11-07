package fan.x0.focus.feature.plugin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import fan.x0.focus.core.designsystem.components.FocusPrimaryButton
import fan.x0.focus.core.designsystem.components.FocusReveal
import fan.x0.focus.core.designsystem.components.FocusTonalButton
import fan.x0.focus.data.plugin.PluginRuntimeState
import fan.x0.focus.data.plugin.PluginStatus
import fan.x0.focus.plugin.PluginScaffold

@Composable
internal fun PluginListPage(
    state: PluginsUiState,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onInstallFile: () -> Unit,
    onInstallUrl: () -> Unit,
    onMarket: () -> Unit,
    onRegistry: () -> Unit,
) {
    PluginScaffold(title = stringResource(R.string.plugins_title), onBack = onBack) {
        item(key = "intro") {
            FocusReveal {
                Text(
                    text = stringResource(R.string.plugins_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        item(key = "actions") {
            FocusReveal(index = 1) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FocusPrimaryButton(
                        text = stringResource(R.string.plugins_install_file),
                        onClick = onInstallFile,
                        modifier = Modifier.fillMaxWidth(),
                        icon = FocusIcons.Add,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FocusTonalButton(
                            text = stringResource(R.string.plugins_install_url),
                            onClick = onInstallUrl,
                            modifier = Modifier.weight(1f),
                            icon = FocusIcons.Link,
                        )
                        FocusOutlinedButton(
                            text = stringResource(R.string.plugins_market),
                            onClick = onMarket,
                            modifier = Modifier.weight(1f),
                            icon = FocusIcons.Library,
                        )
                    }
                    FocusOutlinedButton(
                        text = stringResource(R.string.plugins_registry),
                        onClick = onRegistry,
                        modifier = Modifier.fillMaxWidth(),
                        icon = FocusIcons.Settings,
                    )
                }
            }
        }
        if (state.loading) {
            item(key = "loading") { FocusLoadingBlock() }
        } else if (state.plugins.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.plugins_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
                )
            }
        } else {
            item(key = "plugins") {
                FocusReveal(index = 2) {
                    FocusCardGroup {
                        state.plugins.forEach { plugin ->
                            item(
                                headlineContent = { Text(plugin.manifest.name) },
                                supportingContent = {
                                    Text(
                                        "${plugin.manifest.version} · ${plugin.state.label()}",
                                        color = if (plugin.state == PluginRuntimeState.Failed) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                },
                                leadingContent = {
                                    FocusCardGroupIcon(
                                        icon = FocusIcons.Tool,
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    )
                                },
                                trailingContent = {
                                    Switch(
                                        checked = plugin.enabled,
                                        onCheckedChange = { onToggle(plugin.manifest.id, it) },
                                    )
                                },
                                onClick = { onSelect(plugin.manifest.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun PluginRuntimeState.label(): String = when (this) {
    PluginRuntimeState.Disabled -> "disabled"
    PluginRuntimeState.Loading -> "loading"
    PluginRuntimeState.Loaded -> "loaded"
    PluginRuntimeState.Failed -> "failed"
}
