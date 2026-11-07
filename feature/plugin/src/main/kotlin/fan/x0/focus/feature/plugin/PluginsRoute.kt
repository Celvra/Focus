package fan.x0.focus.feature.plugin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fan.x0.focus.core.mvi.CollectEffect
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsPluginsRoute(
    onBack: () -> Unit,
    onOpenPluginPage: (String, String) -> Unit,
    snackbar: SnackbarHostState,
    viewModel: PluginViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onAction(PluginAction.InstallUri(it.toString())) }
    }
    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            is PluginEffect.Message -> snackbar.showSnackbar(effect.text)
        }
    }
    var uninstallId by remember { mutableStateOf<String?>(null) }
    val selected = state.selected
    when {
        state.logsOpen -> PluginLogsPage(
            pluginId = state.logsPluginId,
            text = state.logs,
            onBack = { viewModel.onAction(PluginAction.HideLogs) },
            onClear = { viewModel.onAction(PluginAction.ClearLogs(state.logsPluginId)) },
        )
        state.marketOpen -> PluginMarketPage(
            state = state,
            onBack = { viewModel.onAction(PluginAction.CloseMarket) },
            onRefresh = { viewModel.onAction(PluginAction.RefreshMarket) },
            onQuery = { viewModel.onAction(PluginAction.MarketQueryChanged(it)) },
            onInstall = { viewModel.onAction(PluginAction.InstallMarket(it)) },
        )
        selected != null -> PluginDetailPage(
            status = selected,
            busy = state.busy,
            onBack = { viewModel.onAction(PluginAction.CloseDetail) },
            onEnabled = { viewModel.onAction(PluginAction.SetEnabled(selected.manifest.id, it)) },
            onCapability = { capability, enabled ->
                viewModel.onAction(PluginAction.SetCapability(selected.manifest.id, capability, enabled))
            },
            onReload = { viewModel.onAction(PluginAction.Reload(selected.manifest.id)) },
            onLogs = { viewModel.onAction(PluginAction.ShowLogs(selected.manifest.id)) },
            onUninstall = { uninstallId = selected.manifest.id },
            onOpenPage = { onOpenPluginPage(selected.manifest.id, it) },
        )
        else -> PluginListPage(
            state = state,
            onBack = onBack,
            onSelect = { viewModel.onAction(PluginAction.Select(it)) },
            onToggle = { id, enabled -> viewModel.onAction(PluginAction.SetEnabled(id, enabled)) },
            onInstallFile = {
                picker.launch(
                    arrayOf(
                        "application/vnd.android.package-archive",
                        "application/zip",
                        "application/octet-stream",
                        "*/*",
                    ),
                )
            },
            onInstallUrl = { viewModel.onAction(PluginAction.OpenImport) },
            onMarket = { viewModel.onAction(PluginAction.OpenMarket) },
            onRegistry = { viewModel.onAction(PluginAction.OpenRegistry) },
        )
    }
    if (state.importOpen) {
        AlertDialog(
            onDismissRequest = { viewModel.onAction(PluginAction.CloseImport) },
            title = { Text(stringResource(R.string.plugins_install_url)) },
            text = {
                OutlinedTextField(
                    value = state.importUrl,
                    onValueChange = { viewModel.onAction(PluginAction.ImportUrlChanged(it)) },
                    placeholder = { Text(stringResource(R.string.plugins_url_hint)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onAction(PluginAction.InstallUrl) }) {
                    Text(stringResource(R.string.plugins_install))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onAction(PluginAction.CloseImport) }) {
                    Text(stringResource(R.string.plugins_cancel))
                }
            },
        )
    }
    if (state.registryOpen) {
        AlertDialog(
            onDismissRequest = { viewModel.onAction(PluginAction.CloseRegistry) },
            title = { Text(stringResource(R.string.plugins_registry)) },
            text = {
                OutlinedTextField(
                    value = state.registryDraft,
                    onValueChange = { viewModel.onAction(PluginAction.RegistryChanged(it)) },
                    placeholder = { Text(stringResource(R.string.plugins_registry_hint)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onAction(PluginAction.SetRegistry(state.registryDraft.trim()))
                    },
                ) {
                    Text(stringResource(R.string.plugins_registry_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onAction(PluginAction.CloseRegistry) }) {
                    Text(stringResource(R.string.plugins_cancel))
                }
            },
        )
    }
    if (uninstallId != null) {
        AlertDialog(
            onDismissRequest = { uninstallId = null },
            title = { Text(stringResource(R.string.plugins_uninstall)) },
            text = { Text(stringResource(R.string.plugins_uninstall_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = uninstallId
                        uninstallId = null
                        if (id != null) viewModel.onAction(PluginAction.Uninstall(id))
                    },
                ) {
                    Text(stringResource(R.string.plugins_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { uninstallId = null }) {
                    Text(stringResource(R.string.plugins_cancel))
                }
            },
        )
    }
}
