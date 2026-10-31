package fan.x0.focus.core.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

@Immutable
class FocusHaptics internal constructor(
    private val feedback: HapticFeedback,
) {
    private var lastSelect = 0L
    private var lastStream = 0L

    fun tap() {
        feedback.performHapticFeedback(HapticFeedbackType.ContextClick)
    }

    fun select() {
        val now = System.nanoTime()
        if (now - lastSelect < SelectInterval) return
        lastSelect = now
        feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }

    fun toggle(checked: Boolean) {
        feedback.performHapticFeedback(
            if (checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
        )
    }

    fun longPress() {
        feedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun streamTick() {
        val now = System.nanoTime()
        if (now - lastStream < StreamInterval) return
        lastStream = now
        feedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    private companion object {
        const val SelectInterval = 60_000_000L
        const val StreamInterval = 70_000_000L
    }
}

private object NoopHapticFeedback : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}

val LocalFocusHaptics = staticCompositionLocalOf { FocusHaptics(NoopHapticFeedback) }

@Composable
fun hapticTap(block: () -> Unit): () -> Unit {
    val haptics = LocalFocusHaptics.current
    return {
        haptics.tap()
        block()
    }
}

@Composable
fun hapticSelect(block: () -> Unit): () -> Unit {
    val haptics = LocalFocusHaptics.current
    return {
        haptics.select()
        block()
    }
}

@Composable
fun hapticToggle(block: (Boolean) -> Unit): (Boolean) -> Unit {
    val haptics = LocalFocusHaptics.current
    return { checked ->
        haptics.toggle(checked)
        block(checked)
    }
}

@Composable
fun hapticToggle(target: Boolean, block: () -> Unit): () -> Unit {
    val haptics = LocalFocusHaptics.current
    return {
        haptics.toggle(target)
        block()
    }
}

@Composable
fun hapticLongPress(block: () -> Unit): () -> Unit {
    val haptics = LocalFocusHaptics.current
    return {
        haptics.longPress()
        block()
    }
}
