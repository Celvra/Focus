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

class ImChannelRegistry(factories: List<ImChannelFactory>) {
    private val base = factories.toList()
    private val extra = java.util.concurrent.CopyOnWriteArrayList<ImChannelFactory>()

    val descriptors: List<ImChannelDescriptor>
        get() = (base + extra).map { it.descriptor }

    fun register(factory: ImChannelFactory) {
        if ((base + extra).none { it.descriptor.id == factory.descriptor.id }) {
            extra += factory
        }
    }

    fun unregister(factory: ImChannelFactory) {
        extra.removeAll { it.descriptor.id == factory.descriptor.id }
    }

    fun descriptor(id: String): ImChannelDescriptor? =
        descriptors.firstOrNull { it.id == id }

    fun create(account: ImAccount): ImChannel? =
        (base + extra).firstOrNull { it.descriptor.id == account.channel }?.create(account)
}
