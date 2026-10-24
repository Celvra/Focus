package fan.x0.focus.app

import android.content.Context
import android.content.Intent
import fan.x0.focus.data.assistant.ChatTurnKeepAlive
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

@Single
class ForegroundTurnKeepAlive(@Provided private val context: Context) : ChatTurnKeepAlive {

    override fun start() {
        runCatching {
            context.startForegroundService(Intent(context, ChatTurnService::class.java))
        }
    }
}
