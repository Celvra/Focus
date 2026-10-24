package fan.x0.focus

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import fan.x0.focus.app.ShellState
import fan.x0.focus.app.ShellViewModel
import fan.x0.focus.core.designsystem.components.FocusAmbientBackground
import fan.x0.focus.core.designsystem.components.LocalFocusDrawerAvailable
import fan.x0.focus.core.designsystem.components.LocalFocusDrawerOpener
import fan.x0.focus.core.designsystem.components.LocalFocusSettingsOpener
import fan.x0.focus.core.designsystem.theme.FocusMotionDefaults
import fan.x0.focus.data.assistant.AssistantSettings
import fan.x0.focus.feature.assistant.AssistantRoute
import fan.x0.focus.feature.assistant.SettingsRoute
import fan.x0.focus.feature.chat.ChatRoute
import fan.x0.focus.feature.goals.GoalsRoute
import fan.x0.focus.feature.library.LibraryRoute
import fan.x0.focus.feature.onboarding.OnboardingRoute
import fan.x0.focus.feature.today.TodayRoute
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import java.util.UUID

@Composable
fun FocusApp() {
    val settings = koinInject<AssistantSettings>()
    val context = LocalContext.current
    var onboardingDone by remember { mutableStateOf<Boolean?>(null) }
    var onboardingSession by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var wasOnboarding by rememberSaveable { mutableStateOf(false) }
    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(settings) {
        settings.onboardingDone.collect { onboardingDone = it }
    }
    LaunchedEffect(onboardingDone) {
        if (onboardingDone == false && !wasOnboarding) {
            onboardingSession = UUID.randomUUID().toString()
        }
        wasOnboarding = onboardingDone == false
    }
    LaunchedEffect(onboardingDone) {
        if (onboardingDone != true || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    when (onboardingDone) {
        null -> Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {}
        false -> OnboardingRoute(sessionKey = onboardingSession)
        true -> MainShell()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell() {
    val backStack = rememberSaveable(saver = ShellBackStack.Saver) {
        ShellBackStack(FocusRoute.Chat)
    }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val activity = LocalActivity.current
    val shell: ShellViewModel = koinViewModel()
    val shellState = shell.state.collectAsStateWithLifecycle()

    val navigate: (FocusRoute) -> Unit = { route ->
        backStack.open(route)
        scope.launch { drawerState.close() }
    }
    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }

    LaunchedEffect(backStack.current) {
        shell.refresh()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 840.dp
        val drawer: @Composable () -> Unit = {
            ShellDrawer(
                state = shellState.value,
                current = backStack.current,
                onNavigate = navigate,
                onNewChat = {
                    shell.newChat()
                    navigate(FocusRoute.Chat)
                },
                onOpenConversation = { id ->
                    shell.openConversation(id)
                    navigate(FocusRoute.Chat)
                },
            )
        }
        val content: @Composable () -> Unit = {
            CompositionLocalProvider(
                LocalFocusDrawerOpener provides openDrawer,
                LocalFocusDrawerAvailable provides !expanded,
                LocalFocusSettingsOpener provides { navigate(FocusRoute.Settings) },
            ) {
                FocusAmbientBackground {
                    ShellContent(
                        backStack = backStack,
                        shellState = shellState,
                        onOpenConversation = { id ->
                            shell.openConversation(id)
                            navigate(FocusRoute.Chat)
                        },
                        onOpenArchivedConversation = { id ->
                            shell.openArchivedConversation(id)
                            navigate(FocusRoute.Chat)
                        },
                        onArchiveConversation = shell::archiveConversation,
                        onDeleteConversation = shell::deleteConversation,
                        onExit = { activity?.finish() },
                    )
                }
            }
        }

        if (expanded) {
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(modifier = Modifier.width(300.dp)) {
                        Box(modifier = Modifier.safeDrawingPadding()) {
                            drawer()
                        }
                    }
                },
            ) {
                content()
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                        drawer()
                    }
                },
            ) {
                content()
            }
            LaunchedEffect(drawerState.isOpen) {
                if (drawerState.isOpen) shell.refresh()
            }
        }
    }
}

@Composable
private fun ShellContent(
    backStack: ShellBackStack,
    shellState: State<ShellState>,
    onOpenConversation: (String) -> Unit,
    onOpenArchivedConversation: (String) -> Unit,
    onArchiveConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onExit: () -> Unit,
) {
    val motion = FocusMotionDefaults
    NavDisplay(
        backStack = backStack.entries,
        onBack = { if (!backStack.back()) onExit() },
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
        entryProvider = { route ->
            when (route) {
                FocusRoute.Chat -> NavEntry(
                    key = route,
                    contentKey = route,
                    metadata = NavDisplay.transitionSpec {
                        fadeIn(motion.effectsDefault()) togetherWith fadeOut(motion.effectsFast())
                    } + NavDisplay.popTransitionSpec {
                        fadeIn(motion.effectsDefault()) togetherWith fadeOut(motion.effectsFast())
                    } + NavDisplay.predictivePopTransitionSpec {
                        fadeIn(motion.effectsDefault()) togetherWith fadeOut(motion.effectsFast())
                    },
                ) {
                    ChatRoute()
                }
                FocusRoute.Today -> NavEntry(route) {
                    TodayRoute(onOpenChat = { backStack.open(FocusRoute.Chat) })
                }
                FocusRoute.Goals -> NavEntry(route) { GoalsRoute() }
                FocusRoute.Library -> NavEntry(route) { LibraryRoute() }
                FocusRoute.Assistant -> NavEntry(route) { AssistantRoute() }
                FocusRoute.Settings -> NavEntry(route) { SettingsRoute() }
                FocusRoute.History -> NavEntry(route) {
                    val state = shellState.value
                    HistoryRoute(
                        conversations = state.conversations,
                        archived = state.archived,
                        onOpen = onOpenConversation,
                        onOpenArchived = onOpenArchivedConversation,
                        onArchive = onArchiveConversation,
                        onDelete = onDeleteConversation,
                    )
                }
            }
        },
    )
}
