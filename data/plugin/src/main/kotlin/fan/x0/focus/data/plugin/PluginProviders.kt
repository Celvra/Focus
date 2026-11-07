package fan.x0.focus.data.plugin

import fan.x0.focus.data.assistant.AssistantEventSink
import fan.x0.focus.data.assistant.ExternalPromptProvider
import fan.x0.focus.data.assistant.PluginSkillContribution
import fan.x0.focus.data.assistant.PluginSkillProvider
import kotlinx.serialization.json.JsonObject
import org.koin.core.annotation.Single

@Single
class PluginPromptProvider(private val registry: PluginRegistry) : ExternalPromptProvider {
    override suspend fun sections(): List<String> = registry.promptSections()
}

@Single
class PluginSkillProviderImpl(private val registry: PluginRegistry) : PluginSkillProvider {
    override suspend fun skills(): List<PluginSkillContribution> = registry.skills()
}

@Single
class PluginEventSinkImpl(private val registry: PluginRegistry) : AssistantEventSink {
    override fun emit(type: String, conversationId: String, payload: JsonObject) {
        registry.dispatch(type, conversationId, payload)
    }
}
