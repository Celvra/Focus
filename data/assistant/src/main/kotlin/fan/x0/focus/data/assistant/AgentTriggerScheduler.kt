package fan.x0.focus.data.assistant

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import org.koin.mp.KoinPlatform
import java.util.concurrent.TimeUnit

class AgentTriggerWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.success()
        return try {
            KoinPlatform.getKoin().get<AgentTriggerScheduler>().fireById(id)
            Result.success()
        } catch (e: Throwable) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_ID = "agent_trigger_id"
    }
}

@Single
class AgentTriggerScheduler(
    @Provided private val context: Context,
    private val store: AgentTriggerStore,
    private val bus: AgentEventBus,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            runCatching { syncAll() }
            while (true) {
                delay(SCAN_MS)
                runCatching { fireDue() }
            }
        }
    }

    suspend fun syncAll() {
        store.list().forEach { trigger -> sync(trigger) }
    }

    suspend fun sync(trigger: AgentTrigger) {
        if (trigger.kind != AgentTriggerKind.Cron || !trigger.enabled) {
            WorkManager.getInstance(context).cancelUniqueWork(workName(trigger.id))
            return
        }
        val now = System.currentTimeMillis()
        val next = when {
            trigger.nextRun <= now -> store.nextRunFor(trigger)
            else -> trigger.nextRun
        }
        val updated = if (next != trigger.nextRun) store.reschedule(trigger) else trigger
        schedule(updated)
    }

    suspend fun fireById(id: String) {
        val trigger = store.trigger(id) ?: return
        if (trigger.kind != AgentTriggerKind.Cron || !trigger.enabled) return
        val now = System.currentTimeMillis()
        if (trigger.nextRun <= 0) {
            sync(trigger)
            return
        }
        if (trigger.nextRun > now) {
            schedule(trigger)
            return
        }
        val next = store.nextRunFor(trigger)
        if (!store.claim(trigger, "fired", next)) return
        bus.publish(
            AgentEvent.scheduled(
                triggerId = trigger.id,
                name = trigger.name,
                prompt = trigger.prompt,
                profile = trigger.profile,
                runAt = trigger.nextRun,
            ),
        )
        sync(trigger.copy(nextRun = next))
    }

    suspend fun fireDue() {
        store.due().forEach { trigger ->
            val next = store.nextRunFor(trigger)
            if (!store.claim(trigger, "fired", next)) return@forEach
            bus.publish(
                AgentEvent.scheduled(
                    triggerId = trigger.id,
                    name = trigger.name,
                    prompt = trigger.prompt,
                    profile = trigger.profile,
                    runAt = trigger.nextRun,
                ),
            )
            sync(trigger.copy(nextRun = next))
        }
    }

    fun cancel(triggerId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(triggerId))
    }

    private fun schedule(trigger: AgentTrigger) {
        if (trigger.nextRun <= 0) return
        val delay = (trigger.nextRun - System.currentTimeMillis()).coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<AgentTriggerWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(AgentTriggerWorker.KEY_ID to trigger.id))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(workName(trigger.id), ExistingWorkPolicy.REPLACE, request)
    }

    private fun workName(id: String): String = "agent-trigger-$id"

    private companion object {
        const val SCAN_MS = 30_000L
    }
}
