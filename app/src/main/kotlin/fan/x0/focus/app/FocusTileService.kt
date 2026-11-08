package fan.x0.focus.app

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import fan.x0.focus.AssistantEntryActivity
import fan.x0.focus.R
import fan.x0.focus.core.notify.R as NotifyR

class FocusTileService : TileService() {

    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(R.string.tile_focus_label)
            icon = Icon.createWithResource(this@FocusTileService, NotifyR.drawable.ic_notification_mascot)
            updateTile()
        }
    }

    override fun onClick() {
        val intent = Intent(this, AssistantEntryActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
