package fan.x0.focus.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object FocusRadii {
    val medium = 12.dp
    val large = 16.dp
    val largeIncreased = 20.dp
    val extraLarge = 28.dp
    val extraLargeIncreased = 32.dp
    val extraExtraLarge = 48.dp
}

val FocusShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(FocusRadii.medium),
    large = RoundedCornerShape(FocusRadii.large),
    largeIncreased = RoundedCornerShape(FocusRadii.largeIncreased),
    extraLarge = RoundedCornerShape(FocusRadii.extraLarge),
    extraLargeIncreased = RoundedCornerShape(FocusRadii.extraLargeIncreased),
    extraExtraLarge = RoundedCornerShape(FocusRadii.extraExtraLarge),
)

object FocusShapeTokens {
    val card = RoundedCornerShape(FocusRadii.largeIncreased)
    val cardLarge = RoundedCornerShape(FocusRadii.extraLarge)
    val sheet = RoundedCornerShape(FocusRadii.extraLargeIncreased)
    val hero = RoundedCornerShape(FocusRadii.extraExtraLarge)
    val pill = RoundedCornerShape(percent = 50)

    // Chat bubble: one speaker-side corner tightened. Design decision, not an M3 token.
    val bubbleIncoming = RoundedCornerShape(28.dp, 28.dp, 28.dp, 8.dp)
    val bubbleOutgoing = RoundedCornerShape(28.dp, 28.dp, 8.dp, 28.dp)

    // Inner radius = outer radius - padding.
    fun inner(outer: androidx.compose.ui.unit.Dp, padding: androidx.compose.ui.unit.Dp) =
        RoundedCornerShape((outer - padding).coerceAtLeast(0.dp))
}
