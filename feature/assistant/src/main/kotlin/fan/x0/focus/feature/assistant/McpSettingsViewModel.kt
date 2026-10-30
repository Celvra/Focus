package fan.x0.focus.feature.assistant

import androidx.lifecycle.viewModelScope
import fan.x0.focus.core.error.AppError
import fan.x0.focus.core.error.toAppError
import fan.x0.focus.core.mvi.BaseViewModel
import fan.x0.focus.core.mvi.UiAction
import fan.x0.focus.core.mvi.UiEffect
import fan.x0.focus.core.mvi.UiState
import fan.x0.focus.data.assistant.McpHeader
import fan.x0.focus.data.assistant.McpSecrets
import fan.x0.focus.data.assistant.McpServerConfig
import fan.x0.focus.data.assistant.McpSettings
import fan.x0.focus.data.assistant.McpTestReport
import fan.x0.focus.data.assistant.McpTransport
import fan.x0.focus.data.assistant.SecretStore
import fan.x0.focus.data.assistant.McpManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import java.util.UUID

data class McpHeaderDraft(
    val name: String = "",
    val value: String = "",
    val secret: Boolean = false,
)

data class McpDraft(
    val id: String = "",
    val name: String = "",
    val transport: McpTransport = McpTransport.StreamableHttp,
    val url: String = "",
    val command: String = "",
    val args: String = "",
    val env: String = "",
    val headers: List<McpHeaderDraft> = emptyList(),
    val enabled: Boolean = true,
)

data class McpUiState(
    val loading: Boolean = true,
    val servers: List<McpServerConfig> = emptyList(),
    val draft: McpDraft? = null,
    val testing: Boolean = false,
    val report: McpTestReport? = null,
    val error: AppError? = null,
    val message: String? = null,
) : UiState

sealed interface McpAction : UiAction {
    data object Add : McpAction
    data class Edit(val id: String) : McpAction
    data object Close : McpAction
    data class NameChanged(val value: String) : McpAction
    data class TransportChanged(val value: McpTransport) : McpAction
    data class UrlChanged(val value: String) : McpAction
    data class CommandChanged(val value: String) : McpAction
    data class ArgsChanged(val value: String) : McpAction
    data class EnvChanged(val value: String) : McpAction
    data object AddHeader : McpAction
    data class RemoveHeader(val index: Int) : McpAction
    data class HeaderNameChanged(val index: Int, val value: String) : McpAction
    data class HeaderValueChanged(val index: Int, val value: String) : McpAction
    data class HeaderSecretChanged(val index: Int, val value: Boolean) : McpAction
    data object Save : McpAction
    data object Delete : McpAction
    data object Test : McpAction
    data class SetEnabled(val id: String, val enabled: Boolean) : McpAction
    data object ClearMessage : McpAction
}

sealed interface McpEffect : UiEffect {
    data object Saved : McpEffect
    data object Deleted : McpEffect
}

@KoinViewModel
class McpSettingsViewModel(
    private val settings: McpSettings,
    private val secrets: SecretStore,
    private val manager: McpManager,
) : BaseViewModel<McpAction, McpUiState, McpEffect>(McpUiState()) {

    init {
        viewModelScope.launch {
            val servers = settings.config.first().servers
            updateState { copy(loading = false, servers = servers) }
        }
    }

    override fun onAction(action: McpAction) {
        when (action) {
            McpAction.Add -> updateState {
                copy(
                    draft = McpDraft(id = UUID.randomUUID().toString()),
                    report = null,
                    error = null,
                    message = null,
                )
            }
            is McpAction.Edit -> edit(action.id)
            McpAction.Close -> updateState { copy(draft = null, report = null, error = null) }
            is McpAction.NameChanged -> updateDraft { it.copy(name = action.value) }
            is McpAction.TransportChanged -> updateDraft { it.copy(transport = action.value) }
            is McpAction.UrlChanged -> updateDraft { it.copy(url = action.value) }
            is McpAction.CommandChanged -> updateDraft { it.copy(command = action.value) }
            is McpAction.ArgsChanged -> updateDraft { it.copy(args = action.value) }
            is McpAction.EnvChanged -> updateDraft { it.copy(env = action.value) }
            McpAction.AddHeader -> updateDraft {
                it.copy(headers = it.headers + McpHeaderDraft())
            }
            is McpAction.RemoveHeader -> updateDraft {
                it.copy(headers = it.headers.filterIndexed { index, _ -> index != action.index })
            }
            is McpAction.HeaderNameChanged -> updateHeader(action.index) { it.copy(name = action.value) }
            is McpAction.HeaderValueChanged -> updateHeader(action.index) { it.copy(value = action.value) }
            is McpAction.HeaderSecretChanged -> updateHeader(action.index) { it.copy(secret = action.value) }
            McpAction.Save -> save()
            McpAction.Delete -> delete()
            McpAction.Test -> test()
            is McpAction.SetEnabled -> viewModelScope.launch {
                settings.setEnabled(action.id, action.enabled)
                val servers = settings.configOnce().servers
                updateState { copy(servers = servers) }
            }
            McpAction.ClearMessage -> updateState { copy(message = null) }
        }
    }

    private fun edit(id: String) {
        val server = currentState.servers.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            val headers = server.headers.map {
                McpHeaderDraft(
                    name = it.name,
                    value = if (it.secret) {
                        secrets.secret(McpSecrets.key(server.id, it.name)).orEmpty()
                    } else {
                        it.value
                    },
                    secret = it.secret,
                )
            }
            updateState {
                copy(
                    draft = McpDraft(
                        id = server.id,
                        name = server.name,
                        transport = server.transport,
                        url = server.url,
                        command = server.command,
                        args = server.args.joinToString("\n"),
                        env = server.env.joinToString("\n") { "${it.name}=${it.value}" },
                        headers = headers,
                        enabled = server.enabled,
                    ),
                    report = null,
                    error = null,
                    message = null,
                )
            }
        }
    }

    private fun save() {
        val draft = currentState.draft ?: return
        if (draft.name.isBlank()) {
            updateState { copy(error = AppError(fan.x0.focus.core.error.AppErrorType.Generic)) }
            return
        }
        if (draft.transport != McpTransport.Stdio && draft.url.isBlank()) {
            updateState { copy(error = AppError(fan.x0.focus.core.error.AppErrorType.Generic)) }
            return
        }
        if (draft.transport == McpTransport.Stdio && draft.command.isBlank()) {
            updateState { copy(error = AppError(fan.x0.focus.core.error.AppErrorType.Generic)) }
            return
        }
        viewModelScope.launch {
            val headers = draft.headers.filter { it.name.isNotBlank() }.map { header ->
                if (header.secret) {
                    if (header.value.isNotBlank()) {
                        secrets.setSecret(McpSecrets.key(draft.id, header.name), header.value)
                    }
                    McpHeader(header.name, "", secret = true)
                } else {
                    McpHeader(header.name, header.value, secret = false)
                }
            }
            val server = McpServerConfig(
                id = draft.id.ifBlank { UUID.randomUUID().toString() },
                name = draft.name.trim(),
                transport = draft.transport,
                url = draft.url.trim(),
                command = draft.command.trim(),
                args = draft.args.lines().map { it.trim() }.filter { it.isNotBlank() },
                env = parseEnv(draft.env),
                headers = headers,
                enabled = draft.enabled,
            )
            settings.upsert(server)
            val servers = settings.configOnce().servers
            updateState {
                copy(
                    servers = servers,
                    draft = null,
                    error = null,
                )
            }
            sendEffect(McpEffect.Saved)
        }
    }

    private fun delete() {
        val draft = currentState.draft ?: return
        viewModelScope.launch {
            settings.remove(draft.id)
            draft.headers.filter { it.secret }.forEach { header ->
                secrets.clearSecret(McpSecrets.key(draft.id, header.name))
            }
            val servers = settings.configOnce().servers
            updateState {
                copy(
                    servers = servers,
                    draft = null,
                    report = null,
                )
            }
            sendEffect(McpEffect.Deleted)
        }
    }

    private fun test() {
        val draft = currentState.draft ?: return
        if (currentState.testing) return
        viewModelScope.launch {
            updateState { copy(testing = true, report = null, error = null) }
            try {
                val headers = draft.headers.filter { it.name.isNotBlank() }.map { header ->
                    val value = if (header.secret && header.value.isBlank()) {
                        secrets.secret(McpSecrets.key(draft.id, header.name)).orEmpty()
                    } else {
                        header.value
                    }
                    McpHeader(header.name, value, secret = false)
                }
                val server = McpServerConfig(
                    id = draft.id,
                    name = draft.name.ifBlank { "MCP" },
                    transport = draft.transport,
                    url = draft.url.trim(),
                    command = draft.command.trim(),
                    args = draft.args.lines().map { it.trim() }.filter { it.isNotBlank() },
                    env = parseEnv(draft.env),
                    headers = headers,
                    enabled = true,
                )
                val report = manager.test(server)
                updateState { copy(testing = false, report = report) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState { copy(testing = false, error = e.toAppError()) }
            }
        }
    }

    private fun parseEnv(raw: String): List<McpHeader> = raw.lines().mapNotNull { line ->
        val index = line.indexOf('=')
        if (index <= 0) return@mapNotNull null
        McpHeader(name = line.substring(0, index).trim(), value = line.substring(index + 1).trim())
    }

    private fun updateDraft(reduce: (McpDraft) -> McpDraft) {
        val draft = currentState.draft ?: return
        updateState { copy(draft = reduce(draft)) }
    }

    private fun updateHeader(index: Int, reduce: (McpHeaderDraft) -> McpHeaderDraft) {
        val draft = currentState.draft ?: return
        if (index !in draft.headers.indices) return
        val headers = draft.headers.toMutableList()
        headers[index] = reduce(headers[index])
        updateState { copy(draft = draft.copy(headers = headers)) }
    }
}
