package fan.x0.focus.data.im

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ImChannelFactory {
    val descriptor: ImChannelDescriptor
    fun create(account: ImAccount): ImChannel
}

interface ImChannel {
    val state: StateFlow<ImConnectionState>
    val events: Flow<ImEvent>
    suspend fun connect()
    suspend fun send(target: ImSendTarget, message: ImOutbound): ImSendResult
    suspend fun disconnect()
}

interface ImLoginChannel {
    suspend fun submitLoginCode(code: String): Boolean
}

class ImChannelRegistry(private val factories: List<ImChannelFactory>) {
    val descriptors: List<ImChannelDescriptor> = factories.map { it.descriptor }

    fun descriptor(id: String): ImChannelDescriptor? =
        descriptors.firstOrNull { it.id == id }

    fun create(account: ImAccount): ImChannel? =
        factories.firstOrNull { it.descriptor.id == account.channel }?.create(account)
}
