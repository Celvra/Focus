package fan.x0.focus

import android.app.Activity
import android.content.Intent
import fan.x0.focus.feature.overlay.FocusOverlay
import fan.x0.focus.feature.overlay.OverlayShortcutActivity

class AssistantEntryActivity : Activity() {
    private var handled = false

    override fun onResume() {
        super.onResume()
        if (handled) return
        handled = true
        if (FocusOverlay.canDraw(this) && FocusOverlay.show(this)) {
            finish()
            return
        }
        startActivity(
            Intent(this, OverlayShortcutActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }
}
