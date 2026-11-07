package fan.x0.focus.data.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import fan.x0.focus.core.error.AppError
import fan.x0.focus.core.error.AppErrorType
import fan.x0.focus.core.error.NetworkException
import fan.x0.focus.core.error.toAppError
import fan.x0.focus.core.network.applyProvider
import fan.x0.focus.core.network.openai.ChatProtocol
import fan.x0.focus.data.assistant.AssistantSettings
import fan.x0.focus.data.assistant.SecretStore
import fan.x0.focus.data.assistant.requestConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@SuppressLint("MissingPermission")
internal class CloudVoiceEngine(
    private val context: Context,
    private val assistant: AssistantSettings,
    private val secrets: SecretStore,
    private val http: HttpClient,
) : VoiceEngineDriver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private var record: AudioRecord? = null
    private var loop: Job? = null
    private var buffer: ByteArrayOutputStream? = null
    private var listener: ((EngineEvent) -> Unit)? = null
    private var config: VoiceConfig? = null
    @Volatile private var active = false
    private var lastLevelAt = 0L

    override suspend fun start(config: VoiceConfig, onEvent: (EngineEvent) -> Unit) = mutex.withLock {
        this.config = config
        listener = onEvent
        val audio = createRecord()
        if (audio == null) {
            onEvent(EngineEvent.Failure(error(R.string.voice_error_recording)))
            return@withLock
        }
        record = audio
        buffer = ByteArrayOutputStream()
        active = true
        runCatching { audio.startRecording() }
        if (audio.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            active = false
            releaseRecord()
            onEvent(EngineEvent.Failure(error(R.string.voice_error_recording)))
            return@withLock
        }
        loop = scope.launch { readLoop(audio) }
        Unit
    }

    override suspend fun stop(discard: Boolean) = mutex.withLock {
        config ?: return@withLock
        active = false
        runCatching { record?.stop() }
        loop?.join()
        loop = null
        val pcm = withContext(Dispatchers.IO) {
            buffer?.toByteArray() ?: ByteArray(0)
        }
        buffer = null
        releaseRecord()
        if (discard || pcm.isEmpty()) {
            listener = null
            return@withLock
        }
        val current = config ?: return@withLock
        try {
            val text = transcribe(current, pcm)
            if (text.isNotBlank()) listener?.invoke(EngineEvent.Text(text.trim()))
        } catch (e: VoiceFailure) {
            listener?.invoke(EngineEvent.Failure(e.error))
        } catch (e: Throwable) {
            listener?.invoke(EngineEvent.Failure(e.toAppError()))
        }
        listener = null
        config = null
    }

    private fun readLoop(audio: AudioRecord) {
        val pcm = buffer ?: return
        val chunk = ShortArray(BUFFER_SIZE)
        while (active) {
            val read = runCatching { audio.read(chunk, 0, chunk.size) }.getOrDefault(-1)
            if (read <= 0) {
                if (!active) break
                continue
            }
            val bytes = ByteArray(read * 2)
            for (index in 0 until read) {
                val value = chunk[index].toInt()
                bytes[index * 2] = (value and 0xFF).toByte()
                bytes[index * 2 + 1] = ((value shr 8) and 0xFF).toByte()
            }
            pcm.write(bytes)
            if (pcm.size() >= MAX_BYTES) {
                active = false
                break
            }
            val now = System.currentTimeMillis()
            if (now - lastLevelAt >= LEVEL_INTERVAL_MS) {
                lastLevelAt = now
                listener?.invoke(EngineEvent.Level(level(chunk, read)))
            }
        }
    }

    private suspend fun transcribe(config: VoiceConfig, pcm: ByteArray): String {
        val assistantConfig = assistant.configOnce()
        val provider = assistantConfig.providers.firstOrNull { it.id == config.cloudProviderId }
            ?: assistantConfig.activeProvider
            ?: throw VoiceFailure(error(R.string.voice_error_cloud_provider))
        if (provider.baseUrl.isBlank()) {
            throw VoiceFailure(error(R.string.voice_error_cloud_provider))
        }
        val apiKey = secrets.apiKey(provider.id).orEmpty()
        if (provider.requiresKey && apiKey.isBlank()) {
            throw VoiceFailure(error(R.string.voice_error_cloud_key))
        }
        val model = config.cloudModel.ifBlank { VoiceSettingsStore.DEFAULT_CLOUD_MODEL }
        val wav = withContext(Dispatchers.IO) { wav(pcm) }
        val response = http.submitFormWithBinaryData(
            url = "${provider.baseUrl.trimEnd('/')}/audio/transcriptions",
            formData = formData {
                append("model", model)
                append("response_format", "json")
                append(
                    "file",
                    wav,
                    Headers.build {
                        append(HttpHeaders.ContentType, "audio/wav")
                        append(HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"voice.wav\"")
                    },
                )
            },
        ) {
            applyProvider(provider.requestConfig(apiKey), ChatProtocol.ChatCompletions)
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw VoiceFailure(
                NetworkException.Http(
                    statusCode = response.status.value,
                    error = null,
                    bodySnippet = body,
                ).toAppError(),
            )
        }
        return runCatching {
            json.decodeFromString<TranscriptionResponse>(body).text
        }.getOrNull() ?: body.trim().trim('"')
    }

    private class VoiceFailure(val error: AppError) : Exception()

    private fun wav(pcm: ByteArray): ByteArray {
        val size = pcm.size
        val header = ByteArray(44)
        writeAscii(header, 0, "RIFF")
        writeInt(header, 4, 36 + size)
        writeAscii(header, 8, "WAVE")
        writeAscii(header, 12, "fmt ")
        writeInt(header, 16, 16)
        writeShort(header, 20, 1)
        writeShort(header, 22, 1)
        writeInt(header, 24, SAMPLE_RATE)
        writeInt(header, 28, SAMPLE_RATE * 2)
        writeShort(header, 32, 2)
        writeShort(header, 34, 16)
        writeAscii(header, 36, "data")
        writeInt(header, 40, size)
        return header + pcm
    }

    private fun writeAscii(target: ByteArray, offset: Int, value: String) {
        value.forEachIndexed { index, char -> target[offset + index] = char.code.toByte() }
    }

    private fun writeInt(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value and 0xFF).toByte()
        target[offset + 1] = ((value shr 8) and 0xFF).toByte()
        target[offset + 2] = ((value shr 16) and 0xFF).toByte()
        target[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun writeShort(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value and 0xFF).toByte()
        target[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }

    private fun createRecord(): AudioRecord? {
        val min = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (min <= 0) return null
        val size = maxOf(min * 2, BUFFER_SIZE * 2)
        val sources = listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)
        sources.forEach { source ->
            val created = runCatching {
                AudioRecord(source, SAMPLE_RATE, CHANNEL, ENCODING, size)
            }.getOrNull()
            if (created != null && created.state == AudioRecord.STATE_INITIALIZED) return created
            created?.release()
        }
        return null
    }

    private fun releaseRecord() {
        runCatching { record?.stop() }
        runCatching { record?.release() }
        record = null
    }

    private fun level(buffer: ShortArray, size: Int): Float {
        if (size <= 0) return 0f
        var sum = 0.0
        for (index in 0 until size) {
            val value = buffer[index].toDouble()
            sum += value * value
        }
        val rms = sqrt(sum / size) / 32768.0
        return (rms * 8.0).toFloat().coerceIn(0f, 1f)
    }

    private fun error(resId: Int): AppError = AppError(
        type = AppErrorType.Generic,
        serverMessage = context.getString(resId),
    )

    @Serializable
    private data class TranscriptionResponse(val text: String = "")

    private companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val BUFFER_SIZE = 3200
        const val LEVEL_INTERVAL_MS = 100L
        const val MAX_BYTES = SAMPLE_RATE * 2 * 60 * 5
    }
}
