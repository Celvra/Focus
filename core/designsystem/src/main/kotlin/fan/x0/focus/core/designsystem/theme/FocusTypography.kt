package fan.x0.focus.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle

// Baseline scale plus the emphasized variants Material 3.5 exposes for hierarchy.
data class FocusTypography(
    val baseline: Typography = Typography(),
    val displayLarge: TextStyle = baseline.displayLargeEmphasized,
    val displayMedium: TextStyle = baseline.displayMediumEmphasized,
    val displaySmall: TextStyle = baseline.displaySmallEmphasized,
    val headlineLarge: TextStyle = baseline.headlineLargeEmphasized,
    val headlineMedium: TextStyle = baseline.headlineMediumEmphasized,
    val headlineSmall: TextStyle = baseline.headlineSmallEmphasized,
    val titleLarge: TextStyle = baseline.titleLargeEmphasized,
    val titleMedium: TextStyle = baseline.titleMediumEmphasized,
    val titleSmall: TextStyle = baseline.titleSmallEmphasized,
    val bodyLarge: TextStyle = baseline.bodyLargeEmphasized,
    val bodyMedium: TextStyle = baseline.bodyMediumEmphasized,
    val bodySmall: TextStyle = baseline.bodySmallEmphasized,
    val labelLarge: TextStyle = baseline.labelLargeEmphasized,
    val labelMedium: TextStyle = baseline.labelMediumEmphasized,
    val labelSmall: TextStyle = baseline.labelSmallEmphasized,
)
