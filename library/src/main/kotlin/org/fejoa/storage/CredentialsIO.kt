package org.fejoa.storage

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JSON
import org.fejoa.crypto.*
import org.fejoa.support.await
import org.fejoa.support.decodeBase64
import org.fejoa.support.encodeBase64


@Serializable
private class KeyJson(val key: String, val type: CryptoSettings.KEY_TYPE) {
    companion object {
        suspend fun from(key: Key): KeyJson {
            val keyBase64 = CryptoHelper.crypto.encode(key).await().encodeBase64()
            return KeyJson(keyBase64, key.type)
        }
    }

    suspend fun toSecretKey(): SecretKey {
        return CryptoHelper.crypto.secretKeyFromRaw(this.key.decodeBase64(), this.type).await()
    }
}

suspend fun Key.toJson(): String {
    return JSON.stringify(KeyJson.from(this))
}

suspend fun secretKeyFromJson(json: String): SecretKey {
    return JSON.parse<KeyJson>(json).toSecretKey()
}

@Serializable
private class SymCredentialsJson(val key: KeyJson, val iv: String, val settings: CryptoSettings.Symmetric)

suspend fun SymCredentials.toJson(): String {
    return JSON.stringify(SymCredentialsJson(KeyJson.from(this.key), this.iv.encodeBase64(), this.symmetric))
}

suspend fun symCredentialsFromJson(json: String): SymCredentials {
    val symJson = JSON.parse<SymCredentialsJson>(json)
    return SymCredentials(symJson.key.toSecretKey(), symJson.iv.decodeBase64(), symJson.settings)
}



@Serializable
private class KeyPairJson(val publicKey: String, val privateKey: String, val type: CryptoSettings.KEY_TYPE) {
    companion object {
        suspend fun from(pair: KeyPair): KeyPairJson {
            val publicKeyBase64 = CryptoHelper.crypto.encode(pair.publicKey).await().encodeBase64()
            val privateKeyBase64 = CryptoHelper.crypto.encode(pair.privateKey).await().encodeBase64()
            return KeyPairJson(publicKeyBase64, privateKeyBase64, pair.publicKey.type)
        }
    }

    suspend fun toKeyPair(): KeyPair {
        val publicK = CryptoHelper.crypto.publicKeyFromRaw(publicKey.decodeBase64(), type).await()
        val privateK = CryptoHelper.crypto.privateKeyFromRaw(privateKey.decodeBase64(), type).await()
        return KeyPair(publicK, privateK)
    }
}

@Serializable
private class SignCredentialsJson(val key: KeyPairJson, val settings: CryptoSettings.Signature)

suspend fun SignCredentials.toJson(): String {
    return JSON.stringify(SignCredentialsJson(KeyPairJson.from(this.keyPair), this.settings))
}

suspend fun signCredentialsFromJson(json: String): SignCredentials {
    val signJson = JSON.parse<SignCredentialsJson>(json)
    return SignCredentials(signJson.key.toKeyPair(), signJson.settings)
}