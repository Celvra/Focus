package fan.x0.focus.plugin

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import fan.x0.focus.core.designsystem.components.FocusPageScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    snackbar: SnackbarHostState? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    FocusPageScaffold(
        title = title,
        onBack = onBack,
        snackbar = snackbar,
        actions = actions,
        content = content,
    )
}
