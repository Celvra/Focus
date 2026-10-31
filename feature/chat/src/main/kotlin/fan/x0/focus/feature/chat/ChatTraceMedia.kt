package fan.x0.focus.feature.chat

import fan.x0.focus.core.designsystem.components.FocusTraceImage

internal fun AttachmentUi.toFocusTraceImage(): FocusTraceImage? = base64?.let { raw ->
    FocusTraceImage(
        id = id,
        name = name,
        base64 = raw,
    )
}
