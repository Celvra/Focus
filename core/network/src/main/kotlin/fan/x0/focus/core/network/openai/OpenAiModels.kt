package fan.x0.focus.core.network.openai

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

@Serializable
data class ToolFunctionDto(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

@Serializable
data class ToolDto(
    @EncodeDefault val type: String = "function",
    val function: ToolFunctionDto,
)

@Serializable
data class FunctionCallDto(
    val name: String? = null,
    val arguments: String? = null,
)

@Serializable
data class ToolCallDto(
    val id: String? = null,
    @EncodeDefault val type: String = "function",
    val function: FunctionCallDto,
)

@Serializable
data class ImageUrlDto(
    val url: String,
    val detail: String? = null,
)

@Serializable
data class ContentPartDto(
    val type: String,
    val text: String? = null,
    @SerialName("image_url") val imageUrl: ImageUrlDto? = null,
) {
    companion object {
        fun text(value: String) = ContentPartDto(type = "text", text = value)
        fun image(url: String, detail: String? = null) =
            ContentPartDto(type = "image_url", imageUrl = ImageUrlDto(url, detail))
    }
}

sealed interface MessageContent {
    data class Text(val value: String) : MessageContent

    data class Parts(val parts: List<ContentPartDto>) : MessageContent

    companion object {
        fun of(text: String, images: List<String> = emptyList()): MessageContent {
            if (images.isEmpty()) return Text(text)
            return Parts(
                buildList {
                    if (text.isNotBlank()) add(ContentPartDto.text(text))
                    images.forEach { add(ContentPartDto.image(it)) }
                },
            )
        }
    }
}

object MessageContentSerializer : KSerializer<MessageContent> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: MessageContent) {
        val json = encoder as? JsonEncoder ?: throw SerializationException("MessageContent needs a JSON encoder")
        when (value) {
            is MessageContent.Text -> json.encodeJsonElement(JsonPrimitive(value.value))
            is MessageContent.Parts -> json.encodeJsonElement(
                buildJsonArray {
                    value.parts.forEach { part ->
                        add(
                            buildJsonObject {
                                put("type", part.type)
                                part.text?.let { put("text", it) }
                                part.imageUrl?.let { image ->
                                    putJsonObject("image_url") {
                                        put("url", image.url)
                                        image.detail?.let { put("detail", it) }
                                    }
                                }
                            },
                        )
                    }
                },
            )
        }
    }

    override fun deserialize(decoder: Decoder): MessageContent {
        val json = decoder as? JsonDecoder ?: throw SerializationException("MessageContent needs a JSON decoder")
        return when (val element = json.decodeJsonElement()) {
            is JsonPrimitive -> MessageContent.Text(element.contentOrNull.orEmpty())
            is JsonArray -> MessageContent.Parts(
                element.mapNotNull { part ->
                    val obj = part.jsonObject
                    val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    when (type) {
                        "text" -> ContentPartDto.text(
                            obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        )
                        "image_url" -> {
                            val image = obj["image_url"]?.jsonObject
                            val url = image?.get("url")?.jsonPrimitive?.contentOrNull
                            if (url == null) null else ContentPartDto.image(
                                url = url,
                                detail = image["detail"]?.jsonPrimitive?.contentOrNull,
                            )
                        }
                        else -> null
                    }
                },
            )
            else -> MessageContent.Text("")
        }
    }
}

@Serializable
data class ChatMessageDto(
    val role: String,
    @Serializable(with = MessageContentSerializer::class) val content: MessageContent? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    val reasoning: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCallDto>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    val name: String? = null,
) {
    constructor(
        role: String,
        content: String,
        reasoningContent: String? = null,
        toolCalls: List<ToolCallDto>? = null,
        toolCallId: String? = null,
        name: String? = null,
    ) : this(
        role = role,
        content = if (content.isEmpty()) null else MessageContent.Text(content),
        reasoningContent = reasoningContent,
        reasoning = null,
        toolCalls = toolCalls,
        toolCallId = toolCallId,
        name = name,
    )
}

@Serializable
data class StreamOptionsDto(
    @SerialName("include_usage") val includeUsage: Boolean = true,
)

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessageDto>,
    val stream: Boolean = false,
    @SerialName("stream_options") val streamOptions: StreamOptionsDto? = null,
    val tools: List<ToolDto>? = null,
    @SerialName("tool_choice") val toolChoice: String? = null,
    val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("reasoning_effort") val reasoningEffort: String? = null,
)

@Serializable
data class UsageDto(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
    @SerialName("prompt_tokens_details") val promptDetails: PromptTokensDetailsDto? = null,
    @SerialName("cache_read_input_tokens") val cacheReadInputTokens: Int = 0,
)

@Serializable
data class PromptTokensDetailsDto(
    @SerialName("cached_tokens") val cachedTokens: Int = 0,
)

@Serializable
data class ResponseMessageDto(
    val role: String? = null,
    val content: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    val reasoning: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCallDto>? = null,
)

@Serializable
data class ToolCallDeltaFunctionDto(
    val name: String? = null,
    val arguments: String? = null,
)

@Serializable
data class ToolCallDeltaDto(
    val index: Int = 0,
    val id: String? = null,
    val function: ToolCallDeltaFunctionDto? = null,
)

@Serializable
data class DeltaDto(
    val role: String? = null,
    val content: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    val reasoning: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCallDeltaDto>? = null,
)

@Serializable
data class ChoiceDto(
    val index: Int = 0,
    val message: ResponseMessageDto? = null,
    val delta: DeltaDto? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class ChatCompletionResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<ChoiceDto> = emptyList(),
    val usage: UsageDto? = null,
)

@Serializable
data class ApiErrorBody(
    val error: ApiErrorDetail? = null,
    val message: String? = null,
)

@Serializable
data class ApiErrorDetail(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null,
)

sealed interface ChatStreamEvent {
    data class Text(val delta: String) : ChatStreamEvent
    data class Reasoning(val delta: String) : ChatStreamEvent
    data class ToolDelta(
        val index: Int,
        val id: String?,
        val name: String?,
        val arguments: String?,
    ) : ChatStreamEvent
    data class Finished(val reason: String?) : ChatStreamEvent
    data class Usage(
        val prompt: Int,
        val completion: Int,
        val total: Int,
        val cached: Int = 0,
    ) : ChatStreamEvent
    data class ProtocolError(val message: String) : ChatStreamEvent
}

// Assembled tool call after the stream ends.
data class CompletedToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

data class CompletedMessage(
    val text: String,
    val reasoning: String,
    val toolCalls: List<CompletedToolCall>,
    val finishReason: String?,
    val usage: UsageDto?,
)
