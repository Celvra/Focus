package fan.x0.focus.feature.assistant.settings

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fan.x0.focus.core.designsystem.components.FocusCardGroup
import fan.x0.focus.core.designsystem.components.FocusCardGroupIcon
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusLoadingBlob
import fan.x0.focus.core.designsystem.components.FocusOutlinedButton
import fan.x0.focus.core.designsystem.components.FocusPrimaryButton
import fan.x0.focus.core.designsystem.components.FocusReveal
import fan.x0.focus.core.designsystem.error.message
import fan.x0.focus.data.assistant.WorkspaceIndexStatus
import fan.x0.focus.feature.assistant.R
import fan.x0.focus.feature.assistant.WorkspaceIndexAction
import fan.x0.focus.feature.assistant.WorkspaceIndexUiState
import fan.x0.focus.feature.assistant.WorkspaceIndexViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun SettingsWorkspaceIndexRoute(
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
    viewModel: WorkspaceIndexViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsWorkspaceIndexPage(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        snackbar = snackbar,
    )
}

@Composable
internal fun SettingsWorkspaceIndexPage(
    state: WorkspaceIndexUiState,
    onAction: (WorkspaceIndexAction) -> Unit,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_tools_index_title),
        onBack = onBack,
        snackbar = snackbar,
    ) {
        item(key = "intro") {
            FocusReveal {
                Text(
                    text = stringResource(R.string.settings_tools_index_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        item(key = "status") {
            FocusReveal(index = 1) {
                if (state.loading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) {
                        FocusLoadingBlob(size = 28.dp)
                    }
                } else {
                    FocusCardGroup(title = stringResource(R.string.settings_tools_index_title)) {
                        val status = state.status
                        if (status == null || status.files == 0) {
                            item(
                                headlineContent = { Text(stringResource(R.string.settings_tools_index_empty)) },
                                leadingContent = {
                                    FocusCardGroupIcon(
                                        icon = FocusIcons.Folder,
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                },
                            )
                        } else {
                            item(
                                headlineContent = { Text(status.root) },
                                supportingContent = {
                                    Text(
                                        text = stringResource(
                                            R.string.settings_tools_index_status,
                                            status.files,
                                            status.updatedLabel(),
                                        ),
                                    )
                                },
                                leadingContent = {
                                    FocusCardGroupIcon(
                                        icon = FocusIcons.Folder,
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
        item(key = "actions") {
            FocusReveal(index = 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FocusOutlinedButton(
                        text = stringResource(R.string.settings_tools_index_clear),
                        onClick = { onAction(WorkspaceIndexAction.Clear) },
                        enabled = state.status?.files?.let { it > 0 } == true,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                    )
                    FocusPrimaryButton(
                        text = stringResource(R.string.settings_tools_index_build),
                        onClick = { onAction(WorkspaceIndexAction.Build) },
                        enabled = !state.building,
                        loading = state.building,
                        icon = FocusIcons.Refresh,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                    )
                }
            }
        }
        state.error?.let { error ->
            item(key = "error") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_tools_index_failed),
                            style = MaterialTheme.typography.titleSmallEmphasized,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = error.message(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }
    }
}

private fun WorkspaceIndexStatus.updatedLabel(): String {
    if (updatedAt <= 0) return ""
    return DateUtils.getRelativeTimeSpanString(
        updatedAt,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
}
