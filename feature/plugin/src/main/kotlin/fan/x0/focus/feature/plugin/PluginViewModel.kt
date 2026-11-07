package fan.x0.focus.feature.plugin

import android.net.Uri
import androidx.lifecycle.viewModelScope
import fan.x0.focus.core.mvi.BaseViewModel
import fan.x0.focus.data.plugin.PluginManager
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class PluginViewModel(
    private val manager: PluginManager,
) : BaseViewModel<PluginAction, PluginsUiState, PluginEffect>(PluginsUiState()) {
    init {
        viewModelScope.launch {
            manager.statuses.collect { statuses ->
                updateState {
                    copy(
                        loading = false,
                        plugins = statuses.values.sortedBy { it.manifest.name.lowercase() },
                    )
                }
            }
        }
        viewModelScope.launch {
            val url = manager.registryUrl()
            updateState { copy(registryUrl = url) }
        }
    }

    override fun onAction(action: PluginAction) {
        when (action) {
            PluginAction.Refresh -> refresh()
            is PluginAction.Select -> updateState { copy(selectedId = action.id) }
            PluginAction.CloseDetail -> updateState { copy(selectedId = null) }
            is PluginAction.SetEnabled -> run(action.id) { manager.setEnabled(action.id, action.enabled) }
            is PluginAction.SetCapability -> run(action.id) {
                manager.setCapability(action.id, action.capability, action.enabled)
            }
            is PluginAction.InstallUri -> run("install") { manager.installUri(Uri.parse(action.uri)) }
            is PluginAction.InstallMarket -> run("install") { manager.installMarket(action.entry) }
            PluginAction.OpenImport -> updateState { copy(importOpen = true, importUrl = "") }
            PluginAction.CloseImport -> updateState { copy(importOpen = false) }
            is PluginAction.ImportUrlChanged -> updateState { copy(importUrl = action.value) }
            PluginAction.InstallUrl -> {
                val url = currentState.importUrl.trim()
                if (url.isBlank()) {
                    sendEffect(PluginEffect.Message("URL is required"))
                    return
                }
                updateState { copy(importOpen = false) }
                run("install") { manager.installUrl(url) }
            }
            PluginAction.OpenMarket -> {
                updateState { copy(marketOpen = true, marketLoading = true) }
                loadMarket()
            }
            PluginAction.CloseMarket -> updateState { copy(marketOpen = false) }
            PluginAction.RefreshMarket -> {
                updateState { copy(marketLoading = true) }
                loadMarket()
            }
            is PluginAction.MarketQueryChanged -> {
                updateState { copy(marketQuery = action.value) }
                loadMarket()
            }
            is PluginAction.Uninstall -> run(action.id) {
                manager.uninstall(action.id)
                updateState { copy(selectedId = null) }
            }
            is PluginAction.Reload -> run(action.id) { manager.reload(action.id) }
            is PluginAction.ShowLogs -> updateState {
                copy(
                    logsOpen = true,
                    logsPluginId = action.id,
                    logs = manager.logs(action.id),
                )
            }
            PluginAction.HideLogs -> updateState { copy(logsOpen = false) }
            is PluginAction.ClearLogs -> {
                manager.clearLogs(action.id)
                updateState { copy(logs = "") }
            }
            PluginAction.RefreshRegistry -> viewModelScope.launch {
                val url = manager.registryUrl()
                updateState { copy(registryUrl = url) }
            }
            PluginAction.OpenRegistry -> updateState {
                copy(registryOpen = true, registryDraft = registryUrl)
            }
            PluginAction.CloseRegistry -> updateState { copy(registryOpen = false) }
            is PluginAction.RegistryChanged -> updateState { copy(registryDraft = action.value) }
            is PluginAction.SetRegistry -> {
                updateState { copy(registryOpen = false, registryUrl = action.url) }
                run("registry") { manager.setRegistryUrl(action.url) }
            }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            updateState { copy(loading = true) }
            runCatching { manager.loadAll() }
                .onFailure { sendEffect(PluginEffect.Message(it.message.orEmpty())) }
            updateState { copy(loading = false) }
        }
    }

    private fun loadMarket() {
        viewModelScope.launch {
            runCatching { manager.listMarketplace(currentState.marketQuery) }
                .onSuccess { entries -> updateState { copy(market = entries, marketLoading = false) } }
                .onFailure {
                    updateState { copy(marketLoading = false) }
                    sendEffect(PluginEffect.Message(it.message.orEmpty()))
                }
        }
    }

    private fun run(key: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            updateState { copy(busy = true) }
            runCatching { block() }
                .onSuccess { sendEffect(PluginEffect.Message("Done")) }
                .onFailure { sendEffect(PluginEffect.Message(it.message.orEmpty())) }
            updateState { copy(busy = false) }
        }
    }
}
