package fan.x0.focus.data.device.display

import android.content.AttributionSource
import android.content.Context
import android.content.ContextWrapper
import android.os.Process

internal class ShellContextV31(base: Context) : ContextWrapper(base) {
    override fun getApplicationContext(): Context = this

    override fun getOpPackageName(): String = "com.android.shell"

    override fun getAttributionSource(): AttributionSource =
        AttributionSource.Builder(Process.myUid())
            .setPackageName("com.android.shell")
            .build()
}
