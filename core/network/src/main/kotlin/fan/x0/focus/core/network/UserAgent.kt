package fan.x0.focus.core.network

import android.content.Context

private const val APP_NAME = "Focus"

fun defaultUserAgent(context: Context): String {
    val version = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()
    return if (version.isNullOrBlank()) APP_NAME else "$APP_NAME/$version"
}
