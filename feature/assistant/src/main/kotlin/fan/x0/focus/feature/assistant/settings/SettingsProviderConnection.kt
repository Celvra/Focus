package fan.x0.focus.feature.assistant.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.BrandIcons
import fan.x0.focus.core.designsystem.components.FocusChoiceButtons
import fan.x0.focus.core.designsystem.components.FocusChoiceList
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusLoadingBlob
import fan.x0.focus.core.designsystem.components.FocusOutlinedButton
import fan.x0.focus.core.designsystem.components.FocusTextButton
import fan.x0.focus.core.designsystem.components.hapticTap
import fan.x0.focus.core.designsystem.components.hapticToggle
import fan.x0.focus.core.designsystem.components.rememberProviderImage
import fan.x0.focus.core.designsystem.error.message
import fan.x0.focus.core.network.ProviderAuthType
import fan.x0.focus.core.network.openai.ChatProtocol
import fan.x0.focus.feature.assistant.R
import fan.x0.focus.feature.assistant.SettingsAction
import fan.x0.focus.feature.assistant.SettingsUiState

@Composable
internal fun ProviderConnectionTab(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProviderIconRow(state = state, onAction = onAction)
        OutlinedTextField(
            value = state.draftName,
            onValueChange = { onAction(SettingsAction.NameChanged(it)) },
            label = { Text(stringResource(R.string.settings_provider_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.baseUrl,
            onValueChange = { onAction(SettingsAction.BaseUrlChanged(it)) },
            label = { Text(stringResource(R.string.assistant_base_url)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.apiKeyInput,
            onValueChange = { onAction(SettingsAction.ApiKeyChanged(it)) },
            label = { Text(stringResource(R.string.assistant_api_key)) },
            singleLine = true,
            visualTransformation = if (state.apiKeyVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = hapticTap { onAction(SettingsAction.ToggleApiKeyVisibility) }) {
                    Icon(
                        imageVector = if (state.apiKeyVisible) {
                            FocusIcons.VisibilityOff
                        } else {
                            FocusIcons.Visibility
                        },
                        contentDescription = stringResource(
                            if (state.apiKeyVisible) {
                                R.string.settings_provider_key_hide
                            } else {
                                R.string.settings_provider_key_show
                            },
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        ProtocolSection(state = state, onAction = onAction)
        AuthSection(state = state, onAction = onAction)
        RequiresKeyRow(state = state, onAction = onAction)
        MakeActiveRow(state = state, onAction = onAction)
        if (state.balanceSupported) {
            BalanceSection(state = state, onAction = onAction)
        }
    }
}

@Composable
private fun ProtocolSection(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.settings_protocol_title))
        FocusChoiceButtons(
            options = ChatProtocol.entries,
            selected = state.protocol,
            onSelect = { onAction(SettingsAction.ProtocolChanged(it)) },
            label = { it.displayName() },
        )
    }
}

@Composable
private fun AuthSection(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.settings_provider_auth_title))
        FocusChoiceList(
            options = ProviderAuthType.entries,
            selected = state.authType,
            onSelect = { onAction(SettingsAction.AuthTypeChanged(it)) },
            label = { stringResource(it.labelRes()) },
        )
        Text(
            text = stringResource(state.authType.bodyRes()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (state.authType) {
            ProviderAuthType.ApiKeyHeader -> OutlinedTextField(
                value = state.authName,
                onValueChange = { onAction(SettingsAction.AuthNameChanged(it)) },
                label = { Text(stringResource(R.string.settings_provider_auth_header_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ProviderAuthType.Query -> OutlinedTextField(
                value = state.authName,
                onValueChange = { onAction(SettingsAction.AuthNameChanged(it)) },
                label = { Text(stringResource(R.string.settings_provider_auth_query_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ProviderAuthType.Basic -> OutlinedTextField(
                value = state.authUsername,
                onValueChange = { onAction(SettingsAction.AuthUsernameChanged(it)) },
                label = { Text(stringResource(R.string.settings_provider_auth_username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            else -> Unit
        }
    }
}

@Composable
private fun RequiresKeyRow(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = hapticToggle(!state.requiresKey) {
                    onAction(SettingsAction.RequiresKeyChanged(!state.requiresKey))
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_provider_requires_key),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.settings_provider_requires_key_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = state.requiresKey,
            onCheckedChange = hapticToggle { onAction(SettingsAction.RequiresKeyChanged(it)) },
        )
    }
}

@Composable
private fun MakeActiveRow(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = hapticToggle(!state.draftMakeActive) {
                    onAction(SettingsAction.MakeActiveChanged(!state.draftMakeActive))
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_provider_use),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.settings_provider_make_active_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = state.draftMakeActive,
            onCheckedChange = hapticToggle { onAction(SettingsAction.MakeActiveChanged(it)) },
        )
    }
}

@Composable
private fun BalanceSection(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel(stringResource(R.string.settings_provider_balance))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.balance?.let(::formatBalance) ?: "-",
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            if (state.fetchingBalance) {
                FocusLoadingBlob(size = 16.dp)
            }
            IconButton(onClick = hapticTap { onAction(SettingsAction.FetchBalance) }) {
                Icon(
                    imageVector = FocusIcons.Refresh,
                    contentDescription = stringResource(R.string.settings_provider_balance_refresh),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        state.balanceError?.let { error ->
            Text(
                text = error.message(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ProviderIconRow(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val image = rememberProviderImage(state.draftIcon)
    val icon = BrandIcons.forKey(state.draftIcon)
        ?: BrandIcons.forProvider(state.editingProviderId.orEmpty(), state.draftName)
    val label = when {
        BrandIcons.isCustomImage(state.draftIcon) -> stringResource(R.string.settings_provider_icon_custom)
        else -> BrandIcons.labelFor(state.draftIcon)
            ?: stringResource(R.string.settings_provider_icon_auto)
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            pickerOpen = false
            onAction(SettingsAction.CustomIconPicked(uri))
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                when {
                    state.encodingIcon -> FocusLoadingBlob(size = 20.dp)
                    image != null -> Image(
                        bitmap = image,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    icon != null -> Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                    else -> Text(
                        text = state.draftName.trim().take(1).uppercase(),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_provider_icon),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FocusTextButton(
            text = stringResource(R.string.settings_provider_icon_choose),
            onClick = { pickerOpen = true },
        )
    }
    if (pickerOpen) {
        ProviderIconPickerDialog(
            selected = state.draftIcon,
            encoding = state.encodingIcon,
            onSelect = { onAction(SettingsAction.IconChanged(it)) },
            onPickImage = {
                imagePicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onDismiss = { pickerOpen = false },
        )
    }
}

@Composable
private fun ProviderIconPickerDialog(
    selected: String,
    encoding: Boolean,
    onSelect: (String) -> Unit,
    onPickImage: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_provider_icon_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FocusOutlinedButton(
                    text = stringResource(R.string.settings_provider_icon_pick_image),
                    onClick = onPickImage,
                    icon = FocusIcons.Photo,
                    loading = encoding,
                    enabled = !encoding,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(72.dp),
                    modifier = Modifier.heightIn(max = 380.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "auto") {
                        ProviderIconTile(
                            icon = null,
                            label = stringResource(R.string.settings_provider_icon_auto),
                            selected = selected.isBlank(),
                            onClick = { onSelect("") },
                        )
                    }
                    items(BrandIcons.providerIcons, key = { it.key }) { option ->
                        ProviderIconTile(
                            icon = option.icon,
                            label = option.label,
                            selected = selected == option.key,
                            onClick = { onSelect(option.key) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = hapticTap(onDismiss)) {
                Text(stringResource(R.string.settings_provider_icon_done))
            }
        },
    )
}

@Composable
private fun ProviderIconTile(
    icon: Int?,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = hapticTap(onClick),
        shape = MaterialTheme.shapes.large,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            } else {
                Icon(
                    imageVector = FocusIcons.Provider,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
