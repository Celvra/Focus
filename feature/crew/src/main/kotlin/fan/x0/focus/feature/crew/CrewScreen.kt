package fan.x0.focus.feature.crew

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fan.x0.focus.core.designsystem.error.HandleAppErrors
import fan.x0.focus.core.mvi.CollectEffect
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

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
    CrewScreen(
        state = state,
        onAction = viewModel::onAction,
        snackbar = snackbar,
        onPickMemberPhoto = {
            if (state.memberDraft != null) {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        },
    )
}

@Composable
fun CrewScreen(
    state: CrewUiState,
    onAction: (CrewAction) -> Unit,
    snackbar: SnackbarHostState,
    onPickMemberPhoto: () -> Unit,
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

    BackHandler(enabled = state.group != null && !state.memberSheetOpen && state.groupDraft == null &&
        state.menu == null && state.deleteGroup == null) {
        onAction(CrewAction.CloseGroup)
    }

    if (state.group == null) {
        CrewGroupListScreen(state = state, onAction = onAction, snackbar = snackbar)
    } else {
        CrewChatScreen(
            state = state,
            onAction = onAction,
            snackbar = snackbar,
            onPickMemberPhoto = onPickMemberPhoto,
        )
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
    state.menu?.let { menu ->
        CrewMessageMenuSheet(
            menu = menu,
            onAction = onAction,
            onCopy = copy,
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
