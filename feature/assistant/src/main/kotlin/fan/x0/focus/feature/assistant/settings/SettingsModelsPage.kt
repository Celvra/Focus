package fan.x0.focus.feature.assistant.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fan.x0.focus.core.designsystem.components.FocusCardGroup
import fan.x0.focus.core.designsystem.components.FocusCardGroupIcon
import fan.x0.focus.core.designsystem.components.FocusGroupedPickerSheet
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusPickerCapability
import fan.x0.focus.core.designsystem.components.FocusPickerDetail
import fan.x0.focus.core.designsystem.components.FocusPickerGroup
import fan.x0.focus.core.designsystem.components.FocusPickerItem
import fan.x0.focus.core.designsystem.components.FocusPickerSelection
import fan.x0.focus.core.designsystem.components.FocusReveal
import fan.x0.focus.core.designsystem.error.HandleAppErrors
import fan.x0.focus.core.mvi.CollectEffect
import fan.x0.focus.data.assistant.AgentRole
import fan.x0.focus.feature.assistant.R
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsModelsRoute(
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
    viewModel: RoleModelsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HandleAppErrors(viewModel.errors, snackbar)
    val saved = stringResource(R.string.settings_models_saved)
    val cleared = stringResource(R.string.settings_models_cleared)
    CollectEffect(viewModel.effect) { effect ->
        snackbar.showSnackbar(if (effect is RoleModelsEffect.Saved && effect.cleared) cleared else saved)
    }
    SettingsPageScaffold(
        title = stringResource(R.string.settings_models_title),
        onBack = onBack,
        snackbar = snackbar,
    ) {
        item(key = "intro") {
            FocusReveal {
                Text(
                    text = stringResource(R.string.settings_models_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
        }
        item(key = "router") {
            FocusReveal(index = 1) {
                RoleModelSection(
                    title = stringResource(R.string.settings_models_group_router),
                    rows = state.rows.filter { it.role == AgentRole.Router || it.role == AgentRole.Judge },
                    onOpen = { viewModel.onAction(RoleModelsAction.OpenPicker(it)) },
                    onClear = { viewModel.onAction(RoleModelsAction.Clear(it)) },
                )
            }
        }
        item(key = "workers") {
            FocusReveal(index = 2) {
                RoleModelSection(
                    title = stringResource(R.string.settings_models_group_workers),
                    rows = state.rows.filter {
                        it.role == AgentRole.WorkerLight ||
                            it.role == AgentRole.WorkerStandard ||
                            it.role == AgentRole.WorkerHeavy
                    },
                    onOpen = { viewModel.onAction(RoleModelsAction.OpenPicker(it)) },
                    onClear = { viewModel.onAction(RoleModelsAction.Clear(it)) },
                )
            }
        }
        item(key = "helpers") {
            FocusReveal(index = 3) {
                RoleModelSection(
                    title = stringResource(R.string.settings_models_group_helpers),
                    rows = state.rows.filter {
                        it.role == AgentRole.Subagent ||
                            it.role == AgentRole.Compactor ||
                            it.role == AgentRole.Watchdog ||
                            it.role == AgentRole.Title ||
                            it.role == AgentRole.Suggestion
                    },
                    onOpen = { viewModel.onAction(RoleModelsAction.OpenPicker(it)) },
                    onClear = { viewModel.onAction(RoleModelsAction.Clear(it)) },
                )
            }
        }
    }
    val pickerRole = state.pickerRole
    if (pickerRole != null) {
        RoleModelPicker(
            role = pickerRole,
            state = state,
            onAction = viewModel::onAction,
        )
    }
}

@Composable
private fun RoleModelSection(
    title: String,
    rows: List<RoleModelRowUi>,
    onOpen: (AgentRole) -> Unit,
    onClear: (AgentRole) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp),
        )
        FocusCardGroup {
            rows.forEach { row ->
                item(
                    headlineContent = { Text(roleLabel(row.role)) },
                    supportingContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(rowSupport(row), style = MaterialTheme.typography.bodySmall)
                            if (row.toolsMissing) {
                                Text(
                                    text = stringResource(R.string.settings_models_tools_warning),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    },
                    leadingContent = { FocusCardGroupIcon(icon = roleIcon(row.role)) },
                    trailingContent = {
                        if (row.own) {
                            IconButton(onClick = { onClear(row.role) }) {
                                Icon(
                                    imageVector = FocusIcons.Close,
                                    contentDescription = stringResource(R.string.settings_models_clear),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                    onClick = { onOpen(row.role) },
                )
            }
        }
    }
}

@Composable
private fun rowSupport(row: RoleModelRowUi): String {
    val target = when {
        row.followsMain -> stringResource(R.string.settings_models_follow_main)
        row.inherited != null -> stringResource(R.string.settings_models_follow_role, roleLabel(row.inherited))
        else -> ""
    }
    val detail = listOf(row.providerName, row.model).filter { it.isNotBlank() }.joinToString(" · ")
    return listOf(target, detail).filter { it.isNotBlank() }.joinToString("  ")
}

@Composable
private fun RoleModelPicker(
    role: AgentRole,
    state: RoleModelsUiState,
    onAction: (RoleModelsAction) -> Unit,
) {
    val groups = state.groups.map { group ->
        FocusPickerGroup(
            id = group.id,
            title = group.title,
            icon = group.icon,
            items = group.models.map { model ->
                FocusPickerItem(
                    id = model.id,
                    label = model.name,
                    detail = model.toDetail(),
                )
            },
        )
    }
    val selected = state.rows.firstOrNull { it.role == role }
        ?.takeIf { it.own && it.inherited == null }
        ?.let { row ->
            val group = state.groups.firstOrNull { it.id == row.providerId }
                ?: state.groups.firstOrNull { group -> group.models.any { it.id == row.model } }
                ?: return@let null
            FocusPickerSelection(group.id, row.model)
        }
    FocusGroupedPickerSheet(
        title = stringResource(R.string.settings_models_picker_title, roleLabel(role)),
        searchHint = stringResource(R.string.settings_models_search),
        emptyText = stringResource(
            if (state.modelsLoading) R.string.settings_models_loading else R.string.settings_models_empty,
        ),
        groups = groups,
        query = state.query,
        onQueryChange = { onAction(RoleModelsAction.QueryChanged(it)) },
        onSelect = { onAction(RoleModelsAction.Select(it.groupId, it.itemId)) },
        onDismiss = { onAction(RoleModelsAction.ClosePicker) },
        selected = selected,
        loading = state.modelsLoading,
        onRefresh = { onAction(RoleModelsAction.RefreshModels) },
    )
}

@Composable
private fun RoleModelOption.toDetail(): FocusPickerDetail? {
    val capabilities = buildList {
        if (vision) add(FocusPickerCapability(FocusIcons.Photo, stringResource(R.string.settings_models_vision)))
        if (tools) add(FocusPickerCapability(FocusIcons.Tool, stringResource(R.string.settings_models_tools)))
        if (reasoning) add(FocusPickerCapability(FocusIcons.Idea, stringResource(R.string.settings_models_reasoning)))
    }
    if (detail.isBlank() && capabilities.isEmpty()) return null
    return FocusPickerDetail(meta = detail.ifBlank { null }, capabilities = capabilities)
}

@Composable
private fun roleLabel(role: AgentRole): String = stringResource(
    when (role) {
        AgentRole.Router -> R.string.settings_models_role_router
        AgentRole.WorkerLight -> R.string.settings_models_role_worker_light
        AgentRole.WorkerStandard -> R.string.settings_models_role_worker_standard
        AgentRole.WorkerHeavy -> R.string.settings_models_role_worker_heavy
        AgentRole.Subagent -> R.string.settings_models_role_subagent
        AgentRole.Compactor -> R.string.settings_models_role_compactor
        AgentRole.Watchdog -> R.string.settings_models_role_watchdog
        AgentRole.Judge -> R.string.settings_models_role_judge
        AgentRole.Title -> R.string.settings_models_role_title
        AgentRole.Suggestion -> R.string.settings_models_role_suggestion
    },
)

private fun roleIcon(role: AgentRole) = when (role) {
    AgentRole.Router -> FocusIcons.Bolt
    AgentRole.WorkerLight -> FocusIcons.Chat
    AgentRole.WorkerStandard -> FocusIcons.Chat
    AgentRole.WorkerHeavy -> FocusIcons.Chat
    AgentRole.Subagent -> FocusIcons.Groups
    AgentRole.Compactor -> FocusIcons.Folder
    AgentRole.Watchdog -> FocusIcons.Notification
    AgentRole.Judge -> FocusIcons.Tool
    AgentRole.Title -> FocusIcons.Chat
    AgentRole.Suggestion -> FocusIcons.Idea
}
