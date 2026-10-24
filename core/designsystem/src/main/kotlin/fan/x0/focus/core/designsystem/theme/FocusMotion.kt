package fan.x0.focus.core.designsystem.theme

import androidx.compose.animation.core.SpringSpec

// Expressive motion scheme. Spatial properties bounce, effects never overshoot.
// Values come from the M3 Expressive motion tokens: spatial 0.8/380, effects 1.0/1600.
class FocusMotion {
    fun <T> spatialDefault(): SpringSpec<T> = SpringSpec(dampingRatio = 0.8f, stiffness = 380f)
    fun <T> spatialFast(): SpringSpec<T> = SpringSpec(dampingRatio = 0.6f, stiffness = 800f)
    fun <T> spatialSlow(): SpringSpec<T> = SpringSpec(dampingRatio = 0.8f, stiffness = 200f)
    fun <T> effectsDefault(): SpringSpec<T> = SpringSpec(dampingRatio = 1f, stiffness = 1600f)
    fun <T> effectsFast(): SpringSpec<T> = SpringSpec(dampingRatio = 1f, stiffness = 3800f)
    fun <T> effectsSlow(): SpringSpec<T> = SpringSpec(dampingRatio = 1f, stiffness = 800f)
}

val FocusMotionDefaults = FocusMotion()
