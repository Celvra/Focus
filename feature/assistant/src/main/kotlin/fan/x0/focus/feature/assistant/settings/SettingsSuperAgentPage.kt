package fan.x0.focus.feature.assistant.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fan.x0.focus.core.designsystem.components.FocusCard
import fan.x0.focus.core.designsystem.components.FocusCardGroup
import fan.x0.focus.core.designsystem.components.FocusCardGroupIcon
import fan.x0.focus.core.designsystem.components.FocusChip
import fan.x0.focus.core.designsystem.components.FocusChoiceButtons
import fan.x0.focus.core.designsystem.components.FocusIconBadge
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusReveal
import fan.x0.focus.core.designsystem.components.FocusSectionHeader
import fan.x0.focus.core.designsystem.components.FocusStatusPill
import fan.x0.focus.core.designsystem.components.StatusTone
import fan.x0.focus.core.designsystem.components.switchItem
import fan.x0.focus.core.designsystem.error.HandleAppErrors
import fan.x0.focus.core.designsystem.theme.LocalFocusTypography
import fan.x0.focus.core.mvi.CollectEffect
import fan.x0.focus.data.assistant.AgentEventSource
import fan.x0.focus.feature.assistant.R
import org.koin.compose.viewmodel.koinViewModel
import java.util.Locale

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
        item(key = "hero") {
            FocusReveal {
                HeroCard(state)
            }
        }
        item(key = "sources") {
            FocusReveal(index = 1) {
                SourceSections(state, viewModel::onAction)
            }
        }
        item(key = "runtime") {
            FocusReveal(index = 2) {
                RuntimeSection(state, viewModel::onAction)
            }
        }
        item(key = "output") {
            FocusReveal(index = 3) {
                OutputSection(state, viewModel::onAction)
            }
        }
    }
}

@Composable
private fun HeroCard(state: SuperAgentUiState) {
    val scheme = MaterialTheme.colorScheme
    val enabled = state.sources.count { it.enabled }
    FocusCard(
        containerColor = scheme.primaryContainer,
        contentPadding = 20.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FocusIconBadge(
                icon = FocusIcons.Bolt,
                tint = scheme.onPrimaryContainer,
                container = scheme.primary,
                size = 56.dp,
            )
            Spacer(Modifier.size(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_super_hero_title),
                    style = LocalFocusTypography.current.titleLarge,
                    color = scheme.onPrimaryContainer,
                )
                Text(
                    text = stringResource(R.string.settings_super_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onPrimaryContainer.copy(alpha = 0.78f),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusStatusPill(
                text = stringResource(
                    R.string.settings_super_sources_count,
                    enabled,
                    state.sources.size,
                ),
                tone = StatusTone.Active,
            )
            FocusStatusPill(
                text = stringResource(
                    R.string.settings_super_concurrency_pill,
                    state.maxConcurrent,
                ),
                tone = StatusTone.Neutral,
            )
            FocusStatusPill(
                text = stringResource(
                    if (state.watchdog) {
                        R.string.settings_super_watchdog_on
                    } else {
                        R.string.settings_super_watchdog_off
                    },
                ),
                tone = if (state.watchdog) StatusTone.Success else StatusTone.Warning,
            )
        }
    }
}

@Composable
private fun SourceSections(
    state: SuperAgentUiState,
    onAction: (SuperAgentAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FocusSectionHeader(
            title = stringResource(R.string.settings_super_sources),
            subtitle = stringResource(R.string.settings_super_sources_body),
        )
        SourceGroup(
            title = stringResource(R.string.settings_super_group_chat),
            sources = listOf(AgentEventSource.User, AgentEventSource.SideChat),
            state = state,
            onAction = onAction,
        )
        SourceGroup(
            title = stringResource(R.string.settings_super_group_background),
            sources = listOf(
                AgentEventSource.Background,
                AgentEventSource.Reminder,
                AgentEventSource.Device,
                AgentEventSource.Feed,
            ),
            state = state,
            onAction = onAction,
        )
        SourceGroup(
            title = stringResource(R.string.settings_super_group_system),
            sources = listOf(AgentEventSource.Watchdog, AgentEventSource.System),
            state = state,
            onAction = onAction,
        )
    }
}

@Composable
private fun SourceGroup(
    title: String,
    sources: List<AgentEventSource>,
    state: SuperAgentUiState,
    onAction: (SuperAgentAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        FocusCardGroup {
            sources.forEach { source ->
                val row = state.sources.firstOrNull { it.source == source } ?: return@forEach
                switchItem(
                    checked = row.enabled,
                    checkedIcon = FocusIcons.Check,
                    onCheckedChange = { onAction(SuperAgentAction.ToggleSource(source, it)) },
                    headlineContent = { Text(sourceLabel(source)) },
                    supportingContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = sourceBody(source),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (supportsSpeculative(source) && row.enabled) {
                                FocusChip(
                                    label = stringResource(R.string.settings_super_speculative),
                                    selected = row.speculative,
                                    leadingIcon = FocusIcons.Bolt,
                                    onClick = {
                                        onAction(
                                            SuperAgentAction.ToggleSpeculative(
                                                source,
                                                !row.speculative,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    },
                    leadingContent = { FocusCardGroupIcon(icon = sourceIcon(source)) },
                )
            }
        }
    }
}

@Composable
private fun RuntimeSection(
    state: SuperAgentUiState,
    onAction: (SuperAgentAction) -> Unit,
) {
    val concurrencyOptions = remember(state.maxConcurrent) {
        (listOf(1, 2, 3, 4, 6, 8) + state.maxConcurrent).distinct().sorted()
    }
    val rateOptions = remember(state.rateLimit) {
        (listOf(30, 60, 120, 180, 300) + state.rateLimit).distinct().sorted()
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FocusSectionHeader(
            title = stringResource(R.string.settings_super_runtime),
            subtitle = stringResource(R.string.settings_super_runtime_body),
        )
        FocusCard {
            Text(
                text = stringResource(R.string.settings_super_concurrency),
                style = MaterialTheme.typography.titleSmallEmphasized,
            )
            Text(
                text = stringResource(R.string.settings_super_concurrency_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            FocusChoiceButtons(
                options = concurrencyOptions,
                selected = state.maxConcurrent,
                onSelect = { onAction(SuperAgentAction.SetConcurrent(it)) },
                label = { it.toString() },
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.settings_super_rate_limit),
                style = MaterialTheme.typography.titleSmallEmphasized,
            )
            Text(
                text = stringResource(R.string.settings_super_rate_limit_body, state.rateLimit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            FocusChoiceButtons(
                options = rateOptions,
                selected = state.rateLimit,
                onSelect = { onAction(SuperAgentAction.SetRateLimit(it)) },
                label = { stringResource(R.string.settings_super_rate_value, it) },
            )
        }
        FocusCardGroup {
            switchItem(
                checked = state.watchdog,
                checkedIcon = FocusIcons.Check,
                onCheckedChange = { onAction(SuperAgentAction.ToggleWatchdog(it)) },
                headlineContent = { Text(stringResource(R.string.settings_super_watchdog)) },
                supportingContent = {
                    Text(
                        text = stringResource(R.string.settings_super_watchdog_body),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                leadingContent = { FocusCardGroupIcon(icon = FocusIcons.Notification) },
            )
        }
    }
}

@Composable
private fun OutputSection(
    state: SuperAgentUiState,
    onAction: (SuperAgentAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FocusSectionHeader(
            title = stringResource(R.string.settings_super_output),
            subtitle = stringResource(R.string.settings_super_output_body),
        )
        FocusCardGroup {
            switchItem(
                checked = state.postToToday,
                checkedIcon = FocusIcons.Check,
                onCheckedChange = { onAction(SuperAgentAction.ToggleToday(it)) },
                headlineContent = { Text(stringResource(R.string.settings_super_today)) },
                supportingContent = {
                    Text(
                        text = stringResource(R.string.settings_super_today_body),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                leadingContent = { FocusCardGroupIcon(icon = FocusIcons.Today) },
            )
        }
        FocusCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FocusCardGroupIcon(icon = FocusIcons.Today)
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_super_usage),
                        style = MaterialTheme.typography.titleSmallEmphasized,
                    )
                    Text(
                        text = stringResource(R.string.settings_super_usage_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                UsageValue(
                    label = stringResource(R.string.settings_super_usage_input),
                    value = compactCount(state.usage.inputTokens),
                )
                UsageValue(
                    label = stringResource(R.string.settings_super_usage_output),
                    value = compactCount(state.usage.outputTokens),
                )
                UsageValue(
                    label = stringResource(R.string.settings_super_usage_messages),
                    value = compactCount(state.usage.messages.toLong()),
                )
            }
        }
    }
}

@Composable
private fun UsageValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = value,
            style = LocalFocusTypography.current.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun compactCount(value: Long): String = when {
    value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
    value >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1_000.0)
    else -> value.toString()
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
