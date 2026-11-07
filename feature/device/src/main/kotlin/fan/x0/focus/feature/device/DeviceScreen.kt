package fan.x0.focus.feature.device

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fan.x0.focus.core.designsystem.components.FocusCompactTopBar
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusTabContent
import fan.x0.focus.core.designsystem.components.FocusTabSwitcher
import fan.x0.focus.core.designsystem.components.hapticTap
import fan.x0.focus.core.designsystem.error.HandleAppErrors
import fan.x0.focus.core.mvi.CollectEffect
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DeviceRoute(viewModel: DeviceViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val localNetworkPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 37 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_LOCAL_NETWORK) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            localNetworkPermission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
    }
    HandleAppErrors(viewModel.errors, snackbar)
    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            is DeviceEffect.Message -> {
                snackbar.showSnackbar(context.getString(effect.res, effect.arg.orEmpty()))
            }
            DeviceEffect.OpenAccessibility -> runCatching {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
    DeviceScreen(state = state, onAction = viewModel::onAction, snackbar = snackbar)
}

@Composable
fun DeviceScreen(
    state: DeviceUiState,
    onAction: (DeviceAction) -> Unit,
    snackbar: SnackbarHostState,
) {
    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            FocusCompactTopBar(
                title = stringResource(R.string.device_title),
                actions = {
                    IconButton(onClick = hapticTap { onAction(DeviceAction.Refresh) }) {
                        Icon(
                            imageVector = FocusIcons.Refresh,
                            contentDescription = stringResource(R.string.device_status),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            val tabs = listOf(
                DeviceTab.Local to stringResource(R.string.device_tab_local),
                DeviceTab.Controlled to stringResource(R.string.device_tab_controlled),
                DeviceTab.Devices to stringResource(R.string.device_tab_devices),
                DeviceTab.Triggers to stringResource(R.string.device_tab_triggers),
            )
            val selected = tabs.indexOfFirst { it.first == state.tab }.coerceAtLeast(0)
            FocusTabSwitcher(
                labels = tabs.map { it.second },
                selectedIndex = selected,
                onSelect = { index -> onAction(DeviceAction.TabChanged(tabs[index].first)) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            FocusTabContent(selectedIndex = selected, modifier = Modifier.weight(1f)) { index ->
                when (tabs[index].first) {
                    DeviceTab.Local -> DeviceLocalPane(state = state, onAction = onAction)
                    DeviceTab.Controlled -> DeviceControlledPane(state = state, onAction = onAction)
                    DeviceTab.Devices -> DeviceDevicesPane(state = state, onAction = onAction)
                    DeviceTab.Triggers -> DeviceTriggersPane(state = state, onAction = onAction)
                }
            }
        }
    }
    if (state.pairOpen) {
        PairPhoneDialog(state = state, onAction = onAction)
    }
}

@Composable
private fun PairPhoneDialog(state: DeviceUiState, onAction: (DeviceAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(DeviceAction.ClosePair) },
        title = { Text(stringResource(R.string.device_pair_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = state.host,
                    onValueChange = { onAction(DeviceAction.HostChanged(it)) },
                    label = { Text(stringResource(R.string.device_host)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = state.portText,
                    onValueChange = { onAction(DeviceAction.PortChanged(it)) },
                    label = { Text(stringResource(R.string.device_port)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = state.code,
                    onValueChange = { onAction(DeviceAction.CodeChanged(it)) },
                    label = { Text(stringResource(R.string.device_code)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = hapticTap { onAction(DeviceAction.Pair) },
                enabled = state.host.isNotBlank() && state.code.isNotBlank() && !state.pairing,
            ) {
                Text(
                    if (state.pairing) {
                        stringResource(R.string.device_pairing)
                    } else {
                        stringResource(R.string.device_pair)
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = hapticTap { onAction(DeviceAction.ClosePair) }) {
                Text(stringResource(R.string.device_cancel))
            }
        },
    )
}
