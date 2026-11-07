package fan.x0.focus.data.voice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fan.x0.focus.core.error.AppError
import org.koin.mp.KoinPlatform

@Stable
class VoiceInputHandle(
    val listening: Boolean,
    val busy: Boolean,
    val status: String?,
    val error: AppError?,
    val autoVoice: Boolean,
    val level: () -> Float,
    val canRecord: () -> Boolean,
    val start: () -> Unit,
    val stop: () -> Unit,
    val cancel: () -> Unit,
    val permissionGranted: () -> Unit,
    val permissionDenied: () -> Unit,
)

@Composable
fun rememberVoiceInput(
    input: String,
    onInput: (String) -> Unit,
): VoiceInputHandle {
    val controller = remember {
        runCatching { KoinPlatform.getKoin().get<VoiceInputController>() }.getOrNull()
    } ?: return noOpVoiceInput()
    val latestInput by rememberUpdatedState(input)
    val latestOnInput by rememberUpdatedState(onInput)
    val state by controller.state.collectAsStateWithLifecycle()
    val config by controller.config.collectAsStateWithLifecycle()
    val localReady by controller.localReady.collectAsStateWithLifecycle()
    val level = remember { mutableFloatStateOf(0f) }
    var base by remember { mutableStateOf("") }
    var lastApplied by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(controller) {
        controller.level.collect { level.floatValue = it }
    }
    LaunchedEffect(controller) {
        controller.events.collect { event ->
            if (event is VoiceEvent.Transcript) {
                val merged = merge(base, event.text)
                lastApplied = merged
                latestOnInput(merged)
            }
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) controller.cancel()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            controller.cancel()
        }
    }

    val status = when (state.status) {
        VoiceStatus.LoadingModel -> stringResource(R.string.voice_status_loading_model)
        VoiceStatus.Transcribing -> stringResource(R.string.voice_status_transcribing)
        VoiceStatus.DownloadingModel -> stringResource(R.string.voice_status_downloading)
        null -> null
    }

    return VoiceInputHandle(
        listening = state.phase == VoicePhase.Listening,
        busy = state.phase == VoicePhase.Busy,
        status = status,
        error = state.error,
        autoVoice = controller.canRecord() &&
            config.engine == VoiceEngine.Local &&
            localReady,
        level = { level.floatValue },
        canRecord = { controller.canRecord() },
        start = {
            controller.clearError()
            base = latestInput
            lastApplied = null
            controller.start()
        },
        stop = { controller.stop() },
        cancel = {
            val current = latestInput
            val applied = lastApplied
            controller.cancel()
            if (applied != null && current == applied && current != base) {
                latestOnInput(base)
            }
            lastApplied = null
        },
        permissionGranted = { controller.notifyPermissionGranted() },
        permissionDenied = { controller.notifyPermissionDenied() },
    )
}

@Composable
private fun noOpVoiceInput(): VoiceInputHandle = remember {
    VoiceInputHandle(
        listening = false,
        busy = false,
        status = null,
        error = null,
        autoVoice = false,
        level = { 0f },
        canRecord = { false },
        start = {},
        stop = {},
        cancel = {},
        permissionGranted = {},
        permissionDenied = {},
    )
}

private fun merge(base: String, text: String): String {
    val tail = text.trim()
    if (tail.isEmpty()) return base
    if (base.isBlank()) return tail
    val noSpace = base.last().isCjk() || tail.first().isCjk()
    return if (base.last().isWhitespace() || noSpace) base + tail else "$base $tail"
}

private fun Char.isCjk(): Boolean = code in 0x4E00..0x9FFF ||
    code in 0x3400..0x4DBF ||
    code in 0x3040..0x30FF ||
    code in 0xAC00..0xD7AF
