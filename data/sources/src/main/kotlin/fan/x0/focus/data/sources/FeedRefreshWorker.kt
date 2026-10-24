package fan.x0.focus.data.sources

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import org.koin.mp.KoinPlatform
import java.util.concurrent.TimeUnit

class FeedRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        KoinPlatform.getKoin().get<FeedRepository>().refreshAll()
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.retry()
    }
}

@Single
class FeedRefreshScheduler(@Provided private val context: Context) {
    fun schedule(hours: Int) {
        val request = PeriodicWorkRequestBuilder<FeedRefreshWorker>(hours.coerceIn(1, 24).toLong(), TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    companion object {
        private const val WORK_NAME = "feed-refresh"
    }
}
