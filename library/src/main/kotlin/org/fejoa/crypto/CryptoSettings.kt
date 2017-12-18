package org.fejoa.crypto

import kotlinx.serialization.SerialId
import kotlinx.serialization.Serializable


class CryptoSettings private constructor() {
    enum class SIGN_ALGO(val javaName: String, val jsName: String, val hash: HASH_TYPE = HASH_TYPE.SHA256) {
        ECDSA_SHA256("SHA256withECDSA", "", HASH_TYPE.SHA256),
        RSASSA_PKCS1_v1_5("SHA256withRSA", "RSASSA-PKCS1-v1_5", HASH_TYPE.SHA256)
    }

    enum class ASYM_ALGO(val javaName: String, val jsName: String, val hash: HASH_TYPE = HASH_TYPE.SHA256) {
        ECIES("ECIES", "-"),
        RSA_OAEP_SHA256("RSA/NONE/OAEPWithSHA256AndMGF1Padding", "RSA-OAEP", HASH_TYPE.SHA256),
    }

    enum class SYM_ALGO(val javaName: String, val jsName: String) {
        AES_CTR("AES/CTR/NoPadding", "AES-CTR")
    }

    enum class KEY_TYPE(val javaName: String, val jsName: String, val curve: String = "-") {
        AES("AES", "AES"),
        ECIES_SECP256R1("ECIES/secp256r1", "-", "P-256"),
        ECDSA_SECP256R1("ECDSA/secp256r1", "ECDSA", "P-256"),
        RSA("RSA", "")
    }

    enum class HASH_TYPE(val jsName: String) {
        SHA256("SHA-256")
    }

    enum class KDF_ALGO(val javaName: String, val jsName: String, val hash: HASH_TYPE) {
        PBKDF2_SHA256("PBKDF2WithHmacSHA256", "PBKDF2", HASH_TYPE.SHA256)
    }

    @Serializable
    data class Symmetric(
            @SerialId(id = 0)
            val key: KeyType = KeyType(),
            @SerialId(id = 1)
            var algo: SYM_ALGO = SYM_ALGO.AES_CTR,
            @SerialId(id = 2)
            var ivSize: Int = -1
    )

    @Serializable
    data class KDF(
            @SerialId(id = 0)
            var algo: KDF_ALGO = KDF_ALGO.PBKDF2_SHA256,
            @SerialId(id = 1)
            var iterations: Int = -1,
            @SerialId(id = 2)
            var keySize: Int = -1
    )

    @Serializable
    data class Signature(
            @SerialId(id = 0)
            val key: KeyType = KeyType(),
            @SerialId(id = 1)
            var algo: SIGN_ALGO = SIGN_ALGO.RSASSA_PKCS1_v1_5
    )

    @Serializable
    data class Asymmetric(
            @SerialId(id = 0)
            val key: KeyType = KeyType(),
            @SerialId(id = 1)
            var algo: ASYM_ALGO = ASYM_ALGO.RSA_OAEP_SHA256
    )

    @Serializable
    data class KeyType(
            @SerialId(id = 0)
            var size: Int = -1,
            @SerialId(id = 1)
            var type: KEY_TYPE = KEY_TYPE.AES
    )

    var kdf = KDF()
    var publicKey = Asymmetric()
    var signature = Signature()
    var symmetric = Symmetric()

    companion object {

        fun setDefaultEC(cryptoSettings: CryptoSettings) {
            cryptoSettings.publicKey.algo = ASYM_ALGO.ECIES
            cryptoSettings.publicKey.key.type = KEY_TYPE.ECIES_SECP256R1
            cryptoSettings.publicKey.key.size = 0

            cryptoSettings.signature.algo = SIGN_ALGO.ECDSA_SHA256
            cryptoSettings.signature.key.type = KEY_TYPE.ECDSA_SECP256R1
            cryptoSettings.signature.key.size = 0
        }

        fun setDefaultRSA(cryptoSettings: CryptoSettings) {
            cryptoSettings.publicKey.algo = ASYM_ALGO.RSA_OAEP_SHA256
            cryptoSettings.publicKey.key.type = KEY_TYPE.RSA
            cryptoSettings.publicKey.key.size = 2048

            cryptoSettings.signature.algo = SIGN_ALGO.RSASSA_PKCS1_v1_5
            cryptoSettings.signature.key.type = KEY_TYPE.RSA
            cryptoSettings.signature.key.size = 2048
        }

        val default: CryptoSettings
            get() {
                val cryptoSettings = CryptoSettings()

                setDefaultRSA(cryptoSettings)

                cryptoSettings.symmetric.algo = SYM_ALGO.AES_CTR
                cryptoSettings.symmetric.key.type = KEY_TYPE.AES
                cryptoSettings.symmetric.key.size = 256
                cryptoSettings.symmetric.ivSize = 16 * 8

                cryptoSettings.kdf.algo = KDF_ALGO.PBKDF2_SHA256
                cryptoSettings.kdf.iterations = 20000
                cryptoSettings.kdf.keySize = 256

                return cryptoSettings
            }

        val fast: CryptoSettings
            get() {
                val cryptoSettings = default
                cryptoSettings.publicKey.key.size = 512

                cryptoSettings.symmetric.key.size = 128
                cryptoSettings.symmetric.ivSize = 16 * 8

                cryptoSettings.kdf.iterations = 1

                return cryptoSettings
            }

        fun empty(): CryptoSettings {
            return CryptoSettings()
        }

        fun signatureSettings(algorithm: SIGN_ALGO): Signature {
            val cryptoSettings = signatureSettings()
            cryptoSettings.algo = algorithm
            return cryptoSettings
        }

        fun signatureSettings(): Signature {
            val settings = empty()
            val defaultSettings = default

            settings.signature.algo = defaultSettings.signature.algo
            settings.signature.key.type = defaultSettings.signature.key.type
            settings.signature.key.size = defaultSettings.signature.key.size
            return settings.signature
        }

        fun symmetricSettings(keyType: KEY_TYPE, algorithm: SYM_ALGO): Symmetric {
            val cryptoSettings = empty()
            cryptoSettings.symmetric.key.type = keyType
            cryptoSettings.symmetric.algo = algorithm
            return cryptoSettings.symmetric
        }

        fun symmetricKeyTypeSettings(keyType: KEY_TYPE): CryptoSettings {
            val cryptoSettings = empty()
            cryptoSettings.symmetric.key.type = keyType
            return cryptoSettings
        }

    }
}
