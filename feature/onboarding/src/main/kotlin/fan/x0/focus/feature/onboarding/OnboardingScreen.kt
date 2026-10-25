package fan.x0.focus.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import fan.x0.focus.core.designsystem.components.FocusAmbientBackground
import fan.x0.focus.core.designsystem.components.FocusAmbientWorking
import fan.x0.focus.core.designsystem.components.FocusChatBubble
import fan.x0.focus.core.designsystem.components.FocusChip
import fan.x0.focus.core.designsystem.components.FocusCompactTopBar
import fan.x0.focus.core.designsystem.components.FocusComposer
import fan.x0.focus.core.designsystem.components.FocusComposerPill
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusLoadingBlob
import fan.x0.focus.core.designsystem.components.FocusLoadingBlock
import fan.x0.focus.core.designsystem.components.FocusOutlinedButton
import fan.x0.focus.core.designsystem.components.FocusPrimaryButton
import fan.x0.focus.core.designsystem.components.FocusSearchPickerSheet
import fan.x0.focus.core.designsystem.components.FocusTextButton
import fan.x0.focus.core.designsystem.error.HandleAppErrors
import fan.x0.focus.core.designsystem.error.message
import fan.x0.focus.core.mvi.CollectEffect
import fan.x0.focus.core.network.openai.ChatProtocol
import fan.x0.focus.data.assistant.ProviderCatalog
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun OnboardingRoute(
    sessionKey: String,
    viewModel: OnboardingViewModel = koinViewModel(key = sessionKey),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    HandleAppErrors(viewModel.errors, snackbar)
    val saved = stringResource(R.string.onboarding_provider_saved)
    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            OnboardingEffect.ProviderSaved -> snackbar.showSnackbar(saved)
        }
    }
    OnboardingScreen(state = state, onAction = viewModel::onAction, snackbar = snackbar)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onAction: (OnboardingAction) -> Unit,
    snackbar: SnackbarHostState,
) {
    val hazeState = rememberHazeState()
    var overlayHeight by remember { mutableStateOf(0) }
    val bottomPadding = with(LocalDensity.current) { overlayHeight.toDp() } + 12.dp
    FocusAmbientBackground {
        FocusAmbientWorking(active = state.sending)
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                FocusCompactTopBar(
                    title = state.assistantName.ifBlank { stringResource(R.string.onboarding_title) },
                    subtitle = stringResource(
                        if (state.providerReady) {
                            R.string.onboarding_ready_subtitle
                        } else {
                            R.string.onboarding_subtitle
                        },
                    ),
                    onMenu = null,
                    actions = {
                        FocusTextButton(
                            text = stringResource(
                                if (state.providerReady) {
                                    R.string.onboarding_finish
                                } else {
                                    R.string.onboarding_skip
                                },
                            ),
                            onClick = { onAction(OnboardingAction.Finish) },
                        )
                    },
                )
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding(),
            ) {
                if (state.loading) {
                    FocusLoadingBlock()
                } else {
                    Conversation(
                        state = state,
                        onAction = onAction,
                        bottomPadding = bottomPadding,
                        hazeState = hazeState,
                    )
                }
                ComposerArea(
                    state = state,
                    onAction = onAction,
                    hazeState = hazeState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { overlayHeight = it.height },
                )
            }
        }
        if (state.providerSheetOpen) {
            ProviderSheet(state = state, onAction = onAction)
        }
        if (state.modelPickerOpen) {
            FocusSearchPickerSheet(
                title = stringResource(R.string.onboarding_model_picker_title),
                searchHint = stringResource(R.string.onboarding_model_search_hint),
                emptyText = stringResource(
                    if (state.fetchingModels) {
                        R.string.onboarding_models_loading
                    } else {
                        R.string.onboarding_models_empty
                    },
                ),
                items = state.models,
                query = state.modelQuery,
                onQueryChange = { onAction(OnboardingAction.ModelQueryChanged(it)) },
                onSelect = { onAction(OnboardingAction.SelectModel(it)) },
                onDismiss = { onAction(OnboardingAction.CloseModelPicker) },
                selected = state.model,
                onRefresh = { onAction(OnboardingAction.FetchModels) },
            )
        }
    }
}

@Composable
private fun Conversation(
    state: OnboardingUiState,
    onAction: (OnboardingAction) -> Unit,
    bottomPadding: Dp,
    hazeState: HazeState,
) {
    val listState = rememberLazyListState()
    val live = state.sending || state.streamingText.isNotBlank()
    LaunchedEffect(state.messages.size, state.streamingText.length, live) {
        val count = state.messages.size + if (live) 1 else 0
        if (count > 0) listState.requestScrollToItem(count - 1)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(hazeState),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 8.dp,
            end = 16.dp,
            bottom = bottomPadding + 8.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = state.messages,
            key = { it.id },
            contentType = { if (it.fromUser) "user" else "assistant" },
        ) { message ->
            FocusChatBubble(text = message.text, fromUser = message.fromUser)
        }
        if (live) {
            item(key = "live", contentType = "assistant") {
                FocusChatBubble(
                    text = state.streamingText,
                    fromUser = false,
                    streaming = state.sending,
                )
            }
        }
    }
}

@Composable
private fun ComposerArea(
    state: OnboardingUiState,
    onAction: (OnboardingAction) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val name = state.assistantName.ifBlank { stringResource(R.string.onboarding_title) }
    Column(modifier = modifier.fillMaxWidth()) {
        if (state.providerReady) {
            ConnectedBanner()
        }
        FocusComposer(
            input = state.input,
            onInputChange = { onAction(OnboardingAction.InputChanged(it)) },
            placeholder = stringResource(R.string.onboarding_input_hint, name),
            canSend = state.canSend,
            sending = state.sending,
            onSend = { onAction(OnboardingAction.Send) },
            onStop = { onAction(OnboardingAction.Stop) },
            hazeState = hazeState,
            pills = {
                FocusComposerPill(
                    label = stringResource(
                        if (state.providerReady) {
                            R.string.onboarding_model_settings
                        } else {
                            R.string.onboarding_connect_model
                        },
                    ),
                    icon = FocusIcons.Tune,
                    compact = true,
                    onClick = { onAction(OnboardingAction.OpenProviderSheet) },
                )
            },
        )
    }
}

@Composable
private fun ConnectedBanner() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = FocusIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.onboarding_connected_title),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.onboarding_connected_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderSheet(
    state: OnboardingUiState,
    onAction: (OnboardingAction) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { onAction(OnboardingAction.CloseProviderSheet) },
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.onboarding_provider_title),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.onboarding_provider_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onAction(OnboardingAction.CloseProviderSheet) }) {
                    Icon(
                        imageVector = FocusIcons.Close,
                        contentDescription = stringResource(R.string.onboarding_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            PresetRow(state = state, onAction = onAction)
            OutlinedTextField(
                value = state.providerName,
                onValueChange = { onAction(OnboardingAction.ProviderNameChanged(it)) },
                label = { Text(stringResource(R.string.onboarding_provider_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = { onAction(OnboardingAction.BaseUrlChanged(it)) },
                label = { Text(stringResource(R.string.onboarding_base_url)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ProtocolChips(state = state, onAction = onAction)
            ApiKeyField(state = state, onAction = onAction)
            ModelField(state = state, onAction = onAction)
            state.providerFailure?.let { failure ->
                ProviderFailureCard(failure = failure)
            }
            if (state.connectionOk) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = FocusIcons.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.onboarding_connection_ok),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FocusOutlinedButton(
                    text = stringResource(
                        if (state.testing) {
                            R.string.onboarding_testing
                        } else {
                            R.string.onboarding_test
                        },
                    ),
                    onClick = { onAction(OnboardingAction.TestProvider) },
                    enabled = state.canTestProvider,
                    loading = state.testing,
                    icon = FocusIcons.Bolt,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                )
                FocusPrimaryButton(
                    text = stringResource(
                        if (state.saving) {
                            R.string.onboarding_saving
                        } else {
                            R.string.onboarding_save
                        },
                    ),
                    onClick = { onAction(OnboardingAction.SaveProvider) },
                    enabled = state.canSaveProvider,
                    loading = state.saving,
                    icon = FocusIcons.Check,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                )
            }
            Text(
                text = stringResource(R.string.onboarding_privacy),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.providerReady) {
                FocusTextButton(
                    text = stringResource(R.string.onboarding_finish),
                    onClick = { onAction(OnboardingAction.Finish) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PresetRow(state: OnboardingUiState, onAction: (OnboardingAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.onboarding_preset_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProviderCatalog.presets.forEach { preset ->
                FocusChip(
                    label = preset.name,
                    selected = state.baseUrl.trim().trimEnd('/') == preset.baseUrl,
                    onClick = { onAction(OnboardingAction.ApplyPreset(preset.id)) },
                )
            }
        }
    }
}

@Composable
private fun ProtocolChips(state: OnboardingUiState, onAction: (OnboardingAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.onboarding_protocol),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FocusChip(
                label = stringResource(R.string.onboarding_protocol_chat),
                selected = state.protocol == ChatProtocol.ChatCompletions,
                onClick = { onAction(OnboardingAction.ProtocolChanged(ChatProtocol.ChatCompletions)) },
            )
            FocusChip(
                label = stringResource(R.string.onboarding_protocol_responses),
                selected = state.protocol == ChatProtocol.Responses,
                onClick = { onAction(OnboardingAction.ProtocolChanged(ChatProtocol.Responses)) },
            )
            FocusChip(
                label = stringResource(R.string.onboarding_protocol_anthropic),
                selected = state.protocol == ChatProtocol.AnthropicMessages,
                onClick = { onAction(OnboardingAction.ProtocolChanged(ChatProtocol.AnthropicMessages)) },
            )
        }
    }
}

@Composable
private fun ApiKeyField(state: OnboardingUiState, onAction: (OnboardingAction) -> Unit) {
    OutlinedTextField(
        value = state.apiKey,
        onValueChange = { onAction(OnboardingAction.ApiKeyChanged(it)) },
        label = { Text(stringResource(R.string.onboarding_api_key)) },
        singleLine = true,
        visualTransformation = if (state.apiKeyVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            IconButton(onClick = { onAction(OnboardingAction.ToggleApiKeyVisibility) }) {
                Icon(
                    imageVector = if (state.apiKeyVisible) {
                        FocusIcons.VisibilityOff
                    } else {
                        FocusIcons.Visibility
                    },
                    contentDescription = stringResource(
                        if (state.apiKeyVisible) {
                            R.string.onboarding_key_hide
                        } else {
                            R.string.onboarding_key_show
                        },
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ModelField(state: OnboardingUiState, onAction: (OnboardingAction) -> Unit) {
    OutlinedTextField(
        value = state.model,
        onValueChange = { onAction(OnboardingAction.ModelChanged(it)) },
        label = { Text(stringResource(R.string.onboarding_model)) },
        singleLine = true,
        trailingIcon = {
            if (state.fetchingModels) {
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    FocusLoadingBlob(size = 18.dp)
                }
            } else {
                IconButton(onClick = { onAction(OnboardingAction.OpenModelPicker) }) {
                    Icon(
                        imageVector = FocusIcons.Search,
                        contentDescription = stringResource(R.string.onboarding_model_picker_title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ProviderFailureCard(failure: ProviderFailure) {
    val text = when (failure) {
        is ProviderFailure.Validation -> stringResource(
            when (failure.field) {
                ProviderFailure.Field.BaseUrl -> R.string.onboarding_err_base_url
                ProviderFailure.Field.Model -> R.string.onboarding_err_model
                ProviderFailure.Field.ApiKey -> R.string.onboarding_err_key
            },
        )
        is ProviderFailure.Network -> failure.error.message()
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}
