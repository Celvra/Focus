package fan.x0.focus.feature.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusChip
import fan.x0.focus.core.designsystem.components.FocusEmptyState
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusOutlinedButton
import fan.x0.focus.core.designsystem.components.FocusPrimaryButton
import fan.x0.focus.core.designsystem.components.FocusStatusPill
import fan.x0.focus.core.designsystem.components.StatusTone
import fan.x0.focus.data.device.DeviceTrigger

@Composable
fun DeviceTriggersPane(state: DeviceUiState, onAction: (DeviceAction) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            FocusPrimaryButton(
                text = stringResource(R.string.device_trigger_add),
                onClick = { onAction(DeviceAction.OpenTrigger) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.triggers.isEmpty()) {
            item {
                Box(modifier = Modifier.fillParentMaxWidth(), contentAlignment = Alignment.Center) {
                    FocusEmptyState(
                        icon = FocusIcons.Bolt,
                        title = stringResource(R.string.device_triggers_empty_title),
                        body = stringResource(R.string.device_triggers_empty_body),
                        actionLabel = stringResource(R.string.device_trigger_add),
                        onAction = { onAction(DeviceAction.OpenTrigger) },
                    )
                }
            }
        } else {
            items(state.triggers, key = { it.id }) { trigger ->
                TriggerCard(trigger = trigger, onAction = onAction)
            }
        }
    }
    if (state.triggerOpen) {
        TriggerDialog(state = state, onAction = onAction)
    }
}

@Composable
private fun TriggerCard(trigger: DeviceTrigger, onAction: (DeviceAction) -> Unit) {
    DeviceCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = trigger.name,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                FocusStatusPill(
                    text = stringResource(
                        when (trigger.action) {
                            "notify" -> R.string.device_trigger_notify
                            "chat" -> R.string.device_trigger_chat
                            else -> R.string.device_trigger_shell
                        },
                    ),
                    tone = if (trigger.enabled) StatusTone.Active else StatusTone.Neutral,
                )
                Switch(
                    checked = trigger.enabled,
                    onCheckedChange = { onAction(DeviceAction.ToggleTrigger(trigger.id, it)) },
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            val filters = listOfNotNull(
                trigger.packageFilter.takeIf { it.isNotBlank() }?.let { "package=$it" },
                trigger.textFilter.takeIf { it.isNotBlank() }?.let { "text=$it" },
                trigger.typeFilter.takeIf { it.isNotBlank() }?.let { "type=$it" },
            ).joinToString("  ")
            if (filters.isNotBlank()) {
                Text(
                    text = filters,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (trigger.command.isNotBlank()) {
                Text(
                    text = trigger.command,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "${trigger.cooldownSec}s",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FocusOutlinedButton(
                text = stringResource(R.string.device_trigger_delete),
                onClick = { onAction(DeviceAction.DeleteTrigger(trigger.id)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TriggerDialog(state: DeviceUiState, onAction: (DeviceAction) -> Unit) {
    val isShell = state.triggerAction == "shell"
    AlertDialog(
        onDismissRequest = { onAction(DeviceAction.CloseTrigger) },
        title = { Text(stringResource(R.string.device_trigger_add)) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.triggerName,
                    onValueChange = { onAction(DeviceAction.TriggerNameChanged(it)) },
                    label = { Text(stringResource(R.string.device_trigger_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FocusChip(
                        label = stringResource(R.string.device_trigger_shell),
                        selected = isShell,
                        onClick = { onAction(DeviceAction.TriggerActionChanged("shell")) },
                    )
                    FocusChip(
                        label = stringResource(R.string.device_trigger_notify),
                        selected = !isShell,
                        onClick = { onAction(DeviceAction.TriggerActionChanged("notify")) },
                    )
                }
                OutlinedTextField(
                    value = state.triggerPackage,
                    onValueChange = { onAction(DeviceAction.TriggerPackageChanged(it)) },
                    label = { Text(stringResource(R.string.device_trigger_package)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.triggerText,
                    onValueChange = { onAction(DeviceAction.TriggerTextChanged(it)) },
                    label = { Text(stringResource(R.string.device_trigger_text)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.triggerType,
                    onValueChange = { onAction(DeviceAction.TriggerTypeChanged(it)) },
                    label = { Text(stringResource(R.string.device_trigger_type)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.triggerCommand,
                    onValueChange = { onAction(DeviceAction.TriggerCommandChanged(it)) },
                    label = {
                        Text(
                            stringResource(
                                if (isShell) R.string.device_trigger_command else R.string.device_trigger_notify_text,
                            ),
                        )
                    },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.triggerCooldown,
                    onValueChange = { onAction(DeviceAction.TriggerCooldownChanged(it)) },
                    label = { Text(stringResource(R.string.device_trigger_cooldown)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAction(DeviceAction.SaveTrigger) },
                enabled = state.triggerName.isNotBlank() && (!isShell || state.triggerCommand.isNotBlank()),
            ) {
                Text(stringResource(R.string.device_trigger_save))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(DeviceAction.CloseTrigger) }) {
                Text(stringResource(R.string.device_trigger_cancel))
            }
        },
    )
}
