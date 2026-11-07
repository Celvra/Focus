package fan.x0.focus.feature.chat

import androidx.lifecycle.viewModelScope
import fan.x0.focus.core.error.AppError
import fan.x0.focus.core.error.AppErrorType
import fan.x0.focus.core.mvi.BaseViewModel
import fan.x0.focus.data.assistant.AgentMode
import fan.x0.focus.data.assistant.AskAnswer
import fan.x0.focus.data.assistant.AskKind
import fan.x0.focus.data.assistant.AttachmentKind
import fan.x0.focus.data.assistant.AttachmentLoader
import fan.x0.focus.data.assistant.AssistantSettings
import fan.x0.focus.data.assistant.BtwRunner
import fan.x0.focus.data.assistant.ChatAttachment
import fan.x0.focus.data.assistant.ChatCommand
import fan.x0.focus.data.assistant.ChatDetailSettings
import fan.x0.focus.data.assistant.ChatEvent
import fan.x0.focus.data.assistant.ChatMessage
import fan.x0.focus.data.assistant.ChatRole
import fan.x0.focus.data.assistant.ChatStore
import fan.x0.focus.data.assistant.ChatSuggestionGenerator
import fan.x0.focus.data.assistant.ChatSwitcher
import fan.x0.focus.data.assistant.ChatTurnApprovalPhase
import fan.x0.focus.data.assistant.ChatTurnEntry
import fan.x0.focus.data.assistant.ChatTurnResult
import fan.x0.focus.data.assistant.ChatTurnRunner
import fan.x0.focus.data.assistant.ChatTurnState
import fan.x0.focus.data.assistant.ChatTurnStep
import fan.x0.focus.data.assistant.ChatTurnStepStatus
import fan.x0.focus.data.assistant.ChatTurnTrustPhase
import fan.x0.focus.data.assistant.CommandPolicy
import fan.x0.focus.data.assistant.ConversationCompaction
import fan.x0.focus.data.assistant.ConversationKind
import fan.x0.focus.data.assistant.ConversationTodo
import fan.x0.focus.data.assistant.FileMentionBinding
import fan.x0.focus.data.assistant.FileMentionEntry
import fan.x0.focus.data.assistant.FileMentionService
import fan.x0.focus.data.assistant.MainChannelSession
import fan.x0.focus.data.assistant.MessageStats
import fan.x0.focus.data.assistant.MentionItemUi
import fan.x0.focus.data.assistant.MentionKind
import fan.x0.focus.data.assistant.MentionText
import fan.x0.focus.data.assistant.MentionUi
import fan.x0.focus.data.assistant.SideResultCard
import fan.x0.focus.data.assistant.SideChatManager
import fan.x0.focus.data.assistant.TaskStatus
import fan.x0.focus.data.assistant.WORKSPACE_BIND_PREFIX
import fan.x0.focus.data.assistant.WORKSPACE_SWITCH_ID
import fan.x0.focus.data.assistant.PlanStatus
import fan.x0.focus.data.assistant.PolicySettings
import fan.x0.focus.data.assistant.ProposedPlan
import fan.x0.focus.data.assistant.ProviderModelCatalog
import fan.x0.focus.data.assistant.ReasoningLevel
import fan.x0.focus.data.assistant.RouterSession
import fan.x0.focus.data.assistant.SkillEntry
import fan.x0.focus.data.assistant.SkillRepository
import fan.x0.focus.data.assistant.SubAgentRunState
import fan.x0.focus.data.assistant.SubAgentRunStatus
import fan.x0.focus.data.assistant.SubAgentRunStore
import fan.x0.focus.data.assistant.TodoPriority
import fan.x0.focus.data.assistant.TodoStatus
import fan.x0.focus.data.assistant.ToolCatalog
import fan.x0.focus.data.assistant.ToolLabels
import fan.x0.focus.data.assistant.ToolPayload
import fan.x0.focus.data.assistant.TurnOptions
import fan.x0.focus.data.assistant.WorkerProfile
import fan.x0.focus.data.assistant.WorkspaceStore
import fan.x0.focus.data.im.ImChatKind
import fan.x0.focus.data.im.ImChatStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.KoinViewModel
import java.util.Locale

private const val REVEAL_FRAME_MS = 33L
private const val REVEAL_CATCHUP_FRAMES = 18
private const val REVEAL_MIN_GROUP = 3
private const val REVEAL_HANDOFF_MS = 280L
private const val REVEAL_JOIN_TIMEOUT_MS = 4_000L
private const val TURN_IDLE_TIMEOUT_MS = 3_000L
private const val MENTION_TTL_MS = 30_000L
private const val MENTION_FILE_DELAY_MS = 140L
private const val MAX_MENTION_ITEMS = 50
private const val DEFAULT_SCOPE = "default"

@KoinViewModel
class ChatViewModel(
    private val chats: ChatStore,
    private val switcher: ChatSwitcher,
    private val workspace: WorkspaceStore,
    private val settings: AssistantSettings,
    private val providerModels: ProviderModelCatalog,
    private val loader: AttachmentLoader,
    private val turns: ChatTurnRunner,
    private val suggestionGenerator: ChatSuggestionGenerator,
    private val details: ChatDetailSettings,
    private val policy: PolicySettings,
    private val btw: BtwRunner,
    private val subAgents: SubAgentRunStore,
    private val skills: SkillRepository,
    private val fileMentions: FileMentionService,
    private val imChats: ImChatStore,
    private val mainChannel: MainChannelSession,
    private val sideChats: SideChatManager,
    private val router: RouterSession,
) : BaseViewModel<ChatAction, ChatUiState, ChatEffect>(ChatUiState()) {

    private var loadJob: Job? = null
    private var modelJob: Job? = null
    private var suggestionJob: Job? = null
    private var btwJob: Job? = null
    private var statsJob: Job? = null
    private var displayJob: Job? = null
    private var mentionJob: Job? = null
    private var mentionSkills: List<SkillEntry> = emptyList()
    private var mentionLoadedAt = 0L
    private var mentionFileJob: Job? = null
    private var mentionFiles: List<FileMentionEntry> = emptyList()
    private var mentionFileQuery: String? = null
    private var mentionFileLoadedAt = 0L
    private var mentionBinding: FileMentionBinding? = null
    private var mentionBindings: List<FileMentionBinding> = emptyList()
    private var mentionBindingsJob: Job? = null
    private var mentionBindingsScope = ""
    private var mentionBindingsLoadedAt = 0L
    private var mentionPickingWorkspace = false
    private var displayConversationId = ""
    private var displayTurnId = 0L
    private var displayTarget = ""
    private var displayText = ""
    private var liveUserTurnId = 0L
    private var liveUserText = ""
    private var localTurnId = 0L
    private var loadGeneration = 0
    private var awaitingUserTurn = false
    private var lastSend: PendingSend? = null
    private var redoEntry: PendingSend? = null
    private var mainSubmitting = false
    private var mainWasWorking = false
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    init {
        ensureMentionSkills()
        ensureMentionBindings()
        loadJob = viewModelScope.launch {
            val requested = switcher.current.value
                ?.let { chats.conversation(it) }
            val conversation = requested ?: chats.ensureMainConversation()
            load(conversation.id)
        }
        viewModelScope.launch {
            switcher.command.filterNotNull().collect { command ->
                when (command) {
                    is ChatCommand.Open -> switchTo(command.conversationId)
                    ChatCommand.New -> newConversation()
                    is ChatCommand.Ask -> ask(command.text)
                }
                switcher.consume(command)
            }
        }
        viewModelScope.launch {
            turns.states.collect { states ->
                val id = currentState.conversationId
                if (id.isBlank() || currentState.loading) return@collect
                states[id]?.let(::applyTurn)
            }
        }
        viewModelScope.launch {
            turns.results.collect { results ->
                val id = currentState.conversationId
                if (id.isBlank() || currentState.loading) return@collect
                results[id]?.let { handleResult(it) }
            }
        }
        viewModelScope.launch {
            turns.titleUpdates.collect { id ->
                if (id == currentState.conversationId && !currentState.loading) refreshTitle()
            }
        }
        viewModelScope.launch {
            chats.changes.collect { id ->
                val conversationId = currentState.conversationId
                if (id != conversationId || currentState.loading) return@collect
                if (currentState.sending || turns.isRunning(conversationId)) return@collect
                reload()
            }
        }
        viewModelScope.launch {
            details.config.collect { config ->
                updateState { copy(statsConfig = config) }
            }
        }
        viewModelScope.launch {
            policy.config.collect { config ->
                updateState { copy(policy = config.policy) }
            }
        }
        viewModelScope.launch {
            subAgents.runs.collect { runs ->
                updateState {
                    copy(
                        subAgentRuns = runs.mapValues { it.value.toUi() },
                        subAgentOpenId = subAgentOpenId?.takeIf { it in runs },
                    )
                }
            }
        }
        viewModelScope.launch {
            settings.config.collect { config ->
                updateState {
                    if (loading) return@updateState this
                    val resolved = providerId
                        .takeIf { id -> config.providers.any { it.id == id } }
                        ?: config.activeProviderId
                    val provider = config.providers.firstOrNull { it.id == resolved }
                        ?: config.activeProvider
                    val previous = providers.firstOrNull { it.id == providerId }
                    val nextModel = when {
                        provider == null -> model
                        resolved != providerId -> provider.model
                        model.isBlank() -> provider.model
                        previous != null && model == previous.model -> provider.model
                        else -> model
                    }
                    val options = config.providers.map {
                        ProviderOption(it.id, it.name, it.model, it.icon, it.models)
                    }
                    val providersChanged = options != providers
                    copy(
                        providers = options,
                        providerId = resolved,
                        model = nextModel,
                        models = if (providersChanged) emptyMap() else models,
                        modelsLoaded = if (providersChanged) false else modelsLoaded,
                    )
                }
            }
        }
        viewModelScope.launch {
            mainChannel.state.collect { channel ->
                val isMain = currentState.isMain
                updateState {
                    copy(
                        routerModel = channel.routerModel,
                        sideActiveCount = channel.activeSideChats,
                        sideUnreadCount = channel.unreadResults,
                    )
                }
                if (!isMain || currentState.loading) return@collect
                when {
                    channel.working -> {
                        mainSubmitting = false
                        mainWasWorking = true
                        updateState {
                            copy(
                                sending = true,
                                workingText = channel.workingText.ifBlank { workingText },
                            )
                        }
                    }
                    mainWasWorking -> {
                        mainWasWorking = false
                        mainSubmitting = false
                        updateState { copy(sending = false, workingText = "") }
                        reload()
                    }
                    !mainSubmitting -> updateState {
                        if (sending) copy(sending = false, workingText = "") else this
                    }
                }
            }
        }
    }

    override fun onAction(action: ChatAction) {
        when (action) {
            is ChatAction.InputChanged -> inputChanged(action.value)
            is ChatAction.Suggest -> {
                updateState { copy(input = action.text) }
                send()
            }
            ChatAction.Send -> send()
            ChatAction.Stop -> stop()
            is ChatAction.Decide -> decide(action)
            is ChatAction.DecideTrust -> decideTrust(action)
            is ChatAction.DecidePlan -> decidePlan(action)
            is ChatAction.AskSelect -> selectAskOption(action)
            is ChatAction.AskConfirm -> updateDraft(action.askId, action.questionId) {
                it.copy(confirmed = action.value)
            }
            is ChatAction.AskEdit -> editAsk(action)
            is ChatAction.AskSubmit -> submitAsk(action.askId, skipped = false)
            is ChatAction.AskSkip -> submitAsk(action.askId, skipped = true)
            ChatAction.NewConversation -> viewModelScope.launch { newConversation() }
            ChatAction.CloseBtw -> closeBtw()
            ChatAction.RetryBtw -> retryBtw()
            is ChatAction.OpenPicker -> {
                updateState { copy(picker = action.picker, modelQuery = "") }
                if (action.picker == ComposerPicker.Model && !currentState.modelsLoaded) {
                    loadModels()
                }
            }
            is ChatAction.SelectModel -> selectModel(action.providerId, action.model)
            is ChatAction.ModelQueryChanged -> updateState { copy(modelQuery = action.value) }
            ChatAction.RefreshModels -> loadModels(force = true)
            is ChatAction.SelectReasoning -> selectReasoning(action.level)
            is ChatAction.AttachPicked -> attach(action.uris)
            is ChatAction.RemoveAttachment -> updateState {
                copy(attachments = attachments.filterNot { it.id == action.id })
            }
            is ChatAction.SetMode -> setMode(action.mode)
            is ChatAction.OpenMessageMenu -> openMenu(action.key)
            ChatAction.CloseMessageMenu -> updateState { copy(menu = null) }
            is ChatAction.ReplyTo -> replyTo(action.key)
            ChatAction.CancelReply -> updateState { copy(reply = null) }
            is ChatAction.StartEdit -> startEdit(action.key)
            is ChatAction.EditDraftChanged -> updateState {
                copy(edit = edit?.copy(draft = action.value))
            }
            ChatAction.SaveEdit -> saveEdit()
            ChatAction.CancelEdit -> updateState { copy(edit = null) }
            is ChatAction.AskDelete -> askDelete(action.key)
            ChatAction.ConfirmDelete -> confirmDelete()
            ChatAction.CancelDelete -> updateState { copy(pendingDelete = null) }
            is ChatAction.SelectText -> updateState {
                copy(selectionKey = action.key, menu = null)
            }
            ChatAction.ClearSelection -> updateState { copy(selectionKey = null) }
            is ChatAction.JumpToMessage -> updateState {
                copy(highlightKey = action.key, menu = null, selectionKey = null)
            }
            ChatAction.ConsumeHighlight -> updateState { copy(highlightKey = null) }
            is ChatAction.SlashInputChanged -> inputChanged(action.value)
            is ChatAction.SlashMove -> moveSlash(action.delta)
            is ChatAction.SlashPick -> pickSlash(action.id)
            ChatAction.SlashSelect -> selectSlash()
            ChatAction.SlashClose -> updateState { copy(slash = slash.copy(open = false)) }
            is ChatAction.MentionMove -> moveMention(action.delta)
            is ChatAction.MentionPick -> pickMention(action.id)
            ChatAction.MentionDismiss -> {
                mentionPickingWorkspace = false
                updateState { copy(mention = MentionUi()) }
            }
            ChatAction.RetryLast -> retryLast()
            ChatAction.OpenSessions -> sendEffect(ChatEffect.OpenSessions)
            ChatAction.OpenSettings -> sendEffect(ChatEffect.OpenSettings)
            is ChatAction.OpenSubAgent -> updateState { copy(subAgentOpenId = action.id) }
            ChatAction.CloseSubAgent -> updateState { copy(subAgentOpenId = null) }
            ChatAction.ConsumeMessage -> updateState { copy(message = null) }
            ChatAction.OpenSidePanel -> openSidePanel()
            ChatAction.CloseSidePanel -> updateState { copy(sidePanel = sidePanel.copy(open = false)) }
            ChatAction.RefreshSidePanel -> refreshSidePanel()
            is ChatAction.OpenSideChat -> openSideChat(action.id)
            is ChatAction.StopSideChat -> stopSideChat(action.id)
            is ChatAction.RetrySideChat -> retrySideChat(action.id)
            ChatAction.BackToMain -> backToMain()
        }
    }

    private fun inputChanged(value: String) {
        val state = currentState
        mentionPickingWorkspace = false
        if (state.slash.visible && !state.input.contains('\n') && value.contains('\n')) {
            selectSlash()
            return
        }
        val mention = mentionFor(value)
        val slash = if (mention.visible) {
            state.slash.copy(open = false)
        } else if (value.startsWith("/") && !value.contains('\n')) {
            val query = value.substringBefore(' ')
            val matches = SlashCommands.filter(query)
            if (query.contains(' ') || matches.isEmpty() || value.count { it == ' ' } > 0) {
                state.slash.copy(open = false)
            } else {
                SlashUi(
                    open = true,
                    query = query,
                    selected = 0,
                    commandIds = matches.map { it.id },
                )
            }
        } else {
            state.slash.copy(open = false)
        }
        updateState { copy(input = value, slash = slash, mention = mention) }
    }

    private fun mentionFor(value: String): MentionUi {
        val active = MentionText.active(value) ?: return MentionUi()
        if (System.currentTimeMillis() - mentionLoadedAt > MENTION_TTL_MS) {
            ensureMentionSkills()
        }
        val query = active.query
        if (mentionPickingWorkspace) {
            ensureMentionBindings()
            val items = mentionBindings.map { binding ->
                MentionItemUi(
                    id = WORKSPACE_BIND_PREFIX + binding.key,
                    kind = MentionKind.Workspace,
                    label = binding.label,
                    description = binding.path,
                    action = true,
                )
            }
            return if (items.isEmpty()) {
                MentionUi()
            } else {
                MentionUi(
                    open = true,
                    query = query,
                    start = active.start,
                    items = items,
                    selected = 0,
                )
            }
        }
        if (query.isBlank()) {
            ensureMentionBindings()
        }
        ensureMentionFiles(query)
        val scope = bindingScope()
        val key = mentionQueryKey(scope, query)
        val items = buildList {
            if (query.isBlank()) {
                mentionBinding?.let { binding ->
                    add(
                        MentionItemUi(
                            id = WORKSPACE_SWITCH_ID,
                            kind = MentionKind.Workspace,
                            label = binding.label,
                            description = binding.path,
                            action = true,
                        ),
                    )
                }
            }
            addAll(skillMentions(query))
            if (mentionFileQuery == key) {
                mentionFiles.forEach { entry ->
                    add(
                        MentionItemUi(
                            id = entry.id,
                            kind = MentionKind.File,
                            label = entry.relative,
                            description = entry.path,
                            insert = entry.insert,
                            directory = entry.directory,
                        ),
                    )
                }
            }
        }
        return if (items.isEmpty()) {
            MentionUi()
        } else {
            MentionUi(
                open = true,
                query = query,
                start = active.start,
                items = items,
                selected = 0,
            )
        }
    }

    private fun skillMentions(query: String): List<MentionItemUi> =
        mentionSkills.asSequence()
            .filter { it.enabled }
            .filter {
                query.isBlank() ||
                    it.name.contains(query, ignoreCase = true) ||
                    it.description.contains(query, ignoreCase = true)
            }
            .sortedWith(
                compareByDescending<SkillEntry> { it.name.startsWith(query, ignoreCase = true) }
                    .thenBy { it.name.lowercase(Locale.ROOT) },
            )
            .take(MAX_MENTION_ITEMS)
            .map { skill ->
                MentionItemUi(
                    id = skill.id,
                    kind = MentionKind.Skill,
                    label = skill.name,
                    description = skill.description,
                )
            }
            .toList()

    private fun ensureMentionFiles(query: String) {
        val scope = bindingScope()
        val key = mentionQueryKey(scope, query)
        val now = System.currentTimeMillis()
        if (mentionFileQuery == key && now - mentionFileLoadedAt < MENTION_TTL_MS) return
        if (mentionPickingWorkspace) return
        mentionFileJob?.cancel()
        mentionFileJob = viewModelScope.launch {
            delay(MENTION_FILE_DELAY_MS)
            try {
                val entries = fileMentions.search(scope, query)
                val binding = fileMentions.current(scope)
                if (bindingScope() == scope &&
                    MentionText.active(currentState.input)?.query == query &&
                    !mentionPickingWorkspace
                ) {
                    mentionFiles = entries
                    mentionBinding = binding
                    mentionFileQuery = key
                    mentionFileLoadedAt = System.currentTimeMillis()
                    refreshMention()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
            }
        }
    }

    private fun ensureMentionBindings() {
        val scope = bindingScope()
        val now = System.currentTimeMillis()
        if (mentionBindingsScope == scope && now - mentionBindingsLoadedAt < MENTION_TTL_MS) return
        if (mentionBindingsJob?.isActive == true) return
        mentionBindingsJob = viewModelScope.launch {
            try {
                val binding = fileMentions.current(scope)
                if (bindingScope() == scope && binding != null) {
                    mentionBinding = binding
                    refreshMention()
                }
                val candidates = fileMentions.candidates(scope)
                if (bindingScope() == scope) {
                    mentionBindings = candidates
                    mentionBindingsScope = scope
                    mentionBindingsLoadedAt = System.currentTimeMillis()
                    refreshMention()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
            }
        }
    }

    private fun bindingScope(): String = currentState.conversationId.ifBlank { DEFAULT_SCOPE }

    private fun mentionQueryKey(scope: String, query: String): String = "$scope\u0000$query"

    private fun mentionedSkillNames(text: String): List<String> =
        MentionText.mentionedNames(text, mentionSkills.filter { it.enabled }.map { it.name })

    private fun ensureMentionSkills() {
        if (mentionJob?.isActive == true) return
        mentionJob = viewModelScope.launch {
            try {
                mentionSkills = skills.appEntries()
                syncMentionNames()
                refreshMention()
                mentionSkills = skills.entries()
                mentionLoadedAt = System.currentTimeMillis()
                syncMentionNames()
                refreshMention()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                mentionLoadedAt = System.currentTimeMillis()
            }
        }
    }

    private fun syncMentionNames() {
        val names = mentionSkills.filter { it.enabled }.map { it.name }
        updateState { copy(mentionNames = names) }
    }

    private fun refreshMention() {
        val input = currentState.input
        if (MentionText.active(input) != null) {
            updateState { copy(mention = mentionFor(input)) }
        }
    }

    private fun moveMention(delta: Int) {
        val state = currentState
        if (!state.mention.visible) return
        val count = state.mention.items.size
        val next = (state.mention.selected + delta).mod(count)
        updateState { copy(mention = mention.copy(selected = next)) }
    }

    private fun pickMention(id: String) {
        val state = currentState
        if (!state.mention.visible) return
        if (id == WORKSPACE_SWITCH_ID) {
            mentionPickingWorkspace = true
            ensureMentionBindings()
            updateState { copy(mention = mentionFor(state.input)) }
            return
        }
        if (id.startsWith(WORKSPACE_BIND_PREFIX)) {
            val binding = mentionBindings.firstOrNull { WORKSPACE_BIND_PREFIX + it.key == id } ?: return
            mentionPickingWorkspace = false
            val scope = bindingScope()
            viewModelScope.launch {
                try {
                    fileMentions.bind(scope, binding)
                    mentionBinding = binding
                    mentionFiles = emptyList()
                    mentionFileQuery = null
                    mentionFileLoadedAt = 0
                    refreshMention()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                }
            }
            return
        }
        val item = state.mention.items.firstOrNull { it.id == id } ?: return
        if (item.directory) {
            val prefix = state.input.substring(0, state.mention.start)
            val text = prefix + "@" + item.insert.trimEnd('/') + "/"
            updateState { copy(input = text, mention = mentionFor(text)) }
            return
        }
        val next = MentionText.insert(state.input, state.mention.start, item.insert)
        val tokens = if (item.kind == MentionKind.File) {
            (state.fileMentionTokens + item.insert).distinct().takeLast(64)
        } else {
            state.fileMentionTokens
        }
        updateState {
            copy(
                input = next,
                mention = MentionUi(),
                fileMentionTokens = tokens,
                slash = slash.copy(open = false),
            )
        }
    }

    private fun moveSlash(delta: Int) {
        val state = currentState
        if (!state.slash.visible) return
        val count = state.slash.commandIds.size
        val next = (state.slash.selected + delta).mod(count)
        updateState { copy(slash = slash.copy(selected = next)) }
    }

    private fun selectSlash() {
        val state = currentState
        val spec = SlashCommands.filter(state.slash.query)
            .getOrNull(state.slash.selected)
            ?: return
        if (state.input.trim().equals(spec.command, ignoreCase = true)) {
            runSlash(spec)
        } else {
            updateState {
                copy(
                    input = if (spec.acceptsArgument) "${spec.command} " else spec.command,
                    slash = slash.copy(
                        open = true,
                        query = spec.command,
                        selected = 0,
                        commandIds = listOf(spec.id),
                    ),
                )
            }
        }
    }

    private fun pickSlash(id: SlashCommandId) {
        val spec = SlashCommands.all.firstOrNull { it.id == id } ?: return
        runSlash(spec)
    }

    private fun runSlash(spec: SlashCommandSpec) {
        when (spec.id) {
            SlashCommandId.Btw,
            SlashCommandId.Resume,
            -> insertSlash(spec)

            else -> {
                updateState { copy(input = "", slash = slash.copy(open = false, query = "")) }
                executeCommand(spec, "")
            }
        }
    }

    private fun insertSlash(spec: SlashCommandSpec) {
        val text = if (spec.acceptsArgument) "${spec.command} " else spec.command
        updateState {
            copy(
                input = text,
                slash = slash.copy(open = false, query = ""),
            )
        }
    }

    private fun send() {
        val state = currentState
        val text = state.input.trim()
        if (state.mention.visible) {
            state.mention.items.getOrNull(state.mention.selected)?.let { pickMention(it.id) }
            return
        }
        if (state.slash.visible) {
            selectSlash()
            return
        }
        if (text.startsWith("/")) {
            val command = text.substringBefore(' ').lowercase()
            val spec = SlashCommands.find(command)
            if (spec != null) {
                updateState { copy(input = "", slash = slash.copy(open = false)) }
                executeCommand(spec, text.removePrefix(spec.command).trim())
                return
            }
        }
        if (state.sending) return
        if (text.isBlank() && state.attachments.isEmpty()) return
        sendText(
            text = text,
            attachments = state.attachments,
            replyToId = state.reply?.messageId,
            mentionedSkills = mentionedSkillNames(text),
        )
    }

    private fun sendText(
        text: String,
        attachments: List<AttachmentUi>,
        replyToId: Long?,
        displayText: String = text,
        mentionedSkills: List<String> = emptyList(),
    ) {
        val state = currentState
        val conversationId = state.conversationId
        if (conversationId.isBlank() || turns.isRunning(conversationId)) return
        resetDisplay(conversationId)
        resetLiveUser()
        awaitingUserTurn = true
        val options = TurnOptions(
            providerId = state.providerId,
            model = state.model,
            reasoning = state.reasoningLevel,
            attachments = attachments.map { it.toModel() },
            agentMode = state.mode,
            replyToId = replyToId,
            mentionedSkills = mentionedSkills,
        )
        lastSend = PendingSend(text, attachments, replyToId, mentionedSkills)
        redoEntry = null
        suggestionJob?.cancel()
        val reply = state.reply
        updateState {
            copy(
                input = "",
                sending = true,
                compacting = false,
                workingText = "",
                live = emptyList(),
                attachments = emptyList(),
                askDrafts = emptyMap(),
                reply = null,
                retry = null,
                slash = SlashUi(open = false),
                mention = MentionUi(),
                liveStats = MessageStats(
                    providerId = state.providerId,
                    providerName = state.providerName,
                    model = state.model,
                ),
                turn = turn + 1,
                history = history + ChatEntry.User(
                    key = "local-${System.currentTimeMillis()}",
                    text = displayText,
                    attachments = attachments,
                    replyTo = reply?.let {
                        QuotedMessageUi(
                            id = it.messageId,
                            author = it.author,
                            text = it.preview,
                            fromUser = true,
                        )
                    },
                ),
            )
        }
        localTurnId = if (state.isMain) {
            mainSubmitting = true
            viewModelScope.launch {
                val delivered = mainChannel.submit(
                    text = text,
                    attachments = attachments.map { it.toModel() },
                    replyToId = replyToId,
                )
                if (delivered == null) {
                    mainSubmitting = false
                    updateState { copy(sending = false, workingText = "") }
                }
            }
            0L
        } else {
            turns.start(conversationId, text, options)?.turnId ?: 0L
        }
        startStatsTicker()
    }

    private fun startStatsTicker() {
        statsJob?.cancel()
        val startedAt = System.currentTimeMillis() - currentState.liveStats.durationMs
        statsJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                if (!currentState.sending) return@launch
                updateState {
                    if (!sending) {
                        this
                    } else {
                        copy(liveStats = liveStats.copy(durationMs = System.currentTimeMillis() - startedAt))
                    }
                }
            }
        }
    }

    private fun executeCommand(spec: SlashCommandSpec, argument: String) {
        when (spec.id) {
            SlashCommandId.Undo -> undo()
            SlashCommandId.Redo -> redo()
            SlashCommandId.Btw -> startBtw(argument)
            SlashCommandId.Side -> startSideChat(argument)
            SlashCommandId.Compact -> compact()
            SlashCommandId.New -> viewModelScope.launch { newConversation() }
            SlashCommandId.Resume -> resume(argument)
            SlashCommandId.Sessions -> sendEffect(ChatEffect.OpenSessions)
            SlashCommandId.Models -> {
                if (currentState.isMain) {
                    sendEffect(ChatEffect.OpenSettings)
                } else {
                    if (argument.isNotBlank()) {
                        updateState { copy(modelQuery = argument) }
                    }
                    onAction(ChatAction.OpenPicker(ComposerPicker.Model))
                }
            }
            SlashCommandId.Effort -> {
                if (currentState.isMain) return
                val level = ReasoningLevel.entries.firstOrNull {
                    it.name.equals(argument, ignoreCase = true)
                }
                if (level != null) selectReasoning(level) else onAction(ChatAction.OpenPicker(ComposerPicker.Reasoning))
            }
            SlashCommandId.Settings -> sendEffect(ChatEffect.OpenSettings)
            SlashCommandId.Help -> showMessage(R.string.chat_command_usage)
        }
    }

    private fun startSideChat(task: String) {
        val text = task.trim()
        if (text.isBlank()) {
            openSidePanel()
            return
        }
        viewModelScope.launch {
            val threadKey = currentState.history
                .lastOrNull { it is ChatEntry.User }
                ?.messageId
                ?.let { "main:$it" }
                .orEmpty()
            val conversation = sideChats.create(
                title = text.take(24),
                task = text,
                profile = WorkerProfile.Standard,
                threadKey = threadKey,
            )
            switchTo(conversation.id)
        }
    }

    private fun startBtw(question: String) {
        val text = question.trim()
        if (text.isBlank()) {
            showMessage(R.string.chat_btw_usage)
            return
        }
        val state = currentState
        if (state.conversationId.isBlank()) return
        btwJob?.cancel()
        updateState {
            copy(btw = BtwUi(open = true, question = text, loading = true))
        }
        btwJob = viewModelScope.launch {
            btw.ask(
                conversationId = state.conversationId,
                question = text,
                options = TurnOptions(
                    providerId = state.providerId,
                    model = state.model,
                    reasoning = state.reasoningLevel,
                ),
            ).collect { event ->
                when (event) {
                    is ChatEvent.Delta -> updateState {
                        copy(btw = btw.copy(answer = btw.answer + event.text))
                    }
                    ChatEvent.Completed -> updateState {
                        copy(btw = btw.copy(loading = false))
                    }
                    is ChatEvent.Failed -> updateState {
                        copy(btw = btw.copy(loading = false, error = event.error))
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun closeBtw() {
        btwJob?.cancel()
        btwJob = null
        updateState { copy(btw = BtwUi()) }
    }

    private fun compact() {
        val state = currentState
        val conversationId = state.conversationId
        if (conversationId.isBlank() || turns.isRunning(conversationId)) return
        updateState {
            copy(
                input = "",
                slash = slash.copy(open = false, query = ""),
                sending = true,
                compacting = true,
                workingText = "",
                live = emptyList(),
                retry = null,
            )
        }
        turns.compact(
            conversationId,
            TurnOptions(
                providerId = state.providerId,
                model = state.model,
                reasoning = state.reasoningLevel,
                agentMode = state.mode,
            ),
        )
    }

    private fun retryBtw() {
        val question = currentState.btw.question
        if (question.isNotBlank()) startBtw(question)
    }

    private fun showMessage(resId: Int, args: List<Any> = emptyList()) {
        updateState { copy(message = UiMessage(resId, args, System.nanoTime())) }
    }

    private fun undo() {
        viewModelScope.launch {
            val id = currentState.conversationId
            if (turns.isRunning(id)) return@launch
            val messages = chats.messages(id, 200)
            val lastUser = messages.lastOrNull { it.role == ChatRole.User } ?: return@launch
            redoEntry = PendingSend(
                text = lastUser.content,
                attachments = lastUser.attachments.map { it.toUi() },
                replyToId = lastUser.replyToId,
                mentionedSkills = mentionedSkillNames(lastUser.content),
            )
            chats.deleteMessage(lastUser.id)
            chats.deleteAfter(id, lastUser.id)
            reload()
            showMessage(R.string.chat_undo_done)
        }
    }

    private fun redo() {
        val entry = redoEntry ?: return
        redoEntry = null
        sendText(entry.text, entry.attachments, entry.replyToId, mentionedSkills = entry.mentionedSkills)
    }

    private fun resume(argument: String) {
        viewModelScope.launch {
            val conversations = chats.conversations()
            val target = if (argument.isBlank()) {
                null
            } else {
                conversations.firstOrNull { it.id == argument || it.id.startsWith(argument) }
                    ?: conversations.firstOrNull { it.title.contains(argument, ignoreCase = true) }
            }
            if (target == null) {
                showMessage(R.string.chat_resume_help)
            } else {
                switchTo(target.id)
            }
        }
    }

    private fun openMenu(key: String) {
        val entry = currentState.history.lastOrNull { it.key == key } ?: return
        val messageId = entry.messageId ?: return
        val fromUser = entry is ChatEntry.User
        val text = when (entry) {
            is ChatEntry.User -> entry.text
            is ChatEntry.Assistant -> entry.text
            else -> return
        }
        updateState {
            copy(
                menu = ChatMenuUi(
                    key = key,
                    messageId = messageId,
                    author = if (fromUser) name.orEmpty() else assistantName,
                    text = text,
                    fromUser = fromUser,
                    canEdit = fromUser,
                    canDelete = true,
                ),
                selectionKey = null,
            )
        }
    }

    private fun replyTo(key: String) {
        val entry = currentState.history.lastOrNull { it.key == key } ?: return
        val messageId = entry.messageId ?: return
        val text = when (entry) {
            is ChatEntry.User -> entry.text
            is ChatEntry.Assistant -> entry.text
            else -> return
        }
        updateState {
            copy(
                reply = ReplyUi(
                    messageId = messageId,
                    author = if (entry is ChatEntry.User) name.orEmpty() else assistantName,
                    preview = text,
                ),
                menu = null,
            )
        }
    }

    private fun startEdit(key: String) {
        val entry = currentState.history.lastOrNull { it.key == key } as? ChatEntry.User ?: return
        val messageId = entry.messageId ?: return
        updateState {
            copy(
                edit = EditUi(messageId = messageId, key = key, original = entry.text, draft = entry.text),
                menu = null,
            )
        }
    }

    private fun saveEdit() {
        val edit = currentState.edit ?: return
        val draft = edit.draft.trim()
        if (draft.isBlank()) {
            showMessage(R.string.chat_edit_empty)
            return
        }
        viewModelScope.launch {
            val id = currentState.conversationId
            chats.updateContent(edit.messageId, draft)
            chats.deleteAfter(id, edit.messageId)
            updateState { copy(edit = null) }
            reload()
            sendEffect(ChatEffect.EditSaved)
            turns.start(
                conversationId = id,
                text = "",
                options = TurnOptions(
                    providerId = currentState.providerId,
                    model = currentState.model,
                    reasoning = currentState.reasoningLevel,
                    agentMode = currentState.mode,
                    mentionedSkills = mentionedSkillNames(draft),
                ),
                appendUser = false,
            )
        }
    }

    private fun askDelete(key: String) {
        val menu = currentState.menu?.takeIf { it.key == key } ?: run {
            openMenu(key)
            currentState.menu
        } ?: return
        updateState { copy(pendingDelete = menu, menu = null) }
    }

    private fun confirmDelete() {
        val target = currentState.pendingDelete ?: return
        viewModelScope.launch {
            chats.deleteMessage(target.messageId)
            if (target.fromUser) {
                chats.deleteAfter(currentState.conversationId, target.messageId)
            }
            updateState { copy(pendingDelete = null) }
            reload()
            sendEffect(ChatEffect.Deleted)
        }
    }

    private fun retryLast() {
        viewModelScope.launch {
            val id = currentState.conversationId
            if (turns.isRunning(id)) return@launch
            val messages = chats.messages(id, 200)
            val lastUser = messages.lastOrNull { it.role == ChatRole.User } ?: return@launch
            chats.deleteAfter(id, lastUser.id)
            reload()
            turns.start(
                conversationId = id,
                text = "",
                options = TurnOptions(
                    providerId = currentState.providerId,
                    model = currentState.model,
                    reasoning = currentState.reasoningLevel,
                    agentMode = currentState.mode,
                    replyToId = lastUser.replyToId,
                    mentionedSkills = mentionedSkillNames(lastUser.content),
                ),
                appendUser = false,
            )
        }
    }

    private fun setMode(mode: AgentMode) {
        val state = currentState
        val id = state.conversationId
        if (mode == AgentMode.Build) {
            val pending = state.planCard?.takeIf { it.card.phase == ApprovalPhase.Waiting }
            if (pending != null) {
                updateState { copy(picker = null) }
                decidePlan(ChatAction.DecidePlan(pending.card.id, true, state.policy))
                return
            }
        }
        updateState { copy(mode = mode, picker = null) }
        if (id.isNotBlank() && mode != state.mode) {
            viewModelScope.launch {
                chats.setMode(id, mode)
            }
        }
    }

    private fun decidePlan(action: ChatAction.DecidePlan) {
        val plan = currentState.planCard ?: return
        if (plan.card.id != action.approvalId && action.approvalId.isNotBlank()) return
        if (plan.card.phase != ApprovalPhase.Waiting) return
        val approved = action.approve
        val hasLivePlan = currentState.live.any { it is ChatEntry.Plan }
        updateState {
            copy(
                planStatus = if (approved) PlanStatus.Approved else PlanStatus.Rejected,
                live = live.resolvePlan(
                    plan.card.copy(
                        phase = if (approved) ApprovalPhase.Running else ApprovalPhase.Declined,
                    ),
                ),
                history = history.map { entry ->
                    if (entry is ChatEntry.Plan && entry.card.id == plan.card.id) {
                        entry.copy(
                            card = entry.card.copy(
                                phase = if (approved) ApprovalPhase.Running else ApprovalPhase.Declined,
                            ),
                        )
                    } else {
                        entry
                    }
                },
            )
        }
        viewModelScope.launch {
            val id = currentState.conversationId
            if (approved) {
                policy.setPolicy(action.policy)
                chats.setPlan(id, plan.plan, PlanStatus.Approved, plan.card.id)
                chats.setMode(id, AgentMode.Build)
                updateState { copy(mode = AgentMode.Build) }
            } else {
                chats.setPlan(id, plan.plan, PlanStatus.Rejected, plan.card.id)
            }
            if (hasLivePlan) {
                turns.decide(action.approvalId, approved)
            } else if (approved) {
                chats.append(
                    conversationId = id,
                    role = ChatRole.System,
                    content = "The user approved the plan. Continue in build mode and execute it.",
                    label = "plan",
                )
                turns.start(
                    conversationId = id,
                    text = "",
                    options = TurnOptions(
                        providerId = currentState.providerId,
                        model = currentState.model,
                        reasoning = currentState.reasoningLevel,
                        agentMode = AgentMode.Build,
                    ),
                    appendUser = false,
                )
            }
        }
    }

    private fun decide(action: ChatAction.Decide) {
        val card = currentState.approval ?: return
        if (card.id != action.approvalId || card.phase != ApprovalPhase.Waiting) return
        updateState {
            copy(
                live = live.updateApproval(card.copy(phase = if (action.approve) ApprovalPhase.Running else ApprovalPhase.Declined)),
            )
        }
        turns.decide(action.approvalId, action.approve)
    }

    private fun decideTrust(action: ChatAction.DecideTrust) {
        val card = currentState.trust ?: return
        if (card.id != action.id || card.phase != TrustPhase.Waiting) return
        updateState {
            copy(
                live = live.updateTrust(
                    card.copy(phase = if (action.trusted) TrustPhase.Trusted else TrustPhase.Declined),
                ),
            )
        }
        turns.decideTrust(action.id, action.trusted)
    }

    private fun selectAskOption(action: ChatAction.AskSelect) {
        val question = askQuestion(action.askId, action.questionId) ?: return
        updateDraft(action.askId, action.questionId) { draft ->
            if (question.kind == AskKind.Multi) {
                draft.copy(
                    selections = if (action.label in draft.selections) {
                        draft.selections - action.label
                    } else {
                        draft.selections + action.label
                    },
                )
            } else {
                draft.copy(selections = listOf(action.label))
            }
        }
    }

    private fun editAsk(action: ChatAction.AskEdit) {
        updateDraft(action.askId, action.questionId) { draft ->
            when (action.field) {
                AskField.Answer -> draft.copy(text = action.value)
                AskField.Other -> draft.copy(other = action.value)
            }
        }
    }

    private fun askQuestion(askId: String, questionId: String): AskQuestionUi? {
        val card = currentState.ask ?: return null
        if (card.id != askId || card.phase != AskPhase.Waiting) return null
        return card.questions.firstOrNull { it.id == questionId }
    }

    private fun updateDraft(askId: String, questionId: String, reduce: (AskDraft) -> AskDraft) {
        if (askQuestion(askId, questionId) == null) return
        updateState {
            copy(askDrafts = askDrafts + (questionId to reduce(askDrafts[questionId] ?: AskDraft())))
        }
    }

    private fun submitAsk(askId: String, skipped: Boolean) {
        val card = currentState.ask ?: return
        if (card.id != askId || card.phase != AskPhase.Waiting) return
        val drafts = currentState.askDrafts
        if (!skipped && !card.isReady(drafts)) return
        val answers = if (skipped) emptyList() else card.toAnswers(drafts)
        updateState {
            copy(
                askDrafts = emptyMap(),
                live = live.resolveAsk(
                    card.copy(
                        phase = if (skipped) AskPhase.Skipped else AskPhase.Answered,
                        answers = answers.map { it.toUi() },
                    ),
                ),
            )
        }
        turns.answer(askId, answers, skipped)
    }

    private fun selectModel(providerId: String, model: String) {
        persistTurnSettings(providerId = providerId, model = model)
        updateState { copy(providerId = providerId, model = model, picker = null) }
    }

    private fun selectReasoning(level: ReasoningLevel) {
        persistTurnSettings(reasoning = level.name)
        updateState { copy(reasoningLevel = level) }
    }

    private fun persistTurnSettings(
        providerId: String = currentState.providerId,
        model: String = currentState.model,
        reasoning: String = currentState.reasoningLevel.name,
    ) {
        val conversationId = currentState.conversationId
        if (conversationId.isBlank()) return
        viewModelScope.launch {
            chats.setProvider(conversationId, providerId, model, reasoning)
        }
    }

    private fun loadModels(force: Boolean = false) {
        if (!force && (currentState.modelsLoaded || currentState.modelsLoading)) return
        modelJob?.cancel()
        updateState { copy(modelsLoading = true) }
        modelJob = viewModelScope.launch {
            val config = settings.configOnce()
            val groups = providerModels.load(config).associate { group ->
                group.providerId to group.models.map { ModelOption(it.id, it.name, it.meta) }
            }
            val loaded = groups.values.any { it.isNotEmpty() }
            updateState {
                copy(
                    models = groups,
                    modelsLoaded = loaded,
                    modelsLoading = false,
                    model = model.ifBlank { groups[providerId]?.firstOrNull()?.id.orEmpty() },
                )
            }
        }
    }

    private fun attach(uris: List<String>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val loaded = uris.mapNotNull { loader.load(it) }.map { it.toUi() }
            if (loaded.isEmpty()) {
                emitError(AppError(AppErrorType.Generic, serverMessage = "Could not read the selected file."))
                return@launch
            }
            updateState { copy(attachments = attachments + loaded) }
        }
    }

    private fun stop() {
        val conversationId = currentState.conversationId
        if (conversationId.isBlank()) return
        if (currentState.isMain) {
            router.stopCurrent()
            updateState { copy(sending = false, workingText = "") }
            return
        }
        turns.stop(conversationId)
    }

    private fun openSidePanel() {
        updateState { copy(sidePanel = sidePanel.copy(open = true)) }
        refreshSidePanel()
    }

    private fun refreshSidePanel() {
        viewModelScope.launch {
            updateState { copy(sidePanel = sidePanel.copy(loading = true)) }
            val rows = sideChats.list().map { item ->
                SideChatRowUi(
                    id = item.id,
                    title = item.title,
                    status = item.status,
                    profile = item.profile,
                    summary = item.latest,
                    updatedAt = item.updatedAt,
                    unseen = !item.resultSeen,
                )
            }
            updateState { copy(sidePanel = sidePanel.copy(loading = false, items = rows)) }
        }
    }

    private fun openSideChat(id: String) {
        viewModelScope.launch {
            sideChats.markSeen(id)
            updateState { copy(sidePanel = sidePanel.copy(open = false)) }
            switchTo(id)
        }
    }

    private fun stopSideChat(id: String) {
        viewModelScope.launch {
            sideChats.stop(id)
            refreshSidePanel()
        }
    }

    private fun retrySideChat(id: String) {
        viewModelScope.launch {
            sideChats.retry(id)
            refreshSidePanel()
        }
    }

    private fun backToMain() {
        viewModelScope.launch {
            switchTo(ChatStore.MAIN_CONVERSATION_ID)
        }
    }

    private suspend fun reload() {
        val id = currentState.conversationId
        if (id.isBlank()) return
        if (chats.conversation(id) == null) {
            load(chats.ensureMainConversation().id)
            return
        }
        if (turns.isRunning(id)) return
        val generation = loadGeneration
        val messages = chats.messages(id)
        val conversation = chats.conversation(id)
        val compaction = chats.compaction(id)
        val config = settings.configOnce()
        val providerId = conversation?.providerId?.takeIf { candidate ->
            config.providers.any { it.id == candidate }
        } ?: config.activeProviderId
        val provider = config.providers.firstOrNull { it.id == providerId } ?: config.activeProvider
        val model = conversation?.model?.ifBlank { null } ?: provider?.model.orEmpty()
        val todos = chats.todos(id)
        val entries = messages.toEntries(compaction, main = conversation?.kind == ConversationKind.Main) +
            listOfNotNull(planEntry(conversation))
        if (currentState.conversationId != id || generation != loadGeneration) return
        if (turns.isRunning(id)) return
        resetDisplay(id)
        resetLiveUser()
        updateState {
            copy(
                history = entries,
                live = emptyList(),
                sending = false,
                compacting = false,
                workingText = "",
                kind = conversation?.kind ?: ConversationKind.Chat,
                taskStatus = conversation?.taskStatus ?: TaskStatus.None,
                workerProfile = conversation?.workerProfile ?: WorkerProfile.Standard,
                routerModel = mainChannel.state.value.routerModel,
                sideActiveCount = mainChannel.state.value.activeSideChats,
                sideUnreadCount = mainChannel.state.value.unreadResults,
                providers = config.providers.map { ProviderOption(it.id, it.name, it.model, it.icon, it.models) },
                providerId = providerId,
                model = model,
                askDrafts = emptyMap(),
                retry = null,
                liveStats = MessageStats(),
                todos = todos,
                mode = conversation?.mode ?: AgentMode.Build,
                plan = conversation?.plan,
                planStatus = conversation?.planStatus ?: PlanStatus.None,
            )
        }
    }

    private suspend fun load(conversationId: String) {
        if (chats.conversation(conversationId) == null) {
            load(chats.ensureMainConversation().id)
            return
        }
        val generation = ++loadGeneration
        resetDisplay(conversationId)
        resetLiveUser()
        awaitingUserTurn = false
        suggestionJob?.cancel()
        btwJob?.cancel()
        btwJob = null
        statsJob?.cancel()
        val messages = chats.messages(conversationId)
        val conversation = chats.conversation(conversationId)
        val compaction = chats.compaction(conversationId)
        val name = workspace.userName()
        val assistantName = workspace.assistantName()
        val config = settings.configOnce()
        val providerId = conversation?.providerId?.takeIf { id ->
            config.providers.any { it.id == id }
        } ?: config.activeProviderId
        val provider = config.providers.firstOrNull { it.id == providerId } ?: config.activeProvider
        val model = conversation?.model?.ifBlank { null } ?: provider?.model.orEmpty()
        val level = conversation?.reasoning
            ?.takeIf { it.isNotBlank() }
            ?.let { ReasoningLevel.fromName(it) }
            ?: ReasoningLevel.Auto
        val todos = chats.todos(conversationId)
        val source = imChats.bindingForConversation(conversationId)
        if (conversation?.kind == ConversationKind.Side) {
            sideChats.markSeen(conversationId)
        }
        if (generation != loadGeneration) return
        val runningTurn = turns.state(conversationId)
        val visibleMessages = if (runningTurn.running && runningTurn.userText.isNotBlank()) {
            val index = messages.indexOfLast {
                it.role == ChatRole.User && it.content == runningTurn.userText
            }
            if (index >= 0) messages.take(index) else messages
        } else {
            messages
        }
        if (runningTurn.running) localTurnId = 0
        val entries = visibleMessages.toEntries(compaction, main = conversation?.kind == ConversationKind.Main) +
            listOfNotNull(planEntry(conversation))
        updateState {
            copy(
                loading = false,
                conversationId = conversationId,
                title = conversation?.title.orEmpty(),
                kind = conversation?.kind ?: ConversationKind.Chat,
                taskStatus = conversation?.taskStatus ?: TaskStatus.None,
                workerProfile = conversation?.workerProfile ?: WorkerProfile.Standard,
                routerModel = mainChannel.state.value.routerModel,
                sideActiveCount = mainChannel.state.value.activeSideChats,
                sideUnreadCount = mainChannel.state.value.unreadResults,
                name = name,
                assistantName = assistantName,
                history = entries,
                live = emptyList(),
                suggestions = emptyList(),
                sending = false,
                compacting = false,
                workingText = "",
                input = "",
                turn = 0,
                attachments = emptyList(),
                providers = config.providers.map { ProviderOption(it.id, it.name, it.model, it.icon, it.models) },
                providerId = providerId,
                model = model,
                reasoningLevel = level,
                mode = conversation?.mode ?: AgentMode.Build,
                modelsLoading = false,
                picker = null,
                modelQuery = "",
                askDrafts = emptyMap(),
                retry = null,
                liveStats = MessageStats(),
                todos = todos,
                menu = null,
                edit = null,
                reply = null,
                pendingDelete = null,
                slash = SlashUi(open = false),
                mention = MentionUi(),
                btw = BtwUi(),
                subAgentOpenId = null,
                highlightKey = null,
                selectionKey = null,
                plan = conversation?.plan,
                planStatus = conversation?.planStatus ?: PlanStatus.None,
                imSource = source?.let {
                    ImSourceUi(
                        accountName = it.accountName,
                        chatTitle = it.title,
                        group = it.kind == ImChatKind.Group,
                    )
                },
            )
        }
        switcher.markLoaded(conversationId)
        applyTurn(turns.state(conversationId))
        turns.result(conversationId)?.let { handleResult(it) }
        if (messages.isEmpty() && !turns.isRunning(conversationId)) {
            suggestionJob = viewModelScope.launch { loadSuggestions(conversationId) }
        }
    }

    private suspend fun loadSuggestions(conversationId: String) {
        val state = currentState
        if (state.conversationId != conversationId || state.history.isNotEmpty()) return
        val generated = try {
            suggestionGenerator.generate(state.providerId, state.model)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            emptyList()
        }
        if (generated.isEmpty()) return
        if (currentState.conversationId != conversationId || currentState.history.isNotEmpty()) return
        updateState {
            copy(
                suggestions = generated.map {
                    SuggestionUi(title = it.title, prompt = it.prompt, icon = it.icon)
                },
            )
        }
    }

    private suspend fun switchTo(conversationId: String) {
        if (conversationId == currentState.conversationId && loadJob?.isActive != true) return
        loadGeneration++
        loadJob?.cancel()
        updateState { copy(loading = true) }
        loadJob = viewModelScope.launch {
            load(conversationId)
        }
    }

    private suspend fun newConversation() {
        if (currentState.isMain) {
            openSidePanel()
            return
        }
        if (currentState.isSide) {
            val conversation = sideChats.createEmpty()
            switchTo(conversation.id)
            return
        }
        loadGeneration++
        loadJob?.cancel()
        if (currentState.history.isEmpty() && currentState.conversationId.isNotBlank()) {
            load(currentState.conversationId)
            return
        }
        val conversation = chats.createConversation("", currentState.mode)
        load(conversation.id)
    }

    private suspend fun ask(text: String) {
        if (currentState.history.isNotEmpty()) newConversation()
        updateState { copy(input = text, sending = false, live = emptyList(), workingText = "") }
        send()
    }

    private fun applyTurn(turn: ChatTurnState) {
        if (turn.conversationId != currentState.conversationId) return
        if (!turn.running) {
            statsJob?.cancel()
            resetDisplay(turn.conversationId)
            updateState {
                copy(
                    sending = false,
                    compacting = false,
                    live = emptyList(),
                    workingText = "",
                    askDrafts = emptyMap(),
                    retry = null,
                    todos = if (turn.todos.isNotEmpty()) turn.todos else todos,
                )
            }
            return
        }
        if (turn.turnId != localTurnId && turn.userText.isNotBlank() &&
            liveUserTurnId != turn.turnId
        ) {
            liveUserTurnId = turn.turnId
            liveUserText = turn.userText
        }
        val userEntry = if (liveUserTurnId == turn.turnId && liveUserText.isNotBlank()) {
            ChatEntry.User(
                key = "live-user-${turn.turnId}",
                text = liveUserText,
            )
        } else {
            null
        }
        val answer = turn.entries.filterIsInstance<ChatTurnEntry.Answer>().lastOrNull()?.text.orEmpty()
        updateDisplayTarget(turn.conversationId, turn.turnId, answer)
        val shown = displayTarget.take(displayText.length)
        updateState {
            copy(
                sending = true,
                compacting = turn.compacting,
                workingText = turn.workingText,
                live = listOfNotNull(userEntry) + turn.entries
                    .mapIndexed { index, entry -> entry.toUi("live-${turn.turnId}", index) }
                    .withDisplayText(shown),
                retry = turn.retry?.let { RetryUi(it.attempt, it.max, it.reason) },
                liveStats = when {
                    turn.stats.hasUsage || turn.stats.round > 0 -> turn.stats.copy(
                        durationMs = maxOf(turn.stats.durationMs, liveStats.durationMs),
                    )
                    turn.stats.providerId.isNotBlank() -> liveStats.copy(
                        providerId = turn.stats.providerId,
                        providerName = turn.stats.providerName.ifBlank { liveStats.providerName },
                        model = turn.stats.model.ifBlank { liveStats.model },
                    )
                    else -> liveStats
                },
                todos = turn.todos.ifEmpty { todos },
                plan = turn.plan ?: plan,
            )
        }
        if (statsJob?.isActive != true) startStatsTicker()
    }

    private suspend fun handleResult(result: ChatTurnResult) {
        if (result.conversationId != currentState.conversationId) return
        if (!turns.consumeResult(result.conversationId, result.turnId)) return
        if (currentState.isSide) sideChats.markSeen(result.conversationId)
        statsJob?.cancel()
        completeDisplay(result)
        awaitingUserTurn = false
        val reveal = displayJob
        withTimeoutOrNull(REVEAL_JOIN_TIMEOUT_MS) { reveal?.join() }
        result.error?.let { error ->
            val message = error.serverMessage ?: error.serverTitle ?: error.type.name
            chats.append(
                conversationId = result.conversationId,
                role = ChatRole.System,
                content = message,
                label = "failure",
            )
        }
        if (currentState.conversationId != result.conversationId) return
        if (displayTurnId > result.turnId) return
        if (!awaitTurnIdle(result.conversationId)) return
        if (displayTarget.isNotEmpty() && result.error == null) delay(REVEAL_HANDOFF_MS)
        if (currentState.conversationId != result.conversationId) return
        if (displayTurnId > result.turnId) return
        if (!awaitTurnIdle(result.conversationId)) return
        if (displayJob === reveal) {
            displayJob?.cancel()
            displayJob = null
        }
        reload()
        result.error?.let { emitError(it) }
        if (result.compactionFailed) showMessage(R.string.chat_compact_empty)
        refreshTitle()
    }

    private suspend fun awaitTurnIdle(conversationId: String): Boolean {
        if (!turns.isRunning(conversationId)) return true
        return withTimeoutOrNull(TURN_IDLE_TIMEOUT_MS) {
            while (turns.isRunning(conversationId)) delay(50)
            true
        } ?: false
    }

    private suspend fun completeDisplay(result: ChatTurnResult) {
        if (currentState.conversationId != result.conversationId) return
        val last = chats.messages(result.conversationId).lastOrNull()
        if (currentState.conversationId != result.conversationId) return
        val observed = displayConversationId == result.conversationId && displayTurnId == result.turnId
        if (!observed && !awaitingUserTurn) return
        val finalText = if (last?.role == ChatRole.Assistant) last.content else ""
        if (finalText.isBlank()) return
        if (!observed) {
            resetDisplay(result.conversationId, result.turnId)
        }
        if (displayTarget != finalText) {
            if (!finalText.startsWith(displayTarget)) displayText = ""
            displayTarget = finalText
        }
        if (displayText.length >= displayTarget.length) return
        ensureDisplayJob()
    }

    private fun updateDisplayTarget(conversationId: String, turnId: Long, target: String) {
        if (displayConversationId != conversationId || displayTurnId != turnId) {
            resetDisplay(conversationId, turnId)
        }
        if (target.length < displayTarget.length || !target.startsWith(displayTarget)) {
            displayText = ""
        }
        displayTarget = target
        if (displayText.length > displayTarget.length) {
            displayText = displayText.take(displayTarget.length)
        }
        if (displayText.length < displayTarget.length) ensureDisplayJob()
    }

    private fun resetDisplay(conversationId: String, turnId: Long = 0L) {
        displayJob?.cancel()
        displayJob = null
        displayConversationId = conversationId
        displayTurnId = turnId
        displayTarget = ""
        displayText = ""
    }

    private fun resetLiveUser() {
        liveUserTurnId = 0L
        liveUserText = ""
    }

    private fun ensureDisplayJob() {
        if (displayJob?.isActive == true) return
        displayJob = viewModelScope.launch {
            while (displayText.length < displayTarget.length) {
                val backlog = displayTarget.length - displayText.length
                val step = ((backlog + REVEAL_CATCHUP_FRAMES - 1) / REVEAL_CATCHUP_FRAMES)
                    .coerceAtLeast(REVEAL_MIN_GROUP)
                delay(REVEAL_FRAME_MS)
                if (displayText.length >= displayTarget.length) break
                displayText = displayTarget.take((displayText.length + step).coerceAtMost(displayTarget.length))
                showDisplayText()
            }
        }
    }

    private fun showDisplayText() {
        if (displayConversationId != currentState.conversationId) return
        val shown = displayTarget.take(displayText.length)
        updateState {
            val index = live.indexOfLast { it is ChatEntry.Assistant }
            if (index >= 0) {
                val entry = live[index] as ChatEntry.Assistant
                if (entry.text == shown) {
                    this
                } else {
                    copy(live = live.toMutableList().also { it[index] = entry.copy(text = shown) })
                }
            } else {
                copy(
                    live = live + ChatEntry.Assistant(
                        key = "live-answer-final-$displayTurnId",
                        text = shown,
                        live = true,
                    ),
                )
            }
        }
    }

    private fun List<ChatEntry>.withDisplayText(shown: String): List<ChatEntry> {
        val index = indexOfLast { it is ChatEntry.Assistant }
        if (index < 0) return this
        val entry = this[index] as ChatEntry.Assistant
        if (shown == entry.text) return this
        return toMutableList().also { it[index] = entry.copy(text = shown) }
    }

    private suspend fun refreshTitle() {
        val id = currentState.conversationId
        if (id.isBlank()) return
        val title = chats.conversation(id)?.title.orEmpty()
        updateState { copy(title = title) }
    }

    private fun ChatTurnEntry.toUi(prefix: String, index: Int): ChatEntry = when (this) {
        is ChatTurnEntry.Answer -> ChatEntry.Assistant(
            key = "$prefix-answer-$index",
            text = text,
            live = true,
            reasoning = reasoning,
            reasoningMs = reasoningMs,
            reasoningStartedAt = reasoningStartedAt,
        )
        is ChatTurnEntry.Trace -> ChatEntry.Trace(
            key = "$prefix-trace-$index",
            steps = steps.map { it.toUi() },
            live = true,
        )
        is ChatTurnEntry.Approval -> ChatEntry.Approval(
            key = "$prefix-approval-${card.id}",
            card = ApprovalUi(
                id = card.id,
                title = card.title,
                summary = card.summary,
                steps = card.steps.map { it.toUi() },
                phase = card.phase.toUi(),
            ),
        )
        is ChatTurnEntry.Plan -> ChatEntry.Plan(
            key = "$prefix-plan-${card.id}",
            plan = plan,
            card = ApprovalUi(
                id = card.id,
                title = card.title,
                summary = card.summary,
                steps = card.steps.map { it.toUi() },
                phase = card.phase.toUi(),
                kind = ApprovalKind.Plan,
                plan = plan,
            ),
        )
        is ChatTurnEntry.Ask -> ChatEntry.Ask(
            key = "$prefix-ask-${card.id}",
            card = card.toUi(),
        )
        is ChatTurnEntry.Trust -> ChatEntry.Trust(
            key = "$prefix-trust-${card.id}",
            card = TrustCardUi(
                id = card.id,
                label = card.label,
                kind = card.kind,
                files = card.files,
                phase = card.phase.toUi(),
            ),
        )
        is ChatTurnEntry.Compaction -> ChatEntry.Compaction(
            key = "$prefix-compaction-$index",
            summary = summary,
            streaming = streaming,
            tokensBefore = tokensBefore,
            tokensAfter = tokensAfter,
        )
    }

    private fun SubAgentRunState.toUi(): SubAgentRunUi = SubAgentRunUi(
        id = id,
        task = task,
        providerName = providerName,
        model = model,
        running = status == SubAgentRunStatus.Running,
        failed = status == SubAgentRunStatus.Failed,
        summary = summary,
        error = error,
        entries = entries.mapIndexed { index, entry ->
            entry.toUi("subagent-$id", index)
        },
    )

    private fun ChatTurnStep.toUi(): TraceStep = TraceStep(
        label = label,
        status = when (status) {
            ChatTurnStepStatus.Pending -> TraceStatus.Pending
            ChatTurnStepStatus.Running -> TraceStatus.Running
            ChatTurnStepStatus.Done -> TraceStatus.Done
            ChatTurnStepStatus.Failed -> TraceStatus.Failed
        },
        summary = summary,
        id = id,
        tool = tool,
        arguments = ToolPayload.format(arguments),
        output = ToolPayload.format(output),
        durationMs = durationMs,
        startedAt = startedAt,
        diffs = diffs,
        media = media.map { it.toUi() },
    )

    private fun ChatTurnApprovalPhase.toUi(): ApprovalPhase = when (this) {
        ChatTurnApprovalPhase.Waiting -> ApprovalPhase.Waiting
        ChatTurnApprovalPhase.Running -> ApprovalPhase.Running
        ChatTurnApprovalPhase.Finished -> ApprovalPhase.Finished
        ChatTurnApprovalPhase.Declined -> ApprovalPhase.Declined
    }

    private fun ChatTurnTrustPhase.toUi(): TrustPhase = when (this) {
        ChatTurnTrustPhase.Waiting -> TrustPhase.Waiting
        ChatTurnTrustPhase.Trusted -> TrustPhase.Trusted
        ChatTurnTrustPhase.Declined -> TrustPhase.Declined
    }

    private suspend fun List<ChatMessage>.toEntries(
        compaction: ConversationCompaction?,
        main: Boolean = currentState.isMain,
    ): List<ChatEntry> {
        val quotes = chats.messagesByIds(mapNotNull { it.replyToId }.toSet())
        val argumentsByCall = flatMap { it.toolCalls }.associate { it.id to it.arguments }
        val entries = mutableListOf<ChatEntry>()
        var pendingStats: MessageStats? = null
        var compactionAdded = false
        val assistant = currentState.assistantName
        forEach { message ->
            if (compaction != null && !compactionAdded && message.id > compaction.upToId) {
                compactionAdded = true
                if (compaction.summary.isNotBlank()) {
                    entries += compaction.toEntry()
                }
            }
            when (message.role) {
                ChatRole.User -> {
                    pendingStats = null
                    entries += ChatEntry.User(
                        key = "m${message.id}",
                        text = message.content,
                        attachments = message.attachments.map { it.toUi() },
                        messageId = message.id,
                        replyTo = message.replyToId?.let { id ->
                            val quoted = quotes[id]
                            QuotedMessageUi(
                                id = id,
                                author = if (quoted?.role == ChatRole.User) {
                                    currentState.name.orEmpty()
                                } else {
                                    assistant
                                },
                                text = quoted?.content.orEmpty(),
                                deleted = quoted == null,
                                fromUser = quoted?.role == ChatRole.User,
                            )
                        },
                        reactions = message.reactions.filter { it.isNotBlank() },
                    )
                }
                ChatRole.Assistant -> {
                    val stats = message.stats ?: pendingStats
                    if (message.content.isNotBlank() || message.reasoning.isNotBlank()) {
                        pendingStats = null
                        entries += ChatEntry.Assistant(
                            key = "m${message.id}",
                            text = message.content,
                            live = false,
                            reasoning = message.reasoning,
                            reasoningMs = message.elapsedMs,
                            messageId = message.id,
                            reactions = message.reactions.filter { it.isNotBlank() },
                            stats = stats,
                        )
                    } else if (stats != null) {
                        pendingStats = stats
                    }
                }
                ChatRole.Tool -> {
                    if (!main && message.name != ToolCatalog.SET_WORKING_TEXT) {
                        entries += ChatEntry.Trace(
                            key = "m${message.id}",
                            steps = message.toSteps(argumentsByCall[message.toolCallId.orEmpty()].orEmpty()),
                        )
                    }
                }
                ChatRole.System -> {
                    when {
                        message.label == "failure" -> entries += ChatEntry.Failure(
                            key = "m${message.id}",
                            message = message.content,
                            retryable = true,
                        )
                        message.label == MainChannelSession.SIDE_RESULT_LABEL -> {
                            val card = runCatching {
                                json.decodeFromString(SideResultCard.serializer(), message.content)
                            }.getOrNull()
                            if (card != null) {
                                entries += ChatEntry.SideResult(
                                    key = "m${message.id}",
                                    card = SideResultUi(
                                        sideChatId = card.sideChatId,
                                        title = card.title,
                                        summary = card.summary,
                                        failed = card.failed,
                                    ),
                                )
                            }
                        }
                        else -> entries += ChatEntry.Notice(
                            key = "m${message.id}",
                            text = message.content,
                        )
                    }
                }
            }
        }
        if (compaction != null && !compactionAdded && compaction.summary.isNotBlank()) {
            entries += compaction.toEntry()
        }
        return entries
    }

    private fun ConversationCompaction.toEntry(): ChatEntry.Compaction = ChatEntry.Compaction(
        key = "compaction-$upToId",
        summary = summary,
        tokensBefore = tokensBefore,
        tokensAfter = tokensAfter,
    )

    private fun planEntry(conversation: fan.x0.focus.data.assistant.Conversation?): ChatEntry.Plan? {
        val plan = conversation?.plan ?: return null
        val phase = when (conversation.planStatus) {
            PlanStatus.Pending -> ApprovalPhase.Waiting
            PlanStatus.Approved -> ApprovalPhase.Finished
            PlanStatus.Rejected -> ApprovalPhase.Declined
            PlanStatus.None -> return null
        }
        return ChatEntry.Plan(
            key = "plan-${conversation.id}",
            plan = plan,
            card = ApprovalUi(
                id = conversation.planRequestId.ifBlank { "plan-${conversation.id}" },
                title = plan.title,
                summary = plan.summary,
                steps = plan.steps.map {
                    TraceStep(
                        label = it.title,
                        status = TraceStatus.Pending,
                        tool = it.tool,
                        arguments = it.arguments,
                    )
                },
                phase = phase,
                kind = ApprovalKind.Plan,
                plan = plan,
            ),
        )
    }

    private fun ChatMessage.toSteps(arguments: String): List<TraceStep> {
        val labels = label?.lines()?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
        val status = if (content.startsWith("Error: ")) TraceStatus.Failed else TraceStatus.Done
        if (labels.size <= 1) {
            return listOf(
                TraceStep(
                    label = labels.firstOrNull() ?: ToolLabels.of(name),
                    status = status,
                    id = toolCallId.orEmpty(),
                    tool = name.orEmpty(),
                    arguments = ToolPayload.format(arguments),
                    output = ToolPayload.format(content),
                    durationMs = elapsedMs,
                    diffs = stepDiffs.firstOrNull().orEmpty(),
                    media = attachments.map { it.toUi() },
                ),
            )
        }
        return labels.mapIndexed { index, value ->
            TraceStep(
                label = value,
                status = status,
                id = toolCallId.orEmpty(),
                tool = name.orEmpty(),
                arguments = ToolPayload.format(arguments),
                output = ToolPayload.format(content),
                durationMs = elapsedMs,
                diffs = stepDiffs.getOrNull(index).orEmpty(),
                media = if (index == 0) attachments.map { it.toUi() } else emptyList(),
            )
        }
    }

    private fun ChatAttachment.toUi(): AttachmentUi = AttachmentUi(
        id = id,
        kind = kind,
        name = name,
        mime = mime,
        text = text,
        base64 = base64,
    )

    private fun AttachmentUi.toModel(): ChatAttachment = ChatAttachment(
        id = id,
        kind = kind,
        name = name,
        mime = mime,
        text = text,
        base64 = base64,
    )

    private fun List<ChatEntry>.resolvePlan(card: ApprovalUi): List<ChatEntry> = map { entry ->
        if (entry is ChatEntry.Plan && entry.card.id == card.id) {
            entry.copy(card = card)
        } else {
            entry
        }
    }

    private data class PendingSend(
        val text: String,
        val attachments: List<AttachmentUi>,
        val replyToId: Long?,
        val mentionedSkills: List<String> = emptyList(),
    )
}
