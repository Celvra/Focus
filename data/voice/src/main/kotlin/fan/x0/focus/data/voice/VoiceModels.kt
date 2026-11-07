package fan.x0.focus.data.voice

import fan.x0.focus.core.error.AppError

enum class VoiceEngine {
    Local,
    Cloud,
}

data class VoiceConfig(
    val engine: VoiceEngine = VoiceEngine.Local,
    val cloudProviderId: String = "",
    val cloudModel: String = "whisper-1",
)

enum class VoicePhase {
    Idle,
    Listening,
    Busy,
}

enum class VoiceStatus {
    LoadingModel,
    Transcribing,
    DownloadingModel,
}

data class VoiceUiState(
    val phase: VoicePhase = VoicePhase.Idle,
    val status: VoiceStatus? = null,
    val error: AppError? = null,
)

sealed interface VoiceEvent {
    data class Transcript(val text: String) : VoiceEvent
    data class Failed(val error: AppError) : VoiceEvent
}

internal sealed interface EngineEvent {
    data class Text(val text: String) : EngineEvent
    data class Level(val value: Float) : EngineEvent
    data class Failure(val error: AppError) : EngineEvent
}

internal interface VoiceEngineDriver {
    suspend fun start(config: VoiceConfig, onEvent: (EngineEvent) -> Unit)
    suspend fun stop(discard: Boolean)
}
