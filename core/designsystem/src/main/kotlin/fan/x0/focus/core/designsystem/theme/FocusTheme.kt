package fan.x0.focus.core.designsystem.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import fan.x0.focus.core.designsystem.components.FocusHaptics
import fan.x0.focus.core.designsystem.components.LocalFocusHaptics

// One accessor for the baseline scale and the emphasized roles used across the app.
val LocalFocusTypography = staticCompositionLocalOf { FocusTypography() }
val LocalFocusMotion = staticCompositionLocalOf { FocusMotionDefaults }

fun focusColorScheme(context: Context, dark: Boolean): ColorScheme {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> FallbackDarkScheme
        else -> FallbackLightScheme
    }
}

@Composable
fun FocusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = focusColorScheme(context, darkTheme)
    val typography = FocusTypography()
    val hapticFeedback = LocalHapticFeedback.current
    val haptics = remember(hapticFeedback) { FocusHaptics(hapticFeedback) }
    CompositionLocalProvider(
        LocalFocusTypography provides typography,
        LocalFocusMotion provides FocusMotionDefaults,
        LocalFocusHaptics provides haptics,
    ) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            typography = typography.baseline,
            shapes = FocusShapes,
            content = content,
        )
    }
}
