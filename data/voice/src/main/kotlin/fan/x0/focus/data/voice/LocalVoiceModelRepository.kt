package fan.x0.focus.data.voice

import android.content.Context
import fan.x0.focus.core.error.AppError
import fan.x0.focus.core.error.toAppError
import fan.x0.focus.data.assistant.ApkDownloader
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

sealed interface LocalModelState {
    data object Missing : LocalModelState
    data class Downloading(val downloaded: Long, val total: Long) : LocalModelState
    data object Extracting : LocalModelState
    data object Ready : LocalModelState
    data class Failed(val error: AppError) : LocalModelState
}

@Single
class LocalVoiceModelRepository(
    @Provided private val context: Context,
    private val downloader: ApkDownloader,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<LocalModelState>(LocalModelState.Missing)
    val state: StateFlow<LocalModelState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        if (job?.isActive == true) return
        _state.value = if (modelReady()) LocalModelState.Ready else LocalModelState.Missing
    }

    fun download() {
        if (job?.isActive == true) return
        if (modelReady()) {
            _state.value = LocalModelState.Ready
            return
        }
        job = scope.launch {
            try {
                val archive = archiveFile()
                if (archive.length() != LocalVoiceModel.ARCHIVE_BYTES) archive.delete()
                _state.value = LocalModelState.Downloading(archive.length(), LocalVoiceModel.ARCHIVE_BYTES)
                downloader.download(
                    url = LocalVoiceModel.ARCHIVE_URL,
                    target = archive,
                    expectedSize = LocalVoiceModel.ARCHIVE_BYTES,
                    multiThreaded = false,
                ) { downloaded, total ->
                    _state.value = LocalModelState.Downloading(downloaded, total)
                }
                _state.value = LocalModelState.Extracting
                extract(archive)
                archive.delete()
                if (!modelReady()) throw IOException("model files are incomplete")
                _state.value = LocalModelState.Ready
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.value = LocalModelState.Failed(e.toAppError())
            }
        }
    }

    fun delete() {
        job?.cancel()
        job = null
        scope.launch {
            modelDir().deleteRecursively()
            downloadDir().deleteRecursively()
            _state.value = LocalModelState.Missing
        }
    }

    fun isReady(): Boolean = modelReady()

    fun modelDir(): File = File(context.filesDir, "voice/models/${LocalVoiceModel.DIR}")

    private fun downloadDir(): File = File(context.filesDir, "voice/download")

    private fun archiveFile(): File = File(downloadDir(), LocalVoiceModel.ARCHIVE_NAME)

    private fun modelReady(): Boolean = LocalVoiceModel.files.all { file ->
        val target = File(modelDir(), file.name)
        target.isFile && target.length() == file.bytes
    }

    private suspend fun extract(archive: File) = withContext(Dispatchers.IO) {
        val dir = modelDir()
        if (!dir.exists() && !dir.mkdirs()) throw IOException("cannot create model directory")
        BZip2CompressorInputStream(BufferedInputStream(archive.inputStream())).use { bzip ->
            TarArchiveInputStream(bzip).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    val name = entry.name.substringAfterLast('/')
                    val target = LocalVoiceModel.files.firstOrNull { it.name == name }
                    if (entry.isFile && target != null) {
                        File(dir, target.name).outputStream().use { output -> tar.copyTo(output) }
                    }
                    entry = tar.nextEntry
                }
            }
        }
    }
}
