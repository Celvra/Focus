package fan.x0.focus.core.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.R

// Screens use these to reach the shell without depending on the app module.
val LocalFocusDrawerOpener = staticCompositionLocalOf<() -> Unit> { {} }
val LocalFocusDrawerAvailable = staticCompositionLocalOf { true }
val LocalFocusSettingsOpener = staticCompositionLocalOf<() -> Unit> { {} }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusLargeTopBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    onMenu: (() -> Unit)? = if (LocalFocusDrawerAvailable.current) LocalFocusDrawerOpener.current else null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    LargeFlexibleTopAppBar(
        title = { Text(title) },
        modifier = modifier,
        navigationIcon = {
            when {
                onBack != null -> IconButton(onClick = onBack) {
                    Icon(FocusIcons.Back, contentDescription = stringResource(R.string.common_back))
                }
                onMenu != null -> IconButton(onClick = onMenu) {
                    Icon(FocusIcons.Menu, contentDescription = null)
                }
            }
        },
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusCompactTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onMenu: (() -> Unit)? = if (LocalFocusDrawerAvailable.current) LocalFocusDrawerOpener.current else null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = {
            when {
                onBack != null -> IconButton(onClick = onBack) {
                    Icon(FocusIcons.Back, contentDescription = stringResource(R.string.common_back))
                }
                onMenu != null -> IconButton(onClick = onMenu) {
                    Icon(FocusIcons.Menu, contentDescription = null)
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}
