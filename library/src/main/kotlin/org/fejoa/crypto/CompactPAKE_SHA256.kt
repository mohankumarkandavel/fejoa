package org.fejoa.auth.crypto

import org.fejoa.crypto.CryptoHelper
import org.fejoa.crypto.CryptoSettings
import org.fejoa.crypto.SecretKey
import org.fejoa.support.*


/**
 * Zero knowledge proof that the prover knows a secret. The prover does not reveal the secret if the verifier does not
 * know the secret. Moreover, the verifier can't brute force the prover's secret from the exchanged data.

 * This is based on EKE2 (Mihir Bellare, David Pointchevaly and Phillip Rogawayz. Authenticated key exchange secure
 * against dictionary attacks)

 * Outline of the protocol:
 * - Enc(), Dec() are encryption/decryption methods using the secret as key
 * - H is a hash function
 * 1) ProverState0 generates random value x and calculates:
 * gx = g.modPow(x, p)
 * encGX = Enc(g.modPow(x, p))
 * The prover sends encGX to the Verifier:
 * ProverState0 -> encGX -> Verifier
 * 2) The verifier generates random value y and calculates:
 * gx = Dec(encGX)
 * gy = g.modPow(y, p)
 * gxy = gx.modPow(Y, p)
 * sk' = gx || gy || gxy
 * authVerifier = H(sk' || 1) (used to by the prover to verify that the verifier knows the secret)
 * The verifier sends Enc(gy) and authVerifier to the prover:
 * Verifier -> Enc(gy),authVerifier -> ProverState1
 * 3) ProverState1 calculates:
 * gy = Dec(encGY)
 * gxy = gy.modPow(x, p)
 * sk' = gx || gy || gxy
 * authVerifier = H(sk' || 1)
 * authProver = H(sk' || 2)
 * The prover checks that authVerifier matches with the received value and sends authProver to the verifier:
 * ProverState1 -> authProver -> Verifier
 * 4) The verifier calculates:
 * authProver = H(sk' || 2)
 * If authProver matches the received value the authentication succeeded.
 */
class CompactPAKE_SHA256_CTR private constructor(encGroup: DH_GROUP, val secret: ByteArray) {
    constructor(encGroup: String, secret: ByteArray)
            : this(DH_GROUP.values().firstOrNull { it.name == encGroup }
                ?: throw Exception("Unsupported group: " + encGroup), secret)

    private val g: BigInteger
    private val p: BigInteger
    private var secretKey: SecretKey? = null

    // the ids are not configurable at the moment
    internal val proverId = "prover"
    internal val verifierId = "verifier"

    val symmetric = CryptoSettings.default.symmetric

    init {
        val parameters: DHParameters = encGroup.params
        g = parameters.g
        p = parameters.p

        symmetric.algorithm = "AES/CTR/NoPadding"
        symmetric.keyType = "AES"
        symmetric.keySize = 256
        symmetric.ivSize = 16 * 8
    }

    private suspend fun getSecretKey(): SecretKey {
        return secretKey?.let { it }
                ?: CryptoHelper.crypto.secretKeyFromRaw(secret, symmetric).await().also { secretKey = it }
    }

    /**
     * @return the encrypted data and the iv used for encryption
     */
    internal suspend fun encrypt(data: BigInteger, secret: SecretKey): Pair<ByteArray, ByteArray> {
        val iv = CryptoHelper.crypto.generateSalt16()
        val byteArray = fromHex(data.toString(16))
        return CryptoHelper.crypto.encryptSymmetric(byteArray, secret, iv, symmetric).await() to iv
    }

    internal suspend fun decrypt(data: ByteArray, secret: SecretKey, iv: ByteArray): BigInteger {
        val plain = CryptoHelper.crypto.decryptSymmetric(data, secret, iv, symmetric).await()
        return BigInteger(plain.toHex(), 16)
    }

    private suspend fun getSessionKeyPrime(gx: BigInteger, gy: BigInteger, gxy: BigInteger): ByteArray {
        return hash(proverId + verifierId + gx.toString(16) + gy.toString(16) + gxy.toString(16))
    }

    private suspend fun getVerifierAuthToken(sessionKeyPrime: ByteArray): ByteArray {
        val hashStream = CryptoHelper.sha256Hash()
        hashStream.write(sessionKeyPrime)
        hashStream.write(1)
        return hashStream.hash()
    }

    private suspend fun getProverAuthToken(sessionKeyPrime: ByteArray): ByteArray {
        val hashStream = CryptoHelper.sha256Hash()
        hashStream.write(sessionKeyPrime)
        hashStream.write(2)
        return hashStream.hash()
    }

    inner class ProverState0 {
        private val x: BigInteger
        private val gx: BigInteger

        init {
            val randomBytes = ByteArray(16)
            Random().read(randomBytes)
            x = BigInteger(randomBytes.toHex(), 16)
            gx = g.modPow(x, p)
        }

        /**
         * @return the encrypted gx and the iv used for encryption
         */
        suspend fun getEncGX(): Pair<ByteArray, ByteArray> {
            return encrypt(gx, getSecretKey())
        }

        suspend fun setVerifierResponse(encGY: ByteArray, iv: ByteArray, authToken: ByteArray): ProverState1? {
            val gy = decrypt(encGY, getSecretKey(), iv)
            val gxy = gy.modPow(x, p)
            val sessionKeyPrime = getSessionKeyPrime(gx, gy, gxy)
            val expectedVerifierToken = getVerifierAuthToken(sessionKeyPrime)
            if (!(expectedVerifierToken contentEquals authToken))
                return null
            return ProverState1(sessionKeyPrime)
        }
    }

    inner class ProverState1 constructor(internal val sessionKeyPrime: ByteArray) {
        suspend fun getAuthToken(): ByteArray {
            return getProverAuthToken(sessionKeyPrime)
        }
    }

    inner class Verifier
    constructor(val encGX: ByteArray, val iv: ByteArray) {
        private val y: BigInteger
        private val gy: BigInteger
        // sessionKey'
        private var sessionKeyPrime: ByteArray? = null

        init {
            val randomBytes = ByteArray(16)
            Random().read(randomBytes)
            y = BigInteger(randomBytes.toHex(), 16)
            gy = g.modPow(y, p)
        }

        suspend private fun calculateSessionKeyPrime(): ByteArray {
            sessionKeyPrime.let { if (it != null) return it }
            val gx = decrypt(encGX, getSecretKey(), iv)
            val gxy = gx.modPow(y, p)
            val keyPrime = getSessionKeyPrime(gx, gy, gxy)
            sessionKeyPrime = keyPrime
            return keyPrime
        }

        suspend fun getEncGy(): Pair<ByteArray, ByteArray> {
            return encrypt(gy, getSecretKey())
        }

        suspend fun getAuthToken(): ByteArray {
            return getVerifierAuthToken(calculateSessionKeyPrime())
        }

        suspend fun verify(proverToken: ByteArray): Boolean {
            val expectedToken = getProverAuthToken(calculateSessionKeyPrime())
            return expectedToken contentEquals proverToken
        }
    }

    private fun createProver(): ProverState0 {
        return ProverState0()
    }

    private fun createVerifier(encGX: ByteArray, iv: ByteArray): Verifier {
        return Verifier(encGX, iv)
    }

    private suspend fun hash(data: String): ByteArray {
        return CryptoHelper.sha256Hash(data.toUTF())
    }

    companion object {
        fun createProver(encGroup: DH_GROUP, secret: ByteArray): CompactPAKE_SHA256_CTR.ProverState0 {
            return CompactPAKE_SHA256_CTR(encGroup, secret).createProver()
        }

        fun createProver(encGroup: String, secret: ByteArray): CompactPAKE_SHA256_CTR.ProverState0 {
            return CompactPAKE_SHA256_CTR(encGroup, secret).createProver()
        }

        fun createVerifier(encGroup: DH_GROUP, secret: ByteArray, encGX: ByteArray, iv: ByteArray)
                : CompactPAKE_SHA256_CTR.Verifier {
            return CompactPAKE_SHA256_CTR(encGroup, secret).createVerifier(encGX, iv)
        }

        fun createVerifier(encGroup: String, secret: ByteArray, encGX: ByteArray, iv: ByteArray)
                : CompactPAKE_SHA256_CTR.Verifier {
            return CompactPAKE_SHA256_CTR(encGroup, secret).createVerifier(encGX, iv)
        }
    }
}
