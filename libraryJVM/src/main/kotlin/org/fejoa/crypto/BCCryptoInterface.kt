package org.fejoa.crypto

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.fejoa.support.Future
import org.fejoa.support.Future.Companion.completedFuture

import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.security.spec.ECGenParameterSpec
import java.util.logging.Level
import java.util.logging.Logger
import javax.crypto.*
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec

import org.fejoa.support.async
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.spec.SecretKeySpec


class BCCryptoInterface : CryptoInterface {
    internal class JavaSecurityRestrictionRemover// Based on http://stackoverflow.com/questions/1179672/how-to-avoid-installing-unlimited-strength-jce-policy-files-when-deploying-an
    constructor() {

        private// This simply matches the Oracle JRE, but not OpenJDK.
        val isRestrictedCryptography: Boolean
            get() = "Java(TM) SE Runtime Environment" == System.getProperty("java.runtime.name")

        init {
            val logger = Logger.getGlobal()
            if (!isRestrictedCryptography) {
                logger.fine("Cryptography restrictions removal not needed")
            } else {
                try {
                    /*
                 * Do the following, but with reflection to bypass access checks:
                 *
                 * JceSecurity.isRestricted = false;
                 * JceSecurity.defaultPolicy.perms.clear();
                 * JceSecurity.defaultPolicy.add(CryptoAllPermission.INSTANCE);
                 */
                    val jceSecurity = Class.forName("javax.crypto.JceSecurity")
                    val cryptoPermissions = Class.forName("javax.crypto.CryptoPermissions")
                    val cryptoAllPermission = Class.forName("javax.crypto.CryptoAllPermission")

                    val isRestrictedField = jceSecurity.getDeclaredField("isRestricted")
                    isRestrictedField.isAccessible = true
                    val modifiersField = Field::class.java.getDeclaredField("modifiers")
                    modifiersField.isAccessible = true
                    modifiersField.setInt(isRestrictedField, isRestrictedField.modifiers and Modifier.FINAL.inv())
                    isRestrictedField.set(null, false)

                    val defaultPolicyField = jceSecurity.getDeclaredField("defaultPolicy")
                    defaultPolicyField.isAccessible = true
                    val defaultPolicy = defaultPolicyField.get(null) as PermissionCollection

                    val perms = cryptoPermissions.getDeclaredField("perms")
                    perms.isAccessible = true
                    (perms.get(defaultPolicy) as MutableMap<*, *>).clear()

                    val instance = cryptoAllPermission.getDeclaredField("INSTANCE")
                    instance.isAccessible = true
                    defaultPolicy.add(instance.get(null) as Permission)

                    logger.fine("Successfully removed cryptography restrictions")
                } catch (e: Exception) {
                    logger.log(Level.WARNING, "Failed to remove cryptography restrictions", e)
                }
            }
        }
    }

    init {
        Security.addProvider(BouncyCastleProvider())
    }

    @Throws(CryptoException::class)
    override fun deriveKey(secret: String, salt: ByteArray, algorithm: String, iterations: Int, keyLength: Int): Future<SecretKey> {
        return async {
            try {
                val factory = SecretKeyFactory.getInstance(algorithm)
                val spec = PBEKeySpec(secret.toCharArray(), salt, iterations, keyLength)
                return@async SecreteKeyJVM(factory.generateSecret(spec))
            } catch (e: Exception) {
                throw CryptoException(e.message)
            }
        }
    }

    @Throws(CryptoException::class)
    override fun generateKeyPair(settings: CryptoSettings.KeyTypeSettings): Future<KeyPair> {
        val keyGen: KeyPairGenerator
        try {
            if (settings.keyType!!.startsWith("ECIES")) {
                keyGen = KeyPairGenerator.getInstance("ECIES")
                val curve = settings.keyType!!.substring("ECIES/".length)
                keyGen.initialize(ECGenParameterSpec(curve))
            } else {
                keyGen = KeyPairGenerator.getInstance(settings.keyType)
                keyGen.initialize(settings.keySize, SecureRandom())
            }
        } catch (e: Exception) {
            throw CryptoException(e.message)
        }

        val keyPair = keyGen.genKeyPair()

        return Future.completedFuture(KeyPair(PublicKeyJVM(keyPair.public), PrivateKeyJVM(keyPair.private)))
    }

    @Throws(CryptoException::class)
    override fun encryptAsymmetric(input: ByteArray, key: PublicKey, settings: CryptoSettings.Asymmetric): Future<ByteArray> {
        return async {
            val cipher: Cipher
            try {
                cipher = Cipher.getInstance(settings.algorithm!!)
                cipher.init(Cipher.ENCRYPT_MODE, (key as PublicKeyJVM).key)
                return@async cipher.doFinal(input)
            } catch (e: Exception) {
                throw CryptoException(e.message)
            }
        }
    }

    @Throws(CryptoException::class)
    override fun decryptAsymmetric(input: ByteArray, key: PrivateKey, settings: CryptoSettings.Asymmetric): Future<ByteArray> {
        return async {
            val cipher: Cipher
            try {
                cipher = Cipher.getInstance(settings.algorithm!!)
                cipher.init(Cipher.DECRYPT_MODE, (key as PrivateKeyJVM).key)
                return@async cipher.doFinal(input)
            } catch (e: Exception) {
                throw CryptoException(e.message)
            }
        }
    }

    @Throws(CryptoException::class)
    override fun generateSymmetricKey(settings: CryptoSettings.KeyTypeSettings): Future<SecretKey> {
        val keyGenerator: KeyGenerator
        try {
            keyGenerator = KeyGenerator.getInstance(settings.keyType)
        } catch (e: Exception) {
            throw CryptoException(e.message)
        }

        keyGenerator.init(settings.keySize, SecureRandom())
        return Future.completedFuture(SecreteKeyJVM(keyGenerator.generateKey()))
    }

    override fun generateInitializationVector(sizeBits: Int): ByteArray {
        val random = SecureRandom()
        val bytes = ByteArray(sizeBits / 8)
        random.nextBytes(bytes)
        return bytes
    }

    override fun generateSalt(): ByteArray {
        return generateInitializationVector(32 * 8)
    }

    @Throws(CryptoException::class)
    override fun encryptSymmetric(input: ByteArray, secretKey: SecretKey, iv: ByteArray,
                         settings: CryptoSettings.Symmetric): Future<ByteArray> {
        return async {
            try {
                val cipher = Cipher.getInstance(settings.algorithm!!)
                val ips = IvParameterSpec(iv)
                cipher.init(Cipher.ENCRYPT_MODE, (secretKey as SecreteKeyJVM).key, ips)
                return@async cipher.doFinal(input)
            } catch (e: Exception) {
                throw CryptoException(e.message)
            }
        }
    }

    @Throws(CryptoException::class)
    override fun decryptSymmetric(input: ByteArray, secretKey: SecretKey, iv: ByteArray,
                         settings: CryptoSettings.Symmetric): Future<ByteArray> {
        return async {
            try {
                val cipher = Cipher.getInstance(settings.algorithm!!)
                val ips = IvParameterSpec(iv)
                cipher.init(Cipher.DECRYPT_MODE, (secretKey as SecreteKeyJVM).key, ips)
                return@async cipher.doFinal(input)
            } catch (e: Exception) {
                throw CryptoException(e.message)
            }
        }
    }

    /*
    @Throws(CryptoException::class)
    override fun encryptSymmetric(in: InputStream, secretKey: SecretKey, iv: ByteArray,
                         settings: CryptoSettings.Symmetric): Future<InputStream> {
        return async {
            try {
                val cipher = Cipher.getInstance(settings.algorithm!!)
                val ips = IvParameterSpec(iv)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, ips)
                return@async CipherInputStream(`in`, cipher)
            } catch (e: Exception) {
                throw CryptoException(e.message)
            }
        }
    }

    @Throws(CryptoException::class)
    override fun encryptSymmetric(out: OutputStream, secretKey: SecretKey, iv: ByteArray,
                         settings: CryptoSettings.Symmetric): Future<OutputStream> {
        return async {
            try {
                val cipher = Cipher.getInstance(settings.algorithm!!)
                val ips = IvParameterSpec(iv)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, ips)
                return@async CipherOutputStream(out, cipher)
            } catch (e: Exception) {
                throw CryptoException(e.message)
            }
        }
    }

    @Throws(CryptoException::class)
    override fun decryptSymmetric(in: InputStream, secretKey: SecretKey, iv: ByteArray,
                         settings: CryptoSettings.Symmetric): Future<InputStream> {
        return async {
            try {
                val cipher = Cipher.getInstance(settings.algorithm!!)
                val ips = IvParameterSpec(iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, ips)
                return@async CipherInputStream(in, cipher)
            } catch (e: Exception) {
                throw CryptoException(e.message)
            }
        }
    }
    */

    @Throws(CryptoException::class)
    override fun sign(input: ByteArray, key: PrivateKey, settings: CryptoSettings.Signature): Future<ByteArray> {
        return async {
            val signature: Signature
            try {
                signature = java.security.Signature.getInstance(settings.algorithm!!)
                signature.initSign((key as PrivateKeyJVM).key as java.security.PrivateKey)
                signature.update(input)
                return@async signature.sign()
            } catch (e: Exception) {
                throw CryptoException(e.message)
            }
        }
    }

    @Throws(CryptoException::class)
    override fun verifySignature(message: ByteArray, signature: ByteArray, key: PublicKey,
                        settings: CryptoSettings.Signature): Future<Boolean> {
        return async {
            val sig: Signature
            try {
                sig = java.security.Signature.getInstance(settings.algorithm!!)
                sig.initVerify((key as PublicKeyJVM).key as java.security.PublicKey)
                sig.update(message)
                return@async sig.verify(signature)
            } catch (e: Exception) {
                throw CryptoException(e.message)
            }
        }
    }

    companion object {
        // enable “Unlimited Strength” JCE policy
        // This is necessary to use AES256!
        private val remover = JavaSecurityRestrictionRemover()
    }

    override fun encode(key: Key): Future<ByteArray> {
        return completedFuture(key.toByteArray())
    }

    override fun secretKeyFromRaw(key: ByteArray, keySizeBytes: Int, algorithm: String): Future<SecretKey> {
        return completedFuture(SecreteKeyJVM(SecretKeySpec(key, 0, keySizeBytes, algorithm)))
    }

    private fun getKeyFactory(keyType: String): KeyFactory {
        return if (keyType.startsWith("EC")) {
            KeyFactory.getInstance("EC")
        } else {
            KeyFactory.getInstance(keyType)
        }
    }

    override fun privateKeyFromRaw(key: ByteArray, keyType: String): Future<PrivateKey> {
        val spec = PKCS8EncodedKeySpec(key)
        val keyFactory = getKeyFactory(keyType)
        return Future.completedFuture(PrivateKeyJVM(keyFactory.generatePrivate(spec)))
    }

    override fun publicKeyFromRaw(key: ByteArray, keyType: String): Future<PublicKey> {
        val spec = X509EncodedKeySpec(key)
        val keyFactory = getKeyFactory(keyType)
        return Future.completedFuture(PublicKeyJVM(keyFactory.generatePublic(spec)))
    }

}
