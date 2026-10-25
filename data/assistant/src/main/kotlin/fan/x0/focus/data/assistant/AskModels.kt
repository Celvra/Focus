package fan.x0.focus.data.assistant

import kotlinx.serialization.Serializable

enum class AskKind { Single, Multi, Text, Confirm, Number }

@Serializable
data class AskOption(
    val label: String,
    val detail: String = "",
    val icon: String = "",
)

@Serializable
data class AskQuestion(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val kind: AskKind = AskKind.Single,
    val icon: String = "",
    val options: List<AskOption> = emptyList(),
    val allowOther: Boolean = false,
    val placeholder: String = "",
)

data class AskRequest(
    val id: String,
    val intro: String = "",
    val questions: List<AskQuestion> = emptyList(),
)

@Serializable
data class AskAnswer(
    val questionId: String,
    val choices: List<String> = emptyList(),
    val text: String = "",
    val number: Double? = null,
    val confirmed: Boolean? = null,
)

data class AskReply(
    val id: String,
    val answers: List<AskAnswer> = emptyList(),
    val skipped: Boolean = false,
) {
    fun answerFor(questionId: String): AskAnswer? = answers.firstOrNull { it.questionId == questionId }
}

fun askKindOf(value: String?): AskKind = when (value?.trim()?.lowercase()) {
    "multi", "multiple", "many", "checkbox", "checkboxes" -> AskKind.Multi
    "text", "free", "free_text", "input", "open" -> AskKind.Text
    "confirm", "boolean", "bool", "yes_no", "switch" -> AskKind.Confirm
    "number", "numeric", "quantity", "amount" -> AskKind.Number
    else -> AskKind.Single
}
