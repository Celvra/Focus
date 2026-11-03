package fan.x0.focus.feature.assistant.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.BrandIcon
import fan.x0.focus.core.designsystem.components.BrandIcons
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusLoadingBlob
import fan.x0.focus.core.designsystem.components.FocusOutlinedButton
import fan.x0.focus.core.designsystem.components.FocusPrimaryButton
import fan.x0.focus.core.designsystem.components.hapticTap
import fan.x0.focus.data.assistant.ProviderModel
import fan.x0.focus.feature.assistant.R
import fan.x0.focus.feature.assistant.SettingsAction
import fan.x0.focus.feature.assistant.SettingsUiState

private data class ProviderModelEditTarget(
    val original: ProviderModel?,
    val model: ProviderModel,
)

@Composable
internal fun ProviderModelsTab(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    var fetchOpen by rememberSaveable { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<ProviderModelEditTarget?>(null) }
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "intro") {
                Text(
                    text = stringResource(R.string.settings_provider_models_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.draftModels.isEmpty()) {
                item(key = "empty") {
                    ProviderModelsEmpty()
                }
            }
            items(state.draftModels, key = { it.id }) { model ->
                ProviderModelRow(
                    model = model,
                    isDefault = model.id == state.model,
                    onEdit = { editTarget = ProviderModelEditTarget(model, model) },
                    onSetDefault = {
                        onAction(SettingsAction.SetDefaultProviderModel(model.id))
                    },
                    onDelete = {
                        onAction(SettingsAction.RemoveProviderModel(model.id))
                    },
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FocusOutlinedButton(
                text = stringResource(R.string.settings_provider_model_add),
                onClick = {
                    editTarget = ProviderModelEditTarget(
                        original = null,
                        model = ProviderModel(id = ""),
                    )
                },
                icon = FocusIcons.Add,
                modifier = Modifier.weight(1f),
            )
            FocusOutlinedButton(
                text = stringResource(R.string.settings_provider_model_fetch),
                onClick = { fetchOpen = true },
                icon = FocusIcons.Refresh,
                modifier = Modifier.weight(1f),
            )
        }
    }
    if (fetchOpen) {
        ProviderModelsFetchSheet(
            state = state,
            onAction = onAction,
            onDismiss = { fetchOpen = false },
        )
    }
    editTarget?.let { target ->
        ProviderModelEditorDialog(
            target = target,
            onDismiss = { editTarget = null },
            onConfirm = { id, name ->
                if (target.original == null) {
                    onAction(SettingsAction.AddProviderModel(id, name))
                } else {
                    onAction(
                        SettingsAction.UpdateProviderModel(
                            originalId = target.original.id,
                            id = id,
                            name = name,
                        ),
                    )
                }
                editTarget = null
            },
        )
    }
}

@Composable
private fun ProviderModelsEmpty() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_provider_models_empty),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.settings_provider_models_empty_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProviderModelRow(
    model: ProviderModel,
    isDefault: Boolean,
    onEdit: () -> Unit,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit,
) {
    val brand = BrandIcons.forModel(model.id)
    Surface(
        onClick = hapticTap(onEdit),
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (isDefault) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        contentColor = if (isDefault) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (brand != null) {
                        BrandIcon(
                            icon = brand,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        Icon(
                            imageVector = FocusIcons.Assistant,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.name.ifBlank { model.id },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (model.name.isNotBlank() && model.name != model.id) {
                    Text(
                        text = model.id,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (isDefault) {
                    Text(
                        text = stringResource(R.string.settings_provider_model_default),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            IconButton(onClick = hapticTap(onSetDefault)) {
                Icon(
                    imageVector = FocusIcons.Check,
                    contentDescription = stringResource(
                        if (isDefault) {
                            R.string.settings_provider_model_default
                        } else {
                            R.string.settings_provider_model_set_default
                        },
                    ),
                    tint = if (isDefault) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            IconButton(onClick = hapticTap(onDelete)) {
                Icon(
                    imageVector = FocusIcons.Delete,
                    contentDescription = stringResource(R.string.settings_provider_model_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProviderModelEditorDialog(
    target: ProviderModelEditTarget,
    onDismiss: () -> Unit,
    onConfirm: (id: String, name: String) -> Unit,
) {
    var id by remember(target) { mutableStateOf(target.model.id) }
    var name by remember(target) { mutableStateOf(target.model.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (target.original == null) {
                        R.string.settings_provider_model_add_title
                    } else {
                        R.string.settings_provider_model_edit_title
                    },
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it },
                    label = { Text(stringResource(R.string.settings_provider_model_id)) },
                    singleLine = true,
                    enabled = target.original == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.settings_provider_model_name)) },
                    placeholder = {
                        Text(stringResource(R.string.settings_provider_model_name_placeholder))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(id, name) },
                enabled = id.isNotBlank(),
            ) {
                Text(stringResource(R.string.assistant_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.assistant_cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderModelsFetchSheet(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) {
        if (state.availableModels.isEmpty() && !state.fetchingModels) {
            onAction(SettingsAction.FetchModels)
        }
    }
    val filtered = remember(state.availableModels, query) {
        val keyword = query.trim()
        if (keyword.isBlank()) {
            state.availableModels
        } else {
            state.availableModels.filter { it.contains(keyword, ignoreCase = true) }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.settings_provider_model_fetch_title),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    modifier = Modifier.weight(1f),
                )
                if (state.fetchingModels) {
                    FocusLoadingBlob(size = 18.dp)
                } else {
                    IconButton(onClick = hapticTap { onAction(SettingsAction.FetchModels) }) {
                        Icon(
                            imageVector = FocusIcons.Refresh,
                            contentDescription = stringResource(R.string.settings_provider_model_fetch),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.settings_provider_model_search)) },
                leadingIcon = {
                    Icon(
                        imageVector = FocusIcons.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            )
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    Text(
                        text = stringResource(
                            if (state.fetchingModels) {
                                R.string.settings_provider_model_fetch_loading
                            } else {
                                R.string.settings_provider_model_fetch_empty
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(filtered, key = { it }) { id ->
                        val selected = state.draftModels.any { it.id == id }
                        ProviderAvailableModelRow(
                            id = id,
                            selected = selected,
                            onToggle = { onAction(SettingsAction.ToggleProviderModel(id)) },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = {
                        onAction(SettingsAction.AddProviderModels(filtered))
                    },
                    enabled = filtered.isNotEmpty(),
                ) {
                    Text(stringResource(R.string.settings_provider_model_fetch_all))
                }
                Box(modifier = Modifier.weight(1f))
                FocusPrimaryButton(
                    text = stringResource(R.string.settings_provider_model_fetch_done),
                    onClick = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun ProviderAvailableModelRow(
    id: String,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    val brand = BrandIcons.forModel(id)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = hapticTap(onToggle))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (brand != null) {
            BrandIcon(
                icon = brand,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        } else {
            Icon(
                imageVector = FocusIcons.Assistant,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = id,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Checkbox(
            checked = selected,
            onCheckedChange = { onToggle() },
        )
    }
}
