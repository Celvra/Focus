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
import fan.x0.focus.data.assistant.CommandPolicy
import fan.x0.focus.data.assistant.ConversationCompaction
import fan.x0.focus.data.assistant.ConversationTodo
import fan.x0.focus.data.assistant.MessageStats
import fan.x0.focus.data.assistant.ModelCatalogRepository
import fan.x0.focus.data.assistant.PlanStatus
import fan.x0.focus.data.assistant.PolicySettings
import fan.x0.focus.data.assistant.ProposedPlan
import fan.x0.focus.data.assistant.ReasoningLevel
import fan.x0.focus.data.assistant.SecretStore
import fan.x0.focus.data.assistant.SubAgentRunState
import fan.x0.focus.data.assistant.SubAgentRunStatus
import fan.x0.focus.data.assistant.SubAgentRunStore
import fan.x0.focus.data.assistant.TodoPriority
import fan.x0.focus.data.assistant.TodoStatus
import fan.x0.focus.data.assistant.ToolCatalog
import fan.x0.focus.data.assistant.ToolLabels
import fan.x0.focus.data.assistant.TurnOptions
import fan.x0.focus.data.assistant.WorkspaceStore
import fan.x0.focus.data.assistant.requestConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class ChatViewModel(
    private val chats: ChatStore,
    private val switcher: ChatSwitcher,
    private val workspace: WorkspaceStore,
    private val settings: AssistantSettings,
    private val secrets: SecretStore,
    private val catalog: ModelCatalogRepository,
    private val loader: AttachmentLoader,
    private val turns: ChatTurnRunner,
    private val suggestionGenerator: ChatSuggestionGenerator,
    private val details: ChatDetailSettings,
    private val policy: PolicySettings,
    private val btw: BtwRunner,
    private val subAgents: SubAgentRunStore,
) : BaseViewModel<ChatAction, ChatUiState, ChatEffect>(ChatUiState()) {

    private var loadJob: Job? = null
    private var modelJob: Job? = null
    private var suggestionJob: Job? = null
    private var btwJob: Job? = null
    private var statsJob: Job? = null
    private var lastSend: PendingSend? = null
    private var redoEntry: PendingSend? = null

    init {
        viewModelScope.launch {
            val conversation = switcher.current.value
                ?.let { chats.conversation(it) }
                ?: chats.ensureConversation()
            load(conversation.id)
            switcher.command.filterNotNull().collect { command ->
                when (command) {
                    is ChatCommand.Open -> switchTo(command.conversationId)
                    ChatCommand.New -> newConversation()
                    is ChatCommand.Ask -> ask(command.text)
                }
                switcher.consume()
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
                    copy(
                        providers = config.providers.map {
                            ProviderOption(it.id, it.name, it.model, it.icon)
                        },
                        providerId = resolved,
                        model = nextModel,
                    )
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
                if (action.picker == ComposerPicker.Model && currentState.models.isEmpty()) {
                    loadModels()
                }
            }
            is ChatAction.SelectProvider -> selectProvider(action.providerId)
            is ChatAction.SelectModel -> selectModel(action.model)
            is ChatAction.ModelQueryChanged -> updateState { copy(modelQuery = action.value) }
            ChatAction.RefreshModels -> loadModels()
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
            ChatAction.RetryLast -> retryLast()
            ChatAction.OpenSessions -> sendEffect(ChatEffect.OpenSessions)
            ChatAction.OpenSettings -> sendEffect(ChatEffect.OpenSettings)
            is ChatAction.OpenSubAgent -> updateState { copy(subAgentOpenId = action.id) }
            ChatAction.CloseSubAgent -> updateState { copy(subAgentOpenId = null) }
            ChatAction.ConsumeMessage -> updateState { copy(message = null) }
        }
    }

    private fun inputChanged(value: String) {
        val state = currentState
        if (state.slash.visible && !state.input.contains('\n') && value.contains('\n')) {
            selectSlash()
            return
        }
        val slash = if (value.startsWith("/") && !value.contains('\n')) {
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
        updateState { copy(input = value, slash = slash) }
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
                    input = spec.command,
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
        )
    }

    private fun sendText(
        text: String,
        attachments: List<AttachmentUi>,
        replyToId: Long?,
        displayText: String = text,
    ) {
        val state = currentState
        val conversationId = state.conversationId
        if (conversationId.isBlank() || turns.isRunning(conversationId)) return
        val options = TurnOptions(
            providerId = state.providerId,
            model = state.model,
            reasoning = state.reasoningLevel,
            attachments = attachments.map { it.toModel() },
            agentMode = state.mode,
            replyToId = replyToId,
        )
        lastSend = PendingSend(text, attachments, replyToId)
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
        turns.start(conversationId, text, options)
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
            SlashCommandId.Compact -> compact()
            SlashCommandId.New -> viewModelScope.launch { newConversation() }
            SlashCommandId.Resume -> resume(argument)
            SlashCommandId.Sessions -> sendEffect(ChatEffect.OpenSessions)
            SlashCommandId.Models -> {
                if (argument.isNotBlank()) {
                    updateState { copy(modelQuery = argument) }
                }
                onAction(ChatAction.OpenPicker(ComposerPicker.Model))
            }
            SlashCommandId.Effort -> {
                val level = ReasoningLevel.entries.firstOrNull {
                    it.name.equals(argument, ignoreCase = true)
                }
                if (level != null) selectReasoning(level) else onAction(ChatAction.OpenPicker(ComposerPicker.Reasoning))
            }
            SlashCommandId.Settings -> sendEffect(ChatEffect.OpenSettings)
            SlashCommandId.Help -> showMessage(R.string.chat_command_usage)
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
        sendText(entry.text, entry.attachments, entry.replyToId)
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
                ),
                appendUser = false,
            )
        }
    }

    private fun setMode(mode: AgentMode) {
        val id = currentState.conversationId
        updateState { copy(mode = mode) }
        if (id.isNotBlank()) {
            viewModelScope.launch {
                chats.setMode(id, mode)
                if (mode == AgentMode.Build && currentState.planStatus == PlanStatus.Pending) {
                    chats.setPlan(id, currentState.plan, PlanStatus.Approved)
                }
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

    private fun selectProvider(providerId: String) {
        val option = currentState.providers.firstOrNull { it.id == providerId } ?: return
        persistTurnSettings(providerId = option.id, model = option.model)
        updateState {
            copy(
                providerId = option.id,
                model = option.model,
                picker = null,
                models = emptyList(),
            )
        }
        loadModels()
    }

    private fun selectModel(model: String) {
        persistTurnSettings(model = model)
        updateState { copy(model = model, picker = null) }
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

    private fun loadModels() {
        val state = currentState
        modelJob?.cancel()
        updateState { copy(modelsLoading = true, models = emptyList()) }
        modelJob = viewModelScope.launch {
            val config = settings.configOnce()
            val provider = config.providers.firstOrNull { it.id == state.providerId }
            val key = secrets.apiKey(state.providerId).orEmpty()
            if (provider == null || (provider.requiresKey && key.isBlank())) {
                updateState { copy(modelsLoading = false) }
                return@launch
            }
            val models = try {
                catalog.models(
                    state.providerId,
                    provider.baseUrl,
                    state.model,
                    provider.requestConfig(key),
                    provider.protocol,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emptyList()
            }
            val options = models.map { ModelOption(it.id, it.meta) }
            updateState {
                copy(
                    models = options,
                    modelsLoading = false,
                    model = if (model.isBlank() && options.isNotEmpty()) options.first().id else model,
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
        if (conversationId.isNotBlank()) turns.stop(conversationId)
    }

    private suspend fun reload() {
        val id = currentState.conversationId
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
        val entries = messages.toEntries(compaction) + listOfNotNull(planEntry(conversation))
        updateState {
            copy(
                history = entries,
                live = emptyList(),
                sending = false,
                compacting = false,
                workingText = "",
                providers = config.providers.map { ProviderOption(it.id, it.name, it.model, it.icon) },
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
        val entries = messages.toEntries(compaction) + listOfNotNull(planEntry(conversation))
        updateState {
            copy(
                loading = false,
                conversationId = conversationId,
                title = conversation?.title.orEmpty(),
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
                providers = config.providers.map { ProviderOption(it.id, it.name, it.model, it.icon) },
                providerId = providerId,
                model = model,
                reasoningLevel = level,
                mode = conversation?.mode ?: AgentMode.Build,
                models = emptyList(),
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
                btw = BtwUi(),
                subAgentOpenId = null,
                highlightKey = null,
                selectionKey = null,
                plan = conversation?.plan,
                planStatus = conversation?.planStatus ?: PlanStatus.None,
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
        if (conversationId == currentState.conversationId) return
        loadJob?.cancel()
        updateState { copy(loading = true) }
        loadJob = viewModelScope.launch {
            load(conversationId)
        }
    }

    private suspend fun newConversation() {
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
        updateState {
            copy(
                sending = true,
                compacting = turn.compacting,
                workingText = turn.workingText,
                live = turn.entries.mapIndexed { index, entry -> entry.toUi("live-${turn.turnId}", index) },
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
        statsJob?.cancel()
        result.error?.let { error ->
            val message = error.serverMessage ?: error.serverTitle ?: error.type.name
            chats.append(
                conversationId = result.conversationId,
                role = ChatRole.System,
                content = message,
                label = "failure",
            )
        }
        reload()
        result.error?.let { emitError(it) }
        if (result.compactionFailed) showMessage(R.string.chat_compact_empty)
        refreshTitle()
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
        arguments = arguments,
        output = output,
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

    private suspend fun List<ChatMessage>.toEntries(compaction: ConversationCompaction?): List<ChatEntry> {
        val quotes = chats.messagesByIds(mapNotNull { it.replyToId }.toSet())
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
                    if (message.name != ToolCatalog.SET_WORKING_TEXT) {
                        entries += ChatEntry.Trace(
                            key = "m${message.id}",
                            steps = message.toSteps(),
                        )
                    }
                }
                ChatRole.System -> {
                    if (message.label == "failure") {
                        entries += ChatEntry.Failure(
                            key = "m${message.id}",
                            message = message.content,
                            retryable = true,
                        )
                    } else {
                        entries += ChatEntry.Notice(
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

    private fun ChatMessage.toSteps(): List<TraceStep> {
        val labels = label?.lines()?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
        val summaries = content.lineSequence().filter { it.isNotBlank() }.toList()
        if (labels.size <= 1) {
            return listOf(
                TraceStep(
                    label = labels.firstOrNull() ?: ToolLabels.of(name),
                    status = TraceStatus.Done,
                    summary = summaries.firstOrNull()?.take(120),
                    id = toolCallId.orEmpty(),
                    tool = name.orEmpty(),
                    output = content.take(2000),
                    durationMs = elapsedMs,
                    diffs = stepDiffs.firstOrNull().orEmpty(),
                    media = attachments.map { it.toUi() },
                ),
            )
        }
        return labels.mapIndexed { index, value ->
            TraceStep(
                label = value,
                status = TraceStatus.Done,
                summary = summaries.getOrNull(index)?.take(120),
                id = toolCallId.orEmpty(),
                tool = name.orEmpty(),
                output = content.take(2000),
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
    )
}
