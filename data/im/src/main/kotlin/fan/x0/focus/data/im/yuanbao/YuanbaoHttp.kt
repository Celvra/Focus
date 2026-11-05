package fan.x0.focus.data.im.yuanbao

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal data class YuanbaoToken(
    val token: String,
    val botId: String,
    val source: String,
    val duration: Int,
)

internal class YuanbaoAuthException(message: String) : Exception(message)

internal class YuanbaoHttp(
    private val http: HttpClient,
    private val appVersion: String,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val random = SecureRandom()

    suspend fun signToken(appKey: String, appSecret: String, baseUrl: String): YuanbaoToken {
        val domain = baseUrl.trim().ifBlank { DEFAULT_API_DOMAIN }
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')
        val url = "https://$domain$SIGN_PATH"
        var lastError = "sign token failed"
        repeat(4) { attempt ->
            val nonce = ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it) }
            val timestamp = OffsetDateTime.now(ZoneOffset.ofHours(8))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"))
            val signature = signature(appSecret, nonce, timestamp, appKey)
            val body = SignBody(
                appKey = appKey,
                nonce = nonce,
                signature = signature,
                timestamp = timestamp,
            )
            val response = http.post(url) {
                contentType(ContentType.Application.Json)
                header("X-AppVersion", appVersion)
                header("X-OperationSystem", "Android")
                header("X-Instance-Id", "16")
                header("X-Bot-Version", appVersion)
                setBody(json.encodeToString(SignBody.serializer(), body))
            }
            val envelope = runCatching {
                json.decodeFromString(SignEnvelope.serializer(), response.bodyAsText())
            }.getOrNull()
            if (envelope == null) {
                lastError = "HTTP ${response.status.value}"
                if (attempt < 3) delay(1_000)
            } else if (envelope.code == 0 && envelope.data?.token?.isNotBlank() == true) {
                val data = envelope.data
                return YuanbaoToken(
                    token = data.token,
                    botId = data.botId,
                    source = data.source.ifBlank { "bot" },
                    duration = data.duration,
                )
            } else if (envelope.code == RETRYABLE_CODE && attempt < 3) {
                lastError = envelope.msg.ifBlank { "retryable error" }
                delay(1_000)
            } else {
                throw YuanbaoAuthException(
                    envelope.msg.ifBlank { "sign token code ${envelope.code}" },
                )
            }
        }
        throw YuanbaoRetryException(lastError)
    }

    private fun signature(appSecret: String, nonce: String, timestamp: String, appKey: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(appSecret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val bytes = mac.doFinal((nonce + timestamp + appKey + appSecret).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val DEFAULT_API_DOMAIN = "bot.yuanbao.tencent.com"
        private const val SIGN_PATH = "/api/v5/robotLogic/sign-token"
        private const val RETRYABLE_CODE = 10099
    }
}

@Serializable
private data class SignBody(
    @SerialName("app_key") val appKey: String,
    val nonce: String,
    val signature: String,
    val timestamp: String,
)

@Serializable
private data class SignEnvelope(
    val code: Int = -1,
    val msg: String = "",
    val data: SignData? = null,
)

@Serializable
private data class SignData(
    val token: String = "",
    @SerialName("bot_id") val botId: String = "",
    val duration: Int = 0,
    val source: String = "bot",
)
