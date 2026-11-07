package fan.x0.focus.plugin

import fan.x0.focus.core.network.openai.ChatProtocol
import fan.x0.focus.core.notify.MessageNotifier
import fan.x0.focus.data.assistant.AssistantConfig
import fan.x0.focus.data.assistant.AssistantSettings
import fan.x0.focus.data.assistant.ChatStore
import fan.x0.focus.data.assistant.GoalStore
import fan.x0.focus.data.assistant.ReminderStore
import fan.x0.focus.data.assistant.TodayStore
import fan.x0.focus.data.assistant.WorkspaceStore
import fan.x0.focus.data.bridge.BridgeRepository
import fan.x0.focus.data.sources.CalendarRepository
import fan.x0.focus.data.sources.FeedRepository
import fan.x0.focus.data.sources.FileRepository
import io.ktor.client.HttpClient

interface PluginSecretStore {
    suspend fun get(pluginId: String, key: String): String?
    suspend fun put(pluginId: String, key: String, value: String)
    suspend fun remove(pluginId: String, key: String)
}

interface FocusHost {
    val calendar: CalendarRepository
    val feeds: FeedRepository
    val files: FileRepository
    val goals: GoalStore
    val today: TodayStore
    val reminders: ReminderStore
    val chats: ChatStore
    val workspace: WorkspaceStore
    val settings: AssistantSettings
    val bridge: BridgeRepository
    val http: HttpClient
    val notifier: MessageNotifier
    val secrets: PluginSecretStore
    val system: PluginSystemHost

    suspend fun config(): AssistantConfig
    suspend fun setDefaultModel(providerId: String, model: String, protocol: ChatProtocol)
    suspend fun openConversation(conversationId: String)
    fun openPluginPage(pluginId: String, pageId: String, args: String? = null)
}
