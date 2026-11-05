package fan.x0.focus.app

import android.content.Context
import fan.x0.focus.data.im.ImAccountStore
import fan.x0.focus.data.im.ImServiceBridge
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

@Single
class AndroidImServiceBridge(
    @Provided context: Context,
    private val accounts: ImAccountStore,
) : ImServiceBridge {
    private val context = context

    override suspend fun sync() {
        ImGatewayService.sync(context, accounts)
    }
}
