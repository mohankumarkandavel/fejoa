package org.fejoa.crypto

import org.fejoa.support.*
import kotlin.math.min


interface CryptoInterface {
    fun deriveKey(secret: String, salt: ByteArray, algorithm: String, keyLength: Int, iterations: Int)
            : Future<SecretKey>

    fun generateKeyPair(settings: CryptoSettings.KeyTypeSettings): Future<KeyPair>

    fun generateSymmetricKey(settings: CryptoSettings.KeyTypeSettings): Future<SecretKey>

    fun generateInitializationVector(sizeInBits: Int): ByteArray {
        val buffer = ByteArray(sizeInBits / 8)
        val random = Random()
        random.read(buffer)
        return buffer
    }

    fun generateSalt(): ByteArray {
        return generateInitializationVector(32 * 8)
    }

    fun encryptAsymmetric(input: ByteArray, key: PublicKey, settings: CryptoSettings.Asymmetric)
            : Future<ByteArray>
    fun decryptAsymmetric(input: ByteArray, key: PrivateKey, settings: CryptoSettings.Asymmetric)
            : Future<ByteArray>

    fun encryptSymmetric(input: ByteArray, secretKey: SecretKey, iv: ByteArray, settings: CryptoSettings.Symmetric)
            : Future<ByteArray>
    fun decryptSymmetric(input: ByteArray, secretKey: SecretKey, iv: ByteArray, settings: CryptoSettings.Symmetric)
            : Future<ByteArray>

    fun encode(key: Key): Future<ByteArray>

    /*
    fun encryptSymmetric(output: OutputStream, secretKey: SecretKey, iv: ByteArray, settings: CryptoSettings.Symmetric)
            : CompletableFuture<OutputStream>
    fun encryptSymmetric(in: InputStream, secretKey: SecretKey, iv: ByteArray, settings: CryptoSettings.Symmetric)
            : CompletableFuture<InputStream>
    fun decryptSymmetric(in: InputStream, secretKey: SecretKey, iv: ByteArray, settings: CryptoSettings.Symmetric)
            : CompletableFuture<InputStream>
*/

    fun sign(input: ByteArray, key: PrivateKey, settings: CryptoSettings.Signature): Future<ByteArray>
    fun verifySignature(message: ByteArray, signature: ByteArray, key: PublicKey, settings: CryptoSettings.Signature)
            : Future<Boolean>

    fun secretKeyFromRaw(key: ByteArray, keySizeBytes: Int, algorithm: String): Future<SecretKey>
    fun secretKeyFromRaw(key: ByteArray, algorithm: String): Future<SecretKey> {
        return secretKeyFromRaw(key, key.size, algorithm)
    }
    fun secretKeyFromRaw(key: ByteArray, settings: CryptoSettings.KeyTypeSettings): Future<SecretKey> {
        return secretKeyFromRaw(key, settings.keySize / 8, settings.keyType)
    }

    fun privateKeyFromRaw(key: ByteArray, keyType: String): Future<PrivateKey>
    fun publicKeyFromRaw(key: ByteArray, keyType: String): Future<PublicKey>

    private fun splitIntoEqualParts(string: String, partitionSize: Int): List<String> {
        val parts = ArrayList<String>()
        val length = string.length
        var i = 0
        while (i < length) {
            parts.add(string.substring(i, min(length, i + partitionSize)))
            i += partitionSize
        }
        return parts
    }

    private fun convertToPEM(type: String, key: Key): Future<String> {
        return async {
            var pemKey = "-----BEGIN $type-----\n"
            val parts = splitIntoEqualParts(encode(key).await().encodeBase64(), 64)
            for (part in parts)
                pemKey += part + "\n"
            pemKey += "-----END $type-----"
            return@async pemKey
        }
    }

    fun convertToPEM(key: PublicKey): Future<String> {
        val algo = key.algorithm
        return convertToPEM(algo + " PUBLIC KEY", key)
    }

    fun convertToPEM(key: PrivateKey): Future<String> {
        val algo = key.algorithm
        return convertToPEM(algo + " PRIVATE KEY", key)
    }

    private fun parsePemKeyType(pemKey: String): String {
        val startIndex = "-----BEGIN ".length
        val endIndex = pemKey.indexOf(" ", startIndex)
        return if (startIndex >= pemKey.length || endIndex < 0 || endIndex >= pemKey.length) "" else pemKey.substring(startIndex, endIndex)

    }

    fun publicKeyFromPem(pemKey: String): Future<PublicKey> {
        var pemKey = pemKey
        val type = parsePemKeyType(pemKey)
        pemKey = pemKey.replace("-----BEGIN $type PUBLIC KEY-----\n", "")
        pemKey = pemKey.replace("-----END $type PUBLIC KEY-----", "")

        val decoded = pemKey.decodeBase64()
        return publicKeyFromRaw(decoded, type)
    }

    fun privateKeyFromPem(pemKey: String): Future<PrivateKey> {
        var pemKey = pemKey
        val type = parsePemKeyType(pemKey)
        pemKey = pemKey.replace("-----BEGIN $type PRIVATE KEY-----\n", "")
        pemKey = pemKey.replace("-----END $type PRIVATE KEY-----", "")

        val decoded = pemKey.decodeBase64()
        return privateKeyFromRaw(decoded, type)
    }
}
