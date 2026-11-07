package fan.x0.focus.feature.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusOutlinedButton
import fan.x0.focus.core.designsystem.components.FocusPrimaryButton
import fan.x0.focus.core.designsystem.components.FocusStatusPill
import fan.x0.focus.core.designsystem.components.StatusTone

@Composable
fun DeviceControlledPane(state: DeviceUiState, onAction: (DeviceAction) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            DeviceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.device_remote_enabled),
                            style = MaterialTheme.typography.titleSmallEmphasized,
                        )
                        Text(
                            text = stringResource(R.string.device_remote_enabled_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.remoteEnabled,
                        onCheckedChange = { onAction(DeviceAction.RemoteEnabledChanged(it)) },
                    )
                }
            }
        }
        item {
            DeviceCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DeviceStatusRow(
                        label = stringResource(R.string.device_server),
                        value = stringResource(
                            if (state.serverRunning) R.string.device_server_on else R.string.device_server_off,
                        ),
                        tone = if (state.serverRunning) StatusTone.Success else StatusTone.Neutral,
                    )
                    DeviceStatusRow(
                        label = stringResource(R.string.device_address),
                        value = if (state.address.isBlank()) "0.0.0.0:${state.port}" else "${state.address}:${state.port}",
                        tone = StatusTone.Neutral,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.device_pairing_code),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = state.pairingCode.ifBlank { "------" },
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        FocusPrimaryButton(
                            text = stringResource(R.string.device_new_code),
                            onClick = { onAction(DeviceAction.NewPairingCode) },
                            enabled = state.remoteEnabled,
                        )
                    }
                }
            }
        }
        item {
            DeviceCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.device_use_full_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FocusOutlinedButton(
                        text = stringResource(R.string.device_use_full),
                        onClick = { onAction(DeviceAction.UseFullMode) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        item {
            DeviceCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.device_controllers),
                        style = MaterialTheme.typography.titleSmallEmphasized,
                    )
                    if (state.controllers.isEmpty()) {
                        Text(
                            text = stringResource(R.string.device_controllers_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        state.controllers.forEach { controller ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = controller.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = controller.id.take(8),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                FocusOutlinedButton(
                                    text = stringResource(R.string.device_revoke),
                                    onClick = { onAction(DeviceAction.RevokeController(controller.id)) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
