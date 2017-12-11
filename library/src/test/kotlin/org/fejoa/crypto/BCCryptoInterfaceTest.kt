package org.fejoa.crypto

import kotlinx.coroutines.experimental.runBlocking
import org.fejoa.support.await
import org.fejoa.support.toUTF
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test


class BCCryptoInterfaceTest {

    @Test
    //@Throws(Exception::class)
    fun testCrypto() {
        val settings = CryptoSettings.default
        CryptoSettings.setDefaultEC(settings)
        doTest(settings)

        CryptoSettings.setDefaultRSA(settings)
        doTest(settings)
    }

    //@Throws(CryptoException::class, IOException::class)
    private fun doTest(settings: CryptoSettings) {
        runBlocking {
            val cryptoInterface = CryptoHelper.crypto
            val keyPair = cryptoInterface.generateKeyPair(settings.publicKey).await()

            // encrypt asymmetric + signature
            val clearTextAsym = "hello crypto asymmetric"
            val encryptedAsymmetric = cryptoInterface.encryptAsymmetric(clearTextAsym.toUTF(), keyPair.publicKey,
                    settings.publicKey).await()
            val signature = cryptoInterface.sign(clearTextAsym.toUTF(), keyPair.privateKey, settings.signature).await()

            // encrypt symmetric
            val clearTextSym = "hello crypto symmetric"
            val iv = cryptoInterface.generateInitializationVector(settings.symmetric.ivSize)
            var secretKey = cryptoInterface.generateSymmetricKey(settings.symmetric).await()
            val encryptedSymmetric = cryptoInterface.encryptSymmetric(clearTextSym.toUTF(), secretKey, iv,
                    settings.symmetric).await()

            // store keys to pem and restore
            val privateKeyString = cryptoInterface.convertToPEM(keyPair.privateKey).await()
            val publicKeyString = cryptoInterface.convertToPEM(keyPair.publicKey).await()
            val secretKeyBytes = cryptoInterface.encode(secretKey).await()
            val privateKey = cryptoInterface.privateKeyFromPem(privateKeyString).await()
            val publicKey = cryptoInterface.publicKeyFromPem(publicKeyString).await()
            secretKey = cryptoInterface.secretKeyFromRaw(secretKeyBytes, settings.symmetric).await()

            // test if we can decrypt / verify the signature
            val decryptedAsymmetric = cryptoInterface.decryptAsymmetric(encryptedAsymmetric, privateKey,
                    settings.publicKey).await()
            assertTrue(clearTextAsym.toUTF() contentEquals decryptedAsymmetric)
            assertTrue(cryptoInterface.verifySignature(clearTextAsym.toUTF(), signature, publicKey, settings.signature).await())
            val decryptedSymmetric = cryptoInterface.decryptSymmetric(encryptedSymmetric, secretKey, iv,
                    settings.symmetric).await()
            assertTrue(clearTextSym.toUTF() contentEquals decryptedSymmetric)

            // check if encryption still works with the public key that we converted to pem and back
            val encryptedAsymmetricAfterPem = cryptoInterface.encryptAsymmetric(clearTextAsym.toUTF(), publicKey,
                    settings.publicKey).await()
            val decryptedAsymmetricAfterPem = cryptoInterface.decryptAsymmetric(encryptedAsymmetric, privateKey,
                    settings.publicKey).await()
            assertTrue(clearTextAsym.toUTF() contentEquals  decryptedAsymmetricAfterPem)
            // symmetric stream test
            /*val byteArrayOutputStream = ByteArrayOutputStream()
            val outputStream = cryptoInterface.encryptSymmetric(byteArrayOutputStream, secretKey, iv,
                    settings.symmetric).await()
            outputStream.write(clearTextSym.toByteArray())
            outputStream.flush()
            val byteArrayInputStream = ByteArrayInStream(byteArrayOutputStream.toByteArray())
            val inputStream = cryptoInterface.decryptSymmetric(byteArrayInputStream, secretKey, iv,
                    settings.symmetric)
            val reader = BufferedReader(InputStreamReader(inputStream))
            TestCase.assertEquals(clearTextSym, reader.readLine())
            */

            // test if kdf gives the same value twice
            val password = "testPassword348#"
            val salt = cryptoInterface.generateSalt()
            val kdfKey1 = cryptoInterface.deriveKey(password, salt, settings.masterPassword.kdfAlgorithm,
                    20000, 256).await()
            assertEquals(32, cryptoInterface.encode(kdfKey1).await().size)
            val kdfKey2 = cryptoInterface.deriveKey(password, salt, settings.masterPassword.kdfAlgorithm,
                    20000, 256).await()
            assertTrue(cryptoInterface.encode(kdfKey1).await() contentEquals
                    cryptoInterface.encode(kdfKey2).await())
        }
    }

    @Test
    //@Throws(Exception::class)
    fun testConvergentEncryption() {
        runBlocking {
            val settings = CryptoSettings.default

            val cryptoInterface = CryptoHelper.crypto

            // encrypt symmetric
            val clearTextSym = "hello crypto symmetric"
            val iv = cryptoInterface.generateInitializationVector(settings.symmetric.ivSize)
            val secretKey = cryptoInterface.generateSymmetricKey(settings.symmetric).await()
            val encryptedSymmetric = cryptoInterface.encryptSymmetric(clearTextSym.toUTF(), secretKey, iv,
                    settings.symmetric).await()
            val encryptedSymmetric2 = cryptoInterface.encryptSymmetric(clearTextSym.toUTF(), secretKey, iv,
                    settings.symmetric).await()

            assertTrue(encryptedSymmetric contentEquals encryptedSymmetric2)
        }
    }
}
