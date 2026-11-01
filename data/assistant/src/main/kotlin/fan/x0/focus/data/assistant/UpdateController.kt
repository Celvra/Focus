package fan.x0.focus.data.assistant

import android.content.Context
import fan.x0.focus.core.error.AppError
import fan.x0.focus.core.error.toAppError
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

data class UpdateUiState(
    val currentVersion: String = "",
    val checking: Boolean = false,
    val checked: Boolean = false,
    val release: ReleaseInfo? = null,
    val updateAvailable: Boolean = false,
    val sheetVisible: Boolean = false,
    val error: AppError? = null,
)

sealed interface DownloadUiState {
    data object Idle : DownloadUiState

    data class Running(
        val assetName: String,
        val downloaded: Long,
        val total: Long,
    ) : DownloadUiState {
        val progress: Float
            get() = if (total <= 0) 0f else (downloaded.toFloat() / total).coerceIn(0f, 1f)
    }

    data class Completed(
        val assetName: String,
        val filePath: String,
    ) : DownloadUiState

    data class Failed(
        val assetName: String,
        val error: AppError,
    ) : DownloadUiState
}

@Single
class UpdateController(
    @Provided private val context: Context,
    private val releases: ReleaseRepository,
    private val downloader: ApkDownloader,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val currentVersion = installedVersion()
    private val _state = MutableStateFlow(UpdateUiState(currentVersion = currentVersion))
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()
    private val _download = MutableStateFlow<DownloadUiState>(DownloadUiState.Idle)
    val download: StateFlow<DownloadUiState> = _download.asStateFlow()
    private var checkJob: Job? = null
    private var downloadJob: Job? = null

    fun checkInBackground() {
        val lastCheck = preferences.getLong(KEY_LAST_CHECK, 0L)
        if (System.currentTimeMillis() - lastCheck < CHECK_INTERVAL_MS) return
        check(userInitiated = false)
    }

    fun check(userInitiated: Boolean) {
        _state.update {
            it.copy(
                sheetVisible = it.sheetVisible || userInitiated,
                checking = true,
                error = null,
            )
        }
        if (checkJob?.isActive == true) return
        checkJob = scope.launch {
            try {
                val release = releases.latest()
                val update = release != null && AppVersion.isNewer(release.tagName, currentVersion)
                preferences.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
                val prompted = preferences.getString(KEY_PROMPTED, null)
                val show = if (userInitiated) true else update && prompted != release.tagName
                if (update && show) {
                    preferences.edit().putString(KEY_PROMPTED, release.tagName).apply()
                }
                _state.update {
                    it.copy(
                        checking = false,
                        checked = true,
                        release = release,
                        updateAvailable = update,
                        error = null,
                        sheetVisible = it.sheetVisible || show,
                    )
                }
            } catch (e: CancellationException) {
                _state.update { it.copy(checking = false) }
                throw e
            } catch (e: Throwable) {
                _state.update {
                    it.copy(
                        checking = false,
                        error = e.toAppError(),
                        sheetVisible = it.sheetVisible || userInitiated,
                    )
                }
            }
        }
    }

    fun dismiss() {
        _state.update { it.copy(sheetVisible = false) }
    }

    fun download(asset: ReleaseAsset) {
        if (downloadJob?.isActive == true) return
        downloadJob = scope.launch {
            _download.value = DownloadUiState.Running(
                assetName = asset.name,
                downloaded = 0,
                total = asset.size,
            )
            try {
                val target = File(updateDirectory(), safeName(asset.name))
                var lastProgressAt = 0L
                val file = downloader.download(
                    url = asset.url,
                    target = target,
                    expectedSize = asset.size,
                ) { downloaded, total ->
                    val now = System.currentTimeMillis()
                    if (downloaded == total || now - lastProgressAt >= PROGRESS_INTERVAL_MS) {
                        lastProgressAt = now
                        _download.value = DownloadUiState.Running(
                            assetName = asset.name,
                            downloaded = downloaded,
                            total = total,
                        )
                    }
                }
                _download.value = DownloadUiState.Completed(asset.name, file.absolutePath)
            } catch (e: CancellationException) {
                _download.value = DownloadUiState.Idle
                throw e
            } catch (e: Throwable) {
                _download.value = DownloadUiState.Failed(asset.name, e.toAppError())
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
    }

    private fun updateDirectory(): File {
        val directory = File(context.cacheDir, "updates")
        if (!directory.exists()) directory.mkdirs()
        return directory
    }

    private fun safeName(name: String): String {
        val base = name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
        return base.ifBlank { "focus.apk" }
    }

    private fun installedVersion(): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName.orEmpty()
    }.getOrDefault("")

    private companion object {
        const val PREFERENCES = "focus_updates"
        const val KEY_LAST_CHECK = "last_check"
        const val KEY_PROMPTED = "prompted_version"
        const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
        const val PROGRESS_INTERVAL_MS = 100L
    }
}

internal object AppVersion {
    fun isNewer(candidate: String, current: String): Boolean {
        val next = parse(candidate)
        val installed = parse(current)
        if (next == null || installed == null) {
            return candidate.trim().removePrefix("v") != current.trim().removePrefix("v")
        }
        repeat(max(next.size, installed.size)) { index ->
            val left = next.getOrElse(index) { 0 }
            val right = installed.getOrElse(index) { 0 }
            if (left != right) return left > right
        }
        return false
    }

    private fun parse(value: String): List<Int>? {
        val core = value.trim().removePrefix("v").substringBefore('-').substringBefore('+')
        if (core.isBlank()) return null
        return core.split('.').map { it.toIntOrNull() ?: return null }
    }
}
