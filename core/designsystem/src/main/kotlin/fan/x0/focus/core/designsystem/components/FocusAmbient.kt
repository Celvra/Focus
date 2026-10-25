package fan.x0.focus.core.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

internal class FocusAmbientActivity {
    private var count = 0
    internal var working by mutableStateOf(false)
        private set

    internal fun acquire() {
        count += 1
        working = true
    }

    internal fun release() {
        count = (count - 1).coerceAtLeast(0)
        working = count > 0
    }
}

internal val LocalFocusAmbientActivity = staticCompositionLocalOf { FocusAmbientActivity() }

@Composable
fun FocusAmbientWorking(active: Boolean) {
    val activity = LocalFocusAmbientActivity.current
    DisposableEffect(activity, active) {
        if (active) activity.acquire()
        onDispose { if (active) activity.release() }
    }
}

@Composable
fun FocusAmbientBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val activity = remember { FocusAmbientActivity() }
    CompositionLocalProvider(LocalFocusAmbientActivity provides activity) {
        Box(modifier = modifier.fillMaxSize()) {
            FocusAmbientBackdrop(activity)
            content()
        }
    }
}
