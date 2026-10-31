package fan.x0.focus.feature.bridge

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusChip
import fan.x0.focus.core.designsystem.components.FocusEmptyState
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusLoadingBlock
import fan.x0.focus.core.designsystem.components.FocusOutlinedButton
import fan.x0.focus.core.designsystem.components.FocusTonalButton
import fan.x0.focus.core.designsystem.components.hapticTap
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens

@Composable
fun TerminalPane(state: BridgeUiState, onAction: (BridgeAction) -> Unit) {
    val session = state.opened
    if (session == null) {
        SessionList(state = state, onAction = onAction)
    } else {
        TerminalView(state = state, session = session, onAction = onAction)
    }
}

@Composable
private fun SessionList(state: BridgeUiState, onAction: (BridgeAction) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.bridge_sessions_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = hapticTap { onAction(BridgeAction.LoadSessions) }) {
                Icon(
                    FocusIcons.Refresh,
                    contentDescription = stringResource(R.string.bridge_refresh),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = hapticTap { onAction(BridgeAction.NewSession) }) {
                Icon(
                    FocusIcons.Add,
                    contentDescription = stringResource(R.string.bridge_new_session),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when {
            state.sessionsLoading -> Box(modifier = Modifier.fillMaxSize()) { FocusLoadingBlock() }
            state.sessions.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                FocusEmptyState(
                    icon = FocusIcons.Terminal,
                    title = stringResource(R.string.bridge_sessions_empty),
                    body = stringResource(R.string.bridge_empty_body),
                    actionLabel = stringResource(R.string.bridge_new_session),
                    onAction = { onAction(BridgeAction.NewSession) },
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.sessions, key = { it.id }) { session ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = FocusShapeTokens.card,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        onClick = hapticTap { onAction(BridgeAction.OpenSession(session.id)) },
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = session.name,
                                    style = MaterialTheme.typography.titleSmallEmphasized,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${session.shell}  ${session.cols}x${session.rows}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = hapticTap { onAction(BridgeAction.CloseSession(session.id)) }) {
                                Icon(
                                    FocusIcons.Close,
                                    contentDescription = stringResource(R.string.bridge_session_close),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TerminalView(
    state: BridgeUiState,
    session: BridgeSessionUi,
    onAction: (BridgeAction) -> Unit,
) {
    val lines = remember(state.terminalOutput) {
        parseTerminal(state.terminalOutput, MAX_RENDER_CHARS)
    }
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }
    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.devices.firstOrNull { it.active }?.name?.takeIf { it.isNotBlank() }
                    ?.let { "$it · ${session.name}" }
                    ?: session.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.connecting) {
                Text(
                    text = stringResource(R.string.bridge_terminal_connecting),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            FocusOutlinedButton(
                text = stringResource(R.string.bridge_terminal_disconnect),
                onClick = { onAction(BridgeAction.CloseTerminal) },
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(FocusShapeTokens.card)
                .background(TerminalBackground),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(count = lines.size) { index ->
                    val line = lines[index]
                    Text(
                        text = if (line.isEmpty()) AnnotatedString(" ") else line,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        softWrap = false,
                        maxLines = 1,
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    )
                }
            }
        }
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TerminalKeyChip(stringResource(R.string.bridge_key_escape), TerminalKey.Escape, onAction)
            TerminalKeyChip(stringResource(R.string.bridge_key_tab), TerminalKey.Tab, onAction)
            TerminalKeyChip(stringResource(R.string.bridge_key_enter), TerminalKey.Enter, onAction)
            TerminalKeyChip(stringResource(R.string.bridge_key_ctrl_c), TerminalKey.ControlC, onAction)
            TerminalKeyChip(stringResource(R.string.bridge_key_ctrl_d), TerminalKey.ControlD, onAction)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.terminalInput,
                onValueChange = { onAction(BridgeAction.TerminalInputChanged(it)) },
                label = { Text(stringResource(R.string.bridge_terminal_input)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { onAction(BridgeAction.SendTerminalInput) },
                ),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = hapticTap { onAction(BridgeAction.SendTerminalInput) }) {
                Icon(
                    FocusIcons.Send,
                    contentDescription = stringResource(R.string.bridge_terminal_send),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private const val MAX_RENDER_CHARS = 60_000

@Composable
private fun TerminalKeyChip(label: String, key: TerminalKey, onAction: (BridgeAction) -> Unit) {
    FocusChip(
        label = label,
        selected = false,
        onClick = { onAction(BridgeAction.SendTerminalKey(key)) },
    )
}
