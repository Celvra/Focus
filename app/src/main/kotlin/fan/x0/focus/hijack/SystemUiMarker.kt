package fan.x0.focus.hijack

import android.content.Context

internal object SystemUiMarker {
    private var marked = false

    @Synchronized
    fun mark(context: Context?) {
        if (marked || context == null) return
        marked = true
        HijackMarker.markSystemUi(context)
    }
}
