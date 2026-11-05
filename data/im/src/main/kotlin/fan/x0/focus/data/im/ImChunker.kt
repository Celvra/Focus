package fan.x0.focus.data.im

object ImChunker {
    fun split(text: String, limit: Int): List<String> {
        val max = limit.coerceAtLeast(200)
        if (text.length <= max) return listOf(text)
        val pieces = mutableListOf<Piece>()
        parse(text).forEach { block ->
            when (block) {
                is Block.Plain -> splitPlain(block.text, max).forEach {
                    pieces.add(Piece(it, code = false))
                }
                is Block.Code -> splitCode(block, max).forEach {
                    pieces.add(Piece(it, code = true))
                }
            }
        }
        val merged = mutableListOf<String>()
        var current = StringBuilder()
        pieces.forEach { piece ->
            val addition = if (current.isEmpty()) {
                piece.text
            } else {
                "\n\n" + piece.text
            }
            if (current.length + addition.length > max || (piece.code && current.isNotEmpty())) {
                if (current.isNotEmpty()) merged.add(current.toString().trim())
                current = StringBuilder(piece.text)
            } else {
                current.append(addition)
            }
        }
        if (current.isNotEmpty()) merged.add(current.toString().trim())
        return merged.filter { it.isNotBlank() }.ifEmpty { listOf(text.take(max)) }
    }

    private fun parse(text: String): List<Block> {
        val blocks = mutableListOf<Block>()
        val plain = StringBuilder()
        val code = StringBuilder()
        var openFence = ""
        var inCode = false
        text.lines().forEach { line ->
            val trimmed = line.trimStart()
            if (!inCode && trimmed.startsWith("```")) {
                if (plain.isNotEmpty()) {
                    blocks.add(Block.Plain(plain.toString().trimEnd()))
                    plain.clear()
                }
                inCode = true
                openFence = line
                code.clear()
            } else if (inCode && trimmed.startsWith("```")) {
                blocks.add(Block.Code(openFence, code.toString().trimEnd(), line))
                inCode = false
                code.clear()
                openFence = ""
            } else {
                if (inCode) {
                    if (code.isNotEmpty()) code.append('\n')
                    code.append(line)
                } else {
                    if (plain.isNotEmpty()) plain.append('\n')
                    plain.append(line)
                }
            }
        }
        if (inCode) {
            blocks.add(Block.Code(openFence, code.toString().trimEnd(), "```"))
        } else if (plain.isNotEmpty()) {
            blocks.add(Block.Plain(plain.toString().trimEnd()))
        }
        return blocks
    }

    private fun splitPlain(text: String, limit: Int): List<String> {
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val remaining = text.length - start
            if (remaining <= limit) {
                val tail = text.substring(start).trim()
                if (tail.isNotEmpty()) chunks.add(tail)
                break
            }
            val window = text.substring(start, start + limit)
            val cut = cutPoint(window, limit)
            val chunk = text.substring(start, start + cut).trim()
            if (chunk.isNotEmpty()) chunks.add(chunk)
            start += cut
            while (start < text.length && text[start] == '\n') start++
        }
        return chunks
    }

    private fun cutPoint(window: String, limit: Int): Int {
        val paragraph = window.lastIndexOf("\n\n")
        if (paragraph >= limit / 3) return paragraph + 2
        val line = window.lastIndexOf('\n')
        if (line >= limit / 2) return line + 1
        val sentence = window.indexOfLast { it == '。' || it == '！' || it == '？' || it == '.' || it == '!' || it == '?' }
        if (sentence >= limit / 2) return sentence + 1
        val space = window.lastIndexOf(' ')
        if (space >= limit / 2) return space + 1
        return limit
    }

    private fun splitCode(block: Block.Code, limit: Int): List<String> {
        val full = "${block.open}\n${block.body}\n${block.close}"
        if (full.length <= limit) return listOf(full)
        val available = limit - block.open.length - block.close.length - 2
        if (available < 120) return hardSplit(full, limit)
        val pieces = mutableListOf<String>()
        var current = StringBuilder()
        block.body.lines().forEach { line ->
            val candidate = if (current.isEmpty()) line else "$current\n$line"
            if (candidate.length <= available) {
                current = StringBuilder(candidate)
            } else {
                if (current.isNotEmpty()) pieces.add(current.toString())
                if (line.length <= available) {
                    current = StringBuilder(line)
                } else {
                    hardSplit(line, available).forEach { pieces.add(it) }
                    current = StringBuilder()
                }
            }
        }
        if (current.isNotEmpty()) pieces.add(current.toString())
        return pieces.map { "${block.open}\n$it\n${block.close}" }
    }

    private fun hardSplit(text: String, limit: Int): List<String> {
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val end = (start + limit).coerceAtMost(text.length)
            chunks.add(text.substring(start, end))
            start = end
        }
        return chunks
    }

    private sealed interface Block {
        data class Plain(val text: String) : Block
        data class Code(val open: String, val body: String, val close: String) : Block
    }

    private data class Piece(val text: String, val code: Boolean)
}
