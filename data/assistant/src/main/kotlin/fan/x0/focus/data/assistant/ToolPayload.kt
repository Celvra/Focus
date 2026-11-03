package fan.x0.focus.data.assistant

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object ToolPayload {
    fun format(raw: String?, maxChars: Int = 1200): String {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return ""
        val element = runCatching { Json.parseToJsonElement(text) }.getOrNull() ?: return clip(text, maxChars)
        val rendered = render(element, "").trim()
        return clip(rendered, maxChars)
    }

    private fun clip(text: String, maxChars: Int): String =
        if (text.length <= maxChars) text else text.take(maxChars).trimEnd() + "..."

    private fun render(element: JsonElement, indent: String): String = when (element) {
        is JsonObject -> element.entries.joinToString("\n") { (key, value) ->
            when (value) {
                is JsonObject -> "$indent$key:\n${render(value, "$indent  ")}"
                is JsonArray -> if (value.isEmpty()) {
                    "$indent$key: []"
                } else {
                    "$indent$key:\n${renderArray(value, "$indent  ")}"
                }
                else -> field(indent, key, value)
            }
        }
        is JsonArray -> renderArray(element, indent)
        else -> "$indent${scalar(element)}"
    }

    private fun renderArray(array: JsonArray, indent: String): String =
        array.joinToString("\n") { item ->
            when (item) {
                is JsonObject, is JsonArray -> {
                    val rendered = render(item, "$indent  ").trimStart()
                    "$indent- $rendered"
                }
                else -> "$indent- ${scalar(item)}"
            }
        }

    private fun field(indent: String, key: String, value: JsonElement): String {
        val text = scalar(value).replace("\n", "\n$indent")
        return "$indent$key: $text"
    }

    private fun scalar(element: JsonElement): String = when (element) {
        is JsonPrimitive -> if (element.isString) element.content else element.toString()
        else -> element.toString()
    }
}
