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
import fan.x0.focus.data.assistant.ChatEngine
import fan.x0.focus.data.assistant.ChatEvent
import fan.x0.focus.data.assistant.ChatMessage
import fan.x0.focus.data.assistant.ChatMode
import fan.x0.focus.data.assistant.ChatRole
import fan.x0.focus.data.assistant.ChatStore
import fan.x0.focus.data.assistant.ChatSwitcher
import fan.x0.focus.data.assistant.ChatTitleGenerator
import fan.x0.focus.data.assistant.ModelCatalogRepository
import fan.x0.focus.data.assistant.ProfileStore
import fan.x0.focus.data.assistant.ReasoningLevel
import fan.x0.focus.data.assistant.SecretStore
import fan.x0.focus.data.assistant.ToolLabels
import fan.x0.focus.data.assistant.TurnOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class ChatViewModel(
    private val engine: ChatEngine,
    private val chats: ChatStore,
    private val switcher: ChatSwitcher,
    private val profile: ProfileStore,
    private val settings: AssistantSettings,
    private val secrets: SecretStore,
    private val catalog: ModelCatalogRepository,
    private val loader: AttachmentLoader,
    private val titles: ChatTitleGenerator,
) : BaseViewModel<ChatAction, ChatUiState, ChatEffect>(ChatUiState()) {

    private var turnJob: Job? = null
    private var loadJob: Job? = null
    private var modelJob: Job? = null
    private var titleJob: Job? = null
    private var titleJobConversationId: String? = null

    init {
        viewModelScope.launch {
            val conversation = chats.ensureConversation()
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
        turnJob?.cancel()
        val conversationId = state.conversationId
        if (conversationId.isBlank()) return
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
        turnJob = viewModelScope.launch {
            try {
                engine.send(conversationId, text, ChatMode.Chat, options).collect { handle(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState { copy(sending = false, live = emptyList()) }
                emitError(AppError(AppErrorType.Generic, serverMessage = e.message ?: e.javaClass.simpleName))
            }
        }
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
        turnJob?.cancel()
        val conversationId = currentState.conversationId
        val pending = currentState.live.filterIsInstance<ChatEntry.Assistant>().lastOrNull()?.takeIf { it.live }
        val answer = pending?.text?.trim().orEmpty()
        val thought = pending?.reasoning?.trim().orEmpty()
        if (answer.isNotBlank() || thought.isNotBlank()) {
            viewModelScope.launch {
                chats.append(
                    conversationId = conversationId,
                    role = ChatRole.Assistant,
                    content = answer,
                    reasoning = thought,
                )
            }
        }
        updateState {
            copy(
                history = history + live.map { it.sealed() },
                live = emptyList(),
                sending = false,
            )
        }
    }

    private suspend fun handle(event: ChatEvent) {
        when (event) {
            is ChatEvent.Delta -> updateState { copy(live = live.appendDelta(turn, event.text)) }
            is ChatEvent.ReasoningDelta -> updateState { copy(live = live.appendReasoning(turn, event.text)) }
            is ChatEvent.UserMessageAppended -> requestTitle(event.turns)
            is ChatEvent.ToolStarted -> updateState {
                copy(live = live.startStep(turn, event.label))
            }
            is ChatEvent.ToolFinished -> {
                updateState { copy(live = live.finishStep(event.label, event.ok, event.summary)) }
                if (event.tool == "rename_conversation") refreshTitle()
            }
            is ChatEvent.ApprovalRequested -> updateState {
                val card = event.request.toUi(ApprovalPhase.Waiting)
                copy(live = live.sealLive() + ChatEntry.Approval(card.id, card))
            }
            is ChatEvent.ApprovalResolved -> updateState {
                val card = live.approvalCard() ?: return@updateState this
                copy(
                    live = live.updateApproval(
                        card.copy(
                            phase = if (event.approved) ApprovalPhase.Running else ApprovalPhase.Declined,
                        ),
                    ),
                )
            }
            ChatEvent.Completed -> finishTurn()
            is ChatEvent.Failed -> {
                val state = currentState
                val pending = state.live.filterIsInstance<ChatEntry.Assistant>().lastOrNull()?.takeIf { it.live }
                if (pending != null && (pending.text.isNotBlank() || pending.reasoning.isNotBlank())) {
                    chats.append(
                        conversationId = state.conversationId,
                        role = ChatRole.Assistant,
                        content = pending.text,
                        reasoning = pending.reasoning,
                    )
                }
                reload()
                emitError(event.error)
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
        engine.decide(action.approvalId, action.approve)
    }

    private suspend fun finishTurn() {
        updateState {
            copy(
                history = history + live.map { it.sealed() },
                live = emptyList(),
                sending = false,
            )
        }
        refreshTitle()
    }

    private suspend fun reload() {
        val id = currentState.conversationId
        val messages = chats.messages(id)
        updateState {
            copy(
                history = messages.toEntries(),
                live = emptyList(),
                sending = false,
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
    }

    private suspend fun switchTo(conversationId: String) {
        if (conversationId == currentState.conversationId) return
        turnJob?.cancel()
        loadJob?.cancel()
        titleJob?.cancel()
        updateState { copy(loading = true) }
        loadJob = viewModelScope.launch {
            load(conversationId)
        }
    }

    private suspend fun newConversation() {
        turnJob?.cancel()
        loadJob?.cancel()
        titleJob?.cancel()
        if (currentState.history.isEmpty() && currentState.conversationId.isNotBlank()) {
            load(currentState.conversationId)
            return
        }
        val conversation = chats.createConversation("")
        load(conversation.id)
    }

    private suspend fun ask(text: String) {
        if (currentState.history.isNotEmpty()) newConversation()
        turnJob?.cancel()
        updateState { copy(input = text, sending = false, live = emptyList()) }
        send()
    }

    private fun requestTitle(turns: Int) {
        val conversationId = currentState.conversationId
        if (conversationId.isBlank()) return
        if (titleJobConversationId == conversationId && titleJob?.isActive == true) return
        val due = currentState.title.isBlank() || turns == 1 || turns % TITLE_REFRESH_TURNS == 0
        if (!due) return
        val providerId = currentState.providerId
        val model = currentState.model
        titleJobConversationId = conversationId
        titleJob = viewModelScope.launch {
            val title = try {
                titles.generate(conversationId, providerId, model)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                null
            } ?: return@launch
            if (currentState.conversationId == conversationId) {
                updateState { copy(title = title) }
            }
        }
    }

    private suspend fun refreshTitle() {
        val id = currentState.conversationId
        val title = chats.conversation(id)?.title.orEmpty()
        updateState { copy(title = title) }
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
                ChatRole.Tool -> steps += message.toSteps()
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

    private fun fan.x0.focus.data.assistant.ApprovalRequest.toUi(phase: ApprovalPhase): ApprovalUi =
        ApprovalUi(
            id = id,
            title = title,
            summary = summary,
            steps = steps.map { TraceStep(it.label, TraceStatus.Pending) },
            phase = phase,
        )

    companion object {
        private const val TITLE_REFRESH_TURNS = 10
    }
}
