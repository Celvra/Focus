package fan.x0.focus.data.plugin

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PluginRoute(
    val pluginId: String,
    val pageId: String,
    val args: String? = null,
)

object PluginNavigation {
    private val _route = MutableStateFlow<PluginRoute?>(null)
    val route: StateFlow<PluginRoute?> = _route.asStateFlow()

    fun open(pluginId: String, pageId: String, args: String? = null) {
        _route.value = PluginRoute(pluginId, pageId, args)
    }

    fun consume() {
        _route.value = null
    }
}
