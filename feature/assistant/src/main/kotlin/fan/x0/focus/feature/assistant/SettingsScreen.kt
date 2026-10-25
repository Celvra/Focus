package fan.x0.focus.feature.assistant

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fan.x0.focus.core.designsystem.components.FocusCardGroup
import fan.x0.focus.core.designsystem.components.FocusCardGroupIcon
import fan.x0.focus.core.designsystem.components.FocusChip
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusLargeTopBar
import fan.x0.focus.core.designsystem.components.FocusLoadingBlob
import fan.x0.focus.core.designsystem.components.FocusOutlinedButton
import fan.x0.focus.core.designsystem.components.FocusPrimaryButton
import fan.x0.focus.core.designsystem.components.FocusReveal
import fan.x0.focus.core.designsystem.components.FocusSearchPickerSheet
import fan.x0.focus.core.designsystem.components.FocusStatusPill
import fan.x0.focus.core.designsystem.components.FocusTextButton
import fan.x0.focus.core.designsystem.components.StatusTone
import fan.x0.focus.core.designsystem.error.HandleAppErrors
import fan.x0.focus.core.designsystem.error.message
import fan.x0.focus.core.designsystem.theme.LocalFocusMotion
import fan.x0.focus.core.mvi.CollectEffect
import fan.x0.focus.core.network.openai.ChatProtocol
import fan.x0.focus.core.network.openai.ProviderBalance
import fan.x0.focus.data.assistant.CommandPolicy
import fan.x0.focus.data.assistant.JudgeKind
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    HandleAppErrors(viewModel.errors, snackbar)
    val saved = stringResource(R.string.settings_provider_saved)
    val connected = stringResource(R.string.assistant_connection_ok)
    val deleted = stringResource(R.string.settings_provider_deleted)
    val reset = stringResource(R.string.settings_reset_done)
    CollectEffect(viewModel.effect) { effect ->
        val message = when (effect) {
            SettingsEffect.ConnectionSaved -> saved
            SettingsEffect.ConnectionOk -> connected
            SettingsEffect.ProviderDeleted -> deleted
            SettingsEffect.Reset -> reset
        }
        snackbar.showSnackbar(message)
    }
    SettingsScreen(state = state, onAction = viewModel::onAction, snackbar = snackbar)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    snackbar: SnackbarHostState,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            FocusLargeTopBar(
                title = stringResource(R.string.settings_page_title),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = inner.calculateStartPadding(LocalLayoutDirection.current) + 16.dp,
                end = inner.calculateEndPadding(LocalLayoutDirection.current) + 16.dp,
                top = inner.calculateTopPadding() + 8.dp,
                bottom = inner.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "providers") {
                FocusReveal {
                    ProvidersGroup(state = state, onAction = onAction)
                }
            }
            item(key = "language") {
                FocusReveal(index = 1) {
                    FocusCardGroup(title = stringResource(R.string.settings_language_title)) {
                        formItem(
                            headlineContent = { Text(stringResource(R.string.settings_language_item)) },
                            supportingContent = { Text(stringResource(R.string.settings_language_body)) },
                            leadingContent = {
                                FocusCardGroupIcon(
                                    icon = FocusIcons.Language,
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            },
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FocusChip(
                                    label = stringResource(R.string.assistant_language_system),
                                    selected = state.language == null,
                                    onClick = { onAction(SettingsAction.LanguageChanged(null)) },
                                )
                                FocusChip(
                                    label = stringResource(R.string.assistant_language_zh),
                                    selected = state.language == "zh",
                                    onClick = { onAction(SettingsAction.LanguageChanged("zh")) },
                                )
                                FocusChip(
                                    label = stringResource(R.string.assistant_language_en),
                                    selected = state.language == "en",
                                    onClick = { onAction(SettingsAction.LanguageChanged("en")) },
                                )
                            }
                        }
                    }
                }
            }
            item(key = "feeds") {
                FocusReveal(index = 2) {
                    FocusCardGroup(title = stringResource(R.string.settings_feeds_title)) {
                        formItem(
                            headlineContent = { Text(stringResource(R.string.settings_feeds_item)) },
                            supportingContent = { Text(stringResource(R.string.settings_feeds_body)) },
                            leadingContent = {
                                FocusCardGroupIcon(
                                    icon = FocusIcons.Feed,
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            },
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listOf(1, 3, 6, 12, 24).forEach { hours ->
                                    FocusChip(
                                        label = stringResource(R.string.assistant_hours, hours),
                                        selected = state.refreshHours == hours,
                                        onClick = { onAction(SettingsAction.RefreshHoursChanged(hours)) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item(key = "policy") {
                FocusReveal(index = 3) {
                    PolicyGroup(state = state, onAction = onAction)
                }
            }
            item(key = "about") {
                FocusReveal(index = 4) {
                    AboutGroup(onReset = { onAction(SettingsAction.AskReset) })
                }
            }
        }
    }
    if (state.editorOpen) {
        ProviderEditorSheet(state = state, onAction = onAction)
    }
    if (state.modelPickerOpen) {
        FocusSearchPickerSheet(
            title = stringResource(R.string.settings_model_picker_title),
            searchHint = stringResource(R.string.settings_model_search_hint),
            emptyText = stringResource(R.string.settings_models_empty),
            items = state.models,
            query = state.modelQuery,
            onQueryChange = { onAction(SettingsAction.ModelQueryChanged(it)) },
            onSelect = { onAction(SettingsAction.SelectModel(it)) },
            onDismiss = { onAction(SettingsAction.CloseModelPicker) },
            selected = state.model,
        )
    }
    if (state.resetConfirm) {
        AlertDialog(
            onDismissRequest = { onAction(SettingsAction.CancelReset) },
            title = { Text(stringResource(R.string.settings_reset_title)) },
            text = { Text(stringResource(R.string.settings_reset_body)) },
            confirmButton = {
                TextButton(onClick = { onAction(SettingsAction.ConfirmReset) }) {
                    Text(stringResource(R.string.settings_reset_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(SettingsAction.CancelReset) }) {
                    Text(stringResource(R.string.assistant_cancel))
                }
            },
        )
    }
    if (state.deleteConfirm) {
        AlertDialog(
            onDismissRequest = { onAction(SettingsAction.CancelDeleteProvider) },
            title = { Text(stringResource(R.string.settings_provider_delete_title)) },
            text = { Text(stringResource(R.string.settings_provider_delete_body, state.draftName)) },
            confirmButton = {
                TextButton(onClick = { onAction(SettingsAction.DeleteProvider) }) {
                    Text(stringResource(R.string.assistant_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(SettingsAction.CancelDeleteProvider) }) {
                    Text(stringResource(R.string.assistant_cancel))
                }
            },
        )
    }
}

@Composable
private fun ProvidersGroup(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    FocusCardGroup(title = stringResource(R.string.settings_providers_title)) {
        state.providers.forEach { provider ->
            val active = provider.id == state.activeProviderId
            item(
                selected = active,
                headlineContent = {
                    Text(
                        text = provider.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    Column {
                        Text(
                            text = provider.baseUrl,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = provider.model.ifBlank {
                                stringResource(R.string.settings_provider_no_model)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = protocolLabel(provider.protocol),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        state.providerBalances[provider.id]?.let { balance ->
                            Text(
                                text = formatBalance(balance),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                leadingContent = {
                    ProviderBadge(providerId = provider.id, active = active)
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProviderCheck(active = active)
                        IconButton(onClick = { onAction(SettingsAction.OpenProvider(provider.id)) }) {
                            Icon(
                                imageVector = FocusIcons.Edit,
                                contentDescription = stringResource(R.string.settings_provider_edit),
                            )
                        }
                    }
                },
                onClick = { onAction(SettingsAction.SelectProvider(provider.id)) },
            )
        }
        item(
            headlineContent = { Text(stringResource(R.string.settings_provider_add)) },
            supportingContent = { Text(stringResource(R.string.settings_provider_add_body)) },
            leadingContent = {
                FocusCardGroupIcon(
                    icon = FocusIcons.Add,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            },
            onClick = { onAction(SettingsAction.AddProvider) },
        )
    }
}

@Composable
private fun protocolLabel(protocol: ChatProtocol): String = stringResource(
    when (protocol) {
        ChatProtocol.ChatCompletions -> R.string.settings_protocol_chat
        ChatProtocol.Responses -> R.string.settings_protocol_responses
        ChatProtocol.AnthropicMessages -> R.string.settings_protocol_anthropic
    },
)

private fun formatBalance(balance: ProviderBalance): String {
    val symbol = when (balance.currency.uppercase()) {
        "USD" -> "$"
        "CNY" -> "¥"
        else -> balance.currency.uppercase() + " "
    }
    val sign = if (balance.amount < 0) "-" else ""
    return sign + symbol + String.format(java.util.Locale.US, "%.2f", kotlin.math.abs(balance.amount))
}

@Composable
private fun ProviderBadge(providerId: String, active: Boolean) {
    val container by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "providerBadgeContainer",
    )
    val content by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "providerBadgeContent",
    )
    val painter = when (providerId.lowercase()) {
        "openai" -> painterResource(R.drawable.ic_provider_openai)
        "openrouter" -> painterResource(R.drawable.ic_provider_openrouter)
        "deepseek" -> painterResource(R.drawable.ic_provider_deepseek)
        else -> null
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (painter != null) {
            Icon(
                painter = painter,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Icon(
                imageVector = FocusIcons.Bolt,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ProviderCheck(active: Boolean) {
    val alpha by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "providerCheckAlpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (active) 1f else 0.6f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "providerCheckScale",
    )
    Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = FocusIcons.Check,
            contentDescription = if (active) stringResource(R.string.settings_provider_active) else null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer {
                    this.alpha = alpha
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderEditorSheet(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { onAction(SettingsAction.CloseEditor) },
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
                        if (state.isNewProvider) {
                            R.string.settings_provider_editor_new
                        } else {
                            R.string.settings_provider_editor_edit
                        },
                    ),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onAction(SettingsAction.CloseEditor) }) {
                    Icon(
                        imageVector = FocusIcons.Close,
                        contentDescription = stringResource(R.string.settings_provider_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.settings_protocol_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FocusChip(
                        label = stringResource(R.string.settings_protocol_chat),
                        selected = state.protocol == ChatProtocol.ChatCompletions,
                        onClick = { onAction(SettingsAction.ProtocolChanged(ChatProtocol.ChatCompletions)) },
                    )
                    FocusChip(
                        label = stringResource(R.string.settings_protocol_responses),
                        selected = state.protocol == ChatProtocol.Responses,
                        onClick = { onAction(SettingsAction.ProtocolChanged(ChatProtocol.Responses)) },
                    )
                    FocusChip(
                        label = stringResource(R.string.settings_protocol_anthropic),
                        selected = state.protocol == ChatProtocol.AnthropicMessages,
                        onClick = { onAction(SettingsAction.ProtocolChanged(ChatProtocol.AnthropicMessages)) },
                    )
                }
            }
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
                    IconButton(onClick = { onAction(SettingsAction.ToggleApiKeyVisibility) }) {
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
            OutlinedTextField(
                value = state.model,
                onValueChange = { onAction(SettingsAction.ModelChanged(it)) },
                label = { Text(stringResource(R.string.assistant_model)) },
                singleLine = true,
                trailingIcon = {
                    if (state.fetchingModels) {
                        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            FocusLoadingBlob(size = 18.dp)
                        }
                    } else {
                        IconButton(onClick = { onAction(SettingsAction.OpenModelPicker) }) {
                            Icon(
                                imageVector = FocusIcons.Search,
                                contentDescription = stringResource(R.string.settings_model_picker_title),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.balanceSupported) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.settings_provider_balance),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        state.balance?.let { balance ->
                            Text(
                                text = formatBalance(balance),
                                style = MaterialTheme.typography.titleSmallEmphasized,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (state.fetchingBalance) {
                            FocusLoadingBlob(size = 16.dp)
                        } else if (state.balance == null) {
                            Text(
                                text = "—",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onAction(SettingsAction.FetchBalance) }) {
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAction(SettingsAction.MakeActiveChanged(!state.draftMakeActive)) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_provider_use),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = state.draftMakeActive,
                    onCheckedChange = { onAction(SettingsAction.MakeActiveChanged(it)) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FocusOutlinedButton(
                    text = stringResource(R.string.assistant_test),
                    onClick = { onAction(SettingsAction.TestProvider) },
                    enabled = !state.testing,
                    loading = state.testing,
                    icon = FocusIcons.Bolt,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                )
                FocusPrimaryButton(
                    text = stringResource(
                        if (state.isNewProvider) R.string.settings_provider_add else R.string.assistant_save,
                    ),
                    onClick = { onAction(SettingsAction.SaveProvider) },
                    enabled = !state.savingConnection,
                    loading = state.savingConnection,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                )
            }
            when {
                state.testing -> FocusStatusPill(
                    text = stringResource(R.string.assistant_testing),
                    tone = StatusTone.Active,
                )
                state.connectionOk -> FocusStatusPill(
                    text = stringResource(R.string.assistant_connected),
                    tone = StatusTone.Success,
                )
            }
            AnimatedVisibility(
                visible = state.connectionError != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                state.connectionError?.let { error ->
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
            }
            if (state.canDelete) {
                FocusTextButton(
                    text = stringResource(R.string.settings_provider_delete),
                    onClick = { onAction(SettingsAction.AskDeleteProvider) },
                )
            }
        }
    }
}

@Composable
private fun PolicyGroup(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    FocusCardGroup(title = stringResource(R.string.settings_policy_title)) {
        formItem(
            headlineContent = { Text(stringResource(R.string.settings_policy_item)) },
            supportingContent = { Text(stringResource(R.string.settings_policy_body)) },
            leadingContent = {
                FocusCardGroupIcon(
                    icon = FocusIcons.Bolt,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            },
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FocusChip(
                    label = stringResource(R.string.settings_policy_yolo),
                    selected = state.policy == CommandPolicy.Yolo,
                    onClick = { onAction(SettingsAction.PolicyChanged(CommandPolicy.Yolo)) },
                )
                FocusChip(
                    label = stringResource(R.string.settings_policy_autopilot),
                    selected = state.policy == CommandPolicy.AutoPilot,
                    onClick = { onAction(SettingsAction.PolicyChanged(CommandPolicy.AutoPilot)) },
                )
                FocusChip(
                    label = stringResource(R.string.settings_policy_manual),
                    selected = state.policy == CommandPolicy.Manual,
                    onClick = { onAction(SettingsAction.PolicyChanged(CommandPolicy.Manual)) },
                )
            }
        }
        formItem(
            headlineContent = { Text(stringResource(R.string.settings_judge_item)) },
            supportingContent = { Text(stringResource(R.string.settings_judge_body)) },
            leadingContent = {
                FocusCardGroupIcon(
                    icon = FocusIcons.Tune,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            },
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FocusChip(
                    label = stringResource(R.string.settings_judge_kind_jev),
                    selected = state.judgeKind == JudgeKind.Jev,
                    onClick = { onAction(SettingsAction.JudgeKindChanged(JudgeKind.Jev)) },
                )
                FocusChip(
                    label = stringResource(R.string.settings_judge_kind_llm),
                    selected = state.judgeKind == JudgeKind.Llm,
                    onClick = { onAction(SettingsAction.JudgeKindChanged(JudgeKind.Llm)) },
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.providers.forEach { provider ->
                    FocusChip(
                        label = provider.name,
                        selected = provider.id == state.judgeProviderId,
                        onClick = { onAction(SettingsAction.JudgeProviderChanged(provider.id)) },
                    )
                }
            }
            OutlinedTextField(
                value = state.judgeModel,
                onValueChange = { onAction(SettingsAction.JudgeModelChanged(it)) },
                label = { Text(stringResource(R.string.settings_judge_model)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(0, 1, 2, 4, 8).forEach { window ->
                    FocusChip(
                        label = stringResource(R.string.settings_judge_context, window),
                        selected = state.judgeContextWindow == window,
                        onClick = { onAction(SettingsAction.JudgeContextChanged(window)) },
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(1, 2, 3).forEach { risk ->
                    FocusChip(
                        label = stringResource(R.string.settings_judge_risk, risk),
                        selected = state.judgeMaxRisk == risk,
                        onClick = { onAction(SettingsAction.JudgeMaxRiskChanged(risk)) },
                    )
                }
                listOf(5, 8, 15, 30).forEach { seconds ->
                    FocusChip(
                        label = stringResource(R.string.settings_judge_timeout, seconds),
                        selected = state.judgeTimeoutSeconds == seconds,
                        onClick = { onAction(SettingsAction.JudgeTimeoutChanged(seconds)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutGroup(onReset: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
    FocusCardGroup(title = stringResource(R.string.settings_about_title)) {
        item(
            headlineContent = { Text(stringResource(R.string.assistant_about, version)) },
            supportingContent = { Text(stringResource(R.string.assistant_about_body)) },
            leadingContent = {
                FocusCardGroupIcon(
                    icon = FocusIcons.Assistant,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            },
        )
        item(
            headlineContent = { Text(stringResource(R.string.settings_privacy_title)) },
            supportingContent = { Text(stringResource(R.string.assistant_privacy)) },
            leadingContent = {
                FocusCardGroupIcon(
                    icon = FocusIcons.Library,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            },
        )
        item(
            headlineContent = { Text(stringResource(R.string.settings_reset_title)) },
            supportingContent = { Text(stringResource(R.string.settings_reset_body)) },
            leadingContent = {
                FocusCardGroupIcon(
                    icon = FocusIcons.Refresh,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            },
            onClick = onReset,
        )
    }
}
