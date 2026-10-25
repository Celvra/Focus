package fan.x0.focus.data.assistant

fun AskRequest.answerContent(reply: AskReply): String {
    if (reply.skipped) return "The user skipped these questions."
    return questions.mapIndexed { index, question ->
        val answer = reply.answerFor(question.id)
        "${index + 1}. ${question.title} -> ${answer.summaryLine()}"
    }.joinToString("\n")
}

fun AskRequest.questionLabels(): String =
    questions.joinToString("\n") { it.title.take(80) }

fun AskAnswer?.summaryLine(): String {
    if (this == null) return "no answer"
    confirmed?.let { return if (it) "yes" else "no" }
    number?.let { return trimNumber(it) }
    val parts = choices.filter { it.isNotBlank() } + text.trim().takeIf { it.isNotBlank() }.orEmpty()
    return parts.joinToString(", ").ifBlank { "no answer" }
}

fun trimNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
