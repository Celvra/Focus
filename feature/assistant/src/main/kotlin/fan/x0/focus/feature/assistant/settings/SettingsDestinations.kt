package fan.x0.focus.feature.assistant.settings

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
enum class SettingsDestination : NavKey {
    Home,
    Account,
    ChatAgent,
    Models,
    Automation,
    Tools,
    Plugins,
    WebSearch,
    Mcp,
    Skills,
    WorkspaceIndex,
    WorkspaceTrust,
    ChatDetail,
    Overlay,
    Voice,
    Im,
    Appearance,
    Notifications,
    Data,
}
