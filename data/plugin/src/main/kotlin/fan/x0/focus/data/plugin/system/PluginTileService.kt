package fan.x0.focus.data.plugin.system

import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import fan.x0.focus.data.plugin.PluginRegistry
import fan.x0.focus.plugin.PluginTileContent
import org.koin.mp.KoinPlatform

class PluginTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        current = this
        refresh()
    }

    override fun onStopListening() {
        if (current === this) current = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val registry = runCatching { KoinPlatform.getKoin().get<PluginRegistry>() }.getOrNull() ?: return
        val handler = registry.tileHandler() ?: return
        runCatching { handler.second.onClick() }
            .onFailure { registry.log().write(handler.first.manifest.id, "W", "Tile click failed", it) }
        refresh()
    }

    fun refresh() {
        val registry = runCatching { KoinPlatform.getKoin().get<PluginRegistry>() }.getOrNull() ?: return
        val handler = registry.tileHandler() ?: return
        runCatching { render(handler.second.content()) }
            .onFailure { registry.log().write(handler.first.manifest.id, "W", "Tile render failed", it) }
    }

    fun render(content: PluginTileContent) {
        val tile = qsTile ?: return
        tile.label = content.label
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = content.subtitle
        }
        tile.state = if (content.active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        content.icon?.let { tile.icon = Icon.createWithBitmap(it) }
        tile.updateTile()
    }

    companion object {
        @Volatile
        var current: PluginTileService? = null
            private set

        fun requestUpdate() {
            val service = current ?: return
            runCatching {
                TileService.requestListeningState(
                    service,
                    ComponentName(service, PluginTileService::class.java),
                )
            }
            service.refresh()
        }
    }
}
