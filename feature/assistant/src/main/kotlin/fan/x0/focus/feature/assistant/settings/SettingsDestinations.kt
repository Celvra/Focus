package fan.x0.focus.feature.assistant.settings

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
enum class SettingsDestination : NavKey {
    Home,
    Account,
    ChatAgent,
    Tools,
    WebSearch,
    Mcp,
    WorkspaceIndex,
    ChatDetail,
    Appearance,
    Notifications,
    Data,
}
