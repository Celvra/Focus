package fan.x0.focus.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusCardGroup
import fan.x0.focus.core.designsystem.components.FocusCardGroupIcon
import fan.x0.focus.core.designsystem.components.FocusCategoryIcons
import fan.x0.focus.core.designsystem.components.FocusChip
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.data.assistant.AskKind

@Composable
internal fun AskQuestionInput(
    askId: String,
    question: AskQuestionUi,
    draft: AskDraft,
    onAction: (ChatAction) -> Unit,
) {
    when (question.kind) {
        AskKind.Single, AskKind.Multi -> {
            if (question.options.isNotEmpty()) {
                AskOptionGroup(
                    askId = askId,
                    question = question,
                    draft = draft,
                    onAction = onAction,
                )
            }
            if (question.allowOther) {
                AskOtherField(askId = askId, question = question, draft = draft, onAction = onAction)
            }
        }
        AskKind.Confirm -> AskConfirmChips(
            askId = askId,
            question = question,
            draft = draft,
            onAction = onAction,
        )
        AskKind.Text -> AskTextField(
            askId = askId,
            question = question,
            draft = draft,
            hint = stringResource(R.string.chat_ask_text_hint),
            keyboard = KeyboardType.Text,
            singleLine = false,
            onAction = onAction,
        )
        AskKind.Number -> AskTextField(
            askId = askId,
            question = question,
            draft = draft,
            hint = stringResource(R.string.chat_ask_number_hint),
            keyboard = KeyboardType.Decimal,
            singleLine = true,
            onAction = onAction,
        )
    }
}

@Composable
private fun AskOptionGroup(
    askId: String,
    question: AskQuestionUi,
    draft: AskDraft,
    onAction: (ChatAction) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    FocusCardGroup {
        question.options.forEach { option ->
            val selected = option.label in draft.selections
            item(
                headlineContent = { Text(option.label) },
                onClick = { onAction(ChatAction.AskSelect(askId, question.id, option.label)) },
                supportingContent = option.detail.takeIf { it.isNotBlank() }?.let { detail ->
                    { Text(detail) }
                },
                leadingContent = option.icon.takeIf { it.isNotBlank() }?.let { icon ->
                    { FocusCardGroupIcon(FocusCategoryIcons.resolve(icon)) }
                },
                trailingContent = when {
                    question.kind == AskKind.Multi -> {
                        {
                            Checkbox(
                                checked = selected,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = scheme.primary,
                                    uncheckedColor = scheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                    selected -> {
                        {
                            Icon(
                                imageVector = FocusIcons.Check,
                                contentDescription = null,
                                tint = scheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    else -> null
                },
                selected = selected,
                role = if (question.kind == AskKind.Multi) Role.Checkbox else Role.RadioButton,
            )
        }
    }
}

@Composable
private fun AskConfirmChips(
    askId: String,
    question: AskQuestionUi,
    draft: AskDraft,
    onAction: (ChatAction) -> Unit,
) {
    val yes = question.options.getOrNull(0)?.label ?: stringResource(R.string.chat_ask_yes)
    val no = question.options.getOrNull(1)?.label ?: stringResource(R.string.chat_ask_no)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FocusChip(
            label = yes,
            selected = draft.confirmed == true,
            onClick = { onAction(ChatAction.AskConfirm(askId, question.id, true)) },
        )
        FocusChip(
            label = no,
            selected = draft.confirmed == false,
            onClick = { onAction(ChatAction.AskConfirm(askId, question.id, false)) },
        )
    }
}

@Composable
private fun AskTextField(
    askId: String,
    question: AskQuestionUi,
    draft: AskDraft,
    hint: String,
    keyboard: KeyboardType,
    singleLine: Boolean,
    onAction: (ChatAction) -> Unit,
) {
    OutlinedTextField(
        value = draft.text,
        onValueChange = {
            onAction(ChatAction.AskEdit(askId, question.id, AskField.Answer, it))
        },
        placeholder = { Text(question.placeholder.ifBlank { hint }) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 4,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AskOtherField(
    askId: String,
    question: AskQuestionUi,
    draft: AskDraft,
    onAction: (ChatAction) -> Unit,
) {
    OutlinedTextField(
        value = draft.other,
        onValueChange = {
            onAction(ChatAction.AskEdit(askId, question.id, AskField.Other, it))
        },
        placeholder = { Text(stringResource(R.string.chat_ask_other)) },
        maxLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
}
