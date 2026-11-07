package fan.x0.focus.data.device

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single

@Single
class AutomationGate {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val _concealed = MutableStateFlow(false)
    private var users = 0
    private var reveal: Job? = null

    val concealed: StateFlow<Boolean> = _concealed.asStateFlow()

    suspend fun <T> conceal(block: suspend () -> T): T {
        val hide = mutex.withLock {
            reveal?.cancel()
            reveal = null
            users += 1
            if (users == 1) {
                val already = _concealed.value
                _concealed.value = true
                !already
            } else {
                false
            }
        }
        if (hide) delay(HIDE_DELAY_MS)
        try {
            return block()
        } finally {
            val last = mutex.withLock {
                users -= 1
                users == 0
            }
            if (last) {
                reveal = scope.launch {
                    delay(REVEAL_DELAY_MS)
                    _concealed.value = false
                }
            }
        }
    }

    private companion object {
        const val HIDE_DELAY_MS = 180L
        const val REVEAL_DELAY_MS = 420L
    }
}
