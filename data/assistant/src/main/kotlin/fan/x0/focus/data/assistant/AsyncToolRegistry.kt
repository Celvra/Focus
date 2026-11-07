package fan.x0.focus.data.assistant

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.koin.core.annotation.Single

data class AsyncOperation(
    val id: String,
    val conversationId: String,
    val tool: String,
    val label: String,
    val startedAt: Long,
)

@Single
class AsyncToolRegistry {
    private val operations = MutableStateFlow<Map<String, AsyncOperation>>(emptyMap())
    private val jobs = mutableMapOf<String, Job>()

    val running: StateFlow<Map<String, AsyncOperation>> = operations.asStateFlow()

    fun track(
        conversationId: String,
        id: String,
        tool: String,
        label: String,
        job: Job,
    ) {
        synchronized(jobs) {
            if (jobs.containsKey(id)) return
            jobs[id] = job
            operations.update {
                it + (id to AsyncOperation(id, conversationId, tool, label, System.currentTimeMillis()))
            }
        }
        job.invokeOnCompletion {
            synchronized(jobs) { jobs.remove(id) }
            operations.update { it - id }
        }
    }

    fun list(conversationId: String = ""): List<AsyncOperation> {
        val all = operations.value.values.toList()
        return if (conversationId.isBlank()) all else all.filter { it.conversationId == conversationId }
    }

    fun stop(id: String, conversationId: String = ""): Boolean {
        val job = synchronized(jobs) {
            val operation = operations.value[id] ?: return false
            if (conversationId.isNotBlank() && operation.conversationId != conversationId) return false
            jobs[id]
        } ?: return false
        job.cancel()
        return true
    }
}
