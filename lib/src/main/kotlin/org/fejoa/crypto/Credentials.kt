package org.fejoa.crypto

import kotlinx.serialization.Serializable
import org.fejoa.storage.HashValue
import org.fejoa.support.await
import org.fejoa.support.decodeBase64
import org.fejoa.support.encodeBase64


@Serializable
class EncData(val encData: String, val iv: String, val settings: CryptoSettings.Symmetric) {
    constructor(encryptedData: ByteArray, iv: ByteArray, settings: CryptoSettings.Symmetric)
            : this(encryptedData.encodeBase64(), iv.encodeBase64(), settings)

    fun getIv(): ByteArray {
        return iv.decodeBase64()
    }

    fun getEncData(): ByteArray {
        return encData.decodeBase64()
    }
}

open class SymBaseCredentials(val key: SecretKey, val settings: CryptoSettings.Symmetric)

class SymCredentials(key: SecretKey, val iv: ByteArray, symmetric: CryptoSettings.Symmetric)
    : SymBaseCredentials(key, symmetric)

suspend fun CryptoSettings.Symmetric.generateBaseCredentials(): SymBaseCredentials {
    val secret = CryptoHelper.crypto.generateSymmetricKey(this.key).await()
    return SymBaseCredentials(secret, this)
}

suspend fun CryptoSettings.Symmetric.generateCredentials(): SymCredentials {
    val iv = CryptoHelper.crypto.generateInitializationVector(ivSize)
    val secret = CryptoHelper.crypto.generateSymmetricKey(this.key).await()
    return SymCredentials(secret, iv, this)
}


class SignCredentials(val keyPair: KeyPair, val settings: CryptoSettings.Signature) {
    suspend fun getId(): HashValue {
        return keyPair.getId()
    }
}