package org.fejoa.crypto

import org.fejoa.support.Future


class SubtleCrypto : CryptoInterface {
    override fun deriveKey(secret: String, salt: ByteArray, algorithm: String, keyLength: Int, iterations: Int): Future<SecretKey> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun generateKeyPair(settings: CryptoSettings.KeyTypeSettings): Future<KeyPair> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun generateSymmetricKey(settings: CryptoSettings.KeyTypeSettings): Future<SecretKey> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun generateInitializationVector(size: Int): ByteArray {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun generateSalt(): ByteArray {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun encryptAsymmetric(input: ByteArray, key: PublicKey, settings: CryptoSettings.Asymmetric): Future<ByteArray> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun decryptAsymmetric(input: ByteArray, key: PrivateKey, settings: CryptoSettings.Asymmetric): Future<ByteArray> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun encryptSymmetric(input: ByteArray, secretKey: SecretKey, iv: ByteArray, settings: CryptoSettings.Symmetric): Future<ByteArray> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun decryptSymmetric(input: ByteArray, secretKey: SecretKey, iv: ByteArray, settings: CryptoSettings.Symmetric): Future<ByteArray> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun encode(key: Key): Future<ByteArray> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun sign(input: ByteArray, key: PrivateKey, settings: CryptoSettings.Signature): Future<ByteArray> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun verifySignature(message: ByteArray, signature: ByteArray, key: PublicKey, settings: CryptoSettings.Signature): Future<Boolean> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun secretKeyFromRaw(key: ByteArray, keySizeBytes: Int, algorithm: String): Future<SecretKey> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun privateKeyFromRaw(key: ByteArray, keyType: String): Future<PrivateKey> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun publicKeyFromRaw(key: ByteArray, keyType: String): Future<PublicKey> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}