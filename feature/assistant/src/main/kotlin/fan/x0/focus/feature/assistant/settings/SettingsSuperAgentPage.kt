package fan.x0.focus.feature.assistant.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fan.x0.focus.core.designsystem.components.FocusCard
import fan.x0.focus.core.designsystem.components.FocusCardGroup
import fan.x0.focus.core.designsystem.components.FocusCardGroupIcon
import fan.x0.focus.core.designsystem.components.FocusChip
import fan.x0.focus.core.designsystem.components.FocusChoiceButtons
import fan.x0.focus.core.designsystem.components.FocusIconBadge
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusOutlinedButton
import fan.x0.focus.core.designsystem.components.FocusPrimaryButton
import fan.x0.focus.core.designsystem.components.FocusReveal
import fan.x0.focus.core.designsystem.components.FocusSectionHeader
import fan.x0.focus.core.designsystem.components.FocusStatusPill
import fan.x0.focus.core.designsystem.components.FocusTextButton
import fan.x0.focus.core.designsystem.components.FocusTonalButton
import fan.x0.focus.core.designsystem.components.StatusTone
import fan.x0.focus.core.designsystem.components.hapticTap
import fan.x0.focus.core.designsystem.components.switchItem
import fan.x0.focus.core.designsystem.error.HandleAppErrors
import fan.x0.focus.core.designsystem.theme.LocalFocusTypography
import fan.x0.focus.core.mvi.CollectEffect
import fan.x0.focus.data.assistant.AgentEventSource
import fan.x0.focus.data.assistant.AgentTrigger
import fan.x0.focus.data.assistant.AgentTriggerKind
import fan.x0.focus.data.assistant.CronExpression
import fan.x0.focus.data.assistant.WorkerProfile
import fan.x0.focus.feature.assistant.R
import org.koin.compose.viewmodel.koinViewModel
import java.text.DateFormat
import java.util.Date
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
    val deleted = stringResource(R.string.settings_super_trigger_deleted)
    CollectEffect(viewModel.effect) { effect ->
        snackbar.showSnackbar(
            when (effect) {
                SuperAgentEffect.Saved -> saved
                SuperAgentEffect.TriggerDeleted -> deleted
            },
        )
    }
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
        item(key = "triggers") {
            FocusReveal(index = 1) {
                TriggerSection(state, viewModel::onAction)
            }
        }
        item(key = "sources") {
            FocusReveal(index = 2) {
                SourceSections(state, viewModel::onAction)
            }
        }
        item(key = "runtime") {
            FocusReveal(index = 3) {
                RuntimeSection(state, viewModel::onAction)
            }
        }
        item(key = "output") {
            FocusReveal(index = 4) {
                OutputSection(state, viewModel::onAction)
            }
        }
    }
    TriggerEditorSheet(state, viewModel::onAction)
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
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
private fun TriggerSection(
    state: SuperAgentUiState,
    onAction: (SuperAgentAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FocusSectionHeader(
            title = stringResource(R.string.settings_super_triggers),
            subtitle = stringResource(R.string.settings_super_triggers_body),
        )
        if (state.triggers.isEmpty()) {
            FocusCard {
                Text(
                    text = stringResource(R.string.settings_super_trigger_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            FocusCardGroup {
                state.triggers.forEach { trigger ->
                    item(
                        headlineContent = { Text(trigger.name) },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = triggerDetail(trigger, state.webhookBase),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (trigger.kind == AgentTriggerKind.Cron && trigger.nextRun > 0) {
                                    Text(
                                        text = stringResource(
                                            R.string.settings_super_trigger_next,
                                            formatTime(trigger.nextRun),
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (trigger.lastStatus.isNotBlank()) {
                                    Text(
                                        text = trigger.lastStatus,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        leadingContent = {
                            FocusCardGroupIcon(
                                icon = if (trigger.kind == AgentTriggerKind.Cron) {
                                    FocusIcons.Calendar
                                } else {
                                    FocusIcons.Link
                                },
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = trigger.enabled,
                                onCheckedChange = {
                                    onAction(SuperAgentAction.ToggleTrigger(trigger.id, it))
                                },
                            )
                        },
                        onClick = { onAction(SuperAgentAction.EditTrigger(trigger.id)) },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            FocusTonalButton(
                text = stringResource(R.string.settings_super_trigger_new),
                icon = FocusIcons.Add,
                onClick = { onAction(SuperAgentAction.NewTrigger) },
            )
        }
    }
}

@Composable
private fun triggerDetail(trigger: AgentTrigger, webhookBase: String): String =
    if (trigger.kind == AgentTriggerKind.Cron) {
        trigger.schedule
    } else {
        "$webhookBase${trigger.webhookPath}"
    }

private fun formatTime(epochMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochMs))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TriggerEditorSheet(
    state: SuperAgentUiState,
    onAction: (SuperAgentAction) -> Unit,
) {
    val draft = state.triggerDraft ?: return
    val cronError = if (draft.kind == AgentTriggerKind.Cron) {
        CronExpression.validate(draft.schedule)
    } else {
        null
    }
    val nextRun = remember(draft.schedule, draft.timezone, draft.kind) {
        if (draft.kind != AgentTriggerKind.Cron || cronError != null) {
            null
        } else {
            runCatching {
                CronExpression.parse(draft.schedule)
                    .next(zoneId = java.time.ZoneId.of(draft.timezone))
            }.getOrNull()
        }
    }
    ModalBottomSheet(
        onDismissRequest = { onAction(SuperAgentAction.DismissTrigger) },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(
                    if (draft.id == null) {
                        R.string.settings_super_trigger_new
                    } else {
                        R.string.settings_super_trigger_edit
                    },
                ),
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
            FocusChoiceButtons(
                options = AgentTriggerKind.entries,
                selected = draft.kind,
                onSelect = { onAction(SuperAgentAction.TriggerDraftChanged(draft.copy(kind = it))) },
                label = { kind ->
                    stringResource(
                        if (kind == AgentTriggerKind.Cron) {
                            R.string.settings_super_trigger_kind_cron
                        } else {
                            R.string.settings_super_trigger_kind_webhook
                        },
                    )
                },
            )
            OutlinedTextField(
                value = draft.name,
                onValueChange = { onAction(SuperAgentAction.TriggerDraftChanged(draft.copy(name = it))) },
                label = { Text(stringResource(R.string.settings_super_trigger_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.prompt,
                onValueChange = { onAction(SuperAgentAction.TriggerDraftChanged(draft.copy(prompt = it))) },
                label = { Text(stringResource(R.string.settings_super_trigger_prompt)) },
                placeholder = { Text(stringResource(R.string.settings_super_trigger_prompt_hint)) },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            if (draft.kind == AgentTriggerKind.Cron) {
                OutlinedTextField(
                    value = draft.schedule,
                    onValueChange = {
                        onAction(SuperAgentAction.TriggerDraftChanged(draft.copy(schedule = it)))
                    },
                    label = { Text(stringResource(R.string.settings_super_trigger_schedule)) },
                    placeholder = { Text(stringResource(R.string.settings_super_trigger_schedule_hint)) },
                    singleLine = true,
                    isError = cronError != null,
                    supportingText = {
                        Text(
                            cronError ?: stringResource(R.string.settings_super_trigger_schedule_help),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (nextRun != null) {
                    Text(
                        text = stringResource(
                            R.string.settings_super_trigger_next,
                            formatTime(nextRun),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val trigger = state.triggers.firstOrNull { it.id == draft.id }
                Text(
                    text = if (trigger == null) {
                        stringResource(R.string.settings_super_trigger_webhook_pending)
                    } else {
                        "${state.webhookBase}${trigger.webhookPath}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.settings_super_trigger_profile),
                style = MaterialTheme.typography.labelLarge,
            )
            FocusChoiceButtons(
                options = WorkerProfile.entries,
                selected = draft.profile,
                onSelect = { onAction(SuperAgentAction.TriggerDraftChanged(draft.copy(profile = it))) },
                label = { profile ->
                    stringResource(
                        when (profile) {
                            WorkerProfile.Light -> R.string.settings_super_profile_light
                            WorkerProfile.Standard -> R.string.settings_super_profile_standard
                            WorkerProfile.Heavy -> R.string.settings_super_profile_heavy
                        },
                    )
                },
            )
            state.triggerError?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (draft.id != null) {
                    FocusTextButton(
                        text = stringResource(R.string.settings_super_trigger_delete),
                        onClick = { onAction(SuperAgentAction.DeleteTrigger(draft.id)) },
                    )
                }
                Spacer(Modifier.weight(1f))
                FocusTextButton(
                    text = stringResource(R.string.settings_super_trigger_cancel),
                    onClick = { onAction(SuperAgentAction.DismissTrigger) },
                )
                FocusPrimaryButton(
                    text = stringResource(R.string.settings_super_trigger_save),
                    onClick = { onAction(SuperAgentAction.SaveTrigger) },
                    loading = state.triggerSaving,
                )
            }
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
                AgentEventSource.Schedule,
                AgentEventSource.Webhook,
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
        AgentEventSource.Schedule -> R.string.settings_super_source_schedule
        AgentEventSource.Webhook -> R.string.settings_super_source_webhook
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
        AgentEventSource.Schedule -> R.string.settings_super_source_schedule_body
        AgentEventSource.Webhook -> R.string.settings_super_source_webhook_body
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
    AgentEventSource.Schedule -> FocusIcons.Calendar
    AgentEventSource.Webhook -> FocusIcons.Link
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
