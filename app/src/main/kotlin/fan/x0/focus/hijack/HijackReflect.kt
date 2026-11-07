package fan.x0.focus.hijack

import android.content.Context
import java.lang.reflect.Method

internal object HijackReflect {

    fun findClass(loader: ClassLoader, name: String): Class<*>? =
        runCatching { Class.forName(name, false, loader) }.getOrNull()

    fun methods(owner: Class<*>, name: String): List<Method> {
        val result = LinkedHashMap<String, Method>()
        var type: Class<*>? = owner
        while (type != null && type != Any::class.java) {
            for (method in type.declaredMethods) {
                if (method.name != name) continue
                val key = method.parameterTypes.joinToString(",") { it.name } + "#" + method.returnType.name
                if (result.containsKey(key)) continue
                method.isAccessible = true
                result[key] = method
            }
            type = type.superclass
        }
        return result.values.toList()
    }

    fun field(owner: Any, name: String): Any? {
        var type: Class<*>? = owner.javaClass
        while (type != null && type != Any::class.java) {
            val field = runCatching { type.getDeclaredField(name) }.getOrNull()
            if (field != null) {
                field.isAccessible = true
                return runCatching { field.get(owner) }.getOrNull()
            }
            type = type.superclass
        }
        return null
    }

    fun contextOf(owner: Any?): Context? {
        if (owner is Context) return owner
        if (owner != null) {
            contextFromFields(owner)?.let { return it }
            for (name in NESTED_OWNERS) {
                val nested = field(owner, name) ?: continue
                if (nested is Context) return nested
                contextFromFields(nested)?.let { return it }
                for (innerName in NESTED_OWNERS) {
                    val inner = field(nested, innerName) ?: continue
                    if (inner is Context) return inner
                    contextFromFields(inner)?.let { return it }
                }
            }
        }
        return SystemContext.get() ?: appContext()
    }

    private val NESTED_OWNERS = listOf("this$0", "mBase", "mPhoneWindowManager")

    fun appContext(): Context? = runCatching {
        Class.forName("android.app.ActivityThread")
            .getMethod("currentApplication")
            .invoke(null) as? Context
    }.getOrNull()

    private fun contextFromFields(owner: Any): Context? {
        var type: Class<*>? = owner.javaClass
        while (type != null && type != Any::class.java) {
            for (field in type.declaredFields) {
                if (!Context::class.java.isAssignableFrom(field.type)) continue
                field.isAccessible = true
                val value = runCatching { field.get(owner) }.getOrNull()
                if (value is Context) return value
            }
            type = type.superclass
        }
        return null
    }
}
