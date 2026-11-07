package fan.x0.focus.data.assistant

import org.koin.core.annotation.Single

@Single
class CrewPrompt(private val locale: LocaleController) {

    fun agentPrompt(
        group: AgentGroup,
        member: CrewMember,
        members: List<CrewMember>,
        instructions: String = "",
        skills: String = "",
    ): String {
        val builder = StringBuilder()
        builder.appendLine("You are ${member.name}, a member of the agent group \"${group.name}\".")
        builder.appendLine("Your duty: ${member.duty.ifBlank { "Help the group reach a clear result." }}")
        if (group.topic.isNotBlank()) {
            builder.appendLine("Group topic: ${group.topic}")
        }
        builder.appendLine("Members and duties:")
        members.forEach { other ->
            val suffix = if (other.id == member.id) " (you)" else ""
            builder.appendLine("- ${other.name}$suffix: ${other.duty.ifBlank { "No duty written." }}")
        }
        builder.appendLine("Rules:")
        builder.appendLine("- Speak only as yourself. Never write another member's lines.")
        builder.appendLine("- Read the whole transcript before replying. Build on what others said and do not repeat them.")
        builder.appendLine("- Keep the reply short and concrete. The user reads this as a group chat.")
        builder.appendLine("- Write @Name to call another member. That member speaks next. Never mention yourself. Use @all only when every member must act.")
        builder.appendLine("- You may use tools. Use a tool before claiming a fact about files, feeds, calendar, goals, reminders, the paired computer, or a device.")
        builder.appendLine("- To operate a phone, call device_list, device_start once, then device_snapshot. Use the returned node index and read the new screen after each action.")
        builder.appendLine("- Use device_bg_start for long commands and device_events or device_wait_event when you need to wait for a screen change.")
        builder.appendLine("- For cross-app work that should not disturb the phone, call device_display_start first and stop it with device_display_stop. Screen tools use the virtual screen automatically; pass display_id=0 only for the physical screen.")
        builder.appendLine("- Tool actions that need approval show a card to the user. Wait for the result before claiming the action happened.")
        builder.appendLine("- Before a slow tool, call set_working_text with a short line in the user's language so the group sees what you are doing.")
        builder.appendLine("- Use the ask tool when a decision blocks the group. The user answers in a card, then the group continues.")
        builder.appendLine("- You may react to a message with react_to_group_message when an emoji fits.")
        builder.appendLine("- When the group has a clear answer, call finish_group_discussion.")
        builder.appendLine("- ${language()}")
        builder.appendLine("Current time: ${ToolTime.full(ToolTime.now())}.")
        if (instructions.isNotBlank()) {
            builder.appendLine("Workspace instructions:")
            builder.appendLine(instructions)
        }
        if (skills.isNotBlank()) {
            builder.appendLine(skills)
        }
        return builder.toString()
    }

    fun transcriptMessage(message: CrewMessage, selfId: String, quoted: CrewMessage?): String {
        val builder = StringBuilder()
        if (quoted != null) {
            builder.append("(replying to ${quoted.authorName}: ")
            builder.append(quoted.content.replace(Regex("\\s+"), " ").trim().take(400))
            builder.appendLine(")")
        }
        when (message.authorType) {
            CrewAuthorType.User -> builder.append("[User] ")
            CrewAuthorType.System -> builder.append("[System] ")
            CrewAuthorType.Agent -> {
                if (message.authorId != selfId) {
                    builder.append("[").append(message.authorName).append("] ")
                }
            }
        }
        builder.append(message.content)
        message.attachments.filter { it.kind == AttachmentKind.Text }.forEach { attachment ->
            val body = attachment.text ?: return@forEach
            if (body.isBlank()) return@forEach
            builder.append("\n\n<<file: ").append(attachment.name).append(">>\n")
            builder.append(body)
            builder.append("\n<<end file>>")
        }
        return builder.toString().trim()
    }

    fun turnPrompt(member: CrewMember): String {
        return "Respond now as ${member.name}. Follow your duty, address people by @Name when needed, and keep it short."
    }

    private fun language(): String {
        val tag = locale.current() ?: java.util.Locale.getDefault().language
        return when {
            tag.startsWith("zh") -> "Reply in Simplified Chinese."
            tag.startsWith("en") -> "Reply in English."
            else -> "Reply in the user's language."
        }
    }
}
