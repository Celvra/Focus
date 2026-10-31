package fan.x0.focus.feature.assistant.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusCardGroup
import fan.x0.focus.core.designsystem.components.FocusCardGroupIcon
import fan.x0.focus.core.designsystem.components.FocusChip
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusLoadingBlob
import fan.x0.focus.core.designsystem.components.FocusReveal
import fan.x0.focus.data.assistant.CommandPolicy
import fan.x0.focus.data.assistant.JudgeKind
import fan.x0.focus.feature.assistant.AgentCard
import fan.x0.focus.feature.assistant.R
import fan.x0.focus.feature.assistant.SettingsAction
import fan.x0.focus.feature.assistant.SettingsUiState

@Composable
internal fun SettingsChatAgentPage(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onBack: () -> Unit,
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_page_chat_agent),
        onBack = onBack,
    ) {
        item(key = "intro") {
            FocusReveal {
                Text(
                    text = stringResource(R.string.settings_page_chat_agent_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        item(key = "policy") {
            FocusReveal(index = 1) {
                PolicyCard(state = state, onAction = onAction)
            }
        }
        item(key = "agent") {
            FocusReveal(index = 2) {
                AgentCard(state = state, onAction = onAction)
            }
        }
        item(key = "subagent") {
            FocusReveal(index = 3) {
                SubagentCard(state = state, onAction = onAction)
            }
        }
        item(key = "judge") {
            AnimatedVisibility(
                visible = state.policy == CommandPolicy.AutoPilot,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                FocusReveal(index = 4) {
                    JudgeCard(state = state, onAction = onAction)
                }
            }
        }
    }
}

@Composable
private fun PolicyCard(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    FocusCardGroup(title = stringResource(R.string.settings_policy_title)) {
        formItem(
            headlineContent = { Text(stringResource(R.string.settings_policy_item)) },
            supportingContent = {
                Text(
                    stringResource(
                        when (state.policy) {
                            CommandPolicy.Yolo -> R.string.settings_policy_body_yolo
                            CommandPolicy.AutoPilot -> R.string.settings_policy_body_autopilot
                            CommandPolicy.Manual -> R.string.settings_policy_body_manual
                        },
                    ),
                )
            },
            leadingContent = {
                FocusCardGroupIcon(
                    icon = FocusIcons.Bolt,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            },
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FocusChip(
                    label = stringResource(R.string.settings_policy_yolo),
                    selected = state.policy == CommandPolicy.Yolo,
                    onClick = { onAction(SettingsAction.PolicyChanged(CommandPolicy.Yolo)) },
                )
                FocusChip(
                    label = stringResource(R.string.settings_policy_autopilot),
                    selected = state.policy == CommandPolicy.AutoPilot,
                    onClick = { onAction(SettingsAction.PolicyChanged(CommandPolicy.AutoPilot)) },
                )
                FocusChip(
                    label = stringResource(R.string.settings_policy_manual),
                    selected = state.policy == CommandPolicy.Manual,
                    onClick = { onAction(SettingsAction.PolicyChanged(CommandPolicy.Manual)) },
                )
            }
        }
    }
}

@Composable
private fun SubagentCard(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    FocusCardGroup(title = stringResource(R.string.settings_subagent_title)) {
        formItem(
            headlineContent = { Text(stringResource(R.string.settings_subagent_provider_item)) },
            supportingContent = { Text(stringResource(R.string.settings_subagent_provider_body)) },
            leadingContent = {
                FocusCardGroupIcon(
                    icon = FocusIcons.Tool,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            },
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FocusChip(
                    label = stringResource(R.string.settings_subagent_follow),
                    selected = state.subagentProviderId.isBlank(),
                    onClick = { onAction(SettingsAction.SubagentProviderChanged("")) },
                )
                state.providers.forEach { provider ->
                    FocusChip(
                        label = provider.name,
                        selected = provider.id == state.subagentProviderId,
                        onClick = { onAction(SettingsAction.SubagentProviderChanged(provider.id)) },
                    )
                }
            }
        }
        if (state.subagentProviderId.isNotBlank()) {
            item(
                headlineContent = {
                    Text(
                        text = state.subagentModel.ifBlank {
                            stringResource(R.string.settings_subagent_model_none)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = { Text(stringResource(R.string.settings_subagent_model_body)) },
                trailingContent = {
                    if (state.subagentModelsLoading) {
                        FocusLoadingBlob(size = 18.dp)
                    } else {
                        Icon(
                            imageVector = FocusIcons.Search,
                            contentDescription = stringResource(R.string.settings_subagent_model_item),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                onClick = { onAction(SettingsAction.OpenSubagentModelPicker) },
            )
        }
    }
}

@Composable
private fun JudgeCard(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    FocusCardGroup(title = stringResource(R.string.settings_judge_title)) {
        formItem(
            headlineContent = { Text(stringResource(R.string.settings_judge_engine_item)) },
            supportingContent = { Text(stringResource(R.string.settings_judge_engine_body)) },
            leadingContent = {
                FocusCardGroupIcon(
                    icon = FocusIcons.Tune,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            },
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FocusChip(
                    label = stringResource(R.string.settings_judge_kind_jev),
                    selected = state.judgeKind == JudgeKind.Jev,
                    onClick = { onAction(SettingsAction.JudgeKindChanged(JudgeKind.Jev)) },
                )
                FocusChip(
                    label = stringResource(R.string.settings_judge_kind_llm),
                    selected = state.judgeKind == JudgeKind.Llm,
                    onClick = { onAction(SettingsAction.JudgeKindChanged(JudgeKind.Llm)) },
                )
            }
        }
        formItem(
            headlineContent = { Text(stringResource(R.string.settings_judge_provider_item)) },
            supportingContent = {
                Text(
                    if (state.providers.isEmpty()) {
                        stringResource(R.string.settings_judge_provider_empty)
                    } else {
                        stringResource(R.string.settings_judge_provider_body)
                    },
                )
            },
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.providers.forEach { provider ->
                    FocusChip(
                        label = provider.name,
                        selected = provider.id == state.judgeProviderId,
                        onClick = { onAction(SettingsAction.JudgeProviderChanged(provider.id)) },
                    )
                }
            }
        }
        item(
            headlineContent = {
                Text(
                    text = state.judgeModel.ifBlank {
                        stringResource(R.string.settings_judge_model_none)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = { Text(stringResource(R.string.settings_judge_model_body)) },
            trailingContent = {
                if (state.judgeModelsLoading) {
                    FocusLoadingBlob(size = 18.dp)
                } else {
                    Icon(
                        imageVector = FocusIcons.Search,
                        contentDescription = stringResource(R.string.settings_judge_model_item),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            onClick = { onAction(SettingsAction.OpenJudgeModelPicker) },
        )
        formItem(
            headlineContent = { Text(stringResource(R.string.settings_judge_context_item)) },
            supportingContent = { Text(stringResource(R.string.settings_judge_context_body)) },
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(0, 1, 2, 4, 8).forEach { window ->
                    FocusChip(
                        label = if (window == 0) {
                            stringResource(R.string.settings_judge_context_none)
                        } else {
                            stringResource(R.string.settings_judge_context_value, window)
                        },
                        selected = state.judgeContextWindow == window,
                        onClick = { onAction(SettingsAction.JudgeContextChanged(window)) },
                    )
                }
            }
        }
        formItem(
            headlineContent = { Text(stringResource(R.string.settings_judge_risk_item)) },
            supportingContent = { Text(stringResource(R.string.settings_judge_risk_body)) },
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(1, 2, 3).forEach { risk ->
                    FocusChip(
                        label = stringResource(R.string.settings_judge_risk_value, risk),
                        selected = state.judgeMaxRisk == risk,
                        onClick = { onAction(SettingsAction.JudgeMaxRiskChanged(risk)) },
                    )
                }
            }
        }
        formItem(
            headlineContent = { Text(stringResource(R.string.settings_judge_timeout_item)) },
            supportingContent = { Text(stringResource(R.string.settings_judge_timeout_body)) },
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(5, 8, 15, 30).forEach { seconds ->
                    FocusChip(
                        label = stringResource(R.string.settings_judge_timeout_value, seconds),
                        selected = state.judgeTimeoutSeconds == seconds,
                        onClick = { onAction(SettingsAction.JudgeTimeoutChanged(seconds)) },
                    )
                }
            }
        }
    }
}
