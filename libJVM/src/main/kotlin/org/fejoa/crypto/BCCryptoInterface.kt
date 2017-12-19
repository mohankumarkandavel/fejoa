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

    companion object {
        // enable “Unlimited Strength” JCE policy
        // This is necessary to use AES256!
        private val remover = JavaSecurityRestrictionRemover()
    }

    internal class JavaSecurityRestrictionRemover {
        // Based on http://stackoverflow.com/questions/1179672/how-to-avoid-installing-unlimited-strength-jce-policy-files-when-deploying-an

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
    override fun deriveKey(secret: String, salt: ByteArray, algorithm: CryptoSettings.KDF_ALGO, iterations: Int,
                           keyLength: Int): Future<SecretKey> {
        return async {
            try {
                val factory = SecretKeyFactory.getInstance(algorithm.javaName)
                val spec = PBEKeySpec(secret.toCharArray(), salt, iterations, keyLength)
                return@async SecreteKeyJVM(factory.generateSecret(spec), CryptoSettings.KEY_TYPE.AES)
            } catch (e: Exception) {
                throw CryptoException(e.message)
            }
        }
    }

    @Throws(CryptoException::class)
    override fun generateKeyPair(settings: CryptoSettings.KeyType): Future<KeyPair> {
        val keyGen: KeyPairGenerator
        try {
            if (settings.type.name.startsWith("ECIES")) {
                keyGen = KeyPairGenerator.getInstance("ECIES")
                val curve = settings.type.name.substring("ECIES/".length)
                keyGen.initialize(ECGenParameterSpec(curve))
            } else {
                keyGen = KeyPairGenerator.getInstance(settings.type.javaName)
                keyGen.initialize(settings.size, SecureRandom())
            }
        } catch (e: Exception) {
            throw CryptoException(e.message)
        }

        val keyPair = keyGen.genKeyPair()

        return Future.completedFuture(KeyPair(PublicKeyJVM(keyPair.public, settings.type),
                PrivateKeyJVM(keyPair.private, settings.type)))
    }

    @Throws(CryptoException::class)
    override fun encryptAsymmetric(input: ByteArray, key: PublicKey, settings: CryptoSettings.Asymmetric): Future<ByteArray> {
        return async {
            val cipher: Cipher
            try {
                cipher = Cipher.getInstance(settings.algo.javaName)
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
                cipher = Cipher.getInstance(settings.algo.javaName)
                cipher.init(Cipher.DECRYPT_MODE, (key as PrivateKeyJVM).key)
                return@async cipher.doFinal(input)
            } catch (e: Exception) {
                throw CryptoException(e.message)
            }
        }
    }

    @Throws(CryptoException::class)
    override fun generateSymmetricKey(settings: CryptoSettings.KeyType): Future<SecretKey> {
        val keyGenerator: KeyGenerator
        try {
            keyGenerator = KeyGenerator.getInstance(settings.type.javaName)
        } catch (e: Exception) {
            throw CryptoException(e.message)
        }

        keyGenerator.init(settings.size, SecureRandom())
        return Future.completedFuture(SecreteKeyJVM(keyGenerator.generateKey(), settings.type))
    }

    @Throws(CryptoException::class)
    override fun encryptSymmetric(input: ByteArray, secretKey: SecretKey, iv: ByteArray,
                         settings: CryptoSettings.Symmetric): Future<ByteArray> {
        return async {
            try {
                val cipher = Cipher.getInstance(settings.algo.javaName)
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
                val cipher = Cipher.getInstance(settings.algo.javaName)
                val ips = IvParameterSpec(iv)
                cipher.init(Cipher.DECRYPT_MODE, (secretKey as SecreteKeyJVM).key, ips)
                return@async cipher.doFinal(input)
            } catch (e: Exception) {
                throw CryptoException(e.message)
            }
        }
    }

    @Throws(CryptoException::class)
    override fun sign(input: ByteArray, key: PrivateKey, settings: CryptoSettings.Signature): Future<ByteArray> {
        return async {
            val signature: Signature
            try {
                signature = Signature.getInstance(settings.algo.javaName)
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
                sig = Signature.getInstance(settings.algo.javaName)
                sig.initVerify((key as PublicKeyJVM).key as java.security.PublicKey)
                sig.update(message)
                return@async sig.verify(signature)
            } catch (e: Exception) {
                throw CryptoException(e.message)
            }
        }
    }

    override fun encode(key: Key): Future<ByteArray> {
        return completedFuture((key as KeyJVM).toByteArray())
    }

    override fun secretKeyFromRaw(key: ByteArray, keySizeBytes: Int, type: CryptoSettings.KEY_TYPE)
            : Future<SecretKey> {
        return completedFuture(SecreteKeyJVM(SecretKeySpec(key, 0, keySizeBytes, type.javaName), type))
    }

    private fun getKeyFactory(keyType: String): KeyFactory {
        return if (keyType.startsWith("EC")) {
            KeyFactory.getInstance("EC")
        } else {
            KeyFactory.getInstance(keyType)
        }
    }

    override fun privateKeyFromRaw(key: ByteArray, type: CryptoSettings.KEY_TYPE): Future<PrivateKey> {
        val spec = PKCS8EncodedKeySpec(key)
        val keyFactory = getKeyFactory(type.javaName)
        return Future.completedFuture(PrivateKeyJVM(keyFactory.generatePrivate(spec), type))
    }

    override fun publicKeyFromRaw(key: ByteArray, type: CryptoSettings.KEY_TYPE): Future<PublicKey> {
        val spec = X509EncodedKeySpec(key)
        val keyFactory = getKeyFactory(type.javaName)
        return Future.completedFuture(PublicKeyJVM(keyFactory.generatePublic(spec), type))
    }

}
