package fan.x0.focus.app

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import fan.x0.focus.data.assistant.LocaleController
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.lang.ref.WeakReference
import java.util.Locale

@Single
class AndroidLocaleController(@Provided private val context: Context) : LocaleController {

    private var host = WeakReference<Activity>(null)

    fun bind(activity: Activity) {
        host = WeakReference(activity)
    }

    override fun current(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            if (locales != null && !locales.isEmpty) return locales[0]?.language
            return null
        }
        return prefs(context).getString(KEY, null)
    }

    override fun setLanguage(tag: String?) {
        val normalized = tag?.takeIf { it.isNotBlank() }
        prefs(context).edit().apply {
            if (normalized == null) remove(KEY) else putString(KEY, normalized)
        }.apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = context.getSystemService(LocaleManager::class.java) ?: return
            manager.applicationLocales = if (normalized == null) {
                LocaleList.getEmptyLocaleList()
            } else {
                LocaleList.forLanguageTags(normalized)
            }
        } else {
            host.get()?.recreate()
        }
    }

    companion object {
        private const val PREFS = "focus_locale"
        private const val KEY = "language_tag"

        private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun wrap(base: Context): Context {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
            val tag = prefs(base).getString(KEY, null) ?: return base
            val configuration = Configuration(base.resources.configuration)
            configuration.setLocale(Locale.forLanguageTag(tag))
            return base.createConfigurationContext(configuration)
        }
    }
}
