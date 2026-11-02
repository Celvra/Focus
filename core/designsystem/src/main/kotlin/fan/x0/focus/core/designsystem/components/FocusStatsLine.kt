package fan.x0.focus.core.designsystem.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun FocusStatsLine(
    parts: List<String>,
    modifier: Modifier = Modifier,
) {
    if (parts.isEmpty()) return
    Text(
        text = parts.joinToString(" · "),
        modifier = modifier.padding(start = 6.dp, top = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}
