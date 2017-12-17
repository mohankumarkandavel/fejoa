package org.fejoa.crypto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JSON
import org.fejoa.storage.secretKeyFromJson
import org.fejoa.storage.toJson
import org.fejoa.support.*


/**
 * @salt encoded as base64
 */
@Serializable
class BaseKeyParams(val kdf: CryptoSettings.KDF = CryptoSettings.KDF(), val salt: String)  {
    constructor(kdf: CryptoSettings.KDF, salt: ByteArray) : this(kdf, salt.encodeBase64())

    fun getSalt(): ByteArray {
        return salt.decodeBase64()
    }
}

/**
 * @param baseKeyParams to derive the base key
 * @param hash to derive a user key from the base key, e.g. SHA256
 * @param salt to derive a user key using the given algorithm
 * @salt encoded as base64
 */
@Serializable
data class UserKeyParams(var baseKeyParams: BaseKeyParams, val hash: CryptoSettings.HASH_TYPE,
                         val keyType: CryptoSettings.KEY_TYPE, val salt: String) {
    constructor(baseKeyParams: BaseKeyParams, hash: CryptoSettings.HASH_TYPE, keyType: CryptoSettings.KEY_TYPE,
                salt: ByteArray) : this(baseKeyParams, hash, keyType, salt.encodeBase64())

    fun getSalt(): ByteArray {
        return salt.decodeBase64()
    }
}


/**
 * Caches the expensive calculation of the base key
 */
class BaseKeyCache {
    private val baseKeyCache: MutableMap<String, SecretKey> = HashMap()

    suspend private fun hash(baseKeyParams: BaseKeyParams, password: String): String {
        val hashStream = CryptoHelper.sha256Hash()
        hashStream.write(JSON.Companion.stringify(baseKeyParams).toUTF())
        hashStream.write(password.toUTF())
        return hashStream.hash().toHex()
    }

    suspend fun getBaseKey(baseKeyParams: BaseKeyParams, password: String): SecretKey {
        val hash = hash(baseKeyParams, password)
        return baseKeyCache[hash] ?: deriveKey(baseKeyParams, password).also { baseKeyCache[hash] = it }
    }

    suspend fun getUserKey(userKeyParams: UserKeyParams, password: String): SecretKey {
        val baseKey = getBaseKey(userKeyParams.baseKeyParams, password)
        val hashStream = CryptoHelper.getHashStream(userKeyParams.hash)
        hashStream.write(CryptoHelper.crypto.encode(baseKey).await())
        hashStream.write(userKeyParams.getSalt())
        return CryptoHelper.crypto.secretKeyFromRaw(hashStream.hash(), userKeyParams.keyType).await()
    }

    private suspend fun deriveKey(baseKeyParams: BaseKeyParams, password: String): SecretKey {
        return CryptoHelper.crypto.deriveKey(password, baseKeyParams.getSalt(), baseKeyParams.kdf).await()
    }
}

@Serializable
class PasswordProtectedKey(val userKeyParams: UserKeyParams, val encKey: EncData) {
    companion object {
        suspend fun create(secretKey: SecretKey, userKeyParams: UserKeyParams, password: String, cache: BaseKeyCache)
                : PasswordProtectedKey {
            val userKey = cache.getUserKey(userKeyParams, password)

            val settings = CryptoSettings.default.symmetric
            val iv = CryptoHelper.crypto.generateInitializationVector(settings.ivSize)
            val credentials = SymCredentials(userKey, iv, settings)
            val encryptedKey = CryptoHelper.crypto.encryptSymmetric(secretKey.toJson().toUTF(),
                    credentials).await()

            return PasswordProtectedKey(userKeyParams, EncData(encryptedKey, iv, settings))
        }
    }

    suspend fun decryptKey(password: String, cache: BaseKeyCache): SecretKey {
        val userKey = cache.getUserKey(userKeyParams, password)
        val jsonKey = CryptoHelper.crypto.decryptSymmetric(encKey.getEncData(), userKey,
                encKey.getIv(), encKey.settings).await().toUTFString()
        return secretKeyFromJson(jsonKey)
    }
}
