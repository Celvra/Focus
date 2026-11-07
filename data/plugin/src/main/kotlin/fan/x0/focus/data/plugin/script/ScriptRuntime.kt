package fan.x0.focus.data.plugin.script

import fan.x0.focus.data.assistant.ToolExecutor
import fan.x0.focus.plugin.PluginContext
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.koin.mp.KoinPlatform

class ScriptRuntime(
    private val context: PluginContext,
    private val source: String,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    @Volatile
    private var deadline = Long.MAX_VALUE

    private val factory = object : ContextFactory() {
        override fun makeContext(): Context {
            val cx = super.makeContext()
            cx.setOptimizationLevel(-1)
            cx.setLanguageVersion(Context.VERSION_ES6)
            cx.setInstructionObserverThreshold(20_000)
            cx.setClassShutter { false }
            return cx
        }

        override fun observeInstructionCount(cx: Context, count: Int) {
            if (Thread.currentThread().isInterrupted || System.currentTimeMillis() > deadline) {
                throw IllegalStateException("Script timed out")
            }
        }
    }

    fun call(function: String, args: List<String> = emptyList()): String? = synchronized(lock) {
        deadline = System.currentTimeMillis() + TIMEOUT_MS
        val cx = factory.enterContext()
        try {
            val scope = cx.initSafeStandardObjects()
            install(cx, scope)
            cx.evaluateString(scope, source, context.manifest.entry, 1, null)
            val target = scope.get(function, scope) as? Function ?: return null
            val result = target.call(cx, scope, scope, args.toTypedArray())
            Context.toString(result)
        } catch (e: Throwable) {
            context.logger.error("Script $function failed", e)
            errorJson(e.message.orEmpty())
        } finally {
            deadline = Long.MAX_VALUE
            factory.exit()
        }
    }

    private fun install(cx: Context, scope: Scriptable) {
        val focus = NativeObject()
        put(focus, "log", fn { args ->
            context.logger.info(args.firstOrNull()?.let(Context::toString).orEmpty())
            ""
        })
        put(focus, "storage", storage())
        put(focus, "http", http())
        put(focus, "tool", tool())
        put(focus, "notify", notify())
        put(focus, "foreground", foreground())
        ScriptableObject.putProperty(scope, "focus", focus)
    }

    private fun storage(): NativeObject {
        val objectValue = NativeObject()
        put(objectValue, "get", fn { args ->
            val key = args.firstOrNull()?.let(Context::toString).orEmpty()
            context.storage.get(key).orEmpty()
        })
        put(objectValue, "put", fn { args ->
            val key = args.getOrNull(0)?.let(Context::toString).orEmpty()
            val value = args.getOrNull(1)?.let(Context::toString).orEmpty()
            context.storage.put(key, value)
            ""
        })
        put(objectValue, "remove", fn { args ->
            val key = args.firstOrNull()?.let(Context::toString).orEmpty()
            context.storage.remove(key)
            ""
        })
        return objectValue
    }

    private fun http(): NativeObject {
        val objectValue = NativeObject()
        put(objectValue, "request", fn { args ->
            val method = args.getOrNull(0)?.let(Context::toString)?.uppercase() ?: "GET"
            val url = args.getOrNull(1)?.let(Context::toString).orEmpty()
            val headers = args.getOrNull(2)?.let(Context::toString).orEmpty()
            val body = args.getOrNull(3)?.let(Context::toString).orEmpty()
            request(method, url, headers, body)
        })
        put(objectValue, "get", fn { args ->
            val url = args.getOrNull(0)?.let(Context::toString).orEmpty()
            request("GET", url, args.getOrNull(1)?.let(Context::toString).orEmpty(), "")
        })
        put(objectValue, "post", fn { args ->
            val url = args.getOrNull(0)?.let(Context::toString).orEmpty()
            val body = args.getOrNull(1)?.let(Context::toString).orEmpty()
            request("POST", url, args.getOrNull(2)?.let(Context::toString).orEmpty(), body)
        })
        return objectValue
    }

    private fun tool(): NativeObject {
        val objectValue = NativeObject()
        put(objectValue, "call", fn { args ->
            val name = args.getOrNull(0)?.let(Context::toString).orEmpty()
            val arguments = args.getOrNull(1)?.let(Context::toString) ?: "{}"
            val executor = runCatching { KoinPlatform.getKoin().get<ToolExecutor>() }.getOrNull()
                ?: return@fn errorJson("Tool executor is not available")
            val result = runBlocking { executor.executeDirect(name, arguments) }
            buildJsonObject {
                put("ok", result.ok)
                put("content", result.content)
                put("summary", result.summary)
            }.toString()
        })
        return objectValue
    }

    private fun notify(): NativeObject {
        val objectValue = NativeObject()
        put(objectValue, "send", fn { args ->
            val title = args.getOrNull(0)?.let(Context::toString).orEmpty()
            val text = args.getOrNull(1)?.let(Context::toString).orEmpty()
            val channel = "plugin_${context.manifest.id}"
            context.system.notifications.channel(channel, context.manifest.name, 3)
            context.system.notifications.notify(context.manifest.id.hashCode(), channel, title, text)
            ""
        })
        return objectValue
    }

    private fun foreground(): NativeObject {
        val objectValue = NativeObject()
        put(objectValue, "start", fn { args ->
            val title = args.getOrNull(0)?.let(Context::toString).orEmpty()
            val text = args.getOrNull(1)?.let(Context::toString).orEmpty()
            context.system.foreground.start(context.manifest.id, title, text)
            ""
        })
        put(objectValue, "stop", fn {
            context.system.foreground.stop(context.manifest.id)
            ""
        })
        return objectValue
    }

    private fun request(method: String, url: String, headersJson: String, body: String): String {
        if (url.isBlank()) return errorJson("url is required")
        val headers = runCatching {
            json.parseToJsonElement(headersJson) as? JsonObject
        }.getOrNull().orEmpty()
        return runCatching {
            val response = runBlocking {
                context.host.http.request(url) {
                    this.method = HttpMethod.parse(method)
                    if (body.isNotBlank()) {
                        contentType(ContentType.Application.Json)
                        setBody(body)
                    }
                    headers.forEach { (key, value) ->
                        header(key, (value as? JsonPrimitive)?.content.orEmpty())
                    }
                }
            }
            val text = runBlocking { response.bodyAsText() }
            buildJsonObject {
                put("status", response.status.value)
                put("body", text)
            }.toString()
        }.getOrElse { errorJson(it.message.orEmpty()) }
    }

    private fun put(target: NativeObject, name: String, value: Any) {
        ScriptableObject.putProperty(target, name, value)
    }

    private fun fn(block: (Array<Any>) -> Any?): BaseFunction = object : BaseFunction() {
        override fun call(
            cx: Context,
            scope: Scriptable,
            thisObj: Scriptable,
            args: Array<Any>,
        ): Any = block(args) ?: Context.getUndefinedValue()
    }

    private fun errorJson(message: String): String = buildJsonObject {
        put("ok", false)
        put("content", message)
        put("summary", message.take(120))
    }.toString()

    companion object {
        private const val TIMEOUT_MS = 60_000L
    }
}
