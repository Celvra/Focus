package fan.x0.focus.data.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import fan.x0.focus.core.error.AppError
import fan.x0.focus.core.error.AppErrorType
import java.io.File
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@SuppressLint("MissingPermission")
internal class LocalVoiceEngine(
    private val context: Context,
    private val models: LocalVoiceModelRepository,
) : VoiceEngineDriver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null
    private var record: AudioRecord? = null
    private var loop: Job? = null
    @Volatile private var active = false
    private var committed = ""
    private var lastText = ""
    private var lastLevelAt = 0L
    private var listener: ((EngineEvent) -> Unit)? = null

    override suspend fun start(config: VoiceConfig, onEvent: (EngineEvent) -> Unit) = mutex.withLock {
        listener = onEvent
        releaseStream()
        if (!models.isReady()) {
            onEvent(EngineEvent.Failure(error(R.string.voice_error_local_missing)))
            return@withLock
        }
        val loaded = withContext(Dispatchers.IO) { ensureRecognizer() }
        if (loaded == null) {
            onEvent(EngineEvent.Failure(error(R.string.voice_error_local_load)))
            return@withLock
        }
        val created = runCatching { loaded.createStream() }.getOrNull()
        if (created == null) {
            onEvent(EngineEvent.Failure(error(R.string.voice_error_local_load)))
            return@withLock
        }
        val audio = createRecord()
        if (audio == null) {
            created.release()
            onEvent(EngineEvent.Failure(error(R.string.voice_error_recording)))
            return@withLock
        }
        stream = created
        record = audio
        committed = ""
        lastText = ""
        active = true
        onEvent(EngineEvent.Level(0f))
        runCatching { audio.startRecording() }
        if (audio.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            active = false
            releaseStream()
            onEvent(EngineEvent.Failure(error(R.string.voice_error_recording)))
            return@withLock
        }
        loop = scope.launch { readLoop(loaded, created, audio) }
        Unit
    }

    override suspend fun stop(discard: Boolean) = mutex.withLock {
        if (!active && loop == null) return@withLock
        active = false
        runCatching { record?.stop() }
        loop?.join()
        loop = null
        withContext(Dispatchers.IO) {
            val current = stream
            if (!discard && current != null) {
                val recognizer = recognizer
                if (recognizer != null) {
                    runCatching {
                        current.inputFinished()
                        while (recognizer.isReady(current)) recognizer.decode(current)
                        val text = join(committed, recognizer.getResult(current).text)
                        listener?.invoke(EngineEvent.Text(text))
                    }
                }
            }
            releaseStream()
        }
        listener = null
    }

    fun release() {
        active = false
        runCatching { record?.stop() }
        loop?.cancel()
        loop = null
        releaseStream()
        recognizer?.release()
        recognizer = null
    }

    private suspend fun readLoop(
        recognizer: OnlineRecognizer,
        stream: OnlineStream,
        audio: AudioRecord,
    ) {
        val buffer = ShortArray(BUFFER_SIZE)
        val samples = FloatArray(BUFFER_SIZE)
        while (active) {
            val read = runCatching { audio.read(buffer, 0, buffer.size) }.getOrDefault(-1)
            if (read <= 0) {
                if (!active) break
                continue
            }
            for (index in 0 until read) samples[index] = buffer[index] / 32768f
            runCatching {
                stream.acceptWaveform(samples.copyOf(read), SAMPLE_RATE)
                while (recognizer.isReady(stream)) recognizer.decode(stream)
                val text = recognizer.getResult(stream).text
                if (text != lastText) {
                    lastText = text
                    listener?.invoke(EngineEvent.Text(join(committed, text)))
                }
                if (recognizer.isEndpoint(stream)) {
                    committed = join(committed, text)
                    recognizer.reset(stream)
                    lastText = ""
                }
            }
            val now = System.currentTimeMillis()
            if (now - lastLevelAt >= LEVEL_INTERVAL_MS) {
                lastLevelAt = now
                listener?.invoke(EngineEvent.Level(level(buffer, read)))
            }
        }
    }

    private fun ensureRecognizer(): OnlineRecognizer? {
        recognizer?.let { return it }
        if (!models.isReady()) return null
        return runCatching {
            val dir = models.modelDir()
            val config = OnlineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = File(dir, LocalVoiceModel.ENCODER).absolutePath,
                        decoder = File(dir, LocalVoiceModel.DECODER).absolutePath,
                        joiner = File(dir, LocalVoiceModel.JOINER).absolutePath,
                    ),
                    tokens = File(dir, LocalVoiceModel.TOKENS).absolutePath,
                    numThreads = 2,
                    modelType = "",
                ),
                endpointConfig = EndpointConfig(
                    rule1 = EndpointRule(false, 2.4f, 0f),
                    rule2 = EndpointRule(true, 1.2f, 0f),
                    rule3 = EndpointRule(false, 0f, 20f),
                ),
                decodingMethod = "greedy_search",
                enableEndpoint = true,
            )
            OnlineRecognizer(assetManager = null, config = config)
        }.getOrNull()?.also { recognizer = it }
    }

    private fun createRecord(): AudioRecord? {
        val min = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (min <= 0) return null
        val buffer = maxOf(min * 2, BUFFER_SIZE * 2)
        val sources = listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)
        sources.forEach { source ->
            val created = runCatching {
                AudioRecord(source, SAMPLE_RATE, CHANNEL, ENCODING, buffer)
            }.getOrNull()
            if (created != null && created.state == AudioRecord.STATE_INITIALIZED) return created
            created?.release()
        }
        return null
    }

    private fun releaseStream() {
        runCatching { record?.stop() }
        runCatching { record?.release() }
        record = null
        runCatching { stream?.release() }
        stream = null
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

    private fun join(left: String, right: String): String {
        val tail = right.trim()
        if (tail.isEmpty()) return left
        if (left.isBlank()) return tail
        return left + tail
    }

    private fun error(resId: Int): AppError = AppError(
        type = AppErrorType.Generic,
        serverMessage = context.getString(resId),
    )

    private companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val BUFFER_SIZE = 3200
        const val LEVEL_INTERVAL_MS = 100L
    }
}
