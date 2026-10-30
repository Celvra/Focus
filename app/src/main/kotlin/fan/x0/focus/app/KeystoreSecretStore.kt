package fan.x0.focus.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import fan.x0.focus.data.assistant.LEGACY_PROVIDER_ID
import fan.x0.focus.data.assistant.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Single
class KeystoreSecretStore(@Provided private val context: Context) : SecretStore {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override suspend fun apiKey(providerId: String): String? = withContext(Dispatchers.IO) {
        val stored = prefs.getString(key(providerId), null)
            ?: if (providerId == LEGACY_PROVIDER_ID) prefs.getString(KEY_API, null) else null
        stored?.let { runCatching { decrypt(it) }.getOrNull() }
    }

    override suspend fun setApiKey(providerId: String, value: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(key(providerId), encrypt(value)).apply()
    }

    override suspend fun clearApiKey(providerId: String) = withContext(Dispatchers.IO) {
        prefs.edit().apply {
            remove(key(providerId))
            if (providerId == LEGACY_PROVIDER_ID) remove(KEY_API)
        }.apply()
    }

    override suspend fun secret(name: String): String? = withContext(Dispatchers.IO) {
        prefs.getString(key(name), null)?.let { runCatching { decrypt(it) }.getOrNull() }
    }

    override suspend fun setSecret(name: String, value: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(key(name), encrypt(value)).apply()
    }

    override suspend fun clearSecret(name: String) = withContext(Dispatchers.IO) {
        prefs.edit().remove(key(name)).apply()
    }

    private fun key(providerId: String): String = "api_key_$providerId"

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val body = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        val packed = ByteArray(1 + iv.size + body.size)
        packed[0] = iv.size.toByte()
        iv.copyInto(packed, destinationOffset = 1)
        body.copyInto(packed, destinationOffset = 1 + iv.size)
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val packed = Base64.decode(stored, Base64.NO_WRAP)
        val ivSize = packed[0].toInt()
        val iv = packed.copyOfRange(1, 1 + ivSize)
        val body = packed.copyOfRange(1 + ivSize, packed.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(body), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val PREFS = "focus_secrets"
        private const val KEY_API = "api_key"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "focus_api_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
