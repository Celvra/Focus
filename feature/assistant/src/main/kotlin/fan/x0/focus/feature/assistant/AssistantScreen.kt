package fan.x0.focus.feature.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fan.x0.focus.core.designsystem.components.AvatarState
import fan.x0.focus.core.designsystem.components.AvatarStyle
import fan.x0.focus.core.designsystem.components.FocusAvatar
import fan.x0.focus.core.designsystem.components.FocusCardGroup
import fan.x0.focus.core.designsystem.components.FocusCardGroupIcon
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusLargeTopBar
import fan.x0.focus.core.designsystem.components.FocusReveal
import fan.x0.focus.core.designsystem.components.LocalFocusSettingsOpener
import fan.x0.focus.core.designsystem.error.HandleAppErrors
import fan.x0.focus.core.designsystem.theme.LocalFocusTypography
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AssistantRoute(viewModel: AssistantViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    HandleAppErrors(viewModel.errors, snackbar)
    AssistantScreen(state = state, onAction = viewModel::onAction, snackbar = snackbar)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    state: AssistantUiState,
    onAction: (AssistantAction) -> Unit,
    snackbar: SnackbarHostState,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val openSettings = LocalFocusSettingsOpener.current
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            FocusLargeTopBar(
                title = stringResource(R.string.assistant_title),
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = openSettings) {
                        Icon(
                            FocusIcons.Settings,
                            contentDescription = stringResource(R.string.assistant_settings_row),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = PaddingValues(
                start = inner.calculateStartPadding(LocalLayoutDirection.current) + 16.dp,
                end = inner.calculateEndPadding(LocalLayoutDirection.current) + 16.dp,
                top = inner.calculateTopPadding() + 8.dp,
                bottom = inner.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "hero") {
                FocusReveal {
                    ProfileHero(
                        name = state.name,
                        focus = state.focus,
                    )
                }
            }
            item(key = "profile") {
                FocusReveal(index = 1) {
                    FocusCardGroup(title = stringResource(R.string.assistant_profile_title)) {
                        formItem(
                            headlineContent = { Text(stringResource(R.string.assistant_profile_item)) },
                            supportingContent = { Text(stringResource(R.string.assistant_profile_body)) },
                            leadingContent = {
                                FocusCardGroupIcon(
                                    icon = FocusIcons.Person,
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            },
                        ) {
                            ProfileField(
                                label = stringResource(R.string.assistant_name),
                                value = state.name,
                            )
                            ProfileField(
                                label = stringResource(R.string.assistant_focus),
                                value = state.focus,
                            )
                            ProfileField(
                                label = stringResource(R.string.assistant_tone),
                                value = state.tone,
                            )
                        }
                    }
                }
            }
            item(key = "memory") {
                FocusReveal(index = 2) {
                    FocusCardGroup(title = stringResource(R.string.assistant_memory_title)) {
                        if (state.memories.isEmpty()) {
                            item(
                                headlineContent = { Text(stringResource(R.string.assistant_memory_empty)) },
                                supportingContent = { Text(stringResource(R.string.assistant_memory_body)) },
                                leadingContent = {
                                    FocusCardGroupIcon(
                                        icon = FocusIcons.Idea,
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    )
                                },
                            )
                        } else {
                            state.memories.forEach { memory ->
                                item(
                                    headlineContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(memory.content, modifier = Modifier.weight(1f))
                                            IconButton(
                                                onClick = {
                                                    onAction(AssistantAction.AskDeleteMemory(memory.id))
                                                },
                                            ) {
                                                Icon(
                                                    FocusIcons.Delete,
                                                    contentDescription = stringResource(R.string.assistant_delete),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
            item(key = "more") {
                FocusReveal(index = 3) {
                    FocusCardGroup(title = stringResource(R.string.assistant_more)) {
                        item(
                            headlineContent = { Text(stringResource(R.string.assistant_settings_row)) },
                            supportingContent = { Text(stringResource(R.string.assistant_settings_row_body)) },
                            leadingContent = {
                                FocusCardGroupIcon(
                                    icon = FocusIcons.Settings,
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            },
                            trailingContent = {
                                Icon(
                                    FocusIcons.Chevron,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            onClick = openSettings,
                        )
                    }
                }
            }
        }
    }
    state.deleteMemory?.let { memory ->
        AlertDialog(
            onDismissRequest = { onAction(AssistantAction.CancelDeleteMemory) },
            title = { Text(stringResource(R.string.assistant_memory_delete_title)) },
            text = { Text(memory.content) },
            confirmButton = {
                TextButton(onClick = { onAction(AssistantAction.ConfirmDeleteMemory) }) {
                    Text(stringResource(R.string.assistant_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(AssistantAction.CancelDeleteMemory) }) {
                    Text(stringResource(R.string.assistant_cancel))
                }
            },
        )
    }
}

@Composable
private fun ProfileHero(name: String, focus: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FocusAvatar(
            style = AvatarStyle(preset = 0, palette = 0),
            size = 88.dp,
            state = AvatarState.Idle,
        )
        Text(
            text = name.ifBlank { stringResource(R.string.assistant_title) },
            style = LocalFocusTypography.current.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = focus.ifBlank { stringResource(R.string.assistant_hero_hint) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProfileField(label: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value.ifBlank { stringResource(R.string.assistant_profile_empty) },
            style = MaterialTheme.typography.bodyLarge,
            color = if (value.isBlank()) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

