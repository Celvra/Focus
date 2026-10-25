package fan.x0.focus.feature.chat

import fan.x0.focus.data.assistant.AskAnswer
import fan.x0.focus.data.assistant.AskKind
import fan.x0.focus.data.assistant.AskQuestion
import fan.x0.focus.data.assistant.ChatTurnAsk
import fan.x0.focus.data.assistant.ChatTurnAskPhase

internal fun ChatTurnAsk.toUi(): AskCardUi = AskCardUi(
    id = id,
    intro = intro,
    questions = questions.map { it.toUi() },
    phase = phase.toUi(),
    answers = answers.map { it.toUi() },
)

internal fun ChatTurnAskPhase.toUi(): AskPhase = when (this) {
    ChatTurnAskPhase.Waiting -> AskPhase.Waiting
    ChatTurnAskPhase.Answered -> AskPhase.Answered
    ChatTurnAskPhase.Skipped -> AskPhase.Skipped
}

internal fun AskQuestion.toUi(): AskQuestionUi = AskQuestionUi(
    id = id,
    title = title,
    subtitle = subtitle,
    kind = kind,
    icon = icon,
    options = options.map { AskOptionUi(it.label, it.detail, it.icon) },
    allowOther = allowOther,
    placeholder = placeholder,
)

internal fun AskAnswer.toUi(): AskAnswerUi = AskAnswerUi(
    questionId = questionId,
    choices = choices,
    text = text,
    number = number,
    confirmed = confirmed,
)

internal fun AskCardUi.toAnswers(drafts: Map<String, AskDraft>): List<AskAnswer> = questions.map { question ->
    val draft = drafts[question.id] ?: AskDraft()
    when (question.kind) {
        AskKind.Single -> AskAnswer(
            questionId = question.id,
            choices = draft.selections.take(1),
            text = draft.other.trim(),
        )
        AskKind.Multi -> AskAnswer(
            questionId = question.id,
            choices = draft.selections,
            text = draft.other.trim(),
        )
        AskKind.Text -> AskAnswer(questionId = question.id, text = draft.text.trim())
        AskKind.Number -> AskAnswer(
            questionId = question.id,
            number = draft.text.trim().toDoubleOrNull(),
        )
        AskKind.Confirm -> AskAnswer(questionId = question.id, confirmed = draft.confirmed)
    }
}

internal fun AskCardUi.isReady(drafts: Map<String, AskDraft>): Boolean = questions.all { question ->
    val draft = drafts[question.id] ?: AskDraft()
    when (question.kind) {
        AskKind.Single -> draft.selections.size == 1 || draft.other.isNotBlank()
        AskKind.Multi -> draft.selections.isNotEmpty() || draft.other.isNotBlank()
        AskKind.Text -> draft.text.isNotBlank()
        AskKind.Number -> draft.text.trim().toDoubleOrNull() != null
        AskKind.Confirm -> draft.confirmed != null
    }
}
