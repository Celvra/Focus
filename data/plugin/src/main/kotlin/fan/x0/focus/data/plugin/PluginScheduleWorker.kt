package fan.x0.focus.data.plugin

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.koin.mp.KoinPlatform

class PluginScheduleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        val pluginId = inputData.getString(KEY_PLUGIN_ID).orEmpty()
        val jobId = inputData.getString(KEY_JOB_ID).orEmpty()
        if (pluginId.isBlank() || jobId.isBlank()) return Result.failure()
        KoinPlatform.getKoin().get<PluginRegistry>().runJob(pluginId, jobId)
        Result.success()
    }.getOrElse { Result.retry() }

    companion object {
        const val KEY_PLUGIN_ID = "plugin_id"
        const val KEY_JOB_ID = "job_id"
    }
}
