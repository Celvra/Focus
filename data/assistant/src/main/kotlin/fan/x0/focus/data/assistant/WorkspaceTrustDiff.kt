package fan.x0.focus.data.assistant

internal object WorkspaceTrustDiff {
    fun render(diff: FileDiff): String {
        val builder = StringBuilder()
        builder.appendLine("--- ${diff.path}")
        builder.appendLine("+++ ${diff.path}")
        diff.hunks.forEach { hunk ->
            builder.appendLine("@@")
            hunk.lines.forEach { line ->
                val prefix = when (line.kind) {
                    DiffLineKind.Added -> "+"
                    DiffLineKind.Removed -> "-"
                    DiffLineKind.Context -> " "
                }
                builder.append(prefix).appendLine(line.text)
            }
        }
        if (diff.truncated) {
            builder.appendLine("(diff truncated)")
        }
        return builder.toString().trim()
    }
}
