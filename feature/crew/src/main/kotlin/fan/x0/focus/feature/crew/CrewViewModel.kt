package fan.x0.focus.feature.crew

import androidx.lifecycle.viewModelScope
import fan.x0.focus.core.error.toAppError
import fan.x0.focus.core.mvi.BaseViewModel
import fan.x0.focus.core.notify.AppPresence
import fan.x0.focus.core.notify.MessageNotifier
import fan.x0.focus.data.assistant.AskAnswer
import fan.x0.focus.data.assistant.AskKind
import fan.x0.focus.data.assistant.AssistantConfig
import fan.x0.focus.data.assistant.AssistantSettings
import fan.x0.focus.data.assistant.AttachmentLoader
import fan.x0.focus.data.assistant.ChatAttachment
import fan.x0.focus.data.assistant.ChatDetailSettings
import fan.x0.focus.data.assistant.CrewAvatarStore
import fan.x0.focus.data.assistant.CrewAvatars
import fan.x0.focus.data.assistant.CrewAskPhase
import fan.x0.focus.data.assistant.CrewDefaults
import fan.x0.focus.data.assistant.CrewMember
import fan.x0.focus.data.assistant.CrewMentions
import fan.x0.focus.data.assistant.CrewMessage
import fan.x0.focus.data.assistant.CrewRunEntry
import fan.x0.focus.data.assistant.CrewRunState
import fan.x0.focus.data.assistant.CrewRunner
import fan.x0.focus.data.assistant.CrewStore
import fan.x0.focus.data.assistant.CrewSwitcher
import fan.x0.focus.data.assistant.MessageStats
import fan.x0.focus.data.assistant.ModelCatalogRepository
import fan.x0.focus.data.assistant.SecretStore
import fan.x0.focus.data.assistant.requestConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.KoinViewModel
import java.util.UUID
import kotlin.math.abs

private const val REVEAL_FRAME_MS = 33L
private const val REVEAL_CATCHUP_FRAMES = 18
private const val REVEAL_MIN_GROUP = 3
private const val REVEAL_HANDOFF_MS = 280L
private const val REVEAL_JOIN_TIMEOUT_MS = 4_000L

@KoinViewModel
class CrewViewModel(
    private val store: CrewStore,
    private val runner: CrewRunner,
    private val switcher: CrewSwitcher,
    private val avatars: CrewAvatarStore,
    private val loader: AttachmentLoader,
    private val settings: AssistantSettings,
    private val notifier: MessageNotifier,
    private val presence: AppPresence,
    private val details: ChatDetailSettings,
    private val catalog: ModelCatalogRepository,
    private val secrets: SecretStore,
) : BaseViewModel<CrewAction, CrewUiState, CrewEffect>(CrewUiState()) {

    private var loadJob: Job? = null
    private var groupJob: Job? = null
    private var modelJob: Job? = null
    private var assistantConfig: AssistantConfig? = null
    private var allMembers: List<CrewMember> = emptyList()
    private var messagesById: Map<Long, CrewMessage> = emptyMap()
    private var wasRunning = false
    private var lastRunningIds: Set<String> = emptySet()
    private var refreshPending = false
    private var revealJob: Job? = null
    private var revealGroupId = ""
    private var revealKey = ""
    private var revealTarget = ""
    private var revealText = ""

    init {
        refreshGroups()
        viewModelScope.launch {
            val config = runCatching { settings.configOnce() }.getOrNull() ?: return@launch
            assistantConfig = config
            updateState {
                copy(
                    providers = config.providers.map {
                        CrewProviderUi(it.id, it.name, it.model)
                    },
                )
            }
        }
        viewModelScope.launch {
            details.config.collect { config ->
                updateState { copy(statsConfig = config) }
            }
        }
        viewModelScope.launch {
            runner.states.collect { runs -> applyRuns(runs) }
        }
        viewModelScope.launch {
            runner.reactionUpdates.collect { update ->
                messagesById = messagesById[update.messageId]?.let { message ->
                    messagesById + (message.id to message.copy(reactions = message.reactions + update.emoji))
                } ?: messagesById
                updateState {
                    copy(
                        history = history.map { entry ->
                            if (entry is CrewEntryUi.Message && entry.messageId == update.messageId) {
                                entry.copy(reactions = entry.reactions + update.emoji)
                            } else {
                                entry
                            }
                        },
                        live = live.map { entry ->
                            if (entry is CrewEntryUi.Message && entry.messageId == update.messageId) {
                                entry.copy(reactions = entry.reactions + update.emoji)
                            } else {
                                entry
                            }
                        },
                        menu = menu?.let { current ->
                            if (current.messageId == update.messageId) {
                                current.copy(reactions = current.reactions + update.emoji)
                            } else {
                                current
                            }
                        },
                    )
                }
            }
        }
        viewModelScope.launch {
            switcher.openRequest.collect { id ->
                if (id != null) {
                    openGroup(id)
                    switcher.consume()
                }
            }
        }
        viewModelScope.launch {
            store.deleted.collect { id ->
                if (currentState.group?.id == id) closeGroup()
                refreshGroups()
            }
        }
        viewModelScope.launch {
            store.changes.collect { refreshGroups() }
        }
    }

    override fun onAction(action: CrewAction) {
        when (action) {
            is CrewAction.OpenGroup -> openGroup(action.id)
            CrewAction.CloseGroup -> closeGroup()
            CrewAction.NewGroup -> newGroup()
            CrewAction.EditGroup -> editGroup()
            is CrewAction.EditGroupById -> editGroupById(action.id)
            CrewAction.ToggleArchivedView -> updateState { copy(showArchived = !showArchived, groupMenuId = null) }
            is CrewAction.OpenGroupMenu -> updateState { copy(groupMenuId = action.id) }
            CrewAction.CloseGroupMenu -> updateState { copy(groupMenuId = null) }
            is CrewAction.SetArchived -> setArchived(action.id, action.archived)
            is CrewAction.AskDeleteGroupById -> updateState {
                copy(groupMenuId = null, deleteGroup = groups.firstOrNull { it.id == action.id })
            }
            CrewAction.DismissGroupEditor -> updateState { copy(groupDraft = null) }
            is CrewAction.GroupNameChanged -> updateState {
                copy(groupDraft = groupDraft?.copy(name = action.value))
            }
            is CrewAction.GroupTopicChanged -> updateState {
                copy(groupDraft = groupDraft?.copy(topic = action.value))
            }
            is CrewAction.GroupAvatarChanged -> updateState {
                copy(groupDraft = groupDraft?.copy(avatarPreset = action.preset, avatarPalette = action.palette))
            }
            CrewAction.AddDraftMember -> addDraftMember()
            is CrewAction.DraftMemberNameChanged -> updateDraftMember(action.key) { it.copy(name = action.value) }
            is CrewAction.DraftMemberDutyChanged -> updateDraftMember(action.key) { it.copy(duty = action.value) }
            is CrewAction.DraftMemberAvatarChanged -> updateDraftMember(action.key) {
                it.copy(avatarPreset = action.preset, avatarPalette = action.palette)
            }
            is CrewAction.RemoveDraftMember -> updateState {
                val members = groupDraft?.members.orEmpty()
                if (members.size <= 1) this else copy(
                    groupDraft = groupDraft?.copy(members = members.filterNot { it.key == action.key }),
                )
            }
            CrewAction.SaveGroup -> saveGroup()
            CrewAction.AskDeleteGroup -> updateState { copy(deleteGroup = group) }
            CrewAction.ConfirmDeleteGroup -> confirmDeleteGroup()
            CrewAction.CancelDeleteGroup -> updateState { copy(deleteGroup = null) }
            CrewAction.OpenMembers -> updateState { copy(memberSheetOpen = true) }
            CrewAction.CloseMembers -> updateState {
                copy(memberSheetOpen = false, memberDraft = null, modelPickerOpen = false)
            }
            CrewAction.NewMember -> newMember()
            is CrewAction.EditMember -> editMember(action.id)
            CrewAction.DismissMemberEditor -> updateState {
                copy(memberDraft = null, modelPickerOpen = false, memberModels = emptyList())
            }
            is CrewAction.MemberNameChanged -> updateState {
                copy(memberDraft = memberDraft?.copy(name = action.value))
            }
            is CrewAction.MemberDutyChanged -> updateState {
                copy(memberDraft = memberDraft?.copy(duty = action.value))
            }
            is CrewAction.MemberAvatarChanged -> updateState {
                copy(
                    memberDraft = memberDraft?.copy(
                        avatarPreset = action.preset,
                        avatarPalette = action.palette,
                        avatarImage = "",
                        avatarPath = null,
                    ),
                )
            }
            is CrewAction.MemberPhotoPicked -> pickMemberPhoto(action.uri)
            CrewAction.MemberPhotoCleared -> updateState {
                copy(memberDraft = memberDraft?.copy(avatarImage = "", avatarPath = null))
            }
            is CrewAction.MemberProviderChanged -> updateState {
                val draft = memberDraft
                if (draft == null || draft.providerId == action.id) {
                    this
                } else {
                    copy(
                        memberDraft = draft.copy(providerId = action.id, model = ""),
                        memberModels = emptyList(),
                        memberModelQuery = "",
                        modelPickerOpen = false,
                    )
                }
            }
            is CrewAction.MemberModelChanged -> updateState {
                copy(
                    memberDraft = memberDraft?.copy(model = action.value),
                    modelPickerOpen = false,
                    memberModelQuery = "",
                )
            }
            CrewAction.OpenMemberModelPicker -> openMemberModelPicker()
            CrewAction.CloseMemberModelPicker -> updateState {
                copy(modelPickerOpen = false, memberModelQuery = "")
            }
            is CrewAction.MemberModelQueryChanged -> updateState {
                copy(memberModelQuery = action.value)
            }
            CrewAction.RefreshMemberModels -> loadMemberModels()
            is CrewAction.MemberReasoningChanged -> updateState {
                copy(memberDraft = memberDraft?.copy(reasoning = action.value))
            }
            CrewAction.SaveMember -> saveMember()
            is CrewAction.SetMemberActive -> setMemberActive(action.id, action.active)
            is CrewAction.InputChanged -> inputChanged(action.value)
            is CrewAction.AttachPicked -> attachPicked(action.uris)
            is CrewAction.RemoveAttachment -> updateState {
                copy(attachments = attachments.filterNot { it.id == action.id })
            }
            is CrewAction.MentionPick -> pickMention(action.id)
            CrewAction.MentionDismiss -> updateState { copy(mention = CrewMentionUi()) }
            CrewAction.Send -> send()
            CrewAction.Stop -> runner.stop(currentState.group?.id.orEmpty())
            is CrewAction.AskSelect -> askSelect(action.askId, action.questionId, action.choice)
            is CrewAction.AskConfirm -> askConfirm(action.askId, action.questionId, action.value)
            is CrewAction.AskEdit -> askEdit(action.askId, action.questionId, action.other, action.value)
            is CrewAction.AskSubmit -> askSubmit(action.askId)
            is CrewAction.AskSkip -> askSkip(action.askId)
            is CrewAction.OpenMessageMenu -> openMessageMenu(action.key)
            CrewAction.CloseMessageMenu -> updateState { copy(menu = null) }
            is CrewAction.React -> react(action.messageId, action.emoji)
            is CrewAction.ReplyTo -> replyTo(action.key)
            CrewAction.CancelReply -> updateState { copy(reply = null) }
            is CrewAction.StartEdit -> startEdit(action.key)
            is CrewAction.EditDraftChanged -> updateState {
                copy(edit = edit?.copy(draft = action.value))
            }
            CrewAction.SaveEdit -> saveEdit()
            CrewAction.DismissEdit -> updateState { copy(edit = null) }
            is CrewAction.AskDeleteMessage -> askDeleteMessage(action.key)
            CrewAction.ConfirmDeleteMessage -> confirmDeleteMessage()
            CrewAction.CancelDeleteMessage -> updateState { copy(pendingDelete = null) }
            is CrewAction.JumpToMessage -> updateState {
                copy(focusMessageId = action.messageId, menu = null)
            }
            is CrewAction.SelectText -> updateState {
                copy(selectionKey = action.key, menu = null)
            }
            CrewAction.ClearSelection -> updateState { copy(selectionKey = null) }
            CrewAction.ClearFocus -> updateState { copy(focusMessageId = null) }
            is CrewAction.Approve -> runner.decide(action.approvalId, action.approved)
        }
    }

    override fun onCleared() {
        presence.setVisibleConversation(null)
        super.onCleared()
    }

    private fun refreshGroups() {
        if (loadJob?.isActive == true) {
            refreshPending = true
            return
        }
        loadJob = viewModelScope.launch {
            try {
                val rows = store.groups(includeArchived = true).map { group ->
                    Triple(group, store.members(group.id), store.lastMessage(group.id))
                }
                val running = runner.states.value.values.filter { it.running }.mapTo(mutableSetOf()) { it.groupId }
                val mapped = rows.map { (group, members, last) ->
                    group.toUi(members, last, group.id in running, avatars)
                }
                updateState { copy(loading = false, groups = mapped) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState { copy(loading = false) }
                emitError(e.toAppError())
            } finally {
                loadJob = null
                if (refreshPending) {
                    refreshPending = false
                    refreshGroups()
                }
            }
        }
    }

    private fun openGroup(id: String) {
        notifier.cancel(conversationId(id))
        presence.setVisibleConversation(conversationId(id))
        wasRunning = false
        resetReveal(id)
        groupJob?.cancel()
        loadGroup(id, showLoading = true)
    }

    private fun loadGroup(id: String, showLoading: Boolean) {
        groupJob?.cancel()
        groupJob = viewModelScope.launch {
            updateState {
                copy(
                    groupLoading = showLoading,
                    group = groups.firstOrNull { it.id == id },
                    members = if (showLoading) emptyList() else members,
                    history = if (showLoading) emptyList() else history,
                    live = emptyList(),
                    running = false,
                    working = "",
                    liveStats = MessageStats(),
                )
            }
            try {
                val detail = store.groupDetail(id)
                    ?: throw IllegalStateException("Group not found")
                allMembers = store.allMembers(id)
                val messages = store.messages(id, MESSAGE_LIMIT)
                messagesById = messages.associateBy { it.id }
                val run = runner.states.value[id]
                val running = run?.running == true
                wasRunning = running
                val live = liveUi(id, run)
                val todos = run?.todos ?: store.todos(id)
                updateState {
                    copy(
                        groupLoading = false,
                        group = detail.group.toUi(
                            detail.members,
                            messages.lastOrNull(),
                            running,
                            avatars,
                        ),
                        members = detail.members.map { it.toUi(avatars) },
                        removedMembers = allMembers.filterNot { it.active }.map { it.toUi(avatars) },
                        history = messages.toEntries(allMembers, avatars),
                        live = live,
                        todos = todos,
                        running = running,
                        working = run?.working.orEmpty(),
                        liveStats = run.stats(),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState { copy(groupLoading = false) }
                emitError(e.toAppError())
            }
        }
    }

    private fun closeGroup() {
        presence.setVisibleConversation(null)
        groupJob?.cancel()
        resetReveal()
        allMembers = emptyList()
        messagesById = emptyMap()
        wasRunning = false
        updateState {
            copy(
                group = null,
                members = emptyList(),
                history = emptyList(),
                live = emptyList(),
                todos = emptyList(),
                askDrafts = emptyMap(),
                attachments = emptyList(),
                groupLoading = false,
                running = false,
                working = "",
                input = "",
                mention = CrewMentionUi(),
                reply = null,
                menu = null,
                edit = null,
                pendingDelete = null,
                focusMessageId = null,
                groupMenuId = null,
                memberSheetOpen = false,
                memberDraft = null,
                memberModels = emptyList(),
                memberModelsLoading = false,
                memberModelQuery = "",
                modelPickerOpen = false,
                selectionKey = null,
                liveStats = MessageStats(),
                groupDraft = null,
                deleteGroup = null,
                removedMembers = emptyList(),
            )
        }
    }

    private fun applyRuns(runs: Map<String, CrewRunState>) {
        val ids = runs.values.filter { it.running }.mapTo(mutableSetOf()) { it.groupId }
        val previousIds = lastRunningIds
        val stopped = previousIds - ids
        val changed = previousIds != ids
        lastRunningIds = ids
        val openId = currentState.group?.id
        if (openId == null) {
            if (changed) {
                updateState { copy(groups = groups.map { it.copy(running = it.id in ids) }) }
            }
            if (stopped.isNotEmpty()) refreshGroups()
            return
        }
        val run = runs[openId]
        if (run == null && !changed && !wasRunning) return
        val running = run?.running == true
        val live = liveUi(openId, run)
        updateState {
            copy(
                groups = groups.map { it.copy(running = it.id in ids) },
                group = group?.copy(running = running),
                running = running,
                working = run?.working.orEmpty(),
                live = live,
                todos = run?.todos ?: todos,
                liveStats = run.stats(),
            )
        }
        if (wasRunning && !running) {
            wasRunning = false
            reloadHistory(openId, settleReveal = true)
        } else {
            wasRunning = running
        }
        if (stopped.isNotEmpty()) refreshGroups()
    }

    private fun reloadHistory(groupId: String, settleReveal: Boolean = false) {
        viewModelScope.launch {
            if (settleReveal) settleReveal(groupId)
            try {
                allMembers = store.allMembers(groupId)
                val messages = store.messages(groupId, MESSAGE_LIMIT)
                messagesById = messages.associateBy { it.id }
                val detail = store.groupDetail(groupId)
                val run = runner.states.value[groupId]
                val running = run?.running == true
                val live = liveUi(groupId, run)
                val todos = run?.todos ?: store.todos(groupId)
                updateState {
                    copy(
                        members = detail?.members?.map { it.toUi(avatars) } ?: members,
                        removedMembers = allMembers.filterNot { it.active }.map { it.toUi(avatars) },
                        group = detail?.group?.toUi(
                            detail.members,
                            messages.lastOrNull(),
                            running,
                            avatars,
                        ) ?: group,
                        history = messages.toEntries(allMembers, avatars),
                        live = live,
                        todos = todos,
                        running = running,
                        working = run?.working.orEmpty(),
                        liveStats = run.stats(),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emitError(e.toAppError())
            }
        }
    }

    private fun liveUi(groupId: String, run: CrewRunState?): List<CrewEntryUi> {
        val entries = run?.entries.orEmpty()
        val agent = entries.lastOrNull { it is CrewRunEntry.Agent } as? CrewRunEntry.Agent
        val shown = if (agent != null) updateReveal(groupId, agent) else null
        return entries.mapNotNull { entry ->
            val mapped = if (shown != null && entry is CrewRunEntry.Agent && entry.key == agent?.key) {
                entry.copy(text = shown)
            } else {
                entry
            }
            mapped.toUi(allMembers, messagesById, avatars)
        }
    }

    private suspend fun settleReveal(groupId: String) {
        if (revealGroupId != groupId) return
        withTimeoutOrNull(REVEAL_JOIN_TIMEOUT_MS) { revealJob?.join() }
        if (revealTarget.isNotEmpty()) delay(REVEAL_HANDOFF_MS)
    }

    private fun updateReveal(groupId: String, agent: CrewRunEntry.Agent): String {
        if (revealGroupId != groupId || revealKey != agent.key) {
            resetReveal(groupId, agent.key)
        }
        if (agent.text.length < revealTarget.length || !agent.text.startsWith(revealTarget)) {
            revealText = ""
        }
        revealTarget = agent.text
        if (revealText.length > revealTarget.length) {
            revealText = revealText.take(revealTarget.length)
        }
        if (revealText.length < revealTarget.length) ensureRevealJob()
        return revealTarget.take(revealText.length)
    }

    private fun resetReveal(groupId: String = "", key: String = "") {
        revealJob?.cancel()
        revealJob = null
        revealGroupId = groupId
        revealKey = key
        revealTarget = ""
        revealText = ""
    }

    private fun ensureRevealJob() {
        if (revealJob?.isActive == true) return
        revealJob = viewModelScope.launch {
            while (revealText.length < revealTarget.length) {
                val backlog = revealTarget.length - revealText.length
                val step = ((backlog + REVEAL_CATCHUP_FRAMES - 1) / REVEAL_CATCHUP_FRAMES)
                    .coerceAtLeast(REVEAL_MIN_GROUP)
                delay(REVEAL_FRAME_MS)
                if (revealText.length >= revealTarget.length) break
                revealText = revealTarget.take((revealText.length + step).coerceAtMost(revealTarget.length))
                showRevealText()
            }
        }
    }

    private fun showRevealText() {
        if (currentState.group?.id != revealGroupId) return
        val shown = revealTarget.take(revealText.length)
        updateState {
            val index = live.indexOfLast { it is CrewEntryUi.Message && it.key == revealKey }
            if (index < 0) return@updateState this
            val entry = live[index] as CrewEntryUi.Message
            if (entry.text == shown) return@updateState this
            copy(live = live.toMutableList().also { it[index] = entry.copy(text = shown) })
        }
    }

    private fun newGroup() {
        val seed = (System.currentTimeMillis() / 1000L).toInt()
        val lead = CrewAvatars.defaultFor(seed)
        val builder = CrewAvatars.defaultFor(seed + 1)
        val groupAvatar = CrewAvatars.defaultFor(seed + 2)
        updateState {
            copy(
                groupDraft = GroupDraft(
                    avatarPreset = groupAvatar.preset,
                    avatarPalette = groupAvatar.palette,
                    members = listOf(
                        MemberDraft(
                            key = UUID.randomUUID().toString(),
                            name = CrewDefaults.LEAD_NAME,
                            duty = CrewDefaults.LEAD_DUTY,
                            avatarPreset = lead.preset,
                            avatarPalette = lead.palette,
                        ),
                        MemberDraft(
                            key = UUID.randomUUID().toString(),
                            name = CrewDefaults.MEMBER_NAME,
                            duty = CrewDefaults.MEMBER_DUTY,
                            avatarPreset = builder.preset,
                            avatarPalette = builder.palette,
                        ),
                    ),
                ),
            )
        }
    }

    private fun editGroup() {
        val group = currentState.group ?: return
        updateState {
            copy(
                groupDraft = GroupDraft(
                    id = group.id,
                    name = group.name,
                    topic = group.topic,
                    avatarPreset = group.avatarPreset,
                    avatarPalette = group.avatarPalette,
                ),
            )
        }
    }

    private fun editGroupById(id: String) {
        viewModelScope.launch {
            try {
                val group = store.group(id) ?: return@launch
                val draft = GroupDraft(
                    id = group.id,
                    name = group.name,
                    topic = group.topic,
                    avatarPreset = group.avatarPreset,
                    avatarPalette = group.avatarPalette,
                )
                updateState { copy(groupDraft = draft, groupMenuId = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emitError(e.toAppError())
            }
        }
    }

    private fun setArchived(id: String, archived: Boolean) {
        viewModelScope.launch {
            try {
                store.setArchived(id, archived)
                updateState { copy(groupMenuId = null) }
                if (archived && currentState.group?.id == id) closeGroup()
                refreshGroups()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emitError(e.toAppError())
            }
        }
    }

    private fun addDraftMember() {
        val draft = currentState.groupDraft ?: return
        val seed = abs(draft.members.size + draft.name.hashCode())
        val avatar = CrewAvatars.defaultFor(seed)
        updateState {
            copy(
                groupDraft = draft.copy(
                    members = draft.members + MemberDraft(
                        key = UUID.randomUUID().toString(),
                        avatarPreset = avatar.preset,
                        avatarPalette = avatar.palette,
                    ),
                ),
            )
        }
    }

    private fun updateDraftMember(key: String, reduce: (MemberDraft) -> MemberDraft) {
        updateState {
            copy(
                groupDraft = groupDraft?.copy(
                    members = groupDraft.members.map { if (it.key == key) reduce(it) else it },
                ),
            )
        }
    }

    private fun saveGroup() {
        val draft = currentState.groupDraft ?: return
        val name = draft.name.trim()
        if (name.isBlank() || currentState.saving) return
        val newMembers = draft.members.filter { it.name.isNotBlank() }
        if (draft.id == null && newMembers.isEmpty()) return
        updateState { copy(saving = true) }
        viewModelScope.launch {
            var createdId: String? = null
            try {
                if (draft.id == null) {
                    val group = store.createGroup(name, draft.topic.trim(), draft.avatarPreset, draft.avatarPalette)
                    createdId = group.id
                    newMembers.forEach { member ->
                        store.addMember(
                            groupId = group.id,
                            name = member.name.trim(),
                            duty = member.duty.trim(),
                            avatarPreset = member.avatarPreset,
                            avatarPalette = member.avatarPalette,
                        )
                    }
                    updateState { copy(saving = false, groupDraft = null) }
                    refreshGroups()
                    openGroup(group.id)
                } else {
                    store.updateGroup(
                        id = draft.id,
                        name = name,
                        topic = draft.topic.trim(),
                        avatarPreset = draft.avatarPreset,
                        avatarPalette = draft.avatarPalette,
                    )
                    updateState { copy(saving = false, groupDraft = null) }
                    if (currentState.group?.id == draft.id) {
                        loadGroup(draft.id, showLoading = false)
                    }
                    refreshGroups()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                createdId?.let { id -> runCatching { store.deleteGroup(id) } }
                updateState { copy(saving = false) }
                emitError(e.toAppError())
            }
        }
    }

    private fun confirmDeleteGroup() {
        val group = currentState.deleteGroup ?: return
        runner.discard(group.id)
        viewModelScope.launch {
            try {
                store.deleteGroup(group.id)
                updateState { copy(deleteGroup = null) }
                if (currentState.group?.id == group.id) closeGroup()
                refreshGroups()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emitError(e.toAppError())
            }
        }
    }

    private fun newMember() {
        val group = currentState.group ?: return
        val seed = abs(group.id.hashCode() + currentState.members.size)
        val avatar = CrewAvatars.defaultFor(seed)
        updateState {
            copy(
                memberDraft = MemberDraft(
                    key = UUID.randomUUID().toString(),
                    avatarPreset = avatar.preset,
                    avatarPalette = avatar.palette,
                ),
                modelPickerOpen = false,
                memberModels = emptyList(),
                memberModelQuery = "",
            )
        }
    }

    private fun editMember(id: String) {
        val member = currentState.members.firstOrNull { it.id == id } ?: return
        val source = allMembers.firstOrNull { it.id == id }
        updateState {
            copy(
                memberDraft = MemberDraft(
                    key = member.id,
                    id = member.id,
                    name = member.name,
                    duty = member.duty,
                    avatarPreset = member.avatarPreset,
                    avatarPalette = member.avatarPalette,
                    avatarPath = member.avatarPath,
                    avatarImage = source?.avatarImage.orEmpty(),
                    providerId = source?.providerId.orEmpty(),
                    model = source?.model.orEmpty(),
                    reasoning = source?.reasoning.orEmpty(),
                ),
                modelPickerOpen = false,
                memberModels = emptyList(),
                memberModelQuery = "",
            )
        }
    }

    private fun openMemberModelPicker() {
        if (currentState.memberDraft == null) return
        updateState { copy(modelPickerOpen = true, memberModelQuery = "") }
        if (currentState.memberModels.isEmpty()) loadMemberModels()
    }

    private fun loadMemberModels() {
        val draft = currentState.memberDraft ?: return
        val config = assistantConfig
        if (config == null) {
            viewModelScope.launch {
                val loaded = runCatching { settings.configOnce() }.getOrNull() ?: return@launch
                assistantConfig = loaded
                loadMemberModels()
            }
            return
        }
        val provider = config.providers.firstOrNull { it.id == draft.providerId }
            ?: config.activeProvider
            ?: return
        modelJob?.cancel()
        updateState { copy(memberModelsLoading = true) }
        modelJob = viewModelScope.launch {
            val key = secrets.apiKey(provider.id).orEmpty()
            if (provider.requiresKey && key.isBlank()) {
                updateState { copy(memberModelsLoading = false) }
                return@launch
            }
            val models = try {
                catalog.models(
                    provider.id,
                    provider.baseUrl,
                    draft.model.ifBlank { provider.model },
                    provider.requestConfig(key),
                    provider.protocol,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emptyList()
            }
            updateState {
                copy(
                    memberModels = models.map { it.id },
                    memberModelsLoading = false,
                )
            }
        }
    }

    private fun pickMemberPhoto(uri: String) {
        val draft = currentState.memberDraft ?: return
        viewModelScope.launch {
            updateState { copy(saving = true) }
            try {
                val name = avatars.import(uri, draft.id ?: draft.key)
                updateState {
                    copy(
                        saving = false,
                        memberDraft = memberDraft?.copy(
                            avatarImage = name,
                            avatarPath = avatars.path(name),
                        ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState { copy(saving = false) }
                emitError(e.toAppError())
            }
        }
    }

    private fun saveMember() {
        val draft = currentState.memberDraft ?: return
        val group = currentState.group ?: return
        val name = draft.name.trim()
        if (name.isBlank() || currentState.saving) return
        updateState { copy(saving = true) }
        viewModelScope.launch {
            try {
                if (draft.id == null) {
                    val member = store.addMember(
                        groupId = group.id,
                        name = name,
                        duty = draft.duty.trim(),
                        avatarPreset = draft.avatarPreset,
                        avatarPalette = draft.avatarPalette,
                        avatarImage = draft.avatarImage,
                        providerId = draft.providerId,
                        model = draft.model.trim(),
                        reasoning = draft.reasoning,
                    )
                    runner.announce(group.id, "+ ${member.name}")
                } else {
                    val existing = allMembers.firstOrNull { it.id == draft.id }
                        ?: throw IllegalStateException("Member not found")
                    store.updateMember(
                        existing.copy(
                            name = name,
                            duty = draft.duty.trim(),
                            avatarPreset = draft.avatarPreset,
                            avatarPalette = draft.avatarPalette,
                            avatarImage = draft.avatarImage,
                            providerId = draft.providerId,
                            model = draft.model.trim(),
                            reasoning = draft.reasoning,
                        ),
                    )
                    if (existing.avatarImage.isNotBlank() && existing.avatarImage != draft.avatarImage) {
                        avatars.delete(existing.avatarImage)
                    }
                }
                updateState { copy(saving = false, memberDraft = null) }
                loadGroup(group.id, showLoading = false)
                refreshGroups()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState { copy(saving = false) }
                emitError(e.toAppError())
            }
        }
    }

    private fun setMemberActive(id: String, active: Boolean) {
        val group = currentState.group ?: return
        val member = allMembers.firstOrNull { it.id == id } ?: return
        if (member.active == active) return
        viewModelScope.launch {
            try {
                store.setMemberActive(id, active)
                runner.announce(group.id, "${if (active) "+" else "-"} ${member.name}")
                loadGroup(group.id, showLoading = false)
                refreshGroups()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emitError(e.toAppError())
            }
        }
    }

    private fun attachPicked(uris: List<String>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            try {
                val loaded = uris.mapNotNull { loader.load(it) }
                if (loaded.isNotEmpty()) {
                    updateState { copy(attachments = attachments + loaded) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emitError(e.toAppError())
            }
        }
    }

    private fun askSelect(askId: String, questionId: String, choice: String) {
        val entry = currentState.entries.firstOrNull { it is CrewEntryUi.Ask && it.id == askId }
            as? CrewEntryUi.Ask ?: return
        val question = entry.questions.firstOrNull { it.id == questionId } ?: return
        updateAskDraft(askId, questionId) { draft ->
            if (question.kind == AskKind.Multi) {
                draft.copy(
                    selections = if (choice in draft.selections) {
                        draft.selections - choice
                    } else {
                        draft.selections + choice
                    },
                )
            } else {
                draft.copy(selections = listOf(choice))
            }
        }
    }

    private fun askConfirm(askId: String, questionId: String, value: Boolean) {
        updateAskDraft(askId, questionId) { it.copy(confirmed = value) }
    }

    private fun askEdit(askId: String, questionId: String, other: Boolean, value: String) {
        updateAskDraft(askId, questionId) {
            if (other) it.copy(other = value) else it.copy(text = value)
        }
    }

    private fun updateAskDraft(
        askId: String,
        questionId: String,
        reduce: (CrewAskDraft) -> CrewAskDraft,
    ) {
        updateState {
            val drafts = askDrafts[askId].orEmpty()
            val next = drafts + (questionId to reduce(drafts[questionId] ?: CrewAskDraft()))
            copy(askDrafts = askDrafts + (askId to next))
        }
    }

    private fun askSubmit(askId: String) {
        val entry = currentState.entries.firstOrNull { it is CrewEntryUi.Ask && it.id == askId }
            as? CrewEntryUi.Ask ?: return
        if (entry.phase != CrewAskPhase.Waiting) return
        val drafts = currentState.askDrafts[askId].orEmpty()
        if (!entry.isReady(drafts)) return
        val answers = entry.questions.map { question ->
            val draft = drafts[question.id] ?: CrewAskDraft()
            when (question.kind) {
                AskKind.Single -> AskAnswer(
                    questionId = question.id,
                    choices = draft.selections.take(1),
                    text = draft.other.trim(),
                )
                AskKind.Multi -> AskAnswer(
                    questionId = question.id,
                    choices = draft.selections,
                    text = draft.other.trim(),
                )
                AskKind.Text -> AskAnswer(questionId = question.id, text = draft.text.trim())
                AskKind.Number -> AskAnswer(
                    questionId = question.id,
                    number = draft.text.trim().toDoubleOrNull(),
                )
                AskKind.Confirm -> AskAnswer(questionId = question.id, confirmed = draft.confirmed)
            }
        }
        runner.answerAsk(askId, answers)
        updateState { copy(askDrafts = askDrafts - askId) }
    }

    private fun askSkip(askId: String) {
        runner.answerAsk(askId, emptyList(), skipped = true)
        updateState { copy(askDrafts = askDrafts - askId) }
    }

    private fun inputChanged(value: String) {
        updateState { copy(input = value, mention = mentionFor(value)) }
    }

    private fun mentionFor(value: String): CrewMentionUi {
        val query = CrewMentions.query(value) ?: return CrewMentionUi()
        val members = currentState.members.filter { member ->
            query.isBlank() || member.name.startsWith(query, ignoreCase = true)
        }
        return if (members.isEmpty()) CrewMentionUi() else CrewMentionUi(
            open = true,
            members = members,
        )
    }

    private fun pickMention(id: String) {
        val member = currentState.members.firstOrNull { it.id == id } ?: return
        val input = currentState.input
        val at = input.lastIndexOf('@')
        if (at < 0) return
        val next = input.substring(0, at) + "@${member.name} "
        updateState { copy(input = next, mention = mentionFor(next)) }
    }

    private fun send() {
        val group = currentState.group ?: return
        val text = currentState.input.trim()
        val attachments = currentState.attachments
        if ((text.isBlank() && attachments.isEmpty()) || currentState.sending) return
        val replyTo = currentState.reply?.messageId
        val restore = currentState.input
        val restoreAttachments = attachments
        val restoreReply = currentState.reply
        updateState {
            copy(
                input = "",
                attachments = emptyList(),
                mention = CrewMentionUi(),
                reply = null,
                sending = true,
            )
        }
        viewModelScope.launch {
            try {
                val started = runner.send(group.id, text, replyTo, attachments)
                if (started) reloadHistory(group.id)
                refreshGroups()
                updateState { copy(sending = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState {
                    copy(
                        sending = false,
                        input = restore,
                        attachments = restoreAttachments,
                        reply = restoreReply,
                    )
                }
                emitError(e.toAppError())
            }
        }
    }

    private fun openMessageMenu(key: String) {
        val entry = currentState.entries.firstOrNull { it.key == key } as? CrewEntryUi.Message ?: return
        if (entry.messageId == null) return
        updateState {
            copy(
                menu = CrewMessageMenuUi(
                    key = entry.key,
                    messageId = entry.messageId,
                    author = entry.author,
                    text = entry.text,
                    reactions = entry.reactions,
                    fromUser = entry.fromUser,
                    canEdit = entry.fromUser && !running,
                    canDelete = !running,
                ),
            )
        }
    }

    private fun startEdit(key: String) {
        val entry = currentState.entries.firstOrNull { it.key == key } as? CrewEntryUi.Message ?: return
        val messageId = entry.messageId ?: return
        if (!entry.fromUser || currentState.running) return
        updateState {
            copy(
                edit = CrewEditUi(
                    messageId = messageId,
                    key = key,
                    original = entry.text,
                    draft = entry.text,
                ),
                menu = null,
            )
        }
    }

    private fun saveEdit() {
        val edit = currentState.edit ?: return
        val group = currentState.group ?: return
        val draft = edit.draft.trim()
        if (draft.isBlank() || currentState.saving) return
        if (draft == edit.original.trim()) {
            updateState { copy(edit = null) }
            return
        }
        updateState { copy(saving = true) }
        viewModelScope.launch {
            try {
                store.updateMessageContent(edit.messageId, group.id, draft)
                store.deleteMessagesAfter(group.id, edit.messageId)
                if (group.archived) store.setArchived(group.id, false)
                updateState { copy(saving = false, edit = null) }
                val members = store.members(group.id)
                runner.start(group.id, CrewMentions.ids(draft, members).toSet())
                reloadHistory(group.id)
                refreshGroups()
                sendEffect(CrewEffect.MessageEdited)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState { copy(saving = false) }
                emitError(e.toAppError())
            }
        }
    }

    private fun askDeleteMessage(key: String) {
        val menu = currentState.menu?.takeIf { it.key == key } ?: run {
            openMessageMenu(key)
            currentState.menu
        } ?: return
        if (!menu.canDelete) return
        updateState { copy(pendingDelete = menu, menu = null) }
    }

    private fun confirmDeleteMessage() {
        val target = currentState.pendingDelete ?: return
        val messageId = target.messageId ?: return
        val group = currentState.group ?: return
        viewModelScope.launch {
            try {
                store.deleteMessage(messageId, group.id)
                messagesById = messagesById - messageId
                updateState { copy(pendingDelete = null) }
                reloadHistory(group.id)
                sendEffect(CrewEffect.MessageDeleted)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emitError(e.toAppError())
            }
        }
    }

    private fun replyTo(key: String) {
        val entry = currentState.entries.firstOrNull { it.key == key } as? CrewEntryUi.Message ?: return
        val messageId = entry.messageId ?: return
        updateState {
            copy(
                reply = CrewQuoteUi(
                    messageId = messageId,
                    author = entry.author,
                    text = entry.text.replace(Regex("\\s+"), " ").trim().take(200),
                ),
                menu = null,
            )
        }
    }

    private fun react(messageId: Long, emoji: String) {
        val entry = currentState.entries.firstOrNull {
            it is CrewEntryUi.Message && it.messageId == messageId
        } as? CrewEntryUi.Message
        val current = messagesById[messageId]?.reactions ?: entry?.reactions.orEmpty()
        val next = if (current.contains(emoji)) current.filterNot { it == emoji } else current + emoji
        messagesById = messagesById[messageId]?.let { messagesById + (messageId to it.copy(reactions = next)) }
            ?: messagesById
        updateState {
            copy(
                history = history.map { entry ->
                    if (entry is CrewEntryUi.Message && entry.messageId == messageId) {
                        entry.copy(reactions = next)
                    } else {
                        entry
                    }
                },
                live = live.map { entry ->
                    if (entry is CrewEntryUi.Message && entry.messageId == messageId) {
                        entry.copy(reactions = next)
                    } else {
                        entry
                    }
                },
                menu = null,
            )
        }
        viewModelScope.launch {
            try {
                store.setReactions(messageId, next)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emitError(e.toAppError())
            }
        }
    }

    private fun conversationId(groupId: String): String = "crew:$groupId"

    private fun CrewRunState?.stats(): MessageStats {
        val entries = this?.entries.orEmpty()
            .filterIsInstance<CrewRunEntry.Agent>()
            .mapNotNull { it.stats }
        return entries.fold(MessageStats()) { total, stats -> total.merge(stats) }
    }

    companion object {
        private const val MESSAGE_LIMIT = 200
    }
}
