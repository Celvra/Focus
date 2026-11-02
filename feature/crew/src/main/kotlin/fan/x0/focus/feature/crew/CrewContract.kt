package fan.x0.focus.feature.crew

import fan.x0.focus.core.mvi.UiAction
import fan.x0.focus.core.mvi.UiEffect
import fan.x0.focus.core.mvi.UiState
import fan.x0.focus.data.assistant.AskAnswer
import fan.x0.focus.data.assistant.AskKind
import fan.x0.focus.data.assistant.AskQuestion
import fan.x0.focus.data.assistant.ChatAttachment
import fan.x0.focus.data.assistant.ChatDetailConfig
import fan.x0.focus.data.assistant.ConversationTodo
import fan.x0.focus.data.assistant.CrewApprovalPhase
import fan.x0.focus.data.assistant.CrewAskPhase
import fan.x0.focus.data.assistant.CrewToolStatus
import fan.x0.focus.data.assistant.MessageStats

data class CrewGroupUi(
    val id: String,
    val name: String,
    val topic: String,
    val avatarPreset: Int,
    val avatarPalette: Int,
    val memberCount: Int,
    val members: List<CrewMemberUi>,
    val preview: String,
    val previewAuthor: String,
    val updatedAt: Long,
    val running: Boolean,
    val archived: Boolean = false,
)

data class CrewMemberUi(
    val id: String,
    val name: String,
    val duty: String,
    val avatarPreset: Int,
    val avatarPalette: Int,
    val avatarPath: String?,
    val active: Boolean = true,
)

data class CrewProviderUi(
    val id: String,
    val name: String,
    val model: String = "",
)

data class CrewQuoteUi(
    val messageId: Long,
    val author: String,
    val text: String,
    val deleted: Boolean = false,
)

data class CrewMentionChipUi(
    val name: String,
)

data class CrewToolUi(
    val id: String,
    val label: String,
    val status: CrewToolStatus,
    val summary: String,
    val arguments: String = "",
    val detail: String = "",
)

sealed interface CrewEntryUi {
    val key: String

    data class Message(
        override val key: String,
        val messageId: Long?,
        val fromUser: Boolean,
        val authorId: String,
        val author: String,
        val duty: String,
        val avatarPreset: Int,
        val avatarPalette: Int,
        val avatarPath: String?,
        val text: String,
        val streaming: Boolean = false,
        val activity: String = "",
        val replyTo: CrewQuoteUi? = null,
        val reactions: List<String> = emptyList(),
        val mentions: List<CrewMentionChipUi> = emptyList(),
        val tools: List<CrewToolUi> = emptyList(),
        val attachments: List<ChatAttachment> = emptyList(),
        val reasoning: String = "",
        val reasoningMs: Long = 0,
        val reasoningStartedAt: Long = 0,
        val reasoningRunning: Boolean = false,
        val error: String? = null,
        val stats: MessageStats? = null,
    ) : CrewEntryUi

    data class Ask(
        override val key: String,
        val id: String,
        val intro: String,
        val questions: List<AskQuestion>,
        val phase: CrewAskPhase,
        val answers: List<AskAnswer> = emptyList(),
    ) : CrewEntryUi

    data class Notice(
        override val key: String,
        val text: String,
    ) : CrewEntryUi

    data class Approval(
        override val key: String,
        val id: String,
        val title: String,
        val summary: String,
        val steps: List<String>,
        val phase: CrewApprovalPhase,
    ) : CrewEntryUi
}

data class CrewMessageMenuUi(
    val key: String,
    val messageId: Long?,
    val author: String,
    val text: String,
    val reactions: List<String>,
    val fromUser: Boolean = false,
    val canEdit: Boolean = false,
    val canDelete: Boolean = false,
)

data class CrewEditUi(
    val messageId: Long,
    val key: String,
    val original: String,
    val draft: String,
)

data class CrewMentionUi(
    val open: Boolean = false,
    val members: List<CrewMemberUi> = emptyList(),
) {
    val visible: Boolean get() = open && members.isNotEmpty()
}

data class MemberDraft(
    val key: String,
    val id: String? = null,
    val name: String = "",
    val duty: String = "",
    val avatarPreset: Int = 0,
    val avatarPalette: Int = 0,
    val avatarImage: String = "",
    val avatarPath: String? = null,
    val providerId: String = "",
    val model: String = "",
    val reasoning: String = "",
)

data class CrewAskDraft(
    val selections: List<String> = emptyList(),
    val text: String = "",
    val other: String = "",
    val confirmed: Boolean? = null,
)

data class GroupDraft(
    val id: String? = null,
    val name: String = "",
    val topic: String = "",
    val avatarPreset: Int = 0,
    val avatarPalette: Int = 0,
    val members: List<MemberDraft> = emptyList(),
)

data class CrewUiState(
    val loading: Boolean = true,
    val groupLoading: Boolean = false,
    val groups: List<CrewGroupUi> = emptyList(),
    val group: CrewGroupUi? = null,
    val showArchived: Boolean = false,
    val groupMenuId: String? = null,
    val members: List<CrewMemberUi> = emptyList(),
    val removedMembers: List<CrewMemberUi> = emptyList(),
    val providers: List<CrewProviderUi> = emptyList(),
    val history: List<CrewEntryUi> = emptyList(),
    val live: List<CrewEntryUi> = emptyList(),
    val todos: List<ConversationTodo> = emptyList(),
    val askDrafts: Map<String, Map<String, CrewAskDraft>> = emptyMap(),
    val running: Boolean = false,
    val working: String = "",
    val input: String = "",
    val attachments: List<ChatAttachment> = emptyList(),
    val sending: Boolean = false,
    val mention: CrewMentionUi = CrewMentionUi(),
    val reply: CrewQuoteUi? = null,
    val menu: CrewMessageMenuUi? = null,
    val edit: CrewEditUi? = null,
    val pendingDelete: CrewMessageMenuUi? = null,
    val focusMessageId: Long? = null,
    val memberSheetOpen: Boolean = false,
    val groupDraft: GroupDraft? = null,
    val memberDraft: MemberDraft? = null,
    val deleteGroup: CrewGroupUi? = null,
    val saving: Boolean = false,
    val memberModels: List<String> = emptyList(),
    val memberModelsLoading: Boolean = false,
    val memberModelQuery: String = "",
    val modelPickerOpen: Boolean = false,
    val selectionKey: String? = null,
    val statsConfig: ChatDetailConfig = ChatDetailConfig(),
    val liveStats: MessageStats = MessageStats(),
) : UiState {
    val entries: List<CrewEntryUi>
        get() {
            val persisted = history.mapNotNullTo(mutableSetOf()) {
                (it as? CrewEntryUi.Message)?.messageId
            }
            val notices = history.mapNotNullTo(mutableListOf()) {
                (it as? CrewEntryUi.Notice)?.text
            }
            return history + live.filterNot { entry ->
                when (entry) {
                    is CrewEntryUi.Message -> entry.messageId != null && entry.messageId in persisted
                    is CrewEntryUi.Notice -> notices.any { text ->
                        text == entry.text ||
                            (entry.text.length >= 400 && text.startsWith(entry.text))
                    }
                    else -> false
                }
            }
        }

    val stats: MessageStats?
        get() {
            if (liveStats.hasUsage || liveStats.providerId.isNotBlank() || liveStats.round > 0) {
                return liveStats
            }
            return history.asReversed().firstNotNullOfOrNull {
                (it as? CrewEntryUi.Message)?.stats
            }
        }
}

sealed interface CrewAction : UiAction {
    data class OpenGroup(val id: String) : CrewAction
    data object CloseGroup : CrewAction
    data object NewGroup : CrewAction
    data object EditGroup : CrewAction
    data class EditGroupById(val id: String) : CrewAction
    data object ToggleArchivedView : CrewAction
    data class OpenGroupMenu(val id: String) : CrewAction
    data object CloseGroupMenu : CrewAction
    data class SetArchived(val id: String, val archived: Boolean) : CrewAction
    data class AskDeleteGroupById(val id: String) : CrewAction
    data object DismissGroupEditor : CrewAction
    data class GroupNameChanged(val value: String) : CrewAction
    data class GroupTopicChanged(val value: String) : CrewAction
    data class GroupAvatarChanged(val preset: Int, val palette: Int) : CrewAction
    data object AddDraftMember : CrewAction
    data class DraftMemberNameChanged(val key: String, val value: String) : CrewAction
    data class DraftMemberDutyChanged(val key: String, val value: String) : CrewAction
    data class DraftMemberAvatarChanged(val key: String, val preset: Int, val palette: Int) : CrewAction
    data class RemoveDraftMember(val key: String) : CrewAction
    data object SaveGroup : CrewAction
    data object AskDeleteGroup : CrewAction
    data object ConfirmDeleteGroup : CrewAction
    data object CancelDeleteGroup : CrewAction
    data object OpenMembers : CrewAction
    data object CloseMembers : CrewAction
    data object NewMember : CrewAction
    data class EditMember(val id: String) : CrewAction
    data object DismissMemberEditor : CrewAction
    data class MemberNameChanged(val value: String) : CrewAction
    data class MemberDutyChanged(val value: String) : CrewAction
    data class MemberAvatarChanged(val preset: Int, val palette: Int) : CrewAction
    data class MemberPhotoPicked(val uri: String) : CrewAction
    data object MemberPhotoCleared : CrewAction
    data class MemberProviderChanged(val id: String) : CrewAction
    data class MemberModelChanged(val value: String) : CrewAction
    data object OpenMemberModelPicker : CrewAction
    data object CloseMemberModelPicker : CrewAction
    data class MemberModelQueryChanged(val value: String) : CrewAction
    data object RefreshMemberModels : CrewAction
    data class MemberReasoningChanged(val value: String) : CrewAction
    data object SaveMember : CrewAction
    data class SetMemberActive(val id: String, val active: Boolean) : CrewAction
    data class InputChanged(val value: String) : CrewAction
    data class AttachPicked(val uris: List<String>) : CrewAction
    data class RemoveAttachment(val id: String) : CrewAction
    data class MentionPick(val id: String) : CrewAction
    data object MentionDismiss : CrewAction
    data object Send : CrewAction
    data object Stop : CrewAction
    data class AskSelect(val askId: String, val questionId: String, val choice: String) : CrewAction
    data class AskConfirm(val askId: String, val questionId: String, val value: Boolean) : CrewAction
    data class AskEdit(val askId: String, val questionId: String, val other: Boolean, val value: String) : CrewAction
    data class AskSubmit(val askId: String) : CrewAction
    data class AskSkip(val askId: String) : CrewAction
    data class OpenMessageMenu(val key: String) : CrewAction
    data object CloseMessageMenu : CrewAction
    data class React(val messageId: Long, val emoji: String) : CrewAction
    data class ReplyTo(val key: String) : CrewAction
    data object CancelReply : CrewAction
    data class StartEdit(val key: String) : CrewAction
    data class EditDraftChanged(val value: String) : CrewAction
    data object SaveEdit : CrewAction
    data object DismissEdit : CrewAction
    data class AskDeleteMessage(val key: String) : CrewAction
    data object ConfirmDeleteMessage : CrewAction
    data object CancelDeleteMessage : CrewAction
    data class JumpToMessage(val messageId: Long) : CrewAction
    data class SelectText(val key: String) : CrewAction
    data object ClearSelection : CrewAction
    data object ClearFocus : CrewAction
    data class Approve(val approvalId: String, val approved: Boolean) : CrewAction
}

sealed interface CrewEffect : UiEffect {
    data object Copied : CrewEffect
    data object MessageEdited : CrewEffect
    data object MessageDeleted : CrewEffect
}

internal val CrewReactionChoices = listOf("👍", "❤️", "😂", "🎉", "👀")
