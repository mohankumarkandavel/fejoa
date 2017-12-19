package org.fejoa.crypto

import org.fejoa.support.*
import kotlin.math.min


interface CryptoInterface {
    fun deriveKey(secret: String, salt: ByteArray, algorithm: CryptoSettings.KDF_ALGO, keyLength: Int, iterations: Int)
            : Future<SecretKey>
    fun deriveKey(secret: String, salt: ByteArray, kdf: CryptoSettings.KDF): Future<SecretKey> {
        return deriveKey(secret, salt, kdf.algo, kdf.keySize, kdf.iterations)
    }

    fun generateKeyPair(settings: CryptoSettings.KeyType): Future<KeyPair>

    fun generateSymmetricKey(settings: CryptoSettings.KeyType): Future<SecretKey>

    fun generateInitializationVector(sizeInBits: Int): ByteArray {
        val buffer = ByteArray(sizeInBits / 8)
        val random = Random()
        random.read(buffer)
        return buffer
    }

    fun generateSalt16(): ByteArray {
        return generateInitializationVector(16 * 8)
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

    fun encryptSymmetric(input: ByteArray, credentials: SymCredentials): Future<ByteArray> {
        return encryptSymmetric(input, credentials.key, credentials.iv, credentials.settings)
    }
    fun decryptSymmetric(input: ByteArray, credentials: SymCredentials): Future<ByteArray> {
        return decryptSymmetric(input, credentials.key, credentials.iv, credentials.settings)
    }

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

    fun secretKeyFromRaw(key: ByteArray, keySizeBytes: Int, type: CryptoSettings.KEY_TYPE): Future<SecretKey>
    fun secretKeyFromRaw(key: ByteArray, type: CryptoSettings.KEY_TYPE): Future<SecretKey> {
        return secretKeyFromRaw(key, key.size, type)
    }
    fun secretKeyFromRaw(key: ByteArray, settings: CryptoSettings.KeyType): Future<SecretKey> {
        return secretKeyFromRaw(key, settings.size / 8, settings.type)
    }

    fun privateKeyFromRaw(key: ByteArray, type: CryptoSettings.KEY_TYPE): Future<PrivateKey>
    fun publicKeyFromRaw(key: ByteArray, type: CryptoSettings.KEY_TYPE): Future<PublicKey>

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
        val algo = key.type.name
        return convertToPEM(algo + " PUBLIC KEY", key)
    }

    fun convertToPEM(key: PrivateKey): Future<String> {
        val algo = key.type.name
        return convertToPEM(algo + " PRIVATE KEY", key)
    }

    private fun parsePemKeyType(pemKey: String): CryptoSettings.KEY_TYPE {
        val startIndex = "-----BEGIN ".length
        val endIndex = pemKey.indexOf(" ", startIndex)
        val rawType = if (startIndex >= pemKey.length || endIndex < 0 || endIndex >= pemKey.length) ""
            else pemKey.substring(startIndex, endIndex)
        return CryptoSettings.KEY_TYPE.values().firstOrNull { it.name == rawType }
                ?: throw Exception("Unsupported key type: $rawType")
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
