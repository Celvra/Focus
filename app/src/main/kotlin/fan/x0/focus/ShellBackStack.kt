package fan.x0.focus

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
sealed interface FocusRoute {
    @Serializable
    data object Chat : FocusRoute

    @Serializable
    data object Today : FocusRoute

    @Serializable
    data object Crew : FocusRoute

    @Serializable
    data object Goals : FocusRoute

    @Serializable
    data object Library : FocusRoute

    @Serializable
    data object Bridge : FocusRoute

    @Serializable
    data object Device : FocusRoute

    @Serializable
    data object Assistant : FocusRoute

    @Serializable
    data object Settings : FocusRoute

    @Serializable
    data object History : FocusRoute

    @Serializable
    data class PluginPage(
        val pluginId: String,
        val pageId: String,
        val args: String? = null,
    ) : FocusRoute
}

val FocusRoute.contentKey: String
    get() = when (this) {
        FocusRoute.Chat -> "chat"
        FocusRoute.Today -> "today"
        FocusRoute.Crew -> "crew"
        FocusRoute.Goals -> "goals"
        FocusRoute.Library -> "library"
        FocusRoute.Bridge -> "bridge"
        FocusRoute.Device -> "device"
        FocusRoute.Assistant -> "assistant"
        FocusRoute.Settings -> "settings"
        FocusRoute.History -> "history"
        is FocusRoute.PluginPage -> "plugin:$pluginId:$pageId:${args.orEmpty()}"
    }

class ShellBackStack(val entries: SnapshotStateList<FocusRoute>) {
    constructor(start: FocusRoute) : this(mutableStateListOf(start))

    val current: FocusRoute get() = entries.last()

    fun open(route: FocusRoute) {
        if (route == current) return
        entries.clear()
        entries.add(FocusRoute.Chat)
        if (route != FocusRoute.Chat) entries.add(route)
    }

    fun push(route: FocusRoute) {
        if (route == current) return
        entries.add(route)
    }

    fun back(): Boolean {
        if (entries.size <= 1) return false
        entries.removeAt(entries.lastIndex)
        return true
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        val Saver: Saver<ShellBackStack, List<String>> = Saver(
            save = { stack ->
                ArrayList(
                    stack.entries.map { route -> json.encodeToString(FocusRoute.serializer(), route) },
                )
            },
            restore = { values ->
                val routes = values.mapNotNull { value ->
                    runCatching { json.decodeFromString(FocusRoute.serializer(), value) }.getOrNull()
                }
                ShellBackStack(
                    mutableStateListOf(*(routes.ifEmpty { listOf(FocusRoute.Chat) }).toTypedArray()),
                )
            },
        )
    }
}
