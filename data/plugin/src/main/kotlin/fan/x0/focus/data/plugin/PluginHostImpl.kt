package fan.x0.focus.data.plugin

import fan.x0.focus.core.network.openai.ChatProtocol
import fan.x0.focus.core.notify.MessageNotifier
import fan.x0.focus.data.assistant.AssistantConfig
import fan.x0.focus.data.assistant.AssistantSettings
import fan.x0.focus.data.assistant.ChatStore
import fan.x0.focus.data.assistant.ChatSwitcher
import fan.x0.focus.data.assistant.GoalStore
import fan.x0.focus.data.assistant.ReminderStore
import fan.x0.focus.data.assistant.SecretStore
import fan.x0.focus.data.assistant.TodayStore
import fan.x0.focus.data.assistant.WorkspaceStore
import fan.x0.focus.data.bridge.BridgeRepository
import fan.x0.focus.data.sources.CalendarRepository
import fan.x0.focus.data.sources.FeedRepository
import fan.x0.focus.data.sources.FileRepository
import fan.x0.focus.plugin.FocusHost
import fan.x0.focus.plugin.PluginSecretStore
import fan.x0.focus.plugin.PluginSystemHost
import io.ktor.client.HttpClient
import org.koin.core.annotation.Single

@Single
class PluginSecretStoreImpl(private val secrets: SecretStore) : PluginSecretStore {
    override suspend fun get(pluginId: String, key: String): String? =
        secrets.secret(secretKey(pluginId, key))

    override suspend fun put(pluginId: String, key: String, value: String) {
        secrets.setSecret(secretKey(pluginId, key), value)
    }

    override suspend fun remove(pluginId: String, key: String) {
        secrets.clearSecret(secretKey(pluginId, key))
    }

    private fun secretKey(pluginId: String, key: String): String = "plugin:$pluginId:$key"
}

@Single
class PluginHostImpl(
    override val calendar: CalendarRepository,
    override val feeds: FeedRepository,
    override val files: FileRepository,
    override val goals: GoalStore,
    override val today: TodayStore,
    override val reminders: ReminderStore,
    override val chats: ChatStore,
    override val workspace: WorkspaceStore,
    override val settings: AssistantSettings,
    override val bridge: BridgeRepository,
    override val http: HttpClient,
    override val notifier: MessageNotifier,
    override val secrets: PluginSecretStore,
    override val system: PluginSystemHost,
    private val switcher: ChatSwitcher,
) : FocusHost {
    override suspend fun config(): AssistantConfig = settings.configOnce()

    override suspend fun setDefaultModel(providerId: String, model: String, protocol: ChatProtocol) {
        val config = settings.configOnce()
        val provider = config.providers.firstOrNull { it.id == providerId } ?: return
        settings.setActiveProvider(provider.id)
        settings.setModel(provider.baseUrl, model, protocol)
    }

    override suspend fun openConversation(conversationId: String) {
        switcher.open(conversationId)
    }

    override fun openPluginPage(pluginId: String, pageId: String, args: String?) {
        PluginNavigation.open(pluginId, pageId, args)
    }
}
