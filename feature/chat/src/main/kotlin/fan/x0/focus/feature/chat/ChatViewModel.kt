package fan.x0.focus.feature.chat

import androidx.lifecycle.viewModelScope
import fan.x0.focus.core.error.AppError
import fan.x0.focus.core.error.AppErrorType
import fan.x0.focus.core.mvi.BaseViewModel
import fan.x0.focus.data.assistant.AttachmentKind
import fan.x0.focus.data.assistant.AttachmentLoader
import fan.x0.focus.data.assistant.AssistantSettings
import fan.x0.focus.data.assistant.ChatAttachment
import fan.x0.focus.data.assistant.ChatCommand
import fan.x0.focus.data.assistant.ChatMessage
import fan.x0.focus.data.assistant.ChatRole
import fan.x0.focus.data.assistant.ChatStore
import fan.x0.focus.data.assistant.ChatSwitcher
import fan.x0.focus.data.assistant.ChatTurnApprovalPhase
import fan.x0.focus.data.assistant.ChatTurnEntry
import fan.x0.focus.data.assistant.ChatTurnResult
import fan.x0.focus.data.assistant.ChatTurnRunner
import fan.x0.focus.data.assistant.ChatTurnState
import fan.x0.focus.data.assistant.ChatTurnStep
import fan.x0.focus.data.assistant.ChatTurnStepStatus
import fan.x0.focus.data.assistant.ModelCatalogRepository
import fan.x0.focus.data.assistant.ProfileStore
import fan.x0.focus.data.assistant.ReasoningLevel
import fan.x0.focus.data.assistant.SecretStore
import fan.x0.focus.data.assistant.ToolCatalog
import fan.x0.focus.data.assistant.ToolLabels
import fan.x0.focus.data.assistant.TurnOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class ChatViewModel(
    private val chats: ChatStore,
    private val switcher: ChatSwitcher,
    private val profile: ProfileStore,
    private val settings: AssistantSettings,
    private val secrets: SecretStore,
    private val catalog: ModelCatalogRepository,
    private val loader: AttachmentLoader,
    private val turns: ChatTurnRunner,
) : BaseViewModel<ChatAction, ChatUiState, ChatEffect>(ChatUiState()) {

    private var loadJob: Job? = null
    private var modelJob: Job? = null

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
    }

    override fun onAction(action: ChatAction) {
        when (action) {
            is ChatAction.InputChanged -> updateState { copy(input = action.value) }
            is ChatAction.Suggest -> {
                updateState { copy(input = action.text) }
                send()
            }
            ChatAction.Send -> send()
            ChatAction.Stop -> stop()
            is ChatAction.Decide -> decide(action)
            ChatAction.NewConversation -> viewModelScope.launch { newConversation() }
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
        }
    }

    private fun send() {
        val state = currentState
        val text = state.input.trim()
        if ((text.isBlank() && state.attachments.isEmpty()) || state.sending) return
        val conversationId = state.conversationId
        if (conversationId.isBlank() || turns.isRunning(conversationId)) return
        val attachments = state.attachments.map { it.toModel() }
        val options = TurnOptions(
            providerId = state.providerId,
            model = state.model,
            reasoning = state.reasoningLevel,
            attachments = attachments,
        )
        updateState {
            copy(
                input = "",
                sending = true,
                workingText = "",
                live = emptyList(),
                attachments = emptyList(),
                turn = turn + 1,
                history = history + ChatEntry.User(
                    key = "local-${System.currentTimeMillis()}",
                    text = text,
                    attachments = state.attachments,
                ),
            )
        }
        turns.start(conversationId, text, options)
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
            if (provider == null || key.isBlank()) {
                updateState { copy(modelsLoading = false) }
                return@launch
            }
            val models = try {
                catalog.models(state.providerId, provider.baseUrl, state.model, key)
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

    private suspend fun reload() {
        val id = currentState.conversationId
        val messages = chats.messages(id)
        updateState {
            copy(
                history = messages.toEntries(),
                live = emptyList(),
                sending = false,
                workingText = "",
            )
        }
    }

    private suspend fun load(conversationId: String) {
        val messages = chats.messages(conversationId)
        val conversation = chats.conversation(conversationId)
        val person = profile.read()
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
        updateState {
            copy(
                loading = false,
                conversationId = conversationId,
                title = conversation?.title.orEmpty(),
                name = person.name,
                history = messages.toEntries(),
                live = emptyList(),
                sending = false,
                workingText = "",
                input = "",
                turn = 0,
                attachments = emptyList(),
                providers = config.providers.map { ProviderOption(it.id, it.name, it.model) },
                providerId = providerId,
                model = model,
                reasoningLevel = level,
                models = emptyList(),
                modelsLoading = false,
                picker = null,
                modelQuery = "",
            )
        }
        switcher.markLoaded(conversationId)
        applyTurn(turns.state(conversationId))
        turns.result(conversationId)?.let { handleResult(it) }
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
        val conversation = chats.createConversation("")
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
            updateState { copy(sending = false, live = emptyList(), workingText = "") }
            return
        }
        updateState {
            copy(
                sending = true,
                workingText = turn.workingText,
                live = turn.entries.mapIndexed { index, entry -> entry.toUi(turn.turnId, index) },
            )
        }
    }

    private suspend fun handleResult(result: ChatTurnResult) {
        if (result.conversationId != currentState.conversationId) return
        if (!turns.consumeResult(result.conversationId, result.turnId)) return
        reload()
        result.error?.let { emitError(it) }
        refreshTitle()
    }

    private suspend fun refreshTitle() {
        val id = currentState.conversationId
        if (id.isBlank()) return
        val title = chats.conversation(id)?.title.orEmpty()
        updateState { copy(title = title) }
    }

    private fun ChatTurnEntry.toUi(turnId: Long, index: Int): ChatEntry = when (this) {
        is ChatTurnEntry.Answer -> ChatEntry.Assistant(
            key = "live-answer-$turnId-$index",
            text = text,
            live = true,
            reasoning = reasoning,
        )
        is ChatTurnEntry.Trace -> ChatEntry.Trace(
            key = "live-trace-$turnId-$index",
            steps = steps.map { it.toUi() },
        )
        is ChatTurnEntry.Approval -> ChatEntry.Approval(
            key = "live-approval-${card.id}",
            card = ApprovalUi(
                id = card.id,
                title = card.title,
                summary = card.summary,
                steps = card.steps.map { it.toUi() },
                phase = card.phase.toUi(),
            ),
        )
    }

    private fun ChatTurnStep.toUi(): TraceStep = TraceStep(
        label = label,
        status = when (status) {
            ChatTurnStepStatus.Pending -> TraceStatus.Pending
            ChatTurnStepStatus.Running -> TraceStatus.Running
            ChatTurnStepStatus.Done -> TraceStatus.Done
            ChatTurnStepStatus.Failed -> TraceStatus.Failed
        },
        summary = summary,
    )

    private fun ChatTurnApprovalPhase.toUi(): ApprovalPhase = when (this) {
        ChatTurnApprovalPhase.Waiting -> ApprovalPhase.Waiting
        ChatTurnApprovalPhase.Running -> ApprovalPhase.Running
        ChatTurnApprovalPhase.Finished -> ApprovalPhase.Finished
        ChatTurnApprovalPhase.Declined -> ApprovalPhase.Declined
    }

    private fun List<ChatMessage>.toEntries(): List<ChatEntry> {
        val entries = mutableListOf<ChatEntry>()
        val steps = mutableListOf<TraceStep>()

        fun flushSteps() {
            if (steps.isEmpty()) return
            entries += ChatEntry.Trace("trace-${entries.size}", steps.toList())
            steps.clear()
        }

        forEach { message ->
            when (message.role) {
                ChatRole.User -> {
                    flushSteps()
                    entries += ChatEntry.User(
                        key = "m${message.id}",
                        text = message.content,
                        attachments = message.attachments.map { it.toUi() },
                    )
                }
                ChatRole.Assistant -> {
                    if (message.content.isNotBlank() || message.reasoning.isNotBlank()) {
                        flushSteps()
                        entries += ChatEntry.Assistant(
                            "m${message.id}",
                            message.content,
                            live = false,
                            reasoning = message.reasoning,
                        )
                    }
                }
                ChatRole.Tool -> if (message.name != ToolCatalog.SET_WORKING_TEXT) steps += message.toSteps()
                ChatRole.System -> Unit
            }
        }
        flushSteps()
        return entries
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
                ),
            )
        }
        return labels.mapIndexed { index, value ->
            TraceStep(value, TraceStatus.Done, summaries.getOrNull(index)?.take(120))
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
}
