package fan.x0.focus.feature.plugin

import fan.x0.focus.core.mvi.UiAction
import fan.x0.focus.core.mvi.UiEffect
import fan.x0.focus.core.mvi.UiState
import fan.x0.focus.data.plugin.MarketplacePlugin
import fan.x0.focus.data.plugin.PluginStatus

data class PluginsUiState(
    val loading: Boolean = true,
    val plugins: List<PluginStatus> = emptyList(),
    val selectedId: String? = null,
    val market: List<MarketplacePlugin> = emptyList(),
    val marketOpen: Boolean = false,
    val marketLoading: Boolean = false,
    val marketQuery: String = "",
    val registryUrl: String = "",
    val registryOpen: Boolean = false,
    val registryDraft: String = "",
    val logsOpen: Boolean = false,
    val logs: String = "",
    val logsPluginId: String = "",
    val importOpen: Boolean = false,
    val importUrl: String = "",
    val busy: Boolean = false,
) : UiState {
    val selected: PluginStatus?
        get() = plugins.firstOrNull { it.manifest.id == selectedId }
}

sealed interface PluginAction : UiAction {
    data object Refresh : PluginAction
    data class Select(val id: String) : PluginAction
    data object CloseDetail : PluginAction
    data class SetEnabled(val id: String, val enabled: Boolean) : PluginAction
    data class SetCapability(val id: String, val capability: String, val enabled: Boolean) : PluginAction
    data class InstallUri(val uri: String) : PluginAction
    data class InstallMarket(val entry: MarketplacePlugin) : PluginAction
    data object OpenImport : PluginAction
    data object CloseImport : PluginAction
    data class ImportUrlChanged(val value: String) : PluginAction
    data object InstallUrl : PluginAction
    data object OpenMarket : PluginAction
    data object CloseMarket : PluginAction
    data object RefreshMarket : PluginAction
    data class MarketQueryChanged(val value: String) : PluginAction
    data class Uninstall(val id: String) : PluginAction
    data class Reload(val id: String) : PluginAction
    data class ShowLogs(val id: String) : PluginAction
    data object HideLogs : PluginAction
    data class ClearLogs(val id: String) : PluginAction
    data object RefreshRegistry : PluginAction
    data object OpenRegistry : PluginAction
    data object CloseRegistry : PluginAction
    data class RegistryChanged(val value: String) : PluginAction
    data class SetRegistry(val url: String) : PluginAction
}

sealed interface PluginEffect : UiEffect {
    data class Message(val text: String) : PluginEffect
}
