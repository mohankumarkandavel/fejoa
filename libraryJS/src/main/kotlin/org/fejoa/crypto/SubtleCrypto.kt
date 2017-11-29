package org.fejoa.crypto

import java8compat.util.concurrent.CompletableFuture
import javacompat.security.KeyPair
import java.security.Key
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.SecretKey


class SubtleCrypto : CryptoInterface {
    override fun deriveKey(secret: String, salt: ByteArray, algorithm: String, keyLength: Int, iterations: Int): CompletableFuture<SecretKey> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun generateKeyPair(settings: CryptoSettings.KeyTypeSettings): CompletableFuture<KeyPair> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun generateSymmetricKey(settings: CryptoSettings.KeyTypeSettings): CompletableFuture<SecretKey> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun generateInitializationVector(size: Int): ByteArray {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun generateSalt(): ByteArray {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun encryptAsymmetric(input: ByteArray, key: PublicKey, settings: CryptoSettings.Asymmetric): CompletableFuture<ByteArray> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun decryptAsymmetric(input: ByteArray, key: PrivateKey, settings: CryptoSettings.Asymmetric): CompletableFuture<ByteArray> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun encryptSymmetric(input: ByteArray, secretKey: SecretKey, iv: ByteArray, settings: CryptoSettings.Symmetric): CompletableFuture<ByteArray> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun decryptSymmetric(input: ByteArray, secretKey: SecretKey, iv: ByteArray, settings: CryptoSettings.Symmetric): CompletableFuture<ByteArray> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun encode(key: Key): CompletableFuture<ByteArray> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun sign(input: ByteArray, key: PrivateKey, settings: CryptoSettings.Signature): CompletableFuture<ByteArray> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun verifySignature(message: ByteArray, signature: ByteArray, key: PublicKey, settings: CryptoSettings.Signature): CompletableFuture<Boolean> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun secretKeyFromRaw(key: ByteArray, keySizeBytes: Int, algorithm: String): CompletableFuture<SecretKey> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun privateKeyFromRaw(key: ByteArray, keyType: String): CompletableFuture<PrivateKey> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun publicKeyFromRaw(key: ByteArray, keyType: String): CompletableFuture<PublicKey> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}