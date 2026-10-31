package fan.x0.focus.feature.bridge

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fan.x0.focus.core.designsystem.components.FocusCompactTopBar
import fan.x0.focus.core.designsystem.components.FocusEmptyState
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusLoadingBlock
import fan.x0.focus.core.designsystem.components.FocusOutlinedButton
import fan.x0.focus.core.designsystem.components.FocusPrimaryButton
import fan.x0.focus.core.designsystem.components.FocusStatusPill
import fan.x0.focus.core.designsystem.components.FocusTabContent
import fan.x0.focus.core.designsystem.components.FocusTabSwitcher
import fan.x0.focus.core.designsystem.components.FocusTextButton
import fan.x0.focus.core.designsystem.components.FocusTonalButton
import fan.x0.focus.core.designsystem.components.StatusTone
import fan.x0.focus.core.designsystem.error.HandleAppErrors
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun BridgeRoute(viewModel: BridgeViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    HandleAppErrors(viewModel.errors, snackbar)
    BridgeScreen(
        state = state,
        onAction = viewModel::onAction,
        snackbar = snackbar,
    )
}

@Composable
fun BridgeScreen(
    state: BridgeUiState,
    onAction: (BridgeAction) -> Unit,
    snackbar: SnackbarHostState,
) {
    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { BridgeTopBar(state = state, onAction = onAction) },
    ) { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            val sections = listOf(
                BridgeTab.Devices to stringResource(R.string.bridge_tab_devices),
                BridgeTab.Terminal to stringResource(R.string.bridge_tab_terminal),
                BridgeTab.Background to stringResource(R.string.bridge_tab_background),
            )
            val selected = sections.indexOfFirst { it.first == state.tab }.coerceAtLeast(0)
            FocusTabSwitcher(
                labels = sections.map { it.second },
                selectedIndex = selected,
                onSelect = { index -> onAction(BridgeAction.TabChanged(sections[index].first)) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            FocusTabContent(
                selectedIndex = selected,
                modifier = Modifier.weight(1f),
            ) { index ->
                when (sections[index].first) {
                    BridgeTab.Devices -> DevicesPane(state = state, onAction = onAction)
                    BridgeTab.Terminal -> TerminalPane(state = state, onAction = onAction)
                    BridgeTab.Background -> BackgroundPane(state = state, onAction = onAction)
                }
            }
        }
    }
    if (state.addOpen) {
        PairDialog(state = state, onAction = onAction)
    }
    state.deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { onAction(BridgeAction.CancelForget) },
            title = { Text(stringResource(R.string.bridge_forget_title)) },
            text = { Text(stringResource(R.string.bridge_forget_body, target.name)) },
            confirmButton = {
                TextButton(onClick = { onAction(BridgeAction.ConfirmForget) }) {
                    Text(stringResource(R.string.bridge_forget))
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(BridgeAction.CancelForget) }) {
                    Text(stringResource(R.string.bridge_cancel))
                }
            },
        )
    }
}

@Composable
private fun BridgeTopBar(state: BridgeUiState, onAction: (BridgeAction) -> Unit) {
    FocusCompactTopBar(
        title = stringResource(R.string.bridge_title),
        actions = {
            IconButton(onClick = { onAction(BridgeAction.Scan) }, enabled = !state.scanning) {
                Icon(
                    FocusIcons.Search,
                    contentDescription = stringResource(R.string.bridge_scan),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onAction(BridgeAction.OpenAdd) }) {
                Icon(
                    FocusIcons.Add,
                    contentDescription = stringResource(R.string.bridge_add_device),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun DevicesPane(state: BridgeUiState, onAction: (BridgeAction) -> Unit) {
    if (state.loading) {
        Box(modifier = Modifier.fillMaxSize()) { FocusLoadingBlock() }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.discovered.isNotEmpty()) {
            item(key = "discovered") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.bridge_discovered_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.discovered.forEach { found ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = found.name.ifBlank { found.host },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${found.host}:${found.port}  ${found.os} ${found.arch}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            FocusTonalButton(
                                text = stringResource(R.string.bridge_pair),
                                onClick = { onAction(BridgeAction.UseDiscovered(found.host, found.port)) },
                            )
                        }
                    }
                }
            }
        }
        if (state.devices.isEmpty()) {
            item(key = "empty") {
                Box(
                    modifier = Modifier.fillParentMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    FocusEmptyState(
                        icon = FocusIcons.Provider,
                        title = stringResource(R.string.bridge_empty_title),
                        body = stringResource(R.string.bridge_empty_body),
                        actionLabel = stringResource(R.string.bridge_add_device),
                        onAction = { onAction(BridgeAction.OpenAdd) },
                    )
                }
            }
        } else {
            items(state.devices, key = { it.id }) { device ->
                DeviceCard(state = state, device = device, onAction = onAction)
            }
        }
    }
}

@Composable
private fun DeviceCard(
    state: BridgeUiState,
    device: BridgeDeviceUi,
    onAction: (BridgeAction) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = FocusShapeTokens.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (device.active) {
                    FocusStatusPill(
                        text = stringResource(R.string.bridge_active),
                        tone = StatusTone.Active,
                    )
                }
                if (state.liveDevices.contains(device.id)) {
                    FocusStatusPill(
                        text = stringResource(R.string.bridge_device_live),
                        tone = StatusTone.Success,
                    )
                }
                Text(
                    text = "${device.host}:${device.port}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val meta = listOf(device.os, device.arch, device.version)
                .filter { it.isNotBlank() }
                .joinToString(" ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (device.active) {
                WorkspaceEditor(state = state, onAction = onAction)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FocusTonalButton(
                    text = stringResource(R.string.bridge_use),
                    onClick = { onAction(BridgeAction.SelectDevice(device.id)) },
                    enabled = !device.active,
                    modifier = Modifier.weight(1f),
                )
                FocusOutlinedButton(
                    text = stringResource(R.string.bridge_forget),
                    onClick = { onAction(BridgeAction.AskForgetDevice(device.id)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun WorkspaceEditor(state: BridgeUiState, onAction: (BridgeAction) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.bridge_workspace_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.bridge_workspace_body),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.roots.isEmpty()) {
            Text(
                text = stringResource(R.string.bridge_workspace_empty),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.roots.forEach { root ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = root,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onAction(BridgeAction.RemoveRoot(root)) }) {
                        Icon(
                            FocusIcons.Close,
                            contentDescription = stringResource(R.string.bridge_forget),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.rootDraft,
                onValueChange = { onAction(BridgeAction.RootDraftChanged(it)) },
                label = { Text(stringResource(R.string.bridge_workspace_path)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { onAction(BridgeAction.AddRoot) },
                enabled = state.rootDraft.isNotBlank(),
            ) {
                Icon(
                    FocusIcons.Add,
                    contentDescription = stringResource(R.string.bridge_workspace_add),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PairDialog(state: BridgeUiState, onAction: (BridgeAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(BridgeAction.DismissAdd) },
        title = { Text(stringResource(R.string.bridge_pair_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.bridge_pair_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = state.host,
                    onValueChange = { onAction(BridgeAction.HostChanged(it)) },
                    label = { Text(stringResource(R.string.bridge_host)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.port,
                    onValueChange = { onAction(BridgeAction.PortChanged(it)) },
                    label = { Text(stringResource(R.string.bridge_port)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.code,
                    onValueChange = { onAction(BridgeAction.CodeChanged(it)) },
                    label = { Text(stringResource(R.string.bridge_code)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            FocusPrimaryButton(
                text = stringResource(R.string.bridge_pair),
                onClick = { onAction(BridgeAction.SubmitPair) },
                enabled = state.host.isNotBlank() && state.port.isNotBlank() &&
                    state.code.isNotBlank() && !state.pairing,
                loading = state.pairing,
            )
        },
        dismissButton = {
            FocusTextButton(
                text = stringResource(R.string.bridge_cancel),
                onClick = { onAction(BridgeAction.DismissAdd) },
            )
        },
    )
}
