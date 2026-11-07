package fan.x0.focus.data.device

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong

object DeviceEvents {
    private const val MAX_EVENTS = 200
    private val sequence = AtomicLong(0)
    private val _events = MutableStateFlow<List<DeviceEventInfo>>(emptyList())
    val events: StateFlow<List<DeviceEventInfo>> = _events.asStateFlow()

    fun record(
        type: String,
        packageName: String = "",
        className: String = "",
        text: String = "",
    ) {
        val event = DeviceEventInfo(
            seq = sequence.incrementAndGet(),
            at = System.currentTimeMillis(),
            type = type,
            packageName = packageName,
            className = className,
            text = text.take(300),
        )
        _events.value = (_events.value + event).takeLast(MAX_EVENTS)
    }

    fun recent(limit: Int = 50): List<DeviceEventInfo> =
        _events.value.takeLast(limit.coerceIn(1, MAX_EVENTS))

    suspend fun waitFor(
        packageName: String? = null,
        text: String? = null,
        timeoutMs: Long = 30_000,
    ): DeviceEventInfo? {
        val start = sequence.get()
        val pkg = packageName?.takeIf { it.isNotBlank() }
        val value = text?.takeIf { it.isNotBlank() }
        return withTimeoutOrNull(timeoutMs.coerceIn(1_000, 600_000)) {
            _events.first { events ->
                events.any { event ->
                    event.seq > start &&
                        (pkg == null || event.packageName.contains(pkg, ignoreCase = true)) &&
                        (value == null || event.text.contains(value, ignoreCase = true) ||
                            event.className.contains(value, ignoreCase = true))
                }
            }.last { event ->
                event.seq > start &&
                    (pkg == null || event.packageName.contains(pkg, ignoreCase = true)) &&
                    (value == null || event.text.contains(value, ignoreCase = true) ||
                        event.className.contains(value, ignoreCase = true))
            }
        }
    }
}
