package fan.x0.focus.app

import fan.x0.focus.data.assistant.ToolProviderGroup
import fan.x0.focus.data.device.DeviceToolProvider
import fan.x0.focus.data.plugin.PluginToolProvider
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Single
fun toolProviderGroup(
    plugin: PluginToolProvider,
    device: DeviceToolProvider,
): ToolProviderGroup = ToolProviderGroup(listOf(plugin, device))

@Module
@ComponentScan("fan.x0.focus.app")
class AppModule
