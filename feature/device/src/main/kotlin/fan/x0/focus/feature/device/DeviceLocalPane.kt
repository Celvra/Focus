package fan.x0.focus.feature.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusOutlinedButton
import fan.x0.focus.core.designsystem.components.FocusPrimaryButton
import fan.x0.focus.core.designsystem.components.FocusStatusPill
import fan.x0.focus.core.designsystem.components.StatusTone

@Composable
fun DeviceLocalPane(state: DeviceUiState, onAction: (DeviceAction) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            DeviceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.device_local_enabled),
                            style = MaterialTheme.typography.titleSmallEmphasized,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.device_local_enabled_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.localEnabled,
                        onCheckedChange = { onAction(DeviceAction.LocalEnabledChanged(it)) },
                    )
                }
            }
        }
        item {
            DeviceCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DeviceStatusRow(
                        label = stringResource(R.string.device_accessibility),
                        value = stringResource(
                            if (state.accessibility) {
                                R.string.device_accessibility_on
                            } else {
                                R.string.device_accessibility_off
                            },
                        ),
                        tone = if (state.accessibility) StatusTone.Success else StatusTone.Warning,
                    )
                    DeviceStatusRow(
                        label = stringResource(R.string.device_root),
                        value = stringResource(if (state.root) R.string.device_root_on else R.string.device_root_off),
                        tone = if (state.root) StatusTone.Success else StatusTone.Neutral,
                    )
                    DeviceStatusRow(
                        label = stringResource(R.string.device_shizuku),
                        value = stringResource(if (state.shizuku) R.string.device_root_on else R.string.device_root_off),
                        tone = if (state.shizuku) StatusTone.Success else StatusTone.Neutral,
                    )
                    FocusOutlinedButton(
                        text = stringResource(R.string.device_accessibility_open),
                        onClick = { onAction(DeviceAction.OpenAccessibility) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!state.shizuku) {
                        FocusOutlinedButton(
                            text = stringResource(R.string.device_shizuku_request),
                            onClick = { onAction(DeviceAction.RequestShizuku) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        item {
            DeviceCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (state.session) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.device_session_active),
                                style = MaterialTheme.typography.titleSmallEmphasized,
                                modifier = Modifier.weight(1f),
                            )
                            FocusStatusPill(
                                text = state.sessionOwner.ifBlank { "Focus" },
                                tone = StatusTone.Success,
                            )
                        }
                        FocusOutlinedButton(
                            text = stringResource(R.string.device_session_stop),
                            onClick = { onAction(DeviceAction.StopSession) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        FocusPrimaryButton(
                            text = stringResource(R.string.device_start),
                            onClick = { onAction(DeviceAction.StartLocal) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    FocusOutlinedButton(
                        text = stringResource(R.string.device_preview),
                        onClick = { onAction(DeviceAction.Preview) },
                        enabled = !state.previewing,
                        loading = state.previewing,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FocusOutlinedButton(
                        text = stringResource(
                            if (state.live && state.liveTarget == null) R.string.device_live_stop else R.string.device_live,
                        ),
                        onClick = { onAction(DeviceAction.ToggleLive(null)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        state.preview?.let { raw ->
            item {
                DevicePreview(raw = raw, title = stringResource(R.string.device_preview))
            }
        }
    }
}

