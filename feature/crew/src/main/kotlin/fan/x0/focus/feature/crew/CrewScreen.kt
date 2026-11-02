package fan.x0.focus.feature.crew

import android.content.ClipData
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import fan.x0.focus.core.designsystem.components.FocusSearchPickerSheet
import fan.x0.focus.core.designsystem.error.HandleAppErrors
import fan.x0.focus.core.mvi.CollectEffect
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import java.io.File

@Composable
fun CrewRoute(viewModel: CrewViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    HandleAppErrors(viewModel.errors, snackbar)
    val edited = stringResource(R.string.crew_edit_saved)
    val deleted = stringResource(R.string.crew_message_deleted)
    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            CrewEffect.Copied -> Unit
            CrewEffect.MessageEdited -> snackbar.showSnackbar(edited)
            CrewEffect.MessageDeleted -> snackbar.showSnackbar(deleted)
        }
    }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.onAction(CrewAction.MemberPhotoPicked(uri.toString()))
    }
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onAction(CrewAction.AttachPicked(uris.map { it.toString() }))
        }
    }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(4),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onAction(CrewAction.AttachPicked(uris.map { it.toString() }))
        }
    }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraUri
        cameraUri = null
        if (saved && uri != null) {
            viewModel.onAction(CrewAction.AttachPicked(listOf(uri.toString())))
        }
    }
    CrewScreen(
        state = state,
        onAction = viewModel::onAction,
        snackbar = snackbar,
        onPickMemberPhoto = {
            if (state.memberDraft != null) {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        },
        onPickFiles = {
            filePicker.launch(
                arrayOf("image/*", "text/*", "application/json", "application/xml"),
            )
        },
        onPickPhotos = {
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onTakePhoto = {
            val file = File(context.cacheDir, "crew_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            cameraUri = uri
            camera.launch(uri)
        },
    )
}

@Composable
fun CrewScreen(
    state: CrewUiState,
    onAction: (CrewAction) -> Unit,
    snackbar: SnackbarHostState,
    onPickMemberPhoto: () -> Unit,
    onPickFiles: () -> Unit,
    onPickPhotos: () -> Unit,
    onTakePhoto: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.crew_copied)
    val copy: (String) -> Unit = { text ->
        onAction(CrewAction.CloseMessageMenu)
        scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, text)))
            snackbar.showSnackbar(copied)
        }
    }

    val backState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    val gestureActive by remember(backState) {
        derivedStateOf {
            backState.transitionState is NavigationEventTransitionState.InProgress
        }
    }
    val backEnabled = state.group != null && state.selectionKey == null &&
        !state.memberSheetOpen && state.groupDraft == null && state.menu == null &&
        state.edit == null && state.pendingDelete == null && state.deleteGroup == null
    NavigationBackHandler(
        state = backState,
        isBackEnabled = backEnabled,
        onBackCompleted = { onAction(CrewAction.CloseGroup) },
    )

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.group != null && gestureActive) {
            CrewGroupListScreen(state = state, onAction = onAction, snackbar = snackbar)
        }
        AnimatedContent(
            targetState = state.group != null,
            transitionSpec = {
                if (targetState) {
                    (slideInHorizontally { it / 4 } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 8 } + fadeOut())
                } else {
                    (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith
                        (slideOutHorizontally { it / 4 } + fadeOut())
                }
            },
            label = "crewPage",
            modifier = Modifier.graphicsLayer {
                val event = (backState.transitionState as? NavigationEventTransitionState.InProgress)
                    ?.latestEvent
                val progress = event?.progress ?: 0f
                val edge = event?.swipeEdge ?: NavigationEvent.EDGE_LEFT
                val direction = if (edge == NavigationEvent.EDGE_RIGHT) -1f else 1f
                translationX = direction * size.width * 0.35f * progress
                val scale = 1f - 0.06f * progress
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(
                    pivotFractionX = if (edge == NavigationEvent.EDGE_RIGHT) 1f else 0f,
                    pivotFractionY = 0.5f,
                )
            },
        ) { hasGroup ->
            if (!hasGroup) {
                CrewGroupListScreen(state = state, onAction = onAction, snackbar = snackbar)
            } else {
                CrewChatScreen(
                    state = state,
                    onAction = onAction,
                    snackbar = snackbar,
                    onPickMemberPhoto = onPickMemberPhoto,
                    onPickFiles = onPickFiles,
                    onPickPhotos = onPickPhotos,
                    onTakePhoto = onTakePhoto,
                    onCopy = copy,
                )
            }
        }
    }

    state.groupDraft?.let { draft ->
        CrewGroupEditorSheet(
            draft = draft,
            saving = state.saving,
            onAction = onAction,
        )
    }
    state.deleteGroup?.let { group ->
        CrewDeleteGroupDialog(
            group = group,
            onConfirm = { onAction(CrewAction.ConfirmDeleteGroup) },
            onDismiss = { onAction(CrewAction.CancelDeleteGroup) },
        )
    }
    if (state.modelPickerOpen && state.memberDraft != null) {
        FocusSearchPickerSheet(
            title = stringResource(R.string.crew_model_picker_title),
            searchHint = stringResource(R.string.crew_model_picker_search),
            emptyText = stringResource(
                if (state.memberModelsLoading) {
                    R.string.crew_model_picker_loading
                } else {
                    R.string.crew_model_picker_empty
                },
            ),
            items = state.memberModels,
            query = state.memberModelQuery,
            onQueryChange = { onAction(CrewAction.MemberModelQueryChanged(it)) },
            onSelect = { onAction(CrewAction.MemberModelChanged(it)) },
            onDismiss = { onAction(CrewAction.CloseMemberModelPicker) },
            selected = state.memberDraft.model.takeIf { it.isNotBlank() },
            onRefresh = { onAction(CrewAction.RefreshMemberModels) },
        )
    }
    state.edit?.let { edit ->
        CrewMessageEditSheet(
            edit = edit,
            saving = state.saving,
            onDraftChange = { onAction(CrewAction.EditDraftChanged(it)) },
            onSave = { onAction(CrewAction.SaveEdit) },
            onDismiss = { onAction(CrewAction.DismissEdit) },
        )
    }
    state.pendingDelete?.let {
        CrewMessageDeleteDialog(
            onConfirm = { onAction(CrewAction.ConfirmDeleteMessage) },
            onDismiss = { onAction(CrewAction.CancelDeleteMessage) },
        )
    }
}
