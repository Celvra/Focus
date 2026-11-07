package fan.x0.focus.feature.plugin

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import fan.x0.focus.core.designsystem.components.FocusReveal
import fan.x0.focus.plugin.PluginScaffold

@Composable
internal fun PluginLogsPage(
    pluginId: String,
    text: String,
    onBack: () -> Unit,
    onClear: () -> Unit,
) {
    PluginScaffold(title = pluginId, onBack = onBack) {
        item(key = "actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.plugins_clear_logs))
                }
            }
        }
        item(key = "logs") {
            FocusReveal {
                Text(
                    text = text.ifBlank { "(no logs)" },
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
