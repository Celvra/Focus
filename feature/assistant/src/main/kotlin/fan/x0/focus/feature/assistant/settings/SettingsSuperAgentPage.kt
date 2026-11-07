package fan.x0.focus.feature.assistant.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fan.x0.focus.core.designsystem.components.FocusCardGroup
import fan.x0.focus.core.designsystem.components.FocusCardGroupIcon
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusReveal
import fan.x0.focus.core.designsystem.error.HandleAppErrors
import fan.x0.focus.core.mvi.CollectEffect
import fan.x0.focus.data.assistant.AgentEventSource
import fan.x0.focus.feature.assistant.R
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsSuperAgentRoute(
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
    viewModel: SuperAgentViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HandleAppErrors(viewModel.errors, snackbar)
    val saved = stringResource(R.string.settings_super_saved)
    CollectEffect(viewModel.effect) { snackbar.showSnackbar(saved) }
    SettingsPageScaffold(
        title = stringResource(R.string.settings_super_title),
        onBack = onBack,
        snackbar = snackbar,
    ) {
        item(key = "intro") {
            FocusReveal {
                Text(
                    text = stringResource(R.string.settings_super_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
        }
        item(key = "sources") {
            FocusReveal(index = 1) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_super_sources),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    FocusCardGroup {
                        state.sources.forEach { row ->
                            item(
                                headlineContent = { Text(sourceLabel(row.source)) },
                                supportingContent = {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = sourceBody(row.source),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        if (row.speculative) {
                                            Text(
                                                text = stringResource(R.string.settings_super_speculative_on),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.tertiary,
                                            )
                                        }
                                    }
                                },
                                leadingContent = { FocusCardGroupIcon(icon = sourceIcon(row.source)) },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (supportsSpeculative(row.source)) {
                                            TextButton(
                                                onClick = {
                                                    viewModel.onAction(
                                                        SuperAgentAction.ToggleSpeculative(
                                                            row.source,
                                                            !row.speculative,
                                                        ),
                                                    )
                                                },
                                            ) {
                                                Text(stringResource(R.string.settings_super_speculative))
                                            }
                                        }
                                        Switch(
                                            checked = row.enabled,
                                            onCheckedChange = {
                                                viewModel.onAction(
                                                    SuperAgentAction.ToggleSource(row.source, it),
                                                )
                                            },
                                        )
                                    }
                                },
                                onClick = {
                                    viewModel.onAction(SuperAgentAction.ToggleSource(row.source, !row.enabled))
                                },
                            )
                        }
                    }
                }
            }
        }
        item(key = "runtime") {
            FocusReveal(index = 2) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_super_runtime),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    FocusCardGroup {
                        item(
                            headlineContent = { Text(stringResource(R.string.settings_super_concurrency)) },
                            supportingContent = {
                                Text(
                                    text = stringResource(R.string.settings_super_concurrency_body),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            leadingContent = { FocusCardGroupIcon(icon = FocusIcons.Groups) },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(
                                        onClick = {
                                            viewModel.onAction(
                                                SuperAgentAction.SetConcurrent(
                                                    (state.maxConcurrent - 1).coerceAtLeast(1),
                                                ),
                                            )
                                        },
                                    ) { Text("-") }
                                    Text(state.maxConcurrent.toString())
                                    TextButton(
                                        onClick = {
                                            viewModel.onAction(
                                                SuperAgentAction.SetConcurrent(
                                                    (state.maxConcurrent + 1).coerceAtMost(
                                                        fan.x0.focus.data.assistant.AgentEventConfig.MAX_CONCURRENT_LIMIT,
                                                    ),
                                                ),
                                            )
                                        },
                                    ) { Text("+") }
                                }
                            },
                            onClick = {},
                        )
                        item(
                            headlineContent = { Text(stringResource(R.string.settings_super_rate_limit)) },
                            supportingContent = {
                                Text(
                                    text = stringResource(R.string.settings_super_rate_limit_body, state.rateLimit),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            leadingContent = { FocusCardGroupIcon(icon = FocusIcons.Bolt) },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(
                                        onClick = {
                                            viewModel.onAction(
                                                SuperAgentAction.SetRateLimit((state.rateLimit - 10).coerceAtLeast(5)),
                                            )
                                        },
                                    ) { Text("-") }
                                    Text(state.rateLimit.toString())
                                    TextButton(
                                        onClick = {
                                            viewModel.onAction(
                                                SuperAgentAction.SetRateLimit((state.rateLimit + 10).coerceAtMost(300)),
                                            )
                                        },
                                    ) { Text("+") }
                                }
                            },
                            onClick = {},
                        )
                        item(
                            headlineContent = { Text(stringResource(R.string.settings_super_watchdog)) },
                            supportingContent = {
                                Text(
                                    text = stringResource(R.string.settings_super_watchdog_body),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            leadingContent = { FocusCardGroupIcon(icon = FocusIcons.Notification) },
                            trailingContent = {
                                Switch(
                                    checked = state.watchdog,
                                    onCheckedChange = {
                                        viewModel.onAction(SuperAgentAction.ToggleWatchdog(it))
                                    },
                                )
                            },
                            onClick = {
                                viewModel.onAction(SuperAgentAction.ToggleWatchdog(!state.watchdog))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun sourceLabel(source: AgentEventSource) = stringResource(
    when (source) {
        AgentEventSource.User -> R.string.settings_super_source_user
        AgentEventSource.SideChat -> R.string.settings_super_source_side_chat
        AgentEventSource.Background -> R.string.settings_super_source_background
        AgentEventSource.Reminder -> R.string.settings_super_source_reminder
        AgentEventSource.Device -> R.string.settings_super_source_device
        AgentEventSource.Feed -> R.string.settings_super_source_feed
        AgentEventSource.Watchdog -> R.string.settings_super_source_watchdog
        AgentEventSource.System -> R.string.settings_super_source_system
    },
)

@Composable
private fun sourceBody(source: AgentEventSource) = stringResource(
    when (source) {
        AgentEventSource.User -> R.string.settings_super_source_user_body
        AgentEventSource.SideChat -> R.string.settings_super_source_side_chat_body
        AgentEventSource.Background -> R.string.settings_super_source_background_body
        AgentEventSource.Reminder -> R.string.settings_super_source_reminder_body
        AgentEventSource.Device -> R.string.settings_super_source_device_body
        AgentEventSource.Feed -> R.string.settings_super_source_feed_body
        AgentEventSource.Watchdog -> R.string.settings_super_source_watchdog_body
        AgentEventSource.System -> R.string.settings_super_source_system_body
    },
)

private fun sourceIcon(source: AgentEventSource) = when (source) {
    AgentEventSource.User -> FocusIcons.Chat
    AgentEventSource.SideChat -> FocusIcons.Groups
    AgentEventSource.Background -> FocusIcons.Terminal
    AgentEventSource.Reminder -> FocusIcons.Notification
    AgentEventSource.Device -> FocusIcons.Device
    AgentEventSource.Feed -> FocusIcons.Feed
    AgentEventSource.Watchdog -> FocusIcons.Warning
    AgentEventSource.System -> FocusIcons.Settings
}

private fun supportsSpeculative(source: AgentEventSource) = when (source) {
    AgentEventSource.Background,
    AgentEventSource.Reminder,
    AgentEventSource.Device,
    AgentEventSource.Feed,
    -> true
    else -> false
}
