package fan.x0.focus.data.assistant

data class JudgeAction(
    val kind: String,
    val title: String,
    val detail: String,
)

data class JudgeVerdict(
    val allowed: Boolean,
    val risk: Int,
    val reason: String,
)

sealed interface GateDecision {
    val reason: String

    data class Allowed(override val reason: String) : GateDecision
    data class Refused(override val reason: String) : GateDecision
    data class NeedsApproval(override val reason: String) : GateDecision
}

interface PolicyJudge {
    suspend fun judge(action: JudgeAction, history: List<String>): JudgeVerdict
}
