package fan.x0.focus.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import fan.x0.focus.core.notify.AppPresence

class AppPresenceCallbacks(private val presence: AppPresence) : Application.ActivityLifecycleCallbacks {

    private var started = 0

    override fun onActivityStarted(activity: Activity) {
        started++
        presence.setForeground(true)
    }

    override fun onActivityStopped(activity: Activity) {
        started = (started - 1).coerceAtLeast(0)
        if (started == 0) presence.setForeground(false)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
