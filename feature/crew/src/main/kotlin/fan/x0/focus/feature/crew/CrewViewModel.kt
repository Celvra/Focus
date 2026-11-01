package fan.x0.focus.feature.crew

import androidx.lifecycle.viewModelScope
import fan.x0.focus.core.error.toAppError
import fan.x0.focus.core.mvi.BaseViewModel
import fan.x0.focus.core.notify.AppPresence
import fan.x0.focus.core.notify.MessageNotifier
import fan.x0.focus.data.assistant.CrewAvatarStore
import fan.x0.focus.data.assistant.CrewAvatars
import fan.x0.focus.data.assistant.CrewDefaults
import fan.x0.focus.data.assistant.CrewMember
import fan.x0.focus.data.assistant.CrewMentions
import fan.x0.focus.data.assistant.CrewMessage
import fan.x0.focus.data.assistant.CrewRunner
import fan.x0.focus.data.assistant.CrewStore
import fan.x0.focus.data.assistant.CrewSwitcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import java.util.UUID
import kotlin.math.abs

@KoinViewModel
class CrewViewModel(
    private val store: CrewStore,
    private val runner: CrewRunner,
    private val switcher: CrewSwitcher,
    private val avatars: CrewAvatarStore,
    private val notifier: MessageNotifier,
    private val presence: AppPresence,
) : BaseViewModel<CrewAction, CrewUiState, CrewEffect>(CrewUiState()) {

    private var loadJob: Job? = null
    private var groupJob: Job? = null
    private var allMembers: List<CrewMember> = emptyList()
    private var messagesById: Map<Long, CrewMessage> = emptyMap()
    private var wasRunning = false
    private var lastRunningIds: Set<String> = emptySet()
    private var refreshPending = false

    init {
        refreshGroups()
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
            CrewAction.CloseMembers -> updateState { copy(memberSheetOpen = false, memberDraft = null) }
            CrewAction.NewMember -> newMember()
            is CrewAction.EditMember -> editMember(action.id)
            CrewAction.DismissMemberEditor -> updateState { copy(memberDraft = null) }
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
            CrewAction.SaveMember -> saveMember()
            is CrewAction.SetMemberActive -> setMemberActive(action.id, action.active)
            is CrewAction.InputChanged -> inputChanged(action.value)
            is CrewAction.MentionPick -> pickMention(action.id)
            CrewAction.MentionDismiss -> updateState { copy(mention = CrewMentionUi()) }
            CrewAction.Send -> send()
            CrewAction.Stop -> runner.stop(currentState.group?.id.orEmpty())
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
                val live = if (running) {
                    run.entries.mapNotNull { it.toUi(allMembers, messagesById, avatars) }
                } else {
                    emptyList()
                }
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
                        running = running,
                        working = run?.working.orEmpty(),
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
        allMembers = emptyList()
        messagesById = emptyMap()
        wasRunning = false
        updateState {
            copy(
                group = null,
                members = emptyList(),
                history = emptyList(),
                live = emptyList(),
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
                groupDraft = null,
                deleteGroup = null,
                removedMembers = emptyList(),
            )
        }
    }

    private fun applyRuns(runs: Map<String, fan.x0.focus.data.assistant.CrewRunState>) {
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
        val live = if (running) {
            run.entries.mapNotNull { it.toUi(allMembers, messagesById, avatars) }
        } else {
            emptyList()
        }
        updateState {
            copy(
                groups = groups.map { it.copy(running = it.id in ids) },
                group = group?.copy(running = running),
                running = running,
                working = run?.working.orEmpty(),
                live = live,
            )
        }
        if (wasRunning && !running) {
            wasRunning = false
            reloadHistory(openId)
        } else {
            wasRunning = running
        }
        if (stopped.isNotEmpty()) refreshGroups()
    }

    private fun reloadHistory(groupId: String) {
        viewModelScope.launch {
            try {
                allMembers = store.allMembers(groupId)
                val messages = store.messages(groupId, MESSAGE_LIMIT)
                messagesById = messages.associateBy { it.id }
                val detail = store.groupDetail(groupId)
                val run = runner.states.value[groupId]
                val running = run?.running == true
                val live = if (running) {
                    run.entries.mapNotNull { it.toUi(allMembers, messagesById, avatars) }
                } else {
                    emptyList()
                }
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
                        running = running,
                        working = run?.working.orEmpty(),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emitError(e.toAppError())
            }
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
            )
        }
    }

    private fun editMember(id: String) {
        val member = currentState.members.firstOrNull { it.id == id } ?: return
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
                    avatarImage = allMembers.firstOrNull { it.id == id }?.avatarImage.orEmpty(),
                ),
            )
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
        if (text.isBlank() || currentState.sending) return
        val replyTo = currentState.reply?.messageId
        val restore = currentState.input
        val restoreReply = currentState.reply
        updateState { copy(input = "", mention = CrewMentionUi(), reply = null, sending = true) }
        viewModelScope.launch {
            try {
                val started = runner.send(group.id, text, replyTo)
                if (started) reloadHistory(group.id)
                refreshGroups()
                updateState { copy(sending = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState { copy(sending = false, input = restore, reply = restoreReply) }
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

    companion object {
        private const val MESSAGE_LIMIT = 200
    }
}
