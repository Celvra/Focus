package fan.x0.focus.core.network.openai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

internal fun JsonObject.wireString(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

internal fun JsonObject.wireText(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }

internal fun JsonObject.wireInt(key: String): Int? =
    (this[key] as? JsonPrimitive)?.intOrNull

internal fun JsonObject.wireObject(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.wireArray(key: String): JsonArray? = this[key] as? JsonArray

internal fun ChatMessageDto.textContent(): String = when (val value = content) {
    is MessageContent.Text -> value.value
    is MessageContent.Parts -> value.parts.mapNotNull { it.text }.joinToString("\n")
    null -> ""
}
