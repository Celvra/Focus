package fan.x0.focus.feature.assistant.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fan.x0.focus.core.designsystem.components.FocusCardGroup
import fan.x0.focus.core.designsystem.components.FocusCardGroupIcon
import fan.x0.focus.core.designsystem.components.FocusCardGroupScope
import fan.x0.focus.core.designsystem.components.FocusChip
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusOutlinedButton
import fan.x0.focus.core.designsystem.components.FocusPrimaryButton
import fan.x0.focus.core.designsystem.components.FocusReveal
import fan.x0.focus.core.designsystem.components.FocusStatusPill
import fan.x0.focus.core.designsystem.components.FocusTextButton
import fan.x0.focus.core.designsystem.components.StatusTone
import fan.x0.focus.core.designsystem.error.message
import fan.x0.focus.core.mvi.CollectEffect
import fan.x0.focus.data.assistant.McpServerConfig
import fan.x0.focus.data.assistant.McpTestReport
import fan.x0.focus.data.assistant.McpTransport
import fan.x0.focus.feature.assistant.McpAction
import fan.x0.focus.feature.assistant.McpDraft
import fan.x0.focus.feature.assistant.McpEffect
import fan.x0.focus.feature.assistant.McpSettingsViewModel
import fan.x0.focus.feature.assistant.McpUiState
import fan.x0.focus.feature.assistant.R
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun SettingsMcpRoute(
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
    viewModel: McpSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val saved = stringResource(R.string.settings_mcp_saved)
    val deleted = stringResource(R.string.settings_mcp_deleted)
    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            McpEffect.Saved -> snackbar.showSnackbar(saved)
            McpEffect.Deleted -> snackbar.showSnackbar(deleted)
        }
    }
    SettingsMcpPage(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        snackbar = snackbar,
    )
}

@Composable
internal fun SettingsMcpPage(
    state: McpUiState,
    onAction: (McpAction) -> Unit,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_mcp_title),
        onBack = onBack,
        snackbar = snackbar,
    ) {
        item(key = "intro") {
            FocusReveal {
                Text(
                    text = stringResource(R.string.settings_mcp_guide),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        item(key = "servers") {
            FocusReveal(index = 1) {
                FocusCardGroup(title = stringResource(R.string.settings_mcp_title)) {
                    state.servers.forEach { server ->
                        ServerRow(
                            server = server,
                            onOpen = { onAction(McpAction.Edit(server.id)) },
                            onEnabled = { onAction(McpAction.SetEnabled(server.id, it)) },
                        )
                    }
                    item(
                        headlineContent = { Text(stringResource(R.string.settings_mcp_add)) },
                        leadingContent = {
                            FocusCardGroupIcon(
                                icon = FocusIcons.Add,
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        },
                        onClick = { onAction(McpAction.Add) },
                    )
                }
            }
        }
        if (state.servers.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.settings_mcp_no_servers),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
    state.draft?.let { draft ->
        McpEditorSheet(
            draft = draft,
            state = state,
            onAction = onAction,
        )
    }
}

private fun FocusCardGroupScope.ServerRow(
    server: McpServerConfig,
    onOpen: () -> Unit,
    onEnabled: (Boolean) -> Unit,
) {
    item(
        headlineContent = { Text(server.name) },
        supportingContent = {
            Text(
                text = if (server.transport == McpTransport.Stdio) {
                    listOf(server.command, server.args.joinToString(" "))
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                } else {
                    server.url
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            FocusCardGroupIcon(
                icon = FocusIcons.Tool,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        },
        trailingContent = {
            Switch(
                checked = server.enabled,
                onCheckedChange = onEnabled,
            )
        },
        onClick = onOpen,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun McpEditorSheet(
    draft: McpDraft,
    state: McpUiState,
    onAction: (McpAction) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val existing = state.servers.any { it.id == draft.id }
    ModalBottomSheet(
        onDismissRequest = { onAction(McpAction.Close) },
        sheetState = sheetState,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        if (existing) R.string.settings_mcp_edit else R.string.settings_mcp_add,
                    ),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onAction(McpAction.Close) }) {
                    Icon(
                        imageVector = FocusIcons.Close,
                        contentDescription = stringResource(R.string.settings_provider_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedTextField(
                value = draft.name,
                onValueChange = { onAction(McpAction.NameChanged(it)) },
                label = { Text(stringResource(R.string.settings_mcp_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.settings_mcp_transport),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FocusChip(
                        label = stringResource(R.string.settings_mcp_transport_http),
                        selected = draft.transport == McpTransport.StreamableHttp,
                        onClick = { onAction(McpAction.TransportChanged(McpTransport.StreamableHttp)) },
                    )
                    FocusChip(
                        label = stringResource(R.string.settings_mcp_transport_sse),
                        selected = draft.transport == McpTransport.Sse,
                        onClick = { onAction(McpAction.TransportChanged(McpTransport.Sse)) },
                    )
                    FocusChip(
                        label = stringResource(R.string.settings_mcp_transport_stdio),
                        selected = draft.transport == McpTransport.Stdio,
                        onClick = { onAction(McpAction.TransportChanged(McpTransport.Stdio)) },
                    )
                }
            }
            if (draft.transport == McpTransport.Stdio) {
                OutlinedTextField(
                    value = draft.command,
                    onValueChange = { onAction(McpAction.CommandChanged(it)) },
                    label = { Text(stringResource(R.string.settings_mcp_command)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.args,
                    onValueChange = { onAction(McpAction.ArgsChanged(it)) },
                    label = { Text(stringResource(R.string.settings_mcp_args)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.env,
                    onValueChange = { onAction(McpAction.EnvChanged(it)) },
                    label = { Text(stringResource(R.string.settings_mcp_env)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OutlinedTextField(
                    value = draft.url,
                    onValueChange = { onAction(McpAction.UrlChanged(it)) },
                    label = { Text(stringResource(R.string.settings_mcp_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                text = stringResource(R.string.settings_mcp_headers),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            draft.headers.forEachIndexed { index, header ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = header.name,
                            onValueChange = { onAction(McpAction.HeaderNameChanged(index, it)) },
                            label = { Text(stringResource(R.string.settings_mcp_header_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = header.value,
                            onValueChange = { onAction(McpAction.HeaderValueChanged(index, it)) },
                            label = { Text(stringResource(R.string.settings_mcp_header_value)) },
                            singleLine = true,
                            visualTransformation = if (header.secret) {
                                PasswordVisualTransformation()
                            } else {
                                VisualTransformation.None
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.settings_mcp_header_secret),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = header.secret,
                                onCheckedChange = { onAction(McpAction.HeaderSecretChanged(index, it)) },
                            )
                            IconButton(onClick = { onAction(McpAction.RemoveHeader(index)) }) {
                                Icon(
                                    imageVector = FocusIcons.Delete,
                                    contentDescription = stringResource(R.string.settings_mcp_remove_header),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            FocusTextButton(
                text = stringResource(R.string.settings_mcp_add_header),
                onClick = { onAction(McpAction.AddHeader) },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.settings_mcp_enabled),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = draft.enabled,
                    onCheckedChange = { onAction(McpAction.EnabledChanged(it)) },
                )
            }
            state.error?.let { error ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = error.message(),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            state.report?.let { report ->
                TestReport(report = report)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FocusOutlinedButton(
                    text = stringResource(
                        if (state.testing) R.string.settings_mcp_testing else R.string.settings_mcp_test,
                    ),
                    onClick = { onAction(McpAction.Test) },
                    enabled = !state.testing,
                    loading = state.testing,
                    icon = FocusIcons.Bolt,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                )
                FocusPrimaryButton(
                    text = stringResource(R.string.settings_mcp_save),
                    onClick = { onAction(McpAction.Save) },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                )
            }
            if (existing) {
                FocusTextButton(
                    text = stringResource(R.string.settings_mcp_delete),
                    onClick = { onAction(McpAction.Delete) },
                )
            }
        }
    }
}

@Composable
private fun TestReport(report: McpTestReport) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FocusStatusPill(
                text = stringResource(
                    R.string.settings_mcp_status_ok,
                    report.info.name,
                    report.info.version,
                ),
                tone = StatusTone.Success,
            )
            Text(
                text = stringResource(R.string.settings_mcp_tools_count, report.tools.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (report.tools.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.settings_mcp_tools),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                )
                report.tools.take(8).forEach { tool ->
                    Text(
                        text = tool.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
