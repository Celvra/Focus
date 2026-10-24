package fan.x0.focus.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fan.x0.focus.core.designsystem.components.AvatarState
import fan.x0.focus.core.designsystem.components.AvatarStyle
import fan.x0.focus.core.designsystem.components.FocusAmbientBackground
import fan.x0.focus.core.designsystem.components.FocusAmbientWorking
import fan.x0.focus.core.designsystem.components.FocusAvatar
import fan.x0.focus.core.designsystem.components.FocusCardGroup
import fan.x0.focus.core.designsystem.components.FocusCardGroupIcon
import fan.x0.focus.core.designsystem.components.FocusChatBubble
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusLoadingBlob
import fan.x0.focus.core.designsystem.components.FocusPrimaryButton
import fan.x0.focus.core.designsystem.components.FocusReveal
import fan.x0.focus.core.designsystem.components.FocusSearchPickerSheet
import fan.x0.focus.core.designsystem.components.FocusTextButton
import fan.x0.focus.core.designsystem.components.pressScale
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens
import fan.x0.focus.core.designsystem.theme.LocalFocusMotion
import fan.x0.focus.core.designsystem.theme.LocalFocusTypography
import org.koin.compose.viewmodel.koinViewModel

private fun OnboardingUiState.avatarState(): AvatarState = when {
    failure != null -> AvatarState.Alert
    testing || fetchingModels -> AvatarState.Orbit
    sending && streamingText.isBlank() -> AvatarState.Sleep
    sending || streamingText.isNotBlank() -> AvatarState.Orbit
    else -> AvatarState.Idle
}

@Composable
fun OnboardingRoute(
    sessionKey: String,
    viewModel: OnboardingViewModel = koinViewModel(key = sessionKey),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OnboardingScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onAction: (OnboardingAction) -> Unit,
) {
    FocusAmbientBackground {
        FocusAmbientWorking(active = state.sending || state.streamingText.isNotBlank())
        AnimatedContent(
            targetState = state.phase,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "onboardingPhase",
        ) { phase ->
            when (phase) {
                OnboardingPhase.Connect -> ConnectContent(state, onAction)
                OnboardingPhase.Chat -> ChatContent(state, onAction)
            }
        }
        if (state.modelPickerOpen) {
            FocusSearchPickerSheet(
                title = stringResource(R.string.onboarding_model_picker_title),
                searchHint = stringResource(R.string.onboarding_model_search_hint),
                emptyText = stringResource(R.string.onboarding_models_empty),
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
private fun ConnectContent(state: OnboardingUiState, onAction: (OnboardingAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        FocusReveal {
            StepHeader(step = 0)
        }
        FocusReveal(index = 1) {
            Box(
                modifier = Modifier.size(168.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.size(156.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                ) {}
                FocusAvatar(
                    style = AvatarStyle(preset = state.avatarSeed % 8, palette = (state.avatarSeed / 8) % 6),
                    size = 132.dp,
                    state = state.avatarState(),
                )
            }
        }
        FocusReveal(index = 2) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_tagline),
                    style = LocalFocusTypography.current.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.onboarding_hero_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        FocusReveal(index = 3) {
            CapabilityRow()
        }
        FocusReveal(index = 4) {
            ConnectionCard(state = state, onAction = onAction)
        }
        FocusReveal(index = 5) {
            Text(
                text = stringResource(R.string.onboarding_later),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun StepHeader(step: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.titleLargeEmphasized,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        StepBar(step = step)
        Text(
            text = stringResource(R.string.onboarding_step_of, step + 1),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StepBar(step: Int) {
    val motion = LocalFocusMotion.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(2) { index ->
            val active = index <= step
            val width by animateDpAsState(
                targetValue = if (index == step) 28.dp else 12.dp,
                animationSpec = motion.spatialFast(),
                label = "stepWidth$index",
            )
            Surface(
                modifier = Modifier
                    .height(6.dp)
                    .width(width)
                    .graphicsLayer { alpha = if (active) 1f else 0.35f },
                shape = CircleShape,
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ) {}
        }
    }
}

@Composable
private fun CapabilityRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Capability(
            icon = FocusIcons.Today,
            title = stringResource(R.string.onboarding_cap_today_title),
            body = stringResource(R.string.onboarding_cap_today_body),
            modifier = Modifier.weight(1f),
        )
        Capability(
            icon = FocusIcons.Library,
            title = stringResource(R.string.onboarding_cap_library_title),
            body = stringResource(R.string.onboarding_cap_library_body),
            modifier = Modifier.weight(1f),
        )
        Capability(
            icon = FocusIcons.Goals,
            title = stringResource(R.string.onboarding_cap_goals_title),
            body = stringResource(R.string.onboarding_cap_goals_body),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Capability(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FocusCardGroupIcon(
                icon = icon,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ConnectionCard(state: OnboardingUiState, onAction: (OnboardingAction) -> Unit) {
    val motion = LocalFocusMotion.current
    FocusCardGroup(title = stringResource(R.string.onboarding_connect_title)) {
        formItem(
            headlineContent = { Text(stringResource(R.string.onboarding_connect_title)) },
            supportingContent = { Text(stringResource(R.string.onboarding_connect_body)) },
            leadingContent = {
                FocusCardGroupIcon(
                    icon = FocusIcons.Bolt,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            },
        ) {
            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = { onAction(OnboardingAction.BaseUrlChanged(it)) },
                label = { Text(stringResource(R.string.onboarding_base_url)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
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
            FocusPrimaryButton(
                text = if (state.testing) {
                    stringResource(R.string.onboarding_testing)
                } else {
                    stringResource(R.string.onboarding_test)
                },
                onClick = { onAction(OnboardingAction.Connect) },
                enabled = !state.testing,
                loading = state.testing,
                icon = FocusIcons.Bolt,
                modifier = Modifier.fillMaxWidth(),
            )
            AnimatedVisibility(
                visible = state.failure != null,
                enter = fadeIn(motion.effectsDefault()) + expandVertically(motion.spatialDefault()),
                exit = fadeOut(motion.effectsFast()) + shrinkVertically(motion.spatialDefault()),
            ) {
                state.failure?.let { failure ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(
                                    when (failure.kind) {
                                        ConnectFailureKind.Streaming -> R.string.onboarding_err_streaming
                                        ConnectFailureKind.Tools -> R.string.onboarding_err_tools
                                        ConnectFailureKind.Models -> R.string.onboarding_err_models
                                        ConnectFailureKind.Generic -> R.string.onboarding_err_generic
                                    },
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            failure.detail?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            Text(
                text = stringResource(R.string.onboarding_privacy),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChatContent(state: OnboardingUiState, onAction: (OnboardingAction) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size, state.streamingText) {
        val target = state.messages.size + if (state.streamingText.isNotBlank() || state.sending) 1 else 0
        if (target > 0) listState.animateScrollToItem(target - 1)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FocusAvatar(
                style = AvatarStyle(preset = 0, palette = 0),
                size = 48.dp,
                state = state.avatarState(),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.onboarding_meet_title),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.onboarding_meet_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StepBar(step = 1)
                Text(
                    text = stringResource(R.string.onboarding_step_of, 2),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Text(
                    text = stringResource(R.string.onboarding_meet_hint),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FocusTextButton(
                text = stringResource(R.string.onboarding_skip),
                onClick = { onAction(OnboardingAction.Skip) },
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.messages.isEmpty() && state.sending && state.streamingText.isBlank()) {
                item(key = "warming") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FocusLoadingBlob(size = 44.dp)
                        Text(
                            text = stringResource(R.string.onboarding_ready_title),
                            style = MaterialTheme.typography.titleSmallEmphasized,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.onboarding_ready_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            items(state.messages, key = { it.id }) { message ->
                Box(modifier = Modifier.animateItem()) {
                    FocusChatBubble(text = message.text, fromUser = message.fromUser)
                }
            }
            if (state.streamingText.isNotBlank() || state.sending) {
                item(key = "streaming") {
                    Box(modifier = Modifier.animateItem()) {
                        FocusChatBubble(
                            text = state.streamingText,
                            fromUser = false,
                            streaming = state.sending,
                        )
                    }
                }
            }
        }
        state.failure?.let { failure ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(
                    text = failure.detail ?: stringResource(R.string.onboarding_err_generic),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Composer(state = state, onAction = onAction)
    }
}

@Composable
private fun Composer(state: OnboardingUiState, onAction: (OnboardingAction) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value = state.input,
                onValueChange = { onAction(OnboardingAction.InputChanged(it)) },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.onboarding_input_hint)) },
                maxLines = 4,
                shape = MaterialTheme.shapes.large,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onAction(OnboardingAction.Send) }),
            )
            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .pressScale(interaction),
                shape = CircleShape,
                color = if (state.input.isNotBlank() && !state.sending) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                onClick = { onAction(OnboardingAction.Send) },
                interactionSource = interaction,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = FocusIcons.Send,
                        contentDescription = stringResource(R.string.onboarding_send),
                        tint = if (state.input.isNotBlank() && !state.sending) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
