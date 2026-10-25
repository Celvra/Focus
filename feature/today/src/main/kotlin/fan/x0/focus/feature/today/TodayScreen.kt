package fan.x0.focus.feature.today

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fan.x0.focus.core.designsystem.components.FocusLargeTopBar
import fan.x0.focus.core.designsystem.components.FocusLoadingBlock
import fan.x0.focus.core.designsystem.components.FocusTabContent
import fan.x0.focus.core.designsystem.components.FocusTabSwitcher
import fan.x0.focus.core.designsystem.error.HandleAppErrors
import fan.x0.focus.core.mvi.CollectEffect
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TodayRoute(
    onOpenChat: () -> Unit,
    viewModel: TodayViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    HandleAppErrors(viewModel.errors, snackbar)
    CollectEffect(viewModel.effect) { effect ->
        if (effect is TodayEffect.OpenChat) onOpenChat()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        viewModel.onAction(TodayAction.Refresh)
    }
    TodayScreen(state = state, onAction = viewModel::onAction, snackbar = snackbar)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    state: TodayUiState,
    onAction: (TodayAction) -> Unit,
    snackbar: SnackbarHostState,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val tabs = listOf(
        TodaySection.Updates to stringResource(R.string.today_tab_updates),
        TodaySection.Ideas to stringResource(R.string.today_tab_ideas),
        TodaySection.Goals to stringResource(R.string.today_tab_goals),
    )
    val selected = tabs.indexOfFirst { it.first == state.section }.coerceAtLeast(0)
    val updatesScroll = rememberLazyListState()
    val ideasScroll = rememberLazyListState()
    val goalsScroll = rememberLazyListState()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            FocusLargeTopBar(
                title = stringResource(R.string.today_title),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { inner ->
        val direction = LocalLayoutDirection.current
        val contentPadding = PaddingValues(
            start = inner.calculateStartPadding(direction) + 20.dp,
            end = inner.calculateEndPadding(direction) + 20.dp,
            top = 4.dp,
            bottom = inner.calculateBottomPadding() + 28.dp,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = inner.calculateTopPadding()),
        ) {
            if (state.loading) {
                Box(modifier = Modifier.fillMaxSize()) {
                    FocusLoadingBlock()
                }
                return@Column
            }
            FocusTabSwitcher(
                labels = tabs.map { it.second },
                selectedIndex = selected,
                onSelect = { index -> onAction(TodayAction.SectionChanged(tabs[index].first)) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            FocusTabContent(selectedIndex = selected) { index ->
                when (tabs[index].first) {
                    TodaySection.Updates -> UpdatesPane(
                        state = state,
                        onAction = onAction,
                        contentPadding = contentPadding,
                        listState = updatesScroll,
                    )
                    TodaySection.Ideas -> IdeasPane(
                        state = state,
                        onAction = onAction,
                        contentPadding = contentPadding,
                        listState = ideasScroll,
                    )
                    TodaySection.Goals -> GoalsPane(
                        state = state,
                        onAction = onAction,
                        contentPadding = contentPadding,
                        listState = goalsScroll,
                    )
                }
            }
        }
    }

    state.reason?.let { update ->
        ReasonSheet(update = update, onDismiss = { onAction(TodayAction.CloseReason) })
    }
    state.openIdea?.let { idea ->
        IdeaSheet(idea = idea, onAction = onAction)
    }
    state.ideaMenu?.let { idea ->
        IdeaMenuDialog(
            idea = idea,
            onDismiss = { onAction(TodayAction.CloseIdeaMenu) },
            onMoreLike = { onAction(TodayAction.MoreLikeIdea(idea.id)) },
            onNotInterested = { onAction(TodayAction.DismissIdea(idea.id)) },
            onDelete = { onAction(TodayAction.AskDeleteIdea(idea.id)) },
        )
    }
    state.deleteUpdate?.let { update ->
        DeleteUpdateDialog(update = update, onAction = onAction)
    }
    state.deleteIdea?.let { idea ->
        DeleteIdeaDialog(idea = idea, onAction = onAction)
    }
    state.goalDraft?.let { draft ->
        GoalEditorSheet(draft = draft, saving = state.saving, onAction = onAction)
    }
    state.deleteGoal?.let { goal ->
        DeleteGoalDialog(goal = goal, onAction = onAction)
    }
}
