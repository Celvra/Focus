package fan.x0.focus.feature.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusCardGroup
import fan.x0.focus.core.designsystem.components.FocusCardGroupIcon
import fan.x0.focus.core.designsystem.components.FocusCardGroupValue
import fan.x0.focus.core.designsystem.components.FocusChip
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.hapticSelect
import fan.x0.focus.core.designsystem.components.switchItem
import fan.x0.focus.data.assistant.AgentDefaults
import kotlin.math.roundToInt

@Composable
internal fun AgentCard(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    val presets = AgentDefaults.ROUND_PRESETS
    val index = presets.indexOf(state.maxRounds).coerceAtLeast(0)
    val slider = rememberSliderState(
        value = index.toFloat(),
        steps = presets.size - 2,
        trackRange = 0f..presets.lastIndex.toFloat(),
    )
    LaunchedEffect(state.maxRounds) {
        val target = presets.indexOf(state.maxRounds).coerceAtLeast(0).toFloat()
        if (slider.value != target) slider.value = target
    }
    val rounds = presets[slider.value.roundToInt().coerceIn(0, presets.lastIndex)]
    FocusCardGroup(title = stringResource(R.string.settings_agent_title)) {
        formItem(
            headlineContent = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings_agent_rounds_item),
                        modifier = Modifier.weight(1f),
                    )
                    FocusCardGroupValue(
                        text = if (state.unlimitedRounds) {
                            stringResource(R.string.settings_agent_rounds_unlimited)
                        } else {
                            stringResource(R.string.settings_agent_rounds_value, rounds)
                        },
                    )
                }
            },
            supportingContent = { Text(stringResource(R.string.settings_agent_rounds_body)) },
            leadingContent = {
                FocusCardGroupIcon(
                    icon = FocusIcons.Tool,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Slider(
                    state = slider,
                    onValueChange = { slider.value = it },
                    onValueChangeFinished = hapticSelect {
                        val value = presets[slider.value.roundToInt().coerceIn(0, presets.lastIndex)]
                        slider.value = presets.indexOf(value).toFloat()
                        onAction(SettingsAction.MaxRoundsChanged(value))
                    },
                    enabled = !state.unlimitedRounds,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = presets.first().toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = presets.last().toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        switchItem(
            checked = state.unlimitedRounds,
            onCheckedChange = { onAction(SettingsAction.UnlimitedRoundsChanged(it)) },
            headlineContent = { Text(stringResource(R.string.settings_agent_unlimited_item)) },
            supportingContent = { Text(stringResource(R.string.settings_agent_unlimited_body)) },
            leadingContent = {
                FocusCardGroupIcon(
                    icon = FocusIcons.History,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            },
            checkedIcon = FocusIcons.Check,
        )
        formItem(
            headlineContent = { Text(stringResource(R.string.settings_agent_retry_item)) },
            supportingContent = { Text(stringResource(R.string.settings_agent_retry_body)) },
            leadingContent = {
                FocusCardGroupIcon(
                    icon = FocusIcons.Refresh,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            },
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AgentDefaults.RETRY_PRESETS.forEach { retries ->
                    FocusChip(
                        label = if (retries == 0) {
                            stringResource(R.string.settings_agent_retry_off)
                        } else {
                            stringResource(R.string.settings_agent_retry_value, retries)
                        },
                        selected = state.maxRetries == retries,
                        onClick = { onAction(SettingsAction.MaxRetriesChanged(retries)) },
                    )
                }
            }
        }
    }
}
