package fan.x0.focus.data.assistant

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.Single

@Single
class MainSessionStore {
    private val _id = MutableStateFlow(DEFAULT_MAIN_ID)
    val id: StateFlow<String> = _id.asStateFlow()

    fun set(id: String) {
        if (id.isNotBlank()) _id.value = id
    }

    companion object {
        const val DEFAULT_MAIN_ID = "main"
    }
}
