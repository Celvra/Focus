package fan.x0.focus.data.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import fan.x0.focus.core.error.AppError
import fan.x0.focus.core.error.AppErrorType
import fan.x0.focus.data.assistant.AssistantSettings
import fan.x0.focus.data.assistant.SecretStore
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

@Single
class VoiceInputController(
    @Provided private val context: Context,
    private val settings: VoiceSettingsStore,
    private val assistant: AssistantSettings,
    private val secrets: SecretStore,
    private val http: HttpClient,
    private val models: LocalVoiceModelRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val local = LocalVoiceEngine(context, models)
    private val cloud = CloudVoiceEngine(context, assistant, secrets, http)

    private val _state = MutableStateFlow(VoiceUiState())
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<VoiceEvent>(extraBufferCapacity = 8)
    val events: Flow<VoiceEvent> = _events.asSharedFlow()

    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    val config: StateFlow<VoiceConfig> = settings.config.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = VoiceConfig(),
    )

    val localReady: StateFlow<Boolean> = models.state.map { it is LocalModelState.Ready }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = models.isReady(),
    )

    private var driver: VoiceEngineDriver? = null
    private var engine: VoiceEngine = VoiceEngine.Local
    private var session = 0

    fun canRecord(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED

    fun isLocalReady(): Boolean = models.isReady()

    fun start() {
        if (!canRecord()) {
            notifyPermissionDenied()
            return
        }
        cancel()
        val current = ++session
        scope.launch {
            val config = settings.configOnce()
            engine = config.engine
            val selected = when (config.engine) {
                VoiceEngine.Local -> local
                VoiceEngine.Cloud -> cloud
            }
            driver = selected
            _state.value = VoiceUiState(
                phase = VoicePhase.Listening,
                status = if (config.engine == VoiceEngine.Local) VoiceStatus.LoadingModel else null,
            )
            selected.start(config) { event ->
                if (current == session) handle(event)
            }
            if (current == session && _state.value.status == VoiceStatus.LoadingModel) {
                _state.value = _state.value.copy(status = null)
            }
        }
    }

    fun stop() {
        val active = driver ?: return
        driver = null
        val current = session
        scope.launch {
            _state.value = _state.value.copy(
                phase = VoicePhase.Busy,
                status = if (engine == VoiceEngine.Cloud) VoiceStatus.Transcribing else null,
            )
            active.stop(discard = false)
            if (current == session) {
                _state.value = VoiceUiState()
                _level.value = 0f
            }
        }
    }

    fun cancel() {
        val active = driver
        driver = null
        session++
        _state.value = VoiceUiState()
        _level.value = 0f
        if (active != null) {
            scope.launch { active.stop(discard = true) }
        }
    }

    fun clearError() {
        if (_state.value.error != null) _state.value = _state.value.copy(error = null)
    }

    fun notifyPermissionGranted() {
        _state.value = VoiceUiState(
            error = AppError(
                AppErrorType.Generic,
                serverMessage = context.getString(R.string.voice_permission_granted),
            ),
        )
    }

    fun notifyPermissionDenied() {
        _state.value = VoiceUiState(
            error = AppError(
                AppErrorType.Generic,
                serverMessage = context.getString(R.string.voice_error_permission),
            ),
        )
    }

    fun releaseLocalModel() {
        local.release()
    }

    private fun handle(event: EngineEvent) {
        when (event) {
            is EngineEvent.Text -> _events.tryEmit(VoiceEvent.Transcript(event.text))
            is EngineEvent.Level -> _level.value = event.value
            is EngineEvent.Failure -> {
                val active = driver
                driver = null
                session++
                _events.tryEmit(VoiceEvent.Failed(event.error))
                _state.value = VoiceUiState(error = event.error)
                _level.value = 0f
                if (active != null) {
                    scope.launch { active.stop(discard = true) }
                }
            }
        }
    }
}
