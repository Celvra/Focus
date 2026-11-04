package fan.x0.focus.feature.assistant.settings

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fan.x0.focus.core.designsystem.components.FocusCardGroup
import fan.x0.focus.core.designsystem.components.FocusCardGroupIcon
import fan.x0.focus.core.designsystem.components.FocusCardGroupScope
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusLoadingBlob
import fan.x0.focus.core.designsystem.components.FocusLoadingBlock
import fan.x0.focus.core.designsystem.components.FocusReveal
import fan.x0.focus.core.designsystem.components.hapticTap
import fan.x0.focus.core.designsystem.components.hapticToggle
import fan.x0.focus.core.mvi.CollectEffect
import fan.x0.focus.data.assistant.SkillEntry
import fan.x0.focus.data.assistant.SkillSource
import fan.x0.focus.feature.assistant.R
import fan.x0.focus.feature.assistant.SkillsAction
import fan.x0.focus.feature.assistant.SkillsEffect
import fan.x0.focus.feature.assistant.SkillsSettingsViewModel
import fan.x0.focus.feature.assistant.SkillsUiState
import org.koin.compose.viewmodel.koinViewModel
import java.io.File

@Composable
internal fun SettingsSkillsRoute(
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
    viewModel: SkillsSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val saved = stringResource(R.string.settings_skills_saved)
    val deleted = stringResource(R.string.settings_skills_deleted)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onAction(SkillsAction.ImportFile(it.toString())) }
    }
    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            SkillsEffect.Saved -> snackbar.showSnackbar(saved)
            SkillsEffect.Deleted -> snackbar.showSnackbar(deleted)
            is SkillsEffect.Imported -> snackbar.showSnackbar(
                if (effect.count == 0) {
                    context.getString(R.string.settings_skills_import_empty)
                } else {
                    context.getString(R.string.settings_skills_imported_count, effect.count)
                },
            )
            SkillsEffect.NothingFound -> snackbar.showSnackbar(
                context.getString(R.string.settings_skills_nothing_found),
            )
            is SkillsEffect.ExportReady -> if (!shareArchive(context, effect.path)) {
                snackbar.showSnackbar(context.getString(R.string.settings_skills_export_failed))
            }
            is SkillsEffect.Failed -> snackbar.showSnackbar(effect.message)
        }
    }
    SettingsSkillsPage(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        snackbar = snackbar,
        onPickFile = {
            picker.launch(
                arrayOf(
                    "application/zip",
                    "application/x-zip-compressed",
                    "text/markdown",
                    "text/x-markdown",
                    "text/plain",
                    "application/octet-stream",
                ),
            )
        },
    )
}

@Composable
internal fun SettingsSkillsPage(
    state: SkillsUiState,
    onAction: (SkillsAction) -> Unit,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
    onPickFile: () -> Unit,
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_page_skills),
        onBack = onBack,
        snackbar = snackbar,
        actions = {
            if (state.refreshing || state.busy) {
                FocusLoadingBlob(
                    size = 20.dp,
                    modifier = Modifier.padding(12.dp),
                )
            } else {
                IconButton(onClick = hapticTap { onAction(SkillsAction.Refresh) }) {
                    Icon(
                        imageVector = FocusIcons.Refresh,
                        contentDescription = stringResource(R.string.settings_skills_refresh),
                    )
                }
            }
        },
    ) {
        item(key = "intro") {
            FocusReveal {
                Text(
                    text = stringResource(R.string.settings_skills_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        if (state.loading) {
            item(key = "loading") {
                FocusLoadingBlock()
            }
        } else {
            item(key = "app") {
                FocusReveal(index = 1) {
                    AppSkillsGroup(
                        skills = state.skills.filter { it.source == SkillSource.App },
                        onAction = onAction,
                    )
                }
            }
            item(key = "workspace") {
                FocusReveal(index = 2) {
                    WorkspaceSkillsGroup(
                        skills = state.skills.filter { it.source == SkillSource.Workspace },
                        onAction = onAction,
                    )
                }
            }
        }
        item(key = "transfer") {
            FocusReveal(index = 3) {
                TransferGroup(
                    busy = state.busy,
                    onPickFile = onPickFile,
                    onAction = onAction,
                )
            }
        }
    }
    state.draft?.let { draft ->
        SkillEditorSheet(
            draft = draft,
            busy = state.busy,
            invalid = state.draftError,
            onAction = onAction,
        )
        if (state.deleteConfirm) {
            SkillDeleteDialog(
                name = draft.name,
                onDismiss = { onAction(SkillsAction.CancelDelete) },
                onConfirm = { onAction(SkillsAction.ConfirmDelete) },
            )
        }
    }
    if (state.linkOpen) {
        SkillLinkDialog(
            link = state.link,
            busy = state.busy,
            fetching = state.fetching,
            fetchProgress = state.fetchProgress,
            onAction = onAction,
        )
    }
    if (state.importOffers.isNotEmpty()) {
        SkillImportSheet(
            offers = state.importOffers,
            selected = state.importSelected,
            busy = state.busy,
            onAction = onAction,
        )
    }
}

@Composable
private fun AppSkillsGroup(
    skills: List<SkillEntry>,
    onAction: (SkillsAction) -> Unit,
) {
    FocusCardGroup(title = stringResource(R.string.settings_skills_app)) {
        skills.forEach { skill ->
            SkillRow(
                skill = skill,
                icon = FocusIcons.Assistant,
                onOpen = { onAction(SkillsAction.Edit(skill.id)) },
                onEnabled = { onAction(SkillsAction.SetEnabled(skill.id, it)) },
            )
        }
        item(
            headlineContent = { Text(stringResource(R.string.settings_skills_add)) },
            supportingContent = if (skills.isEmpty()) {
                { Text(stringResource(R.string.settings_skills_app_empty)) }
            } else {
                null
            },
            leadingContent = {
                FocusCardGroupIcon(
                    icon = FocusIcons.Add,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            },
            onClick = { onAction(SkillsAction.New) },
        )
    }
}

@Composable
private fun WorkspaceSkillsGroup(
    skills: List<SkillEntry>,
    onAction: (SkillsAction) -> Unit,
) {
    FocusCardGroup(title = stringResource(R.string.settings_skills_workspace)) {
        if (skills.isEmpty()) {
            item(
                headlineContent = { Text(stringResource(R.string.settings_skills_workspace_empty)) },
                leadingContent = {
                    FocusCardGroupIcon(
                        icon = FocusIcons.Folder,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                },
            )
        }
        skills.forEach { skill ->
            SkillRow(
                skill = skill,
                icon = FocusIcons.Folder,
                onOpen = { onAction(SkillsAction.Edit(skill.id)) },
                onEnabled = { onAction(SkillsAction.SetEnabled(skill.id, it)) },
            )
        }
    }
}

private fun FocusCardGroupScope.SkillRow(
    skill: SkillEntry,
    icon: ImageVector,
    onOpen: () -> Unit,
    onEnabled: (Boolean) -> Unit,
) {
    item(
        headlineContent = { Text(skill.name) },
        supportingContent = {
            Text(
                text = skill.description,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            FocusCardGroupIcon(
                icon = icon,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        },
        trailingContent = {
            Switch(
                checked = skill.enabled,
                onCheckedChange = hapticToggle(onEnabled),
            )
        },
        onClick = onOpen,
    )
}

@Composable
private fun TransferGroup(
    busy: Boolean,
    onPickFile: () -> Unit,
    onAction: (SkillsAction) -> Unit,
) {
    FocusCardGroup(title = stringResource(R.string.settings_skills_transfer)) {
        item(
            headlineContent = { Text(stringResource(R.string.settings_skills_import_file)) },
            supportingContent = { Text(stringResource(R.string.settings_skills_import_file_body)) },
            leadingContent = {
                FocusCardGroupIcon(
                    icon = FocusIcons.UploadFile,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            },
            onClick = { if (!busy) onPickFile() },
        )
        item(
            headlineContent = { Text(stringResource(R.string.settings_skills_import_link)) },
            supportingContent = { Text(stringResource(R.string.settings_skills_import_link_body)) },
            leadingContent = {
                FocusCardGroupIcon(
                    icon = FocusIcons.Link,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            },
            onClick = { onAction(SkillsAction.OpenLink) },
        )
        item(
            headlineContent = { Text(stringResource(R.string.settings_skills_export)) },
            supportingContent = { Text(stringResource(R.string.settings_skills_export_body)) },
            leadingContent = {
                FocusCardGroupIcon(
                    icon = FocusIcons.Share,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            },
            onClick = { if (!busy) onAction(SkillsAction.Export) },
        )
    }
}

@Composable
private fun SkillLinkDialog(
    link: String,
    busy: Boolean,
    fetching: Boolean,
    fetchProgress: Float?,
    onAction: (SkillsAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onAction(SkillsAction.CloseLink) },
        title = { Text(stringResource(R.string.settings_skills_link_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.settings_skills_link_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = link,
                    onValueChange = { onAction(SkillsAction.LinkChanged(it)) },
                    placeholder = { Text(stringResource(R.string.settings_skills_link_hint)) },
                    singleLine = true,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (fetching) {
                    if (fetchProgress != null) {
                        LinearProgressIndicator(
                            progress = { fetchProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape),
                            strokeCap = StrokeCap.Round,
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape),
                            strokeCap = StrokeCap.Round,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = hapticTap { onAction(SkillsAction.ImportLink) },
                enabled = link.isNotBlank() && !busy,
            ) {
                Text(stringResource(R.string.settings_skills_link_action))
            }
        },
        dismissButton = {
            TextButton(onClick = hapticTap { onAction(SkillsAction.CloseLink) }) {
                Text(stringResource(R.string.assistant_cancel))
            }
        },
    )
}

@Composable
private fun SkillDeleteDialog(
    name: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_skills_delete_title)) },
        text = { Text(stringResource(R.string.settings_skills_delete_body, name)) },
        confirmButton = {
            TextButton(onClick = hapticTap(onConfirm)) {
                Text(stringResource(R.string.settings_skills_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = hapticTap(onDismiss)) {
                Text(stringResource(R.string.assistant_cancel))
            }
        },
    )
}

private fun shareArchive(context: Context, path: String): Boolean {
    val file = File(path)
    if (!file.isFile) return false
    return runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }.isSuccess
}
