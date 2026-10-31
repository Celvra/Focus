package fan.x0.focus.feature.chat

import fan.x0.focus.core.designsystem.components.FocusDiff
import fan.x0.focus.core.designsystem.components.FocusDiffHunk
import fan.x0.focus.core.designsystem.components.FocusDiffLine
import fan.x0.focus.core.designsystem.components.FocusDiffLineKind
import fan.x0.focus.data.assistant.DiffLineKind
import fan.x0.focus.data.assistant.FileDiff

internal fun FileDiff.toFocusDiff(): FocusDiff = FocusDiff(
    path = path,
    additions = additions,
    deletions = deletions,
    hunks = hunks.map { hunk ->
        FocusDiffHunk(
            lines = hunk.lines.map { line ->
                FocusDiffLine(
                    kind = line.kind.toFocusDiffLineKind(),
                    oldLine = line.oldLine,
                    newLine = line.newLine,
                    text = line.text,
                )
            },
        )
    },
    truncated = truncated,
)

private fun DiffLineKind.toFocusDiffLineKind(): FocusDiffLineKind = when (this) {
    DiffLineKind.Context -> FocusDiffLineKind.Context
    DiffLineKind.Added -> FocusDiffLineKind.Added
    DiffLineKind.Removed -> FocusDiffLineKind.Removed
}
