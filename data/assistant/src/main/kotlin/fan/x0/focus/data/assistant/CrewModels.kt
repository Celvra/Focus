package fan.x0.focus.data.assistant

import kotlin.math.abs

enum class CrewAuthorType { User, Agent, System }

enum class CrewMessageKind { Text, Notice }

data class AgentGroup(
    val id: String,
    val name: String,
    val topic: String,
    val avatarPreset: Int,
    val avatarPalette: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val lastMessageAt: Long,
    val archived: Boolean = false,
)

data class CrewMember(
    val id: String,
    val groupId: String,
    val name: String,
    val duty: String,
    val avatarPreset: Int,
    val avatarPalette: Int,
    val avatarImage: String,
    val position: Int,
    val active: Boolean,
    val providerId: String = "",
    val model: String = "",
    val reasoning: String = "",
)

data class CrewMessage(
    val id: Long,
    val groupId: String,
    val authorType: CrewAuthorType,
    val authorId: String,
    val authorName: String,
    val content: String,
    val replyToId: Long? = null,
    val reactions: List<String> = emptyList(),
    val kind: CrewMessageKind = CrewMessageKind.Text,
    val createdAt: Long = 0,
    val reasoning: String = "",
    val elapsedMs: Long = 0,
    val tools: List<CrewToolTrace> = emptyList(),
    val attachments: List<ChatAttachment> = emptyList(),
)

data class CrewGroupDetail(
    val group: AgentGroup,
    val members: List<CrewMember>,
)

data class CrewAvatarChoice(
    val preset: Int,
    val palette: Int,
)

data class PersistedCrewRun(
    val groupId: String,
    val startedAt: Long,
    val lastUserMessageId: Long,
    val focusMemberIds: Set<String>,
)

object CrewAvatars {
    const val PRESET_COUNT = 8
    const val PALETTE_COUNT = 6

    fun defaultFor(seed: Int): CrewAvatarChoice = CrewAvatarChoice(
        preset = abs(seed) % PRESET_COUNT,
        palette = abs(seed / PRESET_COUNT) % PALETTE_COUNT,
    )

    fun parse(value: String?, seed: Int): CrewAvatarChoice {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) return defaultFor(seed)
        val parts = raw.replace(',', ':').split(':').filter { it.isNotBlank() }
        val preset = parts.getOrNull(0)?.toIntOrNull()
        val palette = parts.getOrNull(1)?.toIntOrNull()
        val fallback = defaultFor(seed)
        return CrewAvatarChoice(
            preset = preset?.mod(PRESET_COUNT) ?: fallback.preset,
            palette = palette?.mod(PALETTE_COUNT) ?: fallback.palette,
        )
    }

    fun describe(preset: Int, palette: Int): String = "$preset:$palette"
}

object CrewDefaults {
    const val LEAD_NAME = "Coordinator"
    const val LEAD_DUTY = "Breaks the task down, keeps the group moving, and summarizes the result."
    const val MEMBER_NAME = "Builder"
    const val MEMBER_DUTY = "Turns the plan into concrete work and reports what changed."
}
