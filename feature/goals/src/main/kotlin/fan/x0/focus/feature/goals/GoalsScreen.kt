package fan.x0.focus.feature.goals

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fan.x0.focus.core.designsystem.components.FocusEmptyState
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusLargeTopBar
import fan.x0.focus.core.designsystem.components.FocusLoadingBlock
import fan.x0.focus.core.designsystem.components.FocusPrimaryButton
import fan.x0.focus.core.designsystem.components.FocusStatusPill
import fan.x0.focus.core.designsystem.components.FocusTabContent
import fan.x0.focus.core.designsystem.components.FocusTabSwitcher
import fan.x0.focus.core.designsystem.components.FocusTextButton
import fan.x0.focus.core.designsystem.components.StatusTone
import fan.x0.focus.core.designsystem.components.hapticSelect
import fan.x0.focus.core.designsystem.components.hapticTap
import fan.x0.focus.core.designsystem.error.HandleAppErrors
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens
import fan.x0.focus.core.designsystem.theme.LocalFocusMotion
import fan.x0.focus.core.designsystem.theme.LocalFocusTypography
import fan.x0.focus.data.assistant.GoalStatus
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun GoalsRoute(viewModel: GoalsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    HandleAppErrors(viewModel.errors, snackbar)
    GoalsScreen(state = state, onAction = viewModel::onAction, snackbar = snackbar)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    state: GoalsUiState,
    onAction: (GoalsAction) -> Unit,
    snackbar: SnackbarHostState,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            FocusLargeTopBar(
                title = stringResource(R.string.goals_title),
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = hapticTap { onAction(GoalsAction.Add) }) {
                        Icon(
                            FocusIcons.Add,
                            contentDescription = stringResource(R.string.goals_add),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { inner ->
        val filters = listOf(
            GoalFilter.Active to stringResource(R.string.goals_filter_active, state.activeCount),
            GoalFilter.Done to stringResource(R.string.goals_filter_done, state.doneCount),
            GoalFilter.All to stringResource(R.string.goals_filter_all),
        )
        val selected = filters.indexOfFirst { it.first == state.filter }.coerceAtLeast(0)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = inner.calculateTopPadding()),
        ) {
            FocusTabSwitcher(
                labels = filters.map { it.second },
                selectedIndex = selected,
                onSelect = { index -> onAction(GoalsAction.FilterChanged(filters[index].first)) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            FocusTabContent(selectedIndex = selected) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = inner.calculateStartPadding(LocalLayoutDirection.current) + 16.dp,
                        end = inner.calculateEndPadding(LocalLayoutDirection.current) + 16.dp,
                        top = 4.dp,
                        bottom = inner.calculateBottomPadding() + 28.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when {
                        state.loading -> item(key = "loading") {
                            FocusLoadingBlock()
                        }
                        state.visible.isEmpty() -> item(key = "empty") {
                            FocusEmptyState(
                                icon = FocusIcons.Goals,
                                title = stringResource(R.string.goals_empty_title),
                                body = stringResource(R.string.goals_empty_body),
                                actionLabel = stringResource(R.string.goals_add),
                                onAction = { onAction(GoalsAction.Add) },
                            )
                        }
                        else -> items(state.visible, key = { it.id }) { goal ->
                            Box(modifier = Modifier.animateItem()) {
                                GoalCard(
                                    goal = goal,
                                    onProgress = { onAction(GoalsAction.ProgressChanged(goal.id, it)) },
                                    onToggleDone = { onAction(GoalsAction.ToggleDone(goal.id)) },
                                    onEdit = { onAction(GoalsAction.Edit(goal.id)) },
                                    onDelete = { onAction(GoalsAction.Delete(goal.id)) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    state.draft?.let { draft ->
        ModalBottomSheet(
            onDismissRequest = { onAction(GoalsAction.DismissEditor) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            GoalEditor(draft = draft, saving = state.saving, onAction = onAction)
        }
    }
    state.deleteTarget?.let { goal ->
        AlertDialog(
            onDismissRequest = { onAction(GoalsAction.CancelDelete) },
            title = { Text(stringResource(R.string.goals_delete_title)) },
            text = { Text(stringResource(R.string.goals_delete_body, goal.title)) },
            confirmButton = {
                TextButton(onClick = hapticTap { onAction(GoalsAction.ConfirmDelete) }) {
                    Text(stringResource(R.string.goals_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = hapticTap { onAction(GoalsAction.CancelDelete) }) {
                    Text(stringResource(R.string.goals_cancel))
                }
            },
        )
    }
}

@Composable
private fun GoalCard(
    goal: GoalUi,
    onProgress: (Int) -> Unit,
    onToggleDone: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var slider by remember(goal.id, goal.progress) { mutableFloatStateOf(goal.progress / 100f) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = FocusShapeTokens.card,
        color = if (goal.status == GoalStatus.Done) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = goal.title,
                    style = LocalFocusTypography.current.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (goal.status == GoalStatus.Done) {
                        TextDecoration.LineThrough
                    } else {
                        TextDecoration.None
                    },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                GoalStatusPill(goal)
            }
            goal.detail?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (goal.dueLabel != null) {
                Text(
                    text = stringResource(R.string.goals_due, goal.dueLabel),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (goal.status != GoalStatus.Done) {
                val animatedProgress by animateFloatAsState(
                    targetValue = slider,
                    animationSpec = LocalFocusMotion.current.spatialDefault(),
                    label = "goalProgress",
                )
                val animatedPercent by animateIntAsState(
                    targetValue = (slider * 100).toInt(),
                    animationSpec = LocalFocusMotion.current.spatialDefault(),
                    label = "goalPercent",
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "$animatedPercent%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Slider(
                    value = slider,
                    onValueChange = { slider = it },
                    onValueChangeFinished = hapticSelect { onProgress((slider * 100).toInt()) },
                )
            } else if (!goal.note.isNullOrBlank()) {
                Text(
                    text = goal.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FocusTextButton(
                    text = if (goal.status == GoalStatus.Done) {
                        stringResource(R.string.goals_reopen)
                    } else {
                        stringResource(R.string.goals_done)
                    },
                    onClick = onToggleDone,
                )
                FocusTextButton(text = stringResource(R.string.goals_edit), onClick = onEdit)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = hapticTap(onDelete)) {
                    Icon(
                        FocusIcons.Delete,
                        contentDescription = stringResource(R.string.goals_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun GoalStatusPill(goal: GoalUi) {
    when (goal.status) {
        GoalStatus.Active -> FocusStatusPill(
            text = stringResource(R.string.goals_status_active),
            tone = StatusTone.Active,
        )
        GoalStatus.Paused -> FocusStatusPill(
            text = stringResource(R.string.goals_status_paused),
            tone = StatusTone.Warning,
        )
        GoalStatus.Done -> FocusStatusPill(
            text = stringResource(R.string.goals_status_done),
            tone = StatusTone.Success,
        )
    }
}

@Composable
private fun GoalEditor(draft: GoalDraft, saving: Boolean, onAction: (GoalsAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(
                if (draft.id == null) R.string.goals_editor_new else R.string.goals_editor_edit,
            ),
            style = MaterialTheme.typography.titleLarge,
        )
        OutlinedTextField(
            value = draft.title,
            onValueChange = { onAction(GoalsAction.TitleChanged(it)) },
            label = { Text(stringResource(R.string.goals_field_title)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.detail,
            onValueChange = { onAction(GoalsAction.DetailChanged(it)) },
            label = { Text(stringResource(R.string.goals_field_detail)) },
            minLines = 3,
            maxLines = 6,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        FocusPrimaryButton(
            text = stringResource(R.string.goals_save),
            onClick = { onAction(GoalsAction.Save) },
            enabled = draft.title.isNotBlank() && !saving,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
