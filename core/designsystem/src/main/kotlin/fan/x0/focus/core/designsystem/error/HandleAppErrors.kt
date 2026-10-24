package fan.x0.focus.core.designsystem.error

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import fan.x0.focus.core.designsystem.R
import fan.x0.focus.core.error.AppError
import fan.x0.focus.core.error.AppErrorType
import kotlinx.coroutines.flow.Flow

@Composable
fun AppError.message(): String = when (type) {
    AppErrorType.NoNetwork -> stringResource(R.string.error_no_network)
    AppErrorType.Timeout -> stringResource(R.string.error_timeout)
    AppErrorType.Tls -> stringResource(R.string.error_tls)
    AppErrorType.Unauthorized -> serverMessage ?: stringResource(R.string.error_unauthorized)
    AppErrorType.Forbidden -> serverMessage ?: stringResource(R.string.error_forbidden)
    AppErrorType.NotFound -> serverMessage ?: stringResource(R.string.error_not_found)
    AppErrorType.ServerError -> serverMessage ?: stringResource(R.string.error_server)
    AppErrorType.UpdateRequired -> stringResource(R.string.error_update_required)
    AppErrorType.Storage -> stringResource(R.string.error_storage)
    AppErrorType.Generic -> serverMessage ?: stringResource(R.string.error_generic)
}

@Composable
fun HandleAppErrors(errors: Flow<AppError>, hostState: SnackbarHostState) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var current by remember { mutableStateOf<AppError?>(null) }
    LaunchedEffect(errors, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            errors.collect { current = it }
        }
    }
    val message = current?.message()
    LaunchedEffect(current) {
        if (message != null) {
            hostState.showSnackbar(message)
            current = null
        }
    }
}
