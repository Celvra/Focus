package fan.x0.focus.feature.crew

import fan.x0.focus.data.assistant.AgentGroup
import fan.x0.focus.data.assistant.CrewAuthorType
import fan.x0.focus.data.assistant.CrewAvatarStore
import fan.x0.focus.data.assistant.CrewMember
import fan.x0.focus.data.assistant.CrewMentions
import fan.x0.focus.data.assistant.CrewMessage
import fan.x0.focus.data.assistant.CrewMessageKind
import fan.x0.focus.data.assistant.CrewRunEntry

internal fun CrewMember.toUi(avatars: CrewAvatarStore): CrewMemberUi = CrewMemberUi(
    id = id,
    name = name,
    duty = duty,
    avatarPreset = avatarPreset,
    avatarPalette = avatarPalette,
    avatarPath = avatars.path(avatarImage),
)

internal fun AgentGroup.toUi(
    members: List<CrewMember>,
    last: CrewMessage?,
    running: Boolean,
    avatars: CrewAvatarStore,
): CrewGroupUi = CrewGroupUi(
    id = id,
    name = name,
    topic = topic,
    avatarPreset = avatarPreset,
    avatarPalette = avatarPalette,
    memberCount = members.size,
    members = members.map { it.toUi(avatars) },
    preview = last?.content?.replace(Regex("\\s+"), " ")?.trim().orEmpty(),
    previewAuthor = when (last?.authorType) {
        CrewAuthorType.User -> ""
        CrewAuthorType.Agent -> last.authorName
        CrewAuthorType.System -> "Focus"
        null -> ""
    },
    updatedAt = if (last != null) last.createdAt else updatedAt,
    running = running,
    archived = archived,
)

internal fun List<CrewMessage>.toEntries(
    members: List<CrewMember>,
    avatars: CrewAvatarStore,
): List<CrewEntryUi> {
    val byId = associateBy { it.id }
    return mapNotNull { message ->
        when {
            message.kind == CrewMessageKind.Notice -> CrewEntryUi.Notice(
                key = "message-${message.id}",
                text = message.content,
            )
            message.authorType == CrewAuthorType.User -> CrewEntryUi.Message(
                key = "message-${message.id}",
                messageId = message.id,
                fromUser = true,
                authorId = message.authorId,
                author = "",
                duty = "",
                avatarPreset = 0,
                avatarPalette = 0,
                avatarPath = null,
                text = message.content,
                replyTo = message.replyTo(byId),
                reactions = message.reactions,
                mentions = message.mentions(members),
            )
            message.authorType == CrewAuthorType.Agent -> {
                val member = members.firstOrNull { it.id == message.authorId }
                CrewEntryUi.Message(
                    key = "message-${message.id}",
                    messageId = message.id,
                    fromUser = false,
                    authorId = message.authorId,
                    author = message.authorName,
                    duty = member?.duty.orEmpty(),
                    avatarPreset = member?.avatarPreset ?: 0,
                    avatarPalette = member?.avatarPalette ?: 0,
                    avatarPath = member?.let { avatars.path(it.avatarImage) },
                    text = message.content,
                    replyTo = message.replyTo(byId),
                    reactions = message.reactions,
                    mentions = message.mentions(members),
                )
            }
            else -> CrewEntryUi.Notice(
                key = "message-${message.id}",
                text = message.content,
            )
        }
    }
}

internal fun CrewRunEntry.toUi(
    members: List<CrewMember>,
    messages: Map<Long, CrewMessage>,
    avatars: CrewAvatarStore,
): CrewEntryUi? = when (this) {
    is CrewRunEntry.User -> CrewEntryUi.Message(
        key = key,
        messageId = messageId,
        fromUser = true,
        authorId = "user",
        author = "",
        duty = "",
        avatarPreset = 0,
        avatarPalette = 0,
        avatarPath = null,
        text = text,
        replyTo = replyToId?.let { id ->
            messages[id]?.toQuote() ?: CrewQuoteUi(messageId = id, author = "", text = "", deleted = true)
        },
        mentions = CrewMentions.names(text, members).map { CrewMentionChipUi(it) },
    )
    is CrewRunEntry.Agent -> {
        if (!streaming && text.isBlank() && tools.isEmpty() && error == null) {
            null
        } else {
            CrewEntryUi.Message(
                key = key,
                messageId = messageId,
                fromUser = false,
                authorId = member.id,
                author = member.name,
                duty = member.duty,
                avatarPreset = member.avatarPreset,
                avatarPalette = member.avatarPalette,
                avatarPath = avatars.path(member.avatarImage),
                text = text,
                streaming = streaming,
                activity = activity,
                mentions = CrewMentions.names(text, members).map { CrewMentionChipUi(it) },
                tools = tools.map { CrewToolUi(it.id, it.label, it.status, it.summary) },
                error = error,
            )
        }
    }
    is CrewRunEntry.Approval -> CrewEntryUi.Approval(
        key = key,
        id = card.id,
        title = card.title,
        summary = card.summary,
        steps = card.steps,
        phase = card.phase,
    )
    is CrewRunEntry.Notice -> CrewEntryUi.Notice(key, text)
}

internal fun CrewMessage.toQuote(): CrewQuoteUi = CrewQuoteUi(
    messageId = id,
    author = if (authorType == CrewAuthorType.User) "" else authorName,
    text = content.replace(Regex("\\s+"), " ").trim().take(200),
)

private fun CrewMessage.replyTo(byId: Map<Long, CrewMessage>): CrewQuoteUi? =
    replyToId?.let { id ->
        byId[id]?.toQuote() ?: CrewQuoteUi(
            messageId = id,
            author = "",
            text = "",
            deleted = true,
        )
    }

private fun CrewMessage.mentions(members: List<CrewMember>): List<CrewMentionChipUi> =
    CrewMentions.ids(content, members).mapNotNull { id ->
        members.firstOrNull { it.id == id }?.let { CrewMentionChipUi(it.name) }
    }
