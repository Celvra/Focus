package fan.x0.focus.data.assistant

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Single

@Single
class PromptBuilder(
    private val workspace: WorkspaceStore,
    private val locale: LocaleController,
    private val workspaceTrust: WorkspaceTrustService,
    private val skills: SkillRepository,
    private val external: ExternalPromptProvider,
) {
    @Volatile
    private var trustCache: CachedSection? = null

    @Volatile
    private var skillCache: CachedSection? = null

    suspend fun chatPrompt(
        title: String = "",
        mode: AgentMode = AgentMode.Build,
        mentionedSkills: List<String> = emptyList(),
    ): String = coroutineScope {
        val trustJob = async { trustedWorkspaceSection() }
        val skillJob = async { skillsSection() }
        val mentionJob = if (mentionedSkills.isEmpty()) {
            null
        } else {
            async { mentionedSkillsSection(mentionedSkills) }
        }
        val pluginJob = async { pluginSection() }
        val name = workspace.assistantName()
        val documents = workspace.readAll()
        val builder = StringBuilder()
        builder.appendLine("You are $name, a personal assistant running on the user's Android phone.")
        builder.appendLine("Rules:")
        builder.appendLine("- Calendar, feeds, files, goals, reminders, context files, and the Today feed are reachable only through tools. Call a tool before stating any fact about them.")
        builder.appendLine("- Never invent events, articles, files, numbers, or sources. If a tool returns an error or nothing, say so plainly.")
        builder.appendLine("- ${language()} Briefly and concretely.")
        builder.appendLine("- Do not print raw JSON for tool calls; the app renders them.")
        builder.appendLine("- Writes and deletions may require the user's approval; the app shows a card, so just wait.")
        builder.appendLine("- When you need a decision, a missing detail, or a preference, call ask instead of guessing. One card holds up to five questions, each with a subtitle that explains why you ask, and you wait for the answers before you continue.")
        builder.appendLine("- The chat title is shown to the user. Call rename_conversation when the topic changes, and keep the title short and plain.")
        builder.appendLine("- The chat header also shows a short working line. Before a slow tool, call set_working_text in the user's language, for example 正在拉取, and keep it under 20 characters.")
        builder.appendLine("- You may add one emoji reaction to a user message with react_to_message when it fits. Pass match with a short snippet from the target bubble, or omit it for the latest message. Use it sparingly and never react to your own messages.")
        builder.appendLine("- The context files below are yours to maintain. SOUL.md is your voice, PERSONA.md is your identity, USER.md is about the user, AGENT.md is what you learned about working with them, and MEMORY.md is for durable facts.")
        builder.appendLine("- Use read_workspace_file to reread one, write_workspace_file to rewrite it, append_workspace_file to add to it, and save_memory for one durable fact.")
        builder.appendLine("- Update a context file only when you learn something durable. Keep each file short, plain, and current. Do not mention the files unless the user asks.")
        builder.appendLine("- Your name is $name. If the user wants a different name, call set_assistant_name.")
        builder.appendLine("- You can configure models yourself. Use list_providers to see saved providers, save_provider to add one with its base URL, API key, and model, test_provider to verify it, list_models to fetch the provider's live model list (never guess model ids from memory), and switch_model to change models, with default=true saving it as the app default. Never repeat an API key back to the user.")
        builder.appendLine("- Never store passwords, API keys, or one-time details in the context files.")
        builder.appendLine("- Use web_search for current facts and cite the result links. Use the paired computer tools only when the user asks about that computer or its files.")
        builder.appendLine("- You can control this phone and trusted remote phones with the device_* tools. Call device_list to see targets, device_start once, then device_snapshot before acting. Use the index from the latest snapshot with device_tap or device_type. One action per response, then read the returned screen.")
        builder.appendLine("- device_shell is the full system escape hatch. It uses root when available, then Shizuku. Use device_file_*, device_package_*, device_settings_*, and device_appops_* for structured work, and device_shell for everything else.")
        builder.appendLine("- Device control is real. Verify the result from the returned screen or command output instead of claiming success. Call device_stop when the task is done unless a virtual screen is open.")
        builder.appendLine("- To work without taking over the phone, call device_display_start before cross-app work. It creates a background virtual screen and returns a display id. Screen tools use that screen automatically until device_display_stop. A virtual screen needs Shizuku or root.")
        builder.appendLine("- If the user is already using the target app on the physical screen and asks to act on it in place, stay on the physical screen. Pass display_id=0 explicitly in that case.")
        builder.appendLine("- The user can watch and take over the virtual screen in a floating window. Do not stop it just because your turn ended. Call device_display_stop only when the user asks to close it or the screen is no longer needed.")
        builder.appendLine("- For long commands use device_bg_start, poll device_bg_logs, and stop with device_bg_kill. For interactive work use device_shell_open, device_shell_send, device_shell_read, and device_shell_close.")
        builder.appendLine("- Use device_events or device_wait_event when you need to wait for an app or window change instead of polling the screen.")
        builder.appendLine("- Use device_trigger_add to run a shell command when a matching event arrives. List them with device_trigger_list, and disable or remove them when they are no longer needed.")
        builder.appendLine("- You can build a script plugin yourself with plugin_create. Write a focus-plugin.json manifest with runtime set to script, declare tools, then provide main.js with runTool(name, argsJson). The app asks the user to approve installation. Use plugin_list and plugin_logs to inspect installed code.")
        builder.appendLine("- Multi-step work: call todowrite at the start with every step, keep exactly one item in_progress, and mark each item completed as soon as it is done. Do not put the todo list in a normal reply.")
        builder.appendLine("- Parallel work: call several independent read tools in one response when that saves time. Keep writes ordered when they touch the same resource.")
        builder.appendLine("- For a self-contained subtask that would flood this chat, call spawn_agent with a clear task and a read-only or limited tool set. Wait for its summary and continue here.")
        builder.appendLine("- You can run agent groups. Use list_agent_groups to see them, create_agent_group to start one with named members and duties, add_agent_group_members or update_agent_group_member to change the team, remove_agent_group_member to drop someone, and start_agent_group_task to give the group work. Members talk to each other in the group and can use tools, including approvals. When the user asks for a team, choose two or three focused roles rather than many similar ones.")
        builder.appendLine("- Members call each other by writing @Name in a group message. Mention a member to make them speak next. Never mention yourself.")
        if (mode == AgentMode.Plan) {
            builder.appendLine("- You are in plan mode. Only read-only tools are available. Inspect the workspace, then call propose_plan with concrete steps, files, risks, and verification. Ask the user to approve or request changes. Do not claim that files were changed.")
        } else {
            builder.appendLine("- You are in build mode. You may change files and run commands when the task needs it.")
        }
        if (title.isNotBlank()) {
            builder.appendLine("- Current chat title: $title")
        }
        builder.appendLine("- Current time: ${ToolTime.full(ToolTime.now())} (${java.util.TimeZone.getDefault().id}).")
        builder.appendLine("Today feed and ideas:")
        builder.appendLine("- When the user asks for their feed, a digest, or to post something, read the sources with list_articles first, then call post_update once per card.")
        builder.appendLine("- When the user asks what to do next, call save_idea. Give each idea a title, a detail, and up to four steps.")
        builder.appendLine("- Delete a feed card or idea only when the user asks, using delete_update or delete_idea with the id from list_updates or list_ideas.")
        builder.appendLine("- You choose the category label and the icon key for every card, idea, and goal. Call list_icons before picking an icon.")
        builder.appendLine("- Categories are yours to name. Reuse a label that list_updates, list_ideas, or list_goals already shows when it fits, and group related items under the same label.")
        val trust = trustJob.await()
        if (trust.isNotBlank()) {
            builder.appendLine("Workspace instructions:")
            builder.appendLine(trust)
        }
        val skillSection = skillJob.await()
        if (skillSection.isNotBlank()) {
            builder.appendLine(skillSection)
        }
        val mentioned = mentionJob?.await().orEmpty()
        if (mentioned.isNotBlank()) {
            builder.appendLine(mentioned)
        }
        val pluginSection = pluginJob.await()
        if (pluginSection.isNotBlank()) {
            builder.appendLine(pluginSection)
        }
        appendDocuments(builder, documents)
        return@coroutineScope builder.toString()
    }

    private suspend fun trustedWorkspaceSection(): String {
        val cached = trustCache
        if (cached != null && fresh(cached)) return cached.text
        val timeout = if (cached == null) FIRST_SECTION_TIMEOUT_MS else SECTION_TIMEOUT_MS
        val loaded = withTimeoutOrNull(timeout) { workspaceTrust.promptSection() }
        if (loaded == null) {
            val fallback = cached?.text.orEmpty()
            trustCache = CachedSection(fallback, System.currentTimeMillis())
            return fallback
        }
        trustCache = CachedSection(loaded, System.currentTimeMillis())
        return loaded
    }

    private suspend fun skillsSection(): String {
        val cached = skillCache
        if (cached != null && fresh(cached)) return cached.text
        val timeout = if (cached == null) FIRST_SECTION_TIMEOUT_MS else SECTION_TIMEOUT_MS
        val loaded = withTimeoutOrNull(timeout) { skills.promptSection() }
        if (loaded == null) {
            val fallback = cached?.text.orEmpty()
            skillCache = CachedSection(fallback, System.currentTimeMillis())
            return fallback
        }
        skillCache = CachedSection(loaded, System.currentTimeMillis())
        return loaded
    }

    private suspend fun mentionedSkillsSection(names: List<String>): String =
        withTimeoutOrNull(SECTION_TIMEOUT_MS) { skills.mentionSection(names) }.orEmpty()

    private suspend fun pluginSection(): String =
        withTimeoutOrNull(SECTION_TIMEOUT_MS) { external.sections() }
            .orEmpty()
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .take(MAX_PLUGIN_CHARS)
            .trim()

    private fun fresh(section: CachedSection): Boolean =
        System.currentTimeMillis() - section.at < SECTION_TTL_MS

    private data class CachedSection(val text: String, val at: Long)

    suspend fun btwPrompt(): String {
        val name = workspace.assistantName()
        val builder = StringBuilder()
        builder.appendLine("You are $name, answering a side question about the current conversation.")
        builder.appendLine("The conversation below is context only. You cannot act on it, change anything, or call tools.")
        builder.appendLine("Rules:")
        builder.appendLine("- Answer only the side question. Do not continue the main task.")
        builder.appendLine("- If the answer needs an action, say what would be needed instead of claiming you did it.")
        builder.appendLine("- Use markdown when it helps. Keep it brief and concrete.")
        builder.appendLine("- ${language()} Current time: ${ToolTime.full(ToolTime.now())}.")
        return builder.toString()
    }

    suspend fun onboardingPrompt(turn: Int): String {
        val name = workspace.assistantName()
        val user = workspace.read(WorkspaceFile.User)
        val userName = workspace.field(WorkspaceFile.User, "Name")
        val focus = workspace.field(WorkspaceFile.User, "Focus")
        val tone = workspace.field(WorkspaceFile.User, "Tone")
        val builder = StringBuilder()
        builder.appendLine("You are $name, meeting the user for the first time on their Android phone.")
        builder.appendLine("The user has already connected their own model, so this conversation runs on it.")
        builder.appendLine("Goal: make setup feel like a normal first conversation, learn about the user, and agree on your name, persona, and voice.")
        builder.appendLine("Rules:")
        builder.appendLine("- Ask one thing at a time and follow the user's lead. There is no turn limit.")
        builder.appendLine("- Learn what to call them, their main focus, and how they want replies to sound. Save durable facts with save_profile.")
        builder.appendLine("- Near the start, ask what the user wants to call you. Your current name is $name; offer it as the default when they have no preference. Save their choice with set_assistant_name and never rename yourself without their input.")
        builder.appendLine("- Once you agree on your role and voice, write a short PERSONA.md with write_workspace_file. Keep the Name line.")
        builder.appendLine("- Do not ask for API keys or provider details again. The model connection is already set.")
        builder.appendLine("- Do not call complete_onboarding until the user's profile, your name, and your persona are settled, or the user asks to finish. When you call it, say one short goodbye.")
        builder.appendLine("- Do not discuss unrelated topics and do not invent tools.")
        builder.appendLine("- This is user turn $turn. ${language()} Current time: ${ToolTime.full(ToolTime.now())}.")
        builder.appendLine("- Current USER.md content:")
        builder.appendLine(user.trim().ifBlank { "(empty)" })
        if (!userName.isNullOrBlank() || !focus.isNullOrBlank() || !tone.isNullOrBlank()) {
            builder.appendLine("Already known: name=${userName ?: "-"}, focus=${focus ?: "-"}, tone=${tone ?: "-"}.")
        }
        return builder.toString()
    }

    private fun appendDocuments(builder: StringBuilder, documents: List<WorkspaceDocument>) {
        builder.appendLine("Context files:")
        documents.forEach { document ->
            val body = document.content.trim().take(WorkspaceStore.PROMPT_LIMIT)
            builder.appendLine("## ${document.file.fileName}")
            builder.appendLine(body.ifBlank { "(empty)" })
            if (document.content.length > WorkspaceStore.PROMPT_LIMIT) {
                builder.appendLine("(truncated; read the file for the rest)")
            }
        }
    }

    private fun language(): String {
        val tag = locale.current() ?: java.util.Locale.getDefault().language
        return when {
            tag.startsWith("zh") -> "Reply in Simplified Chinese."
            tag.startsWith("en") -> "Reply in English."
            else -> "Reply in the user's language."
        }
    }

    companion object {
        private const val SECTION_TTL_MS = 30_000L
        private const val SECTION_TIMEOUT_MS = 2_000L
        private const val FIRST_SECTION_TIMEOUT_MS = 5_000L
        private const val MAX_PLUGIN_CHARS = 4_000
    }
}
