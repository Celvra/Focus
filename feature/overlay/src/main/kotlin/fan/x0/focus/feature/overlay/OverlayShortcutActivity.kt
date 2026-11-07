package fan.x0.focus.feature.overlay

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

class OverlayShortcutActivity : Activity() {

    private var awaitingPermission = false
    private var pausedForPermission = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(OverlayLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (openOverlay()) return
        awaitingPermission = true
        Toast.makeText(this, R.string.overlay_permission_needed, Toast.LENGTH_LONG).show()
        if (!FocusOverlay.openPermission(this)) {
            awaitingPermission = false
            finish()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openOverlay()
    }

    override fun onPause() {
        super.onPause()
        if (awaitingPermission) pausedForPermission = true
    }

    override fun onResume() {
        super.onResume()
        if (!awaitingPermission || !pausedForPermission) return
        awaitingPermission = false
        pausedForPermission = false
        if (FocusOverlay.canDraw(this)) {
            if (!openOverlay()) {
                Toast.makeText(this, R.string.overlay_start_failed, Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            Toast.makeText(this, R.string.overlay_permission_needed, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun openOverlay(): Boolean {
        if (!FocusOverlay.canDraw(this)) return false
        if (!FocusOverlay.show(this)) {
            Toast.makeText(this, R.string.overlay_start_failed, Toast.LENGTH_SHORT).show()
        }
        finish()
        return true
    }
}
