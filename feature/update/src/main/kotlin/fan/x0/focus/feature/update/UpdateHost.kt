package fan.x0.focus.feature.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fan.x0.focus.data.assistant.UpdateController
import org.koin.compose.koinInject

@Composable
fun UpdateHost() {
    val controller = koinInject<UpdateController>()
    val state by controller.state.collectAsStateWithLifecycle()
    val downloadState = controller.download.collectAsStateWithLifecycle()
    LaunchedEffect(controller) {
        controller.checkInBackground()
    }
    if (state.sheetVisible) {
        UpdateSheet(
            state = state,
            downloadState = downloadState,
            onDismiss = controller::dismiss,
            onRetry = { controller.check(userInitiated = true) },
            onDownload = controller::download,
            onCancelDownload = controller::cancelDownload,
        )
    }
}
