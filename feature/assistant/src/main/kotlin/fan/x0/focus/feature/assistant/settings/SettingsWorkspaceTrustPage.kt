package fan.x0.focus.feature.assistant.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fan.x0.focus.core.designsystem.components.FocusCardGroup
import fan.x0.focus.core.designsystem.components.FocusCardGroupIcon
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusLoadingBlob
import fan.x0.focus.core.designsystem.components.FocusReveal
import fan.x0.focus.core.designsystem.components.hapticToggle
import fan.x0.focus.core.designsystem.error.message
import fan.x0.focus.data.assistant.WorkspaceKind
import fan.x0.focus.data.assistant.WorkspaceTrustEntry
import fan.x0.focus.data.assistant.WorkspaceTrustState
import fan.x0.focus.feature.assistant.R
import fan.x0.focus.feature.assistant.WorkspaceTrustAction
import fan.x0.focus.feature.assistant.WorkspaceTrustUiState
import fan.x0.focus.feature.assistant.WorkspaceTrustViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun SettingsWorkspaceTrustRoute(
    onBack: () -> Unit,
    viewModel: WorkspaceTrustViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsWorkspaceTrustPage(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
    )
}

@Composable
internal fun SettingsWorkspaceTrustPage(
    state: WorkspaceTrustUiState,
    onAction: (WorkspaceTrustAction) -> Unit,
    onBack: () -> Unit,
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_page_trust),
        onBack = onBack,
    ) {
        item(key = "intro") {
            FocusReveal {
                Text(
                    text = stringResource(R.string.settings_trust_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        item(key = "workspaces") {
            FocusReveal(index = 1) {
                when {
                    state.loading -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        FocusLoadingBlob(size = 28.dp)
                    }
                    state.entries.isEmpty() -> Text(
                        text = stringResource(R.string.settings_trust_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    else -> FocusCardGroup {
                        state.entries.forEach { entry ->
                            item(
                                headlineContent = { Text(entry.label) },
                                supportingContent = {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(entry.subtitle())
                                        if (entry.files.isNotEmpty()) {
                                            Text(
                                                text = stringResource(
                                                    R.string.settings_trust_files,
                                                    entry.files.joinToString(", "),
                                                ),
                                            )
                                        }
                                    }
                                },
                                leadingContent = {
                                    FocusCardGroupIcon(
                                        icon = if (entry.kind == WorkspaceKind.Bridge) {
                                            FocusIcons.Terminal
                                        } else {
                                            FocusIcons.Folder
                                        },
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                },
                                trailingContent = {
                                    Switch(
                                        checked = entry.state == WorkspaceTrustState.Trusted,
                                        onCheckedChange = hapticToggle { trusted ->
                                            onAction(WorkspaceTrustAction.SetTrusted(entry.key, trusted))
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
        state.error?.let { error ->
            item(key = "error") {
                Text(
                    text = error.message(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun WorkspaceTrustEntry.subtitle(): String {
    val kind = when (kind) {
        WorkspaceKind.Bridge -> stringResource(R.string.settings_trust_kind_bridge)
        WorkspaceKind.Local -> stringResource(R.string.settings_trust_kind_local)
    }
    val state = when (state) {
        WorkspaceTrustState.Unknown -> stringResource(R.string.settings_trust_unknown)
        WorkspaceTrustState.Trusted -> stringResource(R.string.settings_trust_trusted)
        WorkspaceTrustState.Declined -> stringResource(R.string.settings_trust_declined)
    }
    val current = if (current) {
        stringResource(R.string.settings_trust_current)
    } else {
        stringResource(R.string.settings_trust_saved)
    }
    return "$kind · $current · $state"
}
