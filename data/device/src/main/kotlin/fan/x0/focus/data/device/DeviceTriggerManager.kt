package fan.x0.focus.data.device

import android.content.Context
import fan.x0.focus.data.assistant.AgentEvent
import fan.x0.focus.data.assistant.AgentEventBus
import fan.x0.focus.data.assistant.AssistantTriggerBus
import fan.x0.focus.data.assistant.AssistantTriggerEvent
import fan.x0.focus.data.device.system.DeviceTriggerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap

@Single
class DeviceTriggerManager(
    @Provided private val context: Context,
    private val store: DeviceStore,
    private val shell: DeviceShell,
    private val notifier: DeviceNotifier,
    private val triggerBus: AssistantTriggerBus,
    private val agentEvents: AgentEventBus,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lastFired = ConcurrentHashMap<String, Long>()
    private val _triggers = MutableStateFlow<List<DeviceTrigger>>(emptyList())
    val triggers: StateFlow<List<DeviceTrigger>> = _triggers.asStateFlow()

    init {
        scope.launch {
            var serviceOn = false
            var firstLoad = true
            store.triggers.collect { list ->
                _triggers.value = list
                list.forEach { trigger -> lastFired.putIfAbsent(trigger.id, trigger.lastFiredAt) }
                val active = list.any { it.enabled }
                if (active && !serviceOn) {
                    DeviceTriggerService.start(context)
                    serviceOn = true
                } else if (!active && (serviceOn || firstLoad)) {
                    DeviceTriggerService.stop(context)
                    serviceOn = false
                }
                firstLoad = false
            }
        }
        scope.launch {
            var lastSeq = DeviceEvents.recent(1).lastOrNull()?.seq ?: 0
            DeviceEvents.events.collect { events ->
                events.forEach eventLoop@ { event ->
                    if (event.seq <= lastSeq) return@eventLoop
                    lastSeq = event.seq
                    _triggers.value.forEach triggerLoop@ { trigger ->
                        if (!trigger.enabled || !matches(trigger, event)) return@triggerLoop
                        val now = System.currentTimeMillis()
                        val last = lastFired[trigger.id] ?: trigger.lastFiredAt
                        if (now - last < trigger.cooldownSec * 1_000L) return@triggerLoop
                        lastFired[trigger.id] = now
                        scope.launch {
                            store.touchTrigger(trigger.id)
                            when (trigger.action) {
                                "notify" -> notifier.showTrigger(
                                    trigger.name,
                                    trigger.command,
                                    true,
                                    "notification",
                                )
                                "chat" -> {
                                    if (trigger.conversationId.isNotBlank()) {
                                        triggerBus.emit(
                                            AssistantTriggerEvent(
                                                conversationId = trigger.conversationId,
                                                label = trigger.name,
                                                prompt = trigger.command,
                                            ),
                                        )
                                        agentEvents.publish(
                                            AgentEvent.deviceTrigger(
                                                label = trigger.name,
                                                prompt = trigger.command,
                                                conversationId = trigger.conversationId,
                                            ),
                                        )
                                    }
                                    notifier.showTrigger(trigger.name, trigger.command, true, "sent to chat")
                                }
                                else -> {
                                    val result = shell.run(trigger.command, 120_000)
                                    notifier.showTrigger(trigger.name, trigger.command, result.ok, result.output)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun list(): List<DeviceTrigger> = _triggers.value

    suspend fun add(
        name: String,
        packageFilter: String,
        textFilter: String,
        typeFilter: String,
        action: String,
        conversationId: String,
        command: String,
        cooldownSec: Int,
    ): DeviceTrigger = store.addTrigger(
        name,
        packageFilter,
        textFilter,
        typeFilter,
        action,
        conversationId,
        command,
        cooldownSec,
    )

    suspend fun remove(id: String) {
        store.removeTrigger(id)
        lastFired.remove(id)
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        store.setTriggerEnabled(id, enabled)
    }

    private fun matches(trigger: DeviceTrigger, event: DeviceEventInfo): Boolean {
        if (trigger.typeFilter.isNotBlank() &&
            !event.type.contains(trigger.typeFilter, ignoreCase = true)
        ) {
            return false
        }
        if (trigger.packageFilter.isNotBlank() &&
            !event.packageName.contains(trigger.packageFilter, ignoreCase = true)
        ) {
            return false
        }
        if (trigger.textFilter.isNotBlank() &&
            !event.text.contains(trigger.textFilter, ignoreCase = true) &&
            !event.className.contains(trigger.textFilter, ignoreCase = true)
        ) {
            return false
        }
        return true
    }
}
