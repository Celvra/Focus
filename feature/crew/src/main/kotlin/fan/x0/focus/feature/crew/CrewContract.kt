package fan.x0.focus.feature.crew

import fan.x0.focus.core.mvi.UiAction
import fan.x0.focus.core.mvi.UiEffect
import fan.x0.focus.core.mvi.UiState
import fan.x0.focus.data.assistant.CrewApprovalPhase
import fan.x0.focus.data.assistant.CrewToolStatus

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
)

data class CrewQuoteUi(
    val messageId: Long,
    val author: String,
    val text: String,
)

data class CrewMentionChipUi(
    val name: String,
)

data class CrewToolUi(
    val id: String,
    val label: String,
    val status: CrewToolStatus,
    val summary: String,
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
        val error: String? = null,
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
    val history: List<CrewEntryUi> = emptyList(),
    val live: List<CrewEntryUi> = emptyList(),
    val running: Boolean = false,
    val working: String = "",
    val input: String = "",
    val sending: Boolean = false,
    val mention: CrewMentionUi = CrewMentionUi(),
    val reply: CrewQuoteUi? = null,
    val menu: CrewMessageMenuUi? = null,
    val memberSheetOpen: Boolean = false,
    val groupDraft: GroupDraft? = null,
    val memberDraft: MemberDraft? = null,
    val deleteGroup: CrewGroupUi? = null,
    val removeMember: CrewMemberUi? = null,
    val saving: Boolean = false,
) : UiState {
    val entries: List<CrewEntryUi>
        get() {
            val persisted = history.mapNotNullTo(mutableSetOf()) {
                (it as? CrewEntryUi.Message)?.messageId
            }
            return history + live.filterNot {
                it is CrewEntryUi.Message && it.messageId != null && it.messageId in persisted
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
    data object SaveMember : CrewAction
    data class AskRemoveMember(val id: String) : CrewAction
    data object ConfirmRemoveMember : CrewAction
    data object CancelRemoveMember : CrewAction
    data class InputChanged(val value: String) : CrewAction
    data class MentionPick(val id: String) : CrewAction
    data object MentionDismiss : CrewAction
    data object Send : CrewAction
    data object Stop : CrewAction
    data class OpenMessageMenu(val key: String) : CrewAction
    data object CloseMessageMenu : CrewAction
    data class React(val messageId: Long, val emoji: String) : CrewAction
    data class ReplyTo(val key: String) : CrewAction
    data object CancelReply : CrewAction
    data class Approve(val approvalId: String, val approved: Boolean) : CrewAction
}

sealed interface CrewEffect : UiEffect {
    data object Copied : CrewEffect
}
