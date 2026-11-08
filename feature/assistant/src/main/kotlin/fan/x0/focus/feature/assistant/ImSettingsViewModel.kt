package fan.x0.focus.feature.assistant

import androidx.lifecycle.viewModelScope
import fan.x0.focus.core.error.AppError
import fan.x0.focus.core.error.AppErrorType
import fan.x0.focus.core.error.toAppError
import fan.x0.focus.core.mvi.BaseViewModel
import fan.x0.focus.core.mvi.UiAction
import fan.x0.focus.core.mvi.UiEffect
import fan.x0.focus.core.mvi.UiState
import fan.x0.focus.data.assistant.ChatStore
import fan.x0.focus.data.assistant.ConversationOrigin
import fan.x0.focus.data.assistant.SecretStore
import fan.x0.focus.data.im.ImAccountStore
import fan.x0.focus.data.im.ImAccountDraft
import fan.x0.focus.data.im.ImAccountStatus
import fan.x0.focus.data.im.ImAuthSpec
import fan.x0.focus.data.im.ImChannelDescriptor
import fan.x0.focus.data.im.ImChannelRegistry
import fan.x0.focus.data.im.ImChat
import fan.x0.focus.data.im.ImChatKind
import fan.x0.focus.data.im.ImChatStore
import fan.x0.focus.data.im.ImConnectionState
import fan.x0.focus.data.im.ImGateway
import fan.x0.focus.data.im.ImLoginChallenge
import fan.x0.focus.data.im.ImSecretKeys
import fan.x0.focus.data.im.ImServiceBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import java.util.UUID

data class ImAccountUi(
    val id: String,
    val channelName: String,
    val name: String,
    val enabled: Boolean,
    val status: ImConnectionState,
    val statusDetail: String,
)

data class ImChatUi(
    val id: String,
    val accountName: String,
    val title: String,
    val kind: ImChatKind,
    val enabled: Boolean,
    val sendThinking: Boolean,
    val sendTools: Boolean,
    val conversationId: String?,
    val conversationTitle: String?,
    val lastMessageAt: Long,
)

data class ImConversationUi(
    val id: String,
    val title: String,
)

data class ImChannelUi(
    val id: String,
    val name: String,
    val description: String,
)

data class ImLoginUi(
    val accountId: String,
    val accountName: String,
    val challenge: ImLoginChallenge,
)

data class ImFieldDraft(
    val key: String,
    val label: String,
    val secret: Boolean,
    val required: Boolean,
    val value: String,
    val existing: Boolean,
)

data class ImDraft(
    val id: String,
    val channel: String,
    val channelName: String,
    val channelDescription: String,
    val name: String,
    val fields: List<ImFieldDraft>,
    val enabled: Boolean,
    val requireMention: Boolean,
    val onlyOwnerInGroups: Boolean,
    val supportsGroup: Boolean,
    val qrAuth: Boolean,
)

data class ImSettingsUiState(
    val loading: Boolean = true,
    val accounts: List<ImAccountUi> = emptyList(),
    val chats: List<ImChatUi> = emptyList(),
    val channels: List<ImChannelUi> = emptyList(),
    val draft: ImDraft? = null,
    val binding: ImChatUi? = null,
    val addChannelOpen: Boolean = false,
    val login: ImLoginUi? = null,
    val loginHidden: Boolean = false,
    val conversations: List<ImConversationUi> = emptyList(),
    val conversationQuery: String = "",
    val error: AppError? = null,
) : UiState

sealed interface ImAction : UiAction {
    data object Add : ImAction
    data class AddChannel(val channelId: String) : ImAction
    data object CloseAddChannel : ImAction
    data class Edit(val id: String) : ImAction
    data object Close : ImAction
    data class NameChanged(val value: String) : ImAction
    data class FieldChanged(val key: String, val value: String) : ImAction
    data class RequireMentionChanged(val value: Boolean) : ImAction
    data class OnlyOwnerChanged(val value: Boolean) : ImAction
    data class EnabledChanged(val value: Boolean) : ImAction
    data object Save : ImAction
    data object Delete : ImAction
    data class SetEnabled(val id: String, val enabled: Boolean) : ImAction
    data class ToggleChat(val id: String, val enabled: Boolean) : ImAction
    data class ToggleChatThinking(val id: String, val enabled: Boolean) : ImAction
    data class ToggleChatTools(val id: String, val enabled: Boolean) : ImAction
    data class StartLogin(val id: String) : ImAction
    data object HideLogin : ImAction
    data class SubmitLoginCode(val code: String) : ImAction
    data class OpenBinding(val id: String) : ImAction
    data object CloseBinding : ImAction
    data class QueryChanged(val value: String) : ImAction
    data class Bind(val conversationId: String) : ImAction
}

sealed interface ImEffect : UiEffect {
    data object Saved : ImEffect
    data object Deleted : ImEffect
    data object Bound : ImEffect
}

@KoinViewModel
class ImSettingsViewModel(
    private val accounts: ImAccountStore,
    private val chats: ImChatStore,
    private val secrets: SecretStore,
    private val registry: ImChannelRegistry,
    private val conversations: ChatStore,
    private val gateway: ImGateway,
    private val bridge: ImServiceBridge,
) : BaseViewModel<ImAction, ImSettingsUiState, ImEffect>(ImSettingsUiState()) {

    private var loadJob: Job? = null

    init {
        refresh()
        viewModelScope.launch {
            accounts.changes.collect { refresh() }
        }
        viewModelScope.launch {
            chats.changes.collect { refresh() }
        }
        viewModelScope.launch {
            gateway.statuses.collect { statuses ->
                updateState {
                    copy(
                        accounts = this.accounts.map { account ->
                            val status = statuses[account.id] ?: return@map account
                            account.copy(status = status.state, statusDetail = status.detail)
                        },
                    )
                }
            }
        }
        viewModelScope.launch {
            gateway.logins.collect { logins ->
                val accountId = logins.keys.firstOrNull()
                val challenge = accountId?.let { logins[it] }
                updateState {
                    val name = accounts.firstOrNull { it.id == accountId }?.name.orEmpty()
                    val next = if (accountId != null && challenge != null) {
                        ImLoginUi(accountId, name, challenge)
                    } else {
                        null
                    }
                    val same = login?.accountId == next?.accountId &&
                        login?.challenge == next?.challenge
                    copy(
                        login = next,
                        loginHidden = if (same) loginHidden else false,
                    )
                }
            }
        }
    }

    override fun onAction(action: ImAction) {
        when (action) {
            ImAction.Add -> openAddChannel()
            is ImAction.AddChannel -> {
                updateState { copy(addChannelOpen = false) }
                add(action.channelId)
            }
            ImAction.CloseAddChannel -> updateState { copy(addChannelOpen = false) }
            is ImAction.Edit -> edit(action.id)
            ImAction.Close -> updateState { copy(draft = null) }
            is ImAction.NameChanged -> updateDraft { it.copy(name = action.value) }
            is ImAction.FieldChanged -> updateDraft { draft ->
                draft.copy(
                    fields = draft.fields.map { field ->
                        if (field.key == action.key) field.copy(value = action.value) else field
                    },
                )
            }
            is ImAction.RequireMentionChanged -> updateDraft { it.copy(requireMention = action.value) }
            is ImAction.OnlyOwnerChanged -> updateDraft { it.copy(onlyOwnerInGroups = action.value) }
            is ImAction.EnabledChanged -> updateDraft { it.copy(enabled = action.value) }
            ImAction.Save -> save()
            ImAction.Delete -> delete()
            is ImAction.SetEnabled -> viewModelScope.launch {
                accounts.setEnabled(action.id, action.enabled)
                bridge.sync()
            }
            is ImAction.ToggleChat -> viewModelScope.launch {
                chats.setEnabled(action.id, action.enabled)
            }
            is ImAction.ToggleChatThinking -> viewModelScope.launch {
                chats.setSendThinking(action.id, action.enabled)
                updateBinding(action.id) { it.copy(sendThinking = action.enabled) }
            }
            is ImAction.ToggleChatTools -> viewModelScope.launch {
                chats.setSendTools(action.id, action.enabled)
                updateBinding(action.id) { it.copy(sendTools = action.enabled) }
            }
            is ImAction.StartLogin -> startLogin(action.id)
            ImAction.HideLogin -> updateState { copy(loginHidden = true) }
            is ImAction.SubmitLoginCode -> submitLoginCode(action.code)
            is ImAction.OpenBinding -> openBinding(action.id)
            ImAction.CloseBinding -> updateState { copy(binding = null) }
            is ImAction.QueryChanged -> updateState { copy(conversationQuery = action.value) }
            is ImAction.Bind -> bind(action.conversationId)
        }
    }

    private fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val accountList = accounts.list()
                val chatList = chats.list()
                val statuses = gateway.statuses.value
                val descriptors = registry.descriptors.associateBy { it.id }
                updateState {
                    copy(
                        loading = false,
                        accounts = accountList.map { account ->
                            val status = statuses[account.id] ?: ImAccountStatus(account.status, account.statusDetail)
                            ImAccountUi(
                                id = account.id,
                                channelName = descriptors[account.channel]?.name ?: account.channel,
                                name = account.name,
                                enabled = account.enabled,
                                status = status.state,
                                statusDetail = status.detail,
                            )
                        },
                        chats = chatList.map { it.toUi() },
                        channels = registry.descriptors.map {
                            ImChannelUi(it.id, it.name, it.description)
                        },
                        error = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState { copy(loading = false, error = e.toAppError()) }
            }
        }
    }

    private fun openAddChannel() {
        val channels = registry.descriptors
        if (channels.isEmpty()) return
        if (channels.size == 1) {
            add(channels.first().id)
            return
        }
        updateState { copy(addChannelOpen = true) }
    }

    private fun add(channelId: String) {
        val descriptor = registry.descriptor(channelId) ?: return
        updateState {
            copy(
                draft = ImDraft(
                    id = UUID.randomUUID().toString(),
                    channel = descriptor.id,
                    channelName = descriptor.name,
                    channelDescription = descriptor.description,
                    name = descriptor.name,
                    fields = descriptor.fields(),
                    enabled = true,
                    requireMention = true,
                    onlyOwnerInGroups = false,
                    supportsGroup = descriptor.supportsGroup(),
                    qrAuth = descriptor.auth is ImAuthSpec.Qr,
                ),
                error = null,
            )
        }
    }

    private fun edit(id: String) {
        viewModelScope.launch {
            try {
                val account = accounts.account(id) ?: return@launch
                val descriptor = registry.descriptor(account.channel) ?: return@launch
                val fields = descriptor.fields().map { field ->
                    val value = if (field.secret) {
                        ""
                    } else {
                        account.config.values[field.key].orEmpty()
                    }
                    val existing = field.secret &&
                        !secrets.secret(ImSecretKeys.field(account.id, field.key)).isNullOrBlank()
                    field.copy(value = value, existing = existing)
                }
                updateState {
                    copy(
                        draft = ImDraft(
                            id = account.id,
                            channel = account.channel,
                            channelName = descriptor.name,
                            channelDescription = descriptor.description,
                            name = account.name,
                            fields = fields,
                            enabled = account.enabled,
                            requireMention = account.config.requireMention,
                            onlyOwnerInGroups = account.config.onlyOwnerInGroups,
                            supportsGroup = descriptor.supportsGroup(),
                            qrAuth = descriptor.auth is ImAuthSpec.Qr,
                        ),
                        error = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState { copy(error = e.toAppError()) }
            }
        }
    }

    private fun save() {
        val draft = currentState.draft ?: return
        if (draft.name.isBlank()) {
            updateState { copy(error = AppError(AppErrorType.Generic)) }
            return
        }
        val missing = draft.fields.firstOrNull { it.required && it.value.isBlank() && !it.existing }
        if (missing != null) {
            updateState { copy(error = AppError(AppErrorType.Generic)) }
            return
        }
        viewModelScope.launch {
            try {
                draft.fields.filter { it.secret && it.value.isNotBlank() }.forEach { field ->
                    secrets.setSecret(ImSecretKeys.field(draft.id, field.key), field.value.trim())
                }
                val values = draft.fields
                    .filterNot { it.secret }
                    .associate { it.key to it.value.trim() }
                accounts.upsert(
                    ImAccountDraft(
                        id = draft.id,
                        channel = draft.channel,
                        name = draft.name.trim(),
                        values = values,
                        enabled = draft.enabled,
                        requireMention = draft.requireMention,
                        onlyOwnerInGroups = draft.onlyOwnerInGroups,
                    ),
                )
                updateState { copy(draft = null, error = null) }
                sendEffect(ImEffect.Saved)
                bridge.sync()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState { copy(error = e.toAppError()) }
            }
        }
    }

    private fun delete() {
        val draft = currentState.draft ?: return
        viewModelScope.launch {
            try {
                draft.fields.filter { it.secret }.forEach { field ->
                    secrets.clearSecret(ImSecretKeys.field(draft.id, field.key))
                }
                secrets.clearSecret(ImSecretKeys.field(draft.id, ImSecretKeys.TOKEN))
                gateway.stop(draft.id)
                accounts.delete(draft.id)
                updateState { copy(draft = null, error = null) }
                sendEffect(ImEffect.Deleted)
                bridge.sync()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState { copy(error = e.toAppError()) }
            }
        }
    }

    private fun startLogin(id: String) {
        viewModelScope.launch {
            try {
                secrets.clearSecret(ImSecretKeys.field(id, ImSecretKeys.TOKEN))
                updateState { copy(draft = null, loginHidden = false) }
                gateway.restart(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState { copy(error = e.toAppError()) }
            }
        }
    }

    private fun submitLoginCode(code: String) {
        val login = currentState.login ?: return
        if (code.isBlank()) return
        viewModelScope.launch {
            gateway.submitLoginCode(login.accountId, code)
        }
    }

    private fun openBinding(id: String) {
        val chat = currentState.chats.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            try {
                val list = conversations.conversations().map { ImConversationUi(it.id, it.title) }
                updateState { copy(binding = chat, conversations = list, conversationQuery = "") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState { copy(error = e.toAppError()) }
            }
        }
    }

    private fun bind(conversationId: String) {
        val binding = currentState.binding ?: return
        viewModelScope.launch {
            try {
                if (conversationId == NEW_CONVERSATION) {
                    val title = "${binding.accountName} / ${binding.title}".take(80)
                    val created = conversations.createConversation(title, origin = ConversationOrigin.Im)
                    chats.setConversation(binding.id, created.id)
                } else {
                    chats.setConversation(binding.id, conversationId)
                }
                updateState { copy(binding = null, error = null) }
                sendEffect(ImEffect.Bound)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState { copy(error = e.toAppError()) }
            }
        }
    }

    private fun updateDraft(reduce: (ImDraft) -> ImDraft) {
        updateState {
            val draft = draft ?: return@updateState this
            copy(draft = reduce(draft), error = null)
        }
    }

    private fun updateBinding(id: String, reduce: (ImChatUi) -> ImChatUi) {
        updateState {
            val binding = binding ?: return@updateState this
            if (binding.id != id) return@updateState this
            copy(binding = reduce(binding))
        }
    }

    private fun ImChannelDescriptor.fields(): List<ImFieldDraft> {
        val spec = auth as? ImAuthSpec.Fields ?: return emptyList()
        return spec.fields.map {
            ImFieldDraft(
                key = it.key,
                label = it.label,
                secret = it.secret,
                required = it.required,
                value = "",
                existing = false,
            )
        }
    }

    private fun ImChannelDescriptor.supportsGroup(): Boolean =
        capabilities.chatKinds.contains(ImChatKind.Group)

    private fun ImChat.toUi(): ImChatUi = ImChatUi(
        id = id,
        accountName = accountName,
        title = title,
        kind = kind,
        enabled = enabled,
        sendThinking = sendThinking,
        sendTools = sendTools,
        conversationId = conversationId,
        conversationTitle = conversationTitle,
        lastMessageAt = lastMessageAt,
    )

    companion object {
        const val NEW_CONVERSATION = "__new__"
    }
}
