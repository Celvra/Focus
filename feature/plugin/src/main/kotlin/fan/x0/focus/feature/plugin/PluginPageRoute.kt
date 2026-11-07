package fan.x0.focus.feature.plugin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import fan.x0.focus.data.plugin.PluginRegistry
import fan.x0.focus.plugin.LocalPluginContext
import fan.x0.focus.plugin.PluginContext
import fan.x0.focus.plugin.PluginPageScope
import fan.x0.focus.plugin.PluginScaffold
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun PluginPageRoute(
    pluginId: String,
    pageId: String,
    args: String?,
    onBack: () -> Unit,
    onNavigate: (String, String?) -> Unit,
) {
    val registry = koinInject<PluginRegistry>()
    val handle = registry.page(pluginId, pageId)
    if (handle == null) {
        PluginScaffold(title = pluginId, onBack = onBack) {
            item {
                Text("Plugin page is not available. Install or enable the plugin first.")
            }
        }
        return
    }
    val plugin = handle.plugin
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pageScope = remember(pluginId, pageId, args, plugin.classLoader) {
        PluginPageScopeImpl(
            plugin = plugin.context,
            args = args,
            snackbar = snackbar,
            scope = scope,
            onNavigate = onNavigate,
            onBack = onBack,
        )
    }
    Box(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalPluginContext provides plugin.context) {
            val content = handle.page.content
            with(pageScope) { content() }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

private class PluginPageScopeImpl(
    override val plugin: PluginContext,
    override val args: String?,
    private val snackbar: SnackbarHostState,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val onNavigate: (String, String?) -> Unit,
    private val onBack: () -> Unit,
) : PluginPageScope {
    override val host get() = plugin.host

    override fun navigate(pageId: String, args: String?) {
        onNavigate(pageId, args)
    }

    override fun back() {
        onBack()
    }

    override fun close() {
        onBack()
    }

    override fun snackbar(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }
}
