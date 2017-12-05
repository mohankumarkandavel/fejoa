package org.fejoa.crypto

import org.fejoa.async.await
import org.fejoa.async.toFuture
import org.fejoa.jsbindings.CryptoKey
import org.fejoa.jsbindings.crypto
import org.fejoa.support.Future
import org.fejoa.support.async
import org.fejoa.support.toUTF
import kotlin.browser.window
import kotlin.js.json


class CryptoKeyWrapper(val key: CryptoKey, algorithm: String) : SecretKey {
    override val algorithm: String = algorithm
}

class SubtleCrypto : CryptoInterface {
    enum class SYM_ALGO(val jsName: String, val javaName: String) {
        AES_CTR("AES-CTR", "AES/CTR/NoPadding")
    }

    enum class KEY_TYPE(val jsName: String, val javaName: String) {
        AES("AES", "AES")
    }

    enum class HASH_TYPE(val jsName: String) {
        SHA256("SHA-256")
    }

    enum class KDF_ALGO(val jsName: String, val javaName: String, val hash: HASH_TYPE) {
        PBKDF2("PBKDF2", "PBKDF2WithHmacSHA256", HASH_TYPE.SHA256)
    }

    private fun toSymJSAlgorithm(algorithm: String): String {
        return SYM_ALGO.values().firstOrNull { it.javaName == algorithm }?.jsName
                ?: throw Exception("Unknown algorithm: $algorithm")
    }

    private fun toJSKeyType(keyType: String): String {
        return KEY_TYPE.values().firstOrNull { it.javaName == keyType }?.jsName
                ?: throw Exception("Unknown key type: $keyType")
    }

    private fun fromJavaKDF(kdf: String): KDF_ALGO {
        return KDF_ALGO.values().firstOrNull { it.javaName == kdf }
                ?: throw Exception("Unknown KDF: $kdf")
    }

    override fun deriveKey(secret: String, salt: ByteArray, algorithm: String, keyLength: Int, iterations: Int): Future<SecretKey> = async {
        val kdf = fromJavaKDF(algorithm)
        val passwordKey = window.crypto().subtle.importKey(
                "raw",
                secret.toUTF(),
                json("name" to kdf.jsName),
                false,
                arrayOf("deriveBits", "deriveKey")
        ).await()

        //TODO: put this into the config?
        val symKeyType = KEY_TYPE.AES

        val derivedKey = window.crypto().subtle.deriveKey(
                    json("name" to kdf.jsName,
                            "salt" to salt,
                            "iterations" to iterations,
                            "hash" to kdf.hash.jsName
                    ),
                    passwordKey,
                    json("name" to symKeyType.jsName, "length" to keyLength),
                    true,
                    arrayOf("encrypt", "decrypt")).await()

        return@async CryptoKeyWrapper(derivedKey, symKeyType.jsName)
    }

    override fun generateKeyPair(settings: CryptoSettings.KeyTypeSettings): Future<KeyPair> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun generateSymmetricKey(settings: CryptoSettings.KeyTypeSettings): Future<SecretKey> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun encryptAsymmetric(input: ByteArray, key: PublicKey, settings: CryptoSettings.Asymmetric): Future<ByteArray> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun decryptAsymmetric(input: ByteArray, key: PrivateKey, settings: CryptoSettings.Asymmetric): Future<ByteArray> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun encryptSymmetric(input: ByteArray, secretKey: SecretKey, iv: ByteArray, settings: CryptoSettings.Symmetric): Future<ByteArray> {
        val algorithm = json("name" to toSymJSAlgorithm(settings.algorithm),
                "counter" to iv,
                "length" to settings.ivSize)
        val cryptoKey = (secretKey as CryptoKeyWrapper).key
        return window.crypto().subtle.encrypt(algorithm, cryptoKey, input).toFuture()
    }

    override fun decryptSymmetric(input: ByteArray, secretKey: SecretKey, iv: ByteArray, settings: CryptoSettings.Symmetric): Future<ByteArray> {
        val algorithm = json("name" to toSymJSAlgorithm(settings.algorithm),
                "counter" to iv,
                "length" to settings.ivSize)
        val cryptoKey = (secretKey as CryptoKeyWrapper).key
        return window.crypto().subtle.decrypt(algorithm, cryptoKey, input).toFuture()
    }

    override fun encode(key: Key): Future<ByteArray> {
        val cryptoKey = (key as CryptoKeyWrapper).key
        return window.crypto().subtle.exportKey("raw", cryptoKey).toFuture().then { it as ByteArray }
    }

    override fun sign(input: ByteArray, key: PrivateKey, settings: CryptoSettings.Signature): Future<ByteArray> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun verifySignature(message: ByteArray, signature: ByteArray, key: PublicKey, settings: CryptoSettings.Signature): Future<Boolean> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun secretKeyFromRaw(key: ByteArray, keySizeBytes: Int, keyType: String): Future<SecretKey> {
        return window.crypto().subtle.importKey("raw",
                key,
                json("name" to toJSKeyType(keyType)),
                true,
                arrayOf("encrypt", "decrypt")).toFuture().then { CryptoKeyWrapper(it, keyType) }
    }

    override fun privateKeyFromRaw(key: ByteArray, keyType: String): Future<PrivateKey> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun publicKeyFromRaw(key: ByteArray, keyType: String): Future<PublicKey> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}