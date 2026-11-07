package fan.x0.focus.feature.plugin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import fan.x0.focus.core.designsystem.components.FocusOutlinedButton
import fan.x0.focus.core.designsystem.components.FocusPrimaryButton
import fan.x0.focus.core.designsystem.components.FocusReveal
import fan.x0.focus.data.plugin.PluginCapability
import fan.x0.focus.data.plugin.PluginStatus
import fan.x0.focus.plugin.PluginScaffold
import fan.x0.focus.plugin.PluginSystemHost
import org.koin.compose.koinInject

@Composable
internal fun PluginDetailPage(
    status: PluginStatus,
    busy: Boolean,
    onBack: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onCapability: (String, Boolean) -> Unit,
    onReload: () -> Unit,
    onLogs: () -> Unit,
    onUninstall: () -> Unit,
    onOpenPage: (String) -> Unit,
) {
    val system = koinInject<PluginSystemHost>()
    PluginScaffold(title = status.manifest.name, onBack = onBack) {
        item(key = "header") {
            FocusReveal {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = status.manifest.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${stringResource(R.string.plugins_version)} ${status.manifest.version} · ${status.source}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (status.error.isNotBlank()) {
                        Text(
                            text = status.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
        item(key = "enable") {
            FocusReveal(index = 1) {
                FocusCardGroup {
                    formItem(
                        headlineContent = { Text(stringResource(R.string.plugins_enabled)) },
                        leadingContent = {
                            FocusCardGroupIcon(
                                icon = FocusIcons.Tool,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        },
                    ) {
                        Switch(checked = status.enabled, onCheckedChange = onEnabled)
                    }
                }
            }
        }
        item(key = "capabilities") {
            FocusReveal(index = 2) {
                FocusCardGroup(title = stringResource(R.string.plugins_capabilities)) {
                    PluginCapability.all.sorted().forEach { capability ->
                        formItem(
                            headlineContent = { Text(capabilityLabel(capability)) },
                            supportingContent = { Text(capabilityDescription(capability)) },
                            leadingContent = {
                                FocusCardGroupIcon(
                                    icon = FocusIcons.Settings,
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            },
                        ) {
                            Switch(
                                checked = capability in status.capabilities,
                                onCheckedChange = { onCapability(capability, it) },
                            )
                        }
                    }
                    if (PluginCapability.OVERLAY in status.capabilities) {
                        item(
                            headlineContent = { Text("Open overlay permission") },
                            onClick = { system.activity.openOverlaySettings() },
                        )
                    }
                    if (PluginCapability.ACCESSIBILITY in status.capabilities) {
                        item(
                            headlineContent = { Text("Open accessibility settings") },
                            onClick = { system.activity.openAccessibilitySettings() },
                        )
                    }
                    if (PluginCapability.NOTIFICATION_LISTENER in status.capabilities) {
                        item(
                            headlineContent = { Text("Open notification access") },
                            onClick = { system.activity.openNotificationListenerSettings() },
                        )
                    }
                }
            }
        }
        if (status.pages.isNotEmpty()) {
            item(key = "pages") {
                FocusReveal(index = 3) {
                    FocusCardGroup(title = stringResource(R.string.plugins_pages)) {
                        status.pages.forEach { page ->
                            item(
                                headlineContent = { Text(page.title) },
                                supportingContent = { Text(page.pageId) },
                                leadingContent = {
                                    FocusCardGroupIcon(
                                        icon = FocusIcons.Chat,
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    )
                                },
                                onClick = { onOpenPage(page.pageId) },
                            )
                        }
                    }
                }
            }
        }
        item(key = "tools") {
            FocusReveal(index = 4) {
                FocusCardGroup(title = stringResource(R.string.plugins_tools)) {
                    if (status.tools.isEmpty()) {
                        item(headlineContent = { Text(stringResource(R.string.plugins_no_tools)) })
                    } else {
                        status.tools.forEach { tool ->
                            item(
                                headlineContent = { Text(tool.substringAfter("__")) },
                                supportingContent = { Text(tool) },
                            )
                        }
                    }
                }
            }
        }
        item(key = "skills") {
            FocusReveal(index = 5) {
                FocusCardGroup(title = stringResource(R.string.plugins_skills)) {
                    if (status.skills.isEmpty()) {
                        item(headlineContent = { Text(stringResource(R.string.plugins_no_skills)) })
                    } else {
                        status.skills.forEach { skill ->
                            item(headlineContent = { Text(skill) })
                        }
                    }
                }
            }
        }
        item(key = "actions") {
            FocusReveal(index = 6) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FocusPrimaryButton(
                            text = stringResource(R.string.plugins_reload),
                            onClick = onReload,
                            modifier = Modifier.weight(1f),
                            enabled = !busy,
                            icon = FocusIcons.Refresh,
                        )
                        FocusOutlinedButton(
                            text = stringResource(R.string.plugins_logs),
                            onClick = onLogs,
                            modifier = Modifier.weight(1f),
                            icon = FocusIcons.File,
                        )
                    }
                    FocusOutlinedButton(
                        text = stringResource(R.string.plugins_uninstall),
                        onClick = onUninstall,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy,
                        icon = FocusIcons.Delete,
                    )
                }
            }
        }
    }
}

@Composable
private fun capabilityLabel(capability: String): String = when (capability) {
    PluginCapability.OVERLAY -> stringResource(R.string.plugins_cap_overlay)
    PluginCapability.FOREGROUND -> stringResource(R.string.plugins_cap_foreground)
    PluginCapability.ACCESSIBILITY -> stringResource(R.string.plugins_cap_accessibility)
    PluginCapability.NOTIFICATION_LISTENER -> stringResource(R.string.plugins_cap_notification_listener)
    PluginCapability.TILE -> stringResource(R.string.plugins_cap_tile)
    PluginCapability.WIDGET -> stringResource(R.string.plugins_cap_widget)
    PluginCapability.BOOT -> stringResource(R.string.plugins_cap_boot)
    PluginCapability.SCREEN -> stringResource(R.string.plugins_cap_screen)
    else -> capability
}

private fun capabilityDescription(capability: String): String = when (capability) {
    PluginCapability.ACCESSIBILITY -> "Read the screen and perform gestures"
    PluginCapability.NOTIFICATION_LISTENER -> "Read and dismiss notifications"
    PluginCapability.OVERLAY -> "Show content above other apps"
    PluginCapability.SCREEN -> "Capture the screen"
    PluginCapability.BOOT -> "Restore after the phone restarts"
    PluginCapability.TILE -> "Add a quick settings tile"
    PluginCapability.WIDGET -> "Add a home screen widget"
    PluginCapability.FOREGROUND -> "Keep a background service alive"
    else -> ""
}
