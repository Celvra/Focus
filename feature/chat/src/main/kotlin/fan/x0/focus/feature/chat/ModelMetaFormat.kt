package fan.x0.focus.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusPickerCapability
import fan.x0.focus.core.designsystem.components.FocusPickerDetail
import fan.x0.focus.core.network.openai.ModelMeta
import java.util.Locale

data class ModelMetaLabels(
    val context: String,
    val knowledge: String,
    val vision: String,
    val tools: String,
    val reasoning: String,
)

@Composable
internal fun modelMetaLabels(): ModelMetaLabels = ModelMetaLabels(
    context = stringResource(R.string.chat_model_context),
    knowledge = stringResource(R.string.chat_model_knowledge),
    vision = stringResource(R.string.chat_model_vision),
    tools = stringResource(R.string.chat_model_tools),
    reasoning = stringResource(R.string.chat_model_reasoning),
)

internal fun ModelMeta.toPickerDetail(labels: ModelMetaLabels): FocusPickerDetail? {
    val meta = buildList {
        contextWindow?.takeIf { it > 0 }?.let { add(labels.context.format(contextLabel(it))) }
        if (inputPrice != null || outputPrice != null) {
            add("${money(inputPrice)} / ${money(outputPrice)} /M")
        }
        knowledgeCutoff?.let { add(labels.knowledge.format(it)) }
    }.joinToString(" · ")

    val capabilities = buildList {
        if (supportsImage == true) add(FocusPickerCapability(FocusIcons.Photo, labels.vision))
        if (supportsTools == true) add(FocusPickerCapability(FocusIcons.Tool, labels.tools))
        if (supportsReasoning == true) add(FocusPickerCapability(FocusIcons.Idea, labels.reasoning))
    }

    val summary = description?.trim()?.ifBlank { null }
    if (meta.isBlank() && capabilities.isEmpty() && summary == null) return null
    return FocusPickerDetail(
        meta = meta.ifBlank { null },
        capabilities = capabilities,
        description = summary,
    )
}

internal fun contextLabel(tokens: Int): String = when {
    tokens >= 1_000_000 -> "${trimDecimal(Math.round(tokens / 100_000.0) / 10.0)}M"
    tokens >= 1_000 -> "${Math.round(tokens / 1_000.0)}K"
    else -> tokens.toString()
}

private fun money(value: Double?): String = value?.let { "$${trimDecimal(it)}" } ?: "-"

private fun trimDecimal(value: Double): String {
    val text = String.format(Locale.US, "%.4f", value)
    return text.trimEnd('0').trimEnd('.')
}
