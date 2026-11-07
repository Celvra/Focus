package fan.x0.focus.data.im.yuanbao

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import java.net.URLEncoder
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Serializable
internal data class CosUploadConfig(
    val bucketName: String = "",
    val region: String = "",
    val location: String = "",
    val encryptTmpSecretId: String = "",
    val encryptTmpSecretKey: String = "",
    val encryptToken: String = "",
    val startTime: Long = 0,
    val expiredTime: Long = 0,
    val resourceUrl: String = "",
    val resourceID: String = "",
)

internal suspend fun putCosObject(
    http: HttpClient,
    config: CosUploadConfig,
    bytes: ByteArray,
    mime: String,
) {
    val path = config.location.let { if (it.startsWith("/")) it else "/$it" }
    val host = "${config.bucketName}.cos.${config.region}.myqcloud.com"
    val headers = sortedMapOf(
        "host" to host,
        "content-length" to bytes.size.toString(),
    )
    if (config.encryptToken.isNotBlank()) {
        headers["x-cos-security-token"] = config.encryptToken
    }
    val authorization = cosAuthorization(
        secretId = config.encryptTmpSecretId,
        secretKey = config.encryptTmpSecretKey,
        method = "PUT",
        path = path,
        headers = headers,
        startTime = config.startTime,
        expiredTime = config.expiredTime,
    )
    val type = runCatching { ContentType.parse(mime) }
        .getOrDefault(ContentType.Application.OctetStream)
    val response = http.put("https://$host$path") {
        contentType(type)
        header("Authorization", authorization)
        if (config.encryptToken.isNotBlank()) {
            header("x-cos-security-token", config.encryptToken)
        }
        setBody(bytes)
    }
    if (!response.status.isSuccess()) {
        val body = runCatching { response.bodyAsText() }.getOrDefault("")
        error("COS 上传失败 ${response.status.value}: ${body.take(200)}")
    }
}

private fun cosAuthorization(
    secretId: String,
    secretKey: String,
    method: String,
    path: String,
    headers: Map<String, String>,
    startTime: Long,
    expiredTime: Long,
): String {
    val keyTime = "$startTime;$expiredTime"
    val signKey = hmacSha1(secretKey, keyTime)
    val keys = headers.keys.map { it.lowercase() }.sorted()
    val headerList = keys.joinToString(";")
    val httpHeaders = keys.joinToString("&") { key ->
        "$key=${encode(headers.entries.first { it.key.lowercase() == key }.value)}"
    }
    val httpString = "${method.lowercase()}\n$path\n\n$httpHeaders\n"
    val stringToSign = "sha1\n$keyTime\n${sha1Hex(httpString)}\n"
    val signature = hmacSha1(signKey, stringToSign)
    return listOf(
        "q-sign-algorithm=sha1",
        "q-ak=$secretId",
        "q-sign-time=$keyTime",
        "q-key-time=$keyTime",
        "q-header-list=$headerList",
        "q-url-param-list=",
        "q-signature=$signature",
    ).joinToString("&")
}

private fun hmacSha1(key: String, value: String): String {
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA1"))
    return mac.doFinal(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

private fun sha1Hex(value: String): String =
    MessageDigest.getInstance("SHA-1").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

private fun encode(value: String): String =
    URLEncoder.encode(value, "UTF-8")
        .replace("+", "%20")
        .replace("*", "%2A")
        .replace("%7E", "~")
