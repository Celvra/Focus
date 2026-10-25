package fan.x0.focus.feature.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
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
import fan.x0.focus.data.assistant.WorkspaceFile
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
        containerColor = Color.Transparent,
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
                    AssistantHero(name = state.assistantName)
                }
            }
            item(key = "workspace") {
                FocusReveal(index = 1) {
                    WorkspaceGroup(state = state, onAction = onAction)
                }
            }
            item(key = "more") {
                FocusReveal(index = 2) {
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
    state.openDocument?.let { document ->
        DocumentSheet(
            document = document,
            onClose = { onAction(AssistantAction.CloseDocument) },
        )
    }
}

@Composable
private fun WorkspaceGroup(state: AssistantUiState, onAction: (AssistantAction) -> Unit) {
    val entries = state.documents.map { it.file to it.file.meta() }
    FocusCardGroup(title = stringResource(R.string.assistant_workspace_title)) {
        entries.forEach { (file, meta) ->
            item(
                headlineContent = { Text(meta.title, modifier = Modifier.fillMaxWidth()) },
                supportingContent = { Text(meta.body) },
                leadingContent = {
                    FocusCardGroupIcon(
                        icon = meta.icon,
                        containerColor = meta.container(),
                        contentColor = meta.content(),
                    )
                },
                trailingContent = {
                    Icon(
                        FocusIcons.Chevron,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = { onAction(AssistantAction.OpenDocument(file)) },
            )
        }
        if (state.documents.isEmpty()) {
            item(
                headlineContent = { Text(stringResource(R.string.assistant_workspace_empty)) },
                supportingContent = { Text(stringResource(R.string.assistant_workspace_body)) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentSheet(document: WorkspaceDocumentUi, onClose: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = document.file.fileName,
                        style = MaterialTheme.typography.titleLargeEmphasized,
                    )
                    Text(
                        text = stringResource(R.string.assistant_workspace_readonly),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = FocusIcons.Close,
                        contentDescription = stringResource(R.string.assistant_workspace_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SelectionContainer(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = document.content.ifBlank {
                        stringResource(R.string.assistant_workspace_empty)
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun AssistantHero(name: String) {
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
            text = name.ifBlank { stringResource(R.string.assistant_hero_default) },
            style = LocalFocusTypography.current.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.assistant_hero_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private data class FileMeta(
    val title: String,
    val body: String,
    val icon: ImageVector,
    val tone: Int,
)

@Composable
private fun WorkspaceFile.meta(): FileMeta = when (this) {
    WorkspaceFile.Soul -> FileMeta(
        title = "SOUL.md",
        body = stringResource(R.string.assistant_file_soul),
        icon = FocusIcons.Assistant,
        tone = 0,
    )
    WorkspaceFile.Persona -> FileMeta(
        title = "PERSONA.md",
        body = stringResource(R.string.assistant_file_persona),
        icon = FocusIcons.Person,
        tone = 1,
    )
    WorkspaceFile.User -> FileMeta(
        title = "USER.md",
        body = stringResource(R.string.assistant_file_user),
        icon = FocusIcons.Chat,
        tone = 2,
    )
    WorkspaceFile.Agent -> FileMeta(
        title = "AGENT.md",
        body = stringResource(R.string.assistant_file_agent),
        icon = FocusIcons.Idea,
        tone = 0,
    )
    WorkspaceFile.Memory -> FileMeta(
        title = "MEMORY.md",
        body = stringResource(R.string.assistant_file_memory),
        icon = FocusIcons.Library,
        tone = 1,
    )
}

@Composable
private fun FileMeta.container(): Color = when (tone) {
    0 -> MaterialTheme.colorScheme.primaryContainer
    1 -> MaterialTheme.colorScheme.secondaryContainer
    else -> MaterialTheme.colorScheme.tertiaryContainer
}

@Composable
private fun FileMeta.content(): Color = when (tone) {
    0 -> MaterialTheme.colorScheme.onPrimaryContainer
    1 -> MaterialTheme.colorScheme.onSecondaryContainer
    else -> MaterialTheme.colorScheme.onTertiaryContainer
}
