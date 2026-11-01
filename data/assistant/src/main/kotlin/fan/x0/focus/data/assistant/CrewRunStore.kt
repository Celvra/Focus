package fan.x0.focus.data.assistant

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.koin.core.annotation.Single
import java.util.concurrent.atomic.AtomicLong

enum class CrewToolStatus { Running, Done, Failed }

enum class CrewApprovalPhase { Waiting, Running, Finished, Declined }

data class CrewToolTrace(
    val id: String,
    val label: String,
    val status: CrewToolStatus,
    val summary: String = "",
    val arguments: String = "",
    val detail: String = "",
)

data class CrewApprovalCard(
    val id: String,
    val title: String,
    val summary: String,
    val steps: List<String>,
    val phase: CrewApprovalPhase,
)

sealed interface CrewRunEntry {
    val key: String

    data class User(
        override val key: String,
        val messageId: Long,
        val text: String,
        val replyToId: Long?,
    ) : CrewRunEntry

    data class Agent(
        override val key: String,
        val member: CrewMember,
        val messageId: Long? = null,
        val text: String = "",
        val activity: String = "",
        val tools: List<CrewToolTrace> = emptyList(),
        val streaming: Boolean = true,
        val error: String? = null,
    ) : CrewRunEntry

    data class Approval(
        override val key: String,
        val card: CrewApprovalCard,
    ) : CrewRunEntry

    data class Notice(
        override val key: String,
        val text: String,
    ) : CrewRunEntry
}

data class CrewRunState(
    val groupId: String,
    val running: Boolean = false,
    val working: String = "",
    val speaker: CrewMember? = null,
    val entries: List<CrewRunEntry> = emptyList(),
    val error: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
)

@Single
class CrewRunStore {
    private val _runs = MutableStateFlow<Map<String, CrewRunState>>(emptyMap())
    val runs: StateFlow<Map<String, CrewRunState>> = _runs.asStateFlow()

    private val nextKey = AtomicLong()

    fun start(groupId: String) {
        val key = System.currentTimeMillis()
        _runs.update { current ->
            (current + (groupId to CrewRunState(
                groupId = groupId,
                running = true,
                startedAt = key,
            ))).prune()
        }
    }

    fun addUserMessage(groupId: String, message: CrewMessage) {
        val entry = CrewRunEntry.User(
            key = "user-${message.id}",
            messageId = message.id,
            text = message.content,
            replyToId = message.replyToId,
        )
        update(groupId) { state ->
            if (state.entries.any { it is CrewRunEntry.User && it.messageId == message.id }) {
                state
            } else {
                state.copy(entries = state.entries + entry)
            }
        }
    }

    fun notice(groupId: String, text: String) {
        if (text.isBlank()) return
        val entry = CrewRunEntry.Notice(
            key = "notice-${nextKey.incrementAndGet()}",
            text = text,
        )
        update(groupId) { it.copy(entries = it.entries + entry) }
    }

    fun setSpeaker(groupId: String, member: CrewMember) {
        update(groupId) {
            it.copy(
                speaker = member,
                working = member.name,
            )
        }
    }

    fun startAgent(groupId: String, member: CrewMember): String {
        val key = "agent-${member.id}-${nextKey.incrementAndGet()}"
        val entry = CrewRunEntry.Agent(key = key, member = member)
        update(groupId) {
            it.copy(
                speaker = member,
                working = member.name,
                entries = it.entries + entry,
            )
        }
        return key
    }

    fun markPersisted(groupId: String, key: String, messageId: Long) {
        updateAgent(groupId, key) { it.copy(messageId = messageId) }
    }

    fun appendAgentText(groupId: String, key: String, text: String) {
        if (text.isEmpty()) return
        updateAgent(groupId, key) { it.copy(text = it.text + text, activity = "") }
    }

    fun resetAgentText(groupId: String, key: String, text: String) {
        updateAgent(groupId, key) { it.copy(text = text, activity = "") }
    }

    fun removeAgentText(groupId: String, key: String, length: Int) {
        if (length <= 0) return
        updateAgent(groupId, key) {
            val text = if (length >= it.text.length) "" else it.text.dropLast(length)
            it.copy(text = text)
        }
    }

    fun setAgentActivity(groupId: String, key: String, text: String) {
        updateAgent(groupId, key) { it.copy(activity = text.take(120)) }
    }

    fun startTool(groupId: String, key: String, id: String, label: String, arguments: String = "") {
        updateAgent(groupId, key) {
            it.copy(
                tools = it.tools + CrewToolTrace(
                    id = id,
                    label = label,
                    status = CrewToolStatus.Running,
                    arguments = arguments,
                ),
            )
        }
    }

    fun finishTool(
        groupId: String,
        key: String,
        id: String,
        label: String,
        ok: Boolean,
        summary: String,
        arguments: String = "",
        detail: String = "",
    ) {
        updateAgent(groupId, key) { agent ->
            val trace = CrewToolTrace(
                id = id,
                label = label,
                status = if (ok) CrewToolStatus.Done else CrewToolStatus.Failed,
                summary = summary.take(200),
                arguments = arguments,
                detail = detail,
            )
            agent.copy(tools = agent.tools.filterNot { it.id == id } + trace)
        }
    }

    fun approvalRequested(groupId: String, request: ApprovalRequest) {
        val card = CrewApprovalCard(
            id = request.id,
            title = request.title,
            summary = request.summary,
            steps = request.steps.map { it.label },
            phase = CrewApprovalPhase.Waiting,
        )
        val entry = CrewRunEntry.Approval("approval-${request.id}", card)
        update(groupId) { it.copy(entries = it.entries + entry) }
    }

    fun approvalResolved(groupId: String, id: String, approved: Boolean) {
        update(groupId) { state ->
            state.copy(
                entries = state.entries.map { entry ->
                    if (entry is CrewRunEntry.Approval && entry.card.id == id) {
                        entry.copy(
                            card = entry.card.copy(
                                phase = if (approved) CrewApprovalPhase.Running else CrewApprovalPhase.Declined,
                            ),
                        )
                    } else {
                        entry
                    }
                },
            )
        }
    }

    fun approvalFinished(groupId: String, id: String) {
        update(groupId) { state ->
            state.copy(
                entries = state.entries.map { entry ->
                    if (entry is CrewRunEntry.Approval && entry.card.id == id) {
                        entry.copy(card = entry.card.copy(phase = CrewApprovalPhase.Finished))
                    } else {
                        entry
                    }
                },
            )
        }
    }

    fun finishAgent(groupId: String, key: String, error: String? = null) {
        updateAgent(groupId, key) { it.copy(streaming = false, error = error) }
    }

    fun finish(groupId: String, error: String? = null) {
        _runs.update { current ->
            val run = current[groupId] ?: return@update current
            current + (groupId to run.copy(
                running = false,
                working = "",
                speaker = null,
                entries = run.entries.map { entry ->
                    if (entry is CrewRunEntry.Agent) entry.copy(streaming = false) else entry
                },
                error = error,
                finishedAt = System.currentTimeMillis(),
            ))
        }
    }

    fun remove(groupId: String) {
        _runs.update { it - groupId }
    }

    fun clear() {
        _runs.value = emptyMap()
    }

    private fun update(groupId: String, reduce: (CrewRunState) -> CrewRunState) {
        _runs.update { current ->
            val run = current[groupId] ?: return@update current
            current + (groupId to reduce(run))
        }
    }

    private fun updateAgent(groupId: String, key: String, reduce: (CrewRunEntry.Agent) -> CrewRunEntry.Agent) {
        update(groupId) { state ->
            state.copy(
                entries = state.entries.map { entry ->
                    if (entry is CrewRunEntry.Agent && entry.key == key) reduce(entry) else entry
                },
            )
        }
    }

    private fun Map<String, CrewRunState>.prune(): Map<String, CrewRunState> {
        if (size <= MAX_RUNS) return this
        val removable = values
            .filterNot { it.running }
            .sortedBy { it.finishedAt ?: it.startedAt }
            .take(size - MAX_RUNS)
            .map { it.groupId }
        return if (removable.isEmpty()) this else this - removable.toSet()
    }

    companion object {
        private const val MAX_RUNS = 12
    }
}
