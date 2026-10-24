package fan.x0.focus.data.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import org.koin.mp.KoinPlatform
import java.util.concurrent.TimeUnit

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val text = inputData.getString(KEY_TEXT) ?: return Result.success()
        post(text)
        val id = inputData.getString(KEY_ID)
        if (id != null) {
            try {
                KoinPlatform.getKoin().get<ReminderStore>().markDone(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The notification already fired; a missing store must not retry.
            }
        }
        return Result.success()
    }

    private fun post(text: String) {
        val manager = applicationContext.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val notification = Notification.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Focus")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        manager.notify(text.hashCode(), notification)
    }

    companion object {
        const val CHANNEL_ID = "focus_reminders"
        private const val KEY_ID = "reminder_id"
        private const val KEY_TEXT = "reminder_text"
    }
}

@Single
class ReminderScheduler(@Provided private val context: Context) {
    fun schedule(id: String, text: String, at: Long) {
        val delay = (at - System.currentTimeMillis()).coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("reminder_id" to id, "reminder_text" to text))
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("$TAG-$id", ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        private const val TAG = "focus-reminder"
    }
}
