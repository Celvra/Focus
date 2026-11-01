package fan.x0.focus.data.assistant

import fan.x0.focus.core.notify.MessageNotifier
import fan.x0.focus.core.notify.NotifyActionKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap

data class CrewReactionUpdate(
    val groupId: String,
    val messageId: Long,
    val emoji: String,
)

@Single
class CrewRunner(
    private val store: CrewStore,
    private val runs: CrewRunStore,
    private val engine: CrewEngine,
    private val launches: CrewLaunchBus,
    private val background: BackgroundCommandMonitor,
    private val notifier: MessageNotifier,
    private val keepAlive: ChatTurnKeepAlive,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val discarded = ConcurrentHashMap.newKeySet<String>()
    private val restart = ConcurrentHashMap.newKeySet<String>()

    private val _reactionUpdates = MutableSharedFlow<CrewReactionUpdate>(extraBufferCapacity = 32)
    val reactionUpdates: SharedFlow<CrewReactionUpdate> = _reactionUpdates.asSharedFlow()

    val states: StateFlow<Map<String, CrewRunState>> = runs.runs

    init {
        scope.launch {
            launches.requests.collect { request ->
                if (!isRunning(request.groupId)) start(request.groupId, request.focusMemberIds)
            }
        }
        scope.launch {
            store.deleted.collect { groupId -> discard(groupId) }
        }
        scope.launch {
            background.completed.collect { completion ->
                val group = store.group(completion.conversationId) ?: return@collect
                val content = buildString {
                    append(completion.summary)
                    if (completion.logs.isNotBlank()) {
                        append("\n\nLogs:\n")
                        append(completion.logs)
                    }
                }
                append(
                    groupId = group.id,
                    authorType = CrewAuthorType.System,
                    authorId = SYSTEM_ID,
                    authorName = SYSTEM_NAME,
                    content = content,
                    kind = CrewMessageKind.Notice,
                )
                if (isRunning(group.id)) {
                    runs.notice(group.id, content.take(400))
                } else {
                    start(group.id)
                }
            }
        }
    }

    fun isRunning(groupId: String): Boolean = jobs[groupId]?.isActive == true

    suspend fun send(
        groupId: String,
        text: String,
        replyToId: Long? = null,
    ): Boolean {
        val content = text.trim()
        if (content.isBlank() || groupId in discarded) return false
        val group = store.group(groupId) ?: return false
        if (group.archived) store.setArchived(groupId, false)
        val members = store.members(groupId)
        val message = append(
            groupId = groupId,
            authorType = CrewAuthorType.User,
            authorId = USER_ID,
            authorName = USER_NAME,
            content = content,
            replyToId = replyToId,
        ) ?: return false
        if (isRunning(groupId)) {
            runs.addUserMessage(groupId, message)
            if (jobs[groupId]?.isCancelled == true) restart.add(groupId)
            return false
        }
        start(groupId, CrewMentions.ids(content, members).toSet())
        return true
    }

    fun start(groupId: String, focusMemberIds: Set<String> = emptySet()) {
        if (groupId.isBlank() || groupId in discarded) return
        if (isRunning(groupId)) return
        keepAlive.start()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            runGroup(groupId, focusMemberIds)
        }
        jobs[groupId] = job
        job.start()
    }

    fun stop(groupId: String) {
        jobs[groupId]?.cancel()
    }

    fun discard(groupId: String) {
        discarded.add(groupId)
        jobs[groupId]?.cancel()
        runs.remove(groupId)
    }

    fun decide(approvalId: String, approved: Boolean) {
        engine.decide(approvalId, approved)
    }

    suspend fun announce(groupId: String, text: String) {
        if (text.isBlank()) return
        append(
            groupId = groupId,
            authorType = CrewAuthorType.System,
            authorId = SYSTEM_ID,
            authorName = SYSTEM_NAME,
            content = text,
            kind = CrewMessageKind.Notice,
        ) ?: return
        if (isRunning(groupId)) runs.notice(groupId, text)
    }

    private suspend fun append(
        groupId: String,
        authorType: CrewAuthorType,
        authorId: String,
        authorName: String,
        content: String,
        replyToId: Long? = null,
        kind: CrewMessageKind = CrewMessageKind.Text,
    ): CrewMessage? {
        if (groupId in discarded) return null
        return store.appendMessage(
            groupId = groupId,
            authorType = authorType,
            authorId = authorId,
            authorName = authorName,
            content = content,
            replyToId = replyToId,
            kind = kind,
        )
    }

    private suspend fun runGroup(groupId: String, focusMemberIds: Set<String>) {
        val group = store.group(groupId) ?: run {
            jobs.remove(groupId)
            return
        }
        runs.start(groupId)
        var failed = false
        var stopped = false
        var lastPreview = ""
        var checkedUserMessageId = store.messages(groupId, 200).lastOrNull()?.id ?: 0
        val liveUsers = mutableSetOf<Long>()
        val spoken = mutableMapOf<String, Int>()
        val queue = ArrayDeque<String>()
        var activeMember: CrewMember? = null
        var activeText = ""
        var activePersisted = false
        var turns = 0
        try {
            var members = store.members(groupId)
            if (members.isEmpty()) {
                append(
                    groupId = groupId,
                    authorType = CrewAuthorType.System,
                    authorId = SYSTEM_ID,
                    authorName = SYSTEM_NAME,
                    content = "The group has no active members.",
                    kind = CrewMessageKind.Notice,
                )
                runs.notice(groupId, "The group has no active members.")
                runs.finish(groupId)
                return
            }
            val focus = focusMemberIds.filter { id -> members.any { it.id == id } }
            if (focus.isNotEmpty()) {
                focus.take(MAX_SPEAKERS).forEach { queue.addLast(it) }
            } else {
                members.take(MAX_SPEAKERS).forEach { queue.addLast(it.id) }
            }
            while (turns < MAX_SPEAKERS) {
                members = store.members(groupId)
                if (members.isEmpty()) break
                val fresh = store.messagesAfter(groupId, checkedUserMessageId)
                if (fresh.isNotEmpty()) {
                    checkedUserMessageId = fresh.last().id
                    fresh.filter { it.authorType == CrewAuthorType.User }.forEach { message ->
                        if (liveUsers.add(message.id)) runs.addUserMessage(groupId, message)
                        val mentions = CrewMentions.ids(message.content, members)
                        if (mentions.isEmpty()) {
                            members.firstOrNull { (spoken[it.id] ?: 0) < MAX_TURNS_PER_MEMBER }
                                ?.let { responder -> queue.addFirst(responder.id) }
                        } else {
                            mentions.reversed().forEach { mentioned ->
                                if ((spoken[mentioned] ?: 0) < MAX_TURNS_PER_MEMBER) queue.addFirst(mentioned)
                            }
                        }
                    }
                }
                val nextId = nextSpeaker(queue, members, spoken, focus.isEmpty(), turns)
                    ?: break
                val member = members.firstOrNull { it.id == nextId } ?: continue
                spoken[member.id] = (spoken[member.id] ?: 0) + 1
                turns++
                activeMember = member
                activeText = ""
                activePersisted = false
                runs.setSpeaker(groupId, member)
                val key = runs.startAgent(groupId, member)
                var handoff: String? = null
                var finishDiscussion = false
                var speechError: String? = null
                engine.speak(group, member, members).collect { event ->
                    when (event) {
                        is CrewSpeechEvent.Delta -> {
                            activeText += event.text
                            runs.appendAgentText(groupId, key, event.text)
                        }
                        is CrewSpeechEvent.TextReset -> {
                            activeText = event.text
                            runs.resetAgentText(groupId, key, event.text)
                        }
                        is CrewSpeechEvent.Activity -> runs.setAgentActivity(groupId, key, event.text)
                        is CrewSpeechEvent.Retry -> runs.setAgentActivity(
                            groupId,
                            key,
                            "Retry ${event.attempt}/${event.max}: ${event.reason}",
                        )
                        is CrewSpeechEvent.ToolStarted -> runs.startTool(groupId, key, event.id, event.label)
                        is CrewSpeechEvent.ToolFinished -> runs.finishTool(
                            groupId,
                            key,
                            event.id,
                            event.label,
                            event.ok,
                            event.summary,
                        )
                        is CrewSpeechEvent.ApprovalRequested -> {
                            runs.approvalRequested(groupId, event.request)
                            notifier.notifyAction(
                                conversationId = conversationId(groupId),
                                title = group.name,
                                kind = NotifyActionKind.Approval,
                            )
                        }
                        is CrewSpeechEvent.ApprovalResolved -> {
                            runs.approvalResolved(groupId, event.request.id, event.approved)
                            notifier.cancel(conversationId(groupId))
                        }
                        is CrewSpeechEvent.ApprovalFinished -> runs.approvalFinished(groupId, event.id)
                        is CrewSpeechEvent.ReactionAdded -> _reactionUpdates.emit(
                            CrewReactionUpdate(groupId, event.messageId, event.emoji),
                        )
                        is CrewSpeechEvent.Handoff -> handoff = event.memberId
                        is CrewSpeechEvent.Completed -> {
                            activeText = event.speech.text
                            runs.resetAgentText(groupId, key, event.speech.text)
                            handoff = event.speech.handoffMemberId ?: handoff
                            finishDiscussion = event.speech.finish
                            speechError = event.speech.error
                            if (speechError != null) {
                                failed = true
                                runs.notice(groupId, "Stopped: $speechError")
                            }
                        }
                    }
                }
                runs.finishAgent(groupId, key, speechError)
                val text = activeText.trim()
                if (text.isNotBlank()) {
                    val stored = append(
                        groupId = groupId,
                        authorType = CrewAuthorType.Agent,
                        authorId = member.id,
                        authorName = member.name,
                        content = text,
                    )
                    if (stored != null) {
                        lastPreview = text
                        activePersisted = true
                        runs.markPersisted(groupId, key, stored.id)
                    }
                }
                if (speechError != null) {
                    append(
                        groupId = groupId,
                        authorType = CrewAuthorType.System,
                        authorId = SYSTEM_ID,
                        authorName = SYSTEM_NAME,
                        content = "Stopped: $speechError",
                        kind = CrewMessageKind.Notice,
                    )
                }
                if (failed) break
                if (finishDiscussion) break
                val current = store.members(groupId)
                val nextHandoff = handoff
                if (nextHandoff != null) {
                    if ((spoken[nextHandoff] ?: 0) < MAX_TURNS_PER_MEMBER) queue.addFirst(nextHandoff)
                } else {
                    CrewMentions.ids(text, current)
                        .filter { it != member.id }
                        .reversed()
                        .forEach { id ->
                            if ((spoken[id] ?: 0) < MAX_TURNS_PER_MEMBER) queue.addFirst(id)
                        }
                }
                current
                    .filter { (spoken[it.id] ?: 0) == 0 && it.id !in queue }
                    .forEach { queue.addLast(it.id) }
                if (turns < MAX_SPEAKERS) delay(SPEAKER_PAUSE_MS)
            }
            if (turns == 0) {
                append(
                    groupId = groupId,
                    authorType = CrewAuthorType.System,
                    authorId = SYSTEM_ID,
                    authorName = SYSTEM_NAME,
                    content = "No member was available to speak.",
                    kind = CrewMessageKind.Notice,
                )
                runs.notice(groupId, "No member was available to speak.")
            }
            runs.finish(groupId)
        } catch (e: CancellationException) {
            stopped = true
            withContext(NonCancellable) {
                val member = activeMember
                val text = activeText.trim()
                if (!activePersisted && member != null && text.isNotBlank()) {
                    append(
                        groupId = groupId,
                        authorType = CrewAuthorType.Agent,
                        authorId = member.id,
                        authorName = member.name,
                        content = text,
                    )
                }
                append(
                    groupId = groupId,
                    authorType = CrewAuthorType.System,
                    authorId = SYSTEM_ID,
                    authorName = SYSTEM_NAME,
                    content = "Discussion stopped.",
                    kind = CrewMessageKind.Notice,
                )
                runs.notice(groupId, "Discussion stopped.")
                runs.finish(groupId)
            }
            throw e
        } catch (e: Throwable) {
            failed = true
            val message = e.message ?: e.javaClass.simpleName
            withContext(NonCancellable) {
                append(
                    groupId = groupId,
                    authorType = CrewAuthorType.System,
                    authorId = SYSTEM_ID,
                    authorName = SYSTEM_NAME,
                    content = "Stopped: $message",
                    kind = CrewMessageKind.Notice,
                )
                runs.notice(groupId, "Stopped: $message")
                runs.finish(groupId, message)
            }
        } finally {
            jobs.remove(groupId)
            if (restart.remove(groupId) && groupId !in discarded) {
                start(groupId)
            }
            if (!stopped && groupId !in discarded) {
                withContext(NonCancellable) {
                    notifier.notifyReply(
                        conversationId = conversationId(groupId),
                        title = group.name,
                        preview = lastPreview.take(160),
                        failed = failed,
                    )
                }
            }
        }
    }

    private fun nextSpeaker(
        queue: ArrayDeque<String>,
        members: List<CrewMember>,
        spoken: Map<String, Int>,
        fillEveryone: Boolean,
        turns: Int,
    ): String? {
        while (queue.isNotEmpty()) {
            val candidate = queue.removeFirst()
            if (members.none { it.id == candidate }) continue
            if ((spoken[candidate] ?: 0) >= MAX_TURNS_PER_MEMBER) continue
            return candidate
        }
        if (!fillEveryone) return null
        return members.firstOrNull {
            (spoken[it.id] ?: 0) == 0 && turns < MAX_SPEAKERS
        }?.id
    }

    private fun conversationId(groupId: String): String = "crew:$groupId"

    companion object {
        private const val USER_ID = "user"
        private const val USER_NAME = "You"
        private const val SYSTEM_ID = "system"
        private const val SYSTEM_NAME = "Focus"
        private const val MAX_SPEAKERS = 10
        private const val MAX_TURNS_PER_MEMBER = 3
        private const val SPEAKER_PAUSE_MS = 450L
    }
}
