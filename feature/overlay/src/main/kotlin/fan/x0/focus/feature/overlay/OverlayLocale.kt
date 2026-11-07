package fan.x0.focus.feature.overlay

import android.content.Context
import android.content.res.Configuration
import fan.x0.focus.data.assistant.LocaleController
import org.koin.mp.KoinPlatform
import java.util.Locale

internal object OverlayLocale {
    fun wrap(base: Context): Context {
        val tag = runCatching {
            KoinPlatform.getKoin().get<LocaleController>().current()
        }.getOrNull() ?: return base
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(tag))
        return base.createConfigurationContext(configuration)
    }
}
