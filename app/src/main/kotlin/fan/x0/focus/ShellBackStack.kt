package fan.x0.focus

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.snapshots.SnapshotStateList

enum class FocusRoute {
    Chat,
    Today,
    Goals,
    Library,
    Assistant,
    Settings,
    History,
}

// One level under chat: drawer destinations replace each other and back returns to chat.
class ShellBackStack(val entries: SnapshotStateList<FocusRoute>) {
    constructor(start: FocusRoute) : this(mutableStateListOf(start))

    val current: FocusRoute get() = entries.last()

    fun open(route: FocusRoute) {
        if (route == current) return
        entries.clear()
        entries.add(FocusRoute.Chat)
        if (route != FocusRoute.Chat) entries.add(route)
    }

    fun back(): Boolean {
        if (entries.size <= 1) return false
        entries.removeAt(entries.lastIndex)
        return true
    }

    companion object {
        val Saver: Saver<ShellBackStack, List<String>> = Saver(
            save = { ArrayList(it.entries.map(FocusRoute::name)) },
            restore = { names ->
                val routes = names.mapNotNull { name ->
                    runCatching { FocusRoute.valueOf(name) }.getOrNull()
                }
                ShellBackStack(mutableStateListOf(*(routes.ifEmpty { listOf(FocusRoute.Chat) }).toTypedArray()))
            },
        )
    }
}
