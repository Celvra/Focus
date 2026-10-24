package fan.x0.focus.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fan.x0.focus.core.designsystem.components.FocusCompactTopBar
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusLoadingBlock
import fan.x0.focus.core.designsystem.components.FocusTabContent
import fan.x0.focus.core.designsystem.components.FocusTabSwitcher
import fan.x0.focus.core.designsystem.error.HandleAppErrors
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LibraryRoute(viewModel: LibraryViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    HandleAppErrors(viewModel.errors, snackbar)
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.onAction(LibraryAction.FolderPicked(uri.toString()))
    }
    LibraryScreen(
        state = state,
        onAction = viewModel::onAction,
        onPickFolder = { folderPicker.launch(null) },
        snackbar = snackbar,
    )
}

private sealed interface LibraryView {
    data object Main : LibraryView
    data class Article(val id: String) : LibraryView
    data class File(val uri: String) : LibraryView
}

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onAction: (LibraryAction) -> Unit,
    onPickFolder: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val view: LibraryView = when {
        state.reader != null -> LibraryView.Article(state.reader.id)
        state.fileReader != null -> LibraryView.File(state.fileReader.uri)
        else -> LibraryView.Main
    }
    val close: () -> Unit = {
        when (view) {
            LibraryView.Main -> Unit
            is LibraryView.Article -> onAction(LibraryAction.CloseReader)
            is LibraryView.File -> onAction(LibraryAction.CloseFile)
        }
    }
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            AnimatedContent(targetState = view, label = "libraryTopBar") { target ->
                when (target) {
                    LibraryView.Main -> FocusCompactTopBar(
                        title = stringResource(R.string.library_title),
                        actions = {
                            MainActions(state = state, onAction = onAction, onPickFolder = onPickFolder)
                        },
                    )
                    is LibraryView.Article -> FocusCompactTopBar(
                        title = state.reader?.feed?.ifBlank { stringResource(R.string.library_tab_articles) }
                            ?: stringResource(R.string.library_tab_articles),
                        subtitle = state.reader?.time,
                        onBack = close,
                        actions = {
                            IconButton(
                                onClick = {
                                    state.reader?.id?.let { onAction(LibraryAction.AskDeleteArticle(it)) }
                                },
                            ) {
                                Icon(
                                    FocusIcons.Delete,
                                    contentDescription = stringResource(R.string.library_delete),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                    is LibraryView.File -> FocusCompactTopBar(
                        title = state.fileReader?.name?.ifBlank { stringResource(R.string.library_tab_files) }
                            ?: stringResource(R.string.library_tab_files),
                        onBack = close,
                    )
                }
            }
        },
    ) { inner ->
        AnimatedContent(
            targetState = view,
            transitionSpec = {
                (fadeIn() + slideInHorizontally { it / 8 }) togetherWith
                    (fadeOut() + slideOutHorizontally { -it / 8 })
            },
            label = "libraryContent",
        ) { target ->
            Box(modifier = Modifier.fillMaxSize().padding(inner)) {
                when (target) {
                    LibraryView.Main -> MainPane(state, onAction, onPickFolder)
                    is LibraryView.Article -> state.reader?.let { ArticleReader(it) }
                    is LibraryView.File -> state.fileReader?.let { FileReader(it) }
                }
            }
        }
    }
    if (state.addFeedOpen) {
        AddFeedDialog(state = state, onAction = onAction)
    }
    if (state.noteOpen) {
        NoteDialog(state = state, onAction = onAction)
    }
    state.deleteTarget?.let { target ->
        val name = when (target) {
            is DeleteTarget.Article -> target.title
            is DeleteTarget.Feed -> target.title
            is DeleteTarget.File -> target.name
        }
        AlertDialog(
            onDismissRequest = { onAction(LibraryAction.CancelDelete) },
            title = { Text(stringResource(R.string.library_delete_title)) },
            text = { Text(stringResource(R.string.library_delete_body, name)) },
            confirmButton = {
                TextButton(onClick = { onAction(LibraryAction.ConfirmDelete) }) {
                    Text(stringResource(R.string.library_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(LibraryAction.CancelDelete) }) {
                    Text(stringResource(R.string.library_cancel))
                }
            },
        )
    }
}

@Composable
private fun MainPane(
    state: LibraryUiState,
    onAction: (LibraryAction) -> Unit,
    onPickFolder: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        val sections = listOf(
            LibrarySection.Articles to stringResource(R.string.library_tab_articles),
            LibrarySection.Feeds to stringResource(R.string.library_tab_feeds),
            LibrarySection.Files to stringResource(R.string.library_tab_files),
        )
        val selected = sections.indexOfFirst { it.first == state.section }.coerceAtLeast(0)
        FocusTabSwitcher(
            labels = sections.map { it.second },
            selectedIndex = selected,
            onSelect = { index -> onAction(LibraryAction.SectionChanged(sections[index].first)) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        when {
            state.loading -> Box(modifier = Modifier.weight(1f)) { FocusLoadingBlock() }
            else -> FocusTabContent(
                selectedIndex = selected,
                modifier = Modifier.weight(1f),
            ) { index ->
                when (sections[index].first) {
                    LibrarySection.Articles -> ArticleList(state, onAction)
                    LibrarySection.Feeds -> FeedList(state, onAction)
                    LibrarySection.Files -> FileList(state, onAction, onPickFolder)
                }
            }
        }
    }
}

@Composable
private fun MainActions(
    state: LibraryUiState,
    onAction: (LibraryAction) -> Unit,
    onPickFolder: () -> Unit,
) {
    if (state.section == LibrarySection.Feeds) {
        IconButton(onClick = { onAction(LibraryAction.OpenAddFeed) }) {
            Icon(
                FocusIcons.Add,
                contentDescription = stringResource(R.string.library_add_feed),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (state.section == LibrarySection.Files) {
        IconButton(onClick = onPickFolder) {
            Icon(
                FocusIcons.Folder,
                contentDescription = stringResource(R.string.library_pick_folder),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    val transition = rememberInfiniteTransition(label = "libraryRefresh")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "libraryRefreshRotation",
    )
    IconButton(onClick = { onAction(LibraryAction.Refresh) }, enabled = !state.refreshing) {
        Icon(
            FocusIcons.Refresh,
            contentDescription = stringResource(R.string.library_refresh),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer { rotationZ = if (state.refreshing) rotation else 0f },
        )
    }
}

@Composable
private fun AddFeedDialog(state: LibraryUiState, onAction: (LibraryAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(LibraryAction.DismissAddFeed) },
        title = { Text(stringResource(R.string.library_add_feed_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.library_add_feed_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = state.feedUrl,
                    onValueChange = { onAction(LibraryAction.FeedUrlChanged(it)) },
                    label = { Text(stringResource(R.string.library_feed_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAction(LibraryAction.SubmitFeed) },
                enabled = state.feedUrl.isNotBlank() && !state.addingFeed,
            ) {
                Text(stringResource(R.string.library_add))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(LibraryAction.DismissAddFeed) }) {
                Text(stringResource(R.string.library_cancel))
            }
        },
    )
}

@Composable
private fun NoteDialog(state: LibraryUiState, onAction: (LibraryAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(LibraryAction.DismissNote) },
        title = { Text(stringResource(R.string.library_note_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = state.noteName,
                    onValueChange = { onAction(LibraryAction.NoteNameChanged(it)) },
                    label = { Text(stringResource(R.string.library_note_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.noteBody,
                    onValueChange = { onAction(LibraryAction.NoteBodyChanged(it)) },
                    label = { Text(stringResource(R.string.library_note_body)) },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAction(LibraryAction.SaveNote) },
                enabled = state.noteName.isNotBlank() && !state.savingNote,
            ) {
                Text(stringResource(R.string.library_note_save))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(LibraryAction.DismissNote) }) {
                Text(stringResource(R.string.library_cancel))
            }
        },
    )
}
