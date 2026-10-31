package fan.x0.focus.data.assistant

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.koin.core.annotation.Single

enum class SubAgentRunStatus { Running, Done, Failed }

data class SubAgentRunState(
    val id: String,
    val task: String,
    val providerId: String,
    val providerName: String,
    val model: String,
    val status: SubAgentRunStatus = SubAgentRunStatus.Running,
    val entries: List<ChatTurnEntry> = emptyList(),
    val summary: String = "",
    val error: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
)

@Single
class SubAgentRunStore {
    private val _runs = MutableStateFlow<Map<String, SubAgentRunState>>(emptyMap())
    val runs: StateFlow<Map<String, SubAgentRunState>> = _runs.asStateFlow()

    fun start(
        id: String,
        task: String,
        providerId: String,
        providerName: String,
        model: String,
    ) {
        if (id.isBlank()) return
        _runs.update { current ->
            val next = current + (id to SubAgentRunState(
                id = id,
                task = task,
                providerId = providerId,
                providerName = providerName,
                model = model,
            ))
            next.prune()
        }
    }

    fun answer(id: String, text: String, reasoning: Boolean) {
        if (text.isEmpty()) return
        updateEntries(id) { it.appendAnswer(text, reasoning, System.currentTimeMillis()) }
    }

    fun resetAnswer(id: String) {
        updateEntries(id) { it.resetAnswer() }
    }

    fun toolStarted(id: String, callId: String, label: String, tool: String, arguments: String) {
        updateEntries(id) {
            it.startStep(callId, label, tool, arguments, startedAt = System.currentTimeMillis())
        }
    }

    fun toolFinished(
        id: String,
        callId: String,
        label: String,
        ok: Boolean,
        summary: String,
        output: String?,
        durationMs: Long = 0,
        diffs: List<FileDiff> = emptyList(),
        media: List<ChatAttachment> = emptyList(),
    ) {
        updateEntries(id) { it.finishStep(callId, label, ok, summary, output, durationMs, diffs, media) }
    }

    fun finish(id: String, summary: String, error: String?) {
        _runs.update { current ->
            val run = current[id] ?: return@update current
            if (run.status != SubAgentRunStatus.Running) return@update current
            val finishedAt = System.currentTimeMillis()
            current + (id to run.copy(
                status = if (error == null) SubAgentRunStatus.Done else SubAgentRunStatus.Failed,
                summary = summary,
                error = error,
                entries = run.entries.stopReasoning(finishedAt),
                finishedAt = finishedAt,
            ))
        }
    }

    fun clear() {
        _runs.value = emptyMap()
    }

    private fun updateEntries(id: String, reduce: (List<ChatTurnEntry>) -> List<ChatTurnEntry>) {
        _runs.update { current ->
            val run = current[id] ?: return@update current
            current + (id to run.copy(entries = reduce(run.entries)))
        }
    }

    private fun Map<String, SubAgentRunState>.prune(): Map<String, SubAgentRunState> {
        if (size <= MAX_RUNS) return this
        val removable = values
            .filter { it.status != SubAgentRunStatus.Running }
            .sortedBy { it.finishedAt ?: it.startedAt }
            .take(size - MAX_RUNS)
            .map { it.id }
        return if (removable.isEmpty()) this else this - removable.toSet()
    }

    companion object {
        private const val MAX_RUNS = 12
    }
}
