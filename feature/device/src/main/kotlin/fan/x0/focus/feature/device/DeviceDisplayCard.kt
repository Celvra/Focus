package fan.x0.focus.feature.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
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
internal fun DeviceDisplayCard(state: DeviceUiState, onAction: (DeviceAction) -> Unit) {
    DeviceCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.device_display_title),
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.device_display_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FocusStatusPill(
                    text = stringResource(
                        when {
                            state.displayStarting -> R.string.device_display_starting
                            state.displayRunning -> R.string.device_display_running
                            else -> R.string.device_display_off
                        },
                    ),
                    tone = when {
                        state.displayRunning && state.displayTrusted -> StatusTone.Success
                        state.displayRunning -> StatusTone.Warning
                        else -> StatusTone.Neutral
                    },
                )
            }
            if (state.displayRunning) {
                DeviceStatusRow(
                    label = stringResource(R.string.device_display_backend),
                    value = state.displayBackend.ifBlank { "-" },
                    tone = StatusTone.Neutral,
                )
                DeviceStatusRow(
                    label = stringResource(R.string.device_display_trusted),
                    value = stringResource(
                        if (state.displayTrusted) {
                            R.string.device_display_trusted_on
                        } else {
                            R.string.device_display_trusted_off
                        },
                    ),
                    tone = if (state.displayTrusted) StatusTone.Success else StatusTone.Warning,
                )
                Text(
                    text = stringResource(
                        R.string.device_display_size,
                        state.displayWidth,
                        state.displayHeight,
                        state.displayDensity,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FocusOutlinedButton(
                    text = stringResource(
                        if (state.displayPreviewing) {
                            R.string.device_display_preview_stop
                        } else {
                            R.string.device_display_preview
                        },
                    ),
                    onClick = { onAction(DeviceAction.ToggleDisplayPreview) },
                    modifier = Modifier.fillMaxWidth(),
                )
                FocusOutlinedButton(
                    text = stringResource(R.string.device_display_stop),
                    onClick = { onAction(DeviceAction.StopDisplay) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                FocusPrimaryButton(
                    text = stringResource(R.string.device_display_start),
                    onClick = { onAction(DeviceAction.StartDisplay) },
                    enabled = state.root || state.shizuku,
                    loading = state.displayStarting,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!state.root && !state.shizuku) {
                    Text(
                        text = stringResource(R.string.device_display_requires),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.displayError.isNotBlank()) {
                Text(
                    text = state.displayError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
