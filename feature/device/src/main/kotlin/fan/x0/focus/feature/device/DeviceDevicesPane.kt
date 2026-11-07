package fan.x0.focus.feature.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusEmptyState
import fan.x0.focus.core.designsystem.components.FocusOutlinedButton
import fan.x0.focus.core.designsystem.components.FocusPrimaryButton
import fan.x0.focus.core.designsystem.components.FocusStatusPill
import fan.x0.focus.core.designsystem.components.FocusTonalButton
import fan.x0.focus.core.designsystem.components.StatusTone

@Composable
fun DeviceDevicesPane(state: DeviceUiState, onAction: (DeviceAction) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            FocusPrimaryButton(
                text = stringResource(R.string.device_add),
                onClick = { onAction(DeviceAction.OpenPair) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.live && state.preview != null) {
            item(key = "live") {
                DevicePreview(
                    raw = state.preview,
                    title = stringResource(R.string.device_live),
                )
            }
        }
        if (state.endpoints.isEmpty()) {
            item {
                Box(modifier = Modifier.fillParentMaxWidth(), contentAlignment = Alignment.Center) {
                    FocusEmptyState(
                        icon = fan.x0.focus.core.designsystem.components.FocusIcons.Device,
                        title = stringResource(R.string.device_empty_title),
                        body = stringResource(R.string.device_empty_body),
                        actionLabel = stringResource(R.string.device_add),
                        onAction = { onAction(DeviceAction.OpenPair) },
                    )
                }
            }
        } else {
            items(state.endpoints, key = { it.id }) { endpoint ->
                DeviceCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = endpoint.name,
                                style = MaterialTheme.typography.titleSmallEmphasized,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (endpoint.id == state.activeEndpointId) {
                                FocusStatusPill(
                                    text = stringResource(R.string.device_active),
                                    tone = StatusTone.Active,
                                )
                            }
                        }
                        Text(
                            text = "${endpoint.host}:${endpoint.port}  ${endpoint.model} ${endpoint.version}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FocusTonalButton(
                                text = stringResource(R.string.device_use),
                                onClick = { onAction(DeviceAction.SelectDevice(endpoint.id)) },
                                enabled = endpoint.id != state.activeEndpointId,
                                modifier = Modifier.weight(1f),
                            )
                            FocusPrimaryButton(
                                text = stringResource(R.string.device_start),
                                onClick = { onAction(DeviceAction.StartRemote(endpoint.id)) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FocusOutlinedButton(
                                text = stringResource(R.string.device_stop),
                                onClick = { onAction(DeviceAction.StopRemote(endpoint.id)) },
                                modifier = Modifier.weight(1f),
                            )
                            FocusOutlinedButton(
                                text = stringResource(R.string.device_forget),
                                onClick = { onAction(DeviceAction.ForgetDevice(endpoint.id)) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        FocusOutlinedButton(
                            text = stringResource(
                                if (state.live && state.liveTarget == endpoint.id) {
                                    R.string.device_live_stop
                                } else {
                                    R.string.device_live
                                },
                            ),
                            onClick = { onAction(DeviceAction.ToggleLive(endpoint.id)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
