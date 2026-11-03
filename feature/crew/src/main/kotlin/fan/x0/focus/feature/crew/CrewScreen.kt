package fan.x0.focus.feature.crew

import android.content.ClipData
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import fan.x0.focus.core.designsystem.components.FocusAmbientSurface
import fan.x0.focus.core.designsystem.components.FocusGroupedPickerSheet
import fan.x0.focus.core.designsystem.components.FocusPickerGroup
import fan.x0.focus.core.designsystem.components.FocusPickerItem
import fan.x0.focus.core.designsystem.components.FocusPickerSelection
import fan.x0.focus.core.designsystem.error.HandleAppErrors
import fan.x0.focus.core.designsystem.theme.FocusMotionDefaults
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
    val latest = rememberUpdatedState(state)
    CrewScreen(
        state = state,
        onAction = viewModel::onAction,
        snackbar = snackbar,
        onPickMemberPhoto = {
            if (latest.value.memberDraft != null) {
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

    val pages = remember {
        mutableStateListOf<CrewPage>(CrewPage.Groups).apply {
            state.group?.let { add(CrewPage.Chat(it.id)) }
        }
    }
    val groupId = state.group?.id
    LaunchedEffect(groupId) {
        val target = groupId?.let { CrewPage.Chat(it) }
        if (target == null) {
            while (pages.size > 1) pages.removeAt(pages.lastIndex)
        } else if (pages.lastOrNull() != target) {
            while (pages.size > 1) pages.removeAt(pages.lastIndex)
            pages.add(target)
        }
    }
    val closeChat: () -> Unit = {
        if (pages.size > 1) pages.removeAt(pages.lastIndex)
        onAction(CrewAction.CloseGroup)
    }
    val pageAction: (CrewAction) -> Unit = { action ->
        if (action == CrewAction.CloseGroup) closeChat() else onAction(action)
    }
    val latestState = rememberUpdatedState(state)
    val motion = FocusMotionDefaults

    NavDisplay(
        backStack = pages,
        onBack = closeChat,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            (
                slideInHorizontally(motion.spatialDefault()) { it / 5 } +
                    fadeIn(motion.effectsDefault())
                ) togetherWith fadeOut(motion.effectsFast())
        },
        popTransitionSpec = {
            (
                slideInHorizontally(motion.spatialDefault()) { -it / 5 } +
                    fadeIn(motion.effectsDefault())
                ) togetherWith (
                slideOutHorizontally(motion.spatialFast()) { it / 5 } +
                    fadeOut(motion.effectsFast())
                )
        },
        predictivePopTransitionSpec = {
            (
                slideInHorizontally(motion.spatialDefault()) { -it / 5 } +
                    fadeIn(motion.effectsDefault())
                ) togetherWith (
                slideOutHorizontally(motion.spatialFast()) { it / 5 } +
                    fadeOut(motion.effectsFast())
                )
        },
        entryProvider = { page ->
            when (page) {
                CrewPage.Groups -> NavEntry(key = page, contentKey = page) {
                    CrewGroupListScreen(
                        state = latestState.value,
                        onAction = pageAction,
                        snackbar = snackbar,
                    )
                }
                is CrewPage.Chat -> NavEntry(key = page, contentKey = page) {
                    CrewChatPage(
                        groupId = page.id,
                        state = latestState.value,
                        onAction = pageAction,
                        snackbar = snackbar,
                        onPickMemberPhoto = onPickMemberPhoto,
                        onPickFiles = onPickFiles,
                        onPickPhotos = onPickPhotos,
                        onTakePhoto = onTakePhoto,
                        onCopy = copy,
                    )
                }
            }
        },
    )

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
        val groups = remember(state.memberModels) {
            state.memberModels.map { group ->
                FocusPickerGroup(
                    id = group.providerId,
                    title = group.providerName,
                    icon = group.icon,
                    items = group.models.map { model ->
                        FocusPickerItem(
                            id = model.id,
                            label = model.name.ifBlank { model.id },
                        )
                    },
                )
            }
        }
        FocusGroupedPickerSheet(
            title = stringResource(R.string.crew_model_picker_title),
            searchHint = stringResource(R.string.crew_model_picker_search),
            emptyText = stringResource(
                if (state.memberModelsLoading) {
                    R.string.crew_model_picker_loading
                } else {
                    R.string.crew_model_picker_empty
                },
            ),
            groups = groups,
            query = state.memberModelQuery,
            onQueryChange = { onAction(CrewAction.MemberModelQueryChanged(it)) },
            onSelect = { onAction(CrewAction.MemberModelChanged(it.groupId, it.itemId)) },
            onDismiss = { onAction(CrewAction.CloseMemberModelPicker) },
            selected = FocusPickerSelection(
                state.memberDraft.providerId,
                state.memberDraft.model,
            ),
            loading = state.memberModelsLoading,
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

@Composable
private fun CrewChatPage(
    groupId: String,
    state: CrewUiState,
    onAction: (CrewAction) -> Unit,
    snackbar: SnackbarHostState,
    onPickMemberPhoto: () -> Unit,
    onPickFiles: () -> Unit,
    onPickPhotos: () -> Unit,
    onTakePhoto: () -> Unit,
    onCopy: (String) -> Unit,
) {
    val previous = remember { mutableStateOf(state) }
    val live = state.group?.id == groupId
    val shown = if (live) state else previous.value
    SideEffect {
        if (live) previous.value = state
    }
    FocusAmbientSurface {
        CrewChatScreen(
            state = shown,
            onAction = onAction,
            snackbar = snackbar,
            onPickMemberPhoto = onPickMemberPhoto,
            onPickFiles = onPickFiles,
            onPickPhotos = onPickPhotos,
            onTakePhoto = onTakePhoto,
            onCopy = onCopy,
        )
    }
}

private sealed interface CrewPage : java.io.Serializable {
    data object Groups : CrewPage
    data class Chat(val id: String) : CrewPage
}
