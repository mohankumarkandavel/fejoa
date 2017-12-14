package org.fejoa.crypto

import org.fejoa.storage.HashValue
import org.fejoa.support.await


open class SymBaseCredentials(val key: SecretKey, val symmetric: CryptoSettings.Symmetric)

class SymCredentials(key: SecretKey, val iv: ByteArray, symmetric: CryptoSettings.Symmetric)
    : SymBaseCredentials(key, symmetric)

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