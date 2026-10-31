package fan.x0.focus.feature.chat

import fan.x0.focus.data.assistant.AgentMode

internal fun AgentMode.labelRes(): Int = when (this) {
    AgentMode.Build -> R.string.chat_mode_build
    AgentMode.Plan -> R.string.chat_mode_plan
}

internal fun AgentMode.descriptionRes(): Int = when (this) {
    AgentMode.Build -> R.string.chat_mode_build_desc
    AgentMode.Plan -> R.string.chat_mode_plan_desc
}
